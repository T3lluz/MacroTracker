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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
 * F1 Standings Widget — 3×2 to 5×3.
 *
 * All sizes ≥ COMPACT are fully scrollable via LazyColumn.
 * The combined standings list (Drivers → Constructors) is rendered as a
 * single sealed-class item list so section headers and rows share one scroll.
 *
 * TINY    (2×2)   — P1 trophy card only
 * COMPACT (*×2)   — header + all standings scrollable (no hero card)
 * MEDIUM  (2-3×3) — header + P1 leader card + scrollable rest
 * FULL    (4-5×3) — header + P1 leader card (bigger) + scrollable rest
 */
class F1StandingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_TALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1StandingsRoot(data) } }
    }
}

// ── Root ──────────────────────────────────────────────────────────
@Composable
private fun F1StandingsRoot(data: F1WidgetData) {
    val sz = LocalSize.current
    val ws = classify(sz.width, sz.height)
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (ws) {
            WSize.TINY    -> StandingsTiny(data, c, sc)
            WSize.COMPACT -> StandingsCompact(data, c, sc, sz.width)
            WSize.MEDIUM  -> StandingsMedium(data, c, sc)
            WSize.FULL    -> StandingsFull(data, c, sc)
        }
    }
}

// ── Shared header ─────────────────────────────────────────────────
@Composable
private fun StandingsHeader(title: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.flg.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.flg, color = c.text), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Box(
            GlanceModifier.width(sc.btnSize).height(sc.btnSize).cornerRadius(sc.btnCorner)
                .background(c.card).clickable(actionRunCallback<RefreshF1WidgetAction>()).padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) { Text("↻", style = TextStyle(fontSize = sc.fmd, fontWeight = FontWeight.Bold, color = c.sub)) }
    }
}

// ── TINY: 2×2 — P1 trophy ────────────────────────────────────────
@Composable
private fun StandingsTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val p1 = d.driverStandings.firstOrNull()
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏆", style = TextStyle(fontSize = sc.iconHero))
        Spacer(GlanceModifier.height(sc.spaceXs))
        if (p1 != null) {
            Text(p1.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = teamColorProvider(p1.teamColor)))
            Text("${p1.points.toInt()} pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            if (p1.wins > 0)
                Text("${p1.wins} wins", style = TextStyle(fontSize = sc.fxs, color = c.gold))
        } else {
            Text("No data", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        }
    }
}

// ── COMPACT: *×2 — header + full scrollable standings ─────────────
@Composable
private fun StandingsCompact(d: F1WidgetData, c: F1Clr, sc: WScale, w: Dp) {
    Column(GlanceModifier.fillMaxSize()) {
        StandingsHeader("Standings", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.driverStandings.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card), contentAlignment = Alignment.Center) {
                Text("No standings data", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(buildStandingsList(d, includeP1 = true)) { item ->
                    StandingsItemRow(item, c, sc, showTeam = w >= 260.dp)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── MEDIUM: 2-3×3 — leader card pinned + all scrollable ───────────
@Composable
private fun StandingsMedium(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        StandingsHeader("Championship", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.driverStandings.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", style = TextStyle(fontSize = sc.iconHero))
                Text("No standings available", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val p1 = d.driverStandings.first()
            LeaderCard(p1, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(buildStandingsList(d, includeP1 = false)) { item ->
                    StandingsItemRow(item, c, sc, showTeam = true)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── FULL: 4-5×3 — leader card + all scrollable ────────────────────
@Composable
private fun StandingsFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        StandingsHeader("F1 Championship  ·  2026", c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.driverStandings.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", style = TextStyle(fontSize = sc.iconHero))
                Text("No standings available", style = TextStyle(fontSize = sc.fsm, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val p1 = d.driverStandings.first()
            LeaderCard(p1, c, sc)
            Spacer(GlanceModifier.height(sc.spaceSm))
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(buildStandingsList(d, includeP1 = false)) { item ->
                    StandingsItemRow(item, c, sc, showTeam = true)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ── Sealed item type for unified LazyColumn ───────────────────────
private sealed interface StandingsItem {
    data class SectionHeader(val label: String) : StandingsItem
    data class Driver(val data: DriverStandingRow) : StandingsItem
    data class Constructor(val data: ConstructorStandingRow) : StandingsItem
}

private fun buildStandingsList(d: F1WidgetData, includeP1: Boolean): List<StandingsItem> = buildList {
    add(StandingsItem.SectionHeader("DRIVERS"))
    val drivers = if (includeP1) d.driverStandings else d.driverStandings.drop(1)
    drivers.forEach { add(StandingsItem.Driver(it)) }
    if (d.constructorStandings.isNotEmpty()) {
        add(StandingsItem.SectionHeader("CONSTRUCTORS"))
        d.constructorStandings.forEach { add(StandingsItem.Constructor(it)) }
    }
}

@Composable
private fun StandingsItemRow(item: StandingsItem, c: F1Clr, sc: WScale, showTeam: Boolean) {
    when (item) {
        is StandingsItem.SectionHeader -> SectionLabel(item.label, c, sc)
        is StandingsItem.Driver        -> DriverRow(item.data, c, sc, showTeam = showTeam, compact = true)
        is StandingsItem.Constructor   -> ConstructorRow(item.data, c, sc)
    }
}

// ── LEADER CARD (P1 pinned hero) ──────────────────────────────────
@Composable
private fun LeaderCard(d: DriverStandingRow, c: F1Clr, sc: WScale) {
    val tcColor = teamColorProvider(d.teamColor)
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Team color bar
            Box(GlanceModifier.width(4.dp).height((sc.fxl.value + sc.spaceMd.value * 2).dp).cornerRadius(2.dp).background(tcColor)) {}
            Spacer(GlanceModifier.width(sc.spaceMd))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", style = TextStyle(fontSize = sc.iconSm))
                    Spacer(GlanceModifier.width(sc.spaceXs))
                    Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                }
                Spacer(GlanceModifier.height(sc.spaceXs))
                Text(d.team, style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.gold))
                Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                if (d.wins > 0) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                    }
                }
            }
        }
    }
}

// ── SECTION LABEL ─────────────────────────────────────────────────
@Composable
private fun SectionLabel(label: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
    }
}

// ── DRIVER ROW ────────────────────────────────────────────────────
@Composable
private fun DriverRow(d: DriverStandingRow, c: F1Clr, sc: WScale, showTeam: Boolean, compact: Boolean = false) {
    val tcColor = teamColorProvider(d.teamColor)
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Team color stripe
        Box(GlanceModifier.width(3.dp).height(sc.fmd.value.dp + sc.spaceXs * 2).cornerRadius(2.dp).background(tcColor)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        // Position badge
        Box(
            GlanceModifier.cornerRadius(3.dp)
                .background(when (d.position) { 1 -> c.gold; 2 -> c.silver; 3 -> c.bronze; else -> c.pill })
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) { Text("${d.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text)) }
        Spacer(GlanceModifier.width(sc.spaceSm))
        // Name / acronym
        if (compact) {
            Text(d.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
            if (showTeam) {
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(d.team.take(12), style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        } else {
            Column(GlanceModifier.defaultWeight()) {
                Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                if (showTeam) Text(d.team, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
        Spacer(GlanceModifier.width(sc.spaceXs))
        Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        if (d.wins > 0) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
            }
        }
    }
}

// ── CONSTRUCTOR ROW ───────────────────────────────────────────────
@Composable
private fun ConstructorRow(d: ConstructorStandingRow, c: F1Clr, sc: WScale) {
    val tcColor = teamColorProvider(d.teamColor)
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(3.dp).height(sc.fmd.value.dp + sc.spaceXs * 2).cornerRadius(2.dp).background(tcColor)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Box(
            GlanceModifier.cornerRadius(3.dp)
                .background(when (d.position) { 1 -> c.gold; 2 -> c.silver; 3 -> c.bronze; else -> c.pill })
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) { Text("${d.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text)) }
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(d.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text("${d.points.toInt()}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
        Spacer(GlanceModifier.width(sc.spaceXs))
        Text("pts", style = TextStyle(fontSize = sc.fxs, color = c.sub))
        if (d.wins > 0) {
            Spacer(GlanceModifier.width(sc.spaceSm))
            Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("${d.wins}W", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
            }
        }
    }
}
