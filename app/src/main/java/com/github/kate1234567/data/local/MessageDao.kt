package com.github.kate1234567.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY time ASC")
    fun getMessagesByChannel(channel: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY time ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByChannelPagedSync(channel: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY time ASC")
    suspend fun getMessagesByChannelSync(channel: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE channel = :channel")
    suspend fun deleteMessagesByChannel(channel: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
