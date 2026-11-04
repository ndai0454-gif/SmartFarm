package com.example.test_tablet.data.vwing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File

/** Phần phụ thuộc Android của NDVI: đọc băng ảnh, lưu/đọc lưới, tô màu. Logic tính toán nằm ở [Ndvi] để kiểm thử được. */
object NdviIO {
    private const val MAX_SIDE = 1600

    private fun sampleSize(w: Int, h: Int): Int { var s = 1; while (w / (s * 2) >= MAX_SIDE || h / (s * 2) >= MAX_SIDE) s *= 2; return s }

    /** Đọc một băng thành mảng giá trị 0..255 (kênh đỏ của ảnh xám). Trả (mảng, rộng, cao, hệ số thu nhỏ). */
    class Band(val v: IntArray, val w: Int, val h: Int, val sample: Int)

    fun loadBand(file: File): Band? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth <= 0) null else {
            val s = sampleSize(o.outWidth, o.outHeight)
            val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
            if (bmp == null) null else {
                val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val out = IntArray(px.size) { (px[it] shr 16) and 0xFF }
                Band(out, bmp.width, bmp.height, s).also { bmp.recycle() }
            }
        }
    } catch (_: Throwable) { null }

    /** PNG 8 bit: giá trị lượng tử hóa ở kênh xám, thiếu dữ liệu = trong suốt. */
    fun saveGrid(g: Ndvi.Grid, file: File): Boolean = try {
        val px = IntArray(g.w * g.h) { i -> val q = g.q[i].toInt() and 0xFF; if (q == Ndvi.NODATA) 0 else Color.argb(255, q, q, q) }
        val bmp = Bitmap.createBitmap(px, g.w, g.h, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle(); true
    } catch (_: Throwable) { false }

    fun loadGrid(file: File): Ndvi.Grid? = try {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp == null) null else {
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val q = ByteArray(px.size) { i -> if ((px[i] ushr 24) == 0) Ndvi.NODATA.toByte() else ((px[i] shr 16) and 0xFF).toByte() }
            Ndvi.Grid(bmp.width, bmp.height, q).also { bmp.recycle() }
        }
    } catch (_: Throwable) { null }

    /** Thang màu NDVI (cùng thang với chú giải trên màn hình): thiếu dữ liệu để trong suốt. */
    fun colorOf(v: Double): Int = when {
        v < 0.0 -> Color.rgb(120, 144, 156)
        v < 0.2 -> Color.rgb(161, 120, 75)
        v < 0.4 -> Color.rgb(230, 200, 70)
        v < 0.6 -> Color.rgb(140, 200, 80)
        else -> Color.rgb(30, 130, 50)
    }

    fun heatBitmap(g: Ndvi.Grid): Bitmap {
        val px = IntArray(g.w * g.h) { i -> val q = g.q[i].toInt() and 0xFF; if (q == Ndvi.NODATA) 0 else colorOf(Ndvi.dequant(q)) }
        return Bitmap.createBitmap(px, g.w, g.h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Tính NDVI từ hai băng đã nhập, tính thống kê và lưu lưới. Mọi điều kiện (cảm biến đa phổ, cùng lưới/khung/ngày)
     * được kiểm tra trước; ảnh RGB bị từ chối.
     */
    fun computeFromBands(store: VWingStore, red: ImageSet, nir: ImageSet, preprocess: Preprocess, canopyRadiusM: Double, provider: String): Result<VegIndex> = runCatching {
        Ndvi.checkBands(red, nir)?.let { error(it) }
        val r = loadBand(File(store.photoDir, red.file)) ?: error("Không đọc được băng Red.")
        val n = loadBand(File(store.photoDir, nir.file)) ?: error("Không đọc được băng NIR.")
        if (r.w != n.w || r.h != n.h) error("Hai băng khác kích thước sau khi đọc.")
        val grid = Ndvi.compute(r.v, n.v, r.w, r.h)
        val bounds = red.bounds
        val notes = mutableListOf<String>()
        val stats: List<IndexStat>
        var resolution: Double? = null
        if (bounds != null) {
            resolution = Ndvi.pixelSize(bounds, grid.w, grid.h)
            val sum = Ndvi.summarize(store.data, grid, bounds, canopyRadiusM)
            stats = sum.stats; notes += sum.notes
        } else {
            val valid = (0 until grid.w * grid.h).mapNotNull { grid.value(it % grid.w, it / grid.w) }
            stats = if (valid.isEmpty()) emptyList() else listOf(IndexStat("garden", valid.average(), valid.min(), valid.max(), valid.size, 100.0 * valid.size / (grid.w * grid.h)))
            notes += "Băng chưa có khung địa lý: chỉ có thống kê toàn ảnh, không có giá trị theo cây/khu và không phủ lên bản đồ."
        }
        if (r.sample > 1) notes += "Ảnh gốc được thu nhỏ ${r.sample} lần để tính (độ phân giải thật thấp hơn ảnh gốc)."
        if (preprocess == Preprocess.RAW_DN) notes += "Giá trị thô (DN) chưa hiệu chỉnh: chỉ so sánh tương đối trong cùng một ngày bay, không so giữa các ngày."
        if (preprocess == Preprocess.UNKNOWN) notes += "Chưa rõ tiền xử lý: không nên so sánh với ngày khác."
        val file = newId() + ".ndvi.png"
        if (!saveGrid(grid, File(store.photoDir, file))) error("Không lưu được lưới NDVI.")
        VegIndex(
            id = newId(), name = "NDVI " + Dates.fmt(red.capturedAt), source = IndexSource.COMPUTED, date = red.capturedAt,
            provider = provider.ifBlank { red.source }, sensor = SensorKind.MULTISPECTRAL, preprocess = preprocess,
            resolutionM = resolution, bounds = bounds, redSetId = red.id, nirSetId = nir.id, gridFile = file,
            gridW = grid.w, gridH = grid.h, canopyRadiusM = canopyRadiusM, stats = stats, notes = notes,
        )
    }
}
