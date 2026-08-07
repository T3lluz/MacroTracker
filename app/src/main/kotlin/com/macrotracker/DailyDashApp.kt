package com.macrotracker

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.macrotracker.widget.WidgetRefreshWorker
import com.macrotracker.widget.WidgetStateProvider
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltAndroidApp
class DailyDashApp : Application(), ImageLoaderFactory {

    companion object {
        /** Survives Activity recreation within the same process (e.g. widget re-launch). */
        @Volatile
        var splashShownThisProcess: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        if (WidgetStateProvider.hasAnyWidget(this)) {
            WidgetRefreshWorker.enqueuePeriodicRefresh(this)
            // Periodic worker covers freshness; skip an immediate full refresh on every cold start.
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Inject a browser-like User-Agent so the F1 media CDN serves real driver
        // headshots instead of the fallback placeholder image.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request: Request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
