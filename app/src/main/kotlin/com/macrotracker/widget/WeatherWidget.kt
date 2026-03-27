package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.macrotracker.MainActivity

/**
 * Weather widget optimized for fixed 5x3 with a guaranteed visible hourly section.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { WeatherRoot(data) } }
    }
}

@Composable
private fun WeatherRoot(d: DashboardWidgetData) {
    val c = WidgetClr()
    val sc = WScale.from()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.corner)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(sc.pad),
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            WeatherHeaderBlock(d, c, sc, showGreeting = true)
            Spacer(GlanceModifier.height(sc.spaceXs))

            if (!d.hasWeatherData) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NoDataPlaceholder("🌤", "No weather data", c, sc)
                }
                return@Column
            }

            WeatherHeroCard(d, c, sc)
            Spacer(GlanceModifier.height(sc.spaceXs))

            WeatherMetaLine(d, c, sc)
            Spacer(GlanceModifier.height(sc.spaceXs))
            Spacer(GlanceModifier.defaultWeight())

            Text("Hourly forecast", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            Spacer(GlanceModifier.height(sc.spaceXs))
            Box(GlanceModifier.fillMaxWidth().height(118.dp)) {
                HourlyScrollableSection(d.hourlyForecast.take(12), c, sc)
            }

            if (!d.aiInsightWeather.isNullOrBlank() && d.hourlyForecast.isEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                AiInsightBanner("🤖 ${d.aiInsightWeather}", c, sc)
            }
        }
    }
}

@Composable
private fun WeatherHeroCard(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(sc.cornerSm)
            .background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.weatherIcon ?: "🌡️", style = TextStyle(fontSize = sc.iconHero))
            Spacer(GlanceModifier.width(sc.spaceSm))

            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${d.weatherTemp ?: "--"}°",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.weather),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(
                        "↑${d.weatherHigh ?: "--"}°",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.cal),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(
                        "↓${d.weatherLow ?: "--"}°",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.steps),
                        maxLines = 1,
                    )
                }

                val desc = d.weatherDescription?.replaceFirstChar { it.uppercase() }.orEmpty()
                if (desc.isNotBlank()) {
                    Text(desc, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WeatherMetaLine(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val line = buildString {
        append("Feels ${d.weatherFeelsLike ?: d.weatherTemp ?: "--"}°")
        append(" • Wind ${d.weatherWindSpeed ?: "--"} km/h")
        append(" • Humid ${d.weatherHumidity ?: "--"}%")
    }
    Text(line, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
}

@Composable
private fun HourlyScrollableSection(hours: List<HourlyForecast>, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.cornerSm)
            .background(c.card),
    ) {
        if (hours.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hourly forecast yet", style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = sc.spaceSm, vertical = sc.spaceSm)) {
                items(hours) { slot ->
                    HourlyForecastRow(slot, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceMd))
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastRow(slot: HourlyForecast, c: WidgetClr, sc: WScale) {
    val tempColor: ColorProvider = slot.temp.toIntOrNull()?.let { temp ->
        when {
            temp >= 25 -> c.cal
            temp <= 5 -> c.steps
            else -> c.weather
        }
    } ?: c.weather

    Box(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(sc.cornerSm)
            .background(c.cardAlt)
            .padding(horizontal = sc.padSm, vertical = 10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(slot.hour, style = TextStyle(fontSize = sc.fsm, color = c.sub, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(sc.spaceSm))
            Text(slot.icon, style = TextStyle(fontSize = sc.iconMd))
            Spacer(GlanceModifier.width(sc.spaceSm))

            Column(GlanceModifier.defaultWeight()) {
                val line1 = buildString {
                    append("${slot.temp}°")
                    slot.pop?.takeIf { it > 0 }?.let { append(" • 💧$it%") }
                }
                Text(
                    line1,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = tempColor),
                    maxLines = 1,
                )

                val line2 = buildString {
                    slot.windSpeed?.takeIf { it.isNotBlank() }?.let { append("💨 $it") }
                    slot.description?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" • ")
                        append(it)
                    }
                }
                if (line2.isNotBlank()) {
                    Text(line2, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WeatherHeaderBlock(
    d: DashboardWidgetData,
    c: WidgetClr,
    sc: WScale,
    showGreeting: Boolean = false,
) {
    Column(GlanceModifier.fillMaxWidth()) {
        WidgetHeader(
            title = "Weather",
            c = c,
            sc = sc,
            showGreeting = showGreeting,
            lastUpdatedAt = d.lastUpdatedAt,
            accent = c.weather,
        )
        if (!d.weatherLocation.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍", style = TextStyle(fontSize = sc.fsm))
                Spacer(GlanceModifier.width(sc.spaceXs))
                Text(d.weatherLocation, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
            }
        }
    }
}

