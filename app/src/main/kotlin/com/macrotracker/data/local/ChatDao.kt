package com.macrotracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: ChatThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_threads WHERE botId = :botId ORDER BY updatedAtMs DESC")
    fun observeThreads(botId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE botId = :botId ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun newestThread(botId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAtMs ASC")
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAtMs ASC")
    suspend fun messagesFor(threadId: String): List<ChatMessageEntity>

    @Query("UPDATE chat_threads SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :threadId")
    suspend fun touchThread(threadId: String, title: String, updatedAtMs: Long)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesFor(threadId: String)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)

    /**
     * Drops the oldest threads for a bot beyond [keep]. Chat history is a convenience,
     * not an archive — an unbounded table on a phone is a slow leak.
     */
    @Query(
        """
        DELETE FROM chat_threads
        WHERE botId = :botId
          AND id NOT IN (
              SELECT id FROM chat_threads WHERE botId = :botId
              ORDER BY updatedAtMs DESC LIMIT :keep
          )
        """,
    )
    suspend fun trimThreads(botId: String, keep: Int)

    /** Messages whose thread is gone — cleaned up after [trimThreads]. */
    @Query("DELETE FROM chat_messages WHERE threadId NOT IN (SELECT id FROM chat_threads)")
    suspend fun deleteOrphanedMessages()
}
