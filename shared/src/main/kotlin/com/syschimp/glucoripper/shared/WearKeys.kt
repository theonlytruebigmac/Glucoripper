package com.syschimp.glucoripper.shared

/**
 * Single source of truth for the DataClient wire format between the phone app
 * and the watch app. Any key added here must be serialized on the phone side
 * ([com.syschimp.glucoripper.wear.WearBridge]) and read back on the watch side
 * ([com.syschimp.glucoripper.wear.data.GlucoseListenerService]).
 */
object WearKeys {
    const val PATH_LATEST = "/glucose/latest"

    const val KEY_TIME = "time"
    const val KEY_MGDL = "mgDl"
    const val KEY_MEAL = "meal"
    const val KEY_LOW = "targetLow"
    const val KEY_HIGH = "targetHigh"
    const val KEY_UNIT = "unit"
    const val KEY_WIN_TIMES = "winTimes"
    const val KEY_WIN_VALUES = "winMgDls"
    const val KEY_WIN_MEALS = "winMeals"
    const val KEY_LAST_SYNC = "lastSync"
    const val KEY_FASTING_LOW = "fastingLow"
    const val KEY_FASTING_HIGH = "fastingHigh"
    const val KEY_PRE_MEAL_LOW = "preMealLow"
    const val KEY_PRE_MEAL_HIGH = "preMealHigh"
    const val KEY_POST_MEAL_LOW = "postMealLow"
    const val KEY_POST_MEAL_HIGH = "postMealHigh"
}
