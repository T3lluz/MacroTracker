package com.macrotracker.data.f1

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

interface F1ApiService {
    suspend fun getSeasonDriverStandings(): List<SeasonDriverStanding>
    suspend fun getF1News(): List<F1NewsArticle>
    suspend fun getLastRaceResults(): Pair<List<RaceResult>, String?>
    suspend fun getLastQualiResults(): List<QualiResult>
    suspend fun getSchedule(): List<RaceScheduleEntry>
}

@Singleton
class F1ApiServiceImpl @Inject constructor(
    private val client: HttpClient
) : F1ApiService {

    companion object {
        private const val TAG = "F1ApiService"
        // Jolpica is the community-maintained successor to the Ergast API (same JSON schema)
        private const val JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"
    }

    // Cache of driverAcronym -> headshotUrl, populated by getSeasonDriverStandings
    private val headshotCache = mutableMapOf<String, String>()
    // Cache of driverAcronym -> teamColor
    private val teamColorCache = mutableMapOf<String, String>()

    /**
     * Build the F1 CDN headshot URL for a driver.
     * Formula: https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/{FolderLetter}/{imageIdUpper}_{FirstName}_{LastName}/{imageId}.png.transform/1col/image.png
     * imageId = first3(firstName) + first3(lastName) + "01" (all lowercase)
     */
    private fun buildHeadshotUrl(givenName: String, familyName: String): String {
        // Known overrides for drivers where the standard formula fails or who are new
        val familyNameLower = familyName.lowercase()
        when {
            familyNameLower.contains("antonelli") ->
                return "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ANDANT01_Andrea_Kimi_Antonelli/andant01.png.transform/1col/image.png"
            familyNameLower.contains("lindblad") ->
                return "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/A/ARVLIN01_Arvid_Lindblad/arvlin01.png.transform/1col/image.png"
            familyNameLower.contains("bortoleto") ->
                return "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/G/GABBOR01_Gabriel_Bortoleto/gabbor01.png.transform/1col/image.png"
            familyNameLower.contains("bearman") ->
                return "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/O/OLIBEA01_Oliver_Bearman/olibea01.png.transform/1col/image.png"
        }

        val firstName = givenName.split(" ").first()
        val imageId = (firstName.take(3) + familyName.replace(" ", "").take(3)).lowercase() + "01"
        val imageIdUpper = imageId.uppercase()
        val folderLetter = firstName.first().uppercaseChar()

        // Use full names with underscores for the directory name to handle multi-part names
        val givenNamePath = givenName.replace(" ", "_")
        val familyNamePath = familyName.replace(" ", "_")

        return "https://media.formula1.com/d_driver_fallback_image.png/content/dam/fom-website/drivers/$folderLetter/${imageIdUpper}_${givenNamePath}_${familyNamePath}/${imageId}.png.transform/1col/image.png"
    }

    override suspend fun getSeasonDriverStandings(): List<SeasonDriverStanding> {
        // "current" always points to the live season on Jolpica
        val standings = fetchStandingsForYear("current")
        if (standings.isNotEmpty()) return standings

        Log.d(TAG, "No data for current season, falling back to 2025")
        return fetchStandingsForYear("2025")
    }

    private suspend fun fetchStandingsForYear(year: String): List<SeasonDriverStanding> {
        return try {
            Log.d(TAG, "Fetching $year season driver standings from Jolpica...")
            val response = client.get("$JOLPICA_BASE/$year/driverStandings.json").body<String>()

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val mrData = root["MRData"]?.jsonObject
            val standingsTable = mrData?.get("StandingsTable")?.jsonObject
            val standingsLists = standingsTable?.get("StandingsLists")?.jsonArray
            val firstList = standingsLists?.firstOrNull()?.jsonObject ?: return emptyList()
            val driverStandingsList = firstList["DriverStandings"]?.jsonArray ?: return emptyList()

            driverStandingsList.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructors = entry["Constructors"]?.jsonArray
                val constructor = constructors?.firstOrNull()?.jsonObject
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                
                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val acronym = driver?.get("code")?.jsonPrimitive?.content ?: familyName.take(3).uppercase()

                // Build the working F1 CDN headshot URL
                val headshotUrl = buildHeadshotUrl(givenName, familyName)
                val teamColorHex = getTeamColor(constructorId)
                val standing = SeasonDriverStanding(
                    position = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    points = entry["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    wins = entry["wins"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    driverName = "$givenName $familyName",
                    driverAcronym = acronym,
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    teamColor = teamColorHex,
                    headshotUrl = headshotUrl,
                    teamLogoUrl = getTeamLogo(constructorId),
                    driverNumber = driver?.get("permanentNumber")?.jsonPrimitive?.content
                )
                // Cache for later enrichment of race/quali results
                headshotCache[acronym] = headshotUrl
                teamColorCache[acronym] = teamColorHex
                standing
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching standings for $year: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getLastRaceResults(): Pair<List<RaceResult>, String?> {
        return try {
            Log.d(TAG, "Fetching last race results from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current/last/results.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val mrData = root["MRData"]?.jsonObject
            val raceTable = mrData?.get("RaceTable")?.jsonObject
            val races = raceTable?.get("Races")?.jsonArray
            val lastRace = races?.firstOrNull()?.jsonObject ?: return Pair(emptyList(), null)
            val raceName = lastRace["raceName"]?.jsonPrimitive?.content
            val results = lastRace["Results"]?.jsonArray ?: return Pair(emptyList(), raceName)

            val list = results.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructor = entry["Constructor"]?.jsonObject
                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val pos = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val grid = entry["grid"]?.jsonPrimitive?.content?.toIntOrNull()
                val fastestLapRank = entry["FastestLap"]?.jsonObject?.get("rank")?.jsonPrimitive?.content?.toIntOrNull()
                val posGained = if (grid != null && grid > 0 && pos > 0) grid - pos else null
                val acronym = driver?.get("code")?.jsonPrimitive?.content
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                // Use cached headshot or build one on-the-fly
                val headshotUrl = (acronym?.let { headshotCache[it] }) ?: buildHeadshotUrl(givenName, familyName)
                val teamColorHex = (acronym?.let { teamColorCache[it] }) ?: getTeamColor(constructorId)
                RaceResult(
                    position = pos,
                    driverName = "$givenName $familyName",
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    points = entry["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    time = entry["Time"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    status = entry["status"]?.jsonPrimitive?.content,
                    grid = grid,
                    driverAcronym = acronym,
                    fastestLap = fastestLapRank == 1,
                    positionsGained = posGained,
                    headshotUrl = headshotUrl,
                    teamColor = teamColorHex,
                )
            }
            Pair(list, raceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching last race results: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    override suspend fun getLastQualiResults(): List<QualiResult> {
        return try {
            Log.d(TAG, "Fetching last qualifying results from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current/last/qualifying.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val races = root["MRData"]?.jsonObject?.get("RaceTable")?.jsonObject?.get("Races")?.jsonArray
            val lastRace = races?.firstOrNull()?.jsonObject ?: return emptyList()
            val results = lastRace["QualifyingResults"]?.jsonArray ?: return emptyList()
            val p1Time = results.firstOrNull()?.jsonObject?.get("Q3")?.jsonPrimitive?.content
                ?: results.firstOrNull()?.jsonObject?.get("Q2")?.jsonPrimitive?.content
                ?: results.firstOrNull()?.jsonObject?.get("Q1")?.jsonPrimitive?.content

            results.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructor = entry["Constructor"]?.jsonObject
                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                val q3 = entry["Q3"]?.jsonPrimitive?.content
                val q2 = entry["Q2"]?.jsonPrimitive?.content
                val q1 = entry["Q1"]?.jsonPrimitive?.content
                val bestTime = q3 ?: q2 ?: q1
                val pos = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val gap = if (pos > 1 && bestTime != null && p1Time != null) {
                    computeTimeGap(p1Time, bestTime)
                } else null
                val acronymQ = driver?.get("code")?.jsonPrimitive?.content
                val headshotUrlQ = (acronymQ?.let { headshotCache[it] }) ?: buildHeadshotUrl(givenName, familyName)
                QualiResult(
                    position = pos,
                    driverName = "$givenName $familyName",
                    driverAcronym = acronymQ,
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    teamColor = getTeamColor(constructorId),
                    q1Time = q1,
                    q2Time = q2,
                    q3Time = q3,
                    gapToP1 = gap,
                    headshotUrl = headshotUrlQ,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching qualifying results: ${e.message}")
            emptyList()
        }
    }

    private fun computeTimeGap(p1: String, other: String): String? {
        return try {
            fun parseMs(t: String): Long {
                val parts = t.split(":")
                val mins = parts[0].toLong()
                val secParts = parts[1].split(".")
                val secs = secParts[0].toLong()
                val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                return mins * 60000 + secs * 1000 + ms
            }
            val diff = parseMs(other) - parseMs(p1)
            if (diff <= 0) null else "+${diff / 1000}.${(diff % 1000).toString().padStart(3, '0')}"
        } catch (_: Exception) { null }
    }

    override suspend fun getSchedule(): List<RaceScheduleEntry> {
        return try {
            Log.d(TAG, "Fetching 2026 race schedule from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val races = root["MRData"]?.jsonObject
                ?.get("RaceTable")?.jsonObject
                ?.get("Races")?.jsonArray ?: return emptyList()

            races.map { it.jsonObject }.map { race ->
                val circuit = race["Circuit"]?.jsonObject
                val location = circuit?.get("Location")?.jsonObject
                val country = location?.get("country")?.jsonPrimitive?.content ?: ""
                val circuitId = circuit?.get("circuitId")?.jsonPrimitive?.content ?: ""
                val meta = getCircuitMeta(circuitId)
                RaceScheduleEntry(
                    round = race["round"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    raceName = race["raceName"]?.jsonPrimitive?.content ?: "",
                    circuitName = circuit?.get("circuitName")?.jsonPrimitive?.content ?: "",
                    locality = location?.get("locality")?.jsonPrimitive?.content ?: "",
                    country = country,
                    raceDate = race["date"]?.jsonPrimitive?.content ?: "",
                    raceTime = race["time"]?.jsonPrimitive?.content,
                    qualifyingDate = race["Qualifying"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    qualifyingTime = race["Qualifying"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    sprintDate = race["Sprint"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    sprintTime = race["Sprint"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    fp1Date = race["FirstPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    fp2Date = race["SecondPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    fp3Date = race["ThirdPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    countryCode = countryToFlag(country),
                    circuitId = circuitId,
                    laps = meta.laps,
                    lapRecord = meta.lapRecord,
                    lapRecordHolder = meta.lapRecordHolder,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching schedule: ${e.message}")
            emptyList()
        }
    }

    private data class CircuitMeta(val laps: Int? = null, val lapRecord: String? = null, val lapRecordHolder: String? = null)

    private fun getCircuitMeta(circuitId: String): CircuitMeta = when (circuitId) {
        "albert_park"       -> CircuitMeta(58, "1:20.235", "Charles Leclerc (2022)")
        "shanghai"          -> CircuitMeta(56, "1:32.238", "Michael Schumacher (2004)")
        "bahrain"           -> CircuitMeta(57, "1:31.447", "Pedro de la Rosa (2005)")
        "jeddah"            -> CircuitMeta(50, "1:30.734", "Lewis Hamilton (2021)")
        "suzuka"            -> CircuitMeta(53, "1:30.983", "Kimi Räikkönen (2005)")
        "miami"             -> CircuitMeta(57, "1:29.708", "Max Verstappen (2023)")
        "imola"             -> CircuitMeta(63, "1:15.484", "Rubens Barrichello (2004)")
        "monaco"            -> CircuitMeta(78, "1:12.909", "Rubens Barrichello (2004)")
        "villeneuve"        -> CircuitMeta(70, "1:13.078", "Valtteri Bottas (2016)")
        "catalunya"         -> CircuitMeta(66, "1:16.330", "Rubens Barrichello (2009)")
        "red_bull_ring"     -> CircuitMeta(71, "1:05.619", "Carlos Sainz (2020)")
        "silverstone"       -> CircuitMeta(52, "1:27.097", "Max Verstappen (2020)")
        "hungaroring"       -> CircuitMeta(70, "1:16.627", "Lewis Hamilton (2020)")
        "spa"               -> CircuitMeta(44, "1:46.286", "Valtteri Bottas (2018)")
        "zandvoort"         -> CircuitMeta(72, "1:11.097", "Lewis Hamilton (2021)")
        "monza"             -> CircuitMeta(53, "1:21.046", "Rubens Barrichello (2004)")
        "baku"              -> CircuitMeta(51, "1:43.009", "Charles Leclerc (2019)")
        "marina_bay"        -> CircuitMeta(62, "1:35.867", "Lewis Hamilton (2023)")
        "rodriguez"         -> CircuitMeta(71, "1:17.774", "Valtteri Bottas (2021)")
        "interlagos"        -> CircuitMeta(71, "1:10.540", "Rubens Barrichello (2004)")
        "las_vegas"         -> CircuitMeta(50, "1:35.490", "Oscar Piastri (2023)")
        "losail"            -> CircuitMeta(57, "1:24.319", "Max Verstappen (2023)")
        "yas_marina"        -> CircuitMeta(58, "1:26.103", "Max Verstappen (2021)")
        "austin"            -> CircuitMeta(56, "1:36.169", "Charles Leclerc (2019)")
        else                -> CircuitMeta()
    }

    private fun countryToFlag(country: String): String {
        return when (country.lowercase()) {
            "australia" -> "🇦🇺"
            "china" -> "🇨🇳"
            "japan" -> "🇯🇵"
            "bahrain" -> "🇧🇭"
            "saudi arabia" -> "🇸🇦"
            "usa", "united states" -> "🇺🇸"
            "canada" -> "🇨🇦"
            "monaco" -> "🇲🇨"
            "spain" -> "🇪🇸"
            "austria" -> "🇦🇹"
            "uk", "united kingdom" -> "🇬🇧"
            "belgium" -> "🇧🇪"
            "hungary" -> "🇭🇺"
            "netherlands" -> "🇳🇱"
            "italy" -> "🇮🇹"
            "azerbaijan" -> "🇦🇿"
            "singapore" -> "🇸🇬"
            "mexico" -> "🇲🇽"
            "brazil" -> "🇧🇷"
            "qatar" -> "🇶🇦"
            "uae", "abu dhabi" -> "🇦🇪"
            "las vegas" -> "🇺🇸"
            else -> "🏁"
        }
    }

    override suspend fun getF1News(): List<F1NewsArticle> {
        return try {
            Log.d(TAG, "Fetching F1 news from RSS feed...")
            // Use rss2json free API with the official F1 RSS feed
            val response = client.get("https://api.rss2json.com/v1/api.json") {
                parameter("rss_url", "https://www.motorsport.com/rss/f1/news/")
                parameter("count", "10")
            }.body<String>()

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val status = root["status"]?.jsonPrimitive?.content
            val items = root["items"]?.jsonArray

            if (status == "ok" && items != null && items.isNotEmpty()) {
                items.map { it.jsonObject }.take(5).map { item ->
                    F1NewsArticle(
                        title = item["title"]?.jsonPrimitive?.content ?: "F1 News",
                        description = stripHtml(item["description"]?.jsonPrimitive?.content ?: ""),
                        url = item["link"]?.jsonPrimitive?.content ?: "https://www.formula1.com",
                        publishedAt = item["pubDate"]?.jsonPrimitive?.content ?: "",
                        category = item["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.uppercase() ?: "F1"
                    )
                }
            } else {
                // Fallback: try autosport F1 feed
                fetchNewsFromFeed("https://www.autosport.com/rss/f1/news/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching F1 news: ${e.message}")
            try {
                fetchNewsFromFeed("https://www.autosport.com/rss/f1/news/")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback news also failed: ${e2.message}")
                emptyList()
            }
        }
    }

    private suspend fun fetchNewsFromFeed(feedUrl: String): List<F1NewsArticle> {
        val response = client.get("https://api.rss2json.com/v1/api.json") {
            parameter("rss_url", feedUrl)
            parameter("count", "10")
        }.body<String>()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(response).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList()
        return items.map { it.jsonObject }.take(5).map { item ->
            F1NewsArticle(
                title = item["title"]?.jsonPrimitive?.content ?: "F1 News",
                description = stripHtml(item["description"]?.jsonPrimitive?.content ?: ""),
                url = item["link"]?.jsonPrimitive?.content ?: "https://www.formula1.com",
                publishedAt = item["pubDate"]?.jsonPrimitive?.content ?: "",
                category = "F1"
            )
        }
    }

    private fun getTeamColor(constructorId: String): String {
        // 2026 colours sourced directly from OpenF1 team_colour field
        return when (constructorId) {
            "red_bull"                       -> "4781D7"
            "mercedes"                       -> "00D7B6"
            "ferrari"                        -> "ED1131"
            "mclaren"                        -> "F47600"
            "aston_martin"                   -> "229971"
            "alpine"                         -> "00A1E8"
            "williams"                       -> "1868DB"
            "rb", "racing_bulls"             -> "6C98FF"
            "sauber", "kick_sauber", "audi"  -> "F50537"
            "haas"                           -> "9C9FA2"
            "cadillac"                       -> "909090"
            else                             -> "FFFFFF"
        }
    }

    private fun getTeamLogo(constructorId: String): String {
        // Use the official F1 website CDN paths for 2025/2026 (same asset names as used in the F1 app)
        return when (constructorId) {
            "red_bull"                       -> "https://media.formula1.com/content/dam/fom-website/teams/2025/red-bull-racing-logo.png"
            "mercedes"                       -> "https://media.formula1.com/content/dam/fom-website/teams/2025/mercedes-logo.png"
            "ferrari"                        -> "https://media.formula1.com/content/dam/fom-website/teams/2025/ferrari-logo.png"
            "mclaren"                        -> "https://media.formula1.com/content/dam/fom-website/teams/2025/mclaren-logo.png"
            "aston_martin"                   -> "https://media.formula1.com/content/dam/fom-website/teams/2025/aston-martin-logo.png"
            "alpine"                         -> "https://media.formula1.com/content/dam/fom-website/teams/2025/alpine-logo.png"
            "williams"                       -> "https://media.formula1.com/content/dam/fom-website/teams/2025/williams-logo.png"
            "rb", "racing_bulls"             -> "https://media.formula1.com/content/dam/fom-website/teams/2025/rb-logo.png"
            "sauber", "kick_sauber"          -> "https://media.formula1.com/content/dam/fom-website/teams/2025/kick-sauber-logo.png"
            "audi"                           -> "https://media.formula1.com/content/dam/fom-website/teams/2026/audi-logo.png"
            "haas"                           -> "https://media.formula1.com/content/dam/fom-website/teams/2025/haas-f1-team-logo.png"
            "cadillac"                       -> "https://media.formula1.com/content/dam/fom-website/teams/2026/cadillac-logo.png"
            else                             -> "https://media.formula1.com/content/dam/fom-website/manual/f1-logo.png"
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<.*?>"), "").trim()
    }
}
