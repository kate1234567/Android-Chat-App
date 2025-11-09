package com.github.kate1234567.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.github.kate1234567.data.api.ChatApiService
import com.github.kate1234567.data.local.*
import com.github.kate1234567.data.model.*
import com.github.kate1234567.utils.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val apiService: ChatApiService,
    private val messageDao: MessageDao,
    private val channelDao: ChannelDao,
    private val networkMonitor: NetworkMonitor
) {

    private val tokenKey = stringPreferencesKey("auth_token")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")

    val authToken: Flow<String?> = dataStore.data.map { it[tokenKey] }
    val savedCredentials: Flow<Pair<String?, String?>> = dataStore.data.map {
        Pair(it[usernameKey], it[passwordKey])
    }
    val networkStatus: Flow<Boolean> = networkMonitor.observeNetworkStatus()

    fun isNetworkAvailable(): Boolean = networkMonitor.isNetworkAvailable()

    private fun normalizeChannelName(channel: String): String {
        return if (channel.endsWith("@channel")) channel else "${channel}@channel"
    }

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val token = response.body()?.trim()?.trim('"') ?: return Result.failure(Exception("Не получен токен от сервера"))
                saveToken(token)
                saveCredentials(username, password)
                Result.success(token)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Неверный логин или пароль"
                    403 -> "Доступ запрещен"
                    404 -> "Сервер не найден"
                    500 -> "Ошибка сервера"
                    else -> "Ошибка: ${response.code()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (_: java.net.UnknownHostException) {
            Result.failure(Exception("Нет подключения к интернету"))
        } catch (_: java.net.SocketTimeoutException) {
            Result.failure(Exception("Время ожидания истекло"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка: ${e.message ?: "Неизвестная ошибка"}"))
        }
    }

    suspend fun getChannels(): Result<List<String>> {
        val token = authToken.first() ?: return Result.failure(Exception("Требуется авторизация"))

        if (isNetworkAvailable()) {
            try {
                val response = apiService.getChannels(token)
                if (response.isSuccessful) {
                    val channels = response.body() ?: emptyList()
                    val now = System.currentTimeMillis()
                    val entities = channels.map { ChannelEntity(it, now) }
                    channelDao.insertChannels(entities)
                    return Result.success(channels)
                } else {
                    if (response.code() == 401) {
                        clearToken()
                        return Result.failure(Exception("Сессия истекла, войдите снова"))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error loading channels from network", e)
            }
        }

        val cachedChannels = channelDao.getAllChannelsSync().map { it.name }
        return if (cachedChannels.isNotEmpty()) {
            Result.success(cachedChannels)
        } else {
            Result.failure(Exception("Нет подключения к интернету и нет кешированных данных"))
        }
    }


    suspend fun getMessages(channel: String, limit: Int = 20, lastKnownId: String = "0", reverse: Boolean? = null): Result<List<Message>> {
        val token = authToken.first() ?: return Result.failure(Exception("Требуется авторизация"))
        val normalizedChannel = normalizeChannelName(channel)

        if (isNetworkAvailable()) {
            try {
                val isInitialLoad = lastKnownId == "0"
                val useReverse = reverse ?: true
                val actualLastKnownId = if (isInitialLoad) Long.MAX_VALUE.toString() else lastKnownId

                android.util.Log.d("ChatRepository", "Loading messages from server: channel=$channel (original=$normalizedChannel), limit=$limit, lastKnownId=$actualLastKnownId, reverse=$useReverse")

                val response = apiService.getMessages(token, normalizedChannel, limit, actualLastKnownId, useReverse)
                if (response.isSuccessful) {
                    var messages = response.body() ?: emptyList()
                    android.util.Log.d("ChatRepository", "Loaded ${messages.size} messages from server for channel $normalizedChannel")

                    messages = messages.sortedBy { it.time }

                    if (messages.isNotEmpty()) {
                        val entities = messages.map { it.toEntity(normalizedChannel) }
                        messageDao.insertMessages(entities)
                        android.util.Log.d("ChatRepository", "Saved ${entities.size} messages to cache (channel=$normalizedChannel)")
                    }

                    return Result.success(messages)
                } else {
                    if (response.code() == 401) {
                        clearToken()
                        return Result.failure(Exception("Сессия истекла, войдите снова"))
                    }
                    android.util.Log.e("ChatRepository", "Server returned error: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error loading messages from network", e)
            }
        } else {
            android.util.Log.d("ChatRepository", "Network unavailable, trying to load from cache for channel $normalizedChannel")
        }

        val cachedMessages = messageDao.getMessagesByChannelSync(normalizedChannel).map { it.toMessage() }
        android.util.Log.d("ChatRepository", "Loaded ${cachedMessages.size} messages from cache for channel $normalizedChannel")

        return if (cachedMessages.isNotEmpty()) {
            Result.success(cachedMessages)
        } else {
            Result.failure(Exception("Нет подключения к интернету и нет кешированных сообщений"))
        }
    }

    suspend fun getMessagesFromCachePaged(channel: String, limit: Int, page: Int): List<Message> {
        val normalizedChannel = normalizeChannelName(channel)
        val offset = page * limit
        val entities = messageDao.getMessagesByChannelPagedSync(normalizedChannel, limit, offset)
        return entities.map { it.toMessage() }
    }

    suspend fun sendMessage(message: SendMessageRequest): Result<String> {
        val token = authToken.first() ?: return Result.failure(Exception("Требуется авторизация"))

        if (!isNetworkAvailable()) {
            return Result.failure(Exception("Нет подключения к интернету. Отправка сообщений недоступна"))
        }

        return try {
            val normalizedTo = message.to?.let { normalizeChannelName(it) }
            val requestWithFullChannel = message.copy(to = normalizedTo)

            android.util.Log.d("ChatRepository", "Sending message: from=${requestWithFullChannel.from}, to=${requestWithFullChannel.to}, time=${requestWithFullChannel.time}")
            val response = apiService.sendMessage(token, requestWithFullChannel)
            if (response.isSuccessful) {
                val messageId = response.body() ?: ""
                android.util.Log.d("ChatRepository", "Message sent successfully, id=$messageId")

                try {
                    val channelName = requestWithFullChannel.to
                    if (!channelName.isNullOrEmpty() && messageId.isNotEmpty()) {
                        val timeInSeconds = (requestWithFullChannel.time ?: System.currentTimeMillis()) / 1000
                        val sentMessage = Message(
                            id = messageId,
                            from = requestWithFullChannel.from,
                            to = channelName,
                            data = requestWithFullChannel.data,
                            time = timeInSeconds
                        )
                        val messageEntity = sentMessage.toEntity(channelName)
                        messageDao.insertMessages(listOf(messageEntity))
                        android.util.Log.d("ChatRepository", "Saved sent message to cache: id=$messageId, channel=$channelName, time=$timeInSeconds")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Error saving sent message to DB", e)
                }

                Result.success(messageId)
            } else {
                if (response.code() == 401) {
                    clearToken()
                    return Result.failure(Exception("Сессия истекла, войдите снова"))
                }
                Result.failure(Exception("Не удалось отправить сообщение"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message ?: "Неизвестная ошибка"}"))
        }
    }

    suspend fun logout(): Result<Unit> {
        val token = authToken.first()
        if (token != null) {
            try {
                apiService.logout(token)
            } catch (_: Exception) {
            }
        }
        clearToken()
        messageDao.deleteAllMessages()
        channelDao.deleteAllChannels()
        return Result.success(Unit)
    }

    private suspend fun saveToken(token: String) {
        dataStore.edit { it[tokenKey] = token }
    }

    private suspend fun saveCredentials(username: String, password: String) {
        dataStore.edit {
            it[usernameKey] = username
            it[passwordKey] = password
        }
    }

    private suspend fun clearToken() {
        dataStore.edit {
            it.remove(tokenKey)
        }
    }
}
