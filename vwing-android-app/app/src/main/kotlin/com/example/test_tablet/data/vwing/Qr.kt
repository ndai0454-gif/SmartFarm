package com.example.test_tablet.data.vwing

import java.util.Locale

/**
 * Nội dung mã QR của cây. Dùng ID cố định (hoặc mã cây + thông số khoảng cách 8×8m / 3×3m) nên đổi mã cây vẫn quét ra đúng hồ sơ.
 * Hỗ trợ cả định dạng chuẩn `vwing://tree/<treeId>` và định dạng mở rộng từ Bản đồ Khoanh vùng:
 * `vwing://tree/<codeOrId>?code=SR-001&crop=DURIAN&spacing=8x8m&unit_m2=64&row=1&col=1&lat=...&lng=...`
 */
object Qr {
    private const val PREFIX = "vwing://tree/"

    fun payloadFor(tree: Tree): String = PREFIX + tree.id

    fun spacingPayloadFor(
        code: String,
        cropType: String,
        spacingM: Int,
        row: Int,
        col: Int,
        lat: Double,
        lng: Double,
        areaM2: Double,
        totalTrees: Int,
        treeId: String = code,
    ): String {
        val unitM2 = spacingM * spacingM
        return String.format(
            Locale.US,
            "vwing://tree/%s?id=%s&code=%s&crop=%s&spacing=%dx%dm&unit_m2=%d&row=%d&col=%d&lat=%.6f&lng=%.6f&area_m2=%.1f&total=%d",
            treeId,
            treeId,
            code,
            cropType.uppercase(Locale.US),
            spacingM,
            spacingM,
            unitM2,
            row,
            col,
            lat,
            lng,
            areaM2,
            totalTrees,
        )
    }

    /** Thông số giải mã trực tiếp từ mã QR lưới khoảng cách (`vwing://tree/SR-001?code=...&crop=DURIAN&spacing=8x8m...`). */
    data class SpacingQrMeta(
        val treeId: String,
        val code: String,
        val crop: String,
        val spacing: String,
        val unitM2: Int?,
        val row: Int?,
        val col: Int?,
        val lat: Double?,
        val lng: Double?,
        val areaM2: Double?,
        val totalTrees: Int?,
    ) {
        val cropVi: String
            get() = when (crop.uppercase(Locale.US)) {
                "DURIAN" -> "Sầu riêng"
                "COFFEE" -> "Cà phê"
                else -> crop.ifBlank { "Cây ăn trái" }
            }
    }

    fun parseSpacingMeta(raw: String): SpacingQrMeta? {
        val text = raw.trim()
        if (!text.startsWith(PREFIX)) return null
        val afterPrefix = text.removePrefix(PREFIX).trim()
        val pathPart = afterPrefix.substringBefore("?").trim()
        val queryPart = afterPrefix.substringAfter("?", "")
        if (queryPart.isBlank()) return null
        val params = queryPart.split("&").mapNotNull { kv ->
            val eq = kv.indexOf('=')
            if (eq > 0) kv.substring(0, eq) to kv.substring(eq + 1) else null
        }.toMap()
        if (params.isEmpty()) return null
        val code = params["code"] ?: pathPart
        val id = params["id"] ?: pathPart.ifBlank { code }
        return SpacingQrMeta(
            treeId = id,
            code = code,
            crop = params["crop"].orEmpty(),
            spacing = params["spacing"].orEmpty(),
            unitM2 = params["unit_m2"]?.toIntOrNull(),
            row = params["row"]?.toIntOrNull(),
            col = params["col"]?.toIntOrNull(),
            lat = params["lat"]?.toDoubleOrNull(),
            lng = params["lng"]?.toDoubleOrNull(),
            areaM2 = params["area_m2"]?.toDoubleOrNull(),
            totalTrees = params["total"]?.toIntOrNull(),
        )
    }

    sealed class Scan {
        data class Found(val tree: Tree, val spacingMeta: SpacingQrMeta? = null) : Scan()
        /** QR của VWing nhưng cây không có trong vườn đang mở (vườn khác, hoặc chưa bấm Lưu vào Hồ sơ vườn). */
        data class Unknown(val id: String, val spacingMeta: SpacingQrMeta? = null) : Scan()
        /** Không phải mã QR của VWing. */
        data class Foreign(val text: String) : Scan()
    }

    fun resolve(d: GardenData, raw: String): Scan {
        val text = raw.trim()
        if (text.startsWith(PREFIX)) {
            val meta = parseSpacingMeta(text)
            val afterPrefix = text.removePrefix(PREFIX).trim()
            val pathPart = afterPrefix.substringBefore("?").trim()
            val queryPart = afterPrefix.substringAfter("?", "")
            val params = queryPart.split("&").mapNotNull { kv ->
                val eq = kv.indexOf('=')
                if (eq > 0) kv.substring(0, eq) to kv.substring(eq + 1) else null
            }.toMap()
            val codeParam = params["code"] ?: params["id"] ?: pathPart
            d.treeById(pathPart)?.let { return Scan.Found(it, meta) }
            params["id"]?.let { d.treeById(it) }?.let { return Scan.Found(it, meta) }
            d.treeByCode(pathPart)?.let { return Scan.Found(it, meta) }
            d.treeByCode(codeParam)?.let { return Scan.Found(it, meta) }
            return Scan.Unknown(codeParam.ifBlank { pathPart }, meta)
        }
        // Nhãn in chỉ có mã cây (ví dụ "SR-001", "CF-0001", "A-04") vẫn dùng được.
        d.treeByCode(text)?.let { return Scan.Found(it, null) }
        return Scan.Foreign(text.take(80))
    }
}
