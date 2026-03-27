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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
 * Dashboard widget — overview of all domains.
 * TINY    (2×2)   — calorie hero
 * COMPACT (*×2)   — greeting + calorie bar + domain chips
 * MEDIUM  (2-3×3) — greeting + cal/pro bars + domain card grid
 * FULL    (4-5×3) — greeting + AI + macro hero + domain snapshot
 */
class DashboardWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { DashRoot(data) } }
    }
}

@Composable
private fun DashRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> DashTiny(data, c, sc)
            WSize.COMPACT -> DashCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> DashMedium(data, c, sc, sz.width)
            WSize.FULL    -> DashFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY ─────────────────────────────────────────────────────────
@Composable
private fun DashTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        Text(
            "${d.totalCalories}",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            Spacer(GlanceModifier.width(sc.spaceXs))
            PctPill(pct(d.totalCalories, d.calorieGoal), c.cal, c, sc)
        }
        if (d.hasHealthData && d.steps > 0) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            Text(
                "👟 ${fmtSteps(d.steps)}",
                style = TextStyle(fontSize = sc.fxs, color = c.steps),
            )
        }
    }
}

// ── COMPACT ──────────────────────────────────────────────────────
@Composable
private fun DashCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.primary)
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Calorie summary card
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", style = TextStyle(fontSize = sc.iconSm))
                Spacer(GlanceModifier.width(sc.spaceXs))
                Text(
                    "${d.totalCalories}",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.cal),
                )
                Spacer(GlanceModifier.width(sc.spaceXs))
                Text("/ ${d.calorieGoal}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                Spacer(GlanceModifier.defaultWeight())
                PctPill(pct(d.totalCalories, d.calorieGoal), c.cal, c, sc)
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Domain chips fill remaining height
        CardGrid(
            buildDomainCards(d, c).take(cardCols(w)),
            cardCols(w), c, sc,
            GlanceModifier.fillMaxWidth().defaultWeight(),
            fillRows = true,
        )
    }
}

// ── MEDIUM ───────────────────────────────────────────────────────
@Composable
private fun DashMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.primary)
        if (!d.aiInsight.isNullOrBlank() && w >= 200.dp) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsight, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        SectionLabel("TODAY", c.primary, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        // Calorie + Protein bars
        EnhancedLabeledBar(
            "🔥", "Cal", "${d.totalCalories}/${d.calorieGoal}",
            pct(d.totalCalories, d.calorieGoal), c.cal, c.track, c, sc,
        )
        Spacer(GlanceModifier.height(sc.spaceSm))
        EnhancedLabeledBar(
            "💪", "Pro", "${d.totalProtein}/${d.proteinGoal}g",
            pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc,
        )
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Domain cards fill remaining space
        val cols = cardCols(w)
        CardGrid(
            buildDomainCards(d, c).take(cols * 2),
            cols, c, sc,
            GlanceModifier.fillMaxWidth().defaultWeight(),
            fillRows = true,
        )
    }
}

// ── FULL ─────────────────────────────────────────────────────────
@Composable
private fun DashFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    val halfBarW = ((w - sc.pad * 2 - sc.spaceMd - sc.padSm * 4) / 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            title = greeting(), c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.primary,
        )
        if (!d.aiInsight.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsight, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Macro hero: Cal + Protein side by side
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.fillMaxWidth()) {
                    // Calories half
                    Column(GlanceModifier.defaultWeight()) {
                        HeroValue("${d.totalCalories}", "of ${d.calorieGoal} kcal", c.cal, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc, halfBarW)
                    }
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    // Protein half
                    Column(GlanceModifier.defaultWeight()) {
                        HeroValue("${d.totalProtein}g", "of ${d.proteinGoal}g", c.pro, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        WidgetProgressBar(pct(d.totalProtein, d.proteinGoal), c.pro, c.track, sc, halfBarW)
                    }
                }
                Spacer(GlanceModifier.height(sc.spaceSm))
                WidgetDivider(c)
                Spacer(GlanceModifier.height(sc.spaceSm))
                // Summary pills
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    InfoPill("${d.mealCount} meal${if (d.mealCount != 1) "s" else ""}", c, sc)
                    if (d.yesterdayCalories > 0) {
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        val diff = d.totalCalories - d.yesterdayCalories
                        val sign = if (diff >= 0) "+" else ""
                        InfoPill("$sign$diff yday", c, sc)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
                    Text("$calLeft kcal left", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Domain snapshot strip
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            MetricChip(
                value = if (d.hasWeatherData && d.weatherTemp != null) "${d.weatherTemp}°" else "—",
                label = d.weatherDescription?.replaceFirstChar { it.uppercase() }?.take(10) ?: "Weather",
                accent = c.weather, c = c, sc = sc,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(sc.spaceSm))
            MetricChip(
                value = if (d.hasHealthData && d.steps > 0) fmtSteps(d.steps) else "—",
                label = if (d.hasHealthData && d.steps > 0) "${(d.steps * 100 / d.stepsGoal).toInt()}% goal" else "Steps",
                accent = c.steps, c = c, sc = sc,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(sc.spaceSm))
            MetricChip(
                value = when {
                    d.hasCalendarData && d.nextEventTitle != null -> d.nextEventTitle.take(8)
                    d.hasCalendarData -> "${d.eventsToday}"
                    else -> "—"
                },
                label = when {
                    d.hasCalendarData && d.nextEventTime != null -> d.nextEventTime
                    d.hasCalendarData -> "event${if (d.eventsToday != 1) "s" else ""}"
                    else -> "Calendar"
                },
                accent = c.event, c = c, sc = sc,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────

@Composable
private fun HeroValue(value: String, sub: String, accent: androidx.glance.unit.ColorProvider, c: WidgetClr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(accent)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Column(GlanceModifier.defaultWeight()) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = accent), maxLines = 1)
            Text(sub, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

@Composable
private fun PctPill(progress: Float, accent: androidx.glance.unit.ColorProvider, c: WidgetClr, sc: WScale) {
    val pctInt = (progress * 100).toInt()
    Box(
        GlanceModifier.cornerRadius(999.dp).background(c.pill)
            .padding(horizontal = sc.spaceSm, vertical = 1.dp),
    ) {
        Text("$pctInt%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = accent))
    }
}

@Composable
private fun InfoPill(text: String, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
            .padding(horizontal = sc.spaceSm, vertical = 1.dp),
    ) {
        Text(text, style = TextStyle(fontSize = sc.fxs, color = c.text))
    }
}

private fun fmtSteps(steps: Long): String =
    if (steps >= 10_000) "${steps / 1000}k" else "%,d".format(steps)

private fun buildDomainCards(d: DashboardWidgetData, c: WidgetClr): List<CardInfo> {
    val list = mutableListOf<CardInfo>()
    val calPct = if (d.calorieGoal > 0) (d.totalCalories * 100 / d.calorieGoal) else 0
    list += CardInfo("🔥", "${d.totalCalories}", "$calPct% of ${d.calorieGoal} kcal", c.cal)
    list += CardInfo("💪", "${d.totalProtein}g", "${d.proteinGoal}g goal", c.pro)
    if (d.hasHealthData && d.steps > 0)
        list += CardInfo("👟", fmtSteps(d.steps), "${(d.steps * 100 / d.stepsGoal).toInt()}% of goal", c.steps)
    if (d.hasHealthData && d.avgHeartRate > 0)
        list += CardInfo("❤️", "${d.avgHeartRate}", "Avg BPM", c.heart)
    if (d.hasWeatherData && d.weatherTemp != null)
        list += CardInfo(d.weatherIcon ?: "🌡️", "${d.weatherTemp}°", d.weatherDescription?.replaceFirstChar { it.uppercase() } ?: "Now", c.weather)
    if (d.hasCalendarData && d.nextEventTitle != null)
        list += CardInfo("📅", d.nextEventTitle.take(12), d.nextEventTime ?: "Soon", c.event)
    list += CardInfo("🍽", "${d.mealCount}", "Meals today", c.text)
    return list
}
