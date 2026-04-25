package com.syschimp.glucoripper.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Record Access Control Point (RACP) request/response builder + parser. */
object RacpClient {
    const val OP_REPORT_STORED_RECORDS = 0x01
    const val OP_REPORT_NUMBER_OF_RECORDS = 0x04
    const val OP_RESPONSE_NUMBER_OF_RECORDS = 0x05
    const val OP_RESPONSE_CODE = 0x06

    const val OPERATOR_ALL = 0x01
    const val OPERATOR_GREATER_THAN_OR_EQUAL = 0x03

    // Filter type for the "greater than or equal to" operator.
    const val FILTER_SEQUENCE_NUMBER = 0x01

    const val RESPONSE_SUCCESS = 0x01
    const val RESPONSE_NO_RECORDS = 0x06

    /** Build "report all stored records". */
    fun reportAll(): ByteArray =
        byteArrayOf(OP_REPORT_STORED_RECORDS.toByte(), OPERATOR_ALL.toByte())

    /** Build "report number of stored records (all)" — used to detect rollover. */
    fun numberOfRecordsAll(): ByteArray =
        byteArrayOf(OP_REPORT_NUMBER_OF_RECORDS.toByte(), OPERATOR_ALL.toByte())

    /** Build "report stored records with sequence number >= minSequence". */
    fun reportFrom(minSequence: Int): ByteArray {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(OP_REPORT_STORED_RECORDS.toByte())
        buf.put(OPERATOR_GREATER_THAN_OR_EQUAL.toByte())
        buf.put(FILTER_SEQUENCE_NUMBER.toByte())
        buf.putShort((minSequence and 0xFFFF).toShort())
        return buf.array()
    }

    sealed interface Response {
        data class NumberOfRecords(val count: Int) : Response
        data class OperationResult(val requestOpcode: Int, val responseCode: Int) : Response {
            val isSuccess get() = responseCode == RESPONSE_SUCCESS
            val isNoRecords get() = responseCode == RESPONSE_NO_RECORDS
        }
        data class Unknown(val opcode: Int) : Response
    }

    fun parse(payload: ByteArray): Response {
        if (payload.size < 2) return Response.Unknown(-1)
        val opcode = payload[0].toInt() and 0xFF
        return when (opcode) {
            OP_RESPONSE_NUMBER_OF_RECORDS -> {
                val count = if (payload.size >= 4) {
                    ((payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8))
                } else 0
                Response.NumberOfRecords(count)
            }
            OP_RESPONSE_CODE -> {
                val reqOp = if (payload.size >= 3) payload[2].toInt() and 0xFF else 0
                val resp = if (payload.size >= 4) payload[3].toInt() and 0xFF else 0
                Response.OperationResult(reqOp, resp)
            }
            else -> Response.Unknown(opcode)
        }
    }
}
