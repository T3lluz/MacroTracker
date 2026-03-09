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
 * Health & Activity widget — 2×2 to 5×3.
 * TINY (2×2): step count hero
 * COMPACT (*×2): header + steps bar + heart-rate, width-adaptive
 * MEDIUM (2-3 cols, 3 rows): header + steps bar + AI + 2-col stat cards
 * FULL (4-5 cols, 3 rows): header + steps hero card + bar + AI + multi-col cards
 */
class HealthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.SMALL_WIDGET)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent {
            GlanceTheme {
                HealthWidgetRoot(data)
            }
        }
    }
}

@Composable
private fun HealthWidgetRoot(data: DashboardWidgetData) {
    val sz  = LocalSize.current
    val ws  = classify(sz.width, sz.height)
    val c   = WidgetClr()
    val sc  = WScale.from()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.corner)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> HealthTiny(data, c, sc)
            WSize.COMPACT -> HealthCompact(data, c, sc)
            WSize.MEDIUM  -> HealthMedium(data, c, sc)
            WSize.FULL    -> HealthFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY: 2×2 — step count hero + % ─────────────────────────────
@Composable
private fun HealthTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    if (!d.hasHealthData) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NoDataPlaceholder("👟", "No health\ndata", c, sc)
        }
    } else {
        val stepPct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("👟", style = TextStyle(fontSize = sc.iconHero))
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(
                "%,d".format(d.steps),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.steps),
            )
            Text("/ %,d steps".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub))
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text("$stepPct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.steps))
        }
    }
}

// ── COMPACT: *×2 — header + steps bar + extras (width-adaptive) ──
@Composable
private fun HealthCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Activity", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Connect Health app", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Steps hero card
            Box(
                GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("👟", style = TextStyle(fontSize = sc.iconSm))
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Text("%,d".format(d.steps), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.steps))
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text("/ %,d".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        Spacer(GlanceModifier.defaultWeight())
                        val pct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
                        Text("$pct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.steps))
                    }
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetProgressBar(pctL(d.steps, d.stepsGoal), c.steps, c.track, sc)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Vitals row — fills remaining height
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (d.avgHeartRate > 0) {
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card).padding(sc.cardPad)) {
                        Column(GlanceModifier.fillMaxWidth()) {
                            Text("❤️", style = TextStyle(fontSize = sc.iconSm))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("${d.avgHeartRate}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.heart))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("BPM avg", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                }
                if (d.sleepMinutes > 0) {
                    val h = d.sleepMinutes / 60; val m = d.sleepMinutes % 60
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card).padding(sc.cardPad)) {
                        Column(GlanceModifier.fillMaxWidth()) {
                            Text("😴", style = TextStyle(fontSize = sc.iconSm))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("${h}h ${m}m", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.sleep))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("Sleep", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                }
                if (d.activeCaloriesBurned > 0) {
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card).padding(sc.cardPad)) {
                        Column(GlanceModifier.fillMaxWidth()) {
                            Text("⚡", style = TextStyle(fontSize = sc.iconSm))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("${d.activeCaloriesBurned.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.cal))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("Active kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                } else if (d.avgHeartRate == 0L && d.sleepMinutes == 0L) {
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card).padding(sc.cardPad), contentAlignment = Alignment.Center) {
                        Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📱", style = TextStyle(fontSize = sc.iconSm))
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text("More stats", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                            Text("Log workouts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        }
                    }
                }
            }
        }
    }
}

// ── MEDIUM: 2×3 / 3×3 — steps bar + AI + 2-col stat cards ───────
@Composable
private fun HealthMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Health & Activity", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Connect Health app", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (!d.aiInsightHealth.isNullOrBlank()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                AiInsightBanner(d.aiInsightHealth, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Compact steps bar row
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("👟", style = TextStyle(fontSize = sc.iconSm))
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text("%,d".format(d.steps), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps))
                Spacer(GlanceModifier.width(sc.spaceXs))
                Text("/ %,d".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                Spacer(GlanceModifier.defaultWeight())
                val pct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
                Text("$pct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.steps))
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            WidgetProgressBar(pctL(d.steps, d.stepsGoal), c.steps, c.track, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            val cards = buildHealthCards(d, c).take(4)
            CardGrid(cards, 2, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        }
    }
}

// ── FULL: 4×3 / 5×3 — steps bar + AI + multi-col cards ──────────
@Composable
private fun HealthFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Health & Activity", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.hasHealthData) {
            Spacer(GlanceModifier.defaultWeight())
            NoDataPlaceholder("🏃", "Connect Health app", c, sc)
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (!d.aiInsightHealth.isNullOrBlank()) {
                Spacer(GlanceModifier.height(sc.spaceSm))
                AiInsightBanner(d.aiInsightHealth, c, sc)
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Steps card (compact, not hero-size)
            Box(
                modifier = GlanceModifier.fillMaxWidth()
                    .cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("👟", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text("%,d".format(d.steps), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.steps))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text("/ %,d".format(d.stepsGoal), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    Spacer(GlanceModifier.defaultWeight())
                    val stepPct = (pctL(d.steps, d.stepsGoal) * 100).toInt()
                    Text("$stepPct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.steps))
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            WidgetProgressBar(pctL(d.steps, d.stepsGoal), c.steps, c.track, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            // 1 row of cards (all cols)
            val cols = cardCols(w)
            val cards = buildHealthCards(d, c).take(cols)
            CardGrid(cards, cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        }
    }
}


private fun buildHealthCards(d: DashboardWidgetData, c: WidgetClr): List<CardInfo> {
    val list = mutableListOf<CardInfo>()
    if (d.avgHeartRate > 0) list += CardInfo("❤️", "${d.avgHeartRate}", "BPM avg", c.heart)
    if (d.sleepMinutes > 0) {
        val h = d.sleepMinutes / 60; val m = d.sleepMinutes % 60
        list += CardInfo("😴", "${h}h ${m}m", "Sleep", c.sleep)
    }
    if (d.activeCaloriesBurned > 0)
        list += CardInfo("⚡", "${d.activeCaloriesBurned.toInt()}", "Active kcal", c.cal)
    if (d.activeCaloriesBurned > 0) {
        val net = d.totalCalories - d.activeCaloriesBurned.toInt()
        list += CardInfo("📊", "$net", "Net kcal", c.text)
    }
    list += CardInfo("👟", "%,d".format(d.steps), "Steps", c.steps)
    return list
}
