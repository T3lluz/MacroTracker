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
