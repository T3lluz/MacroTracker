# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.macrotracker.data.remote.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-keep class dagger.hilt.** { *; }

