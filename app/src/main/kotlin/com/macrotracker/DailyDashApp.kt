package com.macrotracker

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltAndroidApp
class DailyDashApp : Application(), ImageLoaderFactory {

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
            .crossfade(true)
            .build()
    }
}

