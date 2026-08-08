package com.macrotracker.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.macrotracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "AppUpdate"
        private const val OWNER = "T3lluz"
        private const val REPO = "MacroTracker"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        private const val RELEASES_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20"
        private const val PREFS = "app_update_prefs"
        private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
        private const val KEY_LAST_CHECK_MS = "last_check_ms"

        /**
         * While the app is in the foreground, poll GitHub this often so a newly
         * published release prompts in-app quickly.
         */
        const val FOREGROUND_POLL_INTERVAL_MS = 2L * 60L * 1000L

        /** Minimum gap between network checks (avoids hammering on rapid resume). */
        const val MIN_CHECK_INTERVAL_MS = 30L * 1000L

        /**
         * Asset naming contract used by CI and the client:
         * DailyDash-1.1.3-vc3.apk
         */
        private val APK_NAME_REGEX =
            Regex("""DailyDash-([0-9]+(?:\.[0-9]+)*)-vc(\d+)\.apk""", RegexOption.IGNORE_CASE)
    }

    // Metadata calls reuse the app client; APK downloads use a quiet long-timeout client
    // so debug BODY logging never dumps a 30MB binary into logcat.
    private val apiClient = okHttpClient
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val updatesDir: File
        get() = File(context.cacheDir, "app_updates").also { it.mkdirs() }

    fun currentVersionName(): String = BuildConfig.VERSION_NAME
    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    fun shouldAutoCheck(minIntervalMs: Long = MIN_CHECK_INTERVAL_MS): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        return System.currentTimeMillis() - last >= minIntervalMs
    }

    fun markCheckedNow() {
        prefs.edit { putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()) }
    }

    fun dismiss(versionCode: Int) {
        prefs.edit { putInt(KEY_DISMISSED_VERSION_CODE, versionCode) }
    }

    fun isDismissed(versionCode: Int): Boolean =
        prefs.getInt(KEY_DISMISSED_VERSION_CODE, -1) == versionCode

    /**
     * Fetches the latest GitHub Release that contains a DailyDash APK asset
     * with a higher [versionCode] than the installed build.
     */
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        markCheckedNow()
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        apiClient.newCall(request).execute().use { response ->
            if (response.code == 404) {
                Log.i(TAG, "No GitHub releases published yet")
                return@withContext null
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub releases HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext null
            parseLatestRelease(body)?.let { formatUpdateInfo(it) }
        }
    }

    /**
     * Fetches recent published releases for the Settings changelog dropdown.
     */
    suspend fun listReleaseNotes(): List<AppReleaseNotes> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        apiClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext emptyList()
            if (!response.isSuccessful) {
                throw IOException("GitHub releases list HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext emptyList()
            parseReleaseList(body)
        }
    }

    internal fun parseLatestRelease(json: String): AppUpdateInfo? {
        val root = JSONObject(json)
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
            return null
        }
        return parseReleaseObject(root)
            ?.takeIf { it.apkDownloadUrl.isNotBlank() && it.versionCode > currentVersionCode() }
    }

    internal fun parseReleaseList(json: String): List<AppReleaseNotes> {
        val arr = JSONArray(json)
        val out = mutableListOf<AppReleaseNotes>()
        for (i in 0 until arr.length()) {
            val root = arr.optJSONObject(i) ?: continue
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) continue
            val info = parseReleaseObject(root) ?: continue
            val formatted = formatUpdateInfo(info)
            out += AppReleaseNotes(
                versionName = formatted.versionName,
                versionCode = formatted.versionCode,
                tagName = formatted.tagName,
                releaseNotes = formatted.releaseNotes,
                htmlUrl = formatted.htmlUrl,
                publishedAt = root.optString("published_at").takeIf { it.isNotBlank() },
                isNewerThanInstalled = formatted.versionCode > currentVersionCode(),
            )
        }
        return out.sortedByDescending { it.versionCode }
    }

    private fun formatUpdateInfo(info: AppUpdateInfo): AppUpdateInfo =
        info.copy(
            releaseNotes = ReleaseNotesFormatter.format(
                raw = info.releaseNotes,
                htmlUrl = info.htmlUrl,
            ),
        )

    private fun parseReleaseObject(root: JSONObject): AppUpdateInfo? {
        val tagName = root.optString("tag_name").orEmpty()
        val htmlUrl = root.optString("html_url").orEmpty()
        val releaseNotes = root.optString("body").orEmpty().trim()
        val assets = root.optJSONArray("assets")

        var best: AppUpdateInfo? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").orEmpty()
                val match = APK_NAME_REGEX.matchEntire(name) ?: continue
                val versionName = match.groupValues[1]
                val versionCode = match.groupValues[2].toIntOrNull() ?: continue
                val url = asset.optString("browser_download_url").orEmpty()
                if (url.isBlank()) continue
                val size = asset.optLong("size").takeIf { it > 0 }

                if (best == null || versionCode > best.versionCode) {
                    best = AppUpdateInfo(
                        versionName = versionName,
                        versionCode = versionCode,
                        releaseNotes = releaseNotes.ifBlank { "Bug fixes and improvements." },
                        apkDownloadUrl = url,
                        apkBytes = size,
                        htmlUrl = htmlUrl,
                        tagName = tagName.ifBlank { "v$versionName" },
                    )
                }
            }
        }

        if (best != null) return best

        // Fallback for changelog entries without a parseable APK asset yet.
        val fallbackVersion = tagName.removePrefix("v").trim()
        if (fallbackVersion.isBlank()) return null
        val codeGuess = fallbackVersion.split('.')
            .mapNotNull { it.toIntOrNull() }
            .fold(0) { acc, n -> acc * 100 + n }
            .coerceAtLeast(0)
        return AppUpdateInfo(
            versionName = fallbackVersion,
            versionCode = codeGuess,
            releaseNotes = releaseNotes.ifBlank { "Bug fixes and improvements." },
            apkDownloadUrl = "",
            apkBytes = null,
            htmlUrl = htmlUrl,
            tagName = tagName.ifBlank { "v$fallbackVersion" },
        )
    }

    /**
     * Downloads the APK to cache. [onProgress] reports 0f..1f when content length is known.
     */
    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (info.apkDownloadUrl.isBlank()) {
            throw IOException("No APK download URL for ${info.versionName}")
        }
        updatesDir.listFiles()?.forEach { it.delete() }
        val outFile = File(updatesDir, "DailyDash-${info.versionName}-vc${info.versionCode}.apk")

        val request = Request.Builder()
            .url(info.apkDownloadUrl)
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty download body")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkBytes ?: -1L
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        }

        if (!outFile.exists() || outFile.length() < 1_000L) {
            outFile.delete()
            throw IOException("Downloaded APK is missing or too small")
        }
        onProgress(1f)
        outFile
    }

    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun installPermissionSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            throw IOException("APK file not found: ${apkFile.absolutePath}")
        }
        // Always use PackageInstaller self-update sessions. Never fall back to
        // ACTION_VIEW — that opens the system Package Installer and always shows
        // Play Protect's "Scan app" UI for sideloaded APKs.
        //
        // On Android 12+ with UPDATE_PACKAGES_WITHOUT_USER_ACTION, same-package
        // upgrades can commit without any system confirmation when the device
        // treats this as a self-update (or we are installer/update-owner).
        installWithPackageInstaller(apkFile)
    }

    private fun installWithPackageInstaller(apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val apkBytes = apkFile.length()
        logInstallSource()
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            setAppLabel("DailyDash")
            setSize(apkBytes)
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Request silent commit for same-package self-updates.
                // Note: Android throttles repeated silent updates (~1h). Within that
                // window the system falls back to a one-tap confirmation UI.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Avoid PACKAGE_SOURCE_DOWNLOADED_FILE / LOCAL_FILE paths that
                // Android 15+ treats as high-friction internet sideloads.
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Keep installer-of-record as DailyDash so later self-updates qualify
                // for USER_ACTION_NOT_REQUIRED when ownership/installer checks apply.
                setInstallerPackageName(context.packageName)
            }
        }

        val sessionId = installer.createSession(params)
        Log.i(TAG, "Created PackageInstaller session=$sessionId for ${apkFile.name} ($apkBytes bytes)")
        try {
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apkBytes).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                // Activity PendingIntent (not BroadcastReceiver): OEM background
                // restrictions often block starting confirmation / relaunch from a receiver.
                val callback = Intent(context, UpdateInstallActivity::class.java).apply {
                    action = UpdateInstallActivity.ACTION_INSTALL_COMPLETE
                    // Exclude from recents; relaunch path opens MainActivity on success.
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Mutable: PackageInstaller fills EXTRA_STATUS / EXTRA_INTENT.
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val pending = PendingIntent.getActivity(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun logInstallSource() {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            val info = context.packageManager.getInstallSourceInfo(context.packageName)
            Log.i(
                TAG,
                "InstallSource installing=${info.installingPackageName} " +
                    "initiating=${info.initiatingPackageName} " +
                    "originating=${info.originatingPackageName}" +
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        " updateOwner=${info.updateOwnerPackageName}"
                    } else {
                        ""
                    },
            )
            val canSilent = context.checkSelfPermission(
                android.Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION,
            ) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "UPDATE_PACKAGES_WITHOUT_USER_ACTION granted=$canSilent")
        }.onFailure { Log.w(TAG, "Could not read install source", it) }
    }
}
