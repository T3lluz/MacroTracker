package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import com.macrotracker.MainActivity

/**
 * Weather widget — 2×2 to 5×3.
 * TINY (2×2): icon + temp
 * COMPACT (*×2): header + location + temp row + hourly (width-adaptive)
 * MEDIUM (2-3 cols, 3 rows): full temp + hourly + AI insight
 * FULL (4-5 cols, 3 rows): temp hero card + hourly + AI + detail cards
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.WIDE_WIDGET)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent {
            GlanceTheme {
                WeatherWidgetRoot(data)
            }
        }
    }
}

@Composable
private fun WeatherWidgetRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.corner)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> WeatherTiny(data, c, sc)
            WSize.COMPACT -> WeatherCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> WeatherMedium(data, c, sc, sz.width)
            WSize.FULL    -> WeatherFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY: icon + temp only ────────────────────────────────────────
@Composable
private fun WeatherTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    if (!d.hasWeatherData) {
        NoDataPlaceholder("🌤", "No weather", c, sc)
    } else {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.weatherIcon ?: "🌡️", style = TextStyle(fontSize = sc.iconHero))
            Spacer(GlanceModifier.height(sc.spaceSm))
            Text(
                "${d.weatherTemp}°",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.weather),
            )
            val desc = d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: ""
            if (desc.isNotBlank()) Text(desc, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

// ── COMPACT: *×2 — header + temp row + feels-like/humidity + hourly ─
@Composable
private fun WeatherCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Weather", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasWeatherData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🌤", "No weather data", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (d.weatherLocation != null) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text(d.weatherLocation, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            Box(
                GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.weatherIcon ?: "🌡️", style = TextStyle(fontSize = sc.iconHero))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("${d.weatherTemp}°", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.weather))
                        val desc = d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: ""
                        if (desc.isNotBlank()) Text(desc, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (d.weatherHigh != null && d.weatherLow != null) {
                            Text("↑ ${d.weatherHigh}°", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.cal))
                            Text("↓ ${d.weatherLow}°", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps))
                        }
                        if (d.weatherFeelsLike != null) {
                            Text("Feels ${d.weatherFeelsLike}°", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                        if (d.weatherHumidity != null) {
                            Text("💧 ${d.weatherHumidity}%", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
            if (d.hourlyForecast.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                val maxSlots = when {
                    w >= 340.dp -> 5
                    w >= 260.dp -> 4
                    else -> 3
                }
                HourlyForecastRow(d.hourlyForecast.take(maxSlots), c, sc)
            }
        }
    }
}

// ── MEDIUM: 2×3 / 3×3 — full temp + hourly + AI ──────────────────
@Composable
private fun WeatherMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Weather", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasWeatherData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🌤", "No weather data", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (d.weatherLocation != null) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text(d.weatherLocation, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            Box(
                GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm)
                    .background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.weatherIcon ?: "🌡️", style = TextStyle(fontSize = sc.iconHero))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            "${d.weatherTemp}°C",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.weather),
                        )
                        val desc = d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: ""
                        if (desc.isNotBlank()) {
                            Text(desc, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                        }
                        if (d.weatherFeelsLike != null) {
                            Text("Feels like ${d.weatherFeelsLike}°", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (d.weatherHigh != null && d.weatherLow != null) {
                            Text("↑ ${d.weatherHigh}°", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.cal))
                            Text("↓ ${d.weatherLow}°", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps))
                        }
                        if (d.weatherHumidity != null) {
                            Text("💧 ${d.weatherHumidity}%", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (d.hourlyForecast.isNotEmpty()) {
                val maxSlots = if (w >= 260.dp) 5 else 4
                HourlyForecastRow(d.hourlyForecast.take(maxSlots), c, sc)
            }
            if (!d.aiInsightWeather.isNullOrBlank()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                AiInsightBanner(d.aiInsightWeather, c, sc)
            }
        }
    }
}

// ── FULL: 4×3 / 5×3 — temp card + hourly + AI + detail cards ─────
@Composable
private fun WeatherFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Weather", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasWeatherData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🌤", "No weather data", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (d.weatherLocation != null) {
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text(d.weatherLocation, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Main temp hero card
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(sc.cornerSm)
                    .background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.weatherIcon ?: "🌡️", style = TextStyle(fontSize = sc.iconHero))
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            "${d.weatherTemp}°C",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.weather),
                        )
                        val desc = d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: ""
                        if (desc.isNotBlank()) {
                            Text(desc, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                        }
                        if (d.weatherFeelsLike != null) {
                            Text("Feels like ${d.weatherFeelsLike}°", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                    if (d.weatherHigh != null && d.weatherLow != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("↑ ${d.weatherHigh}°",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.cal))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("↓ ${d.weatherLow}°",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps))
                            if (d.weatherHumidity != null) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Text("💧 ${d.weatherHumidity}%", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                            }
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Hourly forecast — 5 max to leave room for detail cards
            if (d.hourlyForecast.isNotEmpty()) {
                val maxSlots = if (w >= 340.dp) 5 else 4
                HourlyForecastRow(d.hourlyForecast.take(maxSlots), c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Detail cards take remaining space
            val detailCards = mutableListOf<CardInfo>()
            if (d.weatherHigh != null) detailCards += CardInfo("🔆", "${d.weatherHigh}°", "High", c.cal)
            if (d.weatherLow != null) detailCards += CardInfo("🌙", "${d.weatherLow}°", "Low", c.steps)
            if (d.weatherFeelsLike != null) detailCards += CardInfo("🌡️", "${d.weatherFeelsLike}°", "Feels like", c.weather)
            if (d.weatherHumidity != null) detailCards += CardInfo("💧", "${d.weatherHumidity}%", "Humidity", c.sub)
            if (detailCards.isNotEmpty()) {
                val cols = minOf(cardCols(w), detailCards.size)
                CardGrid(detailCards, cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
            }
        }
    }
}

// ── Hourly forecast horizontal strip ──────────────────────────────
@Composable
private fun HourlyForecastRow(hours: List<HourlyForecast>, c: WidgetClr, sc: WScale) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(sc.cornerSm)
            .background(c.card)
            .padding(horizontal = sc.spaceSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth()) {
            hours.forEachIndexed { i, slot ->
                if (i > 0) Spacer(GlanceModifier.width(sc.spaceXs))
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(slot.hour, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text(slot.icon, style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text(
                        "${slot.temp}°",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.weather),
                        maxLines = 1,
                    )
                    if (slot.pop != null && slot.pop > 0) {
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Text(
                            "💧${slot.pop}%",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.steps),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
