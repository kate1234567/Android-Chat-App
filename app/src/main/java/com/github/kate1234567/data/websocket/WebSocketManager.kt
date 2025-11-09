package com.github.kate1234567.data.websocket

import android.util.Log
import com.github.kate1234567.data.model.Message
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val gson: Gson,
    private val okHttpClient: OkHttpClient
) {

    private var webSocket: WebSocket? = null

    private val _newMessages = MutableSharedFlow<Message>()
    val newMessages: SharedFlow<Message> = _newMessages

    fun connect(username: String, token: String) {
        val request = Request.Builder()
            .url("wss://faerytea.name/ws/$username?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                try {
                    val json = gson.fromJson(text, Map::class.java)
                    if (json != null && json.containsKey("NewMessage")) {
                        val newMessageObj = json["NewMessage"]
                        if (newMessageObj is Map<*, *> && newMessageObj.containsKey("msg")) {
                            val msgJson = newMessageObj["msg"]
                            if (msgJson is Map<*, *>) {
                                val message = gson.fromJson(gson.toJson(msgJson), Message::class.java)
                                CoroutineScope(Dispatchers.IO).launch {
                                    _newMessages.emit(message)
                                }
                            } else {
                                Log.w("WebSocket", "msg is not a Map: $msgJson")
                            }
                        } else {
                            Log.w("WebSocket", "NewMessage format incorrect: $newMessageObj")
                        }
                    } else {
                        Log.d("WebSocket", "Message doesn't contain NewMessage or json is null")
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing message: $text", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Failure", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Closing")
        webSocket = null
    }
}
