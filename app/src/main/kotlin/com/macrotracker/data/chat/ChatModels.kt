package com.macrotracker.data.chat

import java.util.UUID

/** Which bot a thread belongs to. The id is persisted, so don't rename it. */
enum class ChatBot(val id: String, val displayName: String) {
    MACROS("macros", "Clanker"),
    SYSOP("sysop", "Sysop");

    companion object {
        fun fromId(value: String?): ChatBot = entries.find { it.id == value } ?: MACROS
    }
}

enum class ChatRole { USER, ASSISTANT }

/** One turn as the model sees it — no ids, no UI state, no timestamps. */
data class ChatTurn(
    val role: ChatRole,
    val text: String,
    val imageBase64: String? = null,
)

/** One message as the UI sees it. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val role: ChatRole,
    val text: String,
    val imageBase64: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    /** Set when the turn failed; [text] then holds the reason. */
    val isError: Boolean = false,
    /** Retry target for an errored turn: the user text that produced it. */
    val retryText: String? = null,
    val showSettingsCta: Boolean = false,
    /**
     * Context attached to the *first* user turn of a thread opened from the server
     * dashboard. Sent to the model, never rendered as a bubble.
     */
    val hiddenContext: String? = null,
)

data class ChatThread(
    val id: String = UUID.randomUUID().toString(),
    val bot: ChatBot,
    val title: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)

/** What [AiChatClient] emits while a reply streams in. */
sealed interface ChatDelta {
    data class Text(val chunk: String) : ChatDelta
    data object Done : ChatDelta
    data class Failed(
        val message: String,
        val showSettingsCta: Boolean = false,
    ) : ChatDelta
}
