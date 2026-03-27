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
 * Macros / Nutrition widget — 2×2 to 5×3.
 * TINY    (2×2)   — calorie hero + mini macro row
 * COMPACT (*×2)   — header + cal/pro hero cards + fat/carb/meals row
 * MEDIUM  (2-3×3) — header + all macro bars + budget cards
 * FULL    (4-5×3) — header + AI + hero cards + bars + recent meals
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
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> MacrosTiny(data, c, sc)
            WSize.COMPACT -> MacrosCompact(data, c, sc)
            WSize.MEDIUM  -> MacrosMedium(data, c, sc)
            WSize.FULL    -> MacrosFull(data, c, sc, sz.width)
        }
    }
}

// ── TINY ─────────────────────────────────────────────────────────
@Composable
private fun MacrosTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", style = TextStyle(fontSize = sc.iconMd))
        Spacer(GlanceModifier.height(sc.spaceXs))
        Text(
            "${d.totalCalories}",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("/ ${d.calorieGoal}", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            Spacer(GlanceModifier.width(sc.spaceXs))
            MacroPctPill(pct(d.totalCalories, d.calorieGoal), c.cal, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Mini macro row
        Row(GlanceModifier.fillMaxWidth()) {
            MiniMacro("💪", "${d.totalProtein}g", c.pro, sc, GlanceModifier.defaultWeight())
            if (d.fatGoal > 0)
                MiniMacro("🥑", "${d.totalFat}g", c.fat, sc, GlanceModifier.defaultWeight())
            if (d.carbGoal > 0)
                MiniMacro("🌾", "${d.totalCarbs}g", c.carb, sc, GlanceModifier.defaultWeight())
        }
    }
}

// ── COMPACT ──────────────────────────────────────────────────────
@Composable
private fun MacrosCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val heroBarW = ((sz.width - sc.pad * 2 - sc.spaceSm) / 2 - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal)
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Cal + Protein hero cards side by side
        Row(GlanceModifier.fillMaxWidth()) {
            MacroHeroCard("🔥", "${d.totalCalories}", "/ ${d.calorieGoal} kcal",
                pct(d.totalCalories, d.calorieGoal), c.cal, c, sc,
                GlanceModifier.defaultWeight(), heroBarW)
            Spacer(GlanceModifier.width(sc.spaceSm))
            MacroHeroCard("💪", "${d.totalProtein}g", "/ ${d.proteinGoal}g",
                pct(d.totalProtein, d.proteinGoal), c.pro, c, sc,
                GlanceModifier.defaultWeight(), heroBarW)
        }
        Spacer(GlanceModifier.height(sc.spaceXs))
        // Bottom tiles: fat/carb/meals
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (d.fatGoal > 0) {
                MacroTile("🥑", "Fat", "${d.totalFat}g", c.fat, c, sc, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(sc.spaceSm))
            }
            if (d.carbGoal > 0) {
                MacroTile("🌾", "Carbs", "${d.totalCarbs}g", c.carb, c, sc, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(sc.spaceSm))
            }
            MacroTile("🍽", "Meals", "${d.mealCount}", c.text, c, sc, GlanceModifier.defaultWeight())
        }
    }
}

// ── MEDIUM ───────────────────────────────────────────────────────
@Composable
private fun MacrosMedium(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val sz = LocalSize.current
    val isNarrow = sz.width < 200.dp
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal)
        if (!d.aiInsightNutrition.isNullOrBlank() && !isNarrow) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightNutrition, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // All macro bars in a card
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.width(3.dp).height((sc.fsm.value + 2f).dp).cornerRadius(2.dp).background(c.cal)) {}
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(
                        if (isNarrow) "MACROS" else "MACRO GOALS",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    MacroPctPill(pct(d.totalCalories, d.calorieGoal), c.cal, c, sc)
                }
                Spacer(GlanceModifier.height(sc.spaceSm))
                EnhancedLabeledBar("🔥", "Cal", "${d.totalCalories}/${d.calorieGoal}", pct(d.totalCalories, d.calorieGoal), c.cal, c.track, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
                EnhancedLabeledBar("💪", "Pro", "${d.totalProtein}/${d.proteinGoal}g", pct(d.totalProtein, d.proteinGoal), c.pro, c.track, c, sc)
                if (d.fatGoal > 0) {
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    EnhancedLabeledBar("🥑", "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc)
                }
                if (d.carbGoal > 0) {
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    EnhancedLabeledBar("🌾", "Carb", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc)
                }
            }
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Budget row
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            BudgetCard("${(d.calorieGoal - d.totalCalories).coerceAtLeast(0)}", "kcal left", c.cal, c, sc, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(sc.spaceSm))
            BudgetCard("${(d.proteinGoal - d.totalProtein).coerceAtLeast(0)}g", "pro left", c.pro, c, sc, GlanceModifier.defaultWeight())
        }
    }
}

// ── FULL ─────────────────────────────────────────────────────────
@Composable
private fun MacrosFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale, w: Dp) {
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
                        EnhancedLabeledBar("🥑", "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc, col2W)
                    }
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Column(GlanceModifier.defaultWeight()) {
                        EnhancedLabeledBar("🌾", "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc, col2W)
                    }
                }
            } else {
                if (d.fatGoal > 0)
                    EnhancedLabeledBar("🥑", "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc)
                if (d.fatGoal > 0 && d.carbGoal > 0) Spacer(GlanceModifier.height(sc.spaceSm))
                if (d.carbGoal > 0)
                    EnhancedLabeledBar("🌾", "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc)
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
            val cols = cardCols(w)
            val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
            val proLeft = (d.proteinGoal - d.totalProtein).coerceAtLeast(0)
            val chips = mutableListOf(
                CardInfo("🍽", "${d.mealCount}", "Meals today", c.text),
                CardInfo("🔥", "$calLeft", "kcal left", c.cal),
                CardInfo("💪", "${proLeft}g", "Protein left", c.pro),
            )
            CardGrid(chips.take(cols), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        }
    }
}

// ── Shared components ────────────────────────────────────────────

@Composable
private fun MiniMacro(icon: String, value: String, accent: androidx.glance.unit.ColorProvider, sc: WScale, modifier: GlanceModifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = TextStyle(fontSize = sc.fsm))
        Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = accent), maxLines = 1)
    }
}

@Composable
private fun MacroHeroCard(
    icon: String, value: String, goal: String,
    progress: Float, accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier, contentWidth: Dp,
) {
    Box(modifier.cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceXs)) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = TextStyle(fontSize = sc.iconSm))
                Spacer(GlanceModifier.width(sc.spaceXs))
                Column(GlanceModifier.defaultWeight()) {
                    Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = accent), maxLines = 1)
                    Text(goal, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            WidgetProgressBar(progress, accent, c.track, sc, contentWidth)
        }
    }
}

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
private fun MacroTile(
    icon: String, label: String, value: String,
    accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier,
) {
    Box(
        modifier.fillMaxHeight().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = TextStyle(fontSize = sc.iconSm))
            Spacer(GlanceModifier.height(sc.spaceXs))
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = accent), maxLines = 1)
            Text(label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

@Composable
private fun BudgetCard(
    value: String, label: String,
    accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier,
) {
    Box(
        modifier.fillMaxHeight().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = accent), maxLines = 1)
            Text(label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
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
