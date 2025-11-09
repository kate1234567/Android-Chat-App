package com.github.kate1234567.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kate1234567.data.model.Message
import com.github.kate1234567.data.model.MessageData
import com.github.kate1234567.data.model.SendMessageRequest
import com.github.kate1234567.data.repository.ChatRepository
import com.github.kate1234567.data.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _messagesState = MutableStateFlow<MessagesState>(MessagesState.Loading)
    val messagesState: StateFlow<MessagesState> = _messagesState

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable

    private var currentChannel: String? = null
    private var lastKnownId: String = "0"
    private var loadedMessages: MutableList<Message> = mutableListOf()
    private val messageIds = mutableSetOf<String>()
    private var currentPage = 0

    private fun normalize(channel: String?): String? = channel?.let { if (it.endsWith("@channel")) it else "${it}@channel" }

    init {
        viewModelScope.launch {
            repository.networkStatus.collect { isAvailable ->
                _isNetworkAvailable.value = isAvailable
                android.util.Log.d("MessageViewModel", "Network status changed: $isAvailable")
            }
        }

        viewModelScope.launch {
            webSocketManager.newMessages.collect { message ->
                android.util.Log.d("MessageViewModel", "Received message: from=${message.from}, to=${message.to}, currentChannel=$currentChannel")
                val normalizedTo = normalize(message.to)
                val normalizedCurrent = normalize(currentChannel)
                if (normalizedTo != null && normalizedTo == normalizedCurrent) {
                    if (!messageIds.contains(message.id)) {
                        messageIds.add(message.id)
                        loadedMessages.add(message)
                        _messagesState.value = MessagesState.Success(loadedMessages.toList())
                        android.util.Log.d("MessageViewModel", "Message added to list, total: ${loadedMessages.size}")
                    } else {
                        android.util.Log.d("MessageViewModel", "Message already exists, skipping")
                    }
                } else {
                    android.util.Log.d("MessageViewModel", "Message for different channel: ${message.to} != $currentChannel")
                }
            }
        }
    }



    fun loadMessages(channel: String, isLoadMore: Boolean = false) {

        if (channel != currentChannel) {
            android.util.Log.d("MessageViewModel", "Channel changed from $currentChannel to $channel, resetting state")
            _messagesState.value = MessagesState.Loading
            loadedMessages.clear()
            messageIds.clear()
            lastKnownId = "0"
            currentPage = 0
            currentChannel = channel
        } else if (!isLoadMore && _messagesState.value is MessagesState.Success) {
            android.util.Log.d("MessageViewModel", "Messages already loaded for channel: $channel, skipping")
            return
        }

        if (!isLoadMore) {
            _messagesState.value = MessagesState.Loading
        }

        viewModelScope.launch {
            try {
                if (!isLoadMore) {
                    val result = repository.getMessages(channel, lastKnownId = "0")
                    if (result.isSuccess) {
                        val newMessages = result.getOrDefault(emptyList())
                        android.util.Log.d("MessageViewModel", "Received ${newMessages.size} messages from repository (initial)")

                        loadedMessages.clear()
                        loadedMessages.addAll(newMessages)

                        messageIds.clear()
                        newMessages.forEach { messageIds.add(it.id) }

                        if (newMessages.isNotEmpty()) {
                            lastKnownId = newMessages.first().id
                            android.util.Log.d("MessageViewModel", "Set lastKnownId for pagination: $lastKnownId")
                        }

                        _messagesState.value = MessagesState.Success(loadedMessages.toList())
                        android.util.Log.d("MessageViewModel", "Loaded ${loadedMessages.size} messages for channel: $channel")
                    } else {
                        _messagesState.value = MessagesState.Error(result.exceptionOrNull()?.message ?: "Неизвестная ошибка")
                    }
                } else {
                    val currentFirstId = if (loadedMessages.isNotEmpty()) loadedMessages.first().id else "0"
                    if (currentFirstId == "0") {
                        android.util.Log.d("MessageViewModel", "No more messages to load (no first id)")
                        return@launch
                    }

                    android.util.Log.d("MessageViewModel", "Loading more messages before id=$currentFirstId for channel=$channel, current count: ${loadedMessages.size}")

                    val result = repository.getMessages(channel, limit = 20, lastKnownId = currentFirstId, reverse = true)
                    if (result.isSuccess) {
                        val olderMessages = result.getOrDefault(emptyList())
                        android.util.Log.d("MessageViewModel", "Received ${olderMessages.size} older messages from repository")

                        if (olderMessages.isEmpty()) {
                            android.util.Log.d("MessageViewModel", "No more older messages available")
                            return@launch
                        }


                        val unique = olderMessages.filter { !messageIds.contains(it.id) }
                        android.util.Log.d("MessageViewModel", "Unique older messages to add: ${unique.size}")

                        unique.forEach { messageIds.add(it.id) }
                        loadedMessages.addAll(0, unique)

                        if (unique.isNotEmpty()) {
                            lastKnownId = loadedMessages.first().id
                            android.util.Log.d("MessageViewModel", "Updated lastKnownId to: $lastKnownId")
                        }

                        _messagesState.value = MessagesState.Success(loadedMessages.toList())
                        android.util.Log.d("MessageViewModel", "Added ${unique.size} older messages, total: ${loadedMessages.size}")
                    } else {
                        android.util.Log.e("MessageViewModel", "Failed to load older messages: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageViewModel", "Error loading messages", e)
                _messagesState.value = MessagesState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun sendMessage(text: String, username: String) {
        val channel = currentChannel ?: return
        if (text.isBlank()) return

        _sendState.value = SendState.Loading
        val currentTimeMillis = System.currentTimeMillis()
        val messageRequest = SendMessageRequest(
            from = username,
            to = channel,
            data = MessageData.Text(text),
            time = currentTimeMillis
        )

        val optimisticId = "temp_${currentTimeMillis}"
        val optimisticMessage = Message(
            id = optimisticId,
            from = username,
            to = normalize(channel),
            data = MessageData.Text(text),
            time = currentTimeMillis / 1000
        )

        messageIds.add(optimisticId)
        loadedMessages.add(optimisticMessage)
        _messagesState.value = MessagesState.Success(loadedMessages.toList())
        android.util.Log.d("MessageViewModel", "Added optimistic message with id=$optimisticId")

        viewModelScope.launch {
            val result = repository.sendMessage(messageRequest)
            if (result.isSuccess) {
                val realMessageId = result.getOrNull() ?: optimisticId
                android.util.Log.d("MessageViewModel", "Message sent successfully, id=$realMessageId")

                messageIds.remove(optimisticId)
                loadedMessages.removeAll { it.id == optimisticId }

                android.util.Log.d("MessageViewModel", "Reloading messages from server after send")
                val reloadResult = repository.getMessages(channel, lastKnownId = "0")
                if (reloadResult.isSuccess) {
                    val freshMessages = reloadResult.getOrDefault(emptyList())
                    messageIds.clear()
                    loadedMessages.clear()
                    loadedMessages.addAll(freshMessages)
                    freshMessages.forEach { messageIds.add(it.id) }
                    if (freshMessages.isNotEmpty()) {
                        lastKnownId = freshMessages.first().id
                    }
                    _messagesState.value = MessagesState.Success(loadedMessages.toList())
                    android.util.Log.d("MessageViewModel", "Messages reloaded after send, count: ${loadedMessages.size}")
                } else {
                    _messagesState.value = MessagesState.Success(loadedMessages.toList())
                }

                _sendState.value = SendState.Success
            } else {

                messageIds.remove(optimisticId)
                loadedMessages.removeAll { it.id == optimisticId }
                _messagesState.value = MessagesState.Success(loadedMessages.toList())

                _sendState.value = SendState.Error(result.exceptionOrNull()?.message ?: "Не удалось отправить сообщение")
            }
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }
}

sealed class MessagesState {
    data object Loading : MessagesState()
    data class Success(val messages: List<Message>) : MessagesState()
    data class Error(val message: String) : MessagesState()
}

sealed class SendState {
    data object Idle : SendState()
    data object Loading : SendState()
    data object Success : SendState()
    data class Error(val message: String) : SendState()
}
