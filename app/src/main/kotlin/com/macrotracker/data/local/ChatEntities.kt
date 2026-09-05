package com.macrotracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A chat thread. `botId` is [com.macrotracker.data.chat.ChatBot.id] — stored as a
 * plain string so adding a third bot never needs a migration.
 */
@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val botId: String,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "chat_messages",
    indices = [Index("threadId")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    /** "USER" or "ASSISTANT". */
    val role: String,
    val text: String,
    val createdAtMs: Long,
    val isError: Boolean = false,
    val retryText: String? = null,
    /**
     * The server snapshot attached to a dashboard hand-off. Sent to the model, never
     * rendered. Images are not persisted — a base64 meal photo would bloat the row
     * for no benefit once the estimate is already in the transcript.
     */
    val hiddenContext: String? = null,
)
