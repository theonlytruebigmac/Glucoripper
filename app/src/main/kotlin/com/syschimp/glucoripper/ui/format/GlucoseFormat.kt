package com.syschimp.glucoripper.ui.format

import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.mgDlToMmol

fun formatGlucose(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
    GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl.mgDlToMmol())
}

fun unitLabel(unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "mg/dL"
    GlucoseUnit.MMOL_PER_L -> "mmol/L"
}
