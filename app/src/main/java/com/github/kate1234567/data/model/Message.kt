package com.github.kate1234567.data.model

data class Message(
    val id: String,
    val from: String,
    val to: String? = null,
    val data: MessageData,
    val time: Long
)

sealed class MessageData {
    data class Text(val text: String) : MessageData()
    data class Image(val link: String? = null) : MessageData()
}

data class SendMessageRequest(
    val from: String,
    val to: String? = null,
    val data: MessageData,
    val time: Long? = null
)
