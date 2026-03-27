package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme


import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
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
import com.macrotracker.R

/**
 * Macros / Nutrition widget.
 */
class MacrosWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { MacrosRoot(data) } }
    }
}

@Composable
private fun MacrosRoot(data: DashboardWidgetData) {
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        MacrosFull(data, c, sc)
    }
}

@Composable
private fun MacrosFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val w = 368.dp // Fixed width for fixed scale sizing fallback
    val col2W    = ((w - sc.pad * 2 - sc.spaceMd) / 2).coerceAtLeast(8.dp)
    val heroBarW = (col2W - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal)
        if (!d.aiInsightNutrition.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightNutrition, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Hero row: Cal + Protein
        Row(GlanceModifier.fillMaxWidth()) {
            MacroHeroCardFull("${d.totalCalories}", "of ${d.calorieGoal} kcal",
                pct(d.totalCalories, d.calorieGoal), c.cal, c, sc, GlanceModifier.defaultWeight(), heroBarW)
            Spacer(GlanceModifier.width(sc.spaceMd))
            MacroHeroCardFull("${d.totalProtein}g", "of ${d.proteinGoal}g",
                pct(d.totalProtein, d.proteinGoal), c.pro, c, sc, GlanceModifier.defaultWeight(), heroBarW)
        }
        // Secondary bars
        if (d.fatGoal > 0 || d.carbGoal > 0) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (w >= 340.dp && d.fatGoal > 0 && d.carbGoal > 0) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        EnhancedLabeledBar(R.drawable.ic_fat, "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc, col2W)
                    }
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Column(GlanceModifier.defaultWeight()) {
                        EnhancedLabeledBar(R.drawable.ic_carbs, "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc, col2W)
                    }
                }
            } else {
                if (d.fatGoal > 0)
                    EnhancedLabeledBar(R.drawable.ic_fat, "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc)
                if (d.fatGoal > 0 && d.carbGoal > 0) Spacer(GlanceModifier.height(sc.spaceSm))
                if (d.carbGoal > 0)
                    EnhancedLabeledBar(R.drawable.ic_carbs, "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc)
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Bottom: recent meals or budget chips
        if (d.recentMeals.isNotEmpty()) {
            Box(
                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("RECENT MEALS", c.cal, c, sc)
                        Spacer(GlanceModifier.defaultWeight())
                        MacroPctPill(pct(d.mealCount, 1), c.text, c, sc, "${d.mealCount} meals")
                    }
                    d.recentMeals.take(3).forEach { meal ->
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.width(2.dp).height(sc.fsm.value.dp).cornerRadius(1.dp).background(c.cal)) {}
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Text(meal, style = TextStyle(fontSize = sc.fsm, color = c.text), maxLines = 1)
                        }
                    }
                }
            }
        } else {
            val cols = 4 // Fixed 4 columns for single large widget
            val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
            val proLeft = (d.proteinGoal - d.totalProtein).coerceAtLeast(0)
            val chips = mutableListOf(
                CardInfo(R.drawable.ic_meal, "${d.mealCount}", "Meals today", c.text),
                CardInfo(R.drawable.ic_flame, "$calLeft", "kcal left", c.cal),
                CardInfo(R.drawable.ic_protein, "${proLeft}g", "Protein left", c.pro),
            )
            CardGrid(chips.take(cols), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        }
    }
}

// ——— Shared components ——————————————————————————————————————————————————————————————————————————————————————

@Composable
private fun MacroHeroCardFull(
    value: String, sub: String, progress: Float,
    accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier, contentWidth: Dp,
) {
    Box(modifier.cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(accent)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Column(GlanceModifier.defaultWeight()) {
                    Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = accent), maxLines = 1)
                    Text(sub, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            WidgetProgressBar(progress, accent, c.track, sc, contentWidth)
            Spacer(GlanceModifier.height(sc.spaceXs))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val pctInt = (progress * 100).toInt()
                Text("$pctInt%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = accent), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun MacroPctPill(progress: Float, accent: androidx.glance.unit.ColorProvider, c: WidgetClr, sc: WScale, text: String? = null) {
    val pctInt = (progress * 100).toInt()
    Box(
        GlanceModifier.cornerRadius(999.dp).background(c.pill)
            .padding(horizontal = sc.spaceSm, vertical = 1.dp),
    ) {
        Text(
            text ?: "$pctInt%",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = accent),
        )
    }
}
