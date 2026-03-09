package com.macrotracker.data.youtube

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface YouTubeRepository {
    val apiKeyFlow: StateFlow<String>
    fun getApiKey(): String
    fun saveApiKey(key: String)
    suspend fun getLatestVideosForTrackedChannels(): Result<List<YoutubeVideo>>
    suspend fun searchChannels(query: String): Result<List<YoutubeChannel>>
    suspend fun getMySubscriptions(accessToken: String): Result<List<YoutubeChannel>>
    fun getTrackedChannels(): List<YoutubeChannel>
    fun addTrackedChannel(channel: YoutubeChannel)
    fun removeTrackedChannel(channelId: String)
    fun isChannelTracked(channelId: String): Boolean
    fun invalidateCache()
}

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val api: YouTubeApiService,
    @ApplicationContext private val context: Context,
) : YouTubeRepository {

    companion object {
        private const val TAG = "YouTubeRepository"
        private const val PREFS_NAME = "youtube_settings"
        private const val KEY_TRACKED_CHANNELS = "tracked_channel_ids"
        private const val KEY_CHANNEL_TITLE_PREFIX = "channel_title_"
        private const val KEY_CHANNEL_THUMB_PREFIX = "channel_thumb_"
        private const val KEY_API_KEY = "youtube_api_key"
        private const val CACHE_DURATION = 10 * 60 * 1000L // 10 min
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val _apiKeyFlow = MutableStateFlow(prefs.getString(KEY_API_KEY, "") ?: "")
    override val apiKeyFlow: StateFlow<String> = _apiKeyFlow

    override fun getApiKey(): String = _apiKeyFlow.value

    override fun saveApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit { putString(KEY_API_KEY, trimmed) }
        _apiKeyFlow.value = trimmed
        invalidateCache()
    }

    // In-memory cache
    private var cachedVideos: List<YoutubeVideo>? = null
    private var lastFetchTime: Long = 0

    override fun invalidateCache() {
        cachedVideos = null
        lastFetchTime = 0
    }

    override fun getTrackedChannels(): List<YoutubeChannel> {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet()) ?: emptySet()
        return ids.map { id ->
            YoutubeChannel(
                channelId = id,
                title = prefs.getString("$KEY_CHANNEL_TITLE_PREFIX$id", id) ?: id,
                thumbnailUrl = prefs.getString("$KEY_CHANNEL_THUMB_PREFIX$id", "") ?: "",
                isTracked = true,
            )
        }
    }

    override fun addTrackedChannel(channel: YoutubeChannel) {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(channel.channelId)
        prefs.edit {
            putStringSet(KEY_TRACKED_CHANNELS, ids)
            putString("$KEY_CHANNEL_TITLE_PREFIX${channel.channelId}", channel.title)
            putString("$KEY_CHANNEL_THUMB_PREFIX${channel.channelId}", channel.thumbnailUrl)
        }
        invalidateCache()
    }

    override fun removeTrackedChannel(channelId: String) {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(channelId)
        prefs.edit {
            putStringSet(KEY_TRACKED_CHANNELS, ids)
            remove("$KEY_CHANNEL_TITLE_PREFIX$channelId")
            remove("$KEY_CHANNEL_THUMB_PREFIX$channelId")
        }
        invalidateCache()
    }

    override fun isChannelTracked(channelId: String): Boolean {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet()) ?: emptySet()
        return ids.contains(channelId)
    }

    override suspend fun getLatestVideosForTrackedChannels(): Result<List<YoutubeVideo>> =
        withContext(Dispatchers.IO) {
            val key = getApiKey()
            if (key.isBlank()) return@withContext Result.failure(Exception("NO_API_KEY"))

            val now = System.currentTimeMillis()
            if (cachedVideos != null && (now - lastFetchTime) < CACHE_DURATION) {
                return@withContext Result.success(cachedVideos!!)
            }

            val trackedChannels = getTrackedChannels()
            if (trackedChannels.isEmpty()) return@withContext Result.success(emptyList())

            try {
                val jobs = trackedChannels.map { channel ->
                    async {
                        try {
                            api.searchChannelVideos(channelId = channel.channelId, apiKey = key)
                                .items.mapNotNull { item ->
                                    val vid = item.id.videoId ?: return@mapNotNull null
                                    YoutubeVideo(
                                        videoId = vid,
                                        title = item.snippet.title,
                                        channelTitle = item.snippet.channelTitle,
                                        channelId = item.snippet.channelId,
                                        publishedAt = item.snippet.publishedAt,
                                        thumbnailUrl = item.snippet.thumbnails.medium?.url
                                            ?: item.snippet.thumbnails.default?.url ?: "",
                                    )
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch videos for ${channel.channelId}", e)
                            emptyList()
                        }
                    }
                }
                val all = jobs.awaitAll().flatten().sortedByDescending { it.publishedAt }
                cachedVideos = all
                lastFetchTime = System.currentTimeMillis()
                Result.success(all)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YouTube videos", e)
                Result.failure(e)
            }
        }

    override suspend fun searchChannels(query: String): Result<List<YoutubeChannel>> =
        withContext(Dispatchers.IO) {
            val key = getApiKey()
            if (key.isBlank()) return@withContext Result.failure(Exception("NO_API_KEY"))
            try {
                val response = api.searchChannels(query = query, apiKey = key)
                val channels = response.items.mapNotNull { item ->
                    val channelId = item.snippet.channelId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    YoutubeChannel(
                        channelId = channelId,
                        title = item.snippet.channelTitle,
                        thumbnailUrl = item.snippet.thumbnails.medium?.url
                            ?: item.snippet.thumbnails.default?.url ?: "",
                        isTracked = isChannelTracked(channelId),
                    )
                }
                Result.success(channels)
            } catch (e: Exception) {
                Log.e(TAG, "Channel search failed", e)
                Result.failure(e)
            }
        }

    override suspend fun getMySubscriptions(accessToken: String): Result<List<YoutubeChannel>> =
        withContext(Dispatchers.IO) {
            val key = getApiKey()
            if (key.isBlank()) return@withContext Result.failure(Exception("NO_API_KEY"))
            try {
                val response = api.getMySubscriptions(apiKey = key, bearerToken = "Bearer $accessToken")
                val channels = response.items.map { item ->
                    val channelId = item.snippet.resourceId.channelId
                    YoutubeChannel(
                        channelId = channelId,
                        title = item.snippet.title,
                        thumbnailUrl = item.snippet.thumbnails.medium?.url
                            ?: item.snippet.thumbnails.default?.url ?: "",
                        isTracked = isChannelTracked(channelId),
                    )
                }
                Result.success(channels)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch subscriptions", e)
                Result.failure(e)
            }
        }
}
