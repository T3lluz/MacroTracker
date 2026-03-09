package com.macrotracker.data.youtube

import com.google.gson.annotations.SerializedName

// ── YouTube Data API v3 – Response Models ─────────────────────────────────────

data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeSearchItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
)

data class YouTubeSearchItem(
    @SerializedName("id") val id: YouTubeSearchItemId,
    @SerializedName("snippet") val snippet: YouTubeSnippet,
)

data class YouTubeSearchItemId(
    @SerializedName("kind") val kind: String = "",
    @SerializedName("videoId") val videoId: String? = null,
)

data class YouTubeSnippet(
    @SerializedName("publishedAt") val publishedAt: String = "",
    @SerializedName("channelId") val channelId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails = YouTubeThumbnails(),
    @SerializedName("channelTitle") val channelTitle: String = "",
)

data class YouTubeThumbnails(
    @SerializedName("default") val default: YouTubeThumbnail? = null,
    @SerializedName("medium") val medium: YouTubeThumbnail? = null,
    @SerializedName("high") val high: YouTubeThumbnail? = null,
)

data class YouTubeThumbnail(
    @SerializedName("url") val url: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)

// ── Channel Search ────────────────────────────────────────────────────────────

data class YouTubeChannelListResponse(
    @SerializedName("items") val items: List<YouTubeChannelItem> = emptyList(),
)

data class YouTubeChannelItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("snippet") val snippet: YouTubeChannelSnippet = YouTubeChannelSnippet(),
    @SerializedName("statistics") val statistics: YouTubeChannelStatistics? = null,
)

data class YouTubeChannelSnippet(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("customUrl") val customUrl: String? = null,
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails = YouTubeThumbnails(),
    @SerializedName("country") val country: String? = null,
)

data class YouTubeChannelStatistics(
    @SerializedName("subscriberCount") val subscriberCount: String? = null,
    @SerializedName("videoCount") val videoCount: String? = null,
)

// ── Subscriptions ─────────────────────────────────────────────────────────────

data class YouTubeSubscriptionsResponse(
    @SerializedName("items") val items: List<YouTubeSubscriptionItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
    @SerializedName("pageInfo") val pageInfo: YouTubePageInfo? = null,
)

data class YouTubeSubscriptionItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("snippet") val snippet: YouTubeSubscriptionSnippet = YouTubeSubscriptionSnippet(),
)

data class YouTubeSubscriptionSnippet(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("resourceId") val resourceId: YouTubeResourceId = YouTubeResourceId(),
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails = YouTubeThumbnails(),
)

data class YouTubeResourceId(
    @SerializedName("kind") val kind: String = "",
    @SerializedName("channelId") val channelId: String = "",
)

data class YouTubePageInfo(
    @SerializedName("totalResults") val totalResults: Int = 0,
    @SerializedName("resultsPerPage") val resultsPerPage: Int = 0,
)

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

