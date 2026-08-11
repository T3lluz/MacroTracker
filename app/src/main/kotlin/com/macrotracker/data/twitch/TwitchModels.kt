package com.macrotracker.data.twitch

data class TwitchStream(
    val streamId: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Int,
    val startedAt: String,
    val thumbnailUrl: String,
    val profileImageUrl: String = "",
) {
    fun thumbnail(width: Int = 440, height: Int = 248): String =
        thumbnailUrl
            .replace("{width}", width.toString())
            .replace("{height}", height.toString())
            .ifBlank {
                "https://static-cdn.jtvnw.net/previews-ttv/live_user_$userLogin-${width}x$height.jpg"
            }

    val channelUrl: String get() = "https://www.twitch.tv/$userLogin"
}

data class TwitchChannel(
    val userId: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String,
    val isLive: Boolean = false,
    val isTracked: Boolean = false,
) {
    val channelUrl: String get() = "https://www.twitch.tv/$login"
}

data class FollowImportResult(
    val importedCount: Int,
    val totalFollows: Int,
    val watchingCount: Int,
)
