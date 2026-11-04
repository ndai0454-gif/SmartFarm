package com.example.test_tablet.ui.vwing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GisCurriculumHubCard(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cr = hud.curriculumResult ?: return
    val scope = rememberCoroutineScope()
    var activeModule by remember { mutableStateOf("M123") }

    VCard(modifier.fillMaxWidth()) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("🎓 HỆ THỐNG CHUYÊN ĐỀ GIS · VIỄN THÁM · ĐỊA THỐNG KÊ & QUANG TRẮC UAV (MODULES 1–7)")
                Text(
                    "Phân tích trực tiếp trên vùng đã khoanh \"${hud.regionName}\" (${fmtNum(cr.areaHa, 2)} ha · Hệ tọa độ Quốc gia ${cr.m123.vn2000Code})",
                    color = VC.text2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Thanh 4 Tab Module Học phần
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "M123" to "🌐 1. Nền tảng GIS · Hệ tọa độ VN-2000 & Georef (M1–M3)",
                "M46" to "🛰 2. Ảnh Đa phổ · LULC · Nhiệt độ LST & UHI (M4 & M6)",
                "M5" to "📈 3. Nội suy IDW/Kriging · Ô nhiễm · Điểm nóng Gi* (M5)",
                "M7" to "🚁 4. Khảo sát Drone UAV (GSD) & Agisoft Metashape (M7)",
            ).forEach { (key, title) ->
                val active = activeModule == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                        .border(if (active) 1.5.dp else 1.dp, if (active) VC.mint else VC.border, RoundedCornerShape(10.dp))
                        .clickable { activeModule = key }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(
                        title,
                        color = if (active) VC.mint else VC.text,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeModule) {
            "M123" -> Module123Section(cr.m123)
            "M46" -> Module46Section(cr.m46)
            "M5" -> Module5Section(cr.m5)
            "M7" -> Module7Section(hud, cfg, cr.m7, scope, onToast)
        }
    }
}

@Composable
private fun Module123Section(m: Module123Foundation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "I. HỆ TỌA ĐỘ ĐỊA LÝ (GCS 3D) VS HỆ TỌA ĐỘ PHẲNG/CHIẾU (PCS 2D — UTM & VN-2000)",
            color = VC.teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // GCS WGS84
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.amber.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("🌍 GCS: ${m.gcsCode} (Mặt cầu 3D)", color = VC.amber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Tâm: ${m.centroidDms}",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "(${fmtNum(m.centroidLat, 5)}°N, ${fmtNum(m.centroidLon, 5)}°E) · Sai lệch nếu tính độ phẳng: ±${fmtNum(m.naiveUnprojectedErrorPct, 2)}%",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }

            // PCS UTM
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.cyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("📐 PCS: ${m.utmCode} (UTM Zone ${m.utmZone})", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "E = ${fmtNum(m.utmCentroidEastingM, 1)} m · N = ${fmtNum(m.utmCentroidNorthingM, 1)} m",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Diện tích phẳng: ${fmtNum(m.utmPlanarAreaM2, 1)} m² (${fmtNum(m.utmPlanarAreaHa, 3)} ha) · Chu vi ${fmtNum(m.utmPlanarPerimeterM, 1)} m",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }
        }

        // PCS VN-2000 + Buffers
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.mint.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🇻🇳 HỆ QUY CHIẾU QUỐC GIA: ${m.vn2000Name} (${m.vn2000Code})", color = VC.mint, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                Text("X = ${fmtNum(m.vn2000CentroidXM, 2)} m · Y = ${fmtNum(m.vn2000CentroidYM, 2)} m", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Chuyển đổi 7 tham số Helmert (${m.vn2000DatumShift}) · Diện tích chuẩn PCS: ${fmtNum(m.vn2000PlanarAreaM2, 1)} m² (${fmtNum(m.vn2000PlanarAreaHa, 4)} ha)",
                color = VC.text2,
                fontSize = 11.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.buffers.forEach { b ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(VC.mint.copy(alpha = 0.10f))
                            .border(1.dp, VC.mint.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                            .padding(6.dp),
                    ) {
                        Text(
                            "Buffer +${fmtNum(b.distanceM, 0)}m: ${fmtNum(b.bufferedAreaHa, 2)} ha (+${fmtNum(b.addedCorridorHa, 2)} ha)",
                            color = VC.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Vector & Raster Data Model + Georeferencing
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.teal.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🗂 MÔ HÌNH VECTOR & RASTER + CLIPPING", color = VC.teal, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Vector: ${m.vectorPointsCount} Points · ${m.vectorLinesCount} Lines · ${m.vectorPolygonsCount} Polygons",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Raster: ${m.rasterCols}×${m.rasterRows} ô (${fmtNum(m.rasterCellSizeM, 1)}m/ô) · ${m.rasterDataTypes}",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.lime.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🎯 GEOREFERENCING (NẮN CHỈNH HÌNH HỌC GCPs)", color = VC.lime, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Affine Bậc 1 RMSE: ±${fmtNum(m.affineRmseTotalM, 2)} m · Đa thức Bậc 2: ±${fmtNum(m.poly2RmseTotalM, 2)} m",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "${m.gcps.size} điểm khống chế mặt đất (GCPs) · ${m.mosaicStatus}",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }
        }

        // Bảng thuộc tính Vector (Attribute Table) & GCPs
        Text(
            "II. BẢNG THUỘC TÍNH ĐỐI TƯỢNG VECTOR (ATTRIBUTE TABLE) & ĐIỂM KHỐNG CHẾ GCPs (VN-2000):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            m.attributeTable.take(4).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { r ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .border(1.dp, VC.border, RoundedCornerShape(8.dp))
                                .padding(7.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${r.featureId} (${r.geometryType})", color = VC.mint, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Cạnh ${fmtNum(r.edgeLengthM, 1)} m", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "VN-2000: X=${fmtNum(r.vn2000XM, 1)} · Y=${fmtNum(r.vn2000YM, 1)} | ${r.wgs84Dms}",
                                color = VC.text2,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Module46Section(m: Module46RemoteSensing) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // LST & UHI Highlight Box
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VC.elevated)
                .border(1.2.dp, VC.amber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🌡 NHIỆT ĐỘ BỀ MẶT ĐẤT (LST - LANDSAT TIRS) & PHÂN TÍCH ĐẢO NHIỆT (UHI = LST × NDBI)", color = VC.amber, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                Text("LST TB: ${fmtNum(m.lstMeanC, 1)} °C (${fmtNum(m.lstMinC, 1)}–${fmtNum(m.lstMaxC, 1)} °C)", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Bức xạ đỉnh khí quyển Lλ = ${fmtNum(m.toaRadianceMean, 2)} W/(m²·sr·µm) → Nhiệt độ sáng cảm biến Tb = ${fmtNum(m.brightnessTempTbC, 1)} °C → Che phủ thực vật Pv = ${fmtNum(m.vegetationFractionPv * 100, 1)}% (NDVI TB ${fmtNum(m.ndviMean, 2)}) → Độ phát xạ bề mặt ε = ${fmtNum(m.surfaceEmissivityEps, 4)} → Dị thường nhiệt UHI: ${fmtNum(m.uhiHotspotAreaPct, 1)}% diện tích (ngưỡng ≥ ${fmtNum(m.uhiThresholdC, 1)} °C, NDBI ${fmtNum(m.ndbiMean, 2)})",
                color = VC.text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(m.lstDiagnosisVi, color = VC.text2, fontSize = 11.sp)
        }

        // Tổ hợp dải phổ (Band Combinations)
        Text(
            "I. TỔ HỢP DẢI PHỔ VỆ TINH ĐA PHỔ (SENTINEL-2 10m/20m & LANDSAT 8/9 30m/15m/100m):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            m.bandCombinations.forEach { bc ->
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
                        Text(bc.nameVi, color = VC.mint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        Text("S2: ${bc.sentinel2Bands} · L8/9: ${bc.landsat89Bands}", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${bc.meanReflectance} — ${bc.interpretationVi}", color = VC.text2, fontSize = 11.sp)
                }
            }
        }

        // Phân loại vỏ phủ đất (LULC: Unsupervised vs Supervised)
        Text(
            "II. PHÂN LOẠI LỚP PHỦ MẶT ĐẤT (LULC: UNSUPERVISED K-MEANS/ISODATA & SUPERVISED RANDOM FOREST/SVM — OA ${fmtNum(m.overallAccuracyPct, 1)}%, KAPPA κ = ${fmtNum(m.kappaCoefficient, 2)}):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Unsupervised
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.cyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("🔹 Không giám sát (K-Means / ISODATA)", color = VC.cyan, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                m.unsupervisedClusters.forEach { cl ->
                    Text(
                        "• ${cl.labelVi}: ${fmtNum(cl.areaPct, 1)}% (${fmtNum(cl.areaHa, 2)} ha · NDVI ${fmtNum(cl.ndviCentroid, 2)} · ${fmtNum(cl.lstMeanC, 1)}°C)",
                        color = VC.text,
                        fontSize = 10.5.sp,
                    )
                }
            }

            // Supervised
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.lime.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("🔸 Có giám sát (Random Forest / SVM / MLC)", color = VC.lime, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                m.supervisedClasses.forEach { sc ->
                    Text(
                        "• ${sc.classNameVi}: ${fmtNum(sc.areaPct, 1)}% (${fmtNum(sc.areaHa, 2)} ha · UA ${fmtNum(sc.userAccuracyPct, 0)}%)",
                        color = VC.text,
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Module5Section(m: Module5SpatialAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // IDW vs Ordinary Kriging
        Text(
            "I. NỘI SUY KHÔNG GIAN: IDW (TRỌNG SỐ NGHỊCH ĐẢO KHOẢNG CÁCH) VS ORDINARY KRIGING (ĐỊA THỐNG KÊ SEMIVARIOGRAM):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.cyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("📍 ${m.idwMethod}", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "PM2.5: ${fmtNum(m.idwPm25Ugm3, 2)} µg/m³ · pH nước: ${fmtNum(m.idwWaterPh, 2)} · Ồn: ${fmtNum(m.idwNoiseDba, 1)} dBA",
                    color = VC.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Từ ${m.stationCount} trạm quan trắc lân cận · Sai số chéo LOOCV RMSE = ±${fmtNum(m.idwLoocvRmse, 2)}",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.mint.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("📊 ${m.krigingMethod}", color = VC.mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Dự báo PM2.5: ${fmtNum(m.krigingPm25Ugm3, 2)} ± ${fmtNum(m.krigingStdError, 2)} µg/m³",
                    color = VC.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Semivariogram: Nugget c₀=${fmtNum(m.nuggetC0, 2)} · Sill=${fmtNum(m.sillC0PlusC, 2)} · Range a=${fmtNum(m.rangeAM, 0)} m",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }
        }

        // 3 Bản đồ ô nhiễm: Không khí, Nước (+ Buffer), Tiếng ồn (+ DSM)
        Text(
            "II. BẢN ĐỒ Ô NHIỄM KHÔNG KHÍ (AQI/PM2.5) · NGUỒN NƯỚC (pH/DO/TDS + BUFFER) · TIẾNG ỒN (dBA + DSM):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.lime.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🌫 KHÔNG KHÍ (AQI ${m.vnAqi})", color = VC.lime, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("PM2.5: ${fmtNum(m.pm25Ugm3, 1)} · PM10: ${fmtNum(m.pm10Ugm3, 1)} µg/m³", color = VC.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(m.airRatingVi, color = VC.text2, fontSize = 10.5.sp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.cyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("💧 NGUỒN NƯỚC (Buffer ${fmtNum(m.waterBufferM, 0)}m)", color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("pH ${fmtNum(m.waterPh, 2)} · DO ${fmtNum(m.waterDoMgl, 1)} · TDS ${fmtNum(m.waterTdsMgl, 0)} mg/L", color = VC.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(m.waterBufferNoteVi, color = VC.text2, fontSize = 10.5.sp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VC.elevated)
                    .border(1.dp, VC.teal.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🔊 TIẾNG ỒN (DSM -${fmtNum(m.dsmCanopyAttenuationDb, 1)}dB)", color = VC.teal, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("TB ${fmtNum(m.meanNoiseDba, 1)} dBA (Lõi ${fmtNum(m.orchardCoreDba, 1)} / Rìa ${fmtNum(m.roadEdgeDba, 1)})", color = VC.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(m.noiseRatingVi, color = VC.text2, fontSize = 10.5.sp)
            }
        }

        // KDE & Getis-Ord Gi* Hotspot Analysis
        Text(
            "III. MẬT ĐỘ KERNEL (KDE R=${fmtNum(m.kdeBandwidthM, 0)}m) & THỐNG KÊ ĐIỂM NÓNG GETIS-ORD Gi* (${m.getisOrdSummary}):",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            m.hotspotCells.take(6).chunked(3).forEach { rowCells ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowCells.forEach { cell ->
                        val isHot = cell.giStarZScore >= 1.96
                        val col = if (isHot) VC.amber else VC.mint
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(7.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cell.cellId, color = col, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Gi* Z = ${fmtNum(cell.giStarZScore, 2)} (p=${fmtNum(cell.pValue, 3)})", color = VC.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(cell.classificationVi, color = VC.text2, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Module7Section(
    hud: RegionHudState,
    cfg: SatelliteConfig,
    m: Module7DronePhotogrammetry,
    scope: kotlinx.coroutines.CoroutineScope,
    onToast: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Chọn độ cao bay H để tính GSD trực tiếp
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Chọn độ cao bay UAV (H) để tính GSD = (H × Pixel Size) / F:",
                color = VC.teal,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30.0, 45.0, 60.0, 80.0, 100.0).forEach { hVal ->
                    val active = kotlin.math.abs(hud.uavAltitudeM - hVal) < 1.0
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) VC.mint.copy(alpha = 0.22f) else VC.elevated)
                            .border(1.dp, if (active) VC.mint else VC.border, RoundedCornerShape(14.dp))
                            .clickable {
                                hud.updateUavFlightPlan(cfg, hVal, scope, onToast)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "H = ${hVal.toInt()}m",
                            color = if (active) VC.mint else VC.text,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Hộp tổng hợp GSD & Kế hoạch bay UAV
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.lime.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("📏 ĐỘ PHÂN GIẢI MẶT ĐẤT (GROUND SAMPLING DISTANCE - GSD)", color = VC.lime, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    m.gsdFormulaText,
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "${m.cameraModel} · ${m.targetGradeVi}",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VC.elevated)
                    .border(1.2.dp, VC.cyan.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("🚁 ĐỘ CHỒNG PHỦ (OVERLAP) & THÔNG SỐ ĐƯỜNG BAY", color = VC.cyan, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Phủ dọc ${fmtNum(m.forwardOverlapPct, 0)}% · Phủ ngang ${fmtNum(m.sideOverlapPct, 0)}% → ${m.flightLines} tuyến · ${m.totalPhotos} ảnh",
                    color = VC.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Bước chụp dọc ${fmtNum(m.forwardPhotoStepM, 1)} m · Cách tuyến ${fmtNum(m.sideLineStepM, 1)} m · Bay ~${fmtNum(m.estimatedFlightTimeMin, 1)} phút · ${m.rtkGcps.size} bia RTK GCPs (±1,2 cm)",
                    color = VC.text2,
                    fontSize = 11.sp,
                )
            }
        }

        // Quy trình 7 bước Agisoft Metashape
        Text(
            "II. QUY TRÌNH 7 BƯỚC DỰNG HÌNH QUANG TRẮC 3D & TRỰC ẢNH TRONG AGISOFT METASHAPE:",
            color = VC.teal,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            m.metashapeSteps.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { st ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VC.elevated)
                                .border(1.dp, VC.mint.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(st.titleVi, color = VC.mint, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Text(st.metricBadge, color = VC.cyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(st.detailVi, color = VC.text2, fontSize = 10.5.sp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
