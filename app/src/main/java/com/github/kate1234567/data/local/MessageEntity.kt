package com.github.kate1234567.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.kate1234567.data.model.Message
import com.github.kate1234567.data.model.MessageData


@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val from: String,
    val to: String?,
    val dataType: String,
    val dataContent: String,
    val time: Long,
    val channel: String
)

fun MessageEntity.toMessage(): Message {
    val data = when (dataType) {
        "text" -> MessageData.Text(dataContent)
        "image" -> MessageData.Image(dataContent.takeIf { it.isNotBlank() })
        else -> MessageData.Text("")
    }
    return Message(id, from, to, data, time)
}

fun Message.toEntity(channel: String): MessageEntity {
    val (dataType, dataContent) = when (data) {
        is MessageData.Text -> "text" to data.text
        is MessageData.Image -> "image" to (data.link ?: "")
    }
    return MessageEntity(id, from, to, dataType, dataContent, time, channel)
}

