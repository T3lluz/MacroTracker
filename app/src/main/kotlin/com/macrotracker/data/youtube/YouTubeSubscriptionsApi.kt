package com.macrotracker.data.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Data API v3 — authenticated subscription list.
 * Requires a user OAuth access token with `youtube.readonly`.
 */
@Singleton
class YouTubeSubscriptionsApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "YouTubeSubscriptionsApi"
        private const val BASE =
            "https://www.googleapis.com/youtube/v3/subscriptions" +
                "?part=snippet&mine=true&maxResults=50"
    }

    suspend fun listMine(accessToken: String): Result<List<YoutubeChannel>> =
        withContext(Dispatchers.IO) {
            try {
                val channels = mutableListOf<YoutubeChannel>()
                var pageToken: String? = null
                var pages = 0
                do {
                    pages++
                    if (pages > 40) {
                        // Hard stop (~2000 channels) to avoid runaway loops / quota burn.
                        Log.w(TAG, "Stopping subscription pagination after $pages pages")
                        break
                    }
                    val url = buildString {
                        append(BASE)
                        if (!pageToken.isNullOrBlank()) {
                            append("&pageToken=").append(pageToken)
                        }
                    }
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $accessToken")
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val message = parseApiError(body)
                                ?: "YouTube API error ${response.code}"
                            return@withContext Result.failure(Exception(message))
                        }
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: return@use
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val snippet = item.optJSONObject("snippet") ?: continue
                            val resourceId = snippet.optJSONObject("resourceId") ?: continue
                            val channelId = resourceId.optString("channelId").orEmpty()
                            if (channelId.isBlank()) continue
                            val title = snippet.optString("title").ifBlank { channelId }
                            val thumbs = snippet.optJSONObject("thumbnails")
                            val thumbUrl = thumbs
                                ?.optJSONObject("medium")?.optString("url")
                                ?.takeIf { it.isNotBlank() }
                                ?: thumbs?.optJSONObject("default")?.optString("url").orEmpty()
                            channels += YoutubeChannel(
                                channelId = channelId,
                                title = title,
                                thumbnailUrl = thumbUrl,
                                isTracked = false,
                            )
                        }
                        pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
                    }
                } while (pageToken != null)

                Result.success(channels.distinctBy { it.channelId })
            } catch (e: Exception) {
                Log.e(TAG, "listMine failed", e)
                Result.failure(e)
            }
        }

    private fun parseApiError(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val error = JSONObject(body).optJSONObject("error") ?: return null
            val message = error.optString("message").takeIf { it.isNotBlank() }
            val reason = error.optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("reason")
                ?.takeIf { it.isNotBlank() }
            when {
                reason == "accessNotConfigured" ||
                    message?.contains("has not been used", ignoreCase = true) == true ->
                    "YouTube Data API is not enabled in Google Cloud Console"
                reason == "quotaExceeded" ->
                    "YouTube API quota exceeded — try again later"
                reason == "insufficientPermissions" || reason == "authError" ->
                    "YouTube permission missing — reconnect your Google account"
                else -> message
            }
        } catch (_: Exception) {
            null
        }
    }
}
