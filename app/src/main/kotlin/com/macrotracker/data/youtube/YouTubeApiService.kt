package com.macrotracker.data.youtube

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    /** Search for the latest videos from a specific channel. */
    @GET("search")
    suspend fun searchChannelVideos(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("order") order: String = "date",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String,
    ): YouTubeSearchResponse

    /** Get channel details by channel ID. */
    @GET("channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") channelId: String,
        @Query("key") apiKey: String,
    ): YouTubeChannelListResponse

    /** Search for channels by query text. */
    @GET("search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "channel",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String,
    ): YouTubeSearchResponse

    /** Get user subscriptions (requires OAuth token). */
    @GET("subscriptions")
    suspend fun getMySubscriptions(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String,
        @retrofit2.http.Header("Authorization") bearerToken: String,
    ): YouTubeSubscriptionsResponse
}

