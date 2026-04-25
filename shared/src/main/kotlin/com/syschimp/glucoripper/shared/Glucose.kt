package com.syschimp.glucoripper.shared

/** 1 mmol/L of glucose = 18.0156 mg/dL. Single source of truth for unit conversion. */
const val MGDL_PER_MMOL = 18.0156

fun Double.mgDlToMmol(): Double = this / MGDL_PER_MMOL
fun Double.mmolToMgDl(): Double = this * MGDL_PER_MMOL

/**
 * Returns the mg/dL cutoff that separates the "Elevated" band from the "High" band.
 * Sites that previously hard-coded 180 collapsed the elevated band whenever the
 * user's [highTarget] exceeded 180; we floor at 180 to keep the legacy behavior
 * for typical configurations and require at least 30 mg/dL of elevated headroom
 * above the user's high so the band is always visible.
 */
fun glucoseHighAlarmCutoff(highTarget: Double): Double =
    maxOf(180.0, highTarget + 30.0)

/**
 * Strongly-typed glucose value. Internally stores mg/dL; exposes mmol/L as a
 * derived property so callers never have to remember which variant they're
 * holding. Use this at layer boundaries where Double mg/dL and Double mmol/L
 * would otherwise be ambiguous.
 */
@JvmInline
value class Glucose(val mgDl: Double) {
    val mmolPerL: Double get() = mgDl * (1.0 / MGDL_PER_MMOL)

    operator fun compareTo(other: Glucose): Int = mgDl.compareTo(other.mgDl)

    companion object {
        val Zero = Glucose(0.0)
        fun fromMmol(mmol: Double) = Glucose(mmol * MGDL_PER_MMOL)
    }
}
