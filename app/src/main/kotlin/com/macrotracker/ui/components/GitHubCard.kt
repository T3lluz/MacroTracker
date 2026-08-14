package com.macrotracker.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.macrotracker.R
import com.macrotracker.data.github.GitHubActivity
import com.macrotracker.data.github.GitHubIssue
import com.macrotracker.data.github.GitHubLabel
import com.macrotracker.data.github.GitHubPullRequest
import com.macrotracker.data.github.GitHubRepo
import com.macrotracker.data.github.GitHubSnapshot
import com.macrotracker.data.github.compactCount
import com.macrotracker.data.github.isOpen
import com.macrotracker.data.github.openIssueCount
import com.macrotracker.data.github.openPrCount
import com.macrotracker.data.github.parseGitHubInstant
import com.macrotracker.data.github.statusKey
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberRelativeTime
import com.macrotracker.ui.viewmodel.GitHubAuthUiState
import com.macrotracker.ui.viewmodel.GitHubUiState
import com.macrotracker.ui.viewmodel.GitHubViewModel

private val GhAccent = Color(0xFF58A6FF)
private val GhOpen = Color(0xFF3FB950)
private val GhMerged = Color(0xFFA371F7)
private val GhClosed = Color(0xFFF85149)
private val GhDraft = Color(0xFF8B949E)
private val GhReview = Color(0xFFE3B341)
private val GhSurface = Color(0xFF101820)
private val GhHairline = Color(0xFF243044)
private val GhChip = Color(0xFF161E2C)
private val Sharp = RoundedCornerShape(6.dp)
private val Pill = RoundedCornerShape(20.dp)

private enum class GhTab(val label: String) {
    ISSUES("Issues"),
    PRS("PRs"),
    ACTIVITY("Activity"),
    REPOS("Repos"),
    ACCOUNT("Account"),
}

private enum class IssueFilter(val label: String) { ALL("All"), ASSIGNED("Assigned") }
private enum class PrFilter(val label: String) { ALL("All"), MINE("Mine"), REVIEW("Review") }

@Composable
fun GitHubCard(
    viewModel: GitHubViewModel = hiltViewModel(),
    isVisible: Boolean = true,
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val authState by viewModel.authState.collectAsState()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedTabName by rememberSaveable { mutableStateOf(GhTab.ISSUES.name) }
    val selectedTab = GhTab.entries.find { it.name == selectedTabName } ?: GhTab.ISSUES

    LaunchedEffect(Unit) {
        if (state is GitHubUiState.Idle) viewModel.loadDashboard()
    }

    val success = state as? GitHubUiState.Success
    val data = success?.data
    val headerSub = when {
        data != null -> {
            val issues = data.openIssueCount()
            val prs = data.openPrCount()
            buildString {
                append("@${data.user.login}")
                if (issues > 0 || prs > 0) {
                    append(" · ")
                    if (prs > 0) append("$prs PR${if (prs != 1) "s" else ""}")
                    if (issues > 0 && prs > 0) append(" · ")
                    if (issues > 0) append("$issues issue${if (issues != 1) "s" else ""}")
                }
            }
        }
        state is GitHubUiState.NeedsAuth -> "Connect your account"
        else -> "Your issues, PRs & activity"
    }

    MacroCard(borderColor = GhAccent.copy(alpha = 0.16f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (data?.user?.avatarUrl != null) {
                    GhAvatar(data.user.avatarUrl, data.user.login, size = 22.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_github_logo),
                        contentDescription = "GitHub",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GitHub",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.1.sp,
                    )
                    Text(
                        headerSub,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LastUpdatedText(
                    lastUpdatedAt = success?.lastUpdatedAt,
                    color = TextSecondary,
                )
                if (expanded && state !is GitHubUiState.NeedsAuth) {
                    IconButton(
                        onClick = { haptics.click(); viewModel.loadDashboard(forceRefresh = true) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tick()
                        val url = data?.user?.htmlUrl ?: "https://github.com"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open GitHub",
                        tint = TextSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                WidgetExpandChevron(
                    expanded = expanded,
                    onClick = {
                        expanded = !expanded
                        if (expanded) {
                            haptics.toggleOn()
                            if (state is GitHubUiState.NeedsAuth) {
                                selectedTabName = GhTab.ACCOUNT.name
                            }
                        } else {
                            haptics.toggleOff()
                        }
                    },
                    accentColor = GhAccent,
                )
            }

            if (isVisible) {
                if (!expanded) {
                    Spacer(Modifier.height(12.dp))
                    when (state) {
                        GitHubUiState.Loading, GitHubUiState.Idle -> GhLoading()
                        GitHubUiState.NeedsAuth -> {
                            if (authState.isAwaitingBrowser) {
                                GitHubDeviceCodePanel(
                                    userCode = authState.deviceLogin?.userCode,
                                    onOpenActivation = { viewModel.openActivation() },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                )
                            } else {
                                ConnectPrompt {
                                    expanded = true
                                    selectedTabName = GhTab.ACCOUNT.name
                                    haptics.toggleOn()
                                    viewModel.connectGitHub()
                                }
                            }
                        }
                        is GitHubUiState.Error -> GhError((state as GitHubUiState.Error).message)
                        is GitHubUiState.Success -> GitHubCollapsedGlance(
                            data = (state as GitHubUiState.Success).data,
                            haptics = haptics,
                        )
                    }
                    WidgetExpandFooter(
                        expanded = false,
                        onToggle = {
                            expanded = true
                            if (state is GitHubUiState.NeedsAuth) {
                                selectedTabName = GhTab.ACCOUNT.name
                            }
                        },
                        accentColor = GhAccent,
                        expandLabel = if (state is GitHubUiState.NeedsAuth) "Connect" else "Open hub",
                    )
                }

                WidgetExpandSection(visible = expanded && isVisible) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(14.dp))
                        val openIss = data?.openIssueCount() ?: 0
                        val openPrs = data?.openPrCount() ?: 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            GhTab.entries.forEach { tab ->
                                val badge = when (tab) {
                                    GhTab.ISSUES -> openIss.takeIf { it > 0 }?.toString()
                                    GhTab.PRS -> openPrs.takeIf { it > 0 }?.toString()
                                    else -> null
                                }
                                val badgeColor = when (tab) {
                                    GhTab.ISSUES -> GhOpen
                                    GhTab.PRS -> GhMerged
                                    GhTab.ACTIVITY -> GhReview
                                    else -> GhAccent
                                }
                                GhTabChip(
                                    label = tab.label,
                                    badge = badge,
                                    badgeColor = badgeColor,
                                    active = selectedTab == tab,
                                    onClick = {
                                        haptics.tick()
                                        selectedTabName = tab.name
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = GhHairline, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        WidgetStateSwitch(
                            targetState = when (state) {
                                GitHubUiState.Idle, GitHubUiState.Loading -> 0
                                is GitHubUiState.Error -> 1
                                GitHubUiState.NeedsAuth -> 2
                                is GitHubUiState.Success -> 3
                            },
                            label = "ghState",
                        ) { phase ->
                            when (phase) {
                                0 -> GhLoading()
                                1 -> GhError(
                                    (state as? GitHubUiState.Error)?.message
                                        ?: "Couldn’t load GitHub data",
                                )
                                2 -> AccountTab(
                                    snapshot = null,
                                    authState = authState,
                                    onConnect = { viewModel.connectGitHub() },
                                    onDisconnect = { viewModel.disconnect() },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                    onOpenActivation = { viewModel.openActivation() },
                                    haptics = haptics,
                                )
                                else -> {
                                    val snap = (state as? GitHubUiState.Success)?.data
                                    if (snap == null) {
                                        GhLoading()
                                    } else {
                                        AnimatedContent(
                                            targetState = selectedTab,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clipToBounds(),
                                            transitionSpec = {
                                                MacroMotion.inCardTabSwitch(
                                                    toRight = targetState.ordinal > initialState.ordinal,
                                                )
                                            },
                                            label = "ghTab",
                                        ) { tab ->
                                            when (tab) {
                                                GhTab.ISSUES -> IssuesTab(snap.issues, haptics)
                                                GhTab.PRS -> PullsTab(snap.pullRequests, haptics)
                                                GhTab.ACTIVITY -> ActivityTab(snap.activity, haptics)
                                                GhTab.REPOS -> ReposTab(snap.repos, haptics)
                                                GhTab.ACCOUNT -> AccountTab(
                                                    snapshot = snap,
                                                    authState = authState,
                                                    onConnect = { viewModel.connectGitHub() },
                                                    onDisconnect = { viewModel.disconnect() },
                                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                                    onOpenActivation = { viewModel.openActivation() },
                                                    haptics = haptics,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WidgetExpandFooter(
                            expanded = true,
                            onToggle = { expanded = false },
                            accentColor = GhAccent,
                            collapseLabel = "Show less",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GhTabChip(
    label: String,
    badge: String?,
    badgeColor: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        if (active) TextPrimary else TextSecondary.copy(alpha = 0.75f),
        MacroMotion.colorTween(160),
        label = "ghTabFg",
    )
    val underline by animateColorAsState(
        if (active) GhAccent else Color.Transparent,
        MacroMotion.colorTween(160),
        label = "ghTabLine",
    )
    val underlineW by animateDpAsState(
        if (active) 18.dp else 0.dp,
        MacroMotion.pressSpring(),
        label = "ghTabW",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(Sharp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                color = fg,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(Pill)
                        .background(badgeColor.copy(alpha = if (active) 0.22f else 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(underlineW)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(underline),
        )
    }
}

@Composable
private fun GhLoading() {
    Box(
        Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = GhAccent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun GhError(message: String) {
    Text(
        message,
        color = TextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(Error.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun ConnectPrompt(onConnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onConnect)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github_logo),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Connect GitHub",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                "See issues, PRs, and activity across every repo",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GitHubCollapsedGlance(data: GitHubSnapshot, haptics: HapticHelper) {
    val context = LocalContext.current
    val reviewPr = data.pullRequests.firstOrNull { it.reviewRequested }
    val assigned = data.issues.firstOrNull { it.assignedToMe } ?: data.issues.firstOrNull()
    val latest = data.activity.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaChip(dotColor = GhMerged, text = "${data.openPrCount()} PRs")
            MetaChip(dotColor = GhOpen, text = "${data.openIssueCount()} issues")
            if (data.reviewRequestedCount > 0) {
                MetaChip(dotColor = GhReview, text = "${data.reviewRequestedCount} review")
            }
            MetaChip(text = "${data.repos.size} repos")
        }

        if (latest != null) {
            ActivityRow(latest, compact = true) {
                haptics.tick()
                latest.htmlUrl?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            }
        }

        if (reviewPr != null || assigned != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Sharp)
                    .background(GhSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "NEEDS ATTENTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    color = TextSecondary,
                )
                if (reviewPr != null) {
                    AttentionLine(
                        key = "review",
                        text = "#${reviewPr.number}  ${reviewPr.title}",
                        repo = reviewPr.repoFullName,
                    ) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, reviewPr.htmlUrl.toUri()))
                    }
                }
                if (assigned != null) {
                    AttentionLine(
                        key = if (assigned.isOpen) "open" else "closed",
                        text = "#${assigned.number}  ${assigned.title}",
                        repo = assigned.repoFullName,
                    ) {
                        haptics.tick()
                        context.startActivity(Intent(Intent.ACTION_VIEW, assigned.htmlUrl.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionLine(key: String, text: String, repo: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(key)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (repo.isNotBlank()) {
                Text(repo, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
        }
        StatusTag(key.uppercase(), statusColor(key))
    }
}

@Composable
private fun IssuesTab(issues: List<GitHubIssue>, haptics: HapticHelper) {
    val context = LocalContext.current
    var filterName by rememberSaveable { mutableStateOf(IssueFilter.ALL.name) }
    val filter = IssueFilter.entries.find { it.name == filterName } ?: IssueFilter.ALL
    val filtered = remember(issues, filter) {
        when (filter) {
            IssueFilter.ALL -> issues
            IssueFilter.ASSIGNED -> issues.filter { it.assignedToMe }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterRow {
            IssueFilter.entries.forEach { f ->
                val count = when (f) {
                    IssueFilter.ALL -> issues.size
                    IssueFilter.ASSIGNED -> issues.count { it.assignedToMe }
                }
                FilterChip(
                    label = "${f.label} $count",
                    active = filter == f,
                    activeColor = GhOpen,
                    onClick = { haptics.tick(); filterName = f.name },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyHint("No open issues across your repos")
        } else {
            filtered.take(30).forEach { issue ->
                IssueRow(issue) {
                    haptics.tick()
                    context.startActivity(Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri()))
                }
            }
        }
    }
}

@Composable
private fun PullsTab(pulls: List<GitHubPullRequest>, haptics: HapticHelper) {
    val context = LocalContext.current
    var filterName by rememberSaveable { mutableStateOf(PrFilter.ALL.name) }
    val filter = PrFilter.entries.find { it.name == filterName } ?: PrFilter.ALL
    val filtered = remember(pulls, filter) {
        when (filter) {
            PrFilter.ALL -> pulls
            PrFilter.MINE -> pulls.filter { it.authoredByMe }
            PrFilter.REVIEW -> pulls.filter { it.reviewRequested }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterRow {
            PrFilter.entries.forEach { f ->
                val count = when (f) {
                    PrFilter.ALL -> pulls.size
                    PrFilter.MINE -> pulls.count { it.authoredByMe }
                    PrFilter.REVIEW -> pulls.count { it.reviewRequested }
                }
                val color = when (f) {
                    PrFilter.REVIEW -> GhReview
                    PrFilter.MINE -> GhOpen
                    PrFilter.ALL -> GhMerged
                }
                FilterChip(
                    label = "${f.label} $count",
                    active = filter == f,
                    activeColor = color,
                    onClick = { haptics.tick(); filterName = f.name },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyHint("No open pull requests across your repos")
        } else {
            filtered.take(30).forEach { pr ->
                PullRow(pr) {
                    haptics.tick()
                    context.startActivity(Intent(Intent.ACTION_VIEW, pr.htmlUrl.toUri()))
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(activity: List<GitHubActivity>, haptics: HapticHelper) {
    val context = LocalContext.current
    if (activity.isEmpty()) {
        EmptyHint("No recent activity")
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        activity.take(30).forEach { item ->
            ActivityRow(item) {
                haptics.tick()
                item.htmlUrl?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            }
        }
    }
}

@Composable
private fun ReposTab(repos: List<GitHubRepo>, haptics: HapticHelper) {
    val context = LocalContext.current
    if (repos.isEmpty()) {
        EmptyHint("No repositories yet")
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repos.take(30).forEach { repo ->
            RepoRow(repo) {
                haptics.tick()
                context.startActivity(Intent(Intent.ACTION_VIEW, repo.htmlUrl.toUri()))
            }
        }
    }
}

@Composable
private fun AccountTab(
    snapshot: GitHubSnapshot?,
    authState: GitHubAuthUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
    haptics: HapticHelper,
) {
    val connected = authState.isConnected || snapshot != null

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (snapshot != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhAvatar(snapshot.user.avatarUrl, snapshot.user.login, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snapshot.user.name?.takeIf { it.isNotBlank() } ?: snapshot.user.login,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text("@${snapshot.user.login}", fontSize = 12.sp, color = TextSecondary)
                }
            }
            snapshot.user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = TextSecondary)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetaChip(text = "${snapshot.user.publicRepos + snapshot.user.totalPrivateRepos} repos")
                MetaChip(text = "${compactCount(snapshot.user.followers)} followers")
                MetaChip(text = "${compactCount(snapshot.user.following)} following")
            }
            val remaining = snapshot.rateLimitRemaining
            val limit = snapshot.rateLimitLimit
            if (remaining != null && limit != null) {
                Text(
                    "API · $remaining / $limit left this hour",
                    fontSize = 11.sp,
                    color = if (remaining < 10) GhClosed else TextSecondary,
                )
            }
        } else {
            Text(
                "Sign in with GitHub to load issues, PRs, and activity from every repo your account can see.",
                fontSize = 13.sp,
                color = TextSecondary,
            )
        }

        GitHubAccountActions(
            connected = connected,
            authState = authState,
            onConnect = {
                if (!authState.isBusy) {
                    haptics.click()
                    onConnect()
                }
            },
            onDisconnect = {
                haptics.reject()
                onDisconnect()
            },
            onCancelLogin = onCancelLogin,
            onOpenActivation = onOpenActivation,
        )

        authState.statusMessage?.let { msg ->
            Text(
                msg,
                fontSize = 11.sp,
                color = if (authState.isError) Error else TextSecondary,
            )
        }
    }
}

@Composable
private fun GitHubAccountActions(
    connected: Boolean,
    authState: GitHubAuthUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
) {
    when {
        authState.isAwaitingBrowser -> {
            GitHubDeviceCodePanel(
                userCode = authState.deviceLogin?.userCode,
                onOpenActivation = onOpenActivation,
                onCancelLogin = onCancelLogin,
            )
        }
        connected -> {
            MacroButton(
                text = "Disconnect",
                onClick = onDisconnect,
                variant = ButtonVariant.SECONDARY,
            )
        }
        !authState.isConfigured -> {
            Text(
                "Add GITHUB_CLIENT_ID to local.properties and rebuild, then tap Connect.",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        else -> {
            Button(
                onClick = onConnect,
                enabled = !authState.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = GhAccent),
                shape = Sharp,
            ) {
                if (authState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (authState.isBusy) "Connecting…" else "Connect GitHub",
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun GitHubDeviceCodePanel(
    userCode: String?,
    onOpenActivation: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhAccent.copy(alpha = 0.08f))
            .border(1.dp, GhAccent.copy(alpha = 0.35f), Sharp)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Enter this code on GitHub",
            fontSize = 12.sp,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            userCode?.takeIf { it.isNotBlank() } ?: "····",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 1.5.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Approve DailyDash at github.com/login/device",
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    haptics.click()
                    onOpenActivation()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GhAccent),
                shape = Sharp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open GitHub", fontSize = 13.sp)
            }
            TextButton(onClick = { haptics.tick(); onCancelLogin() }) {
                Text("Cancel", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun IssueRow(issue: GitHubIssue, onClick: () -> Unit) {
    val key = if (issue.isOpen) "open" else "closed"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(key)
            Text(
                "#${issue.number}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                issue.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepoTag(issue.repoFullName)
            if (issue.assignedToMe) StatusTag("ASSIGNED", GhAccent)
            Spacer(Modifier.weight(1f))
            issue.labels.take(2).forEach { LabelChip(it) }
            if (issue.comments > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                    Text("${issue.comments}", fontSize = 11.sp, color = TextSecondary)
                }
            }
            GhRelative(issue.updatedAt)
        }
    }
}

@Composable
private fun PullRow(pr: GitHubPullRequest, onClick: () -> Unit) {
    val key = pr.statusKey()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(key)
            Text(
                "#${pr.number}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                pr.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusTag(key.uppercase(), statusColor(key))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepoTag(pr.repoFullName)
            GhAvatar(pr.userAvatarUrl, pr.userLogin, size = 14.dp)
            Text(pr.userLogin, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            pr.labels.take(2).forEach { LabelChip(it) }
            GhRelative(pr.updatedAt)
        }
    }
}

@Composable
private fun ActivityRow(
    item: GitHubActivity,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .then(if (compact) Modifier.background(GhSurface) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 4.dp, vertical = if (compact) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GhAvatar(item.actorAvatarUrl, item.actorLogin)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.repoFullName,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusTag(item.type.removeSuffix("Event").uppercase().take(8), GhAccent)
        GhRelative(item.createdAt)
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                repo.fullName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (repo.isPrivate) {
                Icon(Icons.Outlined.Lock, null, tint = GhDraft, modifier = Modifier.size(13.dp))
            }
        }
        repo.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!repo.language.isNullOrBlank()) {
                MetaChip(dotColor = languageColor(repo.language), text = repo.language)
            }
            MetaChip(icon = Icons.Outlined.Star, text = compactCount(repo.stars), color = Color(0xFFE3B341))
            MetaChip(icon = Icons.Outlined.AccountTree, text = compactCount(repo.forks))
            Spacer(Modifier.weight(1f))
            GhRelative(repo.pushedAt)
        }
    }
}

@Composable
private fun RepoTag(fullName: String) {
    if (fullName.isBlank()) return
    Text(
        fullName,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = GhAccent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .clip(Pill)
            .background(GhAccent.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun MetaChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dotColor: Color? = null,
    color: Color = TextSecondary,
) {
    Row(
        modifier = Modifier
            .clip(Pill)
            .background(GhChip)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        } else if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary.copy(alpha = 0.88f),
            maxLines = 1,
        )
    }
}

@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() },
    )
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) activeColor.copy(alpha = 0.18f) else GhChip
    val fg = if (active) activeColor else TextSecondary
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(Pill)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LabelChip(label: GitHubLabel) {
    val bg = remember(label.color) { parseHexColor(label.color) }
    val fg = remember(label.color) { labelForeground(label.color) }
    Text(
        label.name,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 88.dp)
            .clip(Pill)
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        color = color,
        modifier = Modifier
            .clip(Pill)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun StatusDot(key: String) {
    val color = statusColor(key)
    Canvas(modifier = Modifier.size(12.dp)) {
        when (key) {
            "merged", "review" -> drawCircle(color, radius = size.minDimension / 2.2f)
            "closed", "failure" -> {
                drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 2.2.dp.toPx()))
            }
            "draft" -> drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 1.8.dp.toPx()))
            else -> drawCircle(color, radius = size.minDimension / 2.2f, style = Stroke(width = 2.2.dp.toPx()))
        }
    }
}

@Composable
private fun GhAvatar(url: String?, name: String, size: Dp = 22.dp) {
    val context = LocalContext.current
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(GhChip),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(GhChip),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GhRelative(iso: String?) {
    val instant = remember(iso) { parseGitHubInstant(iso) } ?: return
    val text = rememberRelativeTime(instant)
    Text(text, fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.85f))
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Sharp)
            .background(GhSurface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

private fun statusColor(key: String): Color = when (key) {
    "open", "success" -> GhOpen
    "merged" -> GhMerged
    "review" -> GhReview
    "closed", "failure" -> GhClosed
    "draft", "cancelled" -> GhDraft
    else -> TextSecondary
}

private fun parseHexColor(hex: String): Color {
    val rgb = hex.trim().removePrefix("#").toLongOrNull(16) ?: return GhDraft
    return Color(
        ((rgb shr 16) and 0xFF).toInt(),
        ((rgb shr 8) and 0xFF).toInt(),
        (rgb and 0xFF).toInt(),
    )
}

private fun labelForeground(hex: String): Color {
    val rgb = hex.trim().removePrefix("#").toLongOrNull(16) ?: return Color.White
    val r = ((rgb shr 16) and 0xFF) / 255.0
    val g = ((rgb shr 8) and 0xFF) / 255.0
    val b = (rgb and 0xFF) / 255.0
    val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return if (l > 0.55) Color(0xFF0D1117) else Color.White
}

private fun languageColor(language: String): Color = when (language.lowercase()) {
    "kotlin" -> Color(0xFFA97BFF)
    "java" -> Color(0xFFB07219)
    "javascript" -> Color(0xFFF1E05A)
    "typescript" -> Color(0xFF3178C6)
    "python" -> Color(0xFF3572A5)
    "go", "golang" -> Color(0xFF00ADD8)
    "rust" -> Color(0xFFDEA584)
    "swift" -> Color(0xFFF05138)
    "c++" -> Color(0xFFF34B7D)
    "ruby" -> Color(0xFF701516)
    "shell" -> Color(0xFF89E051)
    "dart" -> Color(0xFF00B4AB)
    else -> GhAccent
}
