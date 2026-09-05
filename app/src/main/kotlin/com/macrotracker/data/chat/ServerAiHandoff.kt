package com.macrotracker.data.chat

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a server context block from the Servers dashboard to the AI tab.
 *
 * The payload is a few kilobytes of text — far too big for a nav argument, which
 * ends up in the back-stack bundle and the URL — so only [Payload.id] travels on
 * the route and the body is picked up here exactly once.
 */
@Singleton
class ServerAiHandoff @Inject constructor() {

    data class Payload(
        val id: String,
        val context: String,
        val openingQuestion: String,
    )

    private val pending = mutableMapOf<String, Payload>()

    fun offer(context: String, openingQuestion: String): String {
        val payload = Payload(UUID.randomUUID().toString(), context, openingQuestion)
        // One in flight is all a tap can produce; anything older was abandoned.
        pending.clear()
        pending[payload.id] = payload
        return payload.id
    }

    /** Consuming is destructive so a config change can't re-fire the same thread. */
    fun consume(id: String?): Payload? = id?.let { pending.remove(it) }
}
