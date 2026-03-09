package com.macrotracker.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.macrotracker.data.youtube.YoutubeChannel
import com.macrotracker.data.youtube.YoutubeVideo
import com.macrotracker.R
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ChannelSearchState
import com.macrotracker.ui.viewmodel.YouTubeUiState
import com.macrotracker.ui.viewmodel.YouTubeViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val YtRed  = Color(0xFFFF0000)
private val YtDark = Color(0xFF0F0F0F)

// ── Main card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeCard(viewModel: YouTubeViewModel = hiltViewModel()) {
    val haptics          = rememberHaptics()
    val scope            = rememberCoroutineScope()
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val youtubeState     by viewModel.youtubeState.collectAsState()
    val trackedChannels  by viewModel.trackedChannels.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadLatestVideos() }

    MacroCard(
        borderColor = YtRed.copy(alpha = 0.18f),
    ) {
        // ── Header (always visible) ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_youtube_logo),
                contentDescription = "YouTube",
                modifier = Modifier.height(24.dp),
                contentScale = ContentScale.FillHeight,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("YouTube", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "${trackedChannels.size} channel${if (trackedChannels.size != 1) "s" else ""} tracked",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                    val successState = youtubeState as? YouTubeUiState.Success
                    if (successState?.lastUpdatedAt != null) {
                        Text("·", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.35f))
                        LastUpdatedText(
                            lastUpdatedAt = successState.lastUpdatedAt,
                            color = TextSecondary,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded && youtubeState is YouTubeUiState.Success,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(100)) + scaleOut(tween(100)),
            ) {
                IconButton(
                    onClick = { haptics.tick(); viewModel.loadLatestVideos(forceRefresh = true) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(100)) + scaleOut(tween(100)),
            ) {
                IconButton(
                    onClick = { haptics.tick(); showSettings = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            val ytChevronRot by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "yt_hdr_chevron",
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        expanded = !expanded
                        if (expanded) haptics.toggleOn() else haptics.toggleOff()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (expanded) YtRed.copy(alpha = 0.75f) else TextSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp).rotate(ytChevronRot),
                )
            }
        }

        // ── Collapsed view — compact feed ─────────────────────────────────
        AnimatedVisibility(
            visible = !expanded,
            enter = expandVertically(tween(280)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(150)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                when (val state = youtubeState) {
                    is YouTubeUiState.NoChannels -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(YtRed.copy(alpha = 0.07f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("No channels tracked", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("Expand to add channels", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                    is YouTubeUiState.Loading -> {
                        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    is YouTubeUiState.Success -> {
                        CompactVideoFeed(
                            videos = state.videos,
                            trackedChannels = trackedChannels,
                            haptics = haptics,
                        )
                    }
                    is YouTubeUiState.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Error.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("⚠", fontSize = 16.sp)
                            Text(state.message, fontSize = 12.sp, color = Error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    YouTubeUiState.Idle -> Unit
                }
                Spacer(Modifier.height(4.dp))
                WidgetExpandBar(
                    expanded = false,
                    onToggle = { expanded = true; haptics.toggleOn() },
                    accentColor = YtRed,
                    expandLabel = "Full feed",
                )
            }
        }

        // ── Expanded view — full feed + controls ──────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(160)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = youtubeState,
                    transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.97f)) togetherWith fadeOut(tween(150)) },
                    label = "yt_body",
                ) { state ->
                    when (state) {
                        is YouTubeUiState.Loading  -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                        is YouTubeUiState.NoChannels -> NoChannelsPrompt(onOpenSettings = { haptics.tick(); showSettings = true })
                        is YouTubeUiState.Success  -> VideoFeed(videos = state.videos, trackedChannels = trackedChannels)
                        is YouTubeUiState.Error    -> ErrorState(message = state.message, onRetry = { viewModel.loadLatestVideos(forceRefresh = true) })
                        YouTubeUiState.Idle        -> Unit
                    }
                }

                Spacer(Modifier.height(4.dp))
                WidgetExpandBar(
                    expanded = true,
                    onToggle = { expanded = false; haptics.toggleOff() },
                    accentColor = YtRed,
                    collapseLabel = "Show less",
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Surface,
            dragHandle = null,
        ) {
            YouTubeSettingsSheet(
                viewModel = viewModel,
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { showSettings = false } },
            )
        }
    }
}

// ── Compact video feed (collapsed widget) ─────────────────────────────────────

@Composable
private fun CompactVideoFeed(
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedVideos = if (selectedChannelId != null) videos.filter { it.channelId == selectedChannelId } else videos

    if (videos.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(YtRed.copy(alpha = 0.06f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            Text("No videos yet", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Channel avatar strip — clickable to filter
        if (trackedChannels.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                items(trackedChannels) { channel ->
                    val hasNew = videos.any { it.channelId == channel.channelId }
                    val isSelected = selectedChannelId == channel.channelId
                    val avatarUrl = channel.thumbnailUrl
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                selectedChannelId = if (isSelected) null else channel.channelId
                            }
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                                contentDescription = channel.title,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.5.dp,
                                        color = when {
                                            isSelected -> YtRed
                                            hasNew -> YtRed.copy(alpha = 0.6f)
                                            else -> Border
                                        },
                                        shape = CircleShape,
                                    ),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.5.dp,
                                        color = when {
                                            isSelected -> YtRed
                                            hasNew -> YtRed.copy(alpha = 0.6f)
                                            else -> Border
                                        },
                                        shape = CircleShape,
                                    )
                                    .background(Border.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    channel.title.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                )
                            }
                        }
                        if (hasNew && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(YtRed)
                                    .border(1.dp, Surface, CircleShape),
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(YtRed.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            // Channel filter active label
            if (selectedChannelId != null) {
                val channelName = trackedChannels.find { it.channelId == selectedChannelId }?.title ?: ""
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YtRed.copy(alpha = 0.12f))
                            .border(0.5.dp, YtRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(channelName, fontSize = 10.sp, color = YtRed, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Tap avatar to clear",
                        fontSize = 9.sp,
                        color = TextSecondary.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // Latest 3 videos in compact row format (filtered if needed)
        val videosToShow = displayedVideos.take(3)
        videosToShow.forEachIndexed { idx, video ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(idx * 55L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptics.tick()
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                            )
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Compact thumbnail
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(YtDark),
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context).data(video.thumbnailUrl).crossfade(true).build(),
                            contentDescription = video.title,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        ) {
                            Text(formatRelativeTime(video.publishedAt), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    // Title + meta
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            video.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(YtRed))
                            Text(
                                video.channelTitle,
                                fontSize = 10.sp,
                                color = YtRed,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(formatRelativeTime(video.publishedAt), fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.7f))
                    }
                }
            }
        if (idx < minOf(2, videosToShow.size - 1)) {
                HorizontalDivider(color = Border.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 1.dp))
            }
        }

        if (displayedVideos.size > 3) {
            Spacer(Modifier.height(4.dp))
            Text(
                "+${displayedVideos.size - 3} more · Expand to see all",
                fontSize = 10.sp,
                color = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp),
            )
        } else if (displayedVideos.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(YtRed.copy(alpha = 0.06f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Text("No videos from this channel yet", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ── Video feed ────────────────────────────────────────────────────────────────

@Composable
private fun VideoFeed(
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedVideos = if (selectedChannelId != null) videos.filter { it.channelId == selectedChannelId } else videos

    Column {
        // Scrollable channel pill row — clickable to filter
        if (trackedChannels.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(trackedChannels) { channel ->
                    ChannelPill(
                        channel = channel,
                        isSelected = selectedChannelId == channel.channelId,
                        onClick = {
                            haptics.tick()
                            selectedChannelId = if (selectedChannelId == channel.channelId) null else channel.channelId
                        },
                    )
                }
            }
        }
        if (displayedVideos.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(YtRed.copy(alpha = 0.06f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Text("No videos from this channel yet", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            displayedVideos.take(6).forEachIndexed { index, video ->
                VideoCard(video = video, onClick = {
                    haptics.tick()
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri()))
                })
                if (index < minOf(5, displayedVideos.size - 1)) {
                    HorizontalDivider(color = Border.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: YoutubeVideo, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Thumbnail with play overlay
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(YtDark),
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(video.thumbnailUrl).crossfade(true).build(),
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
            )
            // Play icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Time badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(formatRelativeTime(video.publishedAt), fontSize = 9.sp, color = Color.White)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(YtRed))
                Spacer(Modifier.width(5.dp))
                Text(video.channelTitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelPill(channel: YoutubeChannel, isSelected: Boolean = false, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) YtRed.copy(alpha = 0.15f) else Surface,
        animationSpec = tween(200), label = "pill_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) YtRed.copy(alpha = 0.6f) else Border.copy(alpha = 0.4f),
        animationSpec = tween(200), label = "pill_border",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (channel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(channel.thumbnailUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(18.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            channel.title,
            fontSize = 11.sp,
            color = if (isSelected) YtRed else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        if (isSelected) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Close, null, tint = YtRed.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
        }
    }
}

// ── Empty/error states ────────────────────────────────────────────────────────

@Composable
private fun NoChannelsPrompt(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(YtRed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("No channels tracked", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text("Browse or search to add your favourite channels", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = YtRed),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Channels", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠ $message", fontSize = 13.sp, color = Error, modifier = Modifier.padding(horizontal = 12.dp))
        TextButton(onClick = onRetry) { Text("Retry", color = Primary) }
    }
}

// ── Settings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeSettingsSheet(
    viewModel: YouTubeViewModel,
    onDismiss: () -> Unit,
) {
    val haptics            = rememberHaptics()
    val trackedChannels    by viewModel.trackedChannels.collectAsState()
    val channelSearchState by viewModel.channelSearchState.collectAsState()
    val recentlyAdded      by viewModel.recentlyAdded.collectAsState()

    var activeTab   by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Sheet handle + title ─────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(YtRed), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("YouTube Channels", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = TextSecondary) }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Border.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))

            // ── Tabs ─────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Background)) {
                data class Tab(val label: String, val badge: Int = 0)
                listOf(
                    Tab("Watching", trackedChannels.size),
                    Tab("Search"),
                ).forEachIndexed { i, tab ->
                    val selected = activeTab == i
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Primary else Color.Transparent)
                            .clickable { haptics.tick(); activeTab = i; if (i != 1) viewModel.clearChannelSearch() }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else TextSecondary)
                            if (tab.badge > 0) {
                                Spacer(Modifier.width(3.dp))
                                Box(modifier = Modifier.clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.25f) else Border).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("${tab.badge}", fontSize = 9.sp, color = if (selected) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Tab content ───────────────────────────────────────────────────
        when (activeTab) {
            0 -> WatchingTab(trackedChannels, recentlyAdded, viewModel, haptics)
            1 -> SearchTab(searchQuery, onQueryChange = { searchQuery = it }, channelSearchState, recentlyAdded, viewModel, haptics)
        }
    }
}

// ── Tab: Watching ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchingTab(
    trackedChannels: List<YoutubeChannel>,
    recentlyAdded: Set<String>,
    viewModel: YouTubeViewModel,
    haptics: HapticHelper,
) {
    if (trackedChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp, top = 8.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nothing tracked yet", fontSize = 14.sp, color = TextSecondary)
                Text("Go to Search to add channels", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = Modifier.height((trackedChannels.size * 68).coerceAtMost(360).dp),
    ) {
        items(trackedChannels, key = { it.channelId }) { channel ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        haptics.reject()
                        viewModel.removeChannel(channel.channelId)
                        true
                    } else false
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Error.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Row(modifier = Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp, color = Error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            ) {
                ChannelListRow(
                    channel = channel,
                    isTracked = true,
                    justAdded = recentlyAdded.contains(channel.channelId),
                    onToggle = { haptics.reject(); viewModel.removeChannel(channel.channelId) },
                    modifier = Modifier.background(Surface),
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}


// ── Tab: Search ───────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    channelSearchState: ChannelSearchState,
    recentlyAdded: Set<String>,
    viewModel: YouTubeViewModel,
    haptics: HapticHelper,
) {
    val suggestions        by viewModel.searchSuggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()

    // Show the suggestions dropdown whenever the user is actively typing (query ≥ 2 chars)
    // and we have results or are loading — regardless of whether a full search was done before.
    val showSuggestions = searchQuery.length >= 2 &&
        (suggestions.isNotEmpty() || suggestionsLoading)

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { q ->
                onQueryChange(q)
                viewModel.onSearchQueryChanged(q)
                // If query cleared, reset search state
                if (q.isBlank()) viewModel.clearChannelSearch()
            },
            placeholder = { Text("Search channels…", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                if (suggestionsLoading && searchQuery.isNotBlank()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = YtRed,
                    )
                } else {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange(""); viewModel.clearChannelSearch() }) {
                        Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    viewModel.clearSearchSuggestions()
                    viewModel.searchChannels(searchQuery)
                }
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Background, unfocusedContainerColor = Background,
                focusedBorderColor = Primary, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
            ),
        )

        // ── Live suggestions dropdown ─────────────────────────────────────
        AnimatedVisibility(
            visible = showSuggestions,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            ) {
                if (suggestionsLoading && suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = YtRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    suggestions.forEachIndexed { idx, channel ->
                        val tracked = viewModel.isChannelTracked(channel.channelId)
                        val justAdded = recentlyAdded.contains(channel.channelId)
                        SuggestionRow(
                            channel = channel,
                            isTracked = tracked,
                            justAdded = justAdded,
                            onAdd = {
                                haptics.confirm()
                                viewModel.addChannel(channel)
                            },
                            onRemove = {
                                haptics.reject()
                                viewModel.removeChannel(channel.channelId)
                            },
                            onSelect = {
                                // Commit the full search for this channel name
                                onQueryChange(channel.title)
                                viewModel.clearSearchSuggestions()
                                viewModel.searchChannels(channel.title)
                            },
                        )
                        if (idx < suggestions.size - 1) {
                            HorizontalDivider(
                                color = Border.copy(alpha = 0.15f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    // "See all results" footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                haptics.tick()
                                viewModel.clearSearchSuggestions()
                                viewModel.searchChannels(searchQuery)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.Search, null, tint = YtRed, modifier = Modifier.size(14.dp))
                            Text(
                                "Search \"$searchQuery\"",
                                fontSize = 12.sp,
                                color = YtRed,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Full search results ───────────────────────────────────────────
        when (val s = channelSearchState) {
            is ChannelSearchState.Loading -> Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                CircularProgressIndicator(color = YtRed, modifier = Modifier.size(24.dp))
            }
            is ChannelSearchState.Success -> {
                if (s.channels.isEmpty()) {
                    Text("No channels found.", fontSize = 13.sp, color = TextSecondary)
                } else {
                    Text(
                        "${s.channels.size} channel${if (s.channels.size != 1) "s" else ""} found",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    s.channels.forEach { channel ->
                        val tracked = viewModel.isChannelTracked(channel.channelId)
                        ChannelListRow(
                            channel = channel.copy(isTracked = tracked),
                            isTracked = tracked,
                            justAdded = recentlyAdded.contains(channel.channelId),
                            onToggle = {
                                if (tracked) { haptics.reject(); viewModel.removeChannel(channel.channelId) }
                                else { haptics.confirm(); viewModel.addChannel(channel) }
                            },
                        )
                    }
                }
            }
            is ChannelSearchState.Error -> Text("⚠ ${s.message}", fontSize = 13.sp, color = Error)
            ChannelSearchState.Idle -> {
                if (!showSuggestions) {
                    Text(
                        "Type a channel name to search",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

// ── Suggestion row (inline preview while typing) ──────────────────────────────

@Composable
private fun SuggestionRow(
    channel: YoutubeChannel,
    isTracked: Boolean,
    justAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val btnBg by animateColorAsState(
        targetValue = when {
            justAdded -> Primary.copy(alpha = 0.2f)
            isTracked -> Error.copy(alpha = 0.12f)
            else -> YtRed.copy(alpha = 0.12f)
        },
        animationSpec = tween(300), label = "sug_btn_bg",
    )
    val btnScale by animateFloatAsState(
        targetValue = if (justAdded) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sug_btn_scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Avatar
        if (channel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(channel.thumbnailUrl).crossfade(true).build(),
                contentDescription = channel.title,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Border),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Border.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(channel.title.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        // Info
        Column(Modifier.weight(1f)) {
            Text(
                channel.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!channel.subscriberCount.isNullOrBlank()) {
                Text(channel.subscriberCount, fontSize = 10.sp, color = TextSecondary)
            }
        }
        // + / check / remove button
        Box(
            modifier = Modifier
                .scale(btnScale)
                .size(32.dp)
                .clip(CircleShape)
                .background(btnBg)
                .clickable(onClick = if (isTracked) onRemove else onAdd),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    justAdded -> "check"
                    isTracked -> "remove"
                    else -> "add"
                },
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(tween(150))) togetherWith fadeOut(tween(100)) },
                label = "sug_icon",
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        "check" -> Icons.Filled.Check
                        "remove" -> Icons.Filled.Close
                        else -> Icons.Filled.Add
                    },
                    contentDescription = state,
                    tint = when (state) {
                        "check" -> Primary
                        "remove" -> Error
                        else -> YtRed
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


// ── Channel list row (shared) ─────────────────────────────────────────────────

@Composable
private fun ChannelListRow(
    channel: YoutubeChannel,
    isTracked: Boolean,
    justAdded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val buttonBg by animateColorAsState(
        targetValue = when {
            justAdded -> Primary.copy(alpha = 0.2f)
            isTracked -> Error.copy(alpha = 0.12f)
            else      -> Primary.copy(alpha = 0.12f)
        },
        animationSpec = tween(300), label = "btn_bg",
    )
    val btnScale by animateFloatAsState(
        targetValue = if (justAdded) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn_scale",
    )

    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (channel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(channel.thumbnailUrl).crossfade(true).build(),
                contentDescription = channel.title,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Border),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Border.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text(channel.title.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isTracked) {
                Text("Tracked", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Animated add/check/remove button
        Box(
            modifier = Modifier
                .scale(btnScale)
                .size(36.dp)
                .clip(CircleShape)
                .background(buttonBg)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    justAdded -> "check"
                    isTracked -> "remove"
                    else      -> "add"
                },
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(tween(150))) togetherWith fadeOut(tween(100)) },
                label = "btn_icon",
            ) { iconState ->
                Icon(
                    imageVector = when (iconState) {
                        "check"  -> Icons.Filled.Check
                        "remove" -> Icons.Filled.Close
                        else     -> Icons.Filled.Add
                    },
                    contentDescription = iconState,
                    tint = when (iconState) {
                        "check"  -> Primary
                        "remove" -> Error
                        else     -> Primary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


// ── Time helpers ──────────────────────────────────────────────────────────────

private fun formatRelativeTime(iso: String): String = try {
    val instant = Instant.parse(iso)
    val now = Instant.now()
    val mins  = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days  = ChronoUnit.DAYS.between(instant, now)
    when {
        mins  < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days  < 7  -> "${days}d ago"
        else -> DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(instant)
    }
} catch (_: Exception) { iso.take(10) }
