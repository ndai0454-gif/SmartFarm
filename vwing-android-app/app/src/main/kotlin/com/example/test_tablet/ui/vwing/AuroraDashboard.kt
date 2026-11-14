package com.example.test_tablet.ui.vwing

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.R
import com.example.test_tablet.data.vwing.Condition
import com.example.test_tablet.data.vwing.FarmWeather
import com.example.test_tablet.data.vwing.SatelliteSettings
import com.example.test_tablet.data.vwing.WeatherClient
import com.example.test_tablet.data.vwing.activeTrees
import com.example.test_tablet.data.vwing.summary
import com.example.test_tablet.data.vwing.todayWork
import com.example.test_tablet.data.vwing.treesNeedingVisitCount
import com.example.test_tablet.data.vwing.yieldCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val EOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val ESoft = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
private val EPen = CubicBezierEasing(0.37f, 0.01f, 0.2f, 1f)

private enum class AuroraIconKind {
    GRID, CHART, GLOBE, CAL, GEAR, OUT,
    PLUS, SEARCH, BELL, PIN, WIND, DROP, GUST,
    CLOUD, CLOUD2, HAIL, SUN
}

private data class AuroraDayForecast(
    val tempStr: String,
    val icon: AuroraIconKind,
    val dayLabel: String,
    val line1: String,
    val line2: String,
    val high: Int,
    val low: Int,
    val rainProb: Int,
    val windStr: String,
    val advice: String,
)

/**
 * Màn hình Aurora Liquid-Glass Dashboard kết hợp dữ liệu thực tế của Vườn mẫu Tây Nguyên (Bảo Lộc)
 * và thời tiết Open-Meteo / GEE. Khi nhúng trong khung ứng dụng chính ([embeddedInAppShell] = true),
 * tự động ẩn thanh điều hướng phụ lặp lại để tuân thủ nguyên tắc Single Navigation Authority.
 */
@Composable
fun AuroraSmartFarmDashboard(
    c: VCtx,
    onOpenMore: () -> Unit = {},
    embeddedInAppShell: Boolean = true,
) {
    val context = LocalContext.current
    val d = c.d
    val s = d.summary()
    val y = d.yieldCalc()
    val work = d.todayWork()
    val needs = d.treesNeedingVisitCount()

    // Tải dữ liệu thời tiết thực tế từ cache hoặc máy chủ :8000
    LaunchedEffect(Unit) {
        VUi.country = AppLocaleStore.load(context)
    }
    val activeCountry = VUi.country
    val loc = AppStrings.forCountry(activeCountry)
    val gardenPoint = remember(d, activeCountry) {
        if (activeCountry == AppCountry.VN) WeatherClient.gardenPoint(d)
        else com.example.test_tablet.data.vwing.GeoPoint(activeCountry.sampleLat, activeCountry.sampleLng)
    }
    var weatherReport by remember(gardenPoint) {
        mutableStateOf(gardenPoint?.let { WeatherClient.loadCache(context.filesDir, it) })
    }
    var backendOnline by remember { mutableStateOf(true) }

    LaunchedEffect(gardenPoint) {
        val pt = gardenPoint ?: return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.IO) {
                WeatherClient.refresh(SatelliteSettings(context.filesDir).load(), context.filesDir, pt)
            }.onSuccess {
                weatherReport = it
                backendOnline = true
            }.onFailure {
                backendOnline = weatherReport != null
            }
            delay(3 * 60 * 1000L)
        }
    }

    // Chế độ hiển thị: false = Dữ liệu Vườn Tây Nguyên (mặc định), true = Aurora Reference (Central Jakarta)
    var referenceMode by rememberSaveable { mutableStateOf(false) }
    var selectedDayIdx by rememberSaveable { mutableIntStateOf(0) }

    // Hiển thị tức thì theo chuẩn ibelick/ui-skills (fixing-motion-performance: không trễ entry animation, tắt specular sheen gây chói)
    val lineProgress = 1f
    val fillProgress = 1f
    val sheenProgress = 0f

    // Dữ liệu thực tế từ vườn & thời tiết
    val liveTemp = weatherReport?.now?.temperature?.roundToInt() ?: 21
    val liveWind = weatherReport?.now?.wind?.let { fmtNum(it, 1) } ?: "3,4"
    val liveHumidity = weatherReport?.now?.humidity?.roundToInt() ?: 98
    val todayForecast = weatherReport?.days?.firstOrNull()
    val highTemp = todayForecast?.high?.roundToInt() ?: 29
    val lowTemp = todayForecast?.low?.roundToInt() ?: 19
    val rainProb = todayForecast?.rainChance?.roundToInt() ?: 98

    val treeA04 = d.activeTrees.firstOrNull { it.code == "A-04" }
    val treeB05 = d.activeTrees.firstOrNull { it.code == "B-05" }
    val treeD02 = d.activeTrees.firstOrNull { it.code == "D-02" }

    val khuACount = d.activeTrees.count { it.zone == "Khu A" }
    val khuBCount = d.activeTrees.count { it.zone == "Khu B" }
    val khuCDCount = d.activeTrees.count { it.zone == "Khu C" || it.zone == "Khu D" }

    val estTonText = y.estKg?.let { fmtNum(it / 1000.0, 2) + " tấn" } ?: "2,76 tấn"
    val estRevText = y.revenue?.let { fmtNum(it / 1_000_000.0, 1) + " tr ₫" } ?: "171,1 tr ₫"

    val dayForecasts = remember(weatherReport, referenceMode, activeCountry, liveTemp, highTemp, lowTemp, rainProb, liveWind, liveHumidity, d.garden.location, s.totalTrees, needs) {
        if (referenceMode) {
            listOf(
                AuroraDayForecast("11°", AuroraIconKind.CLOUD, "Sunday", "Cloudy", "with Light Breeze", 14, 9, 25, "12 mph", "Overcast skies across Central Jakarta. High around 54°F, light east wind 12 mph."),
                AuroraDayForecast("13°", AuroraIconKind.CLOUD2, "Monday", "Scattered", "Rain Showers", 15, 10, 55, "15 mph", "Scattered afternoon showers. High around 56°F, wind from the east 15 mph."),
                AuroraDayForecast("14°", AuroraIconKind.CLOUD2, "Tuesday", "Mostly", "Cloudy Skies", 16, 11, 45, "14 mph", "Mostly cloudy with mild humidity. High around 57°F, east wind 14 mph."),
                AuroraDayForecast("10°", AuroraIconKind.HAIL, "Wednesday", "Storm", "with Heavy Rain", 12, 8, 40, "19 mph", "Partly cloudy with occasional showers. High around 50°F.\nWind from the east 11 to 21 mph. Rain chance is 40%, with\nrainfall expected to be less than an inch."),
                AuroraDayForecast("19°", AuroraIconKind.SUN, "Thursday", "Sunny", "& Clear Sky", 21, 13, 10, "9 mph", "Bright and clear conditions throughout the day. Ideal visibility and mild breeze."),
                AuroraDayForecast("12°", AuroraIconKind.CLOUD, "Friday", "Partly", "Cloudy Day", 15, 10, 30, "11 mph", "Partly cloudy with comfortable temperatures and light afternoon wind."),
            )
        } else {
            val defaultDays = listOf(
                AuroraDayForecast("${liveTemp}°", AuroraIconKind.CLOUD, AppStrings.weekdayLabel(activeCountry, null, 0), loc.cloudy1, loc.cloudy2, highTemp, lowTemp, rainProb, liveWind, loc.adviceMild(lowTemp, highTemp, rainProb)),
                AuroraDayForecast("28°", AuroraIconKind.CLOUD2, AppStrings.weekdayLabel(activeCountry, null, 1), loc.sunClear1, loc.sunClear2, 28, 20, 45, "3,8", loc.adviceMild(20, 28, 45)),
                AuroraDayForecast("28°", AuroraIconKind.CLOUD2, AppStrings.weekdayLabel(activeCountry, null, 2), loc.rain1, loc.rain2, 28, 20, 68, "4,1", loc.adviceRain(68, 20, 28, needs)),
                AuroraDayForecast("27°", AuroraIconKind.HAIL, AppStrings.weekdayLabel(activeCountry, null, 3), loc.storm1, loc.storm2, 27, 19, 75, "3,5", loc.adviceRain(75, 19, 27, needs)),
                AuroraDayForecast("29°", AuroraIconKind.SUN, AppStrings.weekdayLabel(activeCountry, null, 4), loc.sunClear1, loc.sunClear2, 29, 19, 20, "2,8", loc.adviceMild(19, 29, 20)),
                AuroraDayForecast("28°", AuroraIconKind.CLOUD, AppStrings.weekdayLabel(activeCountry, null, 5), loc.cloudy1, loc.cloudy2, 28, 19, 35, "3,0", loc.adviceMild(19, 28, 35)),
            )
            val days = weatherReport?.days?.take(6)
            if (days != null && days.size >= 6) {
                days.mapIndexed { idx, day ->
                    val icon = when (day.code) {
                        0, 1 -> AuroraIconKind.SUN
                        95, 96, 99 -> AuroraIconKind.HAIL
                        in 51..67, in 80..82 -> AuroraIconKind.CLOUD2
                        else -> AuroraIconKind.CLOUD
                    }
                    val (l1, l2) = when (day.code) {
                        0, 1 -> loc.sunClear1 to loc.sunClear2
                        2, 3 -> loc.cloudy1 to loc.cloudy2
                        in 45..48 -> loc.fog1 to loc.fog2
                        in 51..67 -> loc.rain1 to loc.rain2
                        in 80..82 -> loc.heavyRain1 to loc.heavyRain2
                        95, 96, 99 -> loc.storm1 to loc.storm2
                        else -> defaultDays[idx].line1 to defaultDays[idx].line2
                    }
                    val hi = day.high.roundToInt()
                    val lo = day.low.roundToInt()
                    val rain = day.rainChance?.roundToInt() ?: defaultDays[idx].rainProb
                    val farmerAdvice = when {
                        rain >= 65 -> loc.adviceRain(rain, lo, hi, needs)
                        hi >= 31 -> loc.adviceHot(hi, rain)
                        else -> loc.adviceMild(lo, hi, rain)
                    }
                    AuroraDayForecast(
                        tempStr = if (idx == 0) "${liveTemp}°" else "${hi}°",
                        icon = icon,
                        dayLabel = AppStrings.weekdayLabel(activeCountry, day.date, idx),
                        line1 = l1,
                        line2 = l2,
                        high = hi,
                        low = lo,
                        rainProb = rain,
                        windStr = liveWind,
                        advice = farmerAdvice,
                    )
                }
            } else {
                defaultDays
            }
        }
    }

    val activeDay = dayForecasts.getOrElse(selectedDayIdx.coerceIn(0, dayForecasts.lastIndex)) { dayForecasts.first() }
    val forecastItems = remember(dayForecasts) {
        dayForecasts.map { Triple(it.tempStr, it.icon, it.dayLabel) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (c.wide) 30.dp else 26.dp))
            .background(if (VC.isPro) Color(0xFF071A26) else Color.White)
            .border(
                1.dp,
                if (VC.isPro) Color.White.copy(alpha = 0.22f) else VC.border,
                RoundedCornerShape(if (c.wide) 30.dp else 26.dp),
            )
    ) {
        val isTabletLandscape = !c.phoneMode && (maxWidth >= 860.dp || c.wide)
        val isTabletPortrait = !c.phoneMode && !isTabletLandscape && maxWidth >= 580.dp

        val healthyTrees = (s.totalTrees - needs).coerceAtLeast(0)
        val cardsLoc = AppStrings.cards(activeCountry)
        val cardBData = if (referenceMode) {
            AuroraRowCardData("Indonesia", "North Jakarta", "Mostly Sunny", "12°", AuroraIconKind.CLOUD) { c.nav.top("weather") }
        } else {
            AuroraRowCardData(
                cardsLoc.zoneA,
                cardsLoc.treeA04,
                cardsLoc.visitRoot,
                "${treeA04?.fruits ?: 46}",
                AuroraIconKind.CLOUD,
            ) {
                treeA04?.let { c.selectTree(it.id); c.nav.push("tree|${it.id}") } ?: c.nav.top("issues|trees")
            }
        }
        val cardCData = if (referenceMode) {
            AuroraRowCardData("Indonesia", "Bandung", "Cloudy", "10°", AuroraIconKind.CLOUD) { c.nav.top("weather") }
        } else {
            AuroraRowCardData(
                cardsLoc.zoneB,
                cardsLoc.treeB05,
                cardsLoc.recovering,
                "${treeB05?.fruits ?: 46}",
                AuroraIconKind.CLOUD,
            ) {
                treeB05?.let { c.selectTree(it.id); c.nav.push("tree|${it.id}") } ?: c.nav.top("issues|trees")
            }
        }
        val cardDData = if (referenceMode) {
            AuroraRowCardData("Indonesia", "South Jakarta", "Sunny", "14°", AuroraIconKind.CLOUD2) { c.nav.top("weather") }
        } else {
            AuroraRowCardData(
                cardsLoc.estHarvestTitle(s.totalTrees),
                cardsLoc.estHarvestVal(estTonText),
                cardsLoc.estRevVal(estRevText),
                "$healthyTrees/${s.totalTrees}",
                AuroraIconKind.SUN,
            ) {
                c.nav.top("yield")
            }
        }

        if (VC.isPro) {
            // Gói Pro (Enterprise): Lớp nền ảnh bão & vườn sầu riêng Tây Nguyên + Lớp phủ tối tăng tương phản chuẩn Aurora Liquid-Glass
            Image(
                painter = painterResource(R.drawable.storm_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF06151F).copy(alpha = 0.82f),
                                Color(0xFF06151F).copy(alpha = 0.72f),
                                Color(0xFF06151F).copy(alpha = 0.64f),
                            ),
                            start = Offset.Zero,
                            end = Offset(1400f, 900f),
                        )
                    )
            )
        } else {
            // Gói Free: Nền Trắng sáng ban ngày sạch sẽ (#FFFFFF -> #F6FAF7)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF7FBF8),
                                Color(0xFFFFFFFF),
                            )
                        )
                    )
            )
        }

        when {
            isTabletLandscape -> {
                // ═══════════════════════════════════════════════════════════════════
                // 1) BỐ CỤC TABLET NGANG (1357 × 871 TỶ LỆ CHUẨN AURORA LIQUID GLASS)
                // ═══════════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (embeddedInAppShell) 580.dp else 680.dp)
                        .padding(if (embeddedInAppShell) 22.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (!embeddedInAppShell) {
                        AuroraSidebarRail(
                            activeRoute = c.nav.head,
                            alertCount = needs,
                            onNavigate = { route -> c.nav.top(route) },
                            onTogglePreset = { referenceMode = !referenceMode },
                            onOpenMore = onOpenMore,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (embeddedInAppShell) {
                            AuroraStageStatusBar(
                                titleText = if (referenceMode) "Aurora Weather Station · Central Jakarta" else "${d.garden.name} · ${fmtNum(d.garden.areaHa ?: 1.8)} ha",
                                subText = if (referenceMode) "Operator: Calfin Danang · Live Telemetry" else "${AppStrings.shell(activeCountry).healthyBadge(healthyTrees, s.totalTrees)} · ${AppStrings.shell(activeCountry).soilMoistSub}",
                                healthyBadgeText = AppStrings.shell(activeCountry).healthyBadge(healthyTrees, s.totalTrees),
                                backendOnline = backendOnline,
                                alertCount = needs,
                                isCompact = false,
                                onOpenSatellite = { c.nav.top("map") },
                                onOpenAlerts = { c.nav.top("issues|trees") },
                            )
                        } else {
                            AuroraHeaderBar(
                                helloText = if (referenceMode) "Welcome" else "${loc.hello} ${d.garden.name} (${fmtNum(d.garden.areaHa ?: 1.8)} ha)",
                                whoText = if (referenceMode) "Calfin Danang" else "${d.garden.operatorName} · ${if (activeCountry == AppCountry.VN) d.garden.location else activeCountry.sampleLocation}",
                                backendOnline = backendOnline,
                                alertCount = work.total.coerceAtLeast(needs),
                                operatorInitials = if (referenceMode) "CD" else activeCountry.countryCode,
                                country = activeCountry,
                                onAddLog = { c.nav.push("log") },
                                onCountryPicker = { VUi.countryPickerOpen = true },
                                onAlerts = { c.nav.top("issues|trees") },
                                onProfile = { c.nav.top("setup") },
                            )
                        }

                        AuroraQuickActionRow(
                            needsCount = needs,
                            isCompact = false,
                            onOpenMap = { c.nav.top("map") },
                            onOpenSatellite = { c.nav.top("env") },
                            onOpenAlerts = { c.nav.top("issues|trees") },
                            onOpenAi = { c.nav.top("ai") },
                            onAddLog = { c.nav.push("log") },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 520.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AuroraHeroBlock(
                                    chipText = if (referenceMode) "Weather Forecast · ${activeDay.dayLabel}" else "Thời tiết hôm nay · ${activeDay.dayLabel}",
                                    modeBadgeText = if (c.simpleMode && !referenceMode) "" else if (referenceMode) "Mẫu Aurora ⇄ Vườn" else "Vườn Tây Nguyên ⇄ Mẫu",
                                    line1 = activeDay.line1,
                                    line2 = activeDay.line2,
                                    blurb = activeDay.advice,
                                    sheenProgress = sheenProgress,
                                    onToggleMode = { referenceMode = !referenceMode },
                                    isCompact = false,
                                )

                                Spacer(Modifier.height(16.dp))

                                AuroraForecastStrip(
                                    items = forecastItems,
                                    selectedIdx = selectedDayIdx,
                                    onSelectIdx = { selectedDayIdx = it },
                                    lineProgress = lineProgress,
                                    fillProgress = fillProgress,
                                    isCompact = false,
                                )
                            }

                            AuroraRightRail(
                                modifier = Modifier.width(288.dp),
                                placeTitle = if (referenceMode) "Central Jakarta" else d.garden.location.ifBlank { "Bảo Lộc, Lâm Đồng" },
                                bigTemp = activeDay.tempStr,
                                metric1 = if (referenceMode) activeDay.windStr else "${activeDay.windStr} km/h",
                                metric2 = if (referenceMode) "${activeDay.rainProb}% rain" else "Mưa ${activeDay.rainProb}%",
                                metric3 = if (referenceMode) "15km/h" else "Ẩm đất 64%",
                                cardB = cardBData,
                                cardC = cardCData,
                                cardD = cardDData,
                                onClickBigCard = { c.nav.top("weather") },
                                sheenProgress = sheenProgress,
                            )
                        }
                    }
                }
            }

            isTabletPortrait -> {
                // ═══════════════════════════════════════════════════════════════════
                // 2) BỐ CỤC TABLET DỌC (800×1280 — HERO/WAVE RỘNG + LƯỚI 2×2 THẺ KÍNH)
                // ═══════════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (embeddedInAppShell) 20.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (!embeddedInAppShell) {
                        AuroraSidebarRail(
                            activeRoute = c.nav.head,
                            alertCount = needs,
                            onNavigate = { route -> c.nav.top(route) },
                            onTogglePreset = { referenceMode = !referenceMode },
                            onOpenMore = onOpenMore,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (embeddedInAppShell) {
                            AuroraStageStatusBar(
                                titleText = if (referenceMode) "Aurora Weather Station · Central Jakarta" else "${d.garden.name} · ${fmtNum(d.garden.areaHa ?: 1.8)} ha",
                                subText = if (referenceMode) "Operator: Calfin Danang" else "$healthyTrees/${s.totalTrees} cây khỏe · Đất đủ ẩm 64%",
                                healthyBadgeText = "$healthyTrees/${s.totalTrees} cây khỏe",
                                backendOnline = backendOnline,
                                alertCount = needs,
                                isCompact = false,
                                onOpenSatellite = { c.nav.top("map") },
                                onOpenAlerts = { c.nav.top("issues|trees") },
                            )
                        } else {
                            AuroraHeaderBar(
                                helloText = if (referenceMode) "Welcome" else "${loc.hello} ${d.garden.name} (${fmtNum(d.garden.areaHa ?: 1.8)} ha)",
                                whoText = if (referenceMode) "Calfin Danang" else "${d.garden.operatorName} · ${if (activeCountry == AppCountry.VN) d.garden.location else activeCountry.sampleLocation}",
                                backendOnline = backendOnline,
                                alertCount = work.total.coerceAtLeast(needs),
                                operatorInitials = if (referenceMode) "CD" else activeCountry.countryCode,
                                country = activeCountry,
                                onAddLog = { c.nav.push("log") },
                                onCountryPicker = { VUi.countryPickerOpen = true },
                                onAlerts = { c.nav.top("issues|trees") },
                                onProfile = { c.nav.top("setup") },
                            )
                        }

                        AuroraHeroBlock(
                            chipText = if (referenceMode) "Weather Forecast · ${activeDay.dayLabel}" else "${loc.todayWord} · ${activeDay.dayLabel}",
                            modeBadgeText = if (c.simpleMode && !referenceMode) "" else if (referenceMode) "Mẫu Aurora ⇄ Vườn" else "Vườn Tây Nguyên ⇄ Mẫu",
                            line1 = activeDay.line1,
                            line2 = activeDay.line2,
                            blurb = activeDay.advice,
                            sheenProgress = sheenProgress,
                            onToggleMode = { referenceMode = !referenceMode },
                            isCompact = false,
                        )

                        AuroraQuickActionRow(
                            needsCount = needs,
                            isCompact = false,
                            onOpenMap = { c.nav.top("map") },
                            onOpenSatellite = { c.nav.top("env") },
                            onOpenAlerts = { c.nav.top("issues|trees") },
                            onOpenAi = { c.nav.top("ai") },
                            onAddLog = { c.nav.push("log") },
                        )

                        AuroraForecastStrip(
                            items = forecastItems,
                            selectedIdx = selectedDayIdx,
                            onSelectIdx = { selectedDayIdx = it },
                            lineProgress = lineProgress,
                            fillProgress = fillProgress,
                            isCompact = false,
                        )

                        // Lưới 2×2 cho 4 thẻ Liquid Glass trên Tablet dọc
                        AuroraTabletPortraitCardsGrid(
                            placeTitle = if (referenceMode) "Central Jakarta" else d.garden.location.ifBlank { "Bảo Lộc, Lâm Đồng" },
                            bigTemp = activeDay.tempStr,
                            metric1 = if (referenceMode) activeDay.windStr else "${activeDay.windStr} km/h",
                            metric2 = if (referenceMode) "${activeDay.rainProb}% rain" else "Mưa ${activeDay.rainProb}%",
                            metric3 = if (referenceMode) "15km/h" else "Ẩm đất 64%",
                            cardB = cardBData,
                            cardC = cardCData,
                            cardD = cardDData,
                            onClickBigCard = { c.nav.top("weather") },
                            sheenProgress = sheenProgress,
                        )
                    }
                }
            }

            else -> {
                // ═══════════════════════════════════════════════════════════════════
                // 3) BỐ CỤC ĐIỆN THOẠI (<580dp: CỘT ĐƠN CUỘN MƯỢT)
                // ═══════════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (embeddedInAppShell) {
                        AuroraStageStatusBar(
                            titleText = if (referenceMode) "Aurora Weather" else d.garden.name,
                            subText = if (referenceMode) "Central Jakarta" else "$healthyTrees/${s.totalTrees} cây khỏe · Đất ẩm 64%",
                            healthyBadgeText = "$healthyTrees/${s.totalTrees} khỏe",
                            backendOnline = backendOnline,
                            alertCount = needs,
                            isCompact = true,
                            onOpenSatellite = { c.nav.top("map") },
                            onOpenAlerts = { c.nav.top("issues|trees") },
                        )
                    } else {
                        AuroraHeaderBar(
                            helloText = if (referenceMode) "Welcome" else "${loc.hello} ${d.garden.name}",
                            whoText = if (referenceMode) "Calfin Danang" else "${d.garden.operatorName} · ${if (activeCountry == AppCountry.VN) "Bảo Lộc" else activeCountry.sampleLocation}",
                            backendOnline = backendOnline,
                            alertCount = work.total.coerceAtLeast(needs),
                            operatorInitials = if (referenceMode) "CD" else activeCountry.countryCode,
                            country = activeCountry,
                            onAddLog = { c.nav.push("log") },
                            onCountryPicker = { VUi.countryPickerOpen = true },
                            onAlerts = { c.nav.top("issues|trees") },
                            onProfile = { c.nav.top("setup") },
                            isCompact = true,
                        )
                    }

                    AuroraHeroBlock(
                        chipText = if (referenceMode) "Weather · ${activeDay.dayLabel}" else "${loc.todayWord} · ${activeDay.dayLabel}",
                        modeBadgeText = if (c.simpleMode && !referenceMode) "" else if (referenceMode) "Mẫu ⇄ Vườn" else "Vườn ⇄ Mẫu",
                        line1 = activeDay.line1,
                        line2 = activeDay.line2,
                        blurb = activeDay.advice,
                        sheenProgress = sheenProgress,
                        onToggleMode = { referenceMode = !referenceMode },
                        isCompact = true,
                    )

                    AuroraQuickActionRow(
                        needsCount = needs,
                        isCompact = true,
                        onOpenMap = { c.nav.top("map") },
                        onOpenSatellite = { c.nav.top("env") },
                        onOpenAlerts = { c.nav.top("issues|trees") },
                        onOpenAi = { c.nav.top("ai") },
                        onAddLog = { c.nav.push("log") },
                    )

                    AuroraForecastStrip(
                        items = forecastItems,
                        selectedIdx = selectedDayIdx,
                        onSelectIdx = { selectedDayIdx = it },
                        lineProgress = lineProgress,
                        fillProgress = fillProgress,
                        isCompact = true,
                    )

                    AuroraRightRail(
                        modifier = Modifier.fillMaxWidth(),
                        placeTitle = if (referenceMode) "Central Jakarta" else d.garden.location.ifBlank { "Bảo Lộc, Lâm Đồng" },
                        bigTemp = activeDay.tempStr,
                        metric1 = if (referenceMode) activeDay.windStr else "${activeDay.windStr} km/h",
                        metric2 = if (referenceMode) "${activeDay.rainProb}% rain" else "Mưa ${activeDay.rainProb}%",
                        metric3 = if (referenceMode) "15km/h" else "Ẩm đất 64%",
                        cardB = cardBData,
                        cardC = cardCData,
                        cardD = cardDData,
                        onClickBigCard = { c.nav.top("weather") },
                        sheenProgress = sheenProgress,
                    )

                    if (!embeddedInAppShell) {
                        AuroraMobileDock(
                            activeRoute = c.nav.head,
                            alertCount = needs,
                            onNavigate = { route -> c.nav.top(route) },
                            onTogglePreset = { referenceMode = !referenceMode },
                        )
                    }
                }
            }
        }
    }

    if (!embeddedInAppShell && VUi.countryPickerOpen) {
        CountryPickerDialog(
            current = activeCountry,
            onSelect = { chosen ->
                VUi.country = chosen
                AppLocaleStore.save(context, chosen)
                java.util.Locale.setDefault(chosen.toLocale())
                VUi.countryPickerOpen = false
                c.toast(AppStrings.forCountry(chosen).switchedToast(chosen))
            },
            onDismiss = { VUi.countryPickerOpen = false },
        )
    }
}

private data class AuroraRowCardData(
    val countryOrZone: String,
    val cityOrTitle: String,
    val conditionOrSub: String,
    val tempText: String,
    val icon: AuroraIconKind,
    val onClick: () -> Unit,
)

@Composable
private fun AuroraSidebarRail(
    activeRoute: String,
    alertCount: Int,
    onNavigate: (String) -> Unit,
    onTogglePreset: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val navItems = listOf(
        Triple("overview", AuroraIconKind.GRID, "Dashboard"),
        Triple("satellite", AuroraIconKind.CHART, "Reports"),
        Triple("map", AuroraIconKind.GLOBE, "Explore regions"),
        Triple("issues", AuroraIconKind.CAL, "Calendar"),
        Triple("ai", AuroraIconKind.GEAR, "Settings"),
    )
    val activeIdx = navItems.indexOfFirst { it.first == activeRoute }.coerceAtLeast(0)

    Box(
        modifier = Modifier
            .width(72.dp)
            .heightIn(min = 650.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.125f),
                        0.13f to Color.White.copy(alpha = 0.135f),
                        0.34f to Color.White.copy(alpha = 0.098f),
                        1.00f to Color.White.copy(alpha = 0.092f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
            .padding(top = 22.dp, bottom = 36.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Chấm pip trắng phát sáng bên mép trái
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-1).dp, y = (104 + activeIdx * 66).dp)
                .width(5.dp)
                .height(29.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo 40x40 wave-circle mark
                AuroraWaveLogo(
                    size = 40.dp,
                    onClick = { onNavigate("overview") },
                )

                Spacer(Modifier.height(44.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    navItems.forEachIndexed { idx, (route, iconKind, _) ->
                        val selected = idx == activeIdx
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                .bounceClick { onNavigate(route) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AuroraVectorIcon(
                                kind = iconKind,
                                size = 23.dp,
                                alpha = if (selected) 1f else 0.78f,
                            )
                            if (route == "issues" && alertCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.28f))
                                        .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$alertCount",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // Nút dưới đáy: mở toàn bộ công cụ / đổi chế độ
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .bounceClick { onTogglePreset() },
                contentAlignment = Alignment.Center,
            ) {
                AuroraVectorIcon(kind = AuroraIconKind.OUT, size = 23.dp, alpha = 0.82f)
            }
        }
    }
}

@Composable
private fun AuroraMobileDock(
    activeRoute: String,
    alertCount: Int,
    onNavigate: (String) -> Unit,
    onTogglePreset: () -> Unit,
) {
    val navItems = listOf(
        "overview" to AuroraIconKind.GRID,
        "satellite" to AuroraIconKind.CHART,
        "map" to AuroraIconKind.GLOBE,
        "issues" to AuroraIconKind.CAL,
        "ai" to AuroraIconKind.GEAR,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.11f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AuroraWaveLogo(size = 34.dp, onClick = { onNavigate("overview") })

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { (route, iconKind) ->
                val selected = activeRoute == route
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        .bounceClick { onNavigate(route) },
                    contentAlignment = Alignment.Center,
                ) {
                    AuroraVectorIcon(kind = iconKind, size = 22.dp, alpha = if (selected) 1f else 0.78f)
                    if (route == "issues" && alertCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.30f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$alertCount", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .bounceClick { onTogglePreset() },
            contentAlignment = Alignment.Center,
        ) {
            AuroraVectorIcon(kind = AuroraIconKind.OUT, size = 21.dp, alpha = 0.85f)
        }
    }
}

@Composable
private fun AuroraStageStatusBar(
    titleText: String,
    subText: String,
    healthyBadgeText: String = "15/18 cây khỏe",
    backendOnline: Boolean,
    alertCount: Int,
    isCompact: Boolean,
    onOpenSatellite: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val isPro = VC.isPro
    val mainTextCol = if (isPro) Color.White else Color(0xFF0F2419)
    val subTextCol = if (isPro) Color.White.copy(alpha = 0.84f) else Color(0xFF355244)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (backendOnline) (if (isPro) Color(0xFF38EF7D) else Color(0xFF16794B)) else Color(0xFFFFD166))
                )
                Text(
                    text = titleText,
                    color = mainTextCol,
                    fontSize = if (isCompact) 14.5.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = subText,
                color = subTextCol,
                fontSize = if (isCompact) 12.5.sp else 13.8.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isCompact) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (isPro) Color(0xFF38EF7D).copy(alpha = 0.16f) else Color(0xFFE6F4EC))
                        .border(1.dp, if (isPro) Color(0xFF38EF7D).copy(alpha = 0.42f) else Color(0xFF86EFAC), RoundedCornerShape(19.dp))
                        .bounceClick(onClick = onOpenSatellite)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = healthyBadgeText,
                        color = if (isPro) Color(0xFFD8FCE4) else Color(0xFF14532D),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            if (alertCount > 0) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (isPro) Color(0xFFFFB340).copy(alpha = 0.22f) else Color(0xFFFEF3C7))
                        .border(1.dp, if (isPro) Color(0xFFFFB340).copy(alpha = 0.55f) else Color(0xFFF59E0B), RoundedCornerShape(19.dp))
                        .bounceClick(onClick = onOpenAlerts)
                        .padding(horizontal = if (isCompact) 11.dp else 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCompact) "$alertCount cây" else "$alertCount cây cần thăm",
                        color = if (isPro) Color(0xFFFFE5B4) else Color(0xFF92400E),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraHeaderBar(
    helloText: String,
    whoText: String,
    backendOnline: Boolean,
    alertCount: Int,
    operatorInitials: String,
    country: AppCountry,
    onAddLog: () -> Unit,
    onCountryPicker: () -> Unit,
    onAlerts: () -> Unit,
    onProfile: () -> Unit,
    isCompact: Boolean = false,
) {
    // Theo đúng đặc tả Aurora Mobile (≤860px / ≤420px): .tool, .avatar { width:36px; height:36px } .tools { gap:8px }
    val toolSize = if (isCompact) 36.dp else 52.dp
    val iconSize = if (isCompact) 17.dp else 21.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (backendOnline) Color(0xFFA8DF65) else Color(0xFFFFD166))
                )
                Text(
                    text = helloText,
                    color = Color.White.copy(alpha = 0.93f),
                    fontSize = if (isCompact) 13.sp else 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(if (isCompact) 3.dp else 8.dp))
            Text(
                text = whoText,
                color = Color.White,
                fontSize = if (isCompact) 15.5.sp else 19.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.35).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraToolCircle(size = toolSize, onClick = onAddLog) {
                AuroraVectorIcon(AuroraIconKind.PLUS, size = iconSize)
            }
            AuroraToolCircle(size = toolSize, onClick = onCountryPicker) {
                Text(
                    text = country.flag,
                    fontSize = if (isCompact) 18.sp else 23.sp,
                )
            }
            AuroraToolCircle(size = toolSize, badgeCount = alertCount, onClick = onAlerts) {
                AuroraVectorIcon(AuroraIconKind.BELL, size = iconSize)
            }
            AuroraToolCircle(size = toolSize, onClick = onProfile) {
                Text(
                    text = operatorInitials,
                    color = Color.White,
                    fontSize = if (isCompact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AuroraToolCircle(
    size: Dp,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.30f))
                    .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$badgeCount",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AuroraHeroBlock(
    chipText: String,
    modeBadgeText: String,
    line1: String,
    line2: String,
    blurb: String,
    sheenProgress: Float,
    onToggleMode: () -> Unit,
    isCompact: Boolean,
) {
    val isPro = VC.isPro
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val ttsRef = remember { arrayOfNulls<android.speech.tts.TextToSpeech>(1) }
    androidx.compose.runtime.DisposableEffect(context) {
        var instance: android.speech.tts.TextToSpeech? = null
        runCatching {
            instance = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    instance?.language = java.util.Locale("vi", "VN")
                    ttsReady = true
                }
            }
            ttsRef[0] = instance
        }
        onDispose {
            runCatching {
                instance?.stop()
                instance?.shutdown()
            }
            ttsRef[0] = null
        }
    }

    val mainTextCol = if (isPro) Color.White else Color(0xFF0F2419)
    val subTextCol = if (isPro) Color.White.copy(alpha = 0.95f) else Color(0xFF355244)
    val pillBg = if (isPro) Color.White.copy(alpha = 0.175f) else Color(0xFFEEF5F1)
    val pillBorder = if (isPro) Color.White.copy(alpha = 0.22f) else Color(0xFFC8DCD2)
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(pillBg)
                    .border(1.dp, pillBorder, RoundedCornerShape(18.dp))
                    .specularSheen(sheenProgress)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chipText,
                    color = mainTextCol,
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Nút đọc giọng nói 1 chạm cho nông dân ngoài vườn nắng chói
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSpeaking) VC.mint.copy(alpha = 0.25f) else if (isPro) Color.White.copy(alpha = 0.15f) else Color.White)
                    .border(1.dp, if (isSpeaking) VC.mint else pillBorder, RoundedCornerShape(18.dp))
                    .bounceClick {
                        performConfirmHaptic(view)
                        val engine = ttsRef[0]
                        if (engine != null && ttsReady) {
                            if (isSpeaking && engine.isSpeaking) {
                                engine.stop()
                                isSpeaking = false
                            } else {
                                val speechText = "$line1 ${line2.removePrefix("&").trim()}. $blurb"
                                engine.speak(speechText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "aurora_hero_tts")
                                isSpeaking = true
                            }
                        } else {
                            android.widget.Toast.makeText(context, blurb, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isSpeaking) "Đang đọc" else "Nghe đọc",
                    color = if (isSpeaking) VC.mint else mainTextCol,
                    fontSize = if (isCompact) 11.5.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            if (modeBadgeText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isPro) Color.White.copy(alpha = 0.135f) else Color.White)
                        .border(1.dp, pillBorder, RoundedCornerShape(18.dp))
                        .bounceClick(onClick = onToggleMode)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = modeBadgeText,
                        color = mainTextCol,
                        fontSize = if (isCompact) 11.5.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(if (isCompact) 8.dp else 10.dp))

        if (modeBadgeText.isBlank()) {
            val combinedHeadline = "$line1 ${line2.removePrefix("&").trim()}"
            Text(
                text = combinedHeadline,
                color = mainTextCol,
                fontSize = if (isCompact) 26.sp else 36.sp,
                lineHeight = if (isCompact) 33.sp else 43.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = line1,
                color = mainTextCol,
                fontSize = if (isCompact) 30.sp else 46.sp,
                lineHeight = if (isCompact) 36.sp else 54.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = line2,
                color = mainTextCol,
                fontSize = if (isCompact) 30.sp else 46.sp,
                lineHeight = if (isCompact) 36.sp else 54.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(if (isCompact) 6.dp else 8.dp))

        Text(
            text = blurb,
            color = subTextCol,
            fontSize = if (isCompact) 13.8.sp else 14.8.sp,
            lineHeight = if (isCompact) 20.5.sp else 23.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Composable
private fun AuroraForecastStrip(
    items: List<Triple<String, AuroraIconKind, String>>,
    selectedIdx: Int,
    onSelectIdx: (Int) -> Unit,
    lineProgress: Float,
    fillProgress: Float,
    isCompact: Boolean,
) {
    val isPro = VC.isPro
    val mainTextCol = if (isPro) Color.White else Color(0xFF0F2419)
    val activeAccent = if (isPro) Color(0xFFA8DF65) else Color(0xFF0F2B1D)
    val waveColor = if (isPro) Color.White else Color(0xFF15803D)
    Column(modifier = Modifier.fillMaxWidth()) {
        // Hàng nhiệt độ + icon thời tiết (6 cột chia đều không bao giờ tràn, có pill kính mờ khi được chọn)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
        ) {
            items.forEachIndexed { idx, (tempStr, iconKind, _) ->
                val active = idx == selectedIdx
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (active) {
                                if (isPro) Color.White.copy(alpha = 0.16f) else Color(0xFFE2ECE7)
                            } else {
                                Color.Transparent
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (active) {
                                if (isPro) Color.White.copy(alpha = 0.36f) else Color(0xFFB7D4C5)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelectIdx(idx) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = tempStr,
                        color = if (active) activeAccent else mainTextCol,
                        fontSize = if (isCompact) 21.sp else 30.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = (-0.7).sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    AuroraVectorIcon(
                        kind = iconKind,
                        size = if (isCompact) 20.dp else 25.dp,
                        alpha = if (active) 1f else 0.86f,
                    )
                }
            }
        }

        Spacer(Modifier.height(if (isCompact) 8.dp else 10.dp))

        // Biểu đồ sóng SVG viewBox="0 0 835 230" tự vẽ nét (drawLine) rồi quét nền (wipeX) + nút sáng tại ngày đang chọn
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 100.dp else 112.dp),
        ) {
            val w = size.width
            val h = size.height
            fun sx(x: Float) = (x / 835f) * w
            fun sy(y: Float) = (y / 230f) * h

            val wavePath = Path().apply {
                moveTo(sx(0f), sy(79f))
                cubicTo(sx(68f), sy(79f), sx(95f), sy(38f), sx(155f), sy(38f))
                cubicTo(sx(215f), sy(38f), sx(242f), sy(112f), sx(305f), sy(112f))
                cubicTo(sx(368f), sy(112f), sx(398f), sy(176f), sx(462f), sy(176f))
                cubicTo(sx(526f), sy(176f), sx(558f), sy(28f), sx(625f), sy(28f))
                cubicTo(sx(692f), sy(28f), sx(735f), sy(86f), sx(835f), sy(86f))
            }

            val fillPath = Path().apply {
                addPath(wavePath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            // Fill wipe left -> right (#wclipr)
            clipRect(left = 0f, top = 0f, right = w * fillProgress.coerceIn(0f, 1f), bottom = h) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to waveColor.copy(alpha = if (isPro) 0.34f else 0.22f),
                            0.58f to waveColor.copy(alpha = if (isPro) 0.10f else 0.07f),
                            1.00f to Color.Transparent,
                        )
                    ),
                )
            }

            // Stroke drawLine (pathLength="1") với 3 độ dày 6.2@.17, 4.6@.26, 3.4@1
            val measure = PathMeasure()
            measure.setPath(wavePath, false)
            val drawnPath = Path()
            measure.getSegment(0f, measure.length * lineProgress.coerceIn(0f, 1f), drawnPath, true)

            val strokeBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to waveColor.copy(alpha = 0.18f),
                    0.18f to waveColor.copy(alpha = 0.88f),
                    0.52f to waveColor.copy(alpha = 1.00f),
                    0.84f to waveColor.copy(alpha = 0.90f),
                    1.00f to waveColor.copy(alpha = 0.22f),
                )
            )

            drawPath(
                path = drawnPath,
                brush = strokeBrush,
                alpha = 0.17f,
                style = Stroke(width = 6.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = drawnPath,
                brush = strokeBrush,
                alpha = 0.26f,
                style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = drawnPath,
                brush = strokeBrush,
                alpha = 1.0f,
                style = Stroke(width = 3.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Điểm đánh dấu trên đường sóng tương ứng với cột ngày đang chọn (0..5)
            val anchorPoints = listOf(
                70f to 62f,
                210f to 64f,
                350f to 132f,
                462f to 176f,
                625f to 28f,
                765f to 80f,
            )
            val (ax, ay) = anchorPoints.getOrElse(selectedIdx.coerceIn(0, 5)) { anchorPoints[3] }
            val px = sx(ax)
            val py = sy(ay)
            if (lineProgress > 0.4f) {
                drawLine(
                    color = waveColor.copy(alpha = 0.30f),
                    start = Offset(px, py),
                    end = Offset(px, h - 8.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                )
                drawCircle(
                    color = activeAccent.copy(alpha = 0.28f),
                    radius = 12.dp.toPx(),
                    center = Offset(px, py),
                )
                drawCircle(
                    color = if (isPro) Color.White else Color(0xFF0F2B1D),
                    radius = 6.dp.toPx(),
                    center = Offset(px, py),
                )
                drawCircle(
                    color = if (isPro) Color(0xFF04121B) else Color.White,
                    radius = 2.6.dp.toPx(),
                    center = Offset(px, py),
                )
            }
        }

        // Hàng thứ trong tuần bên dưới biểu đồ (6 cột chia đều, vùng chạm >= 44dp, maxLines = 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-8).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
        ) {
            items.forEachIndexed { idx, (_, _, dayLabel) ->
                val active = idx == selectedIdx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) {
                                if (isPro) Color.White.copy(alpha = 0.14f) else Color(0xFFE2ECE7)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelectIdx(idx) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = dayLabel,
                        color = if (active) activeAccent else if (isPro) Color.White.copy(alpha = 0.84f) else Color(0xFF355244),
                        fontSize = if (isCompact) 11.5.sp else 14.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraBigWeatherCard(
    modifier: Modifier = Modifier,
    placeTitle: String,
    bigTemp: String,
    metric1: String,
    metric2: String,
    metric3: String,
    onClickBigCard: () -> Unit,
    sheenProgress: Float,
) {
    val mainCol = if (VC.isPro) Color.White else Color(0xFF0F2419)
    AuroraLiquidGlassCard(
        cornerRadius = 26.dp,
        sheenProgress = sheenProgress,
        onClick = onClickBigCard,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = placeTitle,
                color = mainCol,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            )
            AuroraVectorIcon(AuroraIconKind.PIN, size = 18.dp)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = bigTemp,
                color = mainCol,
                fontSize = 68.sp,
                lineHeight = 68.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-3.2).sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "C",
                color = mainCol.copy(alpha = 0.92f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Normal,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraMetricPill(AuroraIconKind.WIND, metric1)
            AuroraMetricPill(AuroraIconKind.DROP, metric2)
            AuroraMetricPill(AuroraIconKind.GUST, metric3)
        }
    }
}

@Composable
private fun AuroraSmallRowCard(
    rowCard: AuroraRowCardData,
    sheenProgress: Float,
    modifier: Modifier = Modifier,
) {
    val isPro = VC.isPro
    val mainCol = if (isPro) Color.White else Color(0xFF0F2419)
    val subCol = if (isPro) Color.White.copy(alpha = 0.88f) else Color(0xFF355244)
    val mutedCol = if (isPro) Color.White.copy(alpha = 0.78f) else Color(0xFF4E6B5D)
    val accentCol = if (isPro) Color(0xFFA8DF65) else Color(0xFF15803D)
    AuroraLiquidGlassCard(
        cornerRadius = 24.dp,
        sheenProgress = sheenProgress,
        onClick = rowCard.onClick,
        modifier = modifier.heightIn(min = 104.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = rowCard.countryOrZone,
                    color = mutedCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rowCard.cityOrTitle,
                    color = mainCol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rowCard.conditionOrSub,
                    color = subCol,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 6.dp),
            ) {
                val isShortTemp = rowCard.tempText.length <= 4
                Text(
                    text = rowCard.tempText,
                    color = if (isShortTemp) mainCol else accentCol,
                    fontSize = if (isShortTemp) 30.sp else 17.5.sp,
                    fontWeight = if (isShortTemp) FontWeight.Medium else FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    softWrap = false,
                )
                if (isShortTemp) {
                    AuroraVectorIcon(rowCard.icon, size = 22.dp, alpha = 0.92f)
                } else {
                    Text(
                        text = "Xem →",
                        color = mutedCol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraTabletPortraitCardsGrid(
    placeTitle: String,
    bigTemp: String,
    metric1: String,
    metric2: String,
    metric3: String,
    cardB: AuroraRowCardData,
    cardC: AuroraRowCardData,
    cardD: AuroraRowCardData,
    onClickBigCard: () -> Unit,
    sheenProgress: Float,
) {
    val cardWeather = AuroraRowCardData(
        countryOrZone = placeTitle,
        cityOrTitle = "$metric2 · $metric3",
        conditionOrSub = "Gió $metric1 · Chạm xem chi tiết",
        tempText = "${bigTemp}C",
        icon = AuroraIconKind.CLOUD2,
        onClick = onClickBigCard,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AuroraSmallRowCard(
                rowCard = cardWeather,
                sheenProgress = sheenProgress,
                modifier = Modifier.weight(1f),
            )
            AuroraSmallRowCard(
                rowCard = cardD,
                sheenProgress = sheenProgress,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AuroraSmallRowCard(
                rowCard = cardB,
                sheenProgress = sheenProgress,
                modifier = Modifier.weight(1f),
            )
            AuroraSmallRowCard(
                rowCard = cardC,
                sheenProgress = sheenProgress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AuroraRightRail(
    modifier: Modifier = Modifier,
    placeTitle: String,
    bigTemp: String,
    metric1: String,
    metric2: String,
    metric3: String,
    cardB: AuroraRowCardData,
    cardC: AuroraRowCardData,
    cardD: AuroraRowCardData,
    onClickBigCard: () -> Unit,
    sheenProgress: Float,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuroraBigWeatherCard(
            modifier = Modifier.fillMaxWidth(),
            placeTitle = placeTitle,
            bigTemp = bigTemp,
            metric1 = metric1,
            metric2 = metric2,
            metric3 = metric3,
            onClickBigCard = onClickBigCard,
            sheenProgress = sheenProgress,
        )

        listOf(cardB, cardC, cardD).forEach { rowCard ->
            AuroraSmallRowCard(
                rowCard = rowCard,
                sheenProgress = sheenProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuroraMetricPill(icon: AuroraIconKind, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AuroraVectorIcon(icon, size = 15.dp, alpha = 0.92f)
        Text(
            text = label,
            color = if (VC.isPro) Color.White.copy(alpha = 0.94f) else Color(0xFF355244),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun AuroraQuickActionRow(
    needsCount: Int,
    isCompact: Boolean,
    onOpenMap: () -> Unit,
    onOpenSatellite: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAi: () -> Unit,
    onAddLog: () -> Unit = {},
) {
    val isPro = VC.isPro
    val calmDot = if (isPro) Color(0xFF4ADE80).copy(alpha = 0.78f) else Color(0xFF16794B)
    val alertDot = if (needsCount > 0) (if (isPro) Color(0xFFFBBF24) else Color(0xFFD97706)) else calmDot
    val cardBg = if (isPro) Color(0xFF0A1E2B).copy(alpha = 0.92f) else Color(0xFFF4F8F6)
    val neutralBorder = if (isPro) Color.White.copy(alpha = 0.14f) else Color(0xFFD5E2DC)
    val cardsLoc = AppStrings.cards(VUi.country)
    val actions = listOf(
        Triple(cardsLoc.quickCheck(needsCount), alertDot, onOpenAlerts),
        Triple(cardsLoc.quickLog, calmDot, onAddLog),
        Triple(cardsLoc.quickMap, calmDot, onOpenMap),
        Triple(cardsLoc.quickAi, calmDot, onOpenAi),
    )
    if (isCompact) {
        // Bố cục lưới 2×2 trên điện thoại (Medium Phone): vùng chạm ≥48dp, bảng màu tiết chế đồng nhất
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEachIndexed { idx, (label, dotColor, onClick) ->
                        val isAlertBtn = label == cardsLoc.quickCheck(needsCount) && needsCount > 0
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .border(
                                    1.dp,
                                    if (isAlertBtn) dotColor.copy(alpha = 0.45f) else neutralBorder,
                                    RoundedCornerShape(14.dp),
                                )
                                .bounceClick(onClick = onClick)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = label,
                                color = if (isPro) Color.White else Color(0xFF0F2419),
                                fontSize = 12.5.sp,
                                letterSpacing = (-0.2).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (label, dotColor, onClick) ->
            val isAlertBtn = label.startsWith("Cây cần thăm") && needsCount > 0
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(
                        1.dp,
                        if (isAlertBtn) dotColor.copy(alpha = 0.45f) else neutralBorder,
                        RoundedCornerShape(14.dp),
                    )
                    .bounceClick(onClick = onClick)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    color = if (isPro) Color.White else Color(0xFF0F2419),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AuroraLiquidGlassCard(
    cornerRadius: Dp,
    sheenProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isPro = VC.isPro
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (isPro) {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF0C2230).copy(alpha = 0.92f),
                            0.50f to Color(0xFF091B26).copy(alpha = 0.90f),
                            1.00f to Color(0xFF071620).copy(alpha = 0.92f),
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF7FAF8),
                            Color(0xFFFFFFFF),
                        )
                    )
                }
            )
            .border(1.dp, if (isPro) Color.White.copy(alpha = 0.18f) else Color(0xFFD5E2DC), shape)
            .then(if (isPro) Modifier.specularSheen(sheenProgress) else Modifier)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

/** Hiệu ứng vệt sáng Specular Sheen lướt chéo qua thẻ kính mờ như trong Aurora Weather */
private fun Modifier.specularSheen(progress: Float): Modifier = this.drawWithContent {
    drawContent()
    if (progress > 0.01f && progress < 0.99f) {
        val w = size.width
        val bandW = w * 0.38f
        val startX = -bandW + (w + bandW * 2.2f) * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.17f),
                    Color.Transparent,
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + bandW, size.height * 0.35f),
            )
        )
    }
}

/** Logo 40x40 hình vuông bo góc rx=12 với 7 đường sóng ngang cắt trong hình tròn */
@Composable
private fun AuroraWaveLogo(size: Dp, onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick),
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
            size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
            size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = stroke,
        )

        val circleClip = Path().apply {
            val r = w * 0.34f
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(w / 2f, h / 2f),
                    radius = r,
                )
            )
        }
        clipPath(circleClip) {
            val ys = listOf(0.24f, 0.33f, 0.42f, 0.50f, 0.58f, 0.67f, 0.76f)
            ys.forEach { yf ->
                val y = h * yf
                val amp = h * 0.055f
                val p = Path().apply {
                    moveTo(w * 0.12f, y)
                    quadraticTo(w * 0.31f, y - amp, w * 0.50f, y)
                    quadraticTo(w * 0.69f, y + amp, w * 0.88f, y)
                }
                drawPath(p, Color.White, style = stroke)
            }
        }
    }
}

/** Bộ biểu tượng vector chuẩn theo 17 symbol của Aurora Weather */
@Composable
private fun AuroraVectorIcon(
    kind: AuroraIconKind,
    size: Dp = 22.dp,
    alpha: Float = 1f,
) {
    val baseIconCol = if (VC.isPro) Color.White else Color(0xFF0F2B1D)
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sx = w / 24f
        val sy = h / 24f
        val c = baseIconCol.copy(alpha = alpha)
        val s = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (kind) {
            AuroraIconKind.GRID -> {
                val r = CornerRadius(2f * sx, 2f * sy)
                drawRoundRect(c, Offset(3f * sx, 3f * sy), Size(7f * sx, 7f * sy), r, s)
                drawRoundRect(c, Offset(14f * sx, 3f * sy), Size(7f * sx, 7f * sy), r, s)
                drawRoundRect(c, Offset(14f * sx, 14f * sy), Size(7f * sx, 7f * sy), r, s)
                drawRoundRect(c, Offset(3f * sx, 14f * sy), Size(7f * sx, 7f * sy), r, s)
            }
            AuroraIconKind.CHART -> {
                drawLine(c, Offset(6f * sx, 20f * sy), Offset(6f * sx, 14f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(12f * sx, 20f * sy), Offset(12f * sx, 4f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(18f * sx, 20f * sy), Offset(18f * sx, 10f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.GLOBE -> {
                drawCircle(c, radius = 9f * sx, center = Offset(12f * sx, 12f * sy), style = s)
                drawLine(c, Offset(3f * sx, 12f * sy), Offset(21f * sx, 12f * sy), s.width, StrokeCap.Round)
                drawOval(c, Offset(8f * sx, 3f * sy), Size(8f * sx, 18f * sy), style = s)
            }
            AuroraIconKind.CAL -> {
                drawRoundRect(c, Offset(3f * sx, 4f * sy), Size(18f * sx, 17f * sy), CornerRadius(3f * sx, 3f * sy), s)
                drawLine(c, Offset(8f * sx, 2.5f * sy), Offset(8f * sx, 6f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(16f * sx, 2.5f * sy), Offset(16f * sx, 6f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(3f * sx, 9.5f * sy), Offset(21f * sx, 9.5f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.GEAR -> {
                drawCircle(c, radius = 3.3f * sx, center = Offset(12f * sx, 12f * sy), style = s)
                drawCircle(
                    c,
                    radius = 7.8f * sx,
                    center = Offset(12f * sx, 12f * sy),
                    style = Stroke(
                        width = s.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.2f * sx, 3.0f * sx), 0f),
                    ),
                )
            }
            AuroraIconKind.OUT -> {
                val p = Path().apply {
                    moveTo(9f * sx, 21f * sy)
                    lineTo(5f * sx, 21f * sy)
                    lineTo(5f * sx, 3f * sy)
                    lineTo(9f * sx, 3f * sy)
                }
                drawPath(p, c, style = s)
                drawLine(c, Offset(9f * sx, 12f * sy), Offset(21f * sx, 12f * sy), s.width, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(16f * sx, 7f * sy)
                    lineTo(21f * sx, 12f * sy)
                    lineTo(16f * sx, 17f * sy)
                }
                drawPath(arrow, c, style = s)
            }
            AuroraIconKind.PLUS -> {
                drawLine(c, Offset(12f * sx, 5f * sy), Offset(12f * sx, 19f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(5f * sx, 12f * sy), Offset(19f * sx, 12f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.SEARCH -> {
                drawCircle(c, radius = 6.8f * sx, center = Offset(11f * sx, 11f * sy), style = s)
                drawLine(c, Offset(16f * sx, 16f * sy), Offset(20f * sx, 20f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.BELL -> {
                val p = Path().apply {
                    moveTo(6f * sx, 8f * sy)
                    cubicTo(6f * sx, 4.5f * sy, 18f * sx, 4.5f * sy, 18f * sx, 8f * sy)
                    lineTo(20f * sx, 17f * sy)
                    lineTo(4f * sx, 17f * sy)
                    close()
                }
                drawPath(p, c, style = s)
                drawLine(c, Offset(10f * sx, 20.5f * sy), Offset(14f * sx, 20.5f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.PIN -> {
                drawCircle(c, radius = 2.6f * sx, center = Offset(12f * sx, 10f * sy), style = s)
                val p = Path().apply {
                    moveTo(12f * sx, 22f * sy)
                    cubicTo(6f * sx, 16f * sy, 4.5f * sx, 13f * sy, 4.5f * sx, 9.8f * sy)
                    cubicTo(4.5f * sx, 5.5f * sy, 7.8f * sx, 2.5f * sy, 12f * sx, 2.5f * sy)
                    cubicTo(16.2f * sx, 2.5f * sy, 19.5f * sx, 5.5f * sy, 19.5f * sx, 9.8f * sy)
                    cubicTo(19.5f * sx, 13f * sy, 18f * sx, 16f * sy, 12f * sx, 22f * sy)
                }
                drawPath(p, c, style = s)
            }
            AuroraIconKind.WIND -> {
                drawLine(c, Offset(2f * sx, 8f * sy), Offset(15f * sx, 8f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(2f * sx, 12f * sy), Offset(19f * sx, 12f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(2f * sx, 16f * sy), Offset(13f * sx, 16f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.DROP -> {
                val p = Path().apply {
                    moveTo(12f * sx, 3f * sy)
                    cubicTo(17f * sx, 9f * sy, 19f * sx, 12.5f * sy, 19f * sx, 15.5f * sy)
                    cubicTo(19f * sx, 19.5f * sy, 15.8f * sx, 21.5f * sy, 12f * sx, 21.5f * sy)
                    cubicTo(8.2f * sx, 21.5f * sy, 5f * sx, 19.5f * sy, 5f * sx, 15.5f * sy)
                    cubicTo(5f * sx, 12.5f * sy, 7f * sx, 9f * sy, 12f * sx, 3f * sy)
                }
                drawPath(p, c, style = s)
            }
            AuroraIconKind.GUST -> {
                drawLine(c, Offset(3f * sx, 8f * sy), Offset(16f * sx, 8f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(3f * sx, 12f * sy), Offset(20f * sx, 12f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(3f * sx, 16f * sy), Offset(12f * sx, 16f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.CLOUD -> {
                val p = Path().apply {
                    moveTo(7.5f * sx, 18.5f * sy)
                    lineTo(17.5f * sx, 18.5f * sy)
                    cubicTo(20f * sx, 18.5f * sy, 21.5f * sx, 16.8f * sy, 21.5f * sx, 14.5f * sy)
                    cubicTo(21.5f * sx, 12.4f * sy, 19.8f * sx, 10.8f * sy, 17.8f * sx, 10.6f * sy)
                    cubicTo(17f * sx, 7.2f * sy, 14f * sx, 5.2f * sy, 10.8f * sx, 5.8f * sy)
                    cubicTo(7.8f * sx, 6.4f * sy, 6.2f * sx, 8.8f * sy, 6.2f * sx, 11.2f * sy)
                    cubicTo(4.2f * sx, 11.6f * sy, 3f * sx, 13.3f * sy, 3f * sx, 15.2f * sy)
                    cubicTo(3f * sx, 17.1f * sy, 5f * sx, 18.5f * sy, 7.5f * sx, 18.5f * sy)
                }
                drawPath(p, c, style = s)
            }
            AuroraIconKind.CLOUD2 -> {
                val p = Path().apply {
                    moveTo(7.5f * sx, 16f * sy)
                    lineTo(17.2f * sx, 16f * sy)
                    cubicTo(19.6f * sx, 16f * sy, 21f * sx, 14.4f * sy, 21f * sx, 12.4f * sy)
                    cubicTo(21f * sx, 10.4f * sy, 19.4f * sx, 9f * sy, 17.5f * sx, 8.8f * sy)
                    cubicTo(16.6f * sx, 5.8f * sy, 13.8f * sx, 4.2f * sy, 10.8f * sx, 4.8f * sy)
                    cubicTo(8f * sx, 5.4f * sy, 6.4f * sx, 7.6f * sy, 6.4f * sx, 9.8f * sy)
                    cubicTo(4.4f * sx, 10.2f * sy, 3.2f * sx, 11.6f * sy, 3.2f * sx, 13.3f * sy)
                    cubicTo(3.2f * sx, 14.9f * sy, 5.1f * sx, 16f * sy, 7.5f * sx, 16f * sy)
                }
                drawPath(p, c, style = s)
                drawLine(c, Offset(8f * sx, 18.5f * sy), Offset(7f * sx, 21f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(12f * sx, 18.5f * sy), Offset(11f * sx, 21f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(16f * sx, 18.5f * sy), Offset(15f * sx, 21f * sy), s.width, StrokeCap.Round)
            }
            AuroraIconKind.HAIL -> {
                val p = Path().apply {
                    moveTo(7.5f * sx, 15.5f * sy)
                    lineTo(17.2f * sx, 15.5f * sy)
                    cubicTo(19.6f * sx, 15.5f * sy, 21f * sx, 14f * sy, 21f * sx, 12f * sy)
                    cubicTo(21f * sx, 10f * sy, 19.4f * sx, 8.6f * sy, 17.5f * sx, 8.4f * sy)
                    cubicTo(16.6f * sx, 5.5f * sy, 13.8f * sx, 4f * sy, 10.8f * sx, 4.5f * sy)
                    cubicTo(8f * sx, 5f * sy, 6.4f * sx, 7.2f * sy, 6.4f * sx, 9.4f * sy)
                    cubicTo(4.4f * sx, 9.8f * sy, 3.2f * sx, 11.2f * sy, 3.2f * sx, 12.8f * sy)
                    cubicTo(3.2f * sx, 14.4f * sy, 5.1f * sx, 15.5f * sy, 7.5f * sx, 15.5f * sy)
                }
                drawPath(p, c, style = s)
                val bolt = Path().apply {
                    moveTo(12.5f * sx, 15.5f * sy)
                    lineTo(10.2f * sx, 19f * sy)
                    lineTo(13.2f * sx, 19f * sy)
                    lineTo(11f * sx, 22f * sy)
                }
                drawPath(bolt, c, style = s)
            }
            AuroraIconKind.SUN -> {
                drawCircle(c, radius = 4.4f * sx, center = Offset(12f * sx, 12f * sy), style = s)
                drawLine(c, Offset(12f * sx, 2.5f * sy), Offset(12f * sx, 5f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(12f * sx, 19f * sy), Offset(12f * sx, 21.5f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(2.5f * sx, 12f * sy), Offset(5f * sx, 12f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(19f * sx, 12f * sy), Offset(21.5f * sx, 12f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(5.2f * sx, 5.2f * sy), Offset(7f * sx, 7f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(17f * sx, 17f * sy), Offset(18.8f * sx, 18.8f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(18.8f * sx, 5.2f * sy), Offset(17f * sx, 7f * sy), s.width, StrokeCap.Round)
                drawLine(c, Offset(7f * sx, 17f * sy), Offset(5.2f * sx, 18.8f * sy), s.width, StrokeCap.Round)
            }
        }
    }
}
