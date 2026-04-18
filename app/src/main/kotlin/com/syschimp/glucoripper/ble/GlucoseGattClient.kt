package com.syschimp.glucoripper.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Connects to a Bluetooth Glucose Profile device, reads stored records using RACP,
 * and returns them. Single-use — create a fresh instance per sync.
 */
@SuppressLint("MissingPermission")
class GlucoseGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val tag = "GlucoseGatt"

    private var gatt: BluetoothGatt? = null
    private val opMutex = Mutex()
    private var pendingConnection: CancellableContinuation<Unit>? = null
    private var pendingDiscover: CancellableContinuation<Unit>? = null
    private var pendingWrite: CancellableContinuation<Int>? = null
    private var pendingDescriptor: CancellableContinuation<Int>? = null
    private var pendingRead: CancellableContinuation<ByteArray>? = null
    private val racpIndications = Channel<ByteArray>(Channel.UNLIMITED)
    private val measurements = Channel<ByteArray>(Channel.UNLIMITED)
    private val contexts = Channel<ByteArray>(Channel.UNLIMITED)

    class BleException(message: String) : Exception(message)

    /**
     * Pull stored records. If [fromSequenceExclusive] is non-null, only records with
     * a sequence number strictly greater than it are fetched (delta sync).
     */
    suspend fun pullRecords(fromSequenceExclusive: Int?): List<GlucoseRecord> {
        require(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
        ) { "BLUETOOTH_CONNECT not granted" }
        try {
            connect()
            discoverServices()
            dumpServices()
            dumpDeviceInformation()
            setMeterClock()
            val glucoseSvc = gatt?.getService(GlucoseUuids.SERVICE)
                ?: throw BleException("Glucose Service (0x1808) not found on device")
            val measurement = glucoseSvc.getCharacteristic(GlucoseUuids.GLUCOSE_MEASUREMENT)
                ?: throw BleException("Glucose Measurement characteristic missing")
            val racp = glucoseSvc.getCharacteristic(GlucoseUuids.RACP)
                ?: throw BleException("RACP characteristic missing")
            val measurementContext = glucoseSvc.getCharacteristic(
                GlucoseUuids.GLUCOSE_MEASUREMENT_CONTEXT
            )

            readGlucoseFeature(glucoseSvc)
            enableCccd(measurement, indicate = false)
            if (measurementContext != null) {
                Log.i(tag, "Glucose Measurement Context characteristic present; subscribing")
                enableCccd(measurementContext, indicate = false)
            } else {
                Log.i(tag, "Glucose Measurement Context characteristic NOT present on this meter")
            }
            enableCccd(racp, indicate = true)

            val request = if (fromSequenceExclusive == null) {
                RacpClient.reportAll()
            } else {
                RacpClient.reportFrom(fromSequenceExclusive + 1)
            }
            writeCharacteristic(racp, request)

            return collectRecords()
        } finally {
            closeQuietly()
        }
    }

    private suspend fun connect() = withTimeout(20_000) {
        suspendCancellableCoroutine { cont ->
            pendingConnection = cont
            gatt = device.connectGatt(context, /* autoConnect = */ false, callback)
            cont.invokeOnCancellation { closeQuietly() }
        }
    }

    private suspend fun discoverServices() = withTimeout(15_000) {
        suspendCancellableCoroutine { cont ->
            pendingDiscover = cont
            val ok = gatt?.discoverServices() == true
            if (!ok) cont.resumeWithException(BleException("discoverServices() returned false"))
        }
    }

    private suspend fun enableCccd(
        characteristic: BluetoothGattCharacteristic,
        indicate: Boolean,
    ) = opMutex.withLock {
        val g = gatt ?: throw BleException("GATT closed")
        if (!g.setCharacteristicNotification(characteristic, true))
            throw BleException("setCharacteristicNotification failed for ${characteristic.uuid}")

        val cccd = characteristic.getDescriptor(GlucoseUuids.CCCD)
            ?: throw BleException("CCCD missing on ${characteristic.uuid}")
        val value = if (indicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        withTimeout(5_000) {
            suspendCancellableCoroutine { cont ->
                pendingDescriptor = cont
                val wrote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    (cccd.setValue(value) && g.writeDescriptor(cccd))
                }
                if (!wrote) cont.resumeWithException(BleException("writeDescriptor failed"))
            }
        }
    }

    private suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) = opMutex.withLock {
        val g = gatt ?: throw BleException("GATT closed")
        withTimeout(5_000) {
            suspendCancellableCoroutine { cont ->
                pendingWrite = cont
                val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(characteristic, value, writeType) ==
                            BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        characteristic.writeType = writeType
                        characteristic.value = value
                        g.writeCharacteristic(characteristic)
                    }
                }
                if (!ok) cont.resumeWithException(BleException("writeCharacteristic failed"))
            }
        }
    }

    private suspend fun collectRecords(): List<GlucoseRecord> {
        val records = mutableListOf<GlucoseRecord>()
        val contextBySeq = mutableMapOf<Int, GlucoseRecord.MealRelation?>()
        try {
            withTimeout(60_000) {
                while (true) {
                    val racpResult = kotlinx.coroutines.selects.select<RacpClient.Response?> {
                        racpIndications.onReceive { RacpClient.parse(it) }
                        measurements.onReceive { payload ->
                            runCatching { GlucoseMeasurementParser.parse(payload) }
                                .onSuccess { records += it }
                                .onFailure { Log.w(tag, "parse failure", it) }
                            null
                        }
                        contexts.onReceive { payload ->
                            runCatching { GlucoseMeasurementParser.parseContext(payload) }
                                .onSuccess { ctx ->
                                    contextBySeq[ctx.sequenceNumber] = ctx.mealRelation
                                }
                                .onFailure { Log.w(tag, "context parse failure", it) }
                            null
                        }
                    }
                    if (racpResult != null) {
                        when (racpResult) {
                            is RacpClient.Response.OperationResult -> {
                                if (racpResult.isSuccess || racpResult.isNoRecords) return@withTimeout
                                throw BleException("RACP error: code=${racpResult.responseCode}")
                            }
                            is RacpClient.Response.NumberOfRecords -> Unit
                            is RacpClient.Response.Unknown -> Unit
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            Log.w(tag, "RACP did not complete within timeout; returning partial set")
        }
        return records.map { r ->
            val meal = contextBySeq[r.sequenceNumber]
            if (meal != null) r.copy(mealRelation = meal) else r
        }
    }

    private fun dumpServices() {
        val g = gatt ?: return
        Log.i(tag, "=== GATT services for ${device.address} ===")
        g.services.forEach { svc ->
            Log.i(tag, "Service ${svc.uuid}")
            svc.characteristics.forEach { ch ->
                val props = buildList {
                    val p = ch.properties
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
                    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                }.joinToString("|")
                Log.i(tag, "  Characteristic ${ch.uuid} [$props]")
            }
        }
        Log.i(tag, "=== end service dump ===")
    }

    /**
     * Push the phone's wall-clock time to the meter's Current Time Service so the
     * next readings aren't stamped in the past/future. Silent no-op if CTS not present
     * or if the meter rejects the write (some meters make CTS read-only).
     */
    private suspend fun setMeterClock() {
        val g = gatt ?: return
        val cts = g.getService(GlucoseUuids.CURRENT_TIME_SERVICE) ?: return
        val ch = cts.getCharacteristic(GlucoseUuids.CURRENT_TIME) ?: return
        val isWritable = (ch.properties and
                (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
        if (!isWritable) {
            Log.i(tag, "CTS Current Time is not writable on this meter; skipping clock set")
            return
        }
        val payload = CurrentTimeBuilder.build(java.time.ZonedDateTime.now())
        runCatching { writeCharacteristic(ch, payload) }
            .onSuccess { Log.i(tag, "Pushed phone time to meter CTS") }
            .onFailure { Log.w(tag, "Failed to set meter clock via CTS", it) }
    }

    private suspend fun dumpDeviceInformation() {
        val g = gatt ?: return
        val dis = g.getService(GlucoseUuids.DEVICE_INFORMATION) ?: return
        listOf(
            "manufacturer" to GlucoseUuids.MANUFACTURER_NAME,
            "model" to GlucoseUuids.MODEL_NUMBER,
            "serial" to GlucoseUuids.SERIAL_NUMBER,
        ).forEach { (label, uuid) ->
            val ch = dis.getCharacteristic(uuid) ?: return@forEach
            runCatching {
                val bytes = readCharacteristic(ch)
                Log.i(tag, "DIS $label = ${bytes.toString(Charsets.UTF_8).trim()}")
            }.onFailure { Log.w(tag, "DIS $label read failed", it) }
        }
    }

    private suspend fun readGlucoseFeature(svc: android.bluetooth.BluetoothGattService) {
        val ch = svc.getCharacteristic(GlucoseUuids.GLUCOSE_FEATURE) ?: return
        runCatching {
            val bytes = readCharacteristic(ch)
            val v = if (bytes.size >= 2) (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) else 0
            Log.i(tag, "Glucose Feature bitmap = 0x%04x (raw=${bytes.toHex()})".format(v))
        }.onFailure { Log.w(tag, "Glucose Feature read failed", it) }
    }

    private suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): ByteArray = opMutex.withLock {
        val g = gatt ?: throw BleException("GATT closed")
        withTimeout(5_000) {
            suspendCancellableCoroutine { cont ->
                pendingRead = cont
                if (!g.readCharacteristic(characteristic)) {
                    cont.resumeWithException(BleException("readCharacteristic returned false"))
                }
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

    private fun closeQuietly() {
        try { gatt?.disconnect() } catch (_: Throwable) {}
        try { gatt?.close() } catch (_: Throwable) {}
        gatt = null
        pendingConnection?.takeIf { it.isActive }?.resumeWithException(BleException("closed"))
        pendingDiscover?.takeIf { it.isActive }?.resumeWithException(BleException("closed"))
        pendingWrite?.takeIf { it.isActive }?.resumeWithException(BleException("closed"))
        pendingDescriptor?.takeIf { it.isActive }?.resumeWithException(BleException("closed"))
        pendingRead?.takeIf { it.isActive }?.resumeWithException(BleException("closed"))
        racpIndications.close()
        measurements.close()
        contexts.close()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                pendingConnection?.takeIf { it.isActive }?.resume(Unit)
                pendingConnection = null
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val err = BleException("Disconnected (status=$status)")
                pendingConnection?.takeIf { it.isActive }?.resumeWithException(err)
                pendingConnection = null
                racpIndications.close(err)
                measurements.close(err)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingDiscover?.takeIf { it.isActive }?.resume(Unit)
            } else {
                pendingDiscover?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("discover failed $status"))
            }
            pendingDiscover = null
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            pendingDescriptor?.takeIf { it.isActive }?.resume(status)
            pendingDescriptor = null
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            pendingWrite?.takeIf { it.isActive }?.resume(status)
            pendingWrite = null
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingRead?.takeIf { it.isActive }?.resume(value.copyOf())
            } else {
                pendingRead?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("read failed status=$status"))
            }
            pendingRead = null
        }

        @Deprecated("Pre-Tiramisu overload")
        override fun onCharacteristicRead(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            @Suppress("DEPRECATION")
            val v = characteristic.value ?: ByteArray(0)
            onCharacteristicRead(g, characteristic, v, status)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val hex = value.joinToString(" ") { "%02x".format(it) }
            when (characteristic.uuid) {
                GlucoseUuids.GLUCOSE_MEASUREMENT -> {
                    Log.d(tag, "MEASUREMENT raw: $hex")
                    runCatching { GlucoseMeasurementParser.parse(value) }.onSuccess { r ->
                        Log.d(
                            tag,
                            "MEASUREMENT parsed: seq=${r.sequenceNumber} t=${r.time} " +
                                    "mgdl=${r.mgPerDl} type=${r.sampleType} loc=${r.sampleLocation} " +
                                    "status=0x%04x ctrlSolution=${r.isControlSolution}".format(r.sensorStatus)
                        )
                    }
                    measurements.trySend(value.copyOf())
                }
                GlucoseUuids.GLUCOSE_MEASUREMENT_CONTEXT -> {
                    Log.d(tag, "CONTEXT raw: $hex")
                    contexts.trySend(value.copyOf())
                }
                GlucoseUuids.RACP -> {
                    Log.d(tag, "RACP raw: $hex")
                    racpIndications.trySend(value.copyOf())
                }
                else -> Log.d(tag, "Notify ${characteristic.uuid}: $hex")
            }
        }

        @Deprecated("Pre-Tiramisu overload", ReplaceWith("onCharacteristicChanged(g, c, v)"))
        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onCharacteristicChanged(g, characteristic, value)
        }
    }
}
