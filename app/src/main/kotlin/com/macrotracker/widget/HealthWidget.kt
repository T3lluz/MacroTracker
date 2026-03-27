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
 * Health & Activity widget — 2×2 to 5×3.
 * TINY    (2×2)   — step hero + heart rate
 * COMPACT (*×2)   — header + steps card + vital row
 * MEDIUM  (2-3×3) — header + steps bar + vitals grid
 * FULL    (4-5×3) — header + AI + steps hero card + vitals + metrics
 */
class HealthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { HealthRoot(data) } }
    }
}

@Composable
private fun HealthRoot(data: DashboardWidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> HealthTiny(data, c, sc)
            WSize.COMPACT -> HealthCompact(data, c, sc)
            WSize.MEDIUM  -> HealthMedium(data, c, sc)
            WSize.FULL    -> HealthFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY ─────────────────────────────────────────────────────────
@Composable
private fun HealthTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    if (!d.hasHealthData) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NoDataPlaceholder("👟", "No health\ndata", c, sc)
        }
        return
    }
    val stepPct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("👟", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        Text(
            fmtStepsHealth(d.steps),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.steps),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("steps", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            Spacer(GlanceModifier.width(sc.spaceXs))
            Box(
                GlanceModifier.cornerRadius(999.dp).background(c.pill)
                    .padding(horizontal = sc.spaceSm, vertical = 1.dp),
            ) {
                Text("$stepPct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.steps))
            }
        }
        if (d.avgHeartRate > 0) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            Text("❤️ ${d.avgHeartRate} bpm", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.heart))
        }
    }
}

// ── COMPACT ──────────────────────────────────────────────────────
@Composable
private fun HealthCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val cardBarW = (sz.width - sc.pad * 2 - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Activity", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.steps)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Health data\nunavailable", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Steps card
            Box(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("👟", style = TextStyle(fontSize = sc.iconSm))
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(
                                "%,d".format(d.steps),
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.steps),
                                maxLines = 1,
                            )
                            Text("of %,d".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                        }
                        val pctVal = (pctL(d.steps, d.stepsGoal) * 100).toInt()
                        Box(
                            GlanceModifier.cornerRadius(999.dp).background(c.pill)
                                .padding(horizontal = sc.spaceSm, vertical = 2.dp),
                        ) {
                            Text("$pctVal%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.steps))
                        }
                    }
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    WidgetProgressBar(pctL(d.steps, d.stepsGoal), c.steps, c.track, sc, cardBarW)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Vital row — always 3 cards
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                VitalCard("❤️", if (d.avgHeartRate > 0) "${d.avgHeartRate}" else "—", "BPM", c.heart, c, sc, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(sc.spaceSm))
                VitalCard("😴", fmtSleep(d.sleepMinutes), "Sleep", c.sleep, c, sc, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(sc.spaceSm))
                VitalCard("⚡", if (d.activeCaloriesBurned > 0) "${d.activeCaloriesBurned.toInt()}" else "—", "Active", c.cal, c, sc, GlanceModifier.defaultWeight())
            }
        }
    }
}

// ── MEDIUM ───────────────────────────────────────────────────────
@Composable
private fun HealthMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val isNarrow = sz.width < 200.dp
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Health", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.steps)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Health data\nunavailable", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (!d.aiInsightHealth.isNullOrBlank() && !isNarrow) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                AiInsightBanner(d.aiInsightHealth, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            EnhancedLabeledBar(
                "👟", "Steps", "%,d / %,d".format(d.steps, d.stepsGoal),
                pctL(d.steps, d.stepsGoal), c.steps, c.track, c, sc,
            )
            Spacer(GlanceModifier.height(sc.spaceSm))
            SectionLabel("VITALS", c.steps, c, sc)
            Spacer(GlanceModifier.height(sc.spaceXs))
            CardGrid(
                buildVitalCards(d, c).take(4), 2, c, sc,
                GlanceModifier.fillMaxWidth().defaultWeight(),
                fillRows = true,
            )
        }
    }
}

// ── FULL ─────────────────────────────────────────────────────────
@Composable
private fun HealthFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
    val cardBarW = (w - sc.pad * 2 - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Health", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt, accent = c.steps)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Health data\nunavailable", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (!d.aiInsightHealth.isNullOrBlank()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                AiInsightBanner(d.aiInsightHealth, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Steps hero card
            Box(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(c.steps)) {}
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Column(GlanceModifier.defaultWeight()) {
                            Text("%,d".format(d.steps), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.steps), maxLines = 1)
                            Text("of %,d steps".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                        }
                        val stepPct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
                        Box(GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill).padding(horizontal = sc.spaceSm, vertical = 2.dp)) {
                            Text("$stepPct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps))
                        }
                    }
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetProgressBar(pctL(d.steps, d.stepsGoal), c.steps, c.track, sc, cardBarW)
                    // Inline vitals strip
                    val vitals = buildInlineVitals(d, c)
                    if (vitals.isNotEmpty()) {
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        WidgetDivider(c)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        Row(GlanceModifier.fillMaxWidth()) {
                            vitals.forEachIndexed { idx, (icon, value, label, accent) ->
                                if (idx > 0) Box(GlanceModifier.width(1.dp).height(28.dp).background(c.divider)) {}
                                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(icon, style = TextStyle(fontSize = sc.iconSm))
                                    Spacer(GlanceModifier.height(sc.spaceXs))
                                    Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = accent), maxLines = 1)
                                    Text(label, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            // Extra metric cards
            val extraCards = buildVitalCards(d, c)
            if (extraCards.isNotEmpty()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                SectionLabel("METRICS", c.steps, c, sc)
                Spacer(GlanceModifier.height(sc.spaceXs))
                CardGrid(extraCards.take(cardCols(w) * 2), cardCols(w), c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────

@Composable
private fun VitalCard(
    icon: String, value: String, label: String,
    accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier,
) {
    Box(
        modifier.fillMaxHeight().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Text(icon, style = TextStyle(fontSize = sc.iconSm), maxLines = 1)
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(
                value,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (value.length > 5) sc.fmd else sc.fxl,
                    color = accent,
                ),
                maxLines = 1,
            )
            Text(label, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
        }
    }
}

private fun fmtStepsHealth(steps: Long): String =
    if (steps >= 10_000) "${steps / 1000}k" else "%,d".format(steps)

private fun fmtSleep(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60; val m = minutes % 60
    return "${h}h${m}m"
}

private data class InlineVital(val icon: String, val value: String, val label: String, val accent: androidx.glance.unit.ColorProvider)

private fun buildInlineVitals(d: DashboardWidgetData, c: WidgetClr): List<InlineVital> = buildList {
    if (d.avgHeartRate > 0) add(InlineVital("❤️", "${d.avgHeartRate}", "BPM", c.heart))
    if (d.sleepMinutes > 0) add(InlineVital("😴", fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(InlineVital("⚡", "${d.activeCaloriesBurned.toInt()}", "Active", c.cal))
}

private fun buildVitalCards(d: DashboardWidgetData, c: WidgetClr): List<CardInfo> = buildList {
    if (d.avgHeartRate > 0) add(CardInfo("❤️", "${d.avgHeartRate}", "BPM avg", c.heart))
    if (d.sleepMinutes > 0) add(CardInfo("😴", fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(CardInfo("⚡", "${d.activeCaloriesBurned.toInt()}", "Active kcal", c.cal))
    if (d.activeCaloriesBurned > 0 && d.totalCalories > 0) {
        val net = d.totalCalories - d.activeCaloriesBurned.toInt()
        add(CardInfo("📊", "$net", "Net kcal", if (net < d.calorieGoal) c.pro else c.cal))
    }
    add(CardInfo("👟", "%,d".format(d.steps), "Steps", c.steps))
}
