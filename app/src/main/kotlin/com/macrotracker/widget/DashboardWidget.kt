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
 * Dashboard widget — 2×2 to 5×3.
 * TINY (2×2): calorie hero
 * COMPACT (*×2): greeting + calorie bar + width-adaptive card row
 * MEDIUM (2-3 cols, 3 rows): greeting + AI + cal/protein bars + 2-3 col cards
 * FULL (4-5 cols, 3 rows): greeting + AI + calorie card + bars + multi-col cards + meals
 */
class DashboardWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.DASH_WIDGET)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { WidgetRoot(data) } }
    }
}

@Composable
private fun WidgetRoot(data: DashboardWidgetData) {
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
            WSize.TINY    -> TinyBody(data, c, sc)
            WSize.COMPACT -> CompactBody(data, c, sc, sz.width)
            WSize.MEDIUM  -> MediumBody(data, c, sc, sz.width)
            WSize.FULL    -> FullBody(data, c, sc, sz.width)
        }
    }
}

// ── TINY: 2×2 ────────────────────────────────────────────────────
@Composable
private fun TinyBody(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal))
        Text("kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
    }
}

// ── COMPACT: *×2 — greeting + cal bar + width-adaptive cards ─────
@Composable
private fun CompactBody(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        Spacer(GlanceModifier.height(sc.spaceSm))
        Box(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🔥", style = TextStyle(fontSize = sc.iconMd))
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.cal))
                        Text("of ${d.calorieGoal} kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Column(horizontalAlignment = Alignment.End) {
                        val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
                        Text("${calLeft}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text))
                        Text("kcal left", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                }
                Spacer(GlanceModifier.height(sc.spaceSm))
                WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc)
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        val cols = cardCols(w)
        CardGrid(buildCards(d).take(cols), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
    }
}

// ── MEDIUM: 2×3 / 3×3 — greeting + AI + bars + col-adaptive cards
@Composable
private fun MediumBody(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsight.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsight, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        Box(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥 Today", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("${d.mealCount} meal${if (d.mealCount != 1) "s" else ""}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                }
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.cal))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text("/ ${d.calorieGoal}", style = TextStyle(fontSize = sc.fsm, color = c.sub))
                }
                Spacer(GlanceModifier.height(sc.spaceSm))
                WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc)
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        LabeledBar("💪 Protein", "${d.totalProtein}g / ${d.proteinGoal}g", pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        val cols = cardCols(w)
        CardGrid(buildCards(d).take(cols * 2), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
    }
}

// ── FULL: 4×3 / 5×3 — calorie card + bars + multi-col cards ─────
@Composable
private fun FullBody(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsight.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsight, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Calorie summary highlight card
        Box(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal))
                    Text("of ${d.calorieGoal} kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                }
                Spacer(GlanceModifier.defaultWeight())
                val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
                Column(horizontalAlignment = Alignment.End) {
                    Text("$calLeft left", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text))
                    if (d.yesterdayCalories > 0) {
                        val diff = d.totalCalories - d.yesterdayCalories
                        val sign = if (diff >= 0) "+" else ""
                        Text("$sign$diff vs yesterday", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Macro bars — 2 columns on 5×3, single column on 4×3
        if (w >= 340.dp) {
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    LabeledBar("🔥 Cal", "${d.totalCalories}/${d.calorieGoal}", pct(d.totalCalories, d.calorieGoal), c.cal, c.track, c, sc)
                    if (d.fatGoal > 0) {
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        LabeledBar("🥑 Fat", "${d.totalFat}g/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc)
                    }
                }
                Spacer(GlanceModifier.width(sc.spaceMd))
                Column(GlanceModifier.defaultWeight()) {
                    LabeledBar("💪 Pro", "${d.totalProtein}g/${d.proteinGoal}g", pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc)
                    if (d.carbGoal > 0) {
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        LabeledBar("🌾 Carb", "${d.totalCarbs}g/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc)
                    }
                }
            }
        } else {
            LabeledBar("🔥 Calories", "${d.totalCalories}/${d.calorieGoal}", pct(d.totalCalories, d.calorieGoal), c.cal, c.track, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            LabeledBar("💪 Protein", "${d.totalProtein}g/${d.proteinGoal}g", pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        val cols = cardCols(w)
        // On 4×3 show 1 row of cards; on 5×3 show 2 rows only if meals won't be shown
        val showMeals = d.recentMeals.isNotEmpty()
        val cardRows = if (showMeals) 1 else 2
        CardGrid(buildCards(d).take(cols * cardRows), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        if (showMeals) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            Box(
                GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Text("🍽 Recent meals", style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = c.sub))
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    d.recentMeals.take(2).forEach { meal ->
                        Text("· $meal", style = TextStyle(fontSize = sc.fxs, color = c.text), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun buildCards(d: DashboardWidgetData): List<CardInfo> {
    val l = mutableListOf<CardInfo>()
    val c = WidgetClr()
    val calPct = if (d.calorieGoal > 0) (d.totalCalories * 100 / d.calorieGoal) else 0
    l += CardInfo("🔥", "${d.totalCalories}", "${calPct}% · ${d.calorieGoal} kcal", c.cal)
    val proPct = if (d.proteinGoal > 0) (d.totalProtein * 100 / d.proteinGoal) else 0
    l += CardInfo("💪", "${d.totalProtein}g", "${proPct}% · ${d.proteinGoal}g goal", c.pro)
    l += CardInfo("🍽", "${d.mealCount}", if (d.mealCount == 1) "Meal today" else "Meals today", c.text)
    if (d.hasHealthData && d.steps > 0) {
        val stepPct = (d.steps * 100 / d.stepsGoal).toInt()
        l += CardInfo("👟", "%,d".format(d.steps), "$stepPct% of goal", c.steps)
    }
    if (d.hasHealthData && d.avgHeartRate > 0)
        l += CardInfo("❤️", "${d.avgHeartRate}", "Avg BPM", c.heart)
    if (d.hasHealthData && d.sleepMinutes > 0) {
        val h = d.sleepMinutes / 60; val m = d.sleepMinutes % 60
        l += CardInfo("😴", "${h}h ${m}m", "Last night", c.sleep)
    }
    if (d.hasHealthData && d.activeCaloriesBurned > 0)
        l += CardInfo("⚡", "${d.activeCaloriesBurned.toInt()}", "Active kcal", c.cal)
    if (d.hasWeatherData && d.weatherTemp != null) {
        val sub = buildString {
            append(d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: "")
            if (d.weatherHigh != null && d.weatherLow != null) {
                if (isNotEmpty()) append(" · ")
                append("↑${d.weatherHigh}° ↓${d.weatherLow}°")
            }
        }
        l += CardInfo(d.weatherIcon ?: "🌡️", "${d.weatherTemp}°", sub.ifBlank { "Now" }, c.weather)
    }
    if (d.hasCalendarData && d.nextEventTitle != null)
        l += CardInfo("📅", d.nextEventTitle, "${d.nextEventRelativeDay ?: ""} · ${d.nextEventTime ?: ""}", c.event)
    return l
}
