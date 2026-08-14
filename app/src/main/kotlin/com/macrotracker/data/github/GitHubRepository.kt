package com.macrotracker.data.github

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class GitHubNeedsAuthException : IOException("Connect GitHub to load your issues, PRs, and activity")

interface GitHubRepository {
    suspend fun getDashboard(forceRefresh: Boolean = false): Result<GitHubSnapshot>
    fun getCachedDashboard(): GitHubSnapshot?
    fun hasToken(): Boolean
    fun invalidateCache()
    val lastFetchTimeMs: Long
}

@Singleton
class GitHubRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val authClient: GitHubAuthClient,
    @ApplicationContext private val context: Context,
) : GitHubRepository {

    companion object {
        private const val TAG = "GitHubRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L
        private const val PREFS = "github_dashboard_cache"
        private const val KEY_SNAPSHOT = "snapshot_json"
        private const val KEY_FETCH_TIME = "fetch_time"
        private const val KEY_CACHE_USER = "cache_user"
        private const val CACHE_VERSION = 2
        private const val KEY_CACHE_VERSION = "cache_version"
        private const val API = "https://api.github.com"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val fetchMutex = Mutex()

    @Volatile private var cached: GitHubSnapshot? = null
    @Volatile private var lastFetchTime: Long = 0L
    @Volatile private var cachedUserKey: String = ""

    override val lastFetchTimeMs: Long get() = lastFetchTime
    override fun getCachedDashboard(): GitHubSnapshot? = cached
    override fun hasToken(): Boolean =
        authClient.isConnected() ||
            settings.githubToken.value.isNotBlank() ||
            BuildConfig.GITHUB_TOKEN.isNotBlank()

    init {
        restoreDiskCache()
    }

    override fun invalidateCache() {
        cached = null
        lastFetchTime = 0L
        cachedUserKey = ""
        prefs.edit {
            remove(KEY_SNAPSHOT)
            remove(KEY_FETCH_TIME)
            remove(KEY_CACHE_USER)
        }
    }

    override suspend fun getDashboard(forceRefresh: Boolean): Result<GitHubSnapshot> = fetchMutex.withLock {
        val token = resolveToken()
        if (token.isBlank()) {
            return@withLock Result.failure(GitHubNeedsAuthException())
        }
        val cacheKey = token.hashCode().toString()
        val now = System.currentTimeMillis()

        if (cachedUserKey != cacheKey) {
            cached = null
            lastFetchTime = 0L
        }

        val cacheValid = !forceRefresh &&
            cached != null &&
            cachedUserKey == cacheKey &&
            lastFetchTime > 0L &&
            (now - lastFetchTime) < CACHE_DURATION_MS

        if (cacheValid) {
            return@withLock Result.success(cached!!)
        }

        return@withLock try {
            withContext(Dispatchers.IO) { fetchDashboard(token) }
                .also { snapshot ->
                    cached = snapshot
                    lastFetchTime = System.currentTimeMillis()
                    cachedUserKey = cacheKey
                    persistDiskCache(snapshot, cacheKey)
                }
                .let { Result.success(it) }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub dashboard fetch failed: ${e.message}", e)
            val fallback = cached?.takeIf { cachedUserKey == cacheKey }
            if (fallback != null) Result.success(fallback) else Result.failure(e)
        }
    }

    private suspend fun fetchDashboard(token: String): GitHubSnapshot = coroutineScope {
        val meResp = get("$API/user", token)
        val user = parseUser(JSONObject(meResp.body))
        val login = user.login

        val issuesSearch = async {
            runCatching { searchIssues("is:open is:issue involves:@me", login, token) }.getOrNull()
        }
        val prSearch = async {
            runCatching { searchIssues("is:open is:pr involves:@me", login, token) }.getOrNull()
        }
        val reviewSearch = async {
            runCatching { searchIssues("is:open is:pr review-requested:@me", login, token) }.getOrNull()
        }
        val reposDeferred = async {
            runCatching {
                get("$API/user/repos?sort=pushed&per_page=30&affiliation=owner,collaborator,organization_member", token)
            }.getOrNull()
        }
        val eventsDeferred = async {
            runCatching { get("$API/users/$login/events?per_page=50", token) }.getOrNull()
        }

        val issuePage = issuesSearch.await()
        val prPage = prSearch.await()
        val reviewPage = reviewSearch.await()
        val reposResp = reposDeferred.await()
        val eventsResp = eventsDeferred.await()

        val reviewKeys = reviewPage?.items
            ?.map { it.repoFullName to it.number }
            ?.toSet()
            .orEmpty()

        val pulls = LinkedHashMap<Pair<String, Int>, GitHubPullRequest>()
        prPage?.items?.forEach { issue ->
            val pr = issue.toPull(me = login, reviewRequested = (issue.repoFullName to issue.number) in reviewKeys)
            pulls[issue.repoFullName to issue.number] = pr
        }
        reviewPage?.items?.forEach { issue ->
            val key = issue.repoFullName to issue.number
            val existing = pulls[key]
            pulls[key] = existing?.copy(reviewRequested = true)
                ?: issue.toPull(me = login, reviewRequested = true)
        }

        val remaining = listOfNotNull(
            meResp.remaining,
            issuePage?.remaining,
            prPage?.remaining,
            reviewPage?.remaining,
            reposResp?.remaining,
            eventsResp?.remaining,
        ).minOrNull()
        val limit = listOfNotNull(
            meResp.limit,
            issuePage?.limit,
            prPage?.limit,
            reviewPage?.limit,
            reposResp?.limit,
            eventsResp?.limit,
        ).maxOrNull()

        GitHubSnapshot(
            user = user,
            issues = issuePage?.items.orEmpty().filter { !it.isPullRequest },
            pullRequests = pulls.values.toList(),
            activity = eventsResp?.let { parseEvents(it.body) }.orEmpty(),
            repos = reposResp?.let { parseRepos(it.body) }.orEmpty(),
            issueTotal = issuePage?.totalCount ?: 0,
            pullTotal = prPage?.totalCount ?: 0,
            reviewRequestedCount = reviewPage?.totalCount ?: reviewKeys.size,
            rateLimitRemaining = remaining,
            rateLimitLimit = limit,
        )
    }

    private data class SearchPage(
        val items: List<GitHubIssue>,
        val totalCount: Int,
        val remaining: Int?,
        val limit: Int?,
    )

    private fun searchIssues(query: String, me: String, token: String): SearchPage {
        val url = API.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("issues")
            .addQueryParameter("q", query)
            .addQueryParameter("sort", "updated")
            .addQueryParameter("order", "desc")
            .addQueryParameter("per_page", "50")
            .build()
            .toString()
        val resp = get(url, token)
        val root = JSONObject(resp.body)
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = parseIssueArray(arr, me)
        return SearchPage(
            items = items,
            totalCount = root.optInt("total_count", items.size),
            remaining = resp.remaining,
            limit = resp.limit,
        )
    }

    private suspend fun resolveToken(): String {
        authClient.validAccessToken()?.takeIf { it.isNotBlank() }?.let { return it }
        val stored = settings.githubToken.value.trim()
        if (stored.isNotBlank()) return stored
        return BuildConfig.GITHUB_TOKEN.trim()
    }

    private data class GhResponse(
        val body: String,
        val remaining: Int?,
        val limit: Int?,
    )

    private fun get(url: String, token: String): GhResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        okHttpClient.newCall(builder.build()).execute().use { response ->
            val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
            val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
            when (response.code) {
                401 -> {
                    if (authClient.isConnected()) authClient.markDisconnected()
                    throw GitHubNeedsAuthException()
                }
                403, 429 -> throw IOException("GitHub rate limited. Try again in a few minutes.")
                404 -> throw IOException("GitHub returned 404. Check OAuth scopes (repo + read:user).")
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub HTTP ${response.code}")
            }
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty GitHub response")
            return GhResponse(body, remaining, limit)
        }
    }

    private fun parseUser(o: JSONObject): GitHubUser = GitHubUser(
        login = o.str("login") ?: throw IOException("GitHub user missing login"),
        name = o.str("name"),
        avatarUrl = o.str("avatar_url"),
        htmlUrl = o.str("html_url") ?: "https://github.com/${o.str("login")}",
        bio = o.str("bio"),
        publicRepos = o.optInt("public_repos"),
        totalPrivateRepos = o.optInt("total_private_repos"),
        followers = o.optInt("followers"),
        following = o.optInt("following"),
    )

    private fun parseIssueArray(arr: JSONArray, me: String): List<GitHubIssue> {
        val out = ArrayList<GitHubIssue>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += parseIssue(o, me)
        }
        return out
    }

    private fun parseIssue(o: JSONObject, me: String): GitHubIssue {
        val user = o.optJSONObject("user")
        val assignees = o.optJSONArray("assignees")
        var assignedToMe = false
        if (assignees != null) {
            for (i in 0 until assignees.length()) {
                if (assignees.optJSONObject(i).str("login").equals(me, ignoreCase = true)) {
                    assignedToMe = true
                    break
                }
            }
        }
        return GitHubIssue(
            number = o.optInt("number"),
            title = o.str("title") ?: "",
            state = o.str("state") ?: "open",
            htmlUrl = o.str("html_url") ?: "",
            repoFullName = repoFullName(o),
            userLogin = user.str("login") ?: "unknown",
            userAvatarUrl = user.str("avatar_url"),
            comments = o.optInt("comments"),
            labels = parseLabels(o.optJSONArray("labels")),
            createdAt = o.str("created_at") ?: "",
            updatedAt = o.str("updated_at") ?: "",
            assignedToMe = assignedToMe,
            isPullRequest = o.optJSONObject("pull_request") != null,
            draft = o.optBoolean("draft", false),
        )
    }

    private fun GitHubIssue.toPull(me: String, reviewRequested: Boolean): GitHubPullRequest {
        return GitHubPullRequest(
            number = number,
            title = title,
            state = state,
            htmlUrl = htmlUrl,
            repoFullName = repoFullName,
            userLogin = userLogin,
            userAvatarUrl = userAvatarUrl,
            draft = draft,
            labels = labels,
            createdAt = createdAt,
            updatedAt = updatedAt,
            reviewRequested = reviewRequested,
            authoredByMe = userLogin.equals(me, ignoreCase = true),
        )
    }

    private fun parseRepos(body: String): List<GitHubRepo> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubRepo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ownerObj = o.optJSONObject("owner")
            out += GitHubRepo(
                owner = ownerObj.str("login") ?: "",
                name = o.str("name") ?: continue,
                fullName = o.str("full_name") ?: continue,
                description = o.str("description"),
                htmlUrl = o.str("html_url") ?: continue,
                language = o.str("language"),
                stars = o.optInt("stargazers_count"),
                forks = o.optInt("forks_count"),
                openIssuesCount = o.optInt("open_issues_count"),
                isPrivate = o.optBoolean("private", false),
                pushedAt = o.str("pushed_at"),
                ownerAvatarUrl = ownerObj.str("avatar_url"),
            )
        }
        return out
    }

    private fun parseEvents(body: String): List<GitHubActivity> {
        val arr = JSONArray(body)
        val out = ArrayList<GitHubActivity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseEvent(o)?.let { out += it }
        }
        return out
    }

    private fun parseEvent(o: JSONObject): GitHubActivity? {
        val type = o.str("type") ?: return null
        val repoName = o.optJSONObject("repo").str("name") ?: return null
        val actor = o.optJSONObject("actor")
        val payload = o.optJSONObject("payload")
        val createdAt = o.str("created_at") ?: ""
        val id = o.optLong("id").takeIf { it > 0 }?.toString() ?: "$type-$repoName-$createdAt"
        val repoUrl = "https://github.com/$repoName"

        val title: String
        val url: String?
        when (type) {
            "PushEvent" -> {
                val commits = payload?.optJSONArray("commits")
                val n = commits?.length() ?: 0
                val first = commits?.optJSONObject(0).str("message")
                    ?.lineSequence()?.firstOrNull()?.trim()
                val ref = payload.str("ref")?.removePrefix("refs/heads/")
                val head = payload.str("head")
                title = buildString {
                    append("$n commit")
                    if (n != 1) append("s")
                    if (!ref.isNullOrBlank()) append(" → $ref")
                    if (!first.isNullOrBlank()) append(" · $first")
                }
                url = if (!head.isNullOrBlank()) {
                    "https://github.com/$repoName/commit/$head"
                } else {
                    repoUrl
                }
            }
            "PullRequestEvent" -> {
                val pr = payload?.optJSONObject("pull_request")
                val action = payload.str("action") ?: "updated"
                val number = pr?.optInt("number") ?: 0
                title = "$action PR #$number ${pr.str("title").orEmpty()}".trim()
                url = pr.str("html_url")
            }
            "IssuesEvent" -> {
                val issue = payload?.optJSONObject("issue")
                val action = payload.str("action") ?: "updated"
                val number = issue?.optInt("number") ?: 0
                title = "$action issue #$number ${issue.str("title").orEmpty()}".trim()
                url = issue.str("html_url")
            }
            "IssueCommentEvent", "PullRequestReviewCommentEvent" -> {
                val issue = payload?.optJSONObject("issue") ?: payload?.optJSONObject("pull_request")
                val number = issue?.optInt("number") ?: 0
                title = "commented on #$number ${issue.str("title").orEmpty()}".trim()
                url = payload?.optJSONObject("comment").str("html_url") ?: issue.str("html_url")
            }
            "PullRequestReviewEvent" -> {
                val pr = payload?.optJSONObject("pull_request")
                val state = payload?.optJSONObject("review").str("state") ?: "reviewed"
                title = "$state PR #${pr?.optInt("number") ?: 0}"
                url = payload?.optJSONObject("review").str("html_url") ?: pr.str("html_url")
            }
            "CreateEvent" -> {
                val refType = payload.str("ref_type") ?: "ref"
                val ref = payload.str("ref")
                title = "created $refType${if (ref.isNullOrBlank()) "" else " $ref"}"
                url = repoUrl
            }
            "ReleaseEvent" -> {
                val rel = payload?.optJSONObject("release")
                title = "released ${rel.str("tag_name") ?: rel.str("name") ?: "release"}"
                url = rel.str("html_url")
            }
            "WatchEvent" -> {
                title = "starred $repoName"
                url = repoUrl
            }
            "ForkEvent" -> {
                title = "forked $repoName"
                url = payload?.optJSONObject("forkee").str("html_url")
            }
            "DeleteEvent" -> {
                val refType = payload.str("ref_type") ?: "ref"
                title = "deleted $refType ${payload.str("ref").orEmpty()}".trim()
                url = repoUrl
            }
            else -> {
                title = type.removeSuffix("Event")
                url = repoUrl
            }
        }

        return GitHubActivity(
            id = id,
            type = type,
            repoFullName = repoName,
            createdAt = createdAt,
            title = title,
            htmlUrl = url,
            actorLogin = actor.str("login") ?: "",
            actorAvatarUrl = actor.str("avatar_url"),
        )
    }

    private fun parseLabels(arr: JSONArray?): List<GitHubLabel> {
        if (arr == null) return emptyList()
        val out = ArrayList<GitHubLabel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("name") ?: continue
            out += GitHubLabel(name = name, color = o.optString("color", "8B949E"))
        }
        return out
    }

    private fun repoFullName(o: JSONObject): String {
        o.optJSONObject("repository")?.str("full_name")?.let { return it }
        o.str("repository_url")
            ?.removePrefix("https://api.github.com/repos/")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        o.str("html_url")?.let { url ->
            val parts = url.removePrefix("https://github.com/").split('/')
            if (parts.size >= 2) return "${parts[0]}/${parts[1]}"
        }
        return ""
    }

    private fun persistDiskCache(snapshot: GitHubSnapshot, userKey: String) {
        runCatching {
            prefs.edit {
                putString(KEY_SNAPSHOT, json.encodeToString(GitHubSnapshot.serializer(), snapshot))
                putLong(KEY_FETCH_TIME, lastFetchTime)
                putString(KEY_CACHE_USER, userKey)
                putInt(KEY_CACHE_VERSION, CACHE_VERSION)
            }
        }.onFailure { Log.w(TAG, "Failed to persist GitHub cache", it) }
    }

    private fun restoreDiskCache() {
        if (prefs.getInt(KEY_CACHE_VERSION, 0) != CACHE_VERSION) return
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return
        runCatching {
            cached = json.decodeFromString(GitHubSnapshot.serializer(), raw)
            lastFetchTime = prefs.getLong(KEY_FETCH_TIME, 0L)
            cachedUserKey = prefs.getString(KEY_CACHE_USER, "") ?: ""
        }.onFailure {
            Log.w(TAG, "Discarding GitHub disk cache", it)
            cached = null
            lastFetchTime = 0L
        }
    }
}

private fun JSONObject?.str(key: String): String? {
    if (this == null) return null
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}
