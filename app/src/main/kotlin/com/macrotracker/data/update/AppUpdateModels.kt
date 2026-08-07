package com.macrotracker.data.update

/**
 * A newer build discovered on GitHub Releases.
 *
 * APK assets are expected to be named:
 * `DailyDash-{versionName}-vc{versionCode}.apk`
 * e.g. `DailyDash-1.1.3-vc3.apk`
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkBytes: Long?,
    val htmlUrl: String,
    val tagName: String,
)

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data object Checking : AppUpdateUiState()
    data object UpToDate : AppUpdateUiState()
    data class Available(val info: AppUpdateInfo) : AppUpdateUiState()
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : AppUpdateUiState()
    data class ReadyToInstall(val info: AppUpdateInfo, val apkPath: String) : AppUpdateUiState()
    data class Error(val message: String) : AppUpdateUiState()
}
