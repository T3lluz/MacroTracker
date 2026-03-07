package com.macrotracker.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class HealthStats(
    val steps: Long = 0,
    val avgHeartRate: Long = 0,
    val sleepMinutes: Long = 0,
    val totalCaloriesBurned: Double = 0.0,
)

data class DailyHealthStats(
    val date: LocalDate,
    val stats: HealthStats,
)

@Singleton
class HealthConnectRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "HealthConnectRepo"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
    }

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                Log.w(TAG, "Health Connect SDK not available")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get HealthConnectClient: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = client != null

    suspend fun hasAllPermissions(): Boolean {
        val hc = client ?: return false
        return try {
            val granted = hc.permissionController.getGrantedPermissions()
            PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            false
        }
    }

    /**
     * Reads today's stats using the Aggregate API.
     */
    suspend fun readTodayStats(): HealthStats = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext HealthStats()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val todayRange = TimeRangeFilter.between(startOfDay, now)

        val sleepStart = LocalDate.now(zone).minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepRange = TimeRangeFilter.between(sleepStart, now)

        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ),
                    timeRangeFilter = todayRange,
                ),
            )

            val sleepResponse = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = sleepRange,
                ),
            )

            HealthStats(
                steps = response[StepsRecord.COUNT_TOTAL] ?: 0L,
                avgHeartRate = response[HeartRateRecord.BPM_AVG] ?: 0L,
                sleepMinutes = sleepResponse[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L,
                totalCaloriesBurned = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to aggregate health stats: ${e.message}", e)
            HealthStats()
        }
    }

    /**
     * Reads historical stats up to [days] ago using the AggregateGroupByPeriod API.
     */
    suspend fun readHistoryStats(days: Int = 7): List<DailyHealthStats> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = today.minusDays(days - 1L)
        readHistoryStatsBetween(startDate, today)
    }

    suspend fun readHistoryStatsBetween(startDate: LocalDate, endDate: LocalDate): List<DailyHealthStats> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        
        val startDateTime = startDate.atStartOfDay()
        val idealEndDateTime = endDate.plusDays(1).atStartOfDay()
        val now = LocalDateTime.now(zone)

        // Prevent querying into the future
        val endDateTime = if (idealEndDateTime.isAfter(now)) now else idealEndDateTime

        val dailyStatsMap = mutableMapOf<LocalDate, HealthStats>()

        // Only query if start date is actually before or equal to right now
        if (!startDateTime.isAfter(now)) {
            try {
                // BUGFIX: MUST use LocalDateTime when TimeRangeSlicer is a Period
                val range = TimeRangeFilter.between(startDateTime, endDateTime)

                val response = hc.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            SleepSessionRecord.SLEEP_DURATION_TOTAL
                        ),
                        timeRangeFilter = range,
                        timeRangeSlicer = Period.ofDays(1)
                    )
                )

                response.forEach { bucket ->
                    val bucketDate = bucket.startTime.toLocalDate()
                    val result = bucket.result

                    val steps = result[StepsRecord.COUNT_TOTAL] ?: 0L
                    val hr = result[HeartRateRecord.BPM_AVG] ?: 0L
                    val cals = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                    val sleepMin = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L

                    dailyStatsMap[bucketDate] = HealthStats(steps, hr, sleepMin, cals)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to aggregate history stats: ${e.message}", e)
            }
        }

        val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        (0 until days).map { i ->
            val date = startDate.plusDays(i.toLong())
            DailyHealthStats(
                date = date,
                stats = dailyStatsMap[date] ?: HealthStats()
            )
        }
    }

    /**
     * Reads detailed intraday heart rate data for a specific day.
     */
    suspend fun readHeartRateIntraday(date: LocalDate): List<HeartRateRecord.Sample> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        
        // From start of the day to the start of the next day
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            response.records.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read intraday HR", e)
            emptyList()
        }
    }

    /**
     * Reads detailed sleep sessions (and their stages) for a specific day.
     */
    suspend fun readSleepSessions(date: LocalDate): List<SleepSessionRecord> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        
        // A standard logical "sleep day" starts the evening before (e.g., 6 PM to 6 PM)
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end = date.atTime(18, 0).atZone(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sleep sessions", e)
            emptyList()
        }
    }
}
