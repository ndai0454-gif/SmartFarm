package com.example.test_tablet.ui.vwing

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AgPaperTab(val shortLabel: String, val badge: String) {
    OVERVIEW_ALL("📚 Tổng hợp 9 Tài liệu", "1–9"),
    P1_P6_AI_SSCM("🤖 1 & 6 · AI + JATAED 2025", "Mục 1, 6"),
    P2_TLS_BIOMASS("🌳 2 · Sinh khối TLS + Drone", "Mục 2"),
    P34_NOWCASTING("🌧️ 3–4 · Mưa Dual-Attention RNN", "Mục 3–4"),
    P5_TOPSOIL_SAND("🏜️ 5 · Cát Tầng mặt Planosol", "Mục 5"),
    P7_P8_VISION("🛰️ 7 & 8 · MSTA-YOLO + HRNet-CA", "Mục 7, 8"),
    P9_PGIS_SOURCES("🗺️ 9 · 10 Nguồn GIS Mở P-GIS", "Mục 9"),
}

/**
 * Thẻ Trung tâm Nghiên cứu AI & Địa không gian 9 Tài liệu Khoa học trên vùng vườn đang khoanh:
 * 1. AI in Agriculture [Full Book] (Vahe Aslanyan, freeCodeCamp, 01/2025) — Mục tiêu +70% năng suất 2030
 * 2. Biomass Estimation of Apple & Citrus Trees Using TLS & Drone-Mounted RGB Sensors
 * 3–4. Enhancing Precipitation Nowcasting Through Dual-Attention RNN (Radar VIL + Satellite IR; phát hiện trùng 3 & 4)
 * 5. High-Resolution Mapping of Topsoil Sand Content in Planosol Regions (Sentinel-2 VIS + SWIR + ML)
 * 6. JATAED1stpaperJanuary2026.pdf — Jamal et al., JATAED Vol 2(2), 04/2025, DOI: 10.62671/jataed.v2i2.88
 * 7. Landslide Detection with MSTA-YOLO in Remote Sensing Images (Yang et al., 2025 · Cụm B)
 * 8. Semantic Segmentation of Rice Fields with HRNet-CA-Enhanced DeepLabV3+ (Shao et al., 2025 · Cụm C)
 * 9. Tốp 10 nguồn dữ liệu GIS miễn phí (Phần 2) - P-GIS
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgResearch9PapersCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onSelectOverlay: (MapOverlay?) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(AgPaperTab.OVERVIEW_ALL) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val suite = hud.agResearchSuite
    val openPts = remember(hud.points) { Geo.open(hud.points) }

    fun refreshSuite(applyHighResOverlay: Boolean = false) {
        if (!cfg.configured || openPts.size < 3) return
        busy = true
        err = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                SatelliteClient.agResearchSuite(cfg, openPts, SatCropKey.DURIAN)
            }
            res.onSuccess { rep ->
                hud.agResearchSuite = rep
                val bmp = rep.highresGardenImageUrl?.let { url ->
                    withContext(Dispatchers.IO) {
                        SatelliteClient.image(cfg, url).getOrNull()?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    }
                }
                if (bmp != null) {
                    hud.geeClarityTrueColorBitmap = bmp
                    if (applyHighResOverlay) {
                        hud.selectedQuickLayer = "✨ GEE Siêu nét"
                        onSelectOverlay(
                            MapOverlay(
                                rep.bounds,
                                bmp,
                                0.88f,
                                "Vùng vườn Siêu nét Zoom 20 · ${rep.cropLabelVi} (~${rep.estimatedTrees} cây)",
                            ),
                        )
                    }
                }
                if (applyHighResOverlay) {
                    onToast("Đã làm rõ vùng khoanh Zoom 20 & cập nhật bộ 9 nghiên cứu AI Nông nghiệp.")
                }
            }.onFailure { e ->
                err = e.message ?: "Không tải được bộ 9 mô-đun nghiên cứu AI & GIS."
            }
            busy = false
        }
    }

    LaunchedEffect(openPts, cfg.url, cfg.token) {
        if (suite == null && cfg.configured && openPts.size >= 3) {
            refreshSuite(applyHighResOverlay = false)
        }
    }

    VCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("CHỈ SỐ VỆ TINH & AI NÔNG NGHIỆP")
                Text(
                    "📊 Tổng hợp Sinh khối, Dự báo Mưa, Thổ nhưỡng & Nguồn GIS Mở",
                    color = VC.text,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = VC.mint, strokeWidth = 2.2.dp)
            }
        }

        // Thanh hành động nhanh: Làm nét vùng khoanh 100% hoặc phủ lớp GEE Zoom 20
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "🛰️ Vùng khoanh Rõ nét Gốc (Không phủ mờ)",
                color = Color(0xFF07150D),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.mint)
                    .clickable {
                        hud.selectedQuickLayer = "Tắt phủ"
                        onSelectOverlay(null)
                        onToast("Đã tắt mọi lớp phủ mờ — hiển thị 100% ảnh vệ tinh gốc sắc nét trong vùng khoanh.")
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "✨ Phủ GEE Siêu nét Zoom 20 + Viền Tán",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1565C0))
                    .clickable(enabled = !busy && cfg.configured && openPts.size >= 3) {
                        val cachedBmp = hud.geeClarityTrueColorBitmap
                        if (cachedBmp != null && suite != null) {
                            hud.selectedQuickLayer = "✨ GEE Siêu nét"
                            onSelectOverlay(
                                MapOverlay(
                                    suite.bounds,
                                    cachedBmp,
                                    0.88f,
                                    "Vùng vườn Siêu nét Zoom 20 · ${suite.cropLabelVi} (~${suite.estimatedTrees} cây)",
                                ),
                            )
                            onToast("Đã bật lớp ảnh GEE Siêu nét Zoom 20 (1024px CLAHE + Unsharp) cho vùng khoanh.")
                        } else {
                            refreshSuite(applyHighResOverlay = true)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "🔄 Chạy lại 9 Mô-đun",
                color = VC.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .clickable(enabled = !busy && cfg.configured && openPts.size >= 3) {
                        refreshSuite(applyHighResOverlay = false)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // Thanh chuyển Tab giữa 9 tài liệu
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AgPaperTab.entries.forEach { tab ->
                val sel = activeTab == tab
                Text(
                    tab.shortLabel,
                    color = if (sel) Color(0xFF07150D) else VC.text2,
                    fontSize = 12.5.sp,
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (sel) VC.amber else VC.elevated)
                        .clickable { activeTab = tab }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        err?.let {
            Notice(it, VC.amber, Modifier.padding(top = 8.dp))
        }

        if (suite == null) {
            Text(
                if (openPts.size < 3) "Hãy khoanh vùng vườn (ít nhất 3 đỉnh) để tính toán chỉ số từ 9 tài liệu nghiên cứu."
                else "Đang tổng hợp dữ liệu vệ tinh GEE & 9 mô-đun nghiên cứu cho vùng vườn…",
                color = VC.muted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@VCard
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            AgPaperTab.OVERVIEW_ALL -> {
                Text(
                    suite.catalogSummaryVi,
                    color = VC.mint,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Kpi(
                        "1. Tiềm năng Năng suất AI",
                        "+${fmtNum(suite.p1ProjectedYieldGainPct, 1)}",
                        "%",
                        "Mục tiêu sách Aslanyan 2030: +70% · Tiết kiệm ${fmtNum(suite.p1ResourceSavingPct, 0)}% nước/NPK",
                        Modifier.weight(1f),
                    )
                    Kpi(
                        "2. Sinh khối TLS + Drone",
                        fmtNum(suite.p2TotalAgbTonnes, 2),
                        "tấn",
                        "${fmtNum(suite.p2AgbKgPerTree, 1)} kg/cây · Carbon ${fmtNum(suite.p2CarbonCo2eTonnes, 1)} tCO₂e",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Kpi(
                        "3–4. Mưa 1h–3h (Dual-RNN)",
                        "${fmtNum(suite.p34Rain1hMm, 1)} / ${fmtNum(suite.p34Rain3hMm, 1)}",
                        "mm",
                        "VIL ${fmtNum(suite.p34RadarVilKgM2, 1)} kg/m² · Giảm sai số cực đại ${fmtNum(suite.p34BiasReductionPct, 0)}%",
                        Modifier.weight(1f),
                    )
                    Kpi(
                        "5. Cát tầng mặt (Planosol)",
                        fmtNum(suite.p5TopsoilSandPct, 1),
                        "% cát",
                        "${suite.p5SoilTextureVi} · Sét ${fmtNum(suite.p5TopsoilClayPct, 0)}% · Limon ${fmtNum(suite.p5TopsoilSiltPct, 0)}%",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Kpi(
                        "6. JATAED 2025 (SSCM)",
                        fmtNum(suite.p6SscmReadinessPct, 0),
                        "%",
                        "DOI ${suite.p6Doi} · Digital Twin ${fmtNum(suite.p6DigitalTwinMaturityPct, 0)}%",
                        Modifier.weight(1f),
                    )
                    Kpi(
                        "7 & 8. MSTA-YOLO / HRNet",
                        "F1 ${fmtNum(suite.p8BoundaryF1, 2)}",
                        "",
                        "Sạt lở MSTA-YOLO: ${fmtNum(suite.p7SusceptibilityScore, 0)}/100 · mIoU ${fmtNum(suite.p8MeanIou, 2)}",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C2116))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("✅ Kiểm tra danh sách 9 tài liệu đã nạp vào hệ thống:", color = VC.mint, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text("• [1] AI in Agriculture [Full Book] (Vahe Aslanyan, freeCodeCamp 01/2025): 3 trụ cột AI hướng tới +70% năng suất năm 2030.", color = VC.text2, fontSize = 12.5.sp)
                    Text("• [2] Biomass Estimation of Apple & Citrus Trees (TLS + Drone RGB): Thể tích tán 3D V_TLS = ${fmtNum(suite.p2TlsCrownVolumeM3, 1)} m³/cây.", color = VC.text2, fontSize = 12.5.sp)
                    Text("• [3–4] Enhancing Precipitation Nowcasting Through Dual-Attention RNN: ⚠ Xuất hiện 2 lần ở mục 3 & 4 khi import — đã tự động hợp nhất.", color = VC.amber, fontSize = 12.5.sp)
                    Text("• [5] High-Resolution Mapping of Topsoil Sand Content in Planosol Regions: Sentinel-2 VIS + SWIR ước tính ${fmtNum(suite.p5TopsoilSandPct, 1)}% cát.", color = VC.text2, fontSize = 12.5.sp)
                    Text("• [6] JATAED1stpaperJanuary2026.pdf (Jamal et al., JATAED Vol 2(2), 04/2025, DOI ${suite.p6Doi}): Nguồn chính của notebook — đã xác minh.", color = VC.text2, fontSize = 12.5.sp)
                    Text("• [7] Landslide Detection with MSTA-YOLO (Yang et al. 2025, Cụm B) & [8] HRNet-CA DeepLabV3+ (Shao et al. 2025, Cụm C): Đã đồng bộ từ Vault.", color = VC.text2, fontSize = 12.5.sp)
                    Text("• [9] Tốp 10 nguồn dữ liệu GIS miễn phí (Phần 2 - P-GIS): 5 nhóm LiDAR DEM, Vệ tinh, Dân số WorldPop, Địa giới & Đất mở.", color = VC.text2, fontSize = 12.5.sp)
                }
            }

            AgPaperTab.P1_P6_AI_SSCM -> {
                Eyebrow("MỤC 1 · AI IN AGRICULTURE [FULL BOOK] (VAHE ASLANYAN, FREECODECAMP 01/2025)")
                Text(
                    "Mục tiêu tăng năng suất +70% vào năm 2030 (tiềm năng vườn hiện tại +${fmtNum(suite.p1ProjectedYieldGainPct, 1)}%) · " +
                        "Chỉ số đất bền vững ${fmtNum(suite.p1SoilSustainabilityIdx, 1)}/100 · Tự động hóa lao động ${fmtNum(suite.p1LaborAutomationPct, 1)}%",
                    color = VC.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                suite.p1Pillars.forEach { p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VC.elevated)
                            .padding(9.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.pillar, color = VC.mint, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Text("${fmtNum(p.scorePct, 1)}%", color = VC.amber, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(p.actionVi, color = VC.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Divider2()
                Spacer(Modifier.height(8.dp))
                Eyebrow("MỤC 6 · JATAED VOL 2(2), 04/2025 (JAMAL ET AL. · DOI: ${suite.p6Doi})")
                Text(
                    "✅ Đã xác minh bài báo tạp chí thật (JATAED1stpaperJanuary2026.pdf): Quản lý cây trồng theo vị trí (SSCM ${fmtNum(suite.p6SscmReadinessPct, 1)}%) & Bản sao số (Digital Twin ${fmtNum(suite.p6DigitalTwinMaturityPct, 1)}%).",
                    color = VC.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                suite.p6Applications.forEach { appLine ->
                    Text("• $appLine", color = VC.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text(suite.p6FutureTrendsVi, color = VC.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
            }

            AgPaperTab.P2_TLS_BIOMASS -> {
                Eyebrow("MỤC 2 · ƯỚC LƯỢNG SINH KHỐI & CARBON CÂY ĂN TRÁI (TLS 3D LASER + DRONE RGB)")
                Text(
                    "Phương pháp không phá hủy kết hợp quét Laser mặt đất (TLS) đo thể tích vòm tán 3D (biến dự báo mạnh nhất, R² = 0,91) và diện tích chiếu tán từ ảnh RGB Drone.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Thể tích tán TLS (V_TLS)", fmtNum(suite.p2TlsCrownVolumeM3, 2), "m³/cây", "Đường kính TB ${fmtNum(suite.p2MeanCrownDiameterM, 1)}m · Cao ${fmtNum(suite.p2CanopyHeightM, 1)}m", Modifier.weight(1f))
                    Kpi("Diện tích tán UAV RGB", fmtNum(suite.p2UavCrownAreaM2, 2), "m²/cây", "Trên ${suite.estimatedTrees} cây trong ranh", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Sinh khối khô (AGB)", fmtNum(suite.p2AgbKgPerTree, 1), "kg/cây", "Tổng ${fmtNum(suite.p2TotalAgbTonnes, 2)} tấn (${fmtNum(suite.p2BiomassDensityTHa, 2)} tấn/ha)", Modifier.weight(1f))
                    Kpi("Trữ lượng Carbon", fmtNum(suite.p2CarbonCo2eTonnes, 2), "tCO₂e", "Mật độ hấp thụ ${fmtNum(suite.p2CarbonCo2ePerHa, 2)} tCO₂e/ha", Modifier.weight(1f))
                }
                Text(suite.p2AdviceVi, color = VC.mint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }

            AgPaperTab.P34_NOWCASTING -> {
                Eyebrow("MỤC 3–4 · DỰ BÁO MƯA CỰC NGẮN HẠN DUAL-ATTENTION RNN (RADAR VIL + VỆ TINH IR)")
                Notice(suite.p34DuplicateNoticeVi, VC.amber, Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Radar VIL (Nước lỏng)", fmtNum(suite.p34RadarVilKgM2, 2), "kg/m²", "Vertically Integrated Liquid", Modifier.weight(1f))
                    Kpi("Hồng ngoại Vệ tinh Tb", fmtNum(suite.p34SatIrTbKelvin, 1), "K", "Đỉnh mây ${fmtNum(suite.p34SatIrTbKelvin - 273.15, 1)} °C", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Dự báo mưa 0–1h / 0–3h", "${fmtNum(suite.p34Rain1hMm, 1)} / ${fmtNum(suite.p34Rain3hMm, 1)}", "mm", "Dual-Attention RNN Nowcast", Modifier.weight(1f))
                    Kpi("Giảm ước tính quá mức", "-${fmtNum(suite.p34BiasReductionPct, 1)}", "%", "Chống báo ảo cường độ đỉnh bão", Modifier.weight(1f))
                }
                Text("• Khuyến nghị vận hành vườn: ${suite.p34ActionAdviceVi}", color = VC.mint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }

            AgPaperTab.P5_TOPSOIL_SAND -> {
                Eyebrow("MỤC 5 · BẢN ĐỒ HÀM LƯỢNG CÁT TẦNG ĐẤT MẶT (SENTINEL-2 VIS + SWIR + ML)")
                Text(
                    "Sử dụng cửa sổ thời gian đất lộ và tổ hợp dải phổ tối ưu Sentinel-2 (Ánh sáng nhìn thấy B2/B3/B4 + Hồng ngoại sóng ngắn SWIR B11/B12) để lập bản đồ cơ giới đất mặt (0–20 cm) và phát hiện nguy cơ bí nước tầng đế Planosol.",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Hàm lượng Cát (Sand)", fmtNum(suite.p5TopsoilSandPct, 1), "%", suite.p5SoilTextureVi, Modifier.weight(1f))
                    Kpi("Limon (Silt) / Sét (Clay)", "${fmtNum(suite.p5TopsoilSiltPct, 1)} / ${fmtNum(suite.p5TopsoilClayPct, 1)}", "%", "Cân bằng cơ giới tầng 0–20 cm", Modifier.weight(1f))
                }
                Text("• Nguy cơ bí nước tầng đế Planosol: ${suite.p5PlanosolWaterloggingRiskVi}", color = VC.amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("• ${suite.p5AdviceVi}", color = VC.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            }

            AgPaperTab.P7_P8_VISION -> {
                Eyebrow("MỤC 7 · PHÁT HIỆN SẠT LỞ BẰNG MSTA-YOLO (YANG ET AL. 2025 · CỤM B)")
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Điểm nhạy cảm sạt lở", fmtNum(suite.p7SusceptibilityScore, 1), "/100", suite.p7RiskClassVi, Modifier.weight(1f), warn = suite.p7SusceptibilityScore >= 50.0)
                    Kpi("Độ dốc TB / Cực đại", "${fmtNum(suite.p7SlopeMeanDeg, 1)}° / ${fmtNum(suite.p7SlopeMaxDeg, 1)}°", "", "YOLOv11 + Multi-Scale Token Attention", Modifier.weight(1f))
                }
                Text(suite.p7AdviceVi, color = VC.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Divider2()
                Spacer(Modifier.height(8.dp))
                Eyebrow("MỤC 8 · PHÂN ĐOẠN RANH GIỚI LÔ BẰNG HRNET-CA + DEEPLABV3+ (SHAO ET AL. 2025 · CỤM C)")
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kpi("Độ sắc nét biên (Boundary F1)", fmtNum(suite.p8BoundaryF1, 3), "", "Coordinate-Attention HRNet", Modifier.weight(1f))
                    Kpi("Độ khớp vùng (mIoU)", fmtNum(suite.p8MeanIou, 3), "", "Chu vi ${fmtNum(suite.perimeterM, 0)}m · ${ fmtNum(suite.areaHa, 2)} ha", Modifier.weight(1f))
                }
                Text(suite.p8AdviceVi, color = VC.text2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            }

            AgPaperTab.P9_PGIS_SOURCES -> {
                Eyebrow("MỤC 9 · TỐP 10 NGUỒN DỮ LIỆU GIS MIỄN PHÍ (PHẦN 2 - P-GIS)")
                Text(
                    "5 nhóm nguồn dữ liệu địa không gian miễn phí (Độ cao LiDAR/DEM, Ảnh vệ tinh, Dân số toàn cầu, Địa giới/Thủy văn, Thổ nhưỡng/Lớp phủ) đã được ánh xạ trực tiếp vào tọa độ vườn của bạn:",
                    color = VC.text2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                suite.p9Sources.forEach { src ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(VC.elevated)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(src.titleVi, color = VC.mint, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            if (src.portalUrl.isNotBlank()) {
                                Text(
                                    "🌐 Mở cổng",
                                    color = Color(0xFF07150D),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(VC.amber)
                                        .clickable {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.portalUrl)))
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.5.dp),
                                )
                            }
                        }
                        Text("• Số liệu tại vườn: ${src.gardenMetricVi}", color = VC.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Ứng dụng: ${src.usageInVwingVi}", color = VC.text2, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
