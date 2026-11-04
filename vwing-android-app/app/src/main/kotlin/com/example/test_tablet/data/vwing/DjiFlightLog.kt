package com.example.test_tablet.data.vwing

/**
 * Đọc file flight log nhị phân xuất ra từ DJI Agras/SmartFarm (định dạng Protobuf).
 *
 * Cấu trúc/tên trường (DJIFRProtoAG, DJIFRProtoSummary) được dựng lại bằng cách dịch ngược app
 * DJI Agras (com.dji.agflow 4.6.13, bundle React Native/Hermes) — xem hai file .proto tham khảo
 * đi kèm ghi chú repo. Trong đó **chỉ 2 số field (tag) ở FrameTimeState được xác nhận chắc chắn**
 * bằng cách đọc trực tiếp mã nguồn decode() gốc của DJI: field 1 = flightControllerState,
 * field 7 = agriculturalData. Số field của các trường con bên trong (độ cao, GPS, tốc độ phun…)
 * CHƯA được xác nhận bằng file thật.
 *
 * Vì vậy bộ đọc này KHÔNG đoán tên trường con — [ProtoReader] chỉ giải mã đúng cấu trúc
 * wire-format Protobuf (luôn đúng, không phụ thuộc việc đoán tag), còn [DjiFlightLog] trả về một
 * bản "dump thô" theo số field để người dùng đối chiếu với file log thật rồi tự khai báo ánh xạ
 * đúng tên trường (xem ghi chú ở cuối file). Không suy diễn/gán nhãn trường khi chưa có bằng chứng.
 */
object DjiFlightLog {

    // ---------- bộ đọc wire-format Protobuf (đúng với chuẩn Protobuf, không phụ thuộc schema) ----------

    class ParseException(message: String) : Exception(message)

    sealed class WireValue {
        data class Varint(val value: Long) : WireValue()
        data class Fixed64(val bits: Long) : WireValue()
        data class LenDelim(val data: ByteArray) : WireValue()
        data class Fixed32(val bits: Int) : WireValue()
    }

    /** Một message đã giải mã ở mức wire: số field (tag) -> danh sách giá trị (lặp lại nếu field repeated). */
    class RawMessage(val fields: Map<Int, List<WireValue>>) {
        fun varint(tag: Int): Long? = (fields[tag]?.lastOrNull() as? WireValue.Varint)?.value
        fun bool(tag: Int): Boolean? = varint(tag)?.let { it != 0L }
        fun fixed64Double(tag: Int): Double? = (fields[tag]?.lastOrNull() as? WireValue.Fixed64)?.bits?.let(Double::fromBits)
        fun fixed32Float(tag: Int): Float? = (fields[tag]?.lastOrNull() as? WireValue.Fixed32)?.bits?.let(Float::fromBits)
        fun bytes(tag: Int): ByteArray? = (fields[tag]?.lastOrNull() as? WireValue.LenDelim)?.data
        fun string(tag: Int): String? = bytes(tag)?.toString(Charsets.UTF_8)
        fun message(tag: Int): RawMessage? = bytes(tag)?.let { parse(it) }
        fun repeatedMessages(tag: Int): List<RawMessage> =
            (fields[tag] ?: emptyList()).filterIsInstance<WireValue.LenDelim>().map { parse(it.data) }

        /** Mảng double: hỗ trợ cả packed (một LenDelim chứa nhiều double liên tiếp) và lặp fixed64 rời. */
        fun doubleArray(tag: Int): List<Double> {
            val vals = fields[tag] ?: return emptyList()
            val out = mutableListOf<Double>()
            for (v in vals) when (v) {
                is WireValue.Fixed64 -> out += Double.fromBits(v.bits)
                is WireValue.LenDelim -> {
                    var i = 0
                    while (i + 8 <= v.data.size) { out += Double.fromBits(readLeLong(v.data, i)); i += 8 }
                }
                else -> {}
            }
            return out
        }

        /** Tất cả số field có mặt, theo thứ tự xuất hiện lần đầu — để dò cấu trúc file chưa biết schema. */
        val tagsPresent: List<Int> get() = fields.keys.sorted()
    }

    private fun readLeLong(b: ByteArray, off: Int): Long {
        var r = 0L
        for (i in 0 until 8) r = r or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        return r
    }

    /** Giải mã một message Protobuf từ bytes thô (không bọc length — dùng cho submessage đã length-delimited). */
    fun parse(data: ByteArray): RawMessage {
        val fields = LinkedHashMap<Int, MutableList<WireValue>>()
        var pos = 0
        fun readVarint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                if (pos >= data.size) throw ParseException("Hết dữ liệu khi đọc varint (lệch offset hoặc không phải Protobuf hợp lệ).")
                val b = data[pos].toInt() and 0xFF; pos++
                result = result or ((b.toLong() and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 63) throw ParseException("Varint quá dài (> 64 bit).")
            }
            return result
        }
        while (pos < data.size) {
            val tagByte = readVarint()
            val fieldNo = (tagByte ushr 3).toInt()
            val wireType = (tagByte and 0x7).toInt()
            val value: WireValue = when (wireType) {
                0 -> WireValue.Varint(readVarint()) // varint: int32/int64/uint/bool/enum
                1 -> { // fixed64: double/sfixed64/fixed64
                    if (pos + 8 > data.size) throw ParseException("Hết dữ liệu khi đọc fixed64 (field $fieldNo).")
                    val v = readLeLong(data, pos); pos += 8; WireValue.Fixed64(v)
                }
                2 -> { // length-delimited: string/bytes/message/packed-repeated
                    val len = readVarint().toInt()
                    if (len < 0 || pos + len > data.size) throw ParseException("Độ dài không hợp lệ cho field $fieldNo (len=$len).")
                    val slice = data.copyOfRange(pos, pos + len); pos += len; WireValue.LenDelim(slice)
                }
                5 -> { // fixed32: float/sfixed32/fixed32
                    if (pos + 4 > data.size) throw ParseException("Hết dữ liệu khi đọc fixed32 (field $fieldNo).")
                    var v = 0; for (i in 0 until 4) v = v or ((data[pos + i].toInt() and 0xFF) shl (8 * i)); pos += 4
                    WireValue.Fixed32(v)
                }
                else -> throw ParseException("Wire type $wireType chưa hỗ trợ (field $fieldNo) — có thể không phải Protobuf hoặc file hỏng.")
            }
            fields.getOrPut(fieldNo) { mutableListOf() }.add(value)
        }
        return RawMessage(fields)
    }

    // ---------- lớp đọc theo schema DJI đã biết một phần ----------

    /** Một khung (frame) đã tách theo 2 field đã xác nhận của FrameTimeState. */
    class Frame(val raw: RawMessage) {
        /** DJIFRProtoAG.FlightControllerState — field 1, đã xác nhận. Trường con bên trong chưa xác nhận số tag. */
        val flightControllerStateRaw: RawMessage? get() = raw.message(1)

        /** DJIFRProtoAG.AgriculturalData — field 7, đã xác nhận. Trường con bên trong chưa xác nhận số tag. */
        val agriculturalDataRaw: RawMessage? get() = raw.message(7)
    }

    class DecodedLog(
        val frames: List<Frame>,
        /** true nếu toàn bộ byte của file đã được giải mã hết thành các message hợp lệ (không nhất thiết đúng nghĩa). */
        val wellFormed: Boolean,
        val error: String = "",
    )

    /**
     * Giải mã file flight log. Thử theo 2 cách (vì chưa xác nhận tag của message FrameTimeStates bọc ngoài):
     *  1) Toàn file là một message FrameTimeStates { repeated FrameTimeState = field 1 } — nhiều khung.
     *  2) Toàn file là đúng một FrameTimeState trần (không bọc ngoài) — một khung.
     * Chọn cách cho nhiều khung hợp lệ hơn; nếu cả hai đều ra 0 khung thì coi là lỗi.
     */
    fun decode(data: ByteArray): DecodedLog {
        val attempts = mutableListOf<Pair<List<Frame>, String>>()

        runCatching {
            val top = parse(data)
            val inner = top.repeatedMessages(1)
            if (inner.isNotEmpty()) attempts += inner.map(::Frame) to "wrapped(field 1)"
        }

        runCatching {
            val top = parse(data)
            if (top.fields.isNotEmpty()) attempts += listOf(Frame(top)) to "single-frame"
        }

        if (attempts.isEmpty()) {
            val err = runCatching { parse(data) }.exceptionOrNull()?.message ?: "Không giải mã được: dữ liệu không đúng cấu trúc Protobuf."
            return DecodedLog(emptyList(), wellFormed = false, error = err)
        }
        val best = attempts.maxByOrNull { it.first.size }!!
        return DecodedLog(best.first, wellFormed = true, error = if (attempts.size > 1) "" else "Dùng cách đọc: ${best.second}")
    }

    /**
     * Bản dump thô một khung để hiển thị debug (so khớp bằng tay với file log thật): mỗi field hiển thị
     * kiểu wire + giá trị "đoán hợp lý" (double nếu là fixed64, chuỗi bit nếu là message con…). Dùng để
     * người dùng tự đối chiếu rồi khai báo ánh xạ tên trường đúng — KHÔNG coi đây là tên trường đã xác nhận.
     */
    fun dumpFields(m: RawMessage, maxDepth: Int = 2): List<String> = m.fields.entries.sortedBy { it.key }.map { (tag, values) ->
        val sample = values.first()
        val desc = when (sample) {
            is WireValue.Varint -> "varint=${sample.value}" + (values.takeIf { it.size > 1 }?.let { " (×${it.size})" } ?: "")
            is WireValue.Fixed64 -> "double=${Double.fromBits(sample.bits)}"
            is WireValue.Fixed32 -> "float=${Float.fromBits(sample.bits)}"
            is WireValue.LenDelim -> {
                val asMsg = if (maxDepth > 0) runCatching { parse(sample.data) }.getOrNull() else null
                when {
                    asMsg != null && asMsg.fields.isNotEmpty() -> "message{${asMsg.tagsPresent.joinToString(",")}} (${sample.data.size}B)"
                    sample.data.all { it in 32..126 } && sample.data.isNotEmpty() -> "string=\"${sample.data.toString(Charsets.UTF_8)}\""
                    else -> "bytes(${sample.data.size}B)"
                }
            }
        }
        "field $tag: $desc"
    }
}

/*
 * Cách hoàn thiện ánh xạ tên trường khi có file log thật từ DJI SmartFarm/Agras:
 * 1) decode(bytes).frames[i].flightControllerStateRaw / agriculturalDataRaw
 * 2) DjiFlightLog.dumpFields(...) trên từng message con, so khớp giá trị (vĩ độ/kinh độ trong
 *    khoảng hợp lý, tốc độ phun biến đổi theo thời gian…) với số field hiển thị.
 * 3) Ghi lại ánh xạ xác nhận được vào DJIFRProtoAG.proto (đã có trong repo) và nâng cấp lớp
 *    [DjiFlightLog.Frame] để đọc theo tên field tường minh thay vì RawMessage.
 */
