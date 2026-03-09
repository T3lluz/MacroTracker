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
 * Macros / Nutrition widget — 2×2 to 5×3.
 * TINY (2×2): calorie hero number
 * COMPACT (*×2): header + calorie bar + protein bar side-by-side or stacked
 * MEDIUM (2-3 cols, 3 rows): header + all 4 bars + 2-col cards
 * FULL (4-5 cols, 3 rows): header + AI + calorie card + all bars + multi-col cards
 */
class MacrosWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.SMALL_WIDGET)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent {
            GlanceTheme {
                MacrosWidgetRoot(data)
            }
        }
    }
}

@Composable
private fun MacrosWidgetRoot(data: DashboardWidgetData) {
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
            WSize.TINY    -> MacrosTiny(data, c, sc)
            WSize.COMPACT -> MacrosCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> MacrosMedium(data, c, sc)
            WSize.FULL    -> MacrosFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY: 2×2 — calorie number only ─────────────────────────────
@Composable
private fun MacrosTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal))
        Text("/ ${d.calorieGoal} kcal", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        Spacer(GlanceModifier.height(sc.spaceXs))
        val calPct = if (d.calorieGoal > 0) (d.totalCalories * 100 / d.calorieGoal) else 0
        Text("$calPct%", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.cal))
    }
}

// ── COMPACT: *×2 — header + bars, stacked or side-by-side ────────
@Composable
private fun MacrosCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Macros", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Calorie + protein hero row
        Box(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("🔥 Calories", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${d.totalCalories}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.cal))
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text("/ ${d.calorieGoal}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc)
                }
                Spacer(GlanceModifier.width(sc.spaceMd))
                Column(GlanceModifier.defaultWeight()) {
                    Text("💪 Protein", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${d.totalProtein}g", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.pro))
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text("/ ${d.proteinGoal}g", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetProgressBar(pct(d.totalProtein, d.proteinGoal), c.pro, c.track, sc)
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Fat + carbs row in remaining space
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (d.fatGoal > 0) {
                Box(
                    GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                        .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                ) {
                    Column(GlanceModifier.fillMaxWidth()) {
                        Text("🥑 Fat", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Text("${d.totalFat}g", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.fat))
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        WidgetProgressBar(pct(d.totalFat, d.fatGoal), c.fat, c.track, sc)
                    }
                }
            }
            if (d.fatGoal > 0 && d.carbGoal > 0) Spacer(GlanceModifier.width(sc.spaceSm))
            if (d.carbGoal > 0) {
                Box(
                    GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                        .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                ) {
                    Column(GlanceModifier.fillMaxWidth()) {
                        Text("🌾 Carbs", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Text("${d.totalCarbs}g", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.carb))
                        Spacer(GlanceModifier.height(sc.spaceSm))
                        WidgetProgressBar(pct(d.totalCarbs, d.carbGoal), c.carb, c.track, sc)
                    }
                }
            }
            if (d.fatGoal == 0 && d.carbGoal == 0) {
                Box(
                    GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                        .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍽", style = TextStyle(fontSize = sc.iconMd))
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Text("${d.mealCount}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text))
                        Text("meals", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                }
            }
        }
    }
}

// ── MEDIUM: 2×3 / 3×3 — header + all bars + 2-col cards ─────────
@Composable
private fun MacrosMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsightNutrition.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightNutrition, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        Box(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Macro Goals", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("${d.mealCount} meal${if (d.mealCount != 1) "s" else ""}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                }
                Spacer(GlanceModifier.height(sc.spaceSm))
                LabeledBar("🔥 Calories", "${d.totalCalories} / ${d.calorieGoal}", pct(d.totalCalories, d.calorieGoal), c.cal, c.track, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
                LabeledBar("💪 Protein", "${d.totalProtein}g / ${d.proteinGoal}g", pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc)
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
        val proLeft = (d.proteinGoal - d.totalProtein).coerceAtLeast(0)
        val cards = mutableListOf(
            CardInfo("🔥", "$calLeft", "kcal left", c.cal),
            CardInfo("💪", "${d.totalProtein}g", "protein", c.pro),
            CardInfo("🍽", "${d.mealCount}", "meal${if (d.mealCount != 1) "s" else ""}", c.text),
            CardInfo("📊", "${proLeft}g", "pro left", c.pro),
        )
        if (d.fatGoal > 0) cards += CardInfo("🥑", "${d.totalFat}g", "fat", c.fat)
        if (d.carbGoal > 0) cards += CardInfo("🌾", "${d.totalCarbs}g", "carbs", c.carb)
        CardGrid(cards.take(4), 2, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
    }
}

// ── FULL: 4×3 / 5×3 — calorie card + all bars + multi-col cards ─
@Composable
private fun MacrosFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: androidx.compose.ui.unit.Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, showGreeting = true, lastUpdatedAt = d.lastUpdatedAt)
        if (!d.aiInsightNutrition.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightNutrition, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Calorie highlight card
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
                        Text("$sign$diff vs yday", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // 2-col bars on 5×3; cal+protein only on 4×3
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
        val calLeft2 = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
        val proLeft  = (d.proteinGoal - d.totalProtein).coerceAtLeast(0)
        val carbLeft = (d.carbGoal - d.totalCarbs).coerceAtLeast(0)
        val cards = mutableListOf(
            CardInfo("💪", "${d.totalProtein}g", "/ ${d.proteinGoal}g pro", c.pro),
            CardInfo("🔥", "$calLeft2", "kcal left", c.cal),
            CardInfo("🍽", "${d.mealCount}", "meals", c.text),
            CardInfo("📊", "${proLeft}g", "pro left", c.pro),
        )
        if (d.fatGoal > 0) cards += CardInfo("🥑", "${d.totalFat}g", "fat", c.fat)
        if (d.carbGoal > 0) cards += CardInfo("🌾", "$carbLeft", "carbs left", c.carb)
        // 1 row of cards only — bars already fill the space
        CardGrid(cards.take(cols), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
    }
}
