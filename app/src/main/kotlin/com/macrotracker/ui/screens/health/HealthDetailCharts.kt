package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HrColor = Color(0xFFEF5350)
private val HrSoft = Color(0xFFEF5350).copy(alpha = 0.14f)
private val SleepAwake = Color(0xFFFFB74D)
private val SleepRem = Color(0xFF64B5F6)
private val SleepLight = Color(0xFF81C784)
private val SleepDeep = Color(0xFF7986CB)

@Composable
fun HeartRateDetailChart(
    samples: List<HeartRateRecord.Sample>,
    date: LocalDate,
    haptics: HapticHelper,
) {
    val dateStr = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("MMM d"))
    val bpmList = remember(samples) { samples.map { it.beatsPerMinute } }
    val stats = remember(bpmList) { computeHeartRateDayStats(bpmList) }
    val zones = remember(bpmList) { computeHeartRateZones(bpmList) }
    val hourly = remember(samples) { computeHourlyHeartRate(samples) }
    val zone = remember { ZoneId.systemDefault() }
    val reveal = remember(samples) { Animatable(0f) }
    LaunchedEffect(samples) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Heart rate",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Text(
            dateStr,
            fontSize = 12.sp,
            color = TextSecondary.copy(alpha = 0.85f),
        )

        if (samples.isEmpty() || stats == null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("No heart-rate samples for this day.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero: avg big, resting secondary
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Average", fontSize = 12.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${stats.avgBpm}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = HrColor,
                        lineHeight = 46.sp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "bpm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            stats.restingEstimate?.let { rest ->
                Column {
                    Text("Resting ~", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$rest",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            }
            Column {
                Text("Range", fontSize = 12.sp, color = TextSecondary)
                Text(
                    "${stats.minBpm}–${stats.maxBpm}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val chartPoints = remember(samples) {
            val raw = samples.map { sample ->
                val zdt = sample.time.atZone(zone)
                val hour = zdt.hour + zdt.minute / 60f + zdt.second / 3600f
                hour to sample
            }
            if (raw.size <= 420) raw
            else {
                val step = (raw.size / 420).coerceAtLeast(1)
                raw.filterIndexed { i, _ -> i % step == 0 }
            }
        }
        val hours = remember(chartPoints) { FloatArray(chartPoints.size) { chartPoints[it].first } }
        var touchX by remember { mutableStateOf<Float?>(null) }
        val textMeasurer = rememberTextMeasurer()

        fun nearestIndex(x: Float, width: Float): Int {
            if (hours.isEmpty() || width <= 0f) return -1
            val target = (x / width) * 24f
            var lo = 0
            var hi = hours.lastIndex
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (hours[mid] < target) lo = mid + 1 else hi = mid
            }
            val prev = (lo - 1).coerceAtLeast(0)
            return if (kotlin.math.abs(hours[prev] - target) <= kotlin.math.abs(hours[lo] - target)) prev else lo
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Background)
                .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(chartPoints) {
                        var last = -1
                        detectDragGestures(
                            onDragStart = { touchX = it.x.coerceIn(0f, size.width.toFloat()) },
                            onDrag = { change, _ ->
                                val w = size.width.toFloat()
                                val x = change.position.x.coerceIn(0f, w)
                                touchX = x
                                val idx = nearestIndex(x, w)
                                if (idx != -1 && idx != last) {
                                    haptics.tick()
                                    last = idx
                                }
                            },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null },
                        )
                    }
                    .pointerInput(chartPoints) {
                        detectTapGestures(
                            onPress = {
                                touchX = it.x.coerceIn(0f, size.width.toFloat())
                                haptics.tick()
                                tryAwaitRelease()
                                touchX = null
                            },
                        )
                    },
            ) {
                val padL = 36.dp.toPx()
                val padR = 10.dp.toPx()
                val padTop = 30.dp.toPx()
                val padBottom = 10.dp.toPx()
                val width = size.width - padL - padR
                val graphH = size.height - padTop - padBottom
                val minHr = (stats.minBpm - 10).toFloat().coerceAtLeast(40f)
                val maxHr = (stats.maxBpm + 10).toFloat().coerceAtLeast(minHr + 25f)
                val range = maxHr - minHr
                val progress = reveal.value

                // Y labels + grid
                listOf(maxHr, (minHr + maxHr) / 2f, minHr).forEach { bpm ->
                    val yNorm = ((bpm - minHr) / range).coerceIn(0f, 1f)
                    val y = padTop + graphH - yNorm * graphH
                    drawLine(
                        Border.copy(alpha = 0.35f),
                        Offset(padL, y),
                        Offset(padL + width, y),
                        1.dp.toPx(),
                    )
                    val layout = textMeasurer.measure(
                        "${bpm.roundToInt()}",
                        TextStyle(color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(padL - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f),
                    )
                }

                // Min–max band
                val minY = padTop + graphH - (((stats.minBpm - minHr) / range).toFloat().coerceIn(0f, 1f) * graphH)
                val maxY = padTop + graphH - (((stats.maxBpm - minHr) / range).toFloat().coerceIn(0f, 1f) * graphH)
                drawRect(
                    color = HrSoft,
                    topLeft = Offset(padL, maxY),
                    size = Size(width * progress, (minY - maxY).coerceAtLeast(2f)),
                )

                val line = Path()
                val area = Path()
                val points = ArrayList<Pair<Offset, HeartRateRecord.Sample>>(chartPoints.size)
                chartPoints.forEachIndexed { index, (hour, sample) ->
                    val x = padL + (hour / 24f) * width
                    val yNorm = ((sample.beatsPerMinute - minHr) / range).coerceIn(0f, 1f)
                    val y = padTop + graphH - yNorm * graphH
                    // Reveal clips from left
                    if (hour / 24f > progress) return@forEachIndexed
                    points += Offset(x, y) to sample
                    if (points.size == 1) {
                        line.moveTo(x, y)
                        area.moveTo(x, padTop + graphH)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                if (points.isNotEmpty()) {
                    area.lineTo(points.last().first.x, padTop + graphH)
                    area.close()
                    drawPath(area, HrColor.copy(alpha = 0.16f))
                    drawPath(
                        line,
                        HrColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                val avgY = padTop + graphH - (((stats.avgBpm - minHr) / range).toFloat().coerceIn(0f, 1f) * graphH)
                drawLine(
                    color = TextSecondary.copy(alpha = 0.55f),
                    start = Offset(padL, avgY),
                    end = Offset(padL + width * progress, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )

                val tx = touchX
                if (tx != null && points.isNotEmpty()) {
                    val idx = nearestIndex(tx.coerceIn(padL, padL + width) - padL, width)
                    if (idx in points.indices) {
                        val (point, sample) = points[idx]
                        drawLine(
                            Border.copy(alpha = 0.9f),
                            Offset(point.x, padTop),
                            Offset(point.x, padTop + graphH),
                            1.dp.toPx(),
                        )
                        drawCircle(Color.White, 5.dp.toPx(), point)
                        drawCircle(HrColor, 3.2.dp.toPx(), point)

                        val zdt = sample.time.atZone(zone)
                        val label = "${sample.beatsPerMinute} bpm  ·  ${zdt.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                        val layout = textMeasurer.measure(
                            label,
                            TextStyle(color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        )
                        val tw = layout.size.width + 16.dp.toPx()
                        val th = layout.size.height + 10.dp.toPx()
                        var tipX = point.x - tw / 2f
                        tipX = tipX.coerceIn(8.dp.toPx(), size.width - tw - 8.dp.toPx())
                        drawRoundRect(Surface, Offset(tipX, 6.dp.toPx()), Size(tw, th), CornerRadius(10.dp.toPx()))
                        drawRoundRect(
                            Border,
                            Offset(tipX, 6.dp.toPx()),
                            Size(tw, th),
                            CornerRadius(10.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                        drawText(layout, topLeft = Offset(tipX + 8.dp.toPx(), 11.dp.toPx()))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("12 AM", fontSize = 10.sp, color = TextSecondary)
            Text("12 PM", fontSize = 10.sp, color = TextSecondary)
            Text("11:59 PM", fontSize = 10.sp, color = TextSecondary)
        }

        if (hourly.any { it != null }) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "Hourly average",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HourlyHeartRateBars(hourly = hourly, reveal = reveal.value, accent = HrColor)
        }

        if (zones != null && zones.total >= 10) {
            Spacer(modifier = Modifier.height(18.dp))
            HeartRateZonesChart(zones)
        }
    }
}

@Composable
fun SleepDetailChart(
    sessions: List<SleepSessionRecord>,
    date: LocalDate,
    haptics: HapticHelper,
) {
    val dateStr = if (date == LocalDate.now()) "Last night" else date.format(DateTimeFormatter.ofPattern("MMM d"))
    val stages = remember(sessions) { sessions.flatMap { it.stages }.sortedBy { it.startTime } }
    val nightScore = remember(sessions) { computeSleepNightScore(sessions) }
    val reveal = remember(sessions) { Animatable(0f) }
    LaunchedEffect(sessions) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Sleep",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Text(dateStr, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.85f))

        if (sessions.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("No sleep data for this day.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        nightScore?.let { score ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sleep score", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${score.score}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleepDeep,
                            lineHeight = 50.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            score.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
                Column {
                    Text("Asleep", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        formatMinutesCompact(score.totalMinutes),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
                score.efficiencyPercent?.let { eff ->
                    Column {
                        Text("Efficiency", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            "$eff%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ThinScoreBar(progress = score.score / 100f, color = SleepDeep)
        }

        if (stages.isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("No sleep-stage breakdown for this night.", color = TextSecondary, fontSize = 13.sp)
            return@Column
        }

        fun minutesFor(stageType: Int): Long = stages
            .filter { it.stage == stageType }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

        val deepMin = minutesFor(SleepSessionRecord.STAGE_TYPE_DEEP)
        val lightMin = minutesFor(SleepSessionRecord.STAGE_TYPE_LIGHT)
        val remMin = minutesFor(SleepSessionRecord.STAGE_TYPE_REM)
        val awakeMin = minutesFor(SleepSessionRecord.STAGE_TYPE_AWAKE)
        val asleepTotal = (deepMin + lightMin + remMin).coerceAtLeast(1L)

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "Stage mix",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        StageMixBars(
            rows = listOf(
                Triple("Deep", deepMin, SleepDeep),
                Triple("Light", lightMin, SleepLight),
                Triple("REM", remMin, SleepRem),
                Triple("Awake", awakeMin, SleepAwake),
            ).filter { it.second > 0 },
            totalForPct = asleepTotal + awakeMin,
            reveal = reveal.value,
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "Stages over time",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        var touchX by remember { mutableStateOf<Float?>(null) }
        val textMeasurer = rememberTextMeasurer()
        val minTime = stages.first().startTime.toEpochMilli()
        val maxTime = stages.last().endTime.toEpochMilli()
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)

        Row(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .padding(vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                StageAxisLabel("Awake", SleepAwake)
                StageAxisLabel("REM", SleepRem)
                StageAxisLabel("Light", SleepLight)
                StageAxisLabel("Deep", SleepDeep)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background)
                    .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .pointerInput(stages) {
                            var last: SleepSessionRecord.Stage? = null
                            detectDragGestures(
                                onDragStart = { touchX = it.x.coerceIn(0f, size.width.toFloat()) },
                                onDrag = { change, _ ->
                                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                    touchX = x
                                    val t = minTime + ((x / size.width) * timeRange).toLong()
                                    val stage = stages.find {
                                        t in it.startTime.toEpochMilli()..it.endTime.toEpochMilli()
                                    }
                                    if (stage != null && stage != last) {
                                        haptics.tick()
                                        last = stage
                                    }
                                },
                                onDragEnd = { touchX = null },
                                onDragCancel = { touchX = null },
                            )
                        }
                        .pointerInput(stages) {
                            detectTapGestures(
                                onPress = {
                                    touchX = it.x.coerceIn(0f, size.width.toFloat())
                                    haptics.tick()
                                    tryAwaitRelease()
                                    touchX = null
                                },
                            )
                        },
                ) {
                    val width = size.width
                    val height = size.height
                    val padTop = 28.dp.toPx()
                    val padBottom = 10.dp.toPx()
                    val graphH = height - padTop - padBottom
                    val progress = reveal.value
                    val laneH = graphH / 4f
                    val lanes = mapOf(
                        SleepSessionRecord.STAGE_TYPE_AWAKE to (0 to SleepAwake),
                        SleepSessionRecord.STAGE_TYPE_REM to (1 to SleepRem),
                        SleepSessionRecord.STAGE_TYPE_LIGHT to (2 to SleepLight),
                        SleepSessionRecord.STAGE_TYPE_DEEP to (3 to SleepDeep),
                    )

                    // Soft lane bands
                    for (i in 0 until 4) {
                        val y = padTop + i * laneH
                        drawRect(
                            color = Border.copy(alpha = if (i % 2 == 0) 0.12f else 0.06f),
                            topLeft = Offset(0f, y),
                            size = Size(width, laneH),
                        )
                    }

                    stages.forEach { stage ->
                        val (lane, color) = lanes[stage.stage] ?: return@forEach
                        val startX = ((stage.startTime.toEpochMilli() - minTime).toFloat() / timeRange) * width * progress
                        val endX = ((stage.endTime.toEpochMilli() - minTime).toFloat() / timeRange) * width * progress
                        val top = padTop + lane * laneH + laneH * 0.18f
                        val barH = laneH * 0.64f
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(startX, top),
                            size = Size((endX - startX).coerceAtLeast(2.dp.toPx()), barH),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }

                    val tx = touchX
                    if (tx != null) {
                        val t = minTime + ((tx / width) * timeRange).toLong()
                        val active = stages.find {
                            t in it.startTime.toEpochMilli()..it.endTime.toEpochMilli()
                        }
                        if (active != null) {
                            val (lane, color) = lanes[active.stage] ?: (2 to TextSecondary)
                            val cy = padTop + lane * laneH + laneH / 2f
                            drawLine(Border, Offset(tx, padTop), Offset(tx, padTop + graphH), 1.2.dp.toPx())
                            drawCircle(Color.White, 4.dp.toPx(), Offset(tx, cy))
                            drawCircle(color, 2.6.dp.toPx(), Offset(tx, cy))

                            val name = when (active.stage) {
                                SleepSessionRecord.STAGE_TYPE_AWAKE -> "Awake"
                                SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                                SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light"
                                SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
                                else -> "Sleep"
                            }
                            val fmt = DateTimeFormatter.ofPattern("h:mm a")
                            val z = ZoneId.systemDefault()
                            val startLabel = Instant.ofEpochMilli(active.startTime.toEpochMilli()).atZone(z).format(fmt)
                            val endLabel = Instant.ofEpochMilli(active.endTime.toEpochMilli()).atZone(z).format(fmt)
                            val mins = Duration.between(active.startTime, active.endTime).toMinutes()
                            val label = "$name · ${mins}m\n$startLabel – $endLabel"
                            val layout = textMeasurer.measure(
                                label,
                                TextStyle(
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            val tw = layout.size.width + 16.dp.toPx()
                            val th = layout.size.height + 10.dp.toPx()
                            var tipX = tx - tw / 2f
                            tipX = tipX.coerceIn(6.dp.toPx(), width - tw - 6.dp.toPx())
                            drawRoundRect(Surface, Offset(tipX, 4.dp.toPx()), Size(tw, th), CornerRadius(10.dp.toPx()))
                            drawRoundRect(
                                Border,
                                Offset(tipX, 4.dp.toPx()),
                                Size(tw, th),
                                CornerRadius(10.dp.toPx()),
                                style = Stroke(1.dp.toPx()),
                            )
                            drawText(layout, topLeft = Offset(tipX + 8.dp.toPx(), 9.dp.toPx()))
                        }
                    }
                }
            }
        }

        val startZ = Instant.ofEpochMilli(minTime).atZone(ZoneId.systemDefault())
        val endZ = Instant.ofEpochMilli(maxTime).atZone(ZoneId.systemDefault())
        val fmt = DateTimeFormatter.ofPattern("h:mm a")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 54.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(startZ.format(fmt), fontSize = 10.sp, color = TextSecondary)
            Text(endZ.format(fmt), fontSize = 10.sp, color = TextSecondary)
        }
    }
}

/** Hourly avg bpm; null = no samples that hour. */
fun computeHourlyHeartRate(samples: List<HeartRateRecord.Sample>): List<Long?> {
    if (samples.isEmpty()) return List(24) { null }
    val zone = ZoneId.systemDefault()
    val buckets = Array(24) { mutableListOf<Long>() }
    samples.forEach { s ->
        val hour = s.time.atZone(zone).hour
        buckets[hour].add(s.beatsPerMinute)
    }
    return buckets.map { list ->
        if (list.isEmpty()) null else list.average().roundToInt().toLong()
    }
}

@Composable
private fun HourlyHeartRateBars(
    hourly: List<Long?>,
    reveal: Float,
    accent: Color,
) {
    val maxBpm = hourly.filterNotNull().maxOrNull()?.toFloat() ?: 1f
    val minBpm = hourly.filterNotNull().minOrNull()?.toFloat() ?: 0f
    val floor = (minBpm - 8f).coerceAtLeast(40f)
    val ceiling = (maxBpm + 8f).coerceAtLeast(floor + 20f)
    val range = ceiling - floor

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        val padX = 8.dp.toPx()
        val padY = 10.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val gap = 2.dp.toPx()
        val barW = (w - gap * 23) / 24f
        hourly.forEachIndexed { hour, bpm ->
            val x = padX + hour * (barW + gap)
            if (bpm == null) {
                drawRoundRect(
                    color = Border.copy(alpha = 0.25f),
                    topLeft = Offset(x, padY + h - 3.dp.toPx()),
                    size = Size(barW, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            } else {
                val frac = ((bpm - floor) / range).coerceIn(0.08f, 1f) * reveal
                val barH = h * frac
                drawRoundRect(
                    color = accent.copy(alpha = 0.85f),
                    topLeft = Offset(x, padY + h - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("12a", fontSize = 9.sp, color = TextSecondary)
        Text("6a", fontSize = 9.sp, color = TextSecondary)
        Text("12p", fontSize = 9.sp, color = TextSecondary)
        Text("6p", fontSize = 9.sp, color = TextSecondary)
        Text("11p", fontSize = 9.sp, color = TextSecondary)
    }
}

@Composable
fun HeartRateZonesChart(zones: HeartRateZones) {
    val colors = listOf(
        Color(0xFF90A4AE),
        Color(0xFF66BB6A),
        Color(0xFFFFCA28),
        Color(0xFFFFA726),
        Color(0xFFEF5350),
    )
    val labels = listOf("Rest", "Easy", "Cardio", "Hard", "Peak")
    val counts = listOf(zones.zone1Rest, zones.zone2Easy, zones.zone3Cardio, zones.zone4Hard, zones.zone5Peak)
    val max = counts.max().coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Time in zones",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Background)
                .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEachIndexed { i, count ->
                val pct = if (zones.total > 0) (count * 100f / zones.total) else 0f
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (count > 0) {
                        Text(
                            "${pct.roundToInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors[i],
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((count.toFloat() / max) * 56f).coerceAtLeast(if (count > 0) 6f else 2f).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (count > 0) colors[i] else Border.copy(alpha = 0.3f)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(labels[i], fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StageMixBars(
    rows: List<Triple<String, Long, Color>>,
    totalForPct: Long,
    reveal: Float,
) {
    val total = totalForPct.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, minutes, color) ->
            val pct = minutes.toFloat() / total
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Border.copy(alpha = 0.35f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct.coerceIn(0f, 1f) * reveal)
                            .clip(RoundedCornerShape(5.dp))
                            .background(color),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    formatMinutesCompact(minutes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun ThinScoreBar(progress: Float, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        drawRoundRect(
            color = Border.copy(alpha = 0.45f),
            size = size,
            cornerRadius = CornerRadius(size.height / 2f),
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

@Composable
private fun StageAxisLabel(label: String, color: Color) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
}
