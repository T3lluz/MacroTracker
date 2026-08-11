# Moshi / API response classes
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.macrotracker.data.remote.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-keep class dagger.hilt.** { *; }

# Keep F1 data classes for Ktor serialization
-keep class com.macrotracker.data.f1.** { *; }

# Optional logging binder pulled in transitively; not shipped on Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# In-app update PackageInstaller callback + post-replace open prompt
-keep class com.macrotracker.data.update.UpdateInstallActivity { *; }
-keep class com.macrotracker.data.update.PackageReplacedReceiver { *; }
