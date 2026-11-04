package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.DetStatus
import com.example.test_tablet.data.vwing.Detection
import com.example.test_tablet.data.vwing.GeoBounds
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.Imagery

class ViewerImage(val bitmap: ImageBitmap, val bounds: GeoBounds?, val label: String, val alpha: Float = 1f)
class ViewerTree(val code: String, val pos: GeoPoint, val cond: Condition)

/**
 * Xem ảnh có phóng/kéo. Khi có ảnh thứ hai, hiện so sánh hai thời điểm bằng thanh chia (trái = ảnh 1, phải = ảnh 2).
 * Ảnh đã định vị được đặt đúng khung địa lý; ảnh chưa định vị chỉ được căng vừa khung (không đảm bảo trùng vị trí).
 * [onTap] nhận tọa độ chuẩn hóa (0..1) trong khung xem và tọa độ địa lý nếu khung xem đã định vị.
 */
@Composable
fun ImageViewer(
    primary: ViewerImage,
    modifier: Modifier = Modifier,
    secondary: ViewerImage? = null,
    split: Float = .5f,
    trees: List<ViewerTree> = emptyList(),
    detections: List<Detection> = emptyList(),
    selectedDetectionId: String? = null,
    marker: GeoPoint? = null,
    onTap: (u: Double, v: Double, geo: GeoPoint?) -> Unit = { _, _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val dens = LocalDensity.current.density
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val view: GeoBounds? = primary.bounds
    val aspect = if (view != null) (Imagery.widthM(view) / Imagery.heightM(view)).toFloat()
    else primary.bitmap.width.toFloat() / primary.bitmap.height

    fun fitRect(size: Size): Rect {
        val a = size.width / size.height
        val w = if (aspect > a) size.width else size.height * aspect
        val h = if (aspect > a) size.width / aspect else size.height
        val c = Offset(size.width / 2, size.height / 2)
        val z = zoom
        return Rect(c.x - w * z / 2 + pan.x, c.y - h * z / 2 + pan.y, c.x + w * z / 2 + pan.x, c.y + h * z / 2 + pan.y)
    }
    val sizeState = remember { mutableStateOf(Size.Zero) }
    val viewState = rememberUpdatedState(view)
    val tapCb = rememberUpdatedState(onTap)

    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1A12))) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // Một ngón khi chưa phóng to: nhường cho cuộn trang. Hai ngón (chụm) hoặc đã phóng to: kéo/phóng ảnh.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size >= 2 || zoom > 1.01f) {
                                val z = ev.calculateZoom(); val p = ev.calculatePan(); val centroid = ev.calculateCentroid(useCurrent = false)
                                val nz = (zoom * z).coerceIn(1f, 16f)
                                val c = Offset(sizeState.value.width / 2, sizeState.value.height / 2)
                                pan = (pan - (centroid - c)) * (nz / zoom) + (centroid - c) + p
                                zoom = nz
                                if (nz == 1f && ev.changes.size < 2) pan = Offset.Zero
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { t ->
                        val r = fitRect(sizeState.value)
                        val u = ((t.x - r.left) / r.width).toDouble(); val v = ((t.y - r.top) / r.height).toDouble()
                        if (u in 0.0..1.0 && v in 0.0..1.0) tapCb.value(u, v, viewState.value?.let { Imagery.fromUnit(it, u, v) })
                    }
                },
        ) {
            sizeState.value = size
            val r = fitRect(size)
            fun rectOf(img: ViewerImage): Rect {
                val b = img.bounds
                if (b == null || view == null) return r
                val l = r.left + ((b.west - view.west) / (view.east - view.west)).toFloat() * r.width
                val rt = r.left + ((b.east - view.west) / (view.east - view.west)).toFloat() * r.width
                val tp = r.top + ((view.north - b.north) / (view.north - view.south)).toFloat() * r.height
                val bt = r.top + ((view.north - b.south) / (view.north - view.south)).toFloat() * r.height
                return Rect(l, tp, rt, bt)
            }
            fun draw(img: ViewerImage, clip: Rect?) {
                val rr = rectOf(img)
                val block: DrawScope.() -> Unit = {
                    drawImage(img.bitmap, dstOffset = IntOffset(rr.left.toInt(), rr.top.toInt()), dstSize = IntSize(rr.width.toInt().coerceAtLeast(1), rr.height.toInt().coerceAtLeast(1)), alpha = img.alpha)
                }
                if (clip == null) block() else clipRect(clip.left, clip.top, clip.right, clip.bottom) { block() }
            }
            if (secondary == null) draw(primary, null) else {
                val sx = r.left + r.width * split
                draw(primary, Rect(0f, 0f, sx, size.height)); draw(secondary, Rect(sx, 0f, size.width, size.height))
                drawLine(Color.White, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 2.5f * dens)
                drawText(measurer.measure("1 · " + primary.label, TextStyle(color = Color.White, fontSize = 13.sp)), topLeft = Offset(sx - 8f * dens - 120f * dens, 6f * dens))
                drawText(measurer.measure("2 · " + secondary.label, TextStyle(color = Color.White, fontSize = 13.sp)), topLeft = Offset(sx + 8f * dens, 6f * dens))
            }
            drawRect(Color(0x88FFFFFF), r.topLeft, r.size, style = Stroke(1f * dens))

            fun at(p: GeoPoint): Offset? = view?.let { v -> Imagery.toUnit(v, p)?.let { Offset(r.left + it.first.toFloat() * r.width, r.top + it.second.toFloat() * r.height) } }
            for (t in trees) at(t.pos)?.let { o ->
                drawCircle(Color.White, 4.5f * dens, o); drawCircle(conditionColor(t.cond), 3.2f * dens, o)
                if (zoom >= 2f) drawText(measurer.measure(t.code, TextStyle(color = Color.White, fontSize = 13.sp)), topLeft = o + Offset(5f * dens, -4f * dens))
            }
            for (d in detections) at(d.point)?.let { o ->
                val col = when (d.status) { DetStatus.PENDING -> Color(0xFFF59E0B); DetStatus.CONFIRMED -> Color(0xFF3DDC84); DetStatus.NEW_TREE -> Color(0xFF21A691); DetStatus.REJECTED -> Color(0xFF9E9E9E) }
                val s = 6f * dens
                drawRect(col, Offset(o.x - s, o.y - s), Size(2 * s, 2 * s), style = Stroke((if (d.id == selectedDetectionId) 3.5f else 2f) * dens))
            }
            marker?.let { m -> at(m)?.let { o -> drawCircle(Color(0xFF6BBFFF), 8f * dens, o, style = Stroke(3f * dens)); drawCircle(Color(0xFF6BBFFF), 2.5f * dens, o) } }
        }
        // đặt lại khung nhìn (vuốt trên ảnh là kéo ảnh, không cuộn trang)
        androidx.compose.material3.Text(
            "⌖  Về giữa", color = Color.White, fontSize = 14.sp,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { zoom = 1f; pan = Offset.Zero } }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

