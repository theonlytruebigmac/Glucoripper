package com.syschimp.glucoripper.data.stats

import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.UserPreferences
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class StatsWindow(val days: Int, val label: String) {
    W7(7, "7d"),
    W14(14, "14d"),
    W30(30, "30d"),
    W90(90, "90d"),
}

data class Aggregate(
    val count: Int,
    val avgMgDl: Double?,
    val medianMgDl: Double?,
    val minMgDl: Double?,
    val maxMgDl: Double?,
    /** Coefficient of variation = stddev / mean × 100. <36% = stable (ADA). */
    val cvPercent: Double?,
    /** Glucose Management Indicator (Bergenstal 2018): 3.31 + 0.02392 × avg mg/dL. */
    val gmiPercent: Double?,
    val inRangePct: Int?,
    val lowPct: Int?,
    val highPct: Int?,
    /** Readings below 70 mg/dL — ADA hypo threshold. */
    val hypoEvents: Int,
    /** Readings above 180 mg/dL — ADA post-prandial hyper threshold. */
    val hyperEvents: Int,
)

data class ContextAggregate(
    val label: String,
    /** One of [BloodGlucoseRecord.RELATION_TO_MEAL_*]. */
    val relationCode: Int,
    val targetLowMgDl: Double,
    val targetHighMgDl: Double,
    val aggregate: Aggregate,
)

data class TodBucket(
    val label: String,
    val hourStartInclusive: Int,
    val hourEndInclusive: Int,
    val count: Int,
    val avgMgDl: Double?,
)

data class PairedMealDelta(
    val pairCount: Int,
    val avgPreMgDl: Double?,
    val avgPostMgDl: Double?,
    val avgDeltaMgDl: Double?,
)

data class ContextMixSlice(val label: String, val count: Int, val percent: Int)

data class PeriodDelta(
    val priorAvgMgDl: Double?,
    val currentAvgMgDl: Double?,
    val priorTirPct: Int?,
    val currentTirPct: Int?,
) {
    val avgDeltaMgDl: Double?
        get() = if (priorAvgMgDl != null && currentAvgMgDl != null)
            currentAvgMgDl - priorAvgMgDl else null
    val tirDeltaPct: Int?
        get() = if (priorTirPct != null && currentTirPct != null)
            currentTirPct - priorTirPct else null
}

data class GlucoseStats(
    val window: StatsWindow,
    val readingsPerDay: Double?,
    val overall: Aggregate,
    val byContext: List<ContextAggregate>,
    val timeOfDay: List<TodBucket>,
    val pairedMealDelta: PairedMealDelta,
    val contextMix: List<ContextMixSlice>,
    val periodDelta: PeriodDelta,
)

private const val HYPO_THRESHOLD_MGDL = 70.0
private const val HYPER_THRESHOLD_MGDL = 180.0
private const val MEAL_PAIR_MIN_MINUTES = 10L
private const val MEAL_PAIR_MAX_MINUTES = 180L

fun computeStats(
    readings: List<BloodGlucoseRecord>,
    annotations: Map<String, ReadingAnnotation>,
    prefs: UserPreferences,
    window: StatsWindow,
    now: Instant = Instant.now(),
): GlucoseStats {
    val windowDuration = Duration.ofDays(window.days.toLong())
    val cutoffCurrent = now.minus(windowDuration)
    val cutoffPrior = now.minus(windowDuration.multipliedBy(2))

    val current = readings.filter { it.time.isAfter(cutoffCurrent) }
    val prior = readings.filter {
        it.time.isAfter(cutoffPrior) && !it.time.isAfter(cutoffCurrent)
    }

    fun effectiveMeal(r: BloodGlucoseRecord): Int {
        val ann = r.metadata.clientRecordId?.let(annotations::get)
        return ann?.mealOverride ?: r.relationToMeal
    }

    val overall = aggregate(current, prefs.targetLowMgDl, prefs.targetHighMgDl)

    val contextSpecs = listOf(
        Triple(
            "Fasting",
            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING,
            prefs.fastingLowMgDl to prefs.fastingHighMgDl,
        ),
        Triple(
            "Pre-meal",
            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL,
            prefs.preMealLowMgDl to prefs.preMealHighMgDl,
        ),
        Triple(
            "Post-meal",
            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL,
            prefs.postMealLowMgDl to prefs.postMealHighMgDl,
        ),
        Triple(
            "General",
            BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL,
            prefs.targetLowMgDl to prefs.targetHighMgDl,
        ),
    )
    val byContext = contextSpecs.map { (label, code, bounds) ->
        val subset = current.filter { effectiveMeal(it) == code }
        ContextAggregate(
            label = label,
            relationCode = code,
            targetLowMgDl = bounds.first,
            targetHighMgDl = bounds.second,
            aggregate = aggregate(subset, bounds.first, bounds.second),
        )
    }

    val zone = ZoneId.systemDefault()
    val todSpecs = listOf(
        Triple("Overnight", 0, 5),
        Triple("Morning", 6, 11),
        Triple("Afternoon", 12, 17),
        Triple("Evening", 18, 23),
    )
    val timeOfDay = todSpecs.map { (label, start, end) ->
        val subset = current.filter {
            it.time.atZone(zone).hour in start..end
        }
        val mgs = subset.map { it.level.inMilligramsPerDeciliter }
        TodBucket(
            label = label,
            hourStartInclusive = start,
            hourEndInclusive = end,
            count = subset.size,
            avgMgDl = mgs.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    val pairedMealDelta = computePairedMealDelta(current, ::effectiveMeal)

    val mixSpecs = listOf(
        "Fasting" to BloodGlucoseRecord.RELATION_TO_MEAL_FASTING,
        "Pre-meal" to BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL,
        "Post-meal" to BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL,
        "General" to BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL,
        "Untagged" to BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
    )
    val contextMix = mixSpecs.map { (label, code) ->
        val count = current.count { effectiveMeal(it) == code }
        val pct = if (current.isEmpty()) 0
        else (count * 100.0 / current.size).roundToInt()
        ContextMixSlice(label = label, count = count, percent = pct)
    }

    val priorAgg = aggregate(prior, prefs.targetLowMgDl, prefs.targetHighMgDl)
    val periodDelta = PeriodDelta(
        priorAvgMgDl = priorAgg.avgMgDl,
        currentAvgMgDl = overall.avgMgDl,
        priorTirPct = priorAgg.inRangePct,
        currentTirPct = overall.inRangePct,
    )

    val readingsPerDay = if (current.isEmpty()) null
    else current.size.toDouble() / window.days

    return GlucoseStats(
        window = window,
        readingsPerDay = readingsPerDay,
        overall = overall,
        byContext = byContext,
        timeOfDay = timeOfDay,
        pairedMealDelta = pairedMealDelta,
        contextMix = contextMix,
        periodDelta = periodDelta,
    )
}

/**
 * For every Before-meal reading, pair it with the closest After-meal reading
 * that falls within (10min, 3h] afterwards, and average the deltas.
 */
private fun computePairedMealDelta(
    readings: List<BloodGlucoseRecord>,
    effectiveMeal: (BloodGlucoseRecord) -> Int,
): PairedMealDelta {
    val sorted = readings.sortedBy { it.time }
    val afterReadings = sorted.filter {
        effectiveMeal(it) == BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
    }
    val beforeReadings = sorted.filter {
        effectiveMeal(it) == BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
    }
    val pairs = mutableListOf<Pair<Double, Double>>()
    val usedAfter = mutableSetOf<Int>()
    for (pre in beforeReadings) {
        var matchIdx = -1
        for (i in afterReadings.indices) {
            if (i in usedAfter) continue
            val minutes = Duration.between(pre.time, afterReadings[i].time).toMinutes()
            if (minutes in MEAL_PAIR_MIN_MINUTES..MEAL_PAIR_MAX_MINUTES) {
                matchIdx = i
                break
            }
        }
        if (matchIdx >= 0) {
            usedAfter += matchIdx
            pairs += pre.level.inMilligramsPerDeciliter to
                    afterReadings[matchIdx].level.inMilligramsPerDeciliter
        }
    }
    if (pairs.isEmpty()) return PairedMealDelta(0, null, null, null)
    val deltas = pairs.map { it.second - it.first }
    return PairedMealDelta(
        pairCount = pairs.size,
        avgPreMgDl = pairs.map { it.first }.average(),
        avgPostMgDl = pairs.map { it.second }.average(),
        avgDeltaMgDl = deltas.average(),
    )
}

private fun aggregate(
    readings: List<BloodGlucoseRecord>,
    lowMgDl: Double,
    highMgDl: Double,
): Aggregate {
    if (readings.isEmpty()) return Aggregate(
        count = 0, avgMgDl = null, medianMgDl = null, minMgDl = null, maxMgDl = null,
        cvPercent = null, gmiPercent = null,
        inRangePct = null, lowPct = null, highPct = null,
        hypoEvents = 0, hyperEvents = 0,
    )
    val vs = readings.map { it.level.inMilligramsPerDeciliter }
    val avg = vs.average()
    val sorted = vs.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    val variance = vs.sumOf { (it - avg) * (it - avg) } / vs.size
    val stddev = sqrt(variance)
    val cv = if (avg > 0) stddev / avg * 100 else null
    val gmi = 3.31 + 0.02392 * avg
    val low = vs.count { it < lowMgDl }
    val high = vs.count { it > highMgDl }
    val inRange = vs.size - low - high
    val total = vs.size.toDouble()
    return Aggregate(
        count = vs.size,
        avgMgDl = avg,
        medianMgDl = median,
        minMgDl = vs.min(),
        maxMgDl = vs.max(),
        cvPercent = cv,
        gmiPercent = gmi,
        inRangePct = (inRange / total * 100).roundToInt(),
        lowPct = (low / total * 100).roundToInt(),
        highPct = (high / total * 100).roundToInt(),
        hypoEvents = vs.count { it < HYPO_THRESHOLD_MGDL },
        hyperEvents = vs.count { it > HYPER_THRESHOLD_MGDL },
    )
}
