package com.macrotracker.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class HealthStats(
    val steps: Long = 0,
    val avgHeartRate: Long = 0,
    val sleepMinutes: Long = 0,
    val activeCaloriesBurned: Double = 0.0,
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
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
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

    suspend fun readTodayStats(): HealthStats = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext HealthStats()

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = LocalDateTime.now().atZone(zone).toInstant()
        val todayRange = TimeRangeFilter.between(startOfDay, now)

        // For sleep, look back from midnight to capture last night's sleep
        val sleepStart = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val sleepRange = TimeRangeFilter.between(sleepStart, now)

        val steps = readSteps(hc, todayRange)
        val avgHr = readAvgHeartRate(hc, todayRange)
        val sleepMin = readSleepMinutes(hc, sleepRange)
        val activeCal = readActiveCaloriesBurned(hc, todayRange)

        HealthStats(
            steps = steps,
            avgHeartRate = avgHr,
            sleepMinutes = sleepMin,
            activeCaloriesBurned = activeCal,
        )
    }

    private suspend fun readSteps(
        hc: HealthConnectClient,
        range: TimeRangeFilter,
    ): Long = try {
        val response = hc.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRangeFilter = range),
        )
        response.records.sumOf { it.count }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read steps: ${e.message}")
        0
    }

    private suspend fun readAvgHeartRate(
        hc: HealthConnectClient,
        range: TimeRangeFilter,
    ): Long = try {
        val response = hc.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range),
        )
        val allSamples = response.records.flatMap { it.samples }
        if (allSamples.isNotEmpty()) {
            allSamples.map { it.beatsPerMinute }.average().toLong()
        } else 0
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read heart rate: ${e.message}")
        0
    }

    private suspend fun readSleepMinutes(
        hc: HealthConnectClient,
        range: TimeRangeFilter,
    ): Long = try {
        val response = hc.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range),
        )
        response.records.sumOf { session ->
            java.time.Duration.between(session.startTime, session.endTime).toMinutes()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read sleep: ${e.message}")
        0
    }

    private suspend fun readActiveCaloriesBurned(
        hc: HealthConnectClient,
        range: TimeRangeFilter,
    ): Double = try {
        val response = hc.readRecords(
            ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = range),
        )
        response.records.sumOf { it.energy.inKilocalories }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read active calories burned: ${e.message}")
        0.0
    }
}


