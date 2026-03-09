package com.macrotracker.di

import android.content.Context
import androidx.room.Room
import com.macrotracker.data.f1.F1ApiService
import com.macrotracker.data.f1.F1ApiServiceImpl
import com.macrotracker.data.f1.F1Repository
import com.macrotracker.data.f1.F1RepositoryImpl
import com.macrotracker.data.local.MacroDao
import com.macrotracker.data.local.MacroDatabase
import com.macrotracker.data.youtube.YouTubeRepository
import com.macrotracker.data.youtube.YouTubeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MacroDatabase =
        Room.databaseBuilder(context, MacroDatabase::class.java, "macro_tracker.db").build()

    @Provides
    @Singleton
    fun provideDao(db: MacroDatabase): MacroDao = db.macroDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideKtorClient(okHttpClient: OkHttpClient): HttpClient =
        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            defaultRequest {
                url("https://api.openf1.org/v1/")
                headers.append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class F1DataModule {
    @Binds @Singleton abstract fun bindF1ApiService(impl: F1ApiServiceImpl): F1ApiService
    @Binds @Singleton abstract fun bindF1Repository(impl: F1RepositoryImpl): F1Repository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeDataModule {
    @Binds @Singleton abstract fun bindYouTubeRepository(impl: YouTubeRepositoryImpl): YouTubeRepository
}
