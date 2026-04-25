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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import timber.log.Timber
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

    data class PullResult(
        val records: List<GlucoseRecord>,
        /** Total records the meter currently holds. Used to detect uint16 sequence rollover. */
        val totalCount: Int,
        /** True when [previousTotalCount] was supplied and the meter now holds fewer
         *  records than last time, implying it wrapped (or was reset). */
        val rolledOver: Boolean,
    )

    /**
     * Pull stored records. If [fromSequenceExclusive] is non-null, only records with
     * a sequence number strictly greater than it are fetched (delta sync).
     *
     * If [previousTotalCount] is supplied, the meter's total record count is queried
     * first; when the new total is lower, a rollover is assumed and a full sweep is
     * issued instead of the (now-broken) delta query.
     */
    suspend fun pullRecords(
        fromSequenceExclusive: Int?,
        previousTotalCount: Int? = null,
    ): PullResult {
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
                Timber.i("Glucose Measurement Context characteristic present; subscribing")
                enableCccd(measurementContext, indicate = false)
            } else {
                Timber.i("Glucose Measurement Context characteristic NOT present on this meter")
            }
            enableCccd(racp, indicate = true)

            writeCharacteristic(racp, RacpClient.numberOfRecordsAll())
            val totalCount = awaitNumberOfRecords()
            val rolledOver = previousTotalCount != null && totalCount < previousTotalCount
            if (rolledOver) {
                Timber.w(
                    "Meter total dropped from %d to %d; treating as sequence rollover and full-syncing",
                    previousTotalCount, totalCount,
                )
            }

            val request = if (fromSequenceExclusive == null || rolledOver) {
                RacpClient.reportAll()
            } else {
                RacpClient.reportFrom(fromSequenceExclusive + 1)
            }
            writeCharacteristic(racp, request)
            val records = collectRecords()
            return PullResult(records, totalCount, rolledOver)
        } finally {
            closeQuietly()
        }
    }

    private suspend fun awaitNumberOfRecords(): Int = withTimeout(15_000) {
        while (true) {
            val payload = racpIndications.receive()
            when (val r = RacpClient.parse(payload)) {
                is RacpClient.Response.NumberOfRecords -> return@withTimeout r.count
                is RacpClient.Response.OperationResult ->
                    throw BleException("Number-of-records query failed: code=${r.responseCode}")
                is RacpClient.Response.Unknown -> Unit
            }
        }
        @Suppress("UNREACHABLE_CODE") -1
    }

    private suspend fun connect() = opMutex.withLock {
        check(pendingConnection == null) { "connect() already in flight" }
        withTimeout(20_000) {
            suspendCancellableCoroutine { cont ->
                pendingConnection = cont
                gatt = device.connectGatt(context, /* autoConnect = */ false, callback)
                cont.invokeOnCancellation { closeQuietly() }
            }
        }
    }

    private suspend fun discoverServices() = opMutex.withLock {
        check(pendingDiscover == null) { "discoverServices() already in flight" }
        withTimeout(15_000) {
            suspendCancellableCoroutine { cont ->
                pendingDiscover = cont
                val ok = gatt?.discoverServices() == true
                if (!ok) cont.resumeWithException(BleException("discoverServices() returned false"))
            }
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
                                .onFailure { Timber.w(it, "parse failure") }
                            null
                        }
                        contexts.onReceive { payload ->
                            runCatching { GlucoseMeasurementParser.parseContext(payload) }
                                .onSuccess { ctx ->
                                    contextBySeq[ctx.sequenceNumber] = ctx.mealRelation
                                }
                                .onFailure { Timber.w(it, "context parse failure") }
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
            Timber.w("RACP did not complete within timeout; returning partial set")
        }
        return records.map { r ->
            val meal = contextBySeq[r.sequenceNumber]
            if (meal != null) r.copy(mealRelation = meal) else r
        }
    }

    private fun dumpServices() {
        val g = gatt ?: return
        Timber.i("=== GATT services for ${device.address} ===")
        g.services.forEach { svc ->
            Timber.i("Service ${svc.uuid}")
            svc.characteristics.forEach { ch ->
                val props = buildList {
                    val p = ch.properties
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
                    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                }.joinToString("|")
                Timber.i("  Characteristic ${ch.uuid} [$props]")
            }
        }
        Timber.i("=== end service dump ===")
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
            Timber.i("CTS Current Time is not writable on this meter; skipping clock set")
            return
        }
        val payload = CurrentTimeBuilder.build(java.time.ZonedDateTime.now())
        runCatching { writeCharacteristic(ch, payload) }
            .onSuccess { Timber.i("Pushed phone time to meter CTS") }
            .onFailure { Timber.w(it, "Failed to set meter clock via CTS") }
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
                Timber.i("DIS $label = ${bytes.toString(Charsets.UTF_8).trim()}")
            }.onFailure { Timber.w(it, "DIS $label read failed") }
        }
    }

    private suspend fun readGlucoseFeature(svc: android.bluetooth.BluetoothGattService) {
        val ch = svc.getCharacteristic(GlucoseUuids.GLUCOSE_FEATURE) ?: return
        runCatching {
            val bytes = readCharacteristic(ch)
            val v = if (bytes.size >= 2) (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) else 0
            Timber.i("Glucose Feature bitmap = 0x%04x (raw=${bytes.toHex()})".format(v))
        }.onFailure { Timber.w(it, "Glucose Feature read failed") }
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
                val cont = pendingConnection
                pendingConnection = null
                cont?.takeIf { it.isActive }?.resume(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val err = BleException("Disconnected (status=$status)")
                val connCont = pendingConnection
                pendingConnection = null
                connCont?.takeIf { it.isActive }?.resumeWithException(err)
                racpIndications.close(err)
                measurements.close(err)
                contexts.close(err)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val cont = pendingDiscover
            pendingDiscover = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cont?.takeIf { it.isActive }?.resume(Unit)
            } else {
                cont?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("discover failed $status"))
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            val cont = pendingDescriptor
            pendingDescriptor = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cont?.takeIf { it.isActive }?.resume(status)
            } else {
                cont?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("descriptor write failed status=$status"))
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            val cont = pendingWrite
            pendingWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cont?.takeIf { it.isActive }?.resume(status)
            } else {
                cont?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("write failed status=$status"))
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val cont = pendingRead
            pendingRead = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cont?.takeIf { it.isActive }?.resume(value.copyOf())
            } else {
                cont?.takeIf { it.isActive }
                    ?.resumeWithException(BleException("read failed status=$status"))
            }
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
                    Timber.d("MEASUREMENT raw: $hex")
                    runCatching { GlucoseMeasurementParser.parse(value) }.onSuccess { r ->
                        Timber.d(
                            "MEASUREMENT parsed: seq=${r.sequenceNumber} t=${r.time} " +
                                    "mgdl=${r.mgPerDl} type=${r.sampleType} loc=${r.sampleLocation} " +
                                    "status=0x%04x ctrlSolution=${r.isControlSolution}".format(r.sensorStatus)
                        )
                    }
                    measurements.trySend(value.copyOf())
                }
                GlucoseUuids.GLUCOSE_MEASUREMENT_CONTEXT -> {
                    Timber.d("CONTEXT raw: $hex")
                    contexts.trySend(value.copyOf())
                }
                GlucoseUuids.RACP -> {
                    Timber.d("RACP raw: $hex")
                    racpIndications.trySend(value.copyOf())
                }
                else -> Timber.d("Notify ${characteristic.uuid}: $hex")
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
