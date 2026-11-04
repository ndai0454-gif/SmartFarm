package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.GeoPlace
import com.example.test_tablet.data.vwing.GeoPoint
import com.example.test_tablet.data.vwing.GeocodeClient
import com.example.test_tablet.data.vwing.GisException
import com.example.test_tablet.data.vwing.SatelliteSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ô tìm địa điểm kiểu gõ-tới-đâu-tìm-tới-đó (Photon/OpenStreetMap qua máy chủ vệ tinh). Chờ người dùng ngừng gõ 450 ms mới gọi mạng
 * để không dồn yêu cầu; kết quả ưu tiên Việt Nam và gần [near]. Ghi nguồn dữ liệu bên dưới danh sách theo giấy phép OSM.
 */
@Composable
fun PlaceSearchBox(near: GeoPoint?, onPick: (GeoPlace) -> Unit, modifier: Modifier = Modifier, label: String = "Tìm địa điểm (xã, huyện, tên đường…)") {
    val context = LocalContext.current
    val cfg = remember { SatelliteSettings(context.filesDir).load() }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        if (q.trim().length < 3) { results = emptyList(); err = null; searched = false; return@LaunchedEffect }
        delay(450)
        busy = true; err = null
        runCatching { withContext(Dispatchers.IO) { GeocodeClient.search(cfg, q, near) } }
            .onSuccess { results = it; searched = true }
            .onFailure { results = emptyList(); searched = false; err = (it as? GisException)?.message ?: "Không tìm được: ${it.message}" }
        busy = false
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VField(q, { q = it }, label, enabled = cfg.configured, placeholder = if (cfg.configured) null else "Cần kết nối máy chủ vệ tinh")
        if (!cfg.configured) Text("Kết nối máy chủ vệ tinh (mục Vệ tinh) để dùng tìm địa điểm.", color = VC.amber, fontSize = 13.sp)
        if (busy) Text("Đang tìm…", color = VC.muted, fontSize = 14.sp)
        err?.let { Text(it, color = VC.red, fontSize = 14.sp) }
        if (searched && results.isEmpty() && !busy) Text("Không thấy địa điểm nào khớp.", color = VC.muted, fontSize = 14.sp)
        results.forEach { p ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VC.elevated).clickable { onPick(p); q = ""; results = emptyList() }.padding(10.dp),
            ) {
                Text(p.label, color = VC.text, fontSize = 15.sp)
                if (p.kind.isNotBlank()) Text(p.kind, color = VC.muted, fontSize = 13.sp)
            }
        }
        if (results.isNotEmpty()) Text(GeocodeClient.ATTRIBUTION, color = VC.muted, fontSize = 13.sp)
    }
}

/** Hộp thoại tìm địa điểm; trả về địa chỉ hành chính (xã/huyện/tỉnh) để điền ô "Địa điểm" của vườn. */
@Composable
fun PlaceSearchDialog(near: GeoPoint?, onPick: (GeoPlace) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Tìm địa điểm", color = VC.text) },
        text = { PlaceSearchBox(near, { onPick(it); onDismiss() }) },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}
