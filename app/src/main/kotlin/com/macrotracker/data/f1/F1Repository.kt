package com.macrotracker.data.f1

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

interface F1Repository {
    suspend fun getOverallF1Data(forceRefresh: Boolean = false): Result<F1Standings>
    /** Epoch-ms of the last actual network fetch (0 if never fetched or only served from cache). */
    val lastFetchTimeMs: Long
}

@Singleton
class F1RepositoryImpl @Inject constructor(
    private val api: F1ApiService,
    @ApplicationContext private val context: Context,
) : F1Repository {

    private var cachedStandings: F1Standings? = null
    private var lastFetchTime: Long = 0
    private var cachedYear: Int = 0

    // 6-hour cache — F1 standings only change after a race weekend
    private val CACHE_DURATION = 6 * 60 * 60 * 1000L

    private val prefs by lazy {
        context.getSharedPreferences("f1_cache", Context.MODE_PRIVATE)
    }

    override val lastFetchTimeMs: Long get() = lastFetchTime

    init {
        // Restore disk cache on startup so cold starts skip the network when data is fresh
        restoreDiskCache()
    }

    override suspend fun getOverallF1Data(forceRefresh: Boolean): Result<F1Standings> {
        val now = System.currentTimeMillis()
        val currentYear = Year.now().value

        val cacheValid = !forceRefresh
                && cachedStandings != null
                && (now - lastFetchTime) < CACHE_DURATION
                && cachedYear == currentYear

        if (cacheValid) {
            return Result.success(cachedStandings!!)
        }

        return try {
            val drivers = try {
                api.getSeasonDriverStandings()
            } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching driver standings: ${e.message}")
                emptyList()
            }

            val constructors = if (drivers.isNotEmpty()) {
                drivers.groupBy { it.constructorName }
                    .map { (team, teamDrivers) ->
                        SeasonConstructorStanding(
                            position = 0,
                            points = teamDrivers.sumOf { it.points },
                            wins = teamDrivers.sumOf { it.wins },
                            constructorName = team,
                            teamColor = teamDrivers.first().teamColor,
                            teamLogoUrl = teamDrivers.first().teamLogoUrl
                        )
                    }
                    .sortedByDescending { it.points }
                    .mapIndexed { index, constructor ->
                        constructor.copy(position = index + 1)
                    }
            } else emptyList()

            val news = try { api.getF1News() } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching F1 news: ${e.message}")
                emptyList()
            }

            val lastRacePair = try { api.getLastRaceResults() } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching last race: ${e.message}")
                Pair(emptyList(), null)
            }
            val lastRace = lastRacePair.first
            val lastRaceName = lastRacePair.second

            val lastQuali = try { api.getLastQualiResults() } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching quali: ${e.message}")
                emptyList()
            }

            val schedule = try { api.getSchedule() } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching schedule: ${e.message}")
                emptyList()
            }

            val result = F1Standings(
                driverStandings = drivers,
                constructorStandings = constructors,
                news = news,
                lastRaceResults = lastRace.ifEmpty { null },
                lastQualiResults = lastQuali.ifEmpty { null },
                schedule = schedule,
                lastSessionName = "Championship Standings",
                lastLocation = "Live Data Hub",
                lastRaceName = lastRaceName,
            )

            cachedStandings = result
            lastFetchTime = now
            cachedYear = currentYear
            persistDiskCache(result, now, currentYear)

            Result.success(result)
        } catch (e: Exception) {
            Log.e("F1Repository", "Unexpected error in getOverallF1Data: ${e.message}", e)
            cachedStandings?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    // ── Disk persistence ─────────────────────────────────────────────────────

    private fun persistDiskCache(data: F1Standings, fetchTime: Long, year: Int) {
        try {
            val driversJson = JSONArray().apply {
                data.driverStandings.forEach { d ->
                    put(JSONObject().apply {
                        put("position", d.position)
                        put("driverName", d.driverName)
                        put("driverAcronym", d.driverAcronym)
                        put("constructorName", d.constructorName)
                        put("points", d.points)
                        put("wins", d.wins)
                        put("teamColor", d.teamColor)
                        put("teamLogoUrl", d.teamLogoUrl ?: "")
                        put("headshotUrl", d.headshotUrl ?: "")
                        put("nationality", d.nationality ?: "")
                    })
                }
            }
            prefs.edit {
                putLong("fetch_time", fetchTime)
                putInt("year", year)
                putString("drivers_json", driversJson.toString())
            }
        } catch (e: Exception) {
            Log.w("F1Repository", "Failed to persist F1 cache: ${e.message}")
        }
    }

    private fun restoreDiskCache() {
        try {
            val fetchTime = prefs.getLong("fetch_time", 0L)
            val year = prefs.getInt("year", 0)
            val driversJson = prefs.getString("drivers_json", null) ?: return

            val now = System.currentTimeMillis()
            if (fetchTime == 0L || (now - fetchTime) >= CACHE_DURATION || year != Year.now().value) return

            val arr = JSONArray(driversJson)
            val drivers = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SeasonDriverStanding(
                    position = o.getInt("position"),
                    driverName = o.getString("driverName"),
                    driverAcronym = o.optString("driverAcronym", ""),
                    constructorName = o.getString("constructorName"),
                    points = o.getDouble("points"),
                    wins = o.getInt("wins"),
                    teamColor = o.getString("teamColor"),
                    teamLogoUrl = o.getString("teamLogoUrl").ifEmpty { null },
                    headshotUrl = o.getString("headshotUrl").ifEmpty { null },
                    nationality = o.getString("nationality").ifEmpty { null },
                )
            }
            if (drivers.isEmpty()) return

            val constructors = drivers.groupBy { it.constructorName }
                .map { (team, td) ->
                    SeasonConstructorStanding(
                        position = 0,
                        points = td.sumOf { it.points },
                        wins = td.sumOf { it.wins },
                        constructorName = team,
                        teamColor = td.first().teamColor,
                        teamLogoUrl = td.first().teamLogoUrl,
                    )
                }
                .sortedByDescending { it.points }
                .mapIndexed { i, c -> c.copy(position = i + 1) }

            cachedStandings = F1Standings(
                driverStandings = drivers,
                constructorStandings = constructors,
                news = emptyList(),
                lastRaceResults = null,
                lastQualiResults = null,
                schedule = emptyList(),
                lastSessionName = "Championship Standings",
                lastLocation = "Live Data Hub",
                lastRaceName = null,
            )
            lastFetchTime = fetchTime
            cachedYear = year
            Log.d("F1Repository", "Restored F1 standings from disk cache (${drivers.size} drivers)")
        } catch (e: Exception) {
            Log.w("F1Repository", "Failed to restore F1 disk cache: ${e.message}")
        }
    }
}
