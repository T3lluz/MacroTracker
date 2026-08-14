package com.macrotracker.data.github

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class GitHubSnapshot(
    val user: GitHubUser,
    val issues: List<GitHubIssue> = emptyList(),
    val pullRequests: List<GitHubPullRequest> = emptyList(),
    val activity: List<GitHubActivity> = emptyList(),
    val repos: List<GitHubRepo> = emptyList(),
    val issueTotal: Int = 0,
    val pullTotal: Int = 0,
    val reviewRequestedCount: Int = 0,
    val rateLimitRemaining: Int? = null,
    val rateLimitLimit: Int? = null,
)

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String,
    val bio: String? = null,
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)

@Serializable
data class GitHubRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String? = null,
    val htmlUrl: String,
    val language: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val openIssuesCount: Int = 0,
    val isPrivate: Boolean = false,
    val pushedAt: String? = null,
    val ownerAvatarUrl: String? = null,
)

@Serializable
data class GitHubLabel(
    val name: String,
    val color: String,
)

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val repoFullName: String,
    val userLogin: String,
    val userAvatarUrl: String? = null,
    val comments: Int = 0,
    val labels: List<GitHubLabel> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val assignedToMe: Boolean = false,
    val isPullRequest: Boolean = false,
    val draft: Boolean = false,
)

@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val repoFullName: String,
    val userLogin: String,
    val userAvatarUrl: String? = null,
    val draft: Boolean = false,
    val mergedAt: String? = null,
    val labels: List<GitHubLabel> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val reviewRequested: Boolean = false,
    val authoredByMe: Boolean = false,
)

@Serializable
data class GitHubActivity(
    val id: String,
    val type: String,
    val repoFullName: String,
    val createdAt: String,
    val title: String,
    val htmlUrl: String? = null,
    val actorLogin: String,
    val actorAvatarUrl: String? = null,
)

val GitHubIssue.isOpen: Boolean get() = state.equals("open", ignoreCase = true)

val GitHubPullRequest.isOpen: Boolean get() = state.equals("open", ignoreCase = true) && !draft
val GitHubPullRequest.isMerged: Boolean get() = !mergedAt.isNullOrBlank()
val GitHubPullRequest.isDraftOpen: Boolean get() = draft && state.equals("open", ignoreCase = true)

fun GitHubPullRequest.statusKey(): String = when {
    isMerged -> "merged"
    reviewRequested && state.equals("open", ignoreCase = true) -> "review"
    isDraftOpen -> "draft"
    isOpen -> "open"
    else -> "closed"
}

fun GitHubSnapshot.openIssueCount(): Int = issueTotal.takeIf { it > 0 } ?: issues.count { it.isOpen }
fun GitHubSnapshot.openPrCount(): Int = pullTotal.takeIf { it > 0 } ?: pullRequests.count { it.isOpen || it.draft }

fun GitHubActivity.isPush(): Boolean = type == "PushEvent"

fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> {
        val v = n / 1_000_000.0
        if (v % 1.0 == 0.0) "${v.toInt()}M" else "%.1fM".format(v)
    }
    n >= 1_000 -> {
        val v = n / 1_000.0
        if (v % 1.0 == 0.0) "${v.toInt()}k" else "%.1fk".format(v)
    }
    else -> n.toString()
}

fun parseGitHubInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
}
