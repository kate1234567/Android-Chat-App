package com.github.kate1234567.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.github.kate1234567.data.api.ChatApiService
import com.github.kate1234567.data.api.StringConverterFactory
import com.github.kate1234567.data.local.ChannelDao
import com.github.kate1234567.data.local.ChatDatabase
import com.github.kate1234567.data.local.MessageDao
import com.github.kate1234567.data.model.Message
import com.github.kate1234567.data.model.MessageData
import com.github.kate1234567.data.model.SendMessageRequest
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Message::class.java, MessageDeserializer())
            .registerTypeAdapter(SendMessageRequest::class.java, SendMessageRequestSerializer())
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://faerytea.name/")
            .client(okHttpClient)
            .addConverterFactory(StringConverterFactory())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideChatApiService(retrofit: Retrofit): ChatApiService {
        return retrofit.create(ChatApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ChatDatabase::class.java,
            "chat_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: ChatDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideChannelDao(database: ChatDatabase): ChannelDao {
        return database.channelDao()
    }
}

class MessageDeserializer : JsonDeserializer<Message> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Message {
        val jsonObject = json.asJsonObject
        val id = jsonObject.get("id").asString
        val from = jsonObject.get("from").asString
        val to = jsonObject.get("to")?.asString
        val time = jsonObject.get("time").asLong

        val dataObject = jsonObject.get("data").asJsonObject
        val messageData = when {
            dataObject.has("Text") -> {
                val text = dataObject.getAsJsonObject("Text").get("text").asString
                MessageData.Text(text)
            }
            dataObject.has("Image") -> {
                val link = dataObject.getAsJsonObject("Image").get("link")?.asString
                MessageData.Image(link)
            }
            else -> throw IllegalArgumentException("Unknown message data type")
        }

        return Message(id, from, to, messageData, time)
    }
}

class SendMessageRequestSerializer : com.google.gson.JsonSerializer<SendMessageRequest> {
    override fun serialize(
        src: SendMessageRequest,
        typeOfSrc: Type,
        context: com.google.gson.JsonSerializationContext
    ): JsonElement {
        val jsonObject = com.google.gson.JsonObject()
        jsonObject.addProperty("from", src.from)
        jsonObject.addProperty("to", src.to)

        val dataObject = com.google.gson.JsonObject()
        when (val data = src.data) {
            is MessageData.Text -> {
                val textObject = com.google.gson.JsonObject()
                textObject.addProperty("text", data.text)
                dataObject.add("Text", textObject)
            }
            is MessageData.Image -> {
                val imageObject = com.google.gson.JsonObject()
                imageObject.addProperty("link", data.link)
                dataObject.add("Image", imageObject)
            }
        }
        jsonObject.add("data", dataObject)

        src.time?.let { jsonObject.addProperty("time", it) }

        return jsonObject
    }
}

