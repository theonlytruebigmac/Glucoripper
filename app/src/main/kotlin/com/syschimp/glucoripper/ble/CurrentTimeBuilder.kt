package com.syschimp.glucoripper.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime

/**
 * Serialize a [ZonedDateTime] into a Bluetooth Current Time Service payload (0x2A2B).
 *
 * Layout (10 bytes):
 *   [0..1] year (uint16 LE)
 *   [2]    month 1..12
 *   [3]    day 1..31
 *   [4]    hour 0..23
 *   [5]    minute 0..59
 *   [6]    second 0..59
 *   [7]    day-of-week 1=Mon .. 7=Sun (0 = unknown)
 *   [8]    fractions256 (sub-second, 1/256 s)
 *   [9]    adjust reason bitmask (0x01 = manual change)
 */
object CurrentTimeBuilder {
    fun build(now: ZonedDateTime, adjustReason: Int = 0x01): ByteArray {
        val buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(now.year.coerceIn(1582, 9999).toShort())
        buf.put(now.monthValue.toByte())
        buf.put(now.dayOfMonth.toByte())
        buf.put(now.hour.toByte())
        buf.put(now.minute.toByte())
        buf.put(now.second.toByte())
        buf.put(now.dayOfWeek.value.toByte())
        buf.put(((now.nano / 1_000_000_000.0 * 256).toInt() and 0xFF).toByte())
        buf.put(adjustReason.toByte())
        return buf.array()
    }
}
