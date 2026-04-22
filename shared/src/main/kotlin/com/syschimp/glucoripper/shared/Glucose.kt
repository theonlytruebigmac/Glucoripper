package com.syschimp.glucoripper.shared

/** 1 mmol/L of glucose = 18.0156 mg/dL. Single source of truth for unit conversion. */
const val MGDL_PER_MMOL = 18.0156

fun Double.mgDlToMmol(): Double = this / MGDL_PER_MMOL
fun Double.mmolToMgDl(): Double = this * MGDL_PER_MMOL

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
