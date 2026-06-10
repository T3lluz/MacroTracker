package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.youtube.YoutubeChannel
import com.macrotracker.data.youtube.YoutubeVideo
import com.macrotracker.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

sealed class YouTubeUiState {
    data object Idle : YouTubeUiState()
    data object Loading : YouTubeUiState()
    data class Success(val videos: List<YoutubeVideo>, val lastUpdatedAt: Instant? = null) : YouTubeUiState()
    data class Error(val message: String) : YouTubeUiState()
    data object NoChannels : YouTubeUiState()
}

sealed class ChannelSearchState {
    data object Idle : ChannelSearchState()
    data object Loading : ChannelSearchState()
    data class Success(val channels: List<YoutubeChannel>) : ChannelSearchState()
    data class Error(val message: String) : ChannelSearchState()
}

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "YouTubeViewModel"
        private const val SEARCH_DEBOUNCE_MS = 400L
    }

    private val _youtubeState = MutableStateFlow<YouTubeUiState>(YouTubeUiState.Idle)
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState

    private val _channelSearchState = MutableStateFlow<ChannelSearchState>(ChannelSearchState.Idle)
    val channelSearchState: StateFlow<ChannelSearchState> = _channelSearchState

    private val _trackedChannels = MutableStateFlow<List<YoutubeChannel>>(emptyList())
    val trackedChannels: StateFlow<List<YoutubeChannel>> = _trackedChannels

    /** Channel IDs that were just added — used to briefly show a checkmark on the button. */
    private val _recentlyAdded = MutableStateFlow<Set<String>>(emptySet())
    val recentlyAdded: StateFlow<Set<String>> = _recentlyAdded

    /**
     * Live search suggestions shown while the user types (debounced).
     * These are lightweight results shown as a dropdown / inline preview.
     */
    private val _searchSuggestions = MutableStateFlow<List<YoutubeChannel>>(emptyList())
    val searchSuggestions: StateFlow<List<YoutubeChannel>> = _searchSuggestions

    /** Whether the suggestions dropdown is actively loading */
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading

    private var debounceJob: Job? = null

    init {
        loadTrackedChannels()
        loadLatestVideos()
    }

    fun loadTrackedChannels() {
        _trackedChannels.value = youtubeRepository.getTrackedChannels()
    }

    fun loadLatestVideos(forceRefresh: Boolean = false) {
        if (forceRefresh) youtubeRepository.invalidateCache()
        val tracked = youtubeRepository.getTrackedChannels()
        if (tracked.isEmpty()) {
            _youtubeState.value = YouTubeUiState.NoChannels
            return
        }
        val current = _youtubeState.value
        viewModelScope.launch {
            if (current !is YouTubeUiState.Success || forceRefresh) {
                _youtubeState.value = YouTubeUiState.Loading
            }
            youtubeRepository.getLatestVideosForTrackedChannels()
                .onSuccess { videos ->
                    _youtubeState.value = if (videos.isEmpty()) YouTubeUiState.NoChannels
                    else YouTubeUiState.Success(videos, lastUpdatedAt = Instant.now())
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load YouTube videos", e)
                    _youtubeState.value = YouTubeUiState.Error(e.message ?: "Unknown error")
                }
        }
    }

    /** Full search — called when user presses Search / Enter. */
    fun searchChannels(query: String) {
        if (query.isBlank()) { _channelSearchState.value = ChannelSearchState.Idle; return }
        debounceJob?.cancel()
        viewModelScope.launch {
            _channelSearchState.value = ChannelSearchState.Loading
            _searchSuggestions.value = emptyList()
            youtubeRepository.searchChannels(query)
                .onSuccess { _channelSearchState.value = ChannelSearchState.Success(it) }
                .onFailure { _channelSearchState.value = ChannelSearchState.Error(it.message ?: "Search failed") }
        }
    }

    /**
     * Debounced live-search used to populate inline suggestions while the user types.
     * Results are emitted to [searchSuggestions] without affecting [channelSearchState].
     */
    fun onSearchQueryChanged(query: String) {
        debounceJob?.cancel()
        if (query.length < 2) {
            _searchSuggestions.value = emptyList()
            _suggestionsLoading.value = false
            return
        }
        debounceJob = viewModelScope.launch {
            _suggestionsLoading.value = true
            delay(SEARCH_DEBOUNCE_MS)
            youtubeRepository.searchChannels(query)
                .onSuccess { channels ->
                    _searchSuggestions.value = channels.take(5).map {
                        it.copy(isTracked = youtubeRepository.isChannelTracked(it.channelId))
                    }
                }
                .onFailure { _searchSuggestions.value = emptyList() }
            _suggestionsLoading.value = false
        }
    }

    fun clearSearchSuggestions() {
        debounceJob?.cancel()
        _searchSuggestions.value = emptyList()
        _suggestionsLoading.value = false
    }

    fun clearChannelSearch() {
        _channelSearchState.value = ChannelSearchState.Idle
        clearSearchSuggestions()
    }


    fun addChannel(channel: YoutubeChannel) {
        youtubeRepository.addTrackedChannel(channel)
        loadTrackedChannels()
        loadLatestVideos(forceRefresh = true)
        viewModelScope.launch {
            _recentlyAdded.value = _recentlyAdded.value + channel.channelId
            delay(1500)
            _recentlyAdded.value = _recentlyAdded.value - channel.channelId
        }
    }

    fun removeChannel(channelId: String) {
        youtubeRepository.removeTrackedChannel(channelId)
        loadTrackedChannels()
        loadLatestVideos(forceRefresh = true)
    }

    fun isChannelTracked(channelId: String) = youtubeRepository.isChannelTracked(channelId)
}
