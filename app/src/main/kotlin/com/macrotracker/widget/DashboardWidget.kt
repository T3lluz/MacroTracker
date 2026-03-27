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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity

/**
 * Dashboard widget — overview of all domains.
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
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        DashFull(data, c, sc)
    }
}

@Composable
private fun DashFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val w = 368.dp // Fixed width for fixed scale sizing fallback
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
                value = if (d.hasWeatherData && d.weatherTemp != null) "${d.weatherTemp}" else "—",
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

// ——— Helpers ——————————————————————————————————————————————————————————————————————————————————————————————————————

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
