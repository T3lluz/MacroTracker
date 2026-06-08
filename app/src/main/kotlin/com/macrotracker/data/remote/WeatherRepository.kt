package com.macrotracker.data.remote

import android.util.Log
import com.macrotracker.util.SunCalculator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import com.macrotracker.R

data class HourlyForecast(
    val time: String,       // e.g. "14:00"
    val temperature: Double,
    val iconRes: Int,
    val windSpeed: Double,
    val description: String,
    val symbolCode: String,
    val dateStr: String? = null, // ISO date "yyyy-MM-dd"
    val precipitation: Double? = null, // mm for next_1_hours
)

data class DailyForecast(
    val date: String,       // e.g. "Mon", "Tue"
    val dateFull: String,   // e.g. "2026-03-07"
    val minTemp: Double,
    val maxTemp: Double,
    val iconRes: Int,
    val description: String,
    val symbolCode: String,
)

data class WeatherInfo(
    val temperature: Double,
    val windSpeed: Double,
    val symbolCode: String,
    val description: String,
    val iconRes: Int,
    val locationName: String = "",
    val feelsLike: Double? = null,      // dew-point-based approximation from Yr.no
    val humidity: Double? = null,       // relative_humidity from Yr.no
    val hourlyForecasts: List<HourlyForecast> = emptyList(),
    val dailyForecasts: List<DailyForecast> = emptyList(),
    val sunrise: String? = null,
    val sunset: String? = null,
)

@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "WeatherRepo"
        private const val BASE_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        private const val USER_AGENT = "DailyDash/1.0 (Android; daily-dash-app)"
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes

        fun mapSymbolCode(code: String): Pair<String, Int> {
            // Yr.no symbol codes: https://api.met.no/weatherapi/weathericon/2.0/documentation
            // Strip _day/_night/_polartwilight suffix for matching
            val base = code.replace("_day", "").replace("_night", "").replace("_polartwilight", "")
            return when {
                base == "clearsky" -> "Clear Sky" to R.drawable.ic_weather_sun
                base == "fair" -> "Fair" to R.drawable.ic_weather_cloud_sun
                base.startsWith("partlycloudy") -> "Partly Cloudy" to R.drawable.ic_weather_cloud_sun
                base == "cloudy" -> "Cloudy" to R.drawable.ic_weather_cloud
                base == "fog" -> "Fog" to R.drawable.ic_weather_fog
                base.contains("thunder") && base.contains("rain") -> "Thunderstorm" to R.drawable.ic_weather_storm
                base.contains("thunder") -> "Thunder" to R.drawable.ic_weather_lightning
                base.contains("heavyrain") -> "Heavy Rain" to R.drawable.ic_weather_rain
                base.contains("rain") -> "Rain" to R.drawable.ic_weather_rain
                base.contains("sleet") -> "Sleet" to R.drawable.ic_weather_snow
                base.contains("snow") -> "Snow" to R.drawable.ic_weather_snow
                else -> {
                    val fallbackName = code.replace("_", " ").replaceFirstChar { it.uppercase() }
                    val fallbackIcon = when {
                        base.contains("cloud") -> R.drawable.ic_weather_cloud
                        base.contains("rain") -> R.drawable.ic_weather_rain
                        base.contains("snow") || base.contains("sleet") -> R.drawable.ic_weather_snow
                        base.contains("sun") || base.contains("clear") -> R.drawable.ic_weather_sun
                        else -> R.drawable.ic_weather_cloud // Better neutral fallback than sun
                    }
                    fallbackName to fallbackIcon
                }
            }
        }
    }

    // In-memory cache — keyed by rounded lat/lon so nearby locations reuse the same result
    private var cachedWeather: WeatherInfo? = null
    private var cachedLat: Double = Double.NaN
    private var cachedLon: Double = Double.NaN
    private var cacheTimestamp: Long = 0L

    suspend fun fetchWeather(lat: Double, lon: Double, locationName: String = ""): WeatherInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Round to 2 decimal places (~1 km) for cache key comparison
        val roundedLat = (lat * 100).roundToLong().toDouble() / 100
        val roundedLon = (lon * 100).roundToLong().toDouble() / 100
        if (cachedWeather != null &&
            roundedLat == cachedLat && roundedLon == cachedLon &&
            now - cacheTimestamp < CACHE_TTL_MS
        ) {
            Log.d(TAG, "Returning cached weather")
            return@withContext cachedWeather!!
        }

        val url = "$BASE_URL?lat=${String.format(Locale.US, "%.4f", lat)}&lon=${String.format(Locale.US, "%.4f", lon)}"
        Log.d(TAG, "Fetching weather: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val result = httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response from Yr.no")
            if (!response.isSuccessful) {
                Log.e(TAG, "Weather API error ${response.code}: $body")
                throw Exception("Weather API error: ${response.code}")
            }
            parseWeatherResponse(body, locationName, lat, lon)
        }
        cachedWeather = result
        cachedLat = roundedLat
        cachedLon = roundedLon
        cacheTimestamp = now
        result
    }

    /** Forcing a refresh by clearing the cache for the current location. */
    fun clearCache() {
        cachedWeather = null
        cachedLat = Double.NaN
        cachedLon = Double.NaN
    }

    private fun parseWeatherResponse(json: String, locationName: String, lat: Double, lon: Double): WeatherInfo {
        val root = JSONObject(json)
        val timeseries = root
            .getJSONObject("properties")
            .getJSONArray("timeseries")

        if (timeseries.length() == 0) throw Exception("No weather data available")

        // Find the most relevant current entry (not too far in the past)
        var first = timeseries.getJSONObject(0)
        val now = Instant.now()
        for (i in 0 until timeseries.length()) {
            val entry = timeseries.getJSONObject(i)
            val time = entry.getString("time")
            if (Instant.parse(time).plusSeconds(3600).isAfter(now)) {
                first = entry
                break
            }
        }

        val data = first.getJSONObject("data")
        val instant = data.getJSONObject("instant").getJSONObject("details")

        val temperature = instant.getDouble("air_temperature")
        val windSpeed = instant.getDouble("wind_speed")
        val humidity = instant.optDouble("relative_humidity").takeIf { !it.isNaN() }
        // Yr.no doesn't give feels-like directly; use dew point as a proxy when available
        val feelsLike = instant.optDouble("dew_point_temperature").takeIf { !it.isNaN() }

        // Symbol code from next_1_hours or next_6_hours
        val symbolCode = when {
            data.has("next_1_hours") ->
                data.getJSONObject("next_1_hours")
                    .getJSONObject("summary")
                    .getString("symbol_code")
            data.has("next_6_hours") ->
                data.getJSONObject("next_6_hours")
                    .getJSONObject("summary")
                    .getString("symbol_code")
            else -> "cloudy"
        }

        val (description, iconRes) = mapSymbolCode(symbolCode)

        // Parse hourly forecasts (next 72 entries)
        val hourlyForecasts = mutableListOf<HourlyForecast>()
        val nowInstant = Instant.now()
        for (i in 0 until timeseries.length()) {
            if (hourlyForecasts.size >= 72) break
            try {
                val entry = timeseries.getJSONObject(i)
                val time = entry.getString("time") // ISO 8601

                // Parse ISO 8601 time string
                val zdt = ZonedDateTime.parse(time)

                // Hourly forecast should start at the next forecast slot, not the
                // current/previous hour. At 16:31 the first item should be 17:00.
                if (!zdt.toInstant().isAfter(nowInstant)) {
                    continue
                }

                val entryData = entry.getJSONObject("data")
                val entryInstant = entryData.getJSONObject("instant").getJSONObject("details")
                val temp = entryInstant.getDouble("air_temperature")
                val wind = entryInstant.getDouble("wind_speed")
                val entrySymbol = when {
                    entryData.has("next_1_hours") ->
                        entryData.getJSONObject("next_1_hours")
                            .getJSONObject("summary")
                            .getString("symbol_code")
                    entryData.has("next_6_hours") ->
                        entryData.getJSONObject("next_6_hours")
                            .getJSONObject("summary")
                            .getString("symbol_code")
                    else -> symbolCode
                }
                val (entryDesc, entryIconRes) = mapSymbolCode(entrySymbol)

                // Precipitation for the next 1 hour
                val entryPrecip = if (entryData.has("next_1_hours")) {
                    entryData.getJSONObject("next_1_hours")
                        .getJSONObject("details")
                        .optDouble("precipitation_amount", 0.0)
                } else null

                // Convert to local timezone for display
                val localZdt = zdt.withZoneSameInstant(ZoneId.systemDefault())
                val hour = localZdt.format(DateTimeFormatter.ofPattern("h a", Locale.US))
                val dateStr = localZdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                hourlyForecasts.add(HourlyForecast(hour, temp, entryIconRes, wind, entryDesc, entrySymbol, dateStr, entryPrecip))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping hourly entry $i: ${e.message}")
            }
        }

        // Parse daily forecasts (group by date, next 7 days)
        val dailyMap = linkedMapOf<String, MutableList<Double>>() // date -> list of temps
        val dailySymbols = linkedMapOf<String, String>() // date -> most common symbol
        for (i in 0 until timeseries.length()) {
            try {
                val entry = timeseries.getJSONObject(i)
                val time = entry.getString("time")
                val dateStr = time.substringBefore("T")
                val entryData = entry.getJSONObject("data")
                val temp = entryData.getJSONObject("instant").getJSONObject("details")
                    .getDouble("air_temperature")
                dailyMap.getOrPut(dateStr) { mutableListOf() }.add(temp)
                // Take noon symbol or first available for the day
                if (!dailySymbols.containsKey(dateStr) || time.contains("T12:")) {
                    val sym = when {
                        entryData.has("next_6_hours") ->
                            entryData.getJSONObject("next_6_hours")
                                .getJSONObject("summary")
                                .getString("symbol_code")
                        entryData.has("next_1_hours") ->
                            entryData.getJSONObject("next_1_hours")
                                .getJSONObject("summary")
                                .getString("symbol_code")
                        else -> null
                    }
                    if (sym != null) dailySymbols[dateStr] = sym
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping daily entry $i: ${e.message}")
            }
        }

        val dailyForecasts = dailyMap.entries.drop(1).take(7).map { (dateStr, temps) ->
            val sym = dailySymbols[dateStr] ?: symbolCode
            val (dayDesc, dayIconRes) = mapSymbolCode(sym)
            // Format date as day name
            val dayName = try {
                LocalDate.parse(dateStr)
                    .dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
            } catch (_: Exception) { dateStr }
            DailyForecast(dayName, dateStr, temps.min(), temps.max(), dayIconRes, dayDesc, sym)
        }

        val (sunrise, sunset) = SunCalculator.calculate(lat, lon, LocalDate.now(), ZoneId.systemDefault()) ?: (null to null)

        return WeatherInfo(
            temperature = temperature,
            windSpeed = windSpeed,
            symbolCode = symbolCode,
            description = description,
            iconRes = iconRes,
            locationName = locationName,
            feelsLike = feelsLike,
            humidity = humidity,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts,
            sunrise = sunrise,
            sunset = sunset,
        )
    }

    private fun mapSymbolCode(code: String): Pair<String, Int> = WeatherRepository.mapSymbolCode(code)
}
