package com.example.test_tablet

import com.example.test_tablet.data.vwing.DjiFlightLog
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Kiểm thử bộ đọc wire-format Protobuf dùng để giải mã flight log DJI (xem DjiFlightLog.kt).
 * Vì chưa có file log thật, dữ liệu kiểm thử được tự mã hóa tay theo đúng chuẩn Protobuf (varint,
 * fixed64, length-delimited) để xác nhận [DjiFlightLog.parse]/[DjiFlightLog.decode] đọc đúng cấu
 * trúc — KHÔNG kiểm thử việc ánh xạ tên trường con (chưa xác nhận, xem ghi chú trong file nguồn).
 */
class DjiFlightLogTest {

    private fun varint(v: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var x = v
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) { out.write(b); break } else out.write(b or 0x80)
        }
        return out.toByteArray()
    }

    private fun tag(fieldNo: Int, wireType: Int) = varint(((fieldNo.toLong() shl 3) or wireType.toLong()))
    private fun fieldVarint(fieldNo: Int, v: Long) = tag(fieldNo, 0) + varint(v)
    private fun fieldDouble(fieldNo: Int, v: Double): ByteArray {
        val bits = v.toRawBits()
        val b = ByteArray(8)
        for (i in 0 until 8) b[i] = ((bits ushr (8 * i)) and 0xFF).toByte()
        return tag(fieldNo, 1) + b
    }
    private fun fieldMessage(fieldNo: Int, payload: ByteArray) = tag(fieldNo, 2) + varint(payload.size.toLong()) + payload
    private fun fieldString(fieldNo: Int, s: String) = fieldMessage(fieldNo, s.toByteArray())

    @Test
    fun `parse reads a flat varint and fixed64 message`() {
        val bytes = fieldVarint(3, 42L) + fieldDouble(5, 12.5)
        val msg = DjiFlightLog.parse(bytes)
        assertEquals(42L, msg.varint(3))
        assertEquals(12.5, msg.fixed64Double(5)!!, 1e-9)
        assertEquals(listOf(3, 5), msg.tagsPresent)
    }

    @Test
    fun `parse reads nested submessages by field number`() {
        // giống FrameTimeState thật: field 1 = flightControllerState, field 7 = agriculturalData (đã xác nhận)
        val fcs = fieldDouble(1, 10.123) // ví dụ một trường con double bất kỳ trong flightControllerState
        val ag = fieldVarint(1, 7L)      // ví dụ một trường con varint bất kỳ trong agriculturalData
        val frame = fieldMessage(1, fcs) + fieldMessage(7, ag)

        val f = DjiFlightLog.Frame(DjiFlightLog.parse(frame))
        assertNotNull(f.flightControllerStateRaw)
        assertNotNull(f.agriculturalDataRaw)
        assertEquals(10.123, f.flightControllerStateRaw!!.fixed64Double(1)!!, 1e-9)
        assertEquals(7L, f.agriculturalDataRaw!!.varint(1))
    }

    @Test
    fun `decode wraps repeated frames under field 1`() {
        // Mô phỏng FrameTimeStates { repeated FrameTimeState frameTimeStates = 1 }: mỗi FrameTimeState
        // ở đây chỉ chứa một trường con double tùy ý (field 1) để phân biệt 2 khung với nhau.
        val frame1 = fieldMessage(1, fieldDouble(1, 1.0))
        val frame2 = fieldMessage(1, fieldDouble(1, 2.0))
        val top = fieldMessage(1, frame1) + fieldMessage(1, frame2)
        val decoded = DjiFlightLog.decode(top)
        assertTrue(decoded.wellFormed)
        assertEquals(2, decoded.frames.size)
    }

    @Test
    fun `decode falls back to single frame when not wrapped`() {
        val single = fieldDouble(2, 99.0) + fieldVarint(3, 1L)
        val decoded = DjiFlightLog.decode(single)
        assertTrue(decoded.wellFormed)
        assertEquals(1, decoded.frames.size)
        assertEquals(99.0, decoded.frames[0].raw.fixed64Double(2)!!, 1e-9)
    }

    @Test
    fun `decode reports failure on garbage bytes`() {
        // byte đầu có continuation bit set liên tục tới hết -> varint không bao giờ kết thúc hợp lệ
        val garbage = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val decoded = DjiFlightLog.decode(garbage)
        assertFalse(decoded.wellFormed)
        assertTrue(decoded.error.isNotBlank())
    }

    @Test
    fun `doubleArray reads packed repeated doubles`() {
        // packed repeated double: tag(field,2) + len + 8*doubleBytes lặp lại
        val values = listOf(1.5, -2.25, 3.0)
        val raw = ByteArrayOutputStream()
        for (v in values) {
            val bits = v.toRawBits()
            for (i in 0 until 8) raw.write(((bits ushr (8 * i)) and 0xFF).toInt())
        }
        val msg = fieldMessage(9, raw.toByteArray())
        val m = DjiFlightLog.parse(msg)
        assertEquals(values, m.doubleArray(9))
    }

    @Test
    fun `string field decodes as UTF-8`() {
        val msg = fieldString(4, "flight-001")
        val m = DjiFlightLog.parse(msg)
        assertEquals("flight-001", m.string(4))
    }

    @Test
    fun `dumpFields lists every field number present`() {
        val msg = fieldVarint(1, 1L) + fieldDouble(2, 2.0) + fieldString(3, "x")
        val m = DjiFlightLog.parse(msg)
        val dump = DjiFlightLog.dumpFields(m)
        assertEquals(3, dump.size)
        assertTrue(dump[0].startsWith("field 1:"))
        assertTrue(dump[2].contains("string=\"x\""))
    }
}
