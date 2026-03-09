package com.macrotracker.data.f1

import android.util.Log
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
    private val api: F1ApiService
) : F1Repository {

    private var cachedStandings: F1Standings? = null
    private var lastFetchTime: Long = 0
    private var cachedYear: Int = 0
    private val CACHE_DURATION = 15 * 60 * 1000 // 15 minutes

    override val lastFetchTimeMs: Long get() = lastFetchTime

    override suspend fun getOverallF1Data(forceRefresh: Boolean): Result<F1Standings> {
        val now = System.currentTimeMillis()
        val currentYear = Year.now().value

        // Invalidate cache if the year has changed (new season) or forceRefresh requested
        val cacheValid = !forceRefresh
                && cachedStandings != null
                && (now - lastFetchTime) < CACHE_DURATION
                && cachedYear == currentYear

        if (cacheValid) {
            return Result.success(cachedStandings!!)
        }

        return try {
            // 1. Fetch Drivers
            val drivers = try {
                api.getSeasonDriverStandings()
            } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching driver standings: ${e.message}")
                emptyList()
            }

            // 2. Derive Constructor Standings
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
            } else {
                emptyList()
            }

            // 3. Fetch News
            val news = try {
                api.getF1News()
            } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching F1 news: ${e.message}")
                emptyList()
            }

            // 4. Fetch Last Race Results
            val lastRacePair = try {
                api.getLastRaceResults()
            } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching last race: ${e.message}")
                Pair(emptyList(), null)
            }
            val lastRace = lastRacePair.first
            val lastRaceName = lastRacePair.second

            // 4b. Fetch last qualifying results
            val lastQuali = try {
                api.getLastQualiResults()
            } catch (e: Exception) {
                Log.e("F1Repository", "Error fetching quali: ${e.message}")
                emptyList()
            }

            // 5. Fetch Race Schedule
            val schedule = try {
                api.getSchedule()
            } catch (e: Exception) {
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

            Result.success(result)
        } catch (e: Exception) {
            Log.e("F1Repository", "Unexpected error in getOverallF1Data: ${e.message}", e)
            cachedStandings?.let { Result.success(it) } ?: Result.failure(e)
        }
    }
}
