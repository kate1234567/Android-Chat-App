package com.github.kate1234567.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class, ChannelEntity::class], version = 3, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun channelDao(): ChannelDao
}

