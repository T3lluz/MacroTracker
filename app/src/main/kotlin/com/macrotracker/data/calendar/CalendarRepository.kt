package com.macrotracker.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val calendarColor: Int,
    val isAllDay: Boolean,
    val description: String = "",
    val customAppUri: String = "",
) {
    /** Extract the first URL (meeting link) from description or customAppUri */
    val meetingLink: String?
        get() {
            if (customAppUri.isNotBlank()) return customAppUri
            val urlPattern = Regex("""https?://\S+""")
            return urlPattern.find(description)?.value
        }

    val formattedTime: String
        get() {
            if (isAllDay) return "All day"
            val fmt = DateTimeFormatter.ofPattern("h:mm a")
            return "${startTime.format(fmt)} – ${endTime.format(fmt)}"
        }

    val formattedStartTime: String
        get() {
            if (isAllDay) return "All day"
            return startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        }

    val isHappeningNow: Boolean
        get() {
            val now = LocalDateTime.now()
            return now.isAfter(startTime) && now.isBefore(endTime)
        }

    /** e.g. "Tomorrow", "Wednesday", "Mar 12" */
    val relativeDay: String
        get() {
            val today = LocalDate.now()
            val eventDate = startTime.toLocalDate()
            val daysUntil = ChronoUnit.DAYS.between(today, eventDate)
            return when {
                daysUntil == 0L -> "Today"
                daysUntil == 1L -> "Tomorrow"
                daysUntil in 2..6 -> eventDate.dayOfWeek.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                else -> eventDate.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }

    /** e.g. "Tomorrow · 9:00 AM – 10:00 AM" */
    val formattedDateAndTime: String
        get() = "$relativeDay · $formattedTime"
}

@Singleton
class CalendarRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CalendarRepo"
        const val PERMISSION = Manifest.permission.READ_CALENDAR
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Read calendar events for today and optionally the next [extraDays] days.
     */
    suspend fun readEvents(extraDays: Int = 0): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            Log.w(TAG, "Calendar permission not granted")
            return@withContext emptyList()
        }

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfRange = LocalDate.now().plusDays(1L + extraDays).atStartOfDay(zone).toInstant().toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CUSTOM_APP_URI,
        )

        val events = mutableListOf<CalendarEvent>()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startOfDay)
        ContentUris.appendId(builder, endOfRange)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )

            cursor?.let {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val title = it.getString(1) ?: "(No title)"
                    val begin = it.getLong(2)
                    val end = it.getLong(3)
                    val location = it.getString(4) ?: ""
                    val color = it.getInt(5)
                    val allDay = it.getInt(6) == 1
                    val description = it.getString(7) ?: ""
                    val customAppUri = it.getString(8) ?: ""

                    val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone)
                    val endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), zone)

                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            startTime = startDt,
                            endTime = endDt,
                            location = location,
                            calendarColor = color,
                            isAllDay = allDay,
                            description = description,
                            customAppUri = customAppUri,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read calendar events: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        events
    }
}


