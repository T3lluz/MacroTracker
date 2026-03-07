package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.macrotracker.MainActivity
import com.macrotracker.R
import java.time.LocalTime

class DashboardWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent {
            GlanceTheme { WidgetRoot(data) }
        }
    }
}

// ── Size buckets ─────────────────────────────────────────────────
private enum class WSize { TINY, COMPACT, MEDIUM, LARGE }
private fun classify(w: Dp, h: Dp) = when {
    h < 120.dp || w < 170.dp -> WSize.TINY
    h < 200.dp               -> WSize.COMPACT
    h < 320.dp               -> WSize.MEDIUM
    else                      -> WSize.LARGE
}

// ── Colours ──────────────────────────────────────────────────────
private class Clr(
    val bg: ColorProvider      = ColorProvider(R.color.widget_surface),
    val text: ColorProvider    = ColorProvider(R.color.widget_on_surface),
    val sub: ColorProvider     = ColorProvider(R.color.widget_subtitle),
    val card: ColorProvider    = ColorProvider(R.color.widget_card_bg),
    val cal: ColorProvider     = ColorProvider(R.color.widget_calorie),
    val pro: ColorProvider     = ColorProvider(R.color.widget_protein),
    val track: ColorProvider   = ColorProvider(R.color.widget_track_bg),
    val steps: ColorProvider   = ColorProvider(R.color.widget_steps),
    val sleep: ColorProvider   = ColorProvider(R.color.widget_sleep),
    val heart: ColorProvider   = ColorProvider(R.color.widget_heart),
    val weather: ColorProvider = ColorProvider(R.color.widget_weather),
    val event: ColorProvider   = ColorProvider(R.color.widget_calendar),
    val pill: ColorProvider    = ColorProvider(R.color.widget_accent),
    val onPill: ColorProvider  = ColorProvider(R.color.widget_on_accent),
)

// ═════════════════════════════════════════════════════════════════
//  Root
// ═════════════════════════════════════════════════════════════════
@Composable
private fun WidgetRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c = Clr()
    val pad = if (ws == WSize.TINY) 10.dp else 14.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(pad),
    ) {
        when (ws) {
            WSize.TINY    -> TinyBody(data, c)
            WSize.COMPACT -> CompactBody(data, c)
            WSize.MEDIUM  -> MediumBody(data, c)
            WSize.LARGE   -> LargeBody(data, c)
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  TINY  — single calorie number
// ═════════════════════════════════════════════════════════════════
@Composable
private fun TinyBody(d: DashboardWidgetData, c: Clr) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.cal))
        Text("of ${d.calorieGoal} kcal", style = TextStyle(fontSize = 11.sp, color = c.sub))
        Spacer(GlanceModifier.height(6.dp))
        Bar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track)
    }
}

// ═════════════════════════════════════════════════════════════════
//  COMPACT  — header + 2×2 stat cards
// ═════════════════════════════════════════════════════════════════
@Composable
private fun CompactBody(d: DashboardWidgetData, c: Clr) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(d, c)
        Spacer(GlanceModifier.height(8.dp))
        val cards = buildCards(d)
        EvenGrid(cards.take(4), 2, c)
    }
}

// ═════════════════════════════════════════════════════════════════
//  MEDIUM  — header + AI insight + 2×3 grid of stat cards
// ═════════════════════════════════════════════════════════════════
@Composable
private fun MediumBody(d: DashboardWidgetData, c: Clr) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(d, c)
        Spacer(GlanceModifier.height(8.dp))

        // AI insight banner
        if (!d.aiInsight.isNullOrBlank()) {
            AiInsight(d.aiInsight, c)
            Spacer(GlanceModifier.height(8.dp))
        }

        // Fill remaining space with cards
        Spacer(GlanceModifier.defaultWeight())
        val cards = buildCards(d)
        EvenGrid(cards.take(6), 3, c)
    }
}

// ═════════════════════════════════════════════════════════════════
//  LARGE  — header + AI insight + 3×3 grid
// ═════════════════════════════════════════════════════════════════
@Composable
private fun LargeBody(d: DashboardWidgetData, c: Clr) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(d, c)
        Spacer(GlanceModifier.height(8.dp))

        if (!d.aiInsight.isNullOrBlank()) {
            AiInsight(d.aiInsight, c)
            Spacer(GlanceModifier.height(8.dp))
        }

        Spacer(GlanceModifier.defaultWeight())
        val cards = buildCards(d)
        EvenGrid(cards.take(9), 3, c)
    }
}

// ═════════════════════════════════════════════════════════════════
//  Header  — greeting + refresh button
// ═════════════════════════════════════════════════════════════════
@Composable
private fun Header(d: DashboardWidgetData, c: Clr) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            greeting(),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())

        // Refresh button — pill shape, easy tap target
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .cornerRadius(16.dp)
                .background(c.card)
                .clickable(actionRunCallback<RefreshWidgetAction>())
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("↻", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.sub))
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning ☀️"
        h < 17 -> "Good afternoon 👋"
        else   -> "Good evening 🌙"
    }
}

// ═════════════════════════════════════════════════════════════════
//  AI insight banner
// ═════════════════════════════════════════════════════════════════
@Composable
private fun AiInsight(text: String, c: Clr) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(c.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨", style = TextStyle(fontSize = 12.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(text, style = TextStyle(fontSize = 11.sp, color = c.sub), maxLines = 2)
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Card grid — even rows, fills width
// ═════════════════════════════════════════════════════════════════
@Composable
private fun EvenGrid(cards: List<CInfo>, cols: Int, c: Clr) {
    val rows = cards.chunked(cols)
    rows.forEachIndexed { i, row ->
        if (i > 0) Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            row.forEachIndexed { j, card ->
                if (j > 0) Spacer(GlanceModifier.width(8.dp))
                StatCard(card, c, GlanceModifier.defaultWeight())
            }
            // invisible spacers for incomplete rows
            repeat(cols - row.size) {
                Spacer(GlanceModifier.width(8.dp))
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun StatCard(info: CInfo, c: Clr, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .cornerRadius(16.dp)
            .background(c.card)
            .padding(10.dp),
    ) {
        Column {
            Text(info.icon, style = TextStyle(fontSize = 14.sp))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                info.value,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cardAccent(info.type, c)),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(1.dp))
            Text(info.label, style = TextStyle(fontSize = 10.sp, color = c.sub), maxLines = 1)
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Card data
// ═════════════════════════════════════════════════════════════════
private data class CInfo(val icon: String, val value: String, val label: String, val type: CT)
private enum class CT { CAL, PRO, MEALS, STEPS, HEART, SLEEP, BURN, WEATHER, EVENT, NET }

private fun buildCards(d: DashboardWidgetData): List<CInfo> {
    val l = mutableListOf<CInfo>()

    // ── Always show macros as cards ──
    val calPct = if (d.calorieGoal > 0) (d.totalCalories * 100 / d.calorieGoal) else 0
    l += CInfo("🔥", "${d.totalCalories}", "${calPct}% of ${d.calorieGoal} kcal", CT.CAL)

    val proPct = if (d.proteinGoal > 0) (d.totalProtein * 100 / d.proteinGoal) else 0
    l += CInfo("💪", "${d.totalProtein}g", "${proPct}% of ${d.proteinGoal}g", CT.PRO)

    // ── Meals ──
    l += CInfo("🍽", "${d.mealCount}", if (d.mealCount == 1) "Meal today" else "Meals today", CT.MEALS)

    // ── Health ──
    if (d.hasHealthData && d.steps > 0) {
        val stepPct = (d.steps * 100 / d.stepsGoal).toInt()
        l += CInfo("👟", "%,d".format(d.steps), "$stepPct% of ${"%,d".format(d.stepsGoal)}", CT.STEPS)
    }
    if (d.hasHealthData && d.avgHeartRate > 0)
        l += CInfo("❤️", "${d.avgHeartRate}", "Avg BPM", CT.HEART)
    if (d.hasHealthData && d.sleepMinutes > 0) {
        val h = d.sleepMinutes / 60; val m = d.sleepMinutes % 60
        l += CInfo("😴", "${h}h ${m}m", "Last night", CT.SLEEP)
    }
    if (d.hasHealthData && d.activeCaloriesBurned > 0)
        l += CInfo("⚡", "${d.activeCaloriesBurned.toInt()}", "Active kcal", CT.BURN)

    // ── Weather ──
    if (d.hasWeatherData && d.weatherTemp != null) {
        val sub = buildString {
            append(d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: "")
            if (d.weatherHigh != null && d.weatherLow != null) {
                if (isNotEmpty()) append(" · ")
                append("${d.weatherLow}°/${d.weatherHigh}°")
            }
        }
        l += CInfo(d.weatherIcon ?: "🌡️", "${d.weatherTemp}°", sub.ifBlank { "Now" }, CT.WEATHER)
    }

    // ── Calendar ──
    if (d.hasCalendarData && d.nextEventTitle != null)
        l += CInfo("📅", d.nextEventTitle ?: "",
            "${d.nextEventRelativeDay ?: ""} · ${d.nextEventTime ?: ""}", CT.EVENT)
    else if (d.hasCalendarData && d.eventsToday > 0)
        l += CInfo("📅", "${d.eventsToday}", "Events today", CT.EVENT)

    // ── Net calories (eaten minus burned) ──
    if (d.hasHealthData && d.activeCaloriesBurned > 0) {
        val net = d.totalCalories - d.activeCaloriesBurned.toInt()
        l += CInfo("📊", "$net", "Net kcal", CT.NET)
    }

    return l
}

private fun cardAccent(t: CT, c: Clr): ColorProvider = when (t) {
    CT.CAL     -> c.cal
    CT.PRO     -> c.pro
    CT.MEALS   -> c.text
    CT.STEPS   -> c.steps
    CT.HEART   -> c.heart
    CT.SLEEP   -> c.sleep
    CT.BURN    -> c.cal
    CT.WEATHER -> c.weather
    CT.EVENT   -> c.event
    CT.NET     -> c.text
}

// ═════════════════════════════════════════════════════════════════
//  Mini progress bar (tiny layout only)
// ═════════════════════════════════════════════════════════════════
@Composable
private fun Bar(progress: Float, accent: ColorProvider, track: ColorProvider) {
    val w = LocalSize.current.width - 20.dp
    val filled = (w.value * progress).coerceAtLeast(0f).dp
    Box(GlanceModifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp).background(track)) {
        if (progress > 0f)
            Box(GlanceModifier.width(filled).height(5.dp).cornerRadius(3.dp).background(accent)) {}
    }
}

private fun pct(cur: Int, goal: Int) = if (goal > 0) (cur.toFloat() / goal).coerceIn(0f, 1f) else 0f


