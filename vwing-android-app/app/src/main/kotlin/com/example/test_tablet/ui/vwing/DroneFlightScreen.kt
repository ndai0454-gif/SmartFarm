package com.example.test_tablet.ui.vwing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.Dates
import com.example.test_tablet.data.vwing.DroneFlight
import com.example.test_tablet.data.vwing.FeatureKind
import com.example.test_tablet.data.vwing.Geo
import com.example.test_tablet.data.vwing.GeoFeature
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.VIETNAM_GARDEN_PRESETS
import com.example.test_tablet.data.vwing.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trung tâm Drone Đa phổ (Multispectral UAV: DJI Mavic 3M, Phantom 4M, WingtraOne, AgEagle eBee X),
 * Bản đồ Trực giao RTK (Orthomosaic + DSM), 5 Chỉ số thực vật (NDVI · GNDVI · NDRE · EVI · VARI),
 * Đếm mật độ cây trồng (Stand Count), Kê đơn & Phun Biến thiên VRA (DJI Agras T50/T40/T25, XAG P100 Pro,
 * Máy kéo ISOBUS tiết kiệm 15%–40% vật tư), Bảo hiểm Nông nghiệp & Nhật ký Bay DJI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DroneFlightScreen(c: VCtx) {
    val d = c.d
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SatelliteSettings(context.filesDir) }
    val cfg = remember { settings.load() }

    var showAddLog by remember { mutableStateOf(false) }
    var detailLog by remember { mutableStateOf<DroneFlight?>(null) }

    // Lấy vùng ranh giới vườn hiện tại hoặc dùng vùng mẫu thực tế đầu tiên để luôn phân tích được ngay
    val fallbackPreset = remember { VIETNAM_GARDEN_PRESETS.first() }
    val activeRegionFeature = remember(d.features) {
        d.features.firstOrNull { it.kind == FeatureKind.BOUNDARY && it.points.size >= 3 }
            ?: d.features.firstOrNull { it.kind == FeatureKind.ZONE && it.points.size >= 3 }
            ?: GeoFeature(
                id = "drone_default_boundary",
                kind = FeatureKind.BOUNDARY,
                name = fallbackPreset.name,
                points = fallbackPreset.points,
                source = "Tọa độ mẫu VN: ${fallbackPreset.name}",
            )
    }

    var droneMapOverlay by remember(activeRegionFeature.id, activeRegionFeature.points) { mutableStateOf<MapOverlay?>(null) }
    val droneHud = rememberRegionHudState(
        d = d,
        region = activeRegionFeature,
        cfg = cfg,
        onAutoOverlay = { if (droneMapOverlay == null) droneMapOverlay = it },
    )

    PageHeader(
        Icons.Filled.FlightTakeoff,
        if (c.simpleMode) "BAY CHỤP & PHUN THUỐC" else "DRONE ĐA PHỔ & PHUN VRA",
        if (c.simpleMode) "Bay chụp khám vườn & Phun thuốc tiết kiệm" else "Camera Đa phổ · Bản đồ Trực giao RTK · Phun Biến thiên",
        if (c.simpleMode) "Đưa ảnh chụp từ máy bay vào để đếm cây, phát hiện sớm chỗ cây thiếu phân/sâu bệnh và tính lượng thuốc cần pha giúp tiết kiệm 15%–40% chi phí."
        else "Tích hợp ảnh từ Camera Đa phổ Drone (DJI Mavic 3M, Phantom 4M, WingtraOne, eBee X) định vị RTK ±1 cm, " +
            "tính 5 chỉ số thực vật (NDVI, GNDVI, NDRE, EVI, VARI), đếm mật độ cây (Stand Count), phát hiện sớm thiếu đạm/sâu bệnh " +
            "và xuất Bản đồ Kê đơn Phun Biến thiên (Prescription Map) sang DJI Agras T50/T40/T25, XAG P-Series hoặc máy kéo ISOBUS giúp tiết kiệm 15%–40% phân bón & thuốc BVTV.",
        actions = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton("Mở Bản đồ vườn", { c.nav.top("map") }, primary = false, icon = Icons.Filled.Map)
                if (!c.simpleMode) {
                    VButton("Nhập Flight Log DJI…", { showAddLog = true }, primary = true)
                }
            }
        },
    )

    // Bộ chọn nhanh vùng vườn thực tế tại Việt Nam để thử nghiệm bay khảo sát & kê đơn phun VRA
    val presetLabels = remember {
        listOf("Đổi khu vườn đang xem (Hiện tại: ${activeRegionFeature.name})…") +
            VIETNAM_GARDEN_PRESETS.map { "${it.name} (${it.regionLabel})" }
    }
    VCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "📍 ${activeRegionFeature.name} · ${fmtNum(Geo.polygonAreaHa(activeRegionFeature.points), 2)} ha",
                    color = VC.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!c.simpleMode) {
                    Text(
                        "Máy chủ quang trắc & GEE: ${cfg.url} · Sẵn sàng xử lý Orthomosaic, DSM & Kê đơn VRA",
                        color = VC.mint,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        VPicker(
            "Đổi khu vườn",
            presetLabels,
            presetLabels.first(),
            { picked ->
                val idx = presetLabels.indexOf(picked) - 1
                if (idx in VIETNAM_GARDEN_PRESETS.indices) {
                    val pr = VIETNAM_GARDEN_PRESETS[idx]
                    val f = GeoFeature(newId(), FeatureKind.BOUNDARY, pr.name, pr.points, source = "Tọa độ thực tế VN: ${pr.name}")
                    c.store.applyGeoImport(listOf(f), emptyList(), "tọa độ vườn khảo sát Drone")
                    droneMapOverlay = null
                    c.toast("Đã chuyển sang ${pr.name} (${fmtNum(Geo.polygonAreaHa(pr.points), 2)} ha).")
                }
            },
            Modifier.padding(top = 6.dp),
        )
    }

    Spacer(Modifier.height(14.dp))

    // Thẻ trung tâm Camera Đa phổ Drone & Phun Biến thiên VRA
    if (droneHud != null) {
        DroneMultispectralHubCard(
            hud = droneHud,
            cfg = cfg,
            onApplyOverlay = { droneMapOverlay = it },
            onToast = c.toast,
            simpleMode = c.simpleMode,
        )
        Spacer(Modifier.height(14.dp))
    }

    // Phần Bản đồ GIS chi tiết & Nhật ký Flight Log DJI (.bin/.DAT) chỉ mở khi cần xem kỹ thuật
    TechnicalDetails(c.simpleMode) {
        if (droneHud != null) {
            VCard(Modifier.fillMaxWidth()) {
                Eyebrow("BẢN ĐỒ TRỰC GIAO & LỚP PHỦ KÊ ĐƠN PHUN BIẾN THIÊN (VRA) TRÊN VƯỜN")
                GardenMap(
                    d = d,
                    mode = MapMode.GEO,
                    layers = emptySet(),
                    selectedId = c.selectedTree,
                    modifier = Modifier.fillMaxWidth().height(if (c.wide) 420.dp else 360.dp),
                    overlays = listOfNotNull(droneMapOverlay),
                    analysisRegion = activeRegionFeature.points,
                    regionHud = droneHud,
                    hudCfg = cfg,
                    onHudSelectOverlay = { droneMapOverlay = it },
                    onHudRefresh = {
                        droneHud.refreshAll(
                            cfg = cfg,
                            start = droneHud.ndviResult?.start ?: "2026-09-20",
                            end = droneHud.ndviResult?.endExclusive ?: "2026-10-21",
                            scope = scope,
                            onAutoOverlay = { droneMapOverlay = it },
                        )
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // Nhật ký bay DJI Agras / SmartFarm (Flight Log Importer)
        VCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("NHẬT KÝ BAY THỰC TẾ · DJI AGRAS / SMARTFARM FLIGHT LOG")
                    Text(
                        "Giải mã file nhật ký bay (.txt/.bin/.DAT) từ máy bay phun thuốc DJI Agras T25/T40/T50.",
                        color = VC.text2,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                VButton("Nhập file log…", { showAddLog = true }, primary = false)
            }
            Spacer(Modifier.height(10.dp))
            if (d.droneFlights.isEmpty()) {
                EmptyNote("Chưa có nhật ký bay DJI nào được nhập.")
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.droneFlights.sortedByDescending { it.importedAt }.forEach { f ->
                        VCard(Modifier.fillMaxWidth(), onClick = { detailLog = f }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(f.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VC.text)
                                    Text(Dates.fmt(f.importedAt), fontSize = 14.sp, color = VC.text2)
                                }
                                Text(
                                    if (f.parseOk) "${f.frameCount} khung" else "Không đọc được",
                                    color = if (f.parseOk) VC.mint else VC.red,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddLog) ImportDroneLogDialog(c, onDismiss = { showAddLog = false })
    detailLog?.let { f -> DroneFlightDetailDialog(c, f, onDismiss = { detailLog = null }) }
}

@Composable
private fun ImportDroneLogDialog(c: VCtx, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { c.store.importDroneLogFile(uri, name) }
                busy = false
                if (r.ok) onDismiss() else c.toast(r.message ?: "Không nhập được file.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text("Nhập file log DJI Agras / SmartFarm", color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VField(name, { name = it }, "Tên gợi nhớ (ví dụ: Bay phun VRA lô A 21/10)")
                Text(
                    "Chọn file flight log đã xuất từ app DJI Agras/SmartFarm (thường có đuôi .txt/.bin/.DAT). " +
                        "VWing chỉ đọc cấu trúc file tại chỗ trên máy tính bảng, không yêu cầu đăng nhập tài khoản DJI.",
                    fontSize = 14.sp, color = VC.text2,
                )
            }
        },
        confirmButton = { VButton(if (busy) "Đang nhập…" else "Chọn tệp…", { pick.launch(arrayOf("*/*")) }, enabled = !busy) },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}

@Composable
private fun DroneFlightDetailDialog(c: VCtx, f: DroneFlight, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VC.card,
        title = { Text(f.name, color = VC.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nhập lúc: " + Dates.fmt(f.importedAt), fontSize = 15.sp, color = VC.text2)
                Text("Kích thước file: " + "%.1f".format(f.fileSizeBytes / 1024.0) + " KB", fontSize = 15.sp, color = VC.text2)
                Text(
                    if (f.parseOk) "Đọc được ${f.frameCount} khung dữ liệu." else "Không đọc được: ${f.parseError.ifBlank { "lỗi không xác định" }}",
                    fontSize = 15.sp,
                    color = if (f.parseOk) VC.mint else VC.red,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sử dụng bảng Bản đồ Kê đơn Phun Biến thiên (VRA) ở trên để xuất gói nhiệm vụ .SHP / .TIFF / JSON cho DJI Agras T25/T40/T50 và XAG P100 Pro.",
                    fontSize = 14.sp, color = VC.text2,
                )
            }
        },
        confirmButton = {
            VButton("Xóa", { c.store.deleteDroneFlight(f.id); onDismiss() }, danger = true)
        },
        dismissButton = { TextButton(onDismiss) { Text("Đóng", color = VC.text2) } },
    )
}
