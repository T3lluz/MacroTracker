package com.macrotracker.widget

import android.content.Context
import android.util.Log
import com.macrotracker.data.f1.F1Standings
import com.macrotracker.data.f1.RaceScheduleEntry
import com.macrotracker.data.f1.f1Repository
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TAG = "F1WidgetData"
private const val MEMORY_TTL = 60 * 1000L
private const val REFRESH_AFTER = 15 * 60 * 1000L
/** Max time to wait for an inline network fetch inside provideGlance before giving up. */
private const val INLINE_FETCH_TIMEOUT = 10_000L

/**
 * Lightweight data holder for F1 widgets.
 * Backed by the shared F1 repository so widgets and the app always render the same snapshot.
 */
data class F1WidgetData(
    val nextRaceName: String? = null,
    val nextRaceCountry: String? = null,
    val nextRaceFlag: String? = null,
    val nextRaceDate: String? = null,
    val nextRaceTime: String? = null,
    val daysUntil: Long = -1,
    val hoursUntil: Long = -1,
    val minutesUntil: Long = -1,
    val secondsUntil: Long = -1,
    val isRaceWeekend: Boolean = false,
    val nextSessionLabel: String? = null,
    val nextSessionDate: String? = null,
    val nextSessionTime: String? = null,
    val circuitName: String? = null,
    val circuitId: String? = null,
    val laps: Int? = null,
    val lapRecord: String? = null,
    val lapRecordHolder: String? = null,
    val round: Int? = null,
    val weekendSessions: List<SessionRow> = emptyList(),
    val driverStandings: List<DriverStandingRow> = emptyList(),
    val constructorStandings: List<ConstructorStandingRow> = emptyList(),
    val schedule: List<ScheduleRow> = emptyList(),
    /** Top-5 finishers from the most-recent completed race. */
    val lastRaceResults: List<LastRaceResultRow> = emptyList(),
    val lastRaceName: String? = null,
    val lastRaceFlag: String? = null,
    /** Qualifying grid (top-10) from the last qualifying session. */
    val lastQualiResults: List<LastQualiResultRow> = emptyList(),
    val lastUpdatedAt: Long = 0L,
    val hasData: Boolean = false,
    val isStale: Boolean = false,
    val isLoading: Boolean = false,
)

data class SessionRow(
    val label: String,
    val date: String,
    val time: String? = null,
    val isPast: Boolean,
    val isNext: Boolean,
)

data class DriverStandingRow(
    val position: Int,
    val name: String,
    val acronym: String,
    val team: String,
    val points: Double,
    val wins: Int,
    val teamColor: String,
    val podiums: Int = 0,
    val fastestLaps: Int = 0,
    /** Points gap from championship leader, e.g. "-42". Empty string for P1. */
    val gapToLeader: String = "",
    val driverNumber: String? = null,
    /** Finish position in the most recent race (0 = no data). */
    val lastRacePos: Int = 0,
    /** Points scored in the most recent race. */
    val lastRacePoints: Double = 0.0,
)

data class ConstructorStandingRow(
    val position: Int,
    val name: String,
    val points: Double,
    val wins: Int,
    val teamColor: String,
    /** Top-2 driver acronyms for this constructor (best scorer first). */
    val driver1: String = "",
    val driver2: String = "",
    val driver1Pts: Double = 0.0,
    val driver2Pts: Double = 0.0,
)

data class ScheduleRow(
    val round: Int,
    val raceName: String,
    val flag: String,
    val locality: String,
    val country: String,
    val raceDate: String,
    val raceTime: String? = null,
    val isPast: Boolean,
    val isNext: Boolean,
    val hasSprint: Boolean,
)

/** Compact result row for the last race podium display. */
data class LastRaceResultRow(
    val position: Int,
    val acronym: String,
    val driverName: String,
    val teamColor: String,
    val timeOrStatus: String?,
    val fastestLap: Boolean = false,
    /** Positions gained during the race (positive = moved forward, negative = fell back). */
    val positionsGained: Int = 0,
)

/** Compact qualifying grid row. */
data class LastQualiResultRow(
    val position: Int,
    val acronym: String,
    val teamColor: String,
    val bestTime: String?,   // Q3 > Q2 > Q1
    val gapToP1: String?,    // null for pole sitter
)

// ── Singleton provider ────────────────────────────────────────────
object F1WidgetDataProvider {

    @Volatile private var cached: F1WidgetData? = null
    @Volatile private var cachedAt: Long = 0L

    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    /**
     * Returns true only when the repo cache contains actually useful data (non-empty standings
     * or schedule). A non-null but all-empty F1Standings written during a failed network sweep
     * must NOT be treated as "has data" — doing so would suppress retries in the refresh worker
     * and leave the widget stuck in the "no data / Syncing…" state.
     */
    fun hasCachedData(context: Context): Boolean {
        val data = context.f1Repository().getCachedF1Data() ?: return false
        return data.driverStandings.isNotEmpty() || data.schedule.isNotEmpty()
    }

    /**
     * Fast path for `provideGlance`.
     *
     * 1. Return in-memory cache if still fresh.
     * 2. Return repo disk-cache if available (schedule background refresh when stale).
     * 3. **Attempt an inline network fetch** with a bounded timeout so the widget
     *    renders real data on first placement instead of showing "Syncing…" forever.
     * 4. Only if everything fails, return an empty loading placeholder — but **never**
     *    cache it, so the next render attempt will try again.
     */
    suspend fun loadData(context: Context): F1WidgetData {
        val now = System.currentTimeMillis()
        // 1. Memory cache
        cached?.takeIf { it.hasData && now - cachedAt < MEMORY_TTL }?.let { return it }

        val repo = context.f1Repository()

        // 2. Repo / disk cache
        val repoCache = repo.getCachedF1Data()
        if (repoCache != null) {
            val data = buildWidgetData(repoCache, repo.lastFetchTimeMs)
            if (data.hasData) {
                cache(data)
                if (data.isStale) {
                    // Enqueue a background refresh — but return stale data immediately
                    WidgetRefreshWorker.enqueueImmediateF1Refresh(context)
                }
                return data
            }
            // repoCache is non-null but empty — this happens when a previous forced refresh
            // wrote an all-empty F1Standings because every API endpoint failed. Do NOT return
            // here; fall through to the inline fetch so the widget can self-heal immediately
            // instead of staying stuck until the next periodic 15-min worker fires.
            Log.d(TAG, "Repo cache exists but has no data — falling through to inline fetch")
        }

        // 3. No usable cache — try inline fetch with timeout
        Log.d(TAG, "No F1 cache, attempting inline fetch…")
        val inlineFetch = try {
            withTimeoutOrNull(INLINE_FETCH_TIMEOUT) {
                repo.getOverallF1Data(forceRefresh = true).getOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Inline F1 fetch failed: ${e.message}")
            null
        }
        if (inlineFetch != null) {
            val data = buildWidgetData(inlineFetch, repo.lastFetchTimeMs)
            if (data.hasData) {
                cache(data)
                return data
            }
            // Inline fetch returned an empty result (all APIs still failing) — fall through
            // to step 4 so the background worker is enqueued for when network recovers.
            Log.w(TAG, "Inline F1 fetch returned empty data — enqueueing background retry")
        }

        // 4. All attempts failed — return empty loading state; do NOT cache it
        //    so the next provideGlance call will retry step 3.
        WidgetRefreshWorker.enqueueImmediateF1Refresh(context)
        return F1WidgetData(
            lastUpdatedAt = repo.lastFetchTimeMs,
            hasData = false,
            isStale = repo.lastFetchTimeMs > 0L,
            isLoading = true,
        )
    }

    suspend fun refreshNow(context: Context, force: Boolean = true): Boolean {
        val repo = context.f1Repository()
        val before = repo.lastFetchTimeMs
        val result = repo.getOverallF1Data(forceRefresh = force)
        val after = repo.lastFetchTimeMs
        result.getOrNull()?.let { buildWidgetData(it, after).also(::cache) }
        return after > before
    }

    private fun cache(data: F1WidgetData) {
        // Only cache states with real data — loading/empty states should not be cached
        if (!data.hasData) return
        cached = data
        cachedAt = System.currentTimeMillis()
    }

    private fun buildWidgetData(
        standings: F1Standings,
        fetchedAt: Long,
    ): F1WidgetData {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val schedule = standings.schedule
        val p1Points = standings.driverStandings.firstOrNull()?.points ?: 0.0
        // Build lookup maps from last race results for enriching driver rows
        val lastRacePosMap: Map<String, Int> = standings.lastRaceResults
            ?.associate { (it.driverAcronym ?: "") to it.position } ?: emptyMap()
        val lastRacePointsMap: Map<String, Double> = standings.lastRaceResults
            ?.associate { (it.driverAcronym ?: "") to it.points } ?: emptyMap()
        val drivers = standings.driverStandings.map {
            val gapPts = if (it.position <= 1) 0.0 else p1Points - it.points
            DriverStandingRow(
                position = it.position,
                name = it.driverName,
                acronym = it.driverAcronym,
                team = it.constructorName,
                points = it.points,
                wins = it.wins,
                teamColor = it.teamColor,
                podiums = it.podiums,
                fastestLaps = it.fastestLaps,
                gapToLeader = if (it.position == 1) "" else "-${gapPts.toInt()}",
                driverNumber = it.driverNumber,
                lastRacePos = lastRacePosMap[it.driverAcronym] ?: 0,
                lastRacePoints = lastRacePointsMap[it.driverAcronym] ?: 0.0,
            )
        }
        val driversByTeam: Map<String, List<DriverStandingRow>> = drivers.groupBy { it.team }
        val constructors = standings.constructorStandings
            .takeIf { it.isNotEmpty() }
            ?.map {
                val teamDrivers = driversByTeam[it.constructorName]
                    ?.sortedByDescending { d -> d.points } ?: emptyList()
                ConstructorStandingRow(
                    position = it.position,
                    name = it.constructorName,
                    points = it.points,
                    wins = it.wins,
                    teamColor = it.teamColor,
                    driver1 = teamDrivers.getOrNull(0)?.acronym ?: "",
                    driver2 = teamDrivers.getOrNull(1)?.acronym ?: "",
                    driver1Pts = teamDrivers.getOrNull(0)?.points ?: 0.0,
                    driver2Pts = teamDrivers.getOrNull(1)?.points ?: 0.0,
                )
            }
            ?: drivers.groupBy { it.team }
                .map { (team, td) ->
                    val sorted = td.sortedByDescending { it.points }
                    ConstructorStandingRow(
                        position = 0,
                        name = team,
                        points = td.sumOf { it.points },
                        wins = td.sumOf { it.wins },
                        teamColor = td.first().teamColor,
                        driver1 = sorted.getOrNull(0)?.acronym ?: "",
                        driver2 = sorted.getOrNull(1)?.acronym ?: "",
                        driver1Pts = sorted.getOrNull(0)?.points ?: 0.0,
                        driver2Pts = sorted.getOrNull(1)?.points ?: 0.0,
                    )
                }
                .sortedByDescending { it.points }
                .mapIndexed { i, c -> c.copy(position = i + 1) }

        val upcoming = schedule.filter {
            try { LocalDate.parse(it.raceDate, fmt) >= today } catch (_: Exception) { false }
        }.sortedBy { it.raceDate }

        val nextEntry = upcoming.firstOrNull()

        var daysUntil = -1L
        var hoursUntil = -1L
        var minutesUntil = -1L
        var secondsUntil = -1L
        var isRaceWeekend = false
        var nextSessionLabel: String? = null
        var nextSessionDate: String? = null
        var nextSessionTime: String? = null

        if (nextEntry != null) {
            val raceDate = try { LocalDate.parse(nextEntry.raceDate, fmt) } catch (_: Exception) { null }
            if (raceDate != null) {
                val sessions = buildSessions(nextEntry).sortedBy { it.second }
                val nowDt = LocalDateTime.now(zone)

                // Find next upcoming session (future based on datetime if available, else date)
                val nextSession = sessions.firstOrNull { (_, date, time) ->
                    try {
                        if (time != null) {
                            val clean = time.trimEnd('Z')
                            val dt = LocalDateTime.parse("${date}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime().isAfter(nowDt)
                        } else {
                            LocalDate.parse(date, fmt) >= today
                        }
                    } catch (_: Exception) { false }
                }

                if (nextSession != null) {
                    nextSessionLabel = nextSession.first
                    nextSessionDate = nextSession.second
                    nextSessionTime = nextSession.third
                    val sessionDate = try { LocalDate.parse(nextSession.second, fmt) } catch (_: Exception) { null }
                    if (sessionDate != null) {
                        val sessionDays = ChronoUnit.DAYS.between(today, sessionDate)
                        isRaceWeekend = sessionDays <= 3

                        val time = nextSession.third?.removeSuffix("Z")
                        if (time != null) {
                            // Full D/HH/MM/SS from exact session datetime
                            try {
                                val dt = LocalDateTime.parse(
                                    "${nextSession.second}T$time",
                                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                ).atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime()
                                val totalSeconds = ChronoUnit.SECONDS.between(nowDt, dt).coerceAtLeast(0)
                                daysUntil = totalSeconds / 86400
                                hoursUntil = (totalSeconds % 86400) / 3600
                                minutesUntil = (totalSeconds % 3600) / 60
                                secondsUntil = totalSeconds % 60
                            } catch (_: Exception) {
                                // Fallback: days only from date
                                daysUntil = ChronoUnit.DAYS.between(today, sessionDate).coerceAtLeast(0)
                            }
                        } else {
                            // No time available — days only from session date
                            daysUntil = ChronoUnit.DAYS.between(today, sessionDate).coerceAtLeast(0)
                        }
                    }
                } else {
                    // No upcoming session — fall back to race date days
                    daysUntil = ChronoUnit.DAYS.between(today, raceDate).coerceAtLeast(0)
                }
            }
        }

        val scheduleRows = schedule.map { entry ->
            val rd = try { LocalDate.parse(entry.raceDate, fmt) } catch (_: Exception) { null }
            ScheduleRow(
                round = entry.round,
                raceName = entry.raceName,
                flag = entry.countryCode ?: "🏁",
                locality = entry.locality,
                country = entry.country,
                raceDate = entry.raceDate,
                raceTime = entry.raceTime,
                isPast = rd?.isBefore(today) ?: false,
                isNext = entry == nextEntry,
                hasSprint = entry.sprintDate != null,
            )
        }

        val weekendSessions = nextEntry?.let { entry ->
            val nowDt = LocalDateTime.now(zone)
            buildSessions(entry).sortedBy { it.second }.map { (label, date, time) ->
                val isPastSession = try {
                    if (time != null) {
                        val clean = time.trimEnd('Z')
                        val dt = LocalDateTime.parse("${date}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime().isBefore(nowDt)
                    } else {
                        val d = LocalDate.parse(date, fmt)
                        d.isBefore(today)
                    }
                } catch (_: Exception) { false }
                SessionRow(
                    label = label,
                    date = date,
                    time = time,
                    isPast = isPastSession,
                    isNext = label == nextSessionLabel && date == nextSessionDate,
                )
            }
        } ?: emptyList()

        val hasData = drivers.isNotEmpty() || schedule.isNotEmpty() || constructors.isNotEmpty()
        val isStale = fetchedAt <= 0L || (System.currentTimeMillis() - fetchedAt) > REFRESH_AFTER

        // Last race results (top 5)
        val lastRaceResultRows = standings.lastRaceResults?.take(5)?.map { rr ->
            LastRaceResultRow(
                position = rr.position,
                acronym = rr.driverAcronym
                    ?: rr.driverName.split(" ").last().take(3).uppercase(),
                driverName = rr.driverName,
                teamColor = rr.teamColor,
                timeOrStatus = rr.time ?: rr.status,
                fastestLap = rr.fastestLap,
                positionsGained = rr.positionsGained ?: 0,
            )
        } ?: emptyList()

        // Qualifying grid (top 10)
        val lastQualiRows = standings.lastQualiResults?.take(10)?.map { qr ->
            LastQualiResultRow(
                position = qr.position,
                acronym = qr.driverAcronym
                    ?: qr.driverName.split(" ").last().take(3).uppercase(),
                teamColor = qr.teamColor,
                bestTime = qr.q3Time ?: qr.q2Time ?: qr.q1Time,
                gapToP1 = qr.gapToP1,
            )
        } ?: emptyList()

        val lastRaceEntry = schedule
            .sortedByDescending { it.raceDate }
            .firstOrNull { try { LocalDate.parse(it.raceDate, fmt).isBefore(today) } catch (_: Exception) { false } }
        val lastRaceNameStr = standings.lastRaceName ?: lastRaceEntry?.raceName
        val lastRaceFlagStr = lastRaceEntry?.countryCode ?: "🏁"

        return F1WidgetData(
            nextRaceName = nextEntry?.raceName,
            nextRaceCountry = nextEntry?.country,
            nextRaceFlag = nextEntry?.countryCode ?: "🏁",
            nextRaceDate = nextEntry?.raceDate,
            nextRaceTime = nextEntry?.raceTime,
            daysUntil = daysUntil,
            hoursUntil = hoursUntil,
            minutesUntil = minutesUntil,
            secondsUntil = secondsUntil,
            isRaceWeekend = isRaceWeekend,
            nextSessionLabel = nextSessionLabel,
            nextSessionDate = nextSessionDate,
            nextSessionTime = nextSessionTime,
            circuitName = nextEntry?.circuitName,
            circuitId = nextEntry?.circuitId,
            laps = nextEntry?.laps,
            lapRecord = nextEntry?.lapRecord,
            lapRecordHolder = nextEntry?.lapRecordHolder,
            round = nextEntry?.round,
            weekendSessions = weekendSessions,
            driverStandings = drivers,
            constructorStandings = constructors,
            schedule = scheduleRows,
            lastRaceResults = lastRaceResultRows,
            lastRaceName = lastRaceNameStr,
            lastRaceFlag = lastRaceFlagStr,
            lastQualiResults = lastQualiRows,
            lastUpdatedAt = fetchedAt,
            hasData = hasData,
            isStale = isStale,
            isLoading = !hasData && fetchedAt <= 0L,
        )
    }

    private fun buildSessions(entry: RaceScheduleEntry): List<Triple<String, String, String?>> = listOfNotNull(
        entry.fp1Date?.let { Triple("FP1", it, null as String?) },
        entry.fp2Date?.let { Triple("FP2", it, null) },
        entry.fp3Date?.let { Triple("FP3", it, null) },
        entry.sprintDate?.let { Triple("Sprint", it, entry.sprintTime) },
        entry.qualifyingDate?.let { Triple("Qualifying", it, entry.qualifyingTime) },
        Triple("Race", entry.raceDate, entry.raceTime),
    )
}
