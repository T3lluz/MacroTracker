package com.macrotracker.data.youtube

// ── App-level domain models ───────────────────────────────────────────────────

data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelId: String,
    val publishedAt: String,
    val thumbnailUrl: String,
)

data class YoutubeChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String,
    val subscriberCount: String? = null,
    /** Whether the user has added this channel to their watch list */
    val isTracked: Boolean = false,
)
