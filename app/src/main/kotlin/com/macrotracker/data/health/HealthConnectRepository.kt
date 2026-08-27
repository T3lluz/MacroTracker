package com.macrotracker.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class HealthStats(
    val steps: Long = 0,
    val avgHeartRate: Long = 0,
    val sleepMinutes: Long = 0,
    val totalCaloriesBurned: Double = 0.0,
    val activeCaloriesBurned: Double = 0.0,
    val restingHeartRate: Long = 0,
    val oxygenSaturation: Double = 0.0,
    val respiratoryRate: Double = 0.0,
    val distance: Double = 0.0,
    val floorsClimbed: Double = 0.0,
)

data class DailyHealthStats(
    val date: LocalDate,
    val stats: HealthStats,
)

@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "HealthConnectRepo"

        /** Sparse overnight vitals (RHR / SpO2 / resp) often land just after midnight. */
        private val SPARSE_VITAL_LOOKBACK: Duration = Duration.ofHours(36)

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
        )

        val STEPS_PERMISSION = HealthPermission.getReadPermission(StepsRecord::class)
        val HEART_RATE_PERMISSION = HealthPermission.getReadPermission(HeartRateRecord::class)
        val SLEEP_PERMISSION = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val TOTAL_CALORIES_PERMISSION = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val ACTIVE_CALORIES_PERMISSION = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val RESTING_HEART_RATE_PERMISSION = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val OXYGEN_SATURATION_PERMISSION = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val RESPIRATORY_RATE_PERMISSION = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        val DISTANCE_PERMISSION = HealthPermission.getReadPermission(DistanceRecord::class)
        val FLOORS_PERMISSION = HealthPermission.getReadPermission(FloorsClimbedRecord::class)
        val EXERCISE_PERMISSION = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val ELEVATION_PERMISSION = HealthPermission.getReadPermission(ElevationGainedRecord::class)
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

    // ── Lightweight in-memory cache for individual metric reads ───────────
    private val METRIC_CACHE_TTL = 5 * 60 * 1000L // 5 minutes
    private val metricCache = mutableMapOf<String, Pair<Long, Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String): T? {
        val (ts, value) = metricCache[key] ?: return null
        return if (System.currentTimeMillis() - ts < METRIC_CACHE_TTL) value as? T else null
    }

    private fun putCache(key: String, value: Any?) {
        metricCache[key] = System.currentTimeMillis() to value
    }

    fun clearMetricCache() {
        metricCache.clear()
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val hc = client ?: return emptySet()
        return try {
            hc.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading granted permissions: ${e.message}")
            emptySet()
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return PERMISSIONS.all { it in granted }
    }

    /** True when at least one Health Connect read permission we request is granted. */
    suspend fun hasAnyPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return PERMISSIONS.any { it in granted }
    }

    suspend fun hasPermission(permission: String): Boolean {
        return permission in getGrantedPermissions()
    }

    suspend fun hasPermissions(permissions: Set<String>): Boolean {
        if (permissions.isEmpty()) return true
        val granted = getGrantedPermissions()
        return permissions.all { it in granted }
    }

    suspend fun revokeAllPermissions() {
        val hc = client ?: return
        try {
            hc.permissionController.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke Health Connect permissions: ${e.message}")
        }
    }

    // ── Per-metric reads (DashboardViewModel) ──────────────────────────────

    suspend fun getLatestHeartRate(yesterday: Boolean = false): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "hr_${if (yesterday) "yesterday" else "today"}"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = if (yesterday) getYesterdayTimeRange() else getTodayTimeRange()
        try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.samples?.lastOrNull()?.beatsPerMinute
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest heart rate", e)
            null
        }
    }

    suspend fun getLatestRestingHeartRate(yesterday: Boolean = false): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "rhr_${if (yesterday) "yesterday" else "today"}"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.beatsPerMinute
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest resting heart rate", e)
            null
        }
    }

    suspend fun getLatestOxygenSaturation(yesterday: Boolean = false): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "spo2_${if (yesterday) "yesterday" else "today"}"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.percentage?.value
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest Oxygen Saturation", e)
            null
        }
    }

    suspend fun getLatestRespiratoryRate(yesterday: Boolean = false): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "resp_${if (yesterday) "yesterday" else "today"}"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.rate
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest Respiratory Rate", e)
            null
        }
    }

    suspend fun getStepsToday(): Long? = getStepsForDate(LocalDate.now())
    suspend fun getStepsYesterday(): Long? = getStepsForDate(LocalDate.now().minusDays(1))

    suspend fun getActiveCaloriesToday(): Double? = getActiveCaloriesForDate(LocalDate.now())
    suspend fun getActiveCaloriesYesterday(): Double? = getActiveCaloriesForDate(LocalDate.now().minusDays(1))

    suspend fun getDistanceToday(): Double? = getDistanceForDate(LocalDate.now())
    suspend fun getDistanceYesterday(): Double? = getDistanceForDate(LocalDate.now().minusDays(1))

    suspend fun getFloorsClimbedToday(): Double? = getFloorsClimbedForDate(LocalDate.now())
    suspend fun getFloorsClimbedYesterday(): Double? = getFloorsClimbedForDate(LocalDate.now().minusDays(1))

    private suspend fun getStepsForDate(date: LocalDate): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "steps_$date"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[StepsRecord.COUNT_TOTAL]
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get steps for $date", e)
            null
        }
    }

    private suspend fun getActiveCaloriesForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "cals_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active calories for $date", e)
            null
        }
    }

    private suspend fun getDistanceForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "dist_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get distance for $date", e)
            null
        }
    }

    private suspend fun getFloorsClimbedForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "floors_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get floors climbed for $date", e)
            null
        }
    }

    private fun getTimeRangeForDate(date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return start to end
    }

    private fun getTodayTimeRange(): Pair<Instant, Instant> = getTimeRangeForDate(LocalDate.now())

    private fun getYesterdayTimeRange(): Pair<Instant, Instant> =
        getTimeRangeForDate(LocalDate.now().minusDays(1))

    /**
     * Calendar day for yesterday; for "today" use a 36h lookback so overnight
     * SpO2 / resp / RHR samples still surface in the morning.
     */
    private fun sparseVitalRange(yesterday: Boolean): Pair<Instant, Instant> {
        if (yesterday) return getYesterdayTimeRange()
        val end = Instant.now()
        val start = end.minus(SPARSE_VITAL_LOOKBACK)
        return start to end
    }

    /**
     * Reads today's stats using the Aggregate API + latest sparse vitals.
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
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
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

            val restingHeartRate = getLatestRestingHeartRate() ?: 0L
            val oxygenSaturation = getLatestOxygenSaturation() ?: 0.0
            val respiratoryRate = getLatestRespiratoryRate() ?: 0.0

            HealthStats(
                steps = response[StepsRecord.COUNT_TOTAL] ?: 0L,
                avgHeartRate = response[HeartRateRecord.BPM_AVG] ?: 0L,
                sleepMinutes = sleepResponse[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L,
                totalCaloriesBurned = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
                activeCaloriesBurned = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
                restingHeartRate = restingHeartRate,
                oxygenSaturation = oxygenSaturation,
                respiratoryRate = respiratoryRate,
                distance = response[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0,
                floorsClimbed = response[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to aggregate health stats: ${e.message}", e)
            HealthStats()
        }
    }

    /**
     * Historical daily stats. Aggregates bulk metrics, then enriches with
     * overnight sleep + sparse vitals via range reads (not N×day IPC).
     */
    suspend fun readHistoryStatsBetween(startDate: LocalDate, endDate: LocalDate): List<DailyHealthStats> =
        withContext(Dispatchers.IO) {
            val hc = client ?: return@withContext emptyList()
            val zone = ZoneId.systemDefault()

            val startDateTime = startDate.atStartOfDay()
            val idealEndDateTime = endDate.plusDays(1).atStartOfDay()
            val now = LocalDateTime.now(zone)
            val endDateTime = if (idealEndDateTime.isAfter(now)) now else idealEndDateTime

            val dailyStatsMap = mutableMapOf<LocalDate, HealthStats>()

            if (!startDateTime.isAfter(now)) {
                try {
                    val range = TimeRangeFilter.between(startDateTime, endDateTime)

                    val response = hc.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = setOf(
                                StepsRecord.COUNT_TOTAL,
                                HeartRateRecord.BPM_AVG,
                                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                                DistanceRecord.DISTANCE_TOTAL,
                                FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                                RestingHeartRateRecord.BPM_AVG,
                            ),
                            timeRangeFilter = range,
                            timeRangeSlicer = Period.ofDays(1),
                        ),
                    )

                    response.forEach { bucket ->
                        val bucketDate = bucket.startTime.toLocalDate()
                        val result = bucket.result
                        dailyStatsMap[bucketDate] = HealthStats(
                            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
                            avgHeartRate = result[HeartRateRecord.BPM_AVG] ?: 0L,
                            totalCaloriesBurned = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
                            activeCaloriesBurned = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                                ?: 0.0,
                            restingHeartRate = result[RestingHeartRateRecord.BPM_AVG] ?: 0L,
                            distance = result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0,
                            floorsClimbed = result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to aggregate history stats: ${e.message}", e)
                    // Retry without RHR aggregate (some providers reject unknown metrics).
                    try {
                        val range = TimeRangeFilter.between(startDateTime, endDateTime)
                        val response = hc.aggregateGroupByPeriod(
                            AggregateGroupByPeriodRequest(
                                metrics = setOf(
                                    StepsRecord.COUNT_TOTAL,
                                    HeartRateRecord.BPM_AVG,
                                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                                    DistanceRecord.DISTANCE_TOTAL,
                                    FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                                ),
                                timeRangeFilter = range,
                                timeRangeSlicer = Period.ofDays(1),
                            ),
                        )
                        response.forEach { bucket ->
                            val bucketDate = bucket.startTime.toLocalDate()
                            val result = bucket.result
                            dailyStatsMap[bucketDate] = HealthStats(
                                steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
                                avgHeartRate = result[HeartRateRecord.BPM_AVG] ?: 0L,
                                totalCaloriesBurned = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                                    ?: 0.0,
                                activeCaloriesBurned = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                                    ?.inKilocalories ?: 0.0,
                                distance = result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0,
                                floorsClimbed = result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0,
                            )
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed history aggregate retry: ${e2.message}", e2)
                    }
                }

                enrichHistoryWithSleep(hc, startDate, endDate, zone, dailyStatsMap)
                enrichHistoryWithSparseVitals(hc, startDate, endDate, zone, dailyStatsMap)
            }

            val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            (0 until days).map { i ->
                val date = startDate.plusDays(i.toLong())
                DailyHealthStats(
                    date = date,
                    stats = dailyStatsMap[date] ?: HealthStats(),
                )
            }
        }

    /**
     * Assign sleep to the logical sleep-day (prev day 18:00 → day 18:00), matching today stats.
     */
    private suspend fun enrichHistoryWithSleep(
        hc: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        dailyStatsMap: MutableMap<LocalDate, HealthStats>,
    ) {
        try {
            val sleepWindowStart = startDate.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val sleepWindowEnd = endDate.plusDays(1).atTime(18, 0).atZone(zone).toInstant()
                .coerceAtMost(Instant.now())
            if (!sleepWindowStart.isBefore(sleepWindowEnd)) return

            val sessions = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, sleepWindowEnd),
                ),
            ).records

            val sleepByDay = mutableMapOf<LocalDate, Long>()
            for (session in sessions) {
                val endLocal = session.endTime.atZone(zone)
                // Sleep ending before 18:00 counts for that calendar day; after 18:00 → next day.
                val sleepDay = if (endLocal.toLocalTime().isBefore(java.time.LocalTime.of(18, 0))) {
                    endLocal.toLocalDate()
                } else {
                    endLocal.toLocalDate().plusDays(1)
                }
                if (sleepDay.isBefore(startDate) || sleepDay.isAfter(endDate)) continue
                val minutes = ChronoUnit.MINUTES.between(session.startTime, session.endTime).coerceAtLeast(0)
                sleepByDay[sleepDay] = (sleepByDay[sleepDay] ?: 0L) + minutes
            }

            sleepByDay.forEach { (date, minutes) ->
                val existing = dailyStatsMap[date] ?: HealthStats()
                dailyStatsMap[date] = existing.copy(sleepMinutes = minutes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich sleep history: ${e.message}", e)
        }
    }

    /**
     * One range read each for SpO2 / respiratory / RHR fallback — bucket by local date.
     */
    private suspend fun enrichHistoryWithSparseVitals(
        hc: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        dailyStatsMap: MutableMap<LocalDate, HealthStats>,
    ) {
        val start = startDate.atStartOfDay(zone).toInstant()
        val end = endDate.plusDays(1).atStartOfDay(zone).toInstant().coerceAtMost(Instant.now())
        if (!start.isBefore(end)) return

        // RHR fallback when aggregate left zeros
        try {
            val rhrRecords = hc.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            val rhrByDay = rhrRecords
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, records) ->
                    records.map { it.beatsPerMinute }.average().toLong()
                }
            rhrByDay.forEach { (date, bpm) ->
                if (date !in dailyStatsMap.keys && (date.isBefore(startDate) || date.isAfter(endDate))) return@forEach
                if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                val existing = dailyStatsMap[date] ?: HealthStats()
                if (existing.restingHeartRate <= 0 && bpm > 0) {
                    dailyStatsMap[date] = existing.copy(restingHeartRate = bpm)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich RHR history: ${e.message}", e)
        }

        try {
            val spo2Records = hc.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            spo2Records
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (date, records) ->
                    if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                    val avg = records.mapNotNull { it.percentage?.value }.average().takeIf { !it.isNaN() } ?: 0.0
                    if (avg <= 0) return@forEach
                    val existing = dailyStatsMap[date] ?: HealthStats()
                    dailyStatsMap[date] = existing.copy(oxygenSaturation = avg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich SpO2 history: ${e.message}", e)
        }

        try {
            val respRecords = hc.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            respRecords
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (date, records) ->
                    if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                    val avg = records.map { it.rate }.average().takeIf { !it.isNaN() } ?: 0.0
                    if (avg <= 0) return@forEach
                    val existing = dailyStatsMap[date] ?: HealthStats()
                    dailyStatsMap[date] = existing.copy(respiratoryRate = avg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich respiratory history: ${e.message}", e)
        }
    }

    suspend fun readHeartRateIntraday(date: LocalDate): List<HeartRateRecord.Sample> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                ),
            )
            response.records.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read intraday HR", e)
            emptyList()
        }
    }

    suspend fun readSleepSessions(date: LocalDate): List<SleepSessionRecord> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end = date.atTime(18, 0).atZone(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            response.records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sleep sessions", e)
            emptyList()
        }
    }

    /**
     * Recent workouts written by Garmin Connect and other Health Connect apps.
     * Routes come inline when granted; otherwise [HealthActivity.routeConsentRequired]
     * is true and the UI can request the path per session.
     */
    suspend fun readRecentActivities(
        days: Int = 14,
        limit: Int = 16,
    ): List<HealthActivity> = withContext(Dispatchers.IO) {
        val cacheKey = "activities_${days}_$limit"
        getCached<List<HealthActivity>>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext emptyList()
        if (!hasPermission(EXERCISE_PERMISSION)) return@withContext emptyList()

        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong()))
        val granted = getGrantedPermissions()
        val canDistance = DISTANCE_PERMISSION in granted
        val canCalories = ACTIVE_CALORIES_PERMISSION in granted
        val canHr = HEART_RATE_PERMISSION in granted
        val canSteps = STEPS_PERMISSION in granted
        val canElevation = ELEVATION_PERMISSION in granted

        val sessions = try {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = limit,
                ),
            ).records.sortedByDescending { it.startTime }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read exercise sessions", e)
            return@withContext emptyList()
        }

        val activities = sessions.map { session ->
            enrichSession(
                hc = hc,
                session = session,
                canDistance = canDistance,
                canCalories = canCalories,
                canHr = canHr,
                canSteps = canSteps,
                canElevation = canElevation,
            )
        }
        putCache(cacheKey, activities)
        activities
    }

    fun activityWithRoute(activity: HealthActivity, route: ExerciseRoute?): HealthActivity {
        val raw = route?.route.orEmpty()
        if (raw.isEmpty()) return activity
        val points = raw.map { loc ->
            ActivityRoutePoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitudeMeters = loc.altitude?.inMeters,
                time = loc.time,
            )
        }.let { downsampleRoute(it) }
        val distance = activity.distanceKm
            ?: routeDistanceKm(points).takeIf { it > 0.02 }
        val elevation = activity.elevationGainM
            ?: routeElevationGainM(points).takeIf { it >= 1.0 }
        return activity.copy(
            route = points,
            routeConsentRequired = false,
            distanceKm = distance,
            elevationGainM = elevation,
        )
    }

    suspend fun readActivityHeartRate(
        start: Instant,
        end: Instant,
    ): List<ActivityHrPoint> = withContext(Dispatchers.IO) {
        if (!start.isBefore(end)) return@withContext emptyList()
        if (!hasPermission(HEART_RATE_PERMISSION)) return@withContext emptyList()
        val hc = client ?: return@withContext emptyList()
        try {
            val records = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            ).records
            val samples = records.flatMap { rec ->
                rec.samples.map { sample -> ActivityHrPoint(sample.time, sample.beatsPerMinute) }
            }.sortedBy { it.time }
            downsampleHr(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read activity heart rate", e)
            emptyList()
        }
    }

    private suspend fun enrichSession(
        hc: HealthConnectClient,
        session: ExerciseSessionRecord,
        canDistance: Boolean,
        canCalories: Boolean,
        canHr: Boolean,
        canSteps: Boolean,
        canElevation: Boolean,
    ): HealthActivity {
        val origin = session.metadata.dataOrigin
        val device = session.metadata.device
        val sourcePkg = origin.packageName
        val sourceLabel = activitySourceLabel(sourcePkg, device?.manufacturer)
        val deviceLabel = listOfNotNull(device?.manufacturer, device?.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifBlank { null }

        val title = session.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultActivityTitle(session.exerciseType, session.startTime)

        var distanceKm: Double? = null
        var calories: Double? = null
        var steps: Long? = null
        var avgHr: Long? = null
        var maxHr: Long? = null
        var minHr: Long? = null
        var elevationM: Double? = null

        val metrics = buildSet {
            if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
            if (canCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canHr) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MAX)
                add(HeartRateRecord.BPM_MIN)
            }
            if (canSteps) add(StepsRecord.COUNT_TOTAL)
            if (canElevation) add(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)
        }
        if (metrics.isNotEmpty() && session.startTime.isBefore(session.endTime)) {
            try {
                val agg = hc.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    ),
                )
                if (canDistance) {
                    distanceKm = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.takeIf { it > 0 }
                }
                if (canCalories) {
                    calories = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { it > 0 }
                }
                if (canHr) {
                    avgHr = agg[HeartRateRecord.BPM_AVG]?.takeIf { it > 0 }
                    maxHr = agg[HeartRateRecord.BPM_MAX]?.takeIf { it > 0 }
                    minHr = agg[HeartRateRecord.BPM_MIN]?.takeIf { it > 0 }
                }
                if (canSteps) {
                    steps = agg[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0 }
                }
                if (canElevation) {
                    elevationM = agg[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters?.takeIf { it >= 1.0 }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to aggregate session ${session.metadata.id}: ${e.message}")
            }
        }

        val routeResult = session.exerciseRouteResult
        val routePoints = when (routeResult) {
            is ExerciseRouteResult.Data -> routeResult.exerciseRoute.route.map { loc ->
                ActivityRoutePoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude?.inMeters,
                    time = loc.time,
                )
            }.let { downsampleRoute(it) }
            else -> emptyList()
        }
        val consentRequired = routeResult is ExerciseRouteResult.ConsentRequired

        if (distanceKm == null) {
            val fromGps = routeDistanceKm(routePoints)
            if (fromGps > 0.02) distanceKm = fromGps
        }
        if (elevationM == null) {
            val fromGps = routeElevationGainM(routePoints)
            if (fromGps >= 1.0) elevationM = fromGps
        }

        val laps = session.laps.mapIndexed { index, lap ->
            ActivityLap(
                index = index + 1,
                duration = Duration.between(lap.startTime, lap.endTime).coerceAtLeast(Duration.ZERO),
                distanceKm = lap.length?.inKilometers?.takeIf { it > 0 },
            )
        }

        return HealthActivity(
            id = session.metadata.id,
            title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            exerciseType = session.exerciseType,
            startTime = session.startTime,
            endTime = session.endTime,
            sourcePackage = sourcePkg,
            sourceLabel = sourceLabel,
            deviceLabel = deviceLabel,
            distanceKm = distanceKm,
            caloriesKcal = calories,
            steps = steps,
            avgHr = avgHr,
            maxHr = maxHr,
            minHr = minHr,
            elevationGainM = elevationM,
            route = routePoints,
            routeConsentRequired = consentRequired,
            laps = laps,
        )
    }
}
