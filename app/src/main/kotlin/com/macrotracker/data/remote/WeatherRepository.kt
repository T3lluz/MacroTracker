package com.macrotracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class HourlyForecast(
    val time: String,       // e.g. "14:00"
    val temperature: Double,
    val icon: String,
    val windSpeed: Double,
    val description: String,
)

data class DailyForecast(
    val date: String,       // e.g. "Mon", "Tue"
    val dateFull: String,   // e.g. "2026-03-07"
    val minTemp: Double,
    val maxTemp: Double,
    val icon: String,
    val description: String,
)

data class WeatherInfo(
    val temperature: Double,
    val windSpeed: Double,
    val symbolCode: String,
    val description: String,
    val icon: String, // emoji
    val locationName: String = "",
    val hourlyForecasts: List<HourlyForecast> = emptyList(),
    val dailyForecasts: List<DailyForecast> = emptyList(),
)

@Singleton
class WeatherRepository @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "WeatherRepo"
        private const val BASE_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        // Yr.no requires a descriptive User-Agent
        private const val USER_AGENT = "MacroTracker/1.0 (Android; macro-tracker-app)"
    }

    suspend fun fetchWeather(lat: Double, lon: Double, locationName: String = ""): WeatherInfo = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?lat=${String.format(Locale.US, "%.4f", lat)}&lon=${String.format(Locale.US, "%.4f", lon)}"
        Log.d(TAG, "Fetching weather: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from Yr.no")

        if (!response.isSuccessful) {
            Log.e(TAG, "Weather API error ${response.code}: $body")
            throw Exception("Weather API error: ${response.code}")
        }

        parseWeatherResponse(body, locationName)
    }

    private fun parseWeatherResponse(json: String, locationName: String): WeatherInfo {
        val root = JSONObject(json)
        val timeseries = root
            .getJSONObject("properties")
            .getJSONArray("timeseries")

        if (timeseries.length() == 0) throw Exception("No weather data available")

        val first = timeseries.getJSONObject(0)
        val data = first.getJSONObject("data")
        val instant = data.getJSONObject("instant").getJSONObject("details")

        val temperature = instant.getDouble("air_temperature")
        val windSpeed = instant.getDouble("wind_speed")

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

        val (description, icon) = mapSymbolCode(symbolCode)

        // Parse hourly forecasts (next 24 entries)
        val hourlyForecasts = mutableListOf<HourlyForecast>()
        val hourlyCount = minOf(timeseries.length(), 24)
        for (i in 0 until hourlyCount) {
            try {
                val entry = timeseries.getJSONObject(i)
                val time = entry.getString("time") // ISO 8601
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
                val (entryDesc, entryIcon) = mapSymbolCode(entrySymbol)
                // Extract hour from ISO time (e.g. "2026-03-06T14:00:00Z" -> "14:00")
                val hour = time.substringAfter("T").take(5)
                hourlyForecasts.add(HourlyForecast(hour, temp, entryIcon, wind, entryDesc))
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
            val (dayDesc, dayIcon) = mapSymbolCode(sym)
            // Format date as day name
            val dayName = try {
                java.time.LocalDate.parse(dateStr)
                    .dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
            } catch (_: Exception) { dateStr }
            DailyForecast(dayName, dateStr, temps.min(), temps.max(), dayIcon, dayDesc)
        }

        return WeatherInfo(
            temperature = temperature,
            windSpeed = windSpeed,
            symbolCode = symbolCode,
            description = description,
            icon = icon,
            locationName = locationName,
            hourlyForecasts = hourlyForecasts,
            dailyForecasts = dailyForecasts,
        )
    }

    private fun mapSymbolCode(code: String): Pair<String, String> {
        // Yr.no symbol codes: https://api.met.no/weatherapi/weathericon/2.0/documentation
        // Strip _day/_night/_polartwilight suffix for matching
        val base = code.replace("_day", "").replace("_night", "").replace("_polartwilight", "")
        return when {
            base == "clearsky" -> "Clear Sky" to "☀️"
            base == "fair" -> "Fair" to "🌤️"
            base.startsWith("partlycloudy") -> "Partly Cloudy" to "⛅"
            base == "cloudy" -> "Cloudy" to "☁️"
            base == "fog" -> "Fog" to "🌫️"
            base.contains("thunder") && base.contains("rain") -> "Thunderstorm" to "⛈️"
            base.contains("thunder") -> "Thunder" to "🌩️"
            base == "lightrain" || base == "lightrainshowers" -> "Light Rain" to "🌦️"
            base == "rain" || base == "rainshowers" -> "Rain" to "🌧️"
            base == "heavyrain" || base == "heavyrainshowers" -> "Heavy Rain" to "🌧️"
            base.contains("sleet") -> "Sleet" to "🌨️"
            base == "lightsnow" || base == "lightsnowshowers" -> "Light Snow" to "🌨️"
            base == "snow" || base == "snowshowers" -> "Snow" to "❄️"
            base == "heavysnow" || base == "heavysnowshowers" -> "Heavy Snow" to "❄️"
            base.contains("rain") -> "Rain" to "🌧️"
            base.contains("snow") -> "Snow" to "❄️"
            else -> code.replace("_", " ").replaceFirstChar { it.uppercase() } to "🌡️"
        }
    }
}



