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

# Keep GitHub dashboard models for kotlinx.serialization disk cache
-keep class com.macrotracker.data.github.** { *; }

# Optional logging binder pulled in transitively; not shipped on Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# In-app update PackageInstaller callback + post-replace open prompt
-keep class com.macrotracker.data.update.UpdateInstallActivity { *; }
-keep class com.macrotracker.data.update.PackageReplacedReceiver { *; }

# ── JSch (server monitor SSH) ────────────────────────────────────────────
# JSch resolves cipher/KEX/MAC implementations by class name from its config
# table, so R8 cannot see those references and would strip them.
-keep class com.jcraft.jsch.** { *; }
# Optional integrations JSch declares but Android never provides.
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.**
