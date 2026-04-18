package com.syschimp.glucoripper.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import java.time.Duration
import java.time.Instant

private val GlucoseLow = Color(0xFFE5484D)
private val GlucoseInRange = Color(0xFF30A46C)
private val GlucoseElevated = Color(0xFFF5A524)
private val GlucoseHigh = Color(0xFFE5484D)
private val AccentTeal = Color(0xFF4FD8EB)

@Composable
fun WearMainScreen(payload: GlucosePayload) {
    Scaffold(
        timeText = { TimeText() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (payload.latestTimeMillis == 0L) {
                EmptyState()
            } else {
                ReadingView(payload)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            "Glucoripper",
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Waiting for a reading from your phone…",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReadingView(payload: GlucosePayload) {
    val band = classify(payload.latestMgDl, payload.targetLowMgDl, payload.targetHighMgDl)
    val valueText = formatGlucose(payload.latestMgDl, payload.unit)
    val unitText = unitLabel(payload.unit)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                valueText,
                style = MaterialTheme.typography.display1,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                color = band.color,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unitText,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(band.color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                band.label,
                style = MaterialTheme.typography.caption1,
                color = band.color,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                " · " + relativeTime(payload.latestInstant),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
        }

        if (payload.windowMgDls.size >= 2) {
            Spacer(Modifier.height(10.dp))
            MiniChart(
                times = payload.windowTimesMillis,
                values = payload.windowMgDls,
                lowMgDl = payload.targetLowMgDl,
                highMgDl = payload.targetHighMgDl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            )
        }

        if (payload.lastSyncMillis > 0L) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Synced " + relativeTime(payload.lastSyncInstant),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun MiniChart(
    times: LongArray,
    values: FloatArray,
    lowMgDl: Double,
    highMgDl: Double,
    modifier: Modifier = Modifier,
) {
    val bandFill = GlucoseInRange.copy(alpha = 0.18f)
    Canvas(
        modifier = modifier
            .background(Color(0xFF1A1F22), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (values.size < 2) return@Canvas
        val lowF = lowMgDl.toFloat()
        val highF = highMgDl.toFloat()
        val minV = minOf(values.min(), lowF - 20f).coerceAtLeast(40f)
        val maxV = maxOf(values.max(), highF + 20f).coerceAtMost(400f)
        val range = (maxV - minV).coerceAtLeast(1f)
        val minT = times.first()
        val maxT = times.last()
        val tSpan = (maxT - minT).coerceAtLeast(1L).toFloat()

        fun yFor(v: Float): Float = size.height - ((v - minV) / range) * size.height
        fun xFor(t: Long): Float = ((t - minT).toFloat() / tSpan) * size.width

        val topY = yFor(highF)
        val botY = yFor(lowF)
        drawRect(
            color = bandFill,
            topLeft = Offset(0f, topY),
            size = Size(size.width, botY - topY),
        )

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = xFor(times[i])
            val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = AccentTeal, style = Stroke(width = 2.dp.toPx()))

        val lx = xFor(times.last())
        val ly = yFor(values.last())
        drawCircle(color = AccentTeal, radius = 2.5.dp.toPx(), center = Offset(lx, ly))
    }
}

// ────────── helpers (duplicated from phone to keep wear standalone) ──────────

private data class Band(val label: String, val color: Color)

private fun classify(mgDl: Double, low: Double, high: Double): Band = when {
    mgDl <= 0.0 -> Band("—", Color.Gray)
    mgDl < low -> Band("Low", GlucoseLow)
    mgDl <= high -> Band("In range", GlucoseInRange)
    mgDl < 180.0 -> Band("Elevated", GlucoseElevated)
    else -> Band("High", GlucoseHigh)
}

private fun formatGlucose(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
    GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / 18.0)
}

private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "mg/dL"
    GlucoseUnit.MMOL_PER_L -> "mmol/L"
}

private fun relativeTime(t: Instant): String {
    val d = Duration.between(t, Instant.now())
    return when {
        d.isNegative -> "just now"
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        d.toDays() < 7 -> "${d.toDays()}d ago"
        else -> "> 1w ago"
    }
}
