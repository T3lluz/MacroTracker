package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.youtube.GoogleSignInManager
import com.macrotracker.data.youtube.GoogleSignInState
import com.macrotracker.data.youtube.YoutubeChannel
import com.macrotracker.data.youtube.YoutubeVideo
import com.macrotracker.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class YouTubeUiState {
    data object Idle : YouTubeUiState()
    data object Loading : YouTubeUiState()
    data class Success(val videos: List<YoutubeVideo>) : YouTubeUiState()
    data class Error(val message: String) : YouTubeUiState()
    data object NoChannels : YouTubeUiState()
    data object NoApiKey : YouTubeUiState()
}

sealed class ChannelSearchState {
    data object Idle : ChannelSearchState()
    data object Loading : ChannelSearchState()
    data class Success(val channels: List<YoutubeChannel>) : ChannelSearchState()
    data class Error(val message: String) : ChannelSearchState()
}

data class ChannelCategory(val name: String, val emoji: String, val channels: List<YoutubeChannel>)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    val googleSignInManager: GoogleSignInManager,
) : ViewModel() {

    companion object {
        private const val TAG = "YouTubeViewModel"
    }

    val signInState: StateFlow<GoogleSignInState> = googleSignInManager.signInState
        .stateIn(viewModelScope, SharingStarted.Eagerly, GoogleSignInState())

    val apiKey: StateFlow<String> = youtubeRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, youtubeRepository.getApiKey())

    fun saveApiKey(key: String) {
        youtubeRepository.saveApiKey(key)
        loadLatestVideos(forceRefresh = true)
    }

    private val _youtubeState = MutableStateFlow<YouTubeUiState>(YouTubeUiState.Idle)
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState

    private val _channelSearchState = MutableStateFlow<ChannelSearchState>(ChannelSearchState.Idle)
    val channelSearchState: StateFlow<ChannelSearchState> = _channelSearchState

    private val _trackedChannels = MutableStateFlow<List<YoutubeChannel>>(emptyList())
    val trackedChannels: StateFlow<List<YoutubeChannel>> = _trackedChannels

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError

    /** Channel IDs that were just added — used to briefly show a checkmark on the button. */
    private val _recentlyAdded = MutableStateFlow<Set<String>>(emptySet())
    val recentlyAdded: StateFlow<Set<String>> = _recentlyAdded

    /** Curated popular channels grouped by category for the Browse tab. */
    val browseCategories: List<ChannelCategory> = listOf(
        ChannelCategory("Tech", "💻", listOf(
            YoutubeChannel("UCBcRF18a7Qf58cCRy5xuWwQ", "MKBHD", ""),
            YoutubeChannel("UCXuqSBlHAE6Xw-yeJA0Tunw", "Linus Tech Tips", ""),
            YoutubeChannel("UCVhQ2NnY5Rskt6UjCUkJ_DA", "The Verge", ""),
            YoutubeChannel("UC0RhatS1pyxInC00YKjjBqQ", "Marques Brownlee", ""),
            YoutubeChannel("UCddiUEpeqJcYeBxX1IVBKvQ", "The Primeagen", ""),
            YoutubeChannel("UCs6nmQViDpUw0nuIx9c_WDQ", "Fireship", ""),
        )),
        ChannelCategory("Science", "🔬", listOf(
            YoutubeChannel("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt", ""),
            YoutubeChannel("UCHnyfMqiRRG1u-2MsSQLbXA", "Veritasium", ""),
            YoutubeChannel("UC7_gcs09iThXybpVgjHZ_7g", "PBS Space Time", ""),
            YoutubeChannel("UCYO_jab_esuFRV4b17AJtAg", "3Blue1Brown", ""),
            YoutubeChannel("UCZYTClx2T1of7BRZ86-8fow", "SciShow", ""),
        )),
        ChannelCategory("Gaming", "🎮", listOf(
            YoutubeChannel("UCam8T03EOFBsNdR0thrFHdQ", "GameLinked", ""),
            YoutubeChannel("UCTkXRDQl0luXxVQrRQvWS6w", "IGN", ""),
            YoutubeChannel("UCbu2SsF-Or3Rsn3NxqODImw", "GameSpot", ""),
            YoutubeChannel("UC-lHJZR3Gqxm24_Vd_AJ5Yw", "PewDiePie", ""),
            YoutubeChannel("UC295-Dw4tzbMmF0nFxHFsGg", "jacksepticeye", ""),
        )),
        ChannelCategory("Fitness", "💪", listOf(
            YoutubeChannel("UCERm5yFZ1SptUEU4wZ2vJvw", "Jeff Nippard", ""),
            YoutubeChannel("UCfQgsKhHjSyRLOp9mnffqVg", "Renaissance Periodization", ""),
            YoutubeChannel("UCpQ34afVgk8cRQBjSyC6q_g", "Athlean-X", ""),
            YoutubeChannel("UCKNNMKvGHcSLnqFMbMvC_yA", "Chris Heria", ""),
        )),
        ChannelCategory("News", "📰", listOf(
            YoutubeChannel("UCeY0bbntWzzVIaj2z3QigXg", "NBC News", ""),
            YoutubeChannel("UCupvZG-5ko_eiXAupbDfxWw", "CNN", ""),
            YoutubeChannel("UC16niRr50-MSBwiO3He_b8A", "BBC News", ""),
            YoutubeChannel("UCJXGnMHFJ2pMFkljuAg7v6Q", "DW News", ""),
        )),
        ChannelCategory("Food", "🍳", listOf(
            YoutubeChannel("UCqqJQ_cXSat0KIAVfIfKkVA", "Joshua Weissman", ""),
            YoutubeChannel("UCRIZtPl9nb9RiXc9btSTQNw", "Babish Culinary Universe", ""),
            YoutubeChannel("UCJFp8uSYCjXOMnkUyb3CQ3Q", "Tasty", ""),
            YoutubeChannel("UCVgO39Bk5sMo66-6o6Spn6Q", "Gordon Ramsay", ""),
        )),
        ChannelCategory("Music", "🎵", listOf(
            YoutubeChannel("UCWX3yGbODI3HLv-HpCBBmNQ", "Melodysheep", ""),
            YoutubeChannel("UC-J-KZfRV8c13fOCkhReOrw", "Rick Beato", ""),
            YoutubeChannel("UCWljMOHRRBJOz_IqJMXd1SQ", "Polyphia", ""),
        )),
    )

    init {
        loadTrackedChannels()
        loadLatestVideos()
    }

    fun loadTrackedChannels() {
        _trackedChannels.value = youtubeRepository.getTrackedChannels()
    }

    fun loadLatestVideos(forceRefresh: Boolean = false) {
        if (youtubeRepository.getApiKey().isBlank()) {
            _youtubeState.value = YouTubeUiState.NoApiKey
            return
        }
        val tracked = youtubeRepository.getTrackedChannels()
        if (tracked.isEmpty()) {
            _youtubeState.value = YouTubeUiState.NoChannels
            return
        }
        viewModelScope.launch {
            _youtubeState.value = YouTubeUiState.Loading
            youtubeRepository.getLatestVideosForTrackedChannels()
                .onSuccess { videos ->
                    _youtubeState.value = if (videos.isEmpty()) YouTubeUiState.NoChannels
                    else YouTubeUiState.Success(videos)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load YouTube videos", e)
                    _youtubeState.value = if (e.message == "NO_API_KEY") YouTubeUiState.NoApiKey
                    else YouTubeUiState.Error(e.message ?: "Unknown error")
                }
        }
    }

    fun searchChannels(query: String) {
        if (query.isBlank()) { _channelSearchState.value = ChannelSearchState.Idle; return }
        viewModelScope.launch {
            _channelSearchState.value = ChannelSearchState.Loading
            youtubeRepository.searchChannels(query)
                .onSuccess { _channelSearchState.value = ChannelSearchState.Success(it) }
                .onFailure { _channelSearchState.value = ChannelSearchState.Error(it.message ?: "Search failed") }
        }
    }

    fun clearChannelSearch() { _channelSearchState.value = ChannelSearchState.Idle }

    fun addChannel(channel: YoutubeChannel) {
        youtubeRepository.addTrackedChannel(channel)
        loadTrackedChannels()
        loadLatestVideos(forceRefresh = true)
        // Show checkmark feedback for 1.5 s
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

    /** Called from the Composable's ActivityResultLauncher with the sign-in result Intent. */
    fun onSignInResult(data: android.content.Intent?) {
        googleSignInManager.handleSignInResult(data)
            .onFailure { e ->
                if (e.message != "Sign-in cancelled") {
                    _signInError.value = e.message
                }
            }
    }

    fun signOut() {
        googleSignInManager.signOut()
    }

    fun clearSignInError() { _signInError.value = null }
}
