package com.example.test_tablet.ui.vwing

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DroneMultispectralHubCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onApplyOverlay: ((MapOverlay?) -> Unit)? = null,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    simpleMode: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val dm = hud.droneMsResult
    if (dm == null) {
        VCard(modifier.fillMaxWidth()) {
            Eyebrow("🚁📷 HỆ THỐNG CAMERA ĐA PHỔ DRONE · BẢN ĐỒ TRỰC GIAO & PHUN BIẾN THIÊN (VRA)")
            Text(
                if (hud.loading) "Đang xử lý ảnh quang trắc Drone (NDVI · GNDVI · NDRE · EVI · VARI), đếm cây Stand Count và lập Bản đồ Kê đơn Phun VRA cho vùng \"${hud.regionName}\"…"
                else " Sẵn sàng phân tích dữ liệu Camera Đa phổ Drone (DJI Mavic 3M, Phantom 4M, WingtraOne, eBee X) & xuất bản đồ Kê đơn Phun biến thiên (DJI Agras T50/T40/T25, XAG P100 Pro, ISOBUS).",
                color = VC.text2,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            VButton(
                if (hud.loading) "Đang phân tích Camera Drone…" else "Chạy phân tích Camera Đa phổ Drone & Kê đơn VRA",
                {
                    hud.updateDroneMultispectral(
                        cfg = cfg,
                        scope = scope,
                        onOverlay = onApplyOverlay,
                        onDone = onToast,
                    )
                },
                enabled = !hud.loading && cfg.configured,
            )
        }
        return
    }
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("TAB_VRA_RX") }

    // Trình chọn ảnh thực tế từ thẻ nhớ / bộ nhớ máy tính bảng (nạp ảnh từ Camera Drone)
    val pickDroneFrames = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    uris.take(12).mapNotNull { uri ->
                        runCatching {
                            val name = context.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null,
                                null,
                                null,
                            )?.use { c ->
                                if (c.moveToFirst()) c.getString(0) else "DJI_MS_FRAME.TIF"
                            } ?: "DJI_MS_FRAME.TIF"
                            val bmp = context.contentResolver.openInputStream(uri)?.use { ins ->
                                BitmapFactory.decodeStream(ins)
                            } ?: return@runCatching null
                            DroneMultispectralClient.extractFrameTelemetry(name, bmp)
                        }.getOrNull()
                    }
                }
                if (extracted.isNotEmpty()) {
                    hud.updateDroneMultispectral(
                        cfg = cfg,
                        uploadedFrames = extracted,
                        scope = scope,
                        onOverlay = onApplyOverlay,
                        onDone = {
                            onToast("Đã nạp ${extracted.size} ảnh từ Camera Drone (${extracted.first().fileName}) & cập nhật bản đồ Kê đơn Phun VRA!")
                        },
                    )
                } else {
                    onToast("Không đọc được tệp ảnh Drone đã chọn.")
                }
            }
        }
    }

    VCard(modifier.fillMaxWidth()) {
        val s2 = dm.step2
        val s3 = dm.step3
        val sc = s2.standCountAndYield
        val topAnomaly = s2.earlyAnomalyDetection.firstOrNull()

        // Header ngắn gọn + 2 nút hành động chính
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (simpleMode) "🚁 PHUN THUỐC THÔNG MINH" else "🚁📷 CAMERA ĐA PHỔ DRONE & PHUN BIẾN THIÊN (VRA)")
                Text(
                    "${hud.regionName} · ${fmtNum(dm.areaHa, 2)} ha",
                    color = VC.text2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .border(1.5.dp, VC.lime, RoundedCornerShape(14.dp))
                        .clickable { pickDroneFrames.launch(arrayOf("image/*")) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "📷 Chọn ảnh (${dm.uploadedFramesCount})",
                        color = VC.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.lime)
                        .clickable {
                            onToast("Đã gửi lệnh phun ${fmtNum(s3.vraSprayTotalL, 1)} Lít sang ${s3.sprayerHardware.name}!")
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "🚁 Gửi máy bay phun",
                        color = VC.onLime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 3 Ô SỐ TO RÕ RÀNG (Chỉ số to + 3 từ ngắn gọn, không viết đoạn văn dài)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("🌳 Số cây đếm được", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${sc.detectedStandCount} cây", color = VC.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("${fmtNum(sc.emergenceSurvivalPct, 0)}% khỏe · ~${fmtNum(sc.estimatedHarvestYieldTons, 1)} tấn", color = VC.mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).border(1.5.dp, VC.lime, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("💧 Lượng thuốc cần pha", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${fmtNum(s3.vraSprayTotalL, 1)} Lít", color = VC.lime, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Đỡ tốn ${fmtNum(s3.savedSprayVolumeL, 1)} Lít thuốc", color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("💰 Tiền tiết kiệm", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${fmtNum(s3.estimatedCostSavedVnd / 1000.0, 0)}.000đ", color = VC.mint, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Giảm ${fmtNum(s3.chemicalSavingsPct, 0)}% chi phí", color = VC.text2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 1 dòng nhắc ngắn gọn (nếu có vùng cây yếu) + Chọn nhanh loại cây
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "FRUIT_ORCHARD" to "🍊 Cây ăn trái",
                "CORN_MAIZE" to "🌽 Ngô",
                "WHEAT_RICE" to "🌾 Lúa",
                "SUNFLOWER_INDUSTRIAL" to "🌻 Cây CN",
            ).forEach { (cKey, cLabel) ->
                val sel = hud.droneCropGuide == cKey
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) VC.lime else VC.elevated)
                        .clickable {
                            hud.updateDroneMultispectral(
                                cfg = cfg,
                                cropGuide = cKey,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cLabel,
                        color = if (sel) VC.onLime else VC.text,
                        fontSize = 14.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
            if (topAnomaly != null) {
                Text(
                    "⚠️ Chú ý: ${fmtNum(topAnomaly.affectedHa, 2)} ha ${topAnomaly.titleVi.substringBefore("&").trim()}",
                    color = VC.amber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // Bản đồ màu chỉ dẫn vùng phun + 5 nhãn màu 2 chữ cực ngắn
        hud.droneMsBitmap?.let { bmp ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.border, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Bản đồ phun thuốc",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        "⚪ Đất trống: Tắt vòi",
                        "🟢 Cây khỏe: Phun ít",
                        "🔵 Bình thường: Phun vừa",
                        "🟠 Thiếu đạm: Tăng liều",
                        "🔴 Sâu bệnh: Phun kỹ",
                    ).forEach { tagTxt ->
                        Text(tagTxt, color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bọc toàn bộ thông số kỹ thuật chuyên sâu (5 chỉ số NDVI/GNDVI/NDRE/EVI/VARI, chọn cảm biến, Bảo hiểm…) vào TechnicalDetails khi ở Chế độ Nông dân
        TechnicalDetails(simpleMode, Modifier.padding(top = 8.dp)) {
            Spacer(Modifier.height(6.dp))

        // Hàng 1: Chọn Thiết bị Bay Khảo sát (DJI Mavic 3M, Phantom 4M, WingtraOne, AgEagle eBee X) & Crop Guide
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛰 Drone khảo sát:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf(
                "DJI_MAVIC_3_MULTISPECTRAL" to "DJI Mavic 3M (M3M)",
                "DJI_PHANTOM_4_MULTISPECTRAL" to "Phantom 4M (P4M)",
                "WINGTRAONE_GEN2_REDEDGE_P" to "WingtraOne RedEdge-P",
                "AGEAGLE_EBEE_X_ALTUM_PT" to "AgEagle eBee X Altum",
            ).forEach { (id, label) ->
                val sel = hud.droneSurveyId == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (sel) VC.mint else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroneMultispectral(
                                cfg = cfg,
                                surveyDroneId = id,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        label,
                        color = if (sel) VC.mint else VC.text2,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Text("🌱 Crop Guide:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf(
                "FRUIT_ORCHARD" to "🌳 Cây ăn quả",
                "CORN_MAIZE" to "🌽 Ngô (Corn)",
                "WHEAT_RICE" to "🌾 Lúa mì / Lúa",
                "SUNFLOWER_INDUSTRIAL" to "🌻 Hướng dương",
            ).forEach { (cKey, cLabel) ->
                val sel = hud.droneCropGuide == cKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VC.amber.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (sel) VC.amber else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroneMultispectral(
                                cfg = cfg,
                                cropGuide = cKey,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        cLabel,
                        color = if (sel) VC.amber else VC.text2,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Hàng 2: Chọn Máy bay Phun/Rải (DJI Agras T50/T40/T25, XAG P100 Pro, Máy kéo ISOBUS) & Lớp phủ Bản đồ
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🚁 Máy bay phun:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf(
                "DJI_AGRAS_T50" to "DJI Agras T50 (40L)",
                "DJI_AGRAS_T40" to "DJI Agras T40",
                "DJI_AGRAS_T25" to "DJI Agras T25",
                "XAG_P100_PRO" to "XAG P100 Pro",
                "ISOBUS_TRACTOR_SPRAYER" to "Máy kéo ISOBUS",
            ).forEach { (sId, sLabel) ->
                val sel = hud.droneSprayerId == sId
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VC.cyan.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (sel) VC.cyan else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroneMultispectral(
                                cfg = cfg,
                                sprayerId = sId,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        sLabel,
                        color = if (sel) VC.cyan else VC.text2,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Text("🗺 Lớp phủ:", color = VC.text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            listOf(
                "VRA_PRESCRIPTION" to "💊 Bản đồ Kê đơn Phun VRA",
                "NDRE" to "🧪 NDRE (RedEdge)",
                "GNDVI" to "🍃 GNDVI (Đạm)",
                "NDVI" to "🌿 NDVI",
                "EVI" to "🌲 EVI",
                "VARI" to "📷 VARI (RGB)",
                "DSM" to "⛰ DSM (Cao độ tán)",
            ).forEach { (lKey, lLabel) ->
                val sel = hud.droneOverlayLayer == lKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VC.lime.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (sel) VC.lime else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroneMultispectral(
                                cfg = cfg,
                                overlayLayer = lKey,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        lLabel,
                        color = if (sel) VC.lime else VC.text2,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        // Thanh 4 Tab quy trình khép kín
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "TAB_VRA_RX" to "💊 1. Kê đơn & Phun Biến thiên VRA (Tiết kiệm ${fmtNum(dm.step3.chemicalSavingsPct, 1)}%)",
                "TAB_INDICES_5" to "📷 2. Cảm biến Đa phổ & 5 Chỉ số (NDVI·GNDVI·NDRE·EVI·VARI)",
                "TAB_STAND_ANOMALY" to "🌱 3. Bản đồ Trực giao · Đếm cây (Stand Count) & Sâu bệnh Sớm",
                "TAB_INSURANCE_HW" to "🛡 4. Bảo hiểm Nông nghiệp & Thiết bị DroneField / Pix4Dfields",
            ).forEach { (tKey, tTitle) ->
                val active = activeTab == tKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                        .border(if (active) 1.5.dp else 1.dp, if (active) VC.mint else VC.border, RoundedCornerShape(10.dp))
                        .clickable { activeTab = tKey }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(
                        tTitle,
                        color = if (active) VC.mint else VC.text,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            "TAB_VRA_RX" -> DroneVraPrescriptionTab(dm, hud, cfg, scope, onApplyOverlay, onToast)
            "TAB_INDICES_5" -> DroneFiveIndicesAndSensorTab(dm, hud, cfg, scope, onApplyOverlay, onToast)
            "TAB_STAND_ANOMALY" -> DroneStandCountAndEarlyAnomalyTab(dm)
            "TAB_INSURANCE_HW" -> DroneInsuranceAndEcosystemTab(dm)
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DroneVraPrescriptionTab(
    dm: DroneMultispectralReport,
    hud: RegionHudState,
    cfg: SatelliteConfig,
    scope: kotlinx.coroutines.CoroutineScope,
    onApplyOverlay: ((MapOverlay?) -> Unit)?,
    onToast: (String) -> Unit,
) {
    val s3 = dm.step3
    val sp = s3.sprayerHardware
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Banner tiết kiệm 15% - 40% vật tư
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.3.dp, VC.lime, RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "💰 HIỆU QUẢ PHUN BIẾN THIÊN (VARIABLE-RATE SPRAYING): TIẾT KIỆM ${fmtNum(s3.chemicalSavingsPct, 1)}% VẬT TƯ",
                    color = VC.lime,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Ngắt vòi tự động tại vùng đất trống/đường đi, phun giảm liều ở vùng cây khỏe và tập trung thuốc/phân vào đúng ổ cây suy yếu.",
                    color = VC.text,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "• Phun đồng loạt truyền thống: ${fmtNum(s3.uniformSprayTotalL, 1)} L dung dịch (${fmtNum(s3.uniformFertilizerKg, 1)} kg phân) → Phun biến thiên VRA: chỉ dùng ${fmtNum(s3.vraSprayTotalL, 1)} L (${fmtNum(s3.vraFertilizerKg, 1)} kg phân).",
                    color = VC.cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "-${fmtNum(s3.savedSprayVolumeL, 1)} L",
                    color = VC.mint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "-${fmtNum(s3.savedFertilizerKg, 1)} kg phân",
                    color = VC.amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "~${fmtNum(s3.estimatedCostSavedVnd / 1000.0, 0)}k VNĐ",
                    color = VC.text2,
                    fontSize = 12.sp,
                )
            }
        }

        // Bảng 5 vùng Kê đơn Phun Biến thiên (Prescription Map)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "I. BẢN ĐỒ PHÂN VÙNG KÊ ĐƠN 5 MỨC (PRESCRIPTION MAP CHO ${sp.name.uppercase()})",
                color = VC.teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.mint.copy(alpha = 0.22f))
                    .border(1.dp, VC.mint, RoundedCornerShape(8.dp))
                    .clickable {
                        hud.updateDroneMultispectral(
                            cfg = cfg,
                            overlayLayer = "VRA_PRESCRIPTION",
                            scope = scope,
                            onOverlay = onApplyOverlay,
                            onDone = onToast,
                        )
                    }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text("🗺 Phủ Bản đồ Kê đơn VRA lên Map", color = VC.mint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Thanh tỷ lệ 5 vùng
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            s3.prescriptionZones.forEach { z ->
                val col = when (z.zoneId) {
                    "RX_ZONE_0_SHUTOFF" -> Color(0xFF78909C)
                    "RX_ZONE_1_LOW" -> Color(0xFF43A047)
                    "RX_ZONE_2_STANDARD" -> Color(0xFF039BE5)
                    "RX_ZONE_3_HIGH" -> Color(0xFFFB8C00)
                    else -> Color(0xFFE53935)
                }
                if (z.areaPct > 0.2) {
                    Box(
                        Modifier
                            .weight(z.areaPct.toFloat().coerceAtLeast(0.5f))
                            .fillMaxHeight()
                            .background(col),
                    )
                }
            }
        }

        s3.prescriptionZones.forEach { z ->
            val badgeCol = when (z.zoneId) {
                "RX_ZONE_0_SHUTOFF" -> Color(0xFF90A4AE)
                "RX_ZONE_1_LOW" -> VC.mint
                "RX_ZONE_2_STANDARD" -> VC.cyan
                "RX_ZONE_3_HIGH" -> VC.amber
                else -> Color(0xFFEF5350)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(VC.elevated)
                    .border(1.dp, badgeCol.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        z.zoneNameVi,
                        color = badgeCol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${fmtNum(z.sprayRateLHa, 1)} L/ha · ${fmtNum(z.fertilizerRateKgHa, 1)} kg/ha (${fmtNum(z.dosePctOfStandard, 0)}%)",
                        color = VC.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Ngưỡng: ${z.conditionRule} → ${z.actionVi}", color = VC.text2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${fmtNum(z.areaPct, 1)}% (${fmtNum(z.areaHa, 3)} ha)", color = badgeCol, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Thông số kết nối máy bay phun DJI Agras / XAG / ISOBUS
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.cyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "🚁 II. THÔNG SỐ VẬN HÀNH MÁY BAY PHUN / MÁY KÉO: ${sp.name} (${sp.manufacturer})",
                color = VC.cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "• Bình chứa: ${fmtNum(sp.tankVolumeL, 0)} L (Rải ${fmtNum(sp.spreaderCapacityKg, 0)} kg) · Bề rộng vệt phun: ${fmtNum(sp.swathWidthM, 1)} m · Lưu lượng tối đa: ${fmtNum(sp.maxFlowRateLMin, 0)} L/phút · Số bình cần nạp: ${s3.sprayerTankLoadsNeeded} bình.",
                color = VC.text,
                fontSize = 12.sp,
            )
            Text("• Hệ thống vòi: ${sp.nozzleSystem}", color = VC.text2, fontSize = 12.sp)
            Text("• Định dạng xuất tệp điều khiển: ${s3.exportFormatsReady.joinToString(" | ")}", color = VC.mint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DroneFiveIndicesAndSensorTab(
    dm: DroneMultispectralReport,
    hud: RegionHudState,
    cfg: SatelliteConfig,
    scope: kotlinx.coroutines.CoroutineScope,
    onApplyOverlay: ((MapOverlay?) -> Unit)?,
    onToast: (String) -> Unit,
) {
    val s1 = dm.step1
    val s2 = dm.step2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Thông số Cảm biến Đa phổ & RTK
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.mint.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "📷 I. CẢM BIẾN ĐA PHỔ (MULTISPECTRAL CAMERA) & ĐỊNH VỊ CHÍNH XÁC CAO RTK",
                color = VC.mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "• Thiết bị bay: ${s1.surveyDrone.name} (${s1.surveyDrone.typeVi})",
                color = VC.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("• Cảm biến RGB: ${s1.surveyDrone.rgbSensor}", color = VC.text2, fontSize = 12.sp)
            Text("• Cảm biến Đa phổ: ${s1.surveyDrone.msSensors}", color = VC.cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("• Cảm biến ánh sáng & RTK: ${s1.surveyDrone.sunlightSensor} · ${s1.rtkFixStatus}", color = VC.lime, fontSize = 12.sp)
            Text(
                "• Độ phân giải mặt đất: GSD = ${fmtNum(s1.gsdCmPerPixel, 2)} cm/pixel (Bay cao ${fmtNum(s1.flightAltitudeM, 0)}m, phủ dọc ${fmtNum(s1.forwardOverlapPct, 0)}% / ngang ${fmtNum(s1.sideOverlapPct, 0)}%, tổng ${s1.total5BandCaptures} ảnh đa phổ).",
                color = VC.text,
                fontSize = 12.sp,
            )
        }

        Text(
            "🌿 II. 5 CHỈ SỐ THỰC VẬT ĐA PHỔ & QUANG HỌC (CHẠM ĐỂ PHỦ BẢN ĐỒ TRỰC GIAO LÊN MAP)",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        s2.fiveVegetationIndices.forEach { ix ->
            val isCurrentOverlay = hud.droneOverlayLayer == ix.code
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isCurrentOverlay) VC.mint.copy(alpha = 0.14f) else VC.elevated)
                    .border(
                        if (isCurrentOverlay) 1.5.dp else 1.dp,
                        if (isCurrentOverlay) VC.mint else VC.border,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable {
                        hud.updateDroneMultispectral(
                            cfg = cfg,
                            overlayLayer = ix.code,
                            scope = scope,
                            onOverlay = onApplyOverlay,
                            onDone = onToast,
                        )
                    }
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${ix.nameVi} (${ix.bandsUsed})",
                        color = VC.mint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "TB ${fmtNum(ix.mean, 3)} (P10–P90: ${fmtNum(ix.p10, 2)}..${fmtNum(ix.p90, 2)})",
                        color = VC.lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    "Công thức: ${ix.formula} · Vai trò: ${ix.statusVi}",
                    color = VC.cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(ix.agronomicMeaningVi, color = VC.text, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DroneStandCountAndEarlyAnomalyTab(dm: DroneMultispectralReport) {
    val s1 = dm.step1
    val s2 = dm.step2
    val sc = s2.standCountAndYield

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hướng dẫn cây trồng (Crop Guide) & Đếm mật độ Stand Count
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.lime.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "🌱 I. ĐẾM MẬT ĐỘ CÂY TRỒNG (STAND COUNT) & DỰ BÁO NĂNG SUẤT THU HOẠCH",
                color = VC.lime,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "• Hướng dẫn cây trồng (Crop Guide): ${s1.cropGuide.nameVi} (Chỉ số chủ lực: ${s1.cropGuide.primaryIndex})",
                color = VC.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(s1.cropGuide.guideNoteVi, color = VC.text2, fontSize = 12.sp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandMetricTile(
                    label = "Số cây phát hiện",
                    value = "${sc.detectedStandCount} cây",
                    sub = "Mật độ ${sc.actualDensityPerHa}/${sc.targetDensityPerHa} cây/ha",
                    color = VC.mint,
                    modifier = Modifier.weight(1f),
                )
                StandMetricTile(
                    label = "Tỷ lệ nảy mầm / Sống",
                    value = "${fmtNum(sc.emergenceSurvivalPct, 1)}%",
                    sub = "Mất khoảng: ${sc.missingGapsCount} vị trí",
                    color = VC.amber,
                    modifier = Modifier.weight(1f),
                )
                StandMetricTile(
                    label = "Sản lượng dự kiến",
                    value = "${fmtNum(sc.estimatedHarvestYieldTons, 2)} tấn",
                    sub = "Cao độ tán TB ${fmtNum(s2.canopyHeightMeanM, 1)} m",
                    color = VC.cyan,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Phát hiện sớm sự cố (Early Anomaly Detection: Ngập úng, Thiếu đạm, Ổ sâu bệnh sớm)
        Text(
            "🔍 II. PHÁT HIỆN SỚM SỰ CỐ TRƯỚC KHI XUẤT HIỆN TRIỆU CHỨNG MẮT THƯỜNG (PRE-VISUAL STRESS)",
            color = VC.amber,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        s2.earlyAnomalyDetection.forEach { an ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.amber.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(an.titleVi, color = VC.amber, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("${fmtNum(an.affectedPct, 1)}% (${fmtNum(an.affectedHa, 3)} ha)", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Dấu hiệu quang phổ & DSM: ${an.indicatorUsed} · Mức độ: ${an.severityVi}", color = VC.cyan, fontSize = 12.sp)
                Text("Xử lý tự động: ${an.actionVi}", color = VC.text, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StandMetricTile(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VC.bg)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = VC.text2, fontSize = 12.sp)
    }
}

@Composable
private fun DroneInsuranceAndEcosystemTab(dm: DroneMultispectralReport) {
    val s2 = dm.step2
    val s4 = dm.step4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hồ sơ Bảo hiểm Nông nghiệp (Orthomosaic + DSM Evidence)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.cyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🛡️ I. BẰNG CHỨNG BẢO HIỂM NÔNG NGHIỆP (ORTHOMOSAIC RTK & DSM)",
                    color = VC.cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Mã chứng thư: #${s4.certificateId}",
                    color = VC.lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                "• Diện tích tham gia bảo hiểm: ${fmtNum(s4.totalInsuredAreaHa, 3)} ha · Độ chính xác tọa độ RTK: ±${fmtNum(s4.orthomosaicRtkAccuracyCm, 1)} cm.",
                color = VC.text,
                fontSize = 12.sp,
            )
            Text(
                "• Diện tích thiệt hại xác thực trên Orthomosaic & DSM: ${fmtNum(s4.verifiedDamagedAreaHa, 3)} ha (${fmtNum(s4.verifiedDamagedPct, 1)}%) · Ước tính tổn thất sản lượng: ${fmtNum(s4.estimatedYieldLossTons, 2)} tấn (~${fmtNum(s4.estimatedClaimValueMillionVnd, 1)} triệu VNĐ).",
                color = VC.amber,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(s4.evidenceSummaryVi, color = VC.text2, fontSize = 12.sp)
        }

        // Hệ sinh thái Phần mềm & Thiết bị tương thích
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "💻 II. TƯƠNG THÍCH HỆ SINH THÁI PHẦN MỀM QUANG TRẮC & PHUN BIẾN THIÊN QUỐC TẾ",
                color = VC.teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            s2.compatibleSoftware.forEach { sw ->
                Text("• $sw", color = VC.text, fontSize = 12.sp)
            }
        }
    }
}
