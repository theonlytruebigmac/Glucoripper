package com.syschimp.glucoripper.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Watch "History" page — reverse-chronological list of readings from the last
 * 7 days, grouped by day. Read-only; taps do nothing (no sync triggers on-wrist).
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

    val rows = buildReadingList(payload)
    val today = LocalDate.now(ZoneId.systemDefault())

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
    ) {
        item {
            Text(
                "Recent readings",
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(items = rows, key = { it.key }) { row ->
            when (row) {
                is HistoryRow.DayHeader -> DayHeaderRow(row.date, today)
                is HistoryRow.Reading -> ReadingRow(row)
            }
        }
    }
}

@Composable
private fun DayHeaderRow(date: LocalDate, today: LocalDate) {
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(HEADER_FMT)
    }
    Text(
        label,
        style = MaterialTheme.typography.caption2,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun ReadingRow(row: HistoryRow.Reading) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xFF1A1F22), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(row.color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.valueText,
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = row.color,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    row.unitText,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
            }
            if (row.meal.isNotEmpty()) {
                Text(
                    row.meal,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        Text(
            row.timeText,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.75f),
        )
    }
}

private sealed interface HistoryRow {
    val key: String

    data class DayHeader(val date: LocalDate) : HistoryRow {
        override val key: String get() = "day-$date"
    }

    data class Reading(
        override val key: String,
        val valueText: String,
        val unitText: String,
        val timeText: String,
        val meal: String,
        val color: Color,
    ) : HistoryRow
}

private fun buildReadingList(payload: GlucosePayload): List<HistoryRow> {
    val times = payload.windowTimesMillis
    val values = payload.windowMgDls
    val meals = payload.windowMealRelations
    // All three arrays should be parallel; take the shortest length if the
    // DataClient payload ever arrives with a size mismatch rather than risk
    // reading past the end or mis-pairing meals to readings.
    val n = minOf(times.size, values.size, meals.size)
    if (n == 0) return emptyList()

    val zone = ZoneId.systemDefault()
    val result = mutableListOf<HistoryRow>()
    var currentDate: LocalDate? = null
    // Iterate newest → oldest; payload arrays are sorted ascending by time.
    for (i in n - 1 downTo 0) {
        val t = Instant.ofEpochMilli(times[i])
        val ldt = LocalDateTime.ofInstant(t, zone)
        val date = ldt.toLocalDate()
        if (date != currentDate) {
            result += HistoryRow.DayHeader(date)
            currentDate = date
        }
        val mgDl = values[i].toDouble()
        val relation = meals.getOrElse(i) { GlucosePayload.RELATION_UNKNOWN }
        val (low, high) = payload.targetRangeFor(relation)
        val band = classify(mgDl, low, high)
        result += HistoryRow.Reading(
            key = "r-$i-${times[i]}",
            valueText = formatGlucose(mgDl, payload.unit),
            unitText = unitLabel(payload.unit),
            timeText = ldt.format(TIME_FMT),
            meal = mealLabel(relation),
            color = band.color,
        )
    }
    return result
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val HEADER_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
