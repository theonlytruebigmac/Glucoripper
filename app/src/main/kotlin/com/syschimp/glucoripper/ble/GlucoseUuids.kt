package com.syschimp.glucoripper.ble

import java.util.UUID

/** Bluetooth SIG assigned numbers for the Glucose Service (GLS). */
object GlucoseUuids {
    val SERVICE: UUID = uuid16(0x1808)
    val GLUCOSE_MEASUREMENT: UUID = uuid16(0x2A18)
    val GLUCOSE_MEASUREMENT_CONTEXT: UUID = uuid16(0x2A34)
    val GLUCOSE_FEATURE: UUID = uuid16(0x2A51)
    val RACP: UUID = uuid16(0x2A52)
    val CCCD: UUID = uuid16(0x2902)

    val DEVICE_INFORMATION: UUID = uuid16(0x180A)
    val MANUFACTURER_NAME: UUID = uuid16(0x2A29)
    val MODEL_NUMBER: UUID = uuid16(0x2A24)
    val SERIAL_NUMBER: UUID = uuid16(0x2A25)

    val CURRENT_TIME_SERVICE: UUID = uuid16(0x1805)
    val CURRENT_TIME: UUID = uuid16(0x2A2B)

    private fun uuid16(short: Int): UUID =
        UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(short and 0xFFFF))
}
