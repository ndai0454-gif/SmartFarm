package com.example.test_tablet.data.vwing

import java.text.Normalizer

enum class RowStatus(val label: String) { OK("Sẽ thêm"), DUP_EXISTING("Trùng cây đã có"), DUP_FILE("Trùng trong tệp"), ERROR("Lỗi") }

class BulkRow(
    val line: Int, val code: String, val zone: String, val variety: String, val year: Int?, val fruits: Int?,
    val pos: GeoPoint?, val status: RowStatus, val message: String,
)

class BulkPreview(val rows: List<BulkRow>, val fatal: String?) {
    val addable get() = rows.filter { it.status == RowStatus.OK }
}

/** Nhập hàng loạt từ bảng tính dán vào (CSV/TSV; copy từ Excel là TSV). Luôn xem trước và kiểm tra trùng trước khi thêm. */
object BulkImport {
    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()

    private val aliases = mapOf(
        "code" to listOf("ma", "ma cay", "code", "id", "cay", "tree"),
        "zone" to listOf("khu", "khu vuc", "zone"),
        "fruits" to listOf("trai", "so trai", "fruits"),
        "variety" to listOf("giong", "variety"),
        "year" to listOf("nam trong", "nam", "year", "planted"),
        "lat" to listOf("vi do", "lat", "latitude"),
        "lng" to listOf("kinh do", "lng", "lon", "long", "longitude"),
    )

    private fun splitLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false
        for (c in line) when {
            c == '"' -> q = !q
            c == delim && !q -> { out += sb.toString().trim(); sb.clear() }
            else -> sb.append(c)
        }
        out += sb.toString().trim()
        return out
    }

    fun parse(text: String, d: GardenData): BulkPreview {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return BulkPreview(emptyList(), "Cần một dòng tiêu đề và ít nhất một dòng dữ liệu.")
        val head = lines.first()
        val delim = when {
            head.contains('\t') -> '\t'
            head.count { it == ';' } > head.count { it == ',' } -> ';'
            else -> ','
        }
        val cols = splitLine(head, delim).map(::plain)
        fun idx(key: String) = cols.indexOfFirst { it in aliases.getValue(key) }
        val iCode = idx("code")
        if (iCode < 0) return BulkPreview(emptyList(), "Không thấy cột mã cây. Tiêu đề cần có một trong: ${aliases.getValue("code").joinToString(", ")}.")
        val iZone = idx("zone"); val iFruits = idx("fruits"); val iVar = idx("variety"); val iYear = idx("year"); val iLat = idx("lat"); val iLng = idx("lng")

        val seen = mutableSetOf<String>()
        val rows = mutableListOf<BulkRow>()
        lines.drop(1).forEachIndexed { n, raw ->
            val line = n + 2
            val c = splitLine(raw, delim)
            fun cell(i: Int) = if (i >= 0 && i < c.size) c[i].trim() else ""
            val code = cell(iCode); val zone = cell(iZone).ifBlank { "Chưa phân khu" }
            var err: String? = null
            val fruits = cell(iFruits).takeIf { it.isNotEmpty() }?.let { s -> s.toIntOrNull()?.takeIf { it >= 0 } ?: run { err = "Số trái \"$s\" không hợp lệ."; null } }
            val year = cell(iYear).takeIf { it.isNotEmpty() }?.let { s -> s.toIntOrNull() ?: run { err = "Năm trồng \"$s\" không hợp lệ."; null } }
            var pos: GeoPoint? = null
            if (cell(iLat).isNotEmpty() || cell(iLng).isNotEmpty()) {
                val lat = cell(iLat).replace(',', '.').toDoubleOrNull(); val lng = cell(iLng).replace(',', '.').toDoubleOrNull()
                if (lat == null || lng == null || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180)
                    err = "Tọa độ không hợp lệ (vĩ độ=${cell(iLat)}, kinh độ=${cell(iLng)})."
                else pos = GeoPoint(lat, lng)
            }
            val key = normalizeCode(code) ?: code.uppercase()
            val (st, msg) = when {
                code.isBlank() -> RowStatus.ERROR to "Thiếu mã cây."
                err != null -> RowStatus.ERROR to err!!
                d.treeByCode(code) != null -> RowStatus.DUP_EXISTING to "Đã có cây ${code}; bỏ qua."
                !seen.add(key) -> RowStatus.DUP_FILE to "Mã xuất hiện nhiều lần trong tệp; bỏ qua."
                else -> RowStatus.OK to ""
            }
            rows += BulkRow(line, code, zone, cell(iVar), year, fruits, pos, st, msg)
        }
        return BulkPreview(rows, null)
    }
}
