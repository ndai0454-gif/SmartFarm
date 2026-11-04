package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DroughtMonitoringHubCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onApplyOverlay: ((MapOverlay?) -> Unit)? = null,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    simpleMode: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val dr = hud.droughtResult
    if (dr == null) {
        VCard(modifier.fillMaxWidth()) {
            Eyebrow("🇮🇶🌍 26 NĂM THEO DÕI HẠN HÁN & KHÍ HẬU THẢM THỰC VẬT (2000–2025)")
            Text(
                if (hud.loading) "Đang phân tích chuỗi 26 năm (2000–2025) gồm NDVI, VCI, LST, TCI, VHI & Mưa CHIRPS cho vùng \"${hud.regionName}\"…"
                else "Phân tích hạn hán 26 năm (2000–2025) dựa trên MODIS, CHIRPS, TerraClimate, FAO GAUL và đối chuẩn Basra, Iraq (GISology).",
                color = VC.text2,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            VButton(
                if (hud.loading) "Đang phân tích Hạn hán 26 năm…" else "Chạy phân tích Hạn hán 26 năm (2000–2025)",
                {
                    hud.updateDroughtAnalysis(
                        cfg = cfg,
                        studyMode = hud.droughtStudyMode,
                        focusYear = hud.droughtFocusYear,
                        overlayMetric = hud.droughtOverlayMetric,
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
    var activeTab by remember { mutableStateOf("CORE_6") }

    VCard(modifier.fillMaxWidth()) {
        val snap = dr.focusYearSnapshot
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (simpleMode) "☀️🌧 KIỂM TRA NẮNG HẠN, NHIỆT ĐỘ MẶT ĐẤT & NƯỚC TƯỚI" else "🇮🇶🌍 26 NĂM THEO DÕI HẠN HÁN & KHÍ HẬU THẢM THỰC VẬT (2000–2025)")
                Text(
                    if (simpleMode) "Vườn \"${hud.regionName}\" (${fmtNum(dr.areaHa, 2)} ha) · Khu vực: ${dr.faoGaul.adm1Name}"
                    else "Vùng \"${hud.regionName}\" (${fmtNum(dr.areaHa, 2)} ha) · Ranh giới FAO GAUL: ${dr.faoGaul.adm1Name} (${dr.faoGaul.adm0Name})",
                    color = VC.text,
                    fontSize = if (simpleMode) 15.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!simpleMode) {
                    Text(
                        "Đới khí hậu: ${dr.faoGaul.climateZoneVi} · GEE + MODIS + CHIRPS + TerraClimate",
                        color = VC.text2,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // =========================================================================
        // 3 CON SỐ TO RÕ DÀNH CHO NHÀ NÔNG (ÍT CHỮ · NHÌN LÀ HIỂU NGAY)
        // =========================================================================
        val droughtVerdict = when {
            snap.vhiMeanPct >= 50.0 -> Verdict.GOOD
            snap.vhiMeanPct >= 35.0 -> Verdict.WARN
            else -> Verdict.BAD
        }
        val shortAdviceVi = when {
            snap.vhiMeanPct >= 50.0 -> "Đủ ẩm, tưới nước bình thường"
            snap.vhiMeanPct >= 35.0 -> "Hơi nóng, nên tưới thêm sáng sớm"
            else -> "Đang khô hạn! Cần tưới đẫm giữ ẩm ngay"
        }
        VerdictBanner(
            verdict = droughtVerdict,
            headline = "${snap.droughtClassVi} (${fmtNum(snap.vhiMeanPct, 0)}/100 điểm) — $shortAdviceVi",
            subtext = null,
        )

        Spacer(Modifier.height(10.dp))

        // 3 Ô số to (28sp) — tối đa 4-5 chữ mỗi dòng
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).border(1.dp, VC.border, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🌡 Nhiệt độ đất", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${fmtNum(snap.lstMeanC, 1)}°C", color = VC.amber, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Đất trống: ${fmtNum(snap.lstZones.desertOrBareSoilC, 0)}°C · Nên giữ cỏ",
                    color = VC.text2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).border(1.dp, VC.border, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🌧 Lượng mưa năm", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${fmtNum(snap.rainfallMm, 0)} mm", color = VC.cyan, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (snap.droughtAffectedPct > 20) "${fmtNum(snap.droughtAffectedPct, 0)}% vườn thiếu ẩm" else "Độ ẩm đất ổn định",
                    color = if (snap.droughtAffectedPct > 20) VC.amber else VC.mint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(VC.elevated).border(1.dp, VC.border, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🌿 Độ xanh tán lá", color = VC.text2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${fmtNum(snap.vciMeanPct, 0)}%", color = VC.mint, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${fmtNum(dr.ndviChange2000To2025.netRecoveredPct, 0)}% vườn xanh tốt lên",
                    color = VC.mint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Hiển thị trực tiếp Bản đồ Chuyên đề Hạn hán ngay trong thẻ
        hud.droughtBitmap?.let { bmp ->
            Spacer(Modifier.height(10.dp))
            var showInlineMap by remember { mutableStateOf(true) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (simpleMode) "🗺 Bản đồ màu báo vùng khô nóng & sức khỏe cây (${dr.focusYear})"
                        else "🗺 Bản đồ Chuyên đề Hạn hán (${hud.droughtOverlayMetric} · Năm ${dr.focusYear}) · ${dr.focusYearSnapshot.droughtClassVi}",
                        color = VC.mint,
                        fontSize = if (simpleMode) 14.sp else 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (showInlineMap) "Ẩn bản đồ ▲" else "Mở bản đồ ▼",
                        color = VC.cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showInlineMap = !showInlineMap }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (showInlineMap) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Bản đồ hạn hán ${hud.droughtOverlayMetric} (${dr.focusYear})",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, VC.border, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // Bọc toàn bộ thông số khoa học 26 năm (MODIS, CHIRPS, TerraClimate, 4 năm tham chiếu, đối chuẩn Basra Iraq) vào TechnicalDetails khi ở Chế độ Nông dân
        TechnicalDetails(simpleMode, Modifier.padding(top = 8.dp)) {
            Spacer(Modifier.height(6.dp))

        // Thanh chuyển đổi Chế độ nghiên cứu (Vùng khoanh tại chỗ vs Chuẩn Basra, Iraq) & Năm tham chiếu + Lớp phủ
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "LOCAL_AND_BASRA" to "🌿 Vùng khoanh + Đối chuẩn Basra",
                "BASRA_IRAQ_REFERENCE" to "🇮🇶 Mô phỏng chuẩn Basra, Iraq (2000–2025)",
            ).forEach { (modeKey, label) ->
                val selected = hud.droughtStudyMode == modeKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) VC.cyan.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (selected) VC.cyan else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroughtAnalysis(
                                cfg = cfg,
                                studyMode = modeKey,
                                focusYear = hud.droughtFocusYear,
                                overlayMetric = hud.droughtOverlayMetric,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        color = if (selected) VC.cyan else VC.text2,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }

            // Chọn nhanh năm tham chiếu & năm cực trị (2000, 2010, 2018, 2020, 2021, 2025)
            listOf(
                2000 to "2000",
                2010 to "2010",
                2018 to "2018 (Mưa đỉnh)",
                2020 to "2020",
                2021 to "2021 (Hạn đáy)",
                2025 to "2025",
            ).forEach { (yr, label) ->
                val isYr = hud.droughtFocusYear == yr
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isYr) VC.amber.copy(alpha = 0.24f) else VC.elevated)
                        .border(1.dp, if (isYr) VC.amber else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroughtAnalysis(
                                cfg = cfg,
                                studyMode = hud.droughtStudyMode,
                                focusYear = yr,
                                overlayMetric = hud.droughtOverlayMetric,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        "📅 $label",
                        color = if (isYr) VC.amber else VC.text,
                        fontSize = 11.sp,
                        fontWeight = if (isYr) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Thanh chọn lớp phủ bản đồ chuyên đề (VHI, VCI, LST, TCI, NDVI, DELTA_NDVI)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🗺 Phủ bản đồ chuyên đề:", color = VC.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            listOf(
                "VHI" to "🌾 VHI (Sức khỏe TH)",
                "VCI" to "🌱 VCI (Thực vật)",
                "LST" to "🌡 LST (Nhiệt bề mặt)",
                "TCI" to "🔥 TCI (Điều kiện nhiệt)",
                "NDVI" to "🌿 NDVI",
                "DELTA_NDVI" to "🔄 ΔNDVI (2000→2025)",
            ).forEach { (mKey, mLabel) ->
                val isM = hud.droughtOverlayMetric == mKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isM) VC.lime.copy(alpha = 0.22f) else VC.elevated)
                        .border(1.dp, if (isM) VC.lime else VC.border, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroughtAnalysis(
                                cfg = cfg,
                                studyMode = hud.droughtStudyMode,
                                focusYear = hud.droughtFocusYear,
                                overlayMetric = mKey,
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        mLabel,
                        color = if (isM) VC.lime else VC.text2,
                        fontSize = 11.sp,
                        fontWeight = if (isM) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        // 4 Tab chuyên đề
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "CORE_6" to "📡 1. 6 Chỉ số Hạn hán & Phân vùng Nhiệt LST (${dr.focusYear})",
                "REF_4Y" to "🗺 2. So sánh 4 Năm (2000·2010·2020·2025) & Biến động ΔNDVI",
                "SERIES_26Y" to "📊 3. Chuỗi 26 Năm (2000–2025) & Độ trễ Mưa – Thực vật",
                "BASRA_TECH" to "🇮🇶 4. Đối chuẩn Basra, Iraq (GISology) & Công nghệ GEE",
            ).forEach { (tabKey, tabTitle) ->
                val active = activeTab == tabKey
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                        .border(if (active) 1.5.dp else 1.dp, if (active) VC.mint else VC.border, RoundedCornerShape(10.dp))
                        .clickable { activeTab = tabKey }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(
                        tabTitle,
                        color = if (active) VC.mint else VC.text,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            "CORE_6" -> DroughtCoreSixTab(dr)
            "REF_4Y" -> DroughtRefYearsAndChangeTab(dr, hud, cfg, scope, onApplyOverlay, onToast)
            "SERIES_26Y" -> DroughtTimeSeriesAndLagTab(dr)
            "BASRA_TECH" -> DroughtBasraAndTechTab(dr)
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DroughtCoreSixTab(dr: DroughtMonitoringReport) {
    val snap = dr.focusYearSnapshot
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tóm tắt trạng thái hạn hán của năm đang chọn
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, if (snap.vhiMeanPct >= 40.0) VC.mint else VC.amber, RoundedCornerShape(10.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🌾 TỔNG HỢP NĂM ${dr.focusYear}: ${snap.droughtClassVi.uppercase()}",
                    color = if (snap.vhiMeanPct >= 40.0) VC.mint else VC.amber,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    snap.spatialFindingVi,
                    color = VC.text,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "VHI ${fmtNum(snap.vhiMeanPct, 1)}%",
                    color = VC.lime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Chịu hạn: ${fmtNum(snap.droughtAffectedPct, 1)}% (${fmtNum(snap.droughtAffectedHa, 2)} ha)",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }
        }

        // Lưới 6 chỉ số cốt lõi (NDVI, VCI, LST, TCI, VHI, Lượng mưa CHIRPS)
        Text(
            "I. 6 CHỈ SỐ CỐT LÕI ĐÁNH GIÁ HẠN HÁN ĐA CHIỀU (MODIS · CHIRPS · TERRACLIMATE)",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        dr.sixCoreIndices.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { item ->
                    val accent = when (item.code) {
                        "NDVI", "VCI" -> VC.mint
                        "LST", "TCI" -> VC.amber
                        "VHI" -> VC.lime
                        else -> VC.cyan
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VC.elevated)
                            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${item.icon} ${item.nameVi}",
                                color = accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${fmtNum(item.focusYearValue, if (item.code == "NDVI") 3 else 1)} ${item.unit.substringBefore(" ")}",
                                color = VC.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Text(
                            "Công thức: ${item.formula}",
                            color = VC.cyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Nguồn: ${item.satelliteSource} · Biên 26 năm: ${item.range26yText}",
                            color = VC.text2,
                            fontSize = 10.5.sp,
                        )
                        Text(
                            item.roleVi,
                            color = VC.text,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Phân tích Chênh lệch Nhiệt độ Bề mặt (LST) giữa 3 bề mặt đặc trưng
        val lz = snap.lstZones
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.amber.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "🌡️ II. CHÊNH LỆCH NHIỆT ĐỘ BỀ MẶT ĐẤT (LST) THEO ĐẶC ĐIỂM LỚP PHỦ (NĂM ${dr.focusYear})",
                color = VC.amber,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LstZoneMiniBox(
                    title = "🏜 Đất trống / Sa mạc",
                    tempC = lz.desertOrBareSoilC,
                    sub = "Hấp thụ bức xạ mạnh",
                    color = Color(0xFFEF5350),
                    modifier = Modifier.weight(1f),
                )
                LstZoneMiniBox(
                    title = "🌳 Thảm thực vật / Vườn",
                    tempC = lz.vegetatedCanopyC,
                    sub = "Làm mát thoát hơi nước",
                    color = VC.amber,
                    modifier = Modifier.weight(1f),
                )
                LstZoneMiniBox(
                    title = "🌊 Ven nguồn nước / Kênh",
                    tempC = lz.waterAdjacentRiparianC,
                    sub = "Đệm nhiệt thủy vực",
                    color = VC.cyan,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "• Chênh lệch nhiệt Đất trống vs. Ven nguồn nước: +${fmtNum(lz.bareVsWaterGradientC, 1)}°C | Đất trống vs. Tán thực vật: +${fmtNum(lz.bareVsCanopyGradientC, 1)}°C. Nhiệt độ bề mặt cao chiếm ưu thế tại các khoảnh thiếu thảm che phủ, thúc đẩy bốc hơi nước và hạ thấp chỉ số TCI.",
                color = VC.text2,
                fontSize = 11.sp,
            )
        }

        // Phân bố diện tích 5 cấp hạn hán theo VHI
        val vb = snap.vhiBreakdown
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.dp, VC.border, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "🌾 III. PHÂN BỐ CẤP ĐỘ HẠN HÁN THEO CHỈ SỐ SỨC KHỎE THỰC VẬT (VHI = 0.5×VCI + 0.5×TCI)",
                color = VC.lime,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                val segs = listOf(
                    vb.extremeDroughtPct to Color(0xFF7F0000),
                    vb.severeDroughtPct to Color(0xFFD7301F),
                    vb.moderateDroughtPct to Color(0xFFFC8D59),
                    vb.mildDroughtPct to Color(0xFFFEE08B),
                    vb.noDroughtPct to Color(0xFF1A9850),
                )
                segs.forEach { (pct, col) ->
                    if (pct > 0.2) {
                        Box(
                            Modifier
                                .weight(pct.toFloat().coerceAtLeast(0.5f))
                                .fillMaxHeight()
                                .background(col),
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VhiLegendChip("Cực đoan (<10)", vb.extremeDroughtPct, vb.extremeDroughtHa, Color(0xFFEF5350))
                VhiLegendChip("Nghiêm trọng (10–20)", vb.severeDroughtPct, vb.severeDroughtHa, Color(0xFFFF7043))
                VhiLegendChip("Trung bình (20–30)", vb.moderateDroughtPct, vb.moderateDroughtHa, VC.amber)
                VhiLegendChip("Hạn nhẹ (30–40)", vb.mildDroughtPct, vb.mildDroughtHa, Color(0xFFFFEE58))
                VhiLegendChip("Không hạn (≥40)", vb.noDroughtPct, vb.noDroughtHa, VC.mint)
            }
        }
    }
}

@Composable
private fun LstZoneMiniBox(
    title: String,
    tempC: Double,
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
        Text(title, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Text("${fmtNum(tempC, 1)} °C", color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = VC.text2, fontSize = 10.sp)
    }
}

@Composable
private fun VhiLegendChip(label: String, pct: Double, ha: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            "$label: ${fmtNum(pct, 1)}% (${fmtNum(ha, 2)} ha)",
            color = VC.text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DroughtRefYearsAndChangeTab(
    dr: DroughtMonitoringReport,
    hud: RegionHudState,
    cfg: SatelliteConfig,
    scope: kotlinx.coroutines.CoroutineScope,
    onApplyOverlay: ((MapOverlay?) -> Unit)?,
    onToast: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "I. SO SÁNH ĐA THỜI ĐIỂM QUA 4 NĂM THAM CHIẾU CHUẨN: 2000 · 2010 · 2020 · 2025",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dr.referenceYearsComparison.forEach { snap ->
                val isSelected = hud.droughtFocusYear == snap.year
                Column(
                    Modifier
                        .width(215.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) VC.mint.copy(alpha = 0.12f) else VC.elevated)
                        .border(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) VC.mint else VC.border,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            hud.updateDroughtAnalysis(
                                cfg = cfg,
                                studyMode = hud.droughtStudyMode,
                                focusYear = snap.year,
                                overlayMetric = "VHI",
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
                            "📅 NĂM ${snap.year}",
                            color = if (isSelected) VC.mint else VC.amber,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "VHI ${fmtNum(snap.vhiMeanPct, 1)}%",
                            color = VC.lime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text(
                        snap.droughtClassVi,
                        color = VC.text,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "• NDVI: ${fmtNum(snap.ndviMean, 3)} | VCI: ${fmtNum(snap.vciMeanPct, 1)}%",
                        color = VC.mint,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        "• LST: ${fmtNum(snap.lstMeanC, 1)}°C | TCI: ${fmtNum(snap.tciMeanPct, 1)}%",
                        color = VC.amber,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        "• Mưa CHIRPS: ${fmtNum(snap.rainfallMm, 1)} mm (${if (snap.rainfallAnomalyPct >= 0) "+" else ""}${fmtNum(snap.rainfallAnomalyPct, 1)}%)",
                        color = VC.cyan,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        "• Diện tích chịu hạn: ${fmtNum(snap.droughtAffectedPct, 1)}% (${fmtNum(snap.droughtAffectedHa, 2)} ha)",
                        color = VC.text2,
                        fontSize = 10.5.sp,
                    )
                    Text(
                        "• ΔLST (Đất trống - Nước): +${fmtNum(snap.lstZones.bareVsWaterGradientC, 1)}°C",
                        color = VC.text2,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // Bản đồ biến động NDVI (2000–2025)
        val chg = dr.ndviChange2000To2025
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.teal.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "🛰️ II. BẢN ĐỒ BIẾN ĐỘNG THẢM THỰC VẬT ΔNDVI GIAI ĐOẠN 2000–2025 (ΔNDVI = NDVI₂₀₂₅ - NDVI₂₀₀₀)",
                        color = VC.teal,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "NDVI trung bình 2000: ${fmtNum(chg.ndvi2000Mean, 3)} → Năm 2025: ${fmtNum(chg.ndvi2025Mean, 3)} | ΔNDVI TB = ${if (chg.deltaNdviMean >= 0) "+" else ""}${fmtNum(chg.deltaNdviMean, 4)}",
                        color = VC.text,
                        fontSize = 11.sp,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VC.lime.copy(alpha = 0.22f))
                        .border(1.dp, VC.lime, RoundedCornerShape(8.dp))
                        .clickable {
                            hud.updateDroughtAnalysis(
                                cfg = cfg,
                                studyMode = hud.droughtStudyMode,
                                focusYear = 2025,
                                overlayMetric = "DELTA_NDVI",
                                scope = scope,
                                onOverlay = onApplyOverlay,
                                onDone = onToast,
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text("🗺 Phủ bản đồ ΔNDVI", color = VC.lime, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            chg.changeClasses.forEach { cls ->
                val barColor = when (cls.classCode) {
                    "SEVERE_VEG_LOSS" -> Color(0xFFEF5350)
                    "MODERATE_VEG_DECLINE" -> Color(0xFFFF8A65)
                    "STABLE_VEGETATION" -> VC.cyan
                    "MODERATE_RECOVERY" -> VC.mint
                    else -> VC.lime
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(cls.labelVi, color = VC.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${fmtNum(cls.areaPct, 1)}% (${fmtNum(cls.areaHa, 3)} ha)",
                            color = barColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(VC.bg),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((cls.areaPct / 100.0).toFloat().coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(barColor),
                        )
                    }
                }
            }

            Text(
                chg.interpretationVi,
                color = VC.text2,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DroughtTimeSeriesAndLagTab(dr: DroughtMonitoringReport) {
    val series = dr.annualTimeSeries
    val dec = dr.decouplingAnalysis

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "I. BIỂU ĐỒ CHUỖI THỜI GIAN 26 NĂM (2000–2025): LƯỢNG MƯA CHIRPS · VHI · VCI · TCI",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        // Biểu đồ khoa học 26 năm (2000–2025)
        if (series.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "📊 Cột: Mưa CHIRPS (mm/năm) | Đường: VHI (Xanh) · VCI (Ngọc) · TCI (Cam)",
                        color = VC.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Đỉnh mưa: ${dec.wettestYear} (${fmtNum(dec.wettestRainfallMm, 0)}mm) · Đáy mưa: ${dec.driestYear} (${fmtNum(dec.driestRainfallMm, 0)}mm)",
                        color = VC.cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .background(VC.bg, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    val n = series.size
                    if (n < 2) return@Canvas
                    val maxRain = (series.maxOfOrNull { it.rainfallMm } ?: 1.0).coerceAtLeast(10.0)
                    val w = size.width
                    val h = size.height
                    val stepX = w / n.toFloat()

                    // Ngưỡng hạn VHI = 40%
                    val y40 = h * (1f - 0.40f)
                    drawLine(
                        color = Color(0xFFFFB74D).copy(alpha = 0.45f),
                        start = Offset(0f, y40),
                        end = Offset(w, y40),
                        strokeWidth = 1.5f,
                    )

                    // Cột lượng mưa hàng năm (2000 -> 2025)
                    series.forEachIndexed { idx, pt ->
                        val frac = (pt.rainfallMm / maxRain).toFloat().coerceIn(0.04f, 1f)
                        val barH = h * 0.78f * frac
                        val barW = (stepX * 0.56f).coerceAtLeast(4f)
                        val x = idx * stepX + (stepX - barW) * 0.5f
                        val barCol = when (pt.year) {
                            dec.wettestYear -> Color(0xFF00E5FF)
                            dec.driestYear -> Color(0xFFFF5252)
                            in listOf(2000, 2010, 2020, 2025) -> Color(0xFF4DB6AC)
                            else -> Color(0xFF26A69A).copy(alpha = 0.42f)
                        }
                        drawRoundRect(
                            color = barCol,
                            topLeft = Offset(x, h - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }

                    // Đường VCI (%)
                    val vciPath = Path()
                    series.forEachIndexed { idx, pt ->
                        val cx = idx * stepX + stepX * 0.5f
                        val cy = h * (1f - (pt.vciPct / 100.0).toFloat().coerceIn(0.02f, 0.98f))
                        if (idx == 0) vciPath.moveTo(cx, cy) else vciPath.lineTo(cx, cy)
                    }
                    drawPath(vciPath, color = Color(0xFF64FFDA), style = Stroke(width = 2.0f))

                    // Đường TCI (%)
                    val tciPath = Path()
                    series.forEachIndexed { idx, pt ->
                        val cx = idx * stepX + stepX * 0.5f
                        val cy = h * (1f - (pt.tciPct / 100.0).toFloat().coerceIn(0.02f, 0.98f))
                        if (idx == 0) tciPath.moveTo(cx, cy) else tciPath.lineTo(cx, cy)
                    }
                    drawPath(tciPath, color = Color(0xFFFFB74D), style = Stroke(width = 2.0f))

                    // Đường VHI (%) nổi bật
                    val vhiPath = Path()
                    series.forEachIndexed { idx, pt ->
                        val cx = idx * stepX + stepX * 0.5f
                        val cy = h * (1f - (pt.vhiPct / 100.0).toFloat().coerceIn(0.02f, 0.98f))
                        if (idx == 0) vhiPath.moveTo(cx, cy) else vhiPath.lineTo(cx, cy)
                        drawCircle(
                            color = if (pt.vhiPct < 30.0) Color(0xFFFF5252) else Color(0xFFCCFF90),
                            radius = if (pt.isReferenceYear || pt.year == 2018 || pt.year == 2021) 4.2f else 2.4f,
                            center = Offset(cx, cy),
                        )
                    }
                    drawPath(vhiPath, color = Color(0xFFCCFF90), style = Stroke(width = 3.0f))
                }

                // Nhãn trục năm
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("2000", "2005", "2010", "2015", "2018 (Đỉnh mưa)", "2021 (Đáy mưa)", "2025").forEach { lbl ->
                        Text(lbl, color = VC.text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Phân tích Độ trễ & Giải thích tại sao mưa tăng không đồng nghĩa thực vật phục hồi ngay
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.amber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "🧠 II. TẠI SAO LƯỢNG MƯA TĂNG KHÔNG ĐỒNG NGHĨA THẢM THỰC VẬT PHỤC HỒI NGAY LẬP TỨC?",
                color = VC.amber,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                dec.keyPrincipleVi,
                color = VC.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                dec.case2018Vs2021SummaryVi,
                color = VC.cyan,
                fontSize = 11.sp,
            )

            // Bảng ma trận tương quan & độ trễ
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CorrMetricPill(
                    title = "r(Mưa, NDVI cùng năm)",
                    value = fmtNum(dec.pearsonRRainNdviSameYear, 2),
                    sub = "Phản ứng tức thời yếu/vừa",
                    color = VC.amber,
                    modifier = Modifier.weight(1f),
                )
                CorrMetricPill(
                    title = "r(Mưa, NDVI trễ 1 năm)",
                    value = fmtNum(dec.pearsonRRainNdviLag1Y, 2),
                    sub = "Phục hồi trễ sau tích ẩm",
                    color = VC.mint,
                    modifier = Modifier.weight(1f),
                )
                CorrMetricPill(
                    title = "r(LST, NDVI)",
                    value = fmtNum(dec.pearsonRLstNdvi, 2),
                    sub = "Nhiệt cao ức chế thực vật",
                    color = Color(0xFFEF5350),
                    modifier = Modifier.weight(1f),
                )
                CorrMetricPill(
                    title = "r(PET, VHI)",
                    value = fmtNum(dec.pearsonRPetVhi, 2),
                    sub = "Bốc thoát hơi gây stress",
                    color = VC.cyan,
                    modifier = Modifier.weight(1f),
                )
            }

            // 5 yếu tố chi phối phản ứng của lớp phủ thực vật
            dec.drivingFactors.forEach { f ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(VC.bg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(f.titleVi, color = VC.lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Trọng số tác động: ${fmtNum(f.impactWeightPct, 0)}%", color = VC.amber, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(f.explanationVi, color = VC.text2, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun CorrMetricPill(
    title: String,
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
            .padding(7.dp),
    ) {
        Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = VC.text2, fontSize = 9.5.sp)
    }
}

@Composable
private fun DroughtBasraAndTechTab(dr: DroughtMonitoringReport) {
    val b = dr.basraReference
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hồ sơ chuẩn Basra, Iraq (2000–2025)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.cyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "🇮🇶 I. ĐỐI CHUẨN NGHIÊN CỨU BASRA, MIỀN NAM IRAQ (GISology 2000–2025)",
                color = VC.cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "${b.regionNameVi} · ${b.admBoundary}",
                color = VC.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VC.bg)
                        .border(1.dp, VC.cyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Text("🌧 Năm 2018 — Lượng mưa cao nhất", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${fmtNum(b.wettest2018RainfallMm, 1)} mm/năm", color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "NDVI = ${fmtNum(b.wettest2018Ndvi, 3)} · VHI = ${fmtNum(b.wettest2018VhiPct, 1)}% (Chưa phục hồi ngay trong năm 2018 do nền nhiệt & độ mặn)",
                        color = VC.text2,
                        fontSize = 10.5.sp,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VC.bg)
                        .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Text("🔥 Năm 2021 — Lượng mưa thấp nhất", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${fmtNum(b.driest2021RainfallMm, 1)} mm/năm", color = VC.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "NDVI = ${fmtNum(b.driest2021Ndvi, 3)} · LST = ${fmtNum(b.driest2021LstC, 1)}°C · VHI = ${fmtNum(b.driest2021VhiPct, 1)}% (Hạn cực đoan)",
                        color = VC.text2,
                        fontSize = 10.5.sp,
                    )
                }
            }
            Text(
                b.summaryInsightVi,
                color = VC.text,
                fontSize = 11.sp,
            )
        }

        // Công nghệ & Nguồn dữ liệu Vệ tinh
        Text(
            "🛠️ II. CÔNG NGHỆ & NGUỒN DỮ LIỆU VIỄN THÁM – KHÍ HẬU TÍCH HỢP",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        dr.technologyAndDatasets.forEach { tech ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.border, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("• ${tech.component}", color = VC.mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(tech.collections, color = VC.cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(tech.purposeVi, color = VC.text2, fontSize = 10.5.sp)
            }
        }

        // Khuyến nghị quản lý tài nguyên nước & thích ứng hạn hán
        if (dr.agronomicRecommendationsVi.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.lime.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "🎯 III. KHUYẾN NGHỊ QUẢN LÝ TÀI NGUYÊN NƯỚC, CHỐNG SA MẠC HÓA & THÍCH ỨNG KHÍ HẬU",
                    color = VC.lime,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                dr.agronomicRecommendationsVi.forEach { rec ->
                    Text("• $rec", color = VC.text, fontSize = 11.sp)
                }
            }
        }
    }
}
