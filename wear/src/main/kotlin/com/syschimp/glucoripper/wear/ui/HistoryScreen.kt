package com.syschimp.glucoripper.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.syschimp.glucoripper.wear.data.GlucosePayload
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Watch "History" page — Re-Diary day-card layout. Each day = mini sparkline
 * with target-band reference lines, day label + average mg/dL, status stripe.
 */
@Composable
fun HistoryScreen(
    payload: GlucosePayload,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
) {
    if (payload.windowMgDls.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No readings in the last week",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        return
    }

    val days = remember(payload) { buildDayCards(payload) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                listState.dispatchRawDelta(event.verticalScrollPixels)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                "LAST 7 DAYS",
                style = WearOverline,
                color = WearFgMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(days, key = { it.key }) { card ->
            DayCard(card)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DayCard(card: DayCardData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WearElev1, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Status stripe
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(card.stripeColor.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.width(56.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                card.dayLabel,
                style = MaterialTheme.typography.caption3,
                color = WearFg,
                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Text(
                card.avgText,
                style = WearMono.Row,
                color = WearFgMuted,
            )
        }
        DaySparkline(
            curve = card.curve,
            low = card.low,
            high = card.high,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )
    }
}

@Composable
private fun DaySparkline(
    curve: List<Float>,
    low: Double,
    high: Double,
    modifier: Modifier = Modifier,
) {
    if (curve.size < 2) {
        Box(modifier)
        return
    }
    val fg = MaterialTheme.colors.onBackground
    val faint = WearFgFaint
    Canvas(modifier) {
        val padX = 6.dp.toPx()
        val padY = 4.dp.toPx()
        val w = size.width
        val h = size.height
        val mn = (curve.min().toDouble().coerceAtMost(low - 8.0)).toFloat()
        val mx = (curve.max().toDouble().coerceAtLeast(high + 8.0)).toFloat()
        val span = (mx - mn).coerceAtLeast(1f)
        fun yOf(v: Float): Float = padY + (1f - (v - mn) / span) * (h - padY * 2)
        val xStep = (w - padX * 2) / (curve.size - 1).coerceAtLeast(1)
        val xs = curve.indices.map { padX + it * xStep }
        val ys = curve.map(::yOf)

        val dash = PathEffect.dashPathEffect(floatArrayOf(1f, 3f), 0f)
        drawLine(
            color = faint,
            start = Offset(padX, yOf(low.toFloat())),
            end = Offset(w - padX, yOf(low.toFloat())),
            strokeWidth = 0.5f * density,
            pathEffect = dash,
        )
        drawLine(
            color = faint,
            start = Offset(padX, yOf(high.toFloat())),
            end = Offset(w - padX, yOf(high.toFloat())),
            strokeWidth = 0.5f * density,
            pathEffect = dash,
        )

        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until xs.size) {
                val cx = (xs[i - 1] + xs[i]) / 2f
                cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
            }
        }
        drawPath(
            path,
            color = fg,
            style = Stroke(
                width = 1.4f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// ─────────── Day card data ───────────

private data class DayCardData(
    val key: String,
    val dayLabel: String,
    val avgText: String,
    val low: Double,
    val high: Double,
    val curve: List<Float>,
    val stripeColor: Color,
)

private fun buildDayCards(payload: GlucosePayload): List<DayCardData> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val n = minOf(
        payload.windowTimesMillis.size,
        payload.windowMgDls.size,
        payload.windowMealRelations.size,
    )
    if (n == 0) return emptyList()

    data class Bucket(val date: LocalDate, val ts: MutableList<Long>, val mgs: MutableList<Float>, val meals: MutableList<Int>)
    val buckets = linkedMapOf<LocalDate, Bucket>()
    for (i in 0 until n) {
        val date = Instant.ofEpochMilli(payload.windowTimesMillis[i])
            .atZone(zone).toLocalDate()
        val b = buckets.getOrPut(date) {
            Bucket(date, mutableListOf(), mutableListOf(), mutableListOf())
        }
        b.ts += payload.windowTimesMillis[i]
        b.mgs += payload.windowMgDls[i]
        b.meals += payload.windowMealRelations[i]
    }

    val (low, high) = payload.targetRangeFor(GlucosePayload.RELATION_GENERAL)

    return buckets.values
        .sortedByDescending { it.date }
        .map { bucket ->
            val avg = bucket.mgs.average().roundToInt()
            val mn = bucket.mgs.min().toDouble()
            val mx = bucket.mgs.max().toDouble()
            val stripe = when {
                mn < low -> GlucoseLow
                mx > high -> GlucoseElevated
                else -> GlucoseInRange
            }
            DayCardData(
                key = "day-${bucket.date}",
                dayLabel = when (bucket.date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> bucket.date.format(DAY_FMT)
                },
                avgText = avg.toString(),
                low = low,
                high = high,
                curve = bucket.mgs.toList(),
                stripeColor = stripe,
            )
        }
}

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d")
