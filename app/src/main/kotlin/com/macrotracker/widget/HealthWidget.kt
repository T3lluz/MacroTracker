package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme

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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.macrotracker.MainActivity
import com.macrotracker.R

/**
 * Health / Vitals widget.
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
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        HealthFull(data, c, sc)
    }
}

@Composable
private fun HealthFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val w = 368.dp // Fixed width for fixed scale sizing fallback
    val cardBarW = (w - sc.pad * 2 - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Health", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt, accent = c.steps)
        if (!d.hasHealthData) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NoDataPlaceholder(R.drawable.ic_chart_down, "Health data\nunavailable", c, sc)
            }
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
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        vitals.take(2).forEachIndexed { i, v ->
                            if (i > 0) Spacer(GlanceModifier.width(sc.spaceMd))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(v.iconRes),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(v.accent)
                                )
                                Spacer(GlanceModifier.width(sc.spaceXs))
                                Text(v.value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = v.accent), maxLines = 1)
                                Spacer(GlanceModifier.width(sc.spaceXs))
                                Text(v.label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetDivider(c)
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    // Extra metric cards
                    val extraCards = buildVitalCards(d, c)
                    if (extraCards.isNotEmpty()) {
                        val cols = 4 // Fixed columns for widget grid
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        SectionLabel("METRICS", c.steps, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        CardGrid(extraCards.take(cols * 2), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
                    } else {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

// ——— Helpers ——————————————————————————————————————————————————————————————————————————————————————————————————————

private fun fmtStepsHealth(steps: Long): String =
    if (steps >= 10_000) "${steps / 1000}k" else "%,d".format(steps)

private fun fmtSleep(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60; val m = minutes % 60
    return "${h}h${m}m"
}

private data class InlineVital(val iconRes: Int, val value: String, val label: String, val accent: androidx.glance.unit.ColorProvider)

private fun buildInlineVitals(d: DashboardWidgetData, c: WidgetClr): List<InlineVital> = buildList {
    if (d.avgHeartRate > 0) add(InlineVital(R.drawable.ic_heart, "${d.avgHeartRate}", "BPM", c.heart))
    if (d.sleepMinutes > 0) add(InlineVital(R.drawable.ic_sleep, fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(InlineVital(R.drawable.ic_energy, "${d.activeCaloriesBurned.toInt()}", "Active", c.cal))
}

private fun buildVitalCards(d: DashboardWidgetData, c: WidgetClr): List<CardInfo> = buildList {
    if (d.avgHeartRate > 0) add(CardInfo(R.drawable.ic_heart, "${d.avgHeartRate}", "BPM avg", c.heart))
    if (d.sleepMinutes > 0) add(CardInfo(R.drawable.ic_sleep, fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(CardInfo(R.drawable.ic_energy, "${d.activeCaloriesBurned.toInt()}", "Active kcal", c.cal))
    if (d.activeCaloriesBurned > 0 && d.totalCalories > 0) {
        val net = d.totalCalories - d.activeCaloriesBurned.toInt()
        add(CardInfo(R.drawable.ic_stats, "$net", "Net kcal", if (net < d.calorieGoal) c.pro else c.cal))
    }
    add(CardInfo(R.drawable.ic_steps, "%,d".format(d.steps), "Steps", c.steps))
}
