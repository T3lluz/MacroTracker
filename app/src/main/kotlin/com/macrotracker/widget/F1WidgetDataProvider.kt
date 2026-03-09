package com.macrotracker.widget

import android.content.Context
import android.util.Log
import com.macrotracker.data.f1.F1ApiServiceImpl
import com.macrotracker.data.f1.F1Standings
import com.macrotracker.data.f1.RaceScheduleEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TAG = "F1WidgetData"
private const val F1_PREFS = "daily_dash_f1_cache"
private const val CACHE_TTL = 15 * 60 * 1000L // 15 min

/**
 * Lightweight data holder for F1 widgets.
 * Fetched directly (no Hilt) and cached in SharedPreferences.
 */
data class F1WidgetData(
    // Next race countdown
    val nextRaceName: String? = null,
    val nextRaceCountry: String? = null,
    val nextRaceFlag: String? = null,
    val nextRaceDate: String? = null,       // "yyyy-MM-dd"
    val nextRaceTime: String? = null,       // "HH:mm:ssZ"
    val daysUntil: Long = -1,
    val hoursUntil: Long = -1,
    val minutesUntil: Long = -1,
    val isRaceWeekend: Boolean = false,     // quali or race within next 3 days
    val nextSessionLabel: String? = null,   // "Qualifying", "Sprint", "Race"
    val nextSessionDate: String? = null,
    val nextSessionTime: String? = null,    // "HH:mm:ssZ" UTC
    val circuitName: String? = null,
    val circuitId: String? = null,
    val laps: Int? = null,
    val lapRecord: String? = null,
    val lapRecordHolder: String? = null,
    val round: Int? = null,
    // Full weekend session schedule
    val weekendSessions: List<SessionRow> = emptyList(),
    // Driver standings (top N)
    val driverStandings: List<DriverStandingRow> = emptyList(),
    // Constructor standings (top N)
    val constructorStandings: List<ConstructorStandingRow> = emptyList(),
    // Schedule (upcoming races)
    val schedule: List<ScheduleRow> = emptyList(),
    val lastUpdatedAt: Long = 0L,
    val hasData: Boolean = false,
)

data class SessionRow(
    val label: String,      // "FP1", "FP2", "FP3", "Sprint Quali", "Sprint", "Qualifying", "Race"
    val date: String,       // "yyyy-MM-dd"
    val time: String? = null, // "HH:mm:ssZ" UTC — null if unknown
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
    val teamColor: String,   // hex without #
)

data class ConstructorStandingRow(
    val position: Int,
    val name: String,
    val points: Double,
    val wins: Int,
    val teamColor: String,
)

data class ScheduleRow(
    val round: Int,
    val raceName: String,
    val flag: String,
    val locality: String,
    val country: String,
    val raceDate: String,
    val isPast: Boolean,
    val isNext: Boolean,
    val hasSprint: Boolean,
)

// ── Singleton provider ────────────────────────────────────────────
object F1WidgetDataProvider {

    @Volatile private var cached: F1WidgetData? = null
    @Volatile private var cachedAt: Long = 0L

    fun invalidate() {
        cachedAt = 0L
    }

    suspend fun loadData(context: Context): F1WidgetData {
        val now = System.currentTimeMillis()
        if (cached != null && now - cachedAt < CACHE_TTL) return cached!!

        // Restore from prefs fast-path while network call runs (not implemented here—direct fetch)
        return try {
            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
            val api = F1ApiServiceImpl(client)
            val standings = api.getSeasonDriverStandings()
            val schedule  = api.getSchedule()
            client.close()

            val data = buildWidgetData(standings.map {
                DriverStandingRow(
                    position   = it.position,
                    name       = it.driverName,
                    acronym    = it.driverAcronym,
                    team       = it.constructorName,
                    points     = it.points,
                    wins       = it.wins,
                    teamColor  = it.teamColor,
                )
            }, schedule)

            cached = data
            cachedAt = now
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch F1 data: ${e.message}")
            cached ?: F1WidgetData()
        }
    }

    private fun buildWidgetData(
        drivers: List<DriverStandingRow>,
        schedule: List<RaceScheduleEntry>,
    ): F1WidgetData {
        val today = LocalDate.now()
        val zone  = ZoneId.systemDefault()
        val fmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        // ── Find next race + compute countdown ──────────────────────
        val upcoming = schedule.filter {
            try { LocalDate.parse(it.raceDate, fmt) >= today } catch (_: Exception) { false }
        }.sortedBy { it.raceDate }

        val nextEntry = upcoming.firstOrNull()

        var daysUntil = -1L
        var hoursUntil = -1L
        var minutesUntil = -1L
        var isRaceWeekend = false
        var nextSessionLabel: String? = null
        var nextSessionDate: String? = null
        var nextSessionTime: String? = null

        if (nextEntry != null) {
            val raceDate = try { LocalDate.parse(nextEntry.raceDate, fmt) } catch (_: Exception) { null }
            if (raceDate != null) {
                daysUntil = ChronoUnit.DAYS.between(today, raceDate)

                // All sessions with their times
                val sessions = listOfNotNull(
                    nextEntry.fp1Date?.let { Triple("FP1", it, null as String?) },
                    nextEntry.fp2Date?.let { Triple("FP2", it, null) },
                    nextEntry.fp3Date?.let { Triple("FP3", it, null) },
                    nextEntry.sprintDate?.let { Triple("Sprint", it, nextEntry.sprintTime) },
                    nextEntry.qualifyingDate?.let { Triple("Qualifying", it, nextEntry.qualifyingTime) },
                    Triple("Race", nextEntry.raceDate, nextEntry.raceTime),
                ).sortedBy { it.second }

                val nextSession = sessions.firstOrNull {
                    try { LocalDate.parse(it.second, fmt) >= today } catch (_: Exception) { false }
                }
                if (nextSession != null) {
                    nextSessionLabel = nextSession.first
                    nextSessionDate  = nextSession.second
                    nextSessionTime  = nextSession.third
                    val sessionDate = try { LocalDate.parse(nextSession.second, fmt) } catch (_: Exception) { null }
                    if (sessionDate != null) {
                        val sessionDays = ChronoUnit.DAYS.between(today, sessionDate)
                        isRaceWeekend = sessionDays <= 3
                        if (sessionDays == 0L) {
                            // Use hours/minutes for same-day
                            val time = nextSession.third?.removeSuffix("Z")
                            if (time != null) {
                                try {
                                    val dt = LocalDateTime.parse(
                                        "${nextSession.second}T$time",
                                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                    )
                                    val nowDt = LocalDateTime.now(zone)
                                    hoursUntil = ChronoUnit.HOURS.between(nowDt, dt).coerceAtLeast(0)
                                    minutesUntil = (ChronoUnit.MINUTES.between(nowDt, dt) % 60).coerceAtLeast(0)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        // ── Constructors derived from driver standings ──────────────
        val constructors = drivers.groupBy { it.team }
            .map { (team, td) ->
                ConstructorStandingRow(
                    position  = 0,
                    name      = team,
                    points    = td.sumOf { it.points },
                    wins      = td.sumOf { it.wins },
                    teamColor = td.first().teamColor,
                )
            }
            .sortedByDescending { it.points }
            .mapIndexed { i, c -> c.copy(position = i + 1) }

        // ── Schedule rows ───────────────────────────────────────────
        val scheduleRows = schedule.map { entry ->
            val rd = try { LocalDate.parse(entry.raceDate, fmt) } catch (_: Exception) { null }
            ScheduleRow(
                round     = entry.round,
                raceName  = entry.raceName,
                flag      = entry.countryCode ?: "🏁",
                locality  = entry.locality,
                country   = entry.country,
                raceDate  = entry.raceDate,
                isPast    = rd?.isBefore(today) ?: false,
                isNext    = entry == nextEntry,
                hasSprint = entry.sprintDate != null,
            )
        }

        // ── Weekend sessions for the next race ──────────────────────
        val weekendSessions = if (nextEntry != null) {
            listOfNotNull(
                nextEntry.fp1Date?.let { Triple("FP1", it, null as String?) },
                nextEntry.fp2Date?.let { Triple("FP2", it, null) },
                nextEntry.fp3Date?.let { Triple("FP3", it, null) },
                nextEntry.sprintDate?.let { Triple("Sprint", it, nextEntry.sprintTime) },
                nextEntry.qualifyingDate?.let { Triple("Qualifying", it, nextEntry.qualifyingTime) },
                Triple("Race", nextEntry.raceDate, nextEntry.raceTime),
            ).sortedBy { it.second }
             .map { (lbl, date, time) ->
                 val d = try { LocalDate.parse(date, fmt) } catch (_: Exception) { null }
                 SessionRow(
                     label  = lbl,
                     date   = date,
                     time   = time,
                     isPast = d?.isBefore(today) ?: false,
                     isNext = lbl == nextSessionLabel && date == nextSessionDate,
                 )
             }
        } else emptyList()

        return F1WidgetData(
            nextRaceName      = nextEntry?.raceName,
            nextRaceCountry   = nextEntry?.country,
            nextRaceFlag      = nextEntry?.countryCode ?: "🏁",
            nextRaceDate      = nextEntry?.raceDate,
            nextRaceTime      = nextEntry?.raceTime,
            daysUntil         = daysUntil,
            hoursUntil        = hoursUntil,
            minutesUntil      = minutesUntil,
            isRaceWeekend     = isRaceWeekend,
            nextSessionLabel  = nextSessionLabel,
            nextSessionDate   = nextSessionDate,
            nextSessionTime   = nextSessionTime,
            circuitName       = nextEntry?.circuitName,
            circuitId         = nextEntry?.circuitId,
            laps              = nextEntry?.laps,
            lapRecord         = nextEntry?.lapRecord,
            lapRecordHolder   = nextEntry?.lapRecordHolder,
            round             = nextEntry?.round,
            weekendSessions   = weekendSessions,
            driverStandings   = drivers,
            constructorStandings = constructors,
            schedule          = scheduleRows,
            lastUpdatedAt     = System.currentTimeMillis(),
            hasData           = drivers.isNotEmpty() || schedule.isNotEmpty(),
        )
    }
}




