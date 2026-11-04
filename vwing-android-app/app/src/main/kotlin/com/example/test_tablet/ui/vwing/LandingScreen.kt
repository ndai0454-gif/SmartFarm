package com.example.test_tablet.ui.vwing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.R
import kotlinx.coroutines.launch

private object LC {
    val paper = Color(0xFFE2E6E4)
    val headerBg = Color(0xFFFAFBF6)
    val ink = Color(0xFF0D2117)
    val muted = Color(0xFF526159)
    val green = Color(0xFF2B503F)
    val olive = Color(0xFF628445)
    val line = Color(0xFFD4DCD8)
    val pillBg = Color(0xFFF0F5E8)
    val pillBorder = Color(0xFFDBE4D3)
    val liveDot = Color(0xFF6C964F)
    val visualBg = Color(0xFFE8EDDA)
    val phoneBezel = Color(0xFF253E32)
    val softGreenBg = Color(0xFFEEF3E7)
    val ctaBg = Color(0xFFE9F0D9)
    val ctaBorder = Color(0xFFDDE7CD)
}

private data class SatPreviewTab(
    val key: String,
    val tabLabel: String,
    val metricLabel: String,
    val metricValue: String,
    val metricCopy: String,
    val scaleLow: String,
    val scaleTitle: String,
    val scaleHigh: String,
    val sourceBadge: String,
    val dataNote: String,
    val drawableRes: Int,
    val gradientColors: List<Color>,
)

private val SAT_PREVIEW_TABS = listOf(
    SatPreviewTab(
        key = "NDVI",
        tabLabel = "NDVI",
        metricLabel = "NDVI TRUNG BÌNH",
        metricValue = "0,702",
        metricCopy = "Tín hiệu độ xanh của tán.\nKhông phải kết luận sức khỏe từng cây.",
        scaleLow = "−0,2",
        scaleTitle = "Độ xanh của tán",
        scaleHigh = "0,9",
        sourceBadge = "SENTINEL-2 · 08/2026",
        dataNote = "Vùng mẫu Bảo Lộc · 01/08–01/09/2026 (không gồm ngày cuối). Không phải dữ liệu vườn của bạn.",
        drawableRes = R.drawable.landing_ndvi,
        gradientColors = listOf(Color(0xFFA1784B), Color(0xFFE6C846), Color(0xFF8CC850), Color(0xFF1E8232)),
    ),
    SatPreviewTab(
        key = "EVI",
        tabLabel = "EVI",
        metricLabel = "EVI TRUNG BÌNH",
        metricValue = "0,657",
        metricCopy = "Bổ sung quan sát vùng tán dày.\nCần đối chiếu ảnh và thực địa.",
        scaleLow = "−0,2",
        scaleTitle = "Chỉ số thực vật tăng cường",
        scaleHigh = "1,0",
        sourceBadge = "SENTINEL-2 · 08/2026",
        dataNote = "Vùng mẫu Bảo Lộc · 01/08–01/09/2026 (không gồm ngày cuối). Không phải dữ liệu vườn của bạn.",
        drawableRes = R.drawable.landing_evi,
        gradientColors = listOf(Color(0xFFA1784B), Color(0xFFE6C846), Color(0xFF8CC850), Color(0xFF1E8232)),
    ),
    SatPreviewTab(
        key = "NDMI",
        tabLabel = "NDMI",
        metricLabel = "NDMI TRUNG BÌNH",
        metricValue = "0,247",
        metricCopy = "Tín hiệu liên quan nước trong tán.\nKhông đo trực tiếp độ ẩm đất.",
        scaleLow = "−0,4",
        scaleTitle = "Trạng thái nước trong tán",
        scaleHigh = "0,6",
        sourceBadge = "SENTINEL-2 · 08/2026",
        dataNote = "Vùng mẫu Bảo Lộc · 01/08–01/09/2026 (không gồm ngày cuối). Không phải dữ liệu vườn của bạn.",
        drawableRes = R.drawable.landing_ndmi,
        gradientColors = listOf(Color(0xFFB45F3C), Color(0xFFE6C846), Color(0xFF78C8A0), Color(0xFF1D6FA5)),
    ),
    SatPreviewTab(
        key = "water",
        tabLabel = "Nước mặt",
        metricLabel = "DIỆN TÍCH NƯỚC MỚI",
        metricValue = "0,00 ha",
        metricCopy = "Vùng mẫu không ghi nhận nước mới.\nKhông phải kết luận về ngập toàn vùng.",
        scaleLow = "Nước thường xuyên",
        scaleTitle = "Nước mới",
        scaleHigh = "Nước đã rút",
        sourceBadge = "SENTINEL-1 · 09/2026",
        dataNote = "Vùng mẫu Bảo Lộc · Kỳ nền 01–20/08/2026; sự kiện 01–10/09/2026. Cần kiểm tra thực địa.",
        drawableRes = R.drawable.landing_water,
        gradientColors = listOf(Color(0xFF8A9A8F), Color(0xFF1E78B8), Color(0xFFE0A030)),
    ),
)

/**
 * Màn hình khởi động giới thiệu VWing ("Hiểu từng cây. Chăm cả khu vườn.")
 * hiển thị đầu tiên khi mở ứng dụng, sau đó chuyển vào màn Tổng quan.
 */
@Composable
fun LandingScreen(
    onEnterOverview: (initialRoute: String) -> Unit,
) {
    VWingTheme {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        var featuresY by remember { mutableIntStateOf(0) }
        var satelliteY by remember { mutableIntStateOf(0) }
        var howY by remember { mutableIntStateOf(0) }
        var faqY by remember { mutableIntStateOf(0) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(LC.paper)
        ) {
            // twoCol áp dụng cho cả tablet dọc (>= 680dp) và tablet ngang (>= 960dp)
            val twoCol = maxWidth >= 680.dp
            val largeWide = maxWidth >= 960.dp
            val horizontalPad = when {
                maxWidth >= 1180.dp -> 56.dp
                largeWide -> 36.dp
                twoCol -> 24.dp
                else -> 18.dp
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Thanh điều hướng trên cùng (Header)
                LandingHeader(
                    twoCol = twoCol,
                    largeWide = largeWide,
                    horizontalPad = horizontalPad,
                    onScrollTo = { targetY ->
                        scope.launch { scrollState.animateScrollTo(targetY) }
                    },
                    featuresY = { featuresY },
                    satelliteY = { satelliteY },
                    howY = { howY },
                    faqY = { faqY },
                    onExploreDemo = { onEnterOverview("overview") },
                )

                // Nội dung cuộn chính
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    // 1. Phần Hero chính ("Hiểu từng cây. Chăm cả khu vườn.")
                    LandingHeroSection(
                        twoCol = twoCol,
                        largeWide = largeWide,
                        horizontalPad = horizontalPad,
                        onExploreGarden = { onEnterOverview("overview") },
                        onSeeFeatures = {
                            scope.launch { scrollState.animateScrollTo(featuresY) }
                        },
                        onQuickAction = { route -> onEnterOverview(route) },
                    )

                    // 2. Dải đối tượng sử dụng ("THIẾT KẾ CHO NHỮNG NGƯỜI GẮN BÓ VỚI VƯỜN")
                    LandingAudienceStrip(
                        twoCol = twoCol,
                        largeWide = largeWide,
                        horizontalPad = horizontalPad,
                    )

                    // 3. Phần Tính năng Bento ("Mọi thông tin của vườn. Trong một nơi.")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { featuresY = it.positionInParent().y.toInt() }
                    ) {
                        LandingFeaturesSection(
                            twoCol = twoCol,
                            horizontalPad = horizontalPad,
                            onOpenRoute = { route -> onEnterOverview(route) },
                        )
                    }

                    // 4. Phần Dữ liệu vệ tinh ("Hiểu biến động. Kiểm tra đúng nơi.")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { satelliteY = it.positionInParent().y.toInt() }
                    ) {
                        LandingSatelliteSection(
                            twoCol = twoCol,
                            horizontalPad = horizontalPad,
                            onScrollToHow = {
                                scope.launch { scrollState.animateScrollTo(howY) }
                            },
                            onOpenSatellite = { onEnterOverview("satellite") },
                        )
                    }

                    // 5. Phần Cách hoạt động ("Ba bước nhỏ. Một khu vườn rõ ràng hơn.")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { howY = it.positionInParent().y.toInt() }
                    ) {
                        LandingHowSection(
                            twoCol = twoCol,
                            horizontalPad = horizontalPad,
                        )
                    }

                    // 6. Phần Câu hỏi thường gặp ("Trước khi bắt đầu.")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { faqY = it.positionInParent().y.toInt() }
                    ) {
                        LandingFaqSection(
                            twoCol = twoCol,
                            horizontalPad = horizontalPad,
                        )
                    }

                    // 7. Khối kêu gọi cuối trang + Chân trang
                    LandingFinalCtaSection(
                        twoCol = twoCol,
                        horizontalPad = horizontalPad,
                        onExploreGarden = { onEnterOverview("overview") },
                    )
                }
            }
        }
    }
}

@Composable
private fun LandingHeader(
    twoCol: Boolean,
    largeWide: Boolean,
    horizontalPad: Dp,
    onScrollTo: (Int) -> Unit,
    featuresY: () -> Int,
    satelliteY: () -> Int,
    howY: () -> Int,
    faqY: () -> Int,
    onExploreDemo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LC.headerBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (largeWide) 82.dp else 70.dp)
                .padding(horizontal = horizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Logo VWing.
            LandingBrandMark(compact = !largeWide, onClick = onExploreDemo)

            // Liên kết điều hướng giữa: Tính năng | Dữ liệu vệ tinh | Cách hoạt động | Câu hỏi thường gặp
            if (twoCol) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (largeWide) 26.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderNavLink("Tính năng", largeWide) { onScrollTo(featuresY()) }
                    HeaderNavLink("Dữ liệu vệ tinh", largeWide) { onScrollTo(satelliteY()) }
                    HeaderNavLink("Cách hoạt động", largeWide) { onScrollTo(howY()) }
                    HeaderNavLink("Câu hỏi thường gặp", largeWide) { onScrollTo(faqY()) }
                }
            }

            // Nút "Vào vườn của mình ->"
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LC.ink)
                    .bounceClick(onClick = onExploreDemo)
                    .padding(
                        horizontal = if (largeWide) 18.dp else 14.dp,
                        vertical = if (largeWide) 13.dp else 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Vào vườn của mình",
                    color = Color.White,
                    fontSize = if (largeWide) 13.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (largeWide) 16.dp else 14.dp),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LC.line)
    }
}

@Composable
private fun LandingBrandMark(compact: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.bounceClick(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.vwing_logo),
            contentDescription = "VWing",
            modifier = Modifier.size(if (compact) 36.dp else 40.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = LC.ink, fontWeight = FontWeight.ExtraBold)) {
                    append("VWing")
                }
                withStyle(SpanStyle(color = Color(0xFF77A052), fontWeight = FontWeight.ExtraBold)) {
                    append(".")
                }
            },
            fontSize = if (compact) 23.sp else 26.sp,
            letterSpacing = (-1).sp,
        )
    }
}

@Composable
private fun HeaderNavLink(text: String, largeWide: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = LC.ink,
        fontSize = if (largeWide) 14.sp else 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun LeafVectorIcon(
    modifier: Modifier = Modifier,
    color: Color = LC.green,
    strokeWidth: Float = 2f,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val leafPath = Path().apply {
            moveTo(19f * sx, 4f * sy)
            cubicTo(9f * sx, 3f * sy, 4f * sx, 7f * sy, 5f * sx, 14f * sy)
            cubicTo(5.4f * sx, 18f * sy, 10f * sx, 20f * sy, 13f * sx, 18f * sy)
            cubicTo(18f * sx, 15f * sy, 19f * sx, 10f * sy, 19f * sx, 4f * sy)
            close()
        }
        drawPath(
            path = leafPath,
            color = color,
            style = Stroke(width = strokeWidth * sx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawLine(
            color = color,
            start = Offset(4f * sx, 21f * sy),
            end = Offset(14f * sx, 10f * sy),
            strokeWidth = strokeWidth * sx,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LandingHeroSection(
    twoCol: Boolean,
    largeWide: Boolean,
    horizontalPad: Dp,
    onExploreGarden: () -> Unit,
    onSeeFeatures: () -> Unit,
    onQuickAction: (String) -> Unit,
) {
    if (twoCol) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = if (largeWide) 42.dp else 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (largeWide) 38.dp else 20.dp),
        ) {
            Box(modifier = Modifier.weight(0.95f)) {
                HeroCopyColumn(
                    largeWide = largeWide,
                    onExploreGarden = onExploreGarden,
                    onSeeFeatures = onSeeFeatures,
                )
            }
            Box(modifier = Modifier.weight(1.05f)) {
                HeroVisualIllustration(
                    largeWide = largeWide,
                    onExploreGarden = onExploreGarden,
                    onQuickAction = onQuickAction,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            HeroCopyColumn(
                largeWide = false,
                onExploreGarden = onExploreGarden,
                onSeeFeatures = onSeeFeatures,
            )
            HeroVisualIllustration(
                largeWide = false,
                onExploreGarden = onExploreGarden,
                onQuickAction = onQuickAction,
            )
        }
    }
}

@Composable
private fun HeroCopyColumn(
    largeWide: Boolean,
    onExploreGarden: () -> Unit,
    onSeeFeatures: () -> Unit,
) {
    val headingSize = if (largeWide) 60.sp else 42.sp
    val headingLineHeight = if (largeWide) 66.sp else 47.sp

    Column(
        verticalArrangement = Arrangement.spacedBy(if (largeWide) 22.dp else 16.dp),
    ) {
        // Eyebrow pill: "● NGƯỜI BẠN ĐỒNG HÀNH CỦA NHÀ VƯỜN"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(LC.pillBg)
                .border(1.dp, LC.pillBorder, RoundedCornerShape(5.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LC.liveDot)
            )
            Text(
                text = "NGƯỜI BẠN ĐỒNG HÀNH CỦA NHÀ VƯỜN",
                color = LC.ink,
                fontSize = if (largeWide) 10.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }

        // Tiêu đề lớn:
        // Hiểu từng cây.
        // Chăm cả
        // khu vườn. [leaf]
        Column {
            Text(
                text = "Hiểu từng cây.\nChăm cả",
                color = LC.ink,
                fontSize = headingSize,
                lineHeight = headingLineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2.0).sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "khu vườn.",
                    color = LC.olive,
                    fontSize = headingSize,
                    lineHeight = headingLineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2.0).sp,
                )
                LeafVectorIcon(
                    modifier = Modifier
                        .size(if (largeWide) 40.dp else 30.dp)
                        .rotate(15f),
                    color = Color(0xFF8CA361),
                    strokeWidth = 1.8f,
                )
            }
        }

        // Đoạn mô tả dẫn nhập
        Text(
            text = "Từ bản đồ vườn đến lịch sử chăm sóc —\nVWing giúp bạn kết nối những điều đang diễn ra\ntrong vườn, ngay trên chiếc điện thoại.",
            color = LC.muted,
            fontSize = if (largeWide) 15.5.sp else 13.5.sp,
            lineHeight = if (largeWide) 28.sp else 23.sp,
            fontWeight = FontWeight.Normal,
        )

        // Hàng nút hành động: "Vào vườn của mình ->" & "Xem các tính năng ↘"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (largeWide) 22.dp else 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LC.green)
                    .bounceClick(onClick = onExploreGarden)
                    .padding(
                        horizontal = if (largeWide) 22.dp else 16.dp,
                        vertical = if (largeWide) 16.dp else 13.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Vào vườn của mình",
                    color = Color.White,
                    fontSize = if (largeWide) 15.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (largeWide) 18.dp else 15.dp),
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeFeatures)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Xem các tính năng",
                    color = LC.ink,
                    fontSize = if (largeWide) 14.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Filled.SouthEast,
                    contentDescription = null,
                    tint = LC.ink,
                    modifier = Modifier.size(if (largeWide) 18.dp else 15.dp),
                )
            }
        }

        // Ghi chú nhỏ dưới nút
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text("✦", color = LC.green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Đang phát triển trên Android   ·   Giao diện tiếng Việt",
                color = LC.muted,
                fontSize = if (largeWide) 11.5.sp else 10.5.sp,
            )
        }
    }
}

@Composable
private fun HeroVisualIllustration(
    largeWide: Boolean,
    onExploreGarden: () -> Unit,
    onQuickAction: (String) -> Unit,
) {
    val visualHeight = if (largeWide) 540.dp else 440.dp
    val visualShape = RoundedCornerShape(
        topStart = if (largeWide) 105.dp else 72.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 18.dp,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(visualHeight)
    ) {
        // Nền bản đồ vườn minh họa (Canvas vẽ hàng cây + đường nội bộ + hồ nước + ranh vườn nét đứt)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(visualShape)
                .background(LC.visualBg)
        ) {
            OrchardMapCanvas(modifier = Modifier.fillMaxSize())

            // Nhãn góc trên phải: "● MỘT KHU VƯỜN. MỘT KHÔNG GIAN QUẢN LÝ."
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(LC.liveDot)
                )
                Text(
                    text = "MỘT KHU VƯỜN. MỘT KHÔNG GIAN QUẢN LÝ.",
                    color = Color(0xFF405E3C),
                    fontSize = if (largeWide) 8.sp else 7.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp,
                )
            }

            // Chú thích góc dưới phải
            Text(
                text = "Minh họa trải nghiệm sản phẩm",
                color = Color(0xFF506344),
                fontSize = if (largeWide) 8.5.sp else 7.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp, end = 14.dp),
            )
        }

        // Thẻ nổi góc trên trái: "Khu A · Vườn mẫu / Bản đồ minh họa"
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = if (largeWide) 92.dp else 64.dp, start = if (largeWide) 16.dp else 8.dp)
                .shadow(14.dp, RoundedCornerShape(10.dp), ambientColor = LC.ink.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFDEE6D5), RoundedCornerShape(10.dp))
                .bounceClick { onQuickAction("map") }
                .padding(horizontal = if (largeWide) 12.dp else 9.dp, vertical = if (largeWide) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (largeWide) 30.dp else 25.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFEEF4E8)),
                contentAlignment = Alignment.Center,
            ) {
                LeafVectorIcon(
                    modifier = Modifier.size(if (largeWide) 17.dp else 14.dp),
                    color = LC.green,
                    strokeWidth = 1.9f,
                )
            }
            Column {
                Text(
                    text = "Khu A · Vườn mẫu",
                    color = LC.ink,
                    fontSize = if (largeWide) 11.5.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Bản đồ minh họa",
                    color = LC.muted,
                    fontSize = if (largeWide) 9.5.sp else 8.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF73915C))
            )
        }

        // Điện thoại mô phỏng xoay nghiêng 5.5 độ ở bên phải ("Hồ sơ cây · Sầu riêng A-04")
        PhoneMockupCard(
            largeWide = largeWide,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (largeWide) 24.dp else 12.dp, top = 8.dp)
                .rotate(5.5f),
            onOpenTreeOrOverview = onExploreGarden,
            onLogCare = { onQuickAction("log") },
        )

        // Thẻ nổi góc dưới trái: "Quét QR. Mở đúng hồ sơ."
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = if (largeWide) 26.dp else 16.dp, start = 2.dp)
                .rotate(-3f)
                .shadow(16.dp, RoundedCornerShape(11.dp), ambientColor = LC.ink.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE3E7DC), RoundedCornerShape(11.dp))
                .bounceClick { onExploreGarden() }
                .padding(horizontal = if (largeWide) 14.dp else 10.dp, vertical = if (largeWide) 13.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (largeWide) 38.dp else 30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFEAF3DE)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = LC.green,
                    modifier = Modifier.size(if (largeWide) 21.dp else 17.dp),
                )
            }
            Column {
                Text(
                    text = "Quét QR. Mở đúng hồ sơ.",
                    color = LC.ink,
                    fontSize = if (largeWide) 11.5.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Lịch sử của cây, luôn trong tầm tay.",
                    color = LC.muted,
                    fontSize = if (largeWide) 9.5.sp else 8.sp,
                )
            }
        }
    }
}

@Composable
private fun OrchardMapCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 640f
        val sy = h / 660f

        // Vùng đất trồng cây
        val landClip = Path().apply {
            moveTo(-40f * sx, 85f * sy)
            lineTo(560f * sx, 0f * sy)
            lineTo(710f * sx, 450f * sy)
            lineTo(230f * sx, 720f * sy)
            lineTo(-80f * sx, 530f * sy)
            close()
        }

        clipPath(landClip) {
            drawRect(color = Color(0xFFDFE9BD), size = size)

            // Lưới hàng cây xoay nghiêng -23 độ
            rotate(degrees = -23f, pivot = Offset(w * 0.45f, h * 0.45f)) {
                val cellW = 65f * sx
                val cellH = 66f * sy
                val startX = -w * 0.35f
                val endX = w * 1.35f
                val startY = -h * 0.35f
                val endY = h * 1.35f

                var y = startY
                while (y < endY) {
                    drawLine(
                        color = Color(0xFFECF0D4),
                        start = Offset(startX, y + cellH * 0.95f),
                        end = Offset(endX, y + cellH * 0.95f),
                        strokeWidth = 14f * sy,
                    )
                    var x = startX
                    while (x < endX) {
                        val cx = x + 29f * sx
                        val cy = y + 26f * sy
                        drawOval(
                            color = Color(0xFF92AD6B).copy(alpha = 0.32f),
                            topLeft = Offset(x + 12f * sx, y + 16f * sy),
                            size = Size(38f * sx, 34f * sy),
                        )
                        drawCircle(
                            color = Color(0xFF4D8555),
                            radius = 18f * minOf(sx, sy),
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = Color(0xFF6A9C61),
                            radius = 12f * minOf(sx, sy),
                            center = Offset(x + 25f * sx, y + 22f * sy),
                        )
                        drawCircle(
                            color = Color(0xFF7FAE68),
                            radius = 9f * minOf(sx, sy),
                            center = Offset(x + 33f * sx, y + 25f * sy),
                        )
                        x += cellW
                    }
                    y += cellH
                }
            }

            // Đường nội bộ uốn lượn
            val roadOuter = Path().apply {
                moveTo(-40f * sx, 520f * sy)
                cubicTo(110f * sx, 340f * sy, 260f * sx, 425f * sy, 370f * sx, 220f * sy)
                cubicTo(480f * sx, 15f * sy, 540f * sx, 80f * sy, 700f * sx, 0f * sy)
            }
            drawPath(
                path = roadOuter,
                color = Color(0xFFF9F8E7),
                style = Stroke(width = 30f * minOf(sx, sy), cap = StrokeCap.Round),
            )
            val roadInner = Path().apply {
                moveTo(-50f * sx, 523f * sy)
                cubicTo(110f * sx, 343f * sy, 260f * sx, 428f * sy, 370f * sx, 223f * sy)
                cubicTo(480f * sx, 18f * sy, 540f * sx, 83f * sy, 700f * sx, 3f * sy)
            }
            drawPath(
                path = roadInner,
                color = Color(0xFFD5C9A3),
                style = Stroke(width = 14f * minOf(sx, sy), cap = StrokeCap.Round),
            )
        }

        // Hồ nước góc dưới phải
        drawOval(
            color = Color(0xFFB0D1C3),
            topLeft = Offset(430f * sx, 435f * sy),
            size = Size(165f * sx, 130f * sy),
        )
        drawOval(
            color = Color(0xFF94BFAE),
            topLeft = Offset(455f * sx, 452f * sy),
            size = Size(120f * sx, 95f * sy),
        )

        // Đường ranh giới khu vườn nét đứt + 5 đỉnh tròn trắng viền xanh
        val vertices = listOf(
            Offset(75f * sx, 140f * sy),
            Offset(397f * sx, 55f * sy),
            Offset(562f * sx, 381f * sy),
            Offset(259f * sx, 547f * sy),
            Offset(55f * sx, 303f * sy),
        )
        val boundaryPath = Path().apply {
            moveTo(vertices.first().x, vertices.first().y)
            for (i in 1 until vertices.size) {
                lineTo(vertices[i].x, vertices[i].y)
            }
            close()
        }
        drawPath(
            path = boundaryPath,
            color = Color(0xFF386D3B),
            style = Stroke(
                width = 3f * minOf(sx, sy),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * sx, 10f * sx), 0f),
            ),
        )
        vertices.forEach { pt ->
            drawCircle(
                color = Color.White,
                radius = 6.5f * minOf(sx, sy),
                center = pt,
            )
            drawCircle(
                color = Color(0xFF386D3B),
                radius = 6.5f * minOf(sx, sy),
                center = pt,
                style = Stroke(width = 3f * minOf(sx, sy)),
            )
        }
    }
}

@Composable
private fun PhoneMockupCard(
    largeWide: Boolean,
    modifier: Modifier = Modifier,
    onOpenTreeOrOverview: () -> Unit,
    onLogCare: () -> Unit,
) {
    val phoneWidth = if (largeWide) 236.dp else 192.dp
    val phoneShape = RoundedCornerShape(if (largeWide) 32.dp else 26.dp)

    Column(
        modifier = modifier
            .width(phoneWidth)
            .shadow(22.dp, phoneShape, ambientColor = LC.phoneBezel.copy(alpha = 0.25f), spotColor = LC.phoneBezel.copy(alpha = 0.30f))
            .clip(phoneShape)
            .background(Color.White)
            .border(if (largeWide) 6.dp else 5.dp, LC.phoneBezel, phoneShape)
            .clickable(onClick = onOpenTreeOrOverview)
    ) {
        // Thanh trạng thái điện thoại + tai thỏ (island)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, start = 14.dp, end = 14.dp, bottom = 3.dp)
        ) {
            Text(
                text = "9:41",
                color = LC.phoneBezel,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(if (largeWide) 62.dp else 52.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(LC.phoneBezel)
            )
            Text(
                text = "▰ ▮▮ ●",
                color = LC.phoneBezel,
                fontSize = 7.5.sp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Đầu trang "‹  Hồ sơ cây  [QR]"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("‹", color = LC.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Hồ sơ cây", color = LC.ink, fontSize = if (largeWide) 10.5.sp else 9.5.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = LC.ink,
                modifier = Modifier.size(13.dp),
            )
        }

        // Khung minh họa cây sầu riêng + nhãn "HỒ SƠ MINH HỌA"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (largeWide) 114.dp else 90.dp)
                .background(Color(0xFFEDF3E6)),
        ) {
            DurianTreeCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "HỒ SƠ MINH HỌA",
                    color = Color(0xFF537441),
                    fontSize = 5.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }

        // Nội dung hồ sơ cây A-04
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Sầu riêng A-04",
                    color = LC.ink,
                    fontSize = if (largeWide) 14.sp else 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Icon(
                    imageVector = Icons.Filled.NorthEast,
                    contentDescription = null,
                    tint = Color(0xFF648450),
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = "Khu A · Mã cây cố định",
                color = LC.muted,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 1.dp, bottom = 7.dp),
            )

            // Tabs: Tổng quan | Chăm sóc | Hình ảnh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Tổng quan",
                        color = LC.green,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(2.dp)
                            .background(Color(0xFF5B8245))
                    )
                }
                Text("Chăm sóc", color = Color(0xFF76826D), fontSize = 7.5.sp)
                Text("Hình ảnh", color = Color(0xFF76826D), fontSize = 7.5.sp)
            }
            HorizontalDivider(thickness = 1.dp, color = LC.line)

            Spacer(Modifier.height(7.dp))

            // Trạng thái "✓ Đã kiểm tra thực địa"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF0F5E9))
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD9E7C8)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF5B8046),
                        modifier = Modifier.size(10.dp),
                    )
                }
                Column {
                    Text(
                        text = "Đã kiểm tra thực địa",
                        color = LC.ink,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Ghi nhận từ nhật ký của bạn",
                        color = LC.muted,
                        fontSize = 7.sp,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Dòng nhật ký 1: Tưới nước
            PhoneLogRow(
                icon = Icons.Filled.NorthEast,
                iconBg = Color.Transparent,
                iconTint = Color(0xFF67824D),
                title = "Tưới nước",
                subtitle = "12/10 · Ghi nhận thủ công",
            )

            Spacer(Modifier.height(5.dp))

            // Dòng nhật ký 2: Kiểm tra lá
            PhoneLogRow(
                icon = Icons.Filled.Schedule,
                iconBg = Color(0xFFFAF2E1),
                iconTint = Color(0xFF9C824B),
                title = "Kiểm tra lá",
                subtitle = "Công việc cần theo dõi",
            )

            Spacer(Modifier.height(8.dp))

            // Nút "+ Ghi hoạt động chăm sóc"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF365D3C))
                    .clickable(onClick = onLogCare)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋ Ghi hoạt động chăm sóc",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PhoneLogRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(iconBg)
                .border(1.dp, Color(0xFFDCE5D2), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(11.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = LC.ink,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = LC.muted,
                fontSize = 7.sp,
            )
        }
        Text("→", color = LC.muted, fontSize = 9.sp)
    }
}

@Composable
private fun DurianTreeCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 210f
        val sy = size.height / 150f
        val s = minOf(sx, sy)

        drawOval(
            color = Color(0xFFD6E3C6),
            topLeft = Offset(57f * sx, 126f * sy),
            size = Size(98f * sx, 18f * sy),
        )

        val strokeW = 8f * s
        drawLine(
            color = Color(0xFF8C7650),
            start = Offset(105f * sx, 134f * sy),
            end = Offset(105f * sx, 55f * sy),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF8C7650),
            start = Offset(105f * sx, 105f * sy),
            end = Offset(75f * sx, 80f * sy),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF8C7650),
            start = Offset(105f * sx, 88f * sy),
            end = Offset(135f * sx, 59f * sy),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round,
        )

        drawCircle(color = Color(0xFF6F9859), radius = 29f * s, center = Offset(72f * sx, 66f * sy))
        drawCircle(color = Color(0xFF6F9859), radius = 34f * s, center = Offset(130f * sx, 64f * sy))
        drawCircle(color = Color(0xFF6F9859), radius = 34f * s, center = Offset(102f * sx, 38f * sy))

        drawCircle(color = Color(0xFF8BB16D), radius = 23f * s, center = Offset(78f * sx, 48f * sy))
        drawCircle(color = Color(0xFF8BB16D), radius = 25f * s, center = Offset(107f * sx, 29f * sy))
        drawCircle(color = Color(0xFF8BB16D), radius = 24f * s, center = Offset(133f * sx, 51f * sy))

        drawOval(
            color = Color(0xFFB0C977),
            topLeft = Offset(83f * sx, 67f * sy),
            size = Size(16f * sx, 22f * sy),
        )
        drawOval(
            color = Color(0xFFB0C977),
            topLeft = Offset(116f * sx, 67f * sy),
            size = Size(16f * sx, 22f * sy),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandingAudienceStrip(
    twoCol: Boolean,
    largeWide: Boolean,
    horizontalPad: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad)
    ) {
        HorizontalDivider(thickness = 1.dp, color = LC.line)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (largeWide) 22.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "THIẾT KẾ CHO NHỮNG NGƯỜI GẮN BÓ VỚI VƯỜN",
                color = Color(0xFF77826F),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (largeWide) 36.dp else 22.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val roles = listOf("Chủ vườn", "Nhóm chăm sóc", "Tổ hợp tác", "Nghiên cứu nông nghiệp")
                roles.forEachIndexed { idx, role ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (largeWide) 36.dp else 22.dp),
                    ) {
                        Text(
                            text = role,
                            color = Color(0xFF68745E),
                            fontSize = if (twoCol) 15.5.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (idx < roles.lastIndex) {
                            Text(
                                text = "✳",
                                color = Color(0xFFA2B18D),
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LC.line)
    }
}

@Composable
private fun LandingFeaturesSection(
    twoCol: Boolean,
    horizontalPad: Dp,
    onOpenRoute: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = if (twoCol) 56.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (twoCol) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "VƯỜN CỦA BẠN, RÕ RÀNG HƠN",
                        color = Color(0xFF637C50),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "Mọi thông tin của vườn.\nTrong một nơi.",
                        color = LC.ink,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.2).sp,
                    )
                }
                Text(
                    text = "Giảm thời gian lục tìm ghi chép.\nDành thêm thời gian cho cây trồng.",
                    color = LC.muted,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "VƯỜN CỦA BẠN, RÕ RÀNG HƠN",
                    color = Color(0xFF637C50),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "Mọi thông tin của vườn.\nTrong một nơi.",
                    color = LC.ink,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Giảm thời gian lục tìm ghi chép. Dành thêm thời gian cho cây trồng.",
                    color = LC.muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        if (twoCol) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FeatureBigCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Map,
                    badge = "01 / KHÔNG GIAN",
                    title = "Nhìn cả vườn.\nTìm đúng cây.",
                    desc = "Quản lý cây theo khu vực, xem vị trí trên bản đồ và mở hồ sơ ngay từ nơi cây đang đứng.",
                    bg = LC.softGreenBg,
                    onClick = { onOpenRoute("map") },
                )
                FeatureBigCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CalendarMonth,
                    badge = "02 / NHẬT KÝ",
                    title = "Mỗi lần chăm sóc,\nđều được ghi nhớ.",
                    desc = "Lưu hoạt động, hình ảnh và ghi chú theo cây. Tra lại những gì đã làm, khi cần.",
                    bg = Color.White,
                    onClick = { onOpenRoute("records") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FeatureCompactCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.QrCodeScanner,
                    title = "Một mã QR.\nMột hồ sơ cây.",
                    desc = "Tạo nhãn QR, quét và truy lại lịch sử của đúng cây trong vườn.",
                    footer = "HỒ SƠ & NHÃN QR",
                    onClick = { onOpenRoute("overview") },
                )
                FeatureCompactCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AutoAwesome,
                    title = "Hỏi về vườn.\nTìm lại dữ liệu.",
                    desc = "Trợ lý VWing giúp tra cứu hồ sơ, nhật ký và nguồn dữ liệu đã ghi nhận.",
                    footer = "TRỢ LÝ VWING",
                    onClick = { onOpenRoute("ai") },
                )
                FeatureCompactCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Insights,
                    title = "Theo dõi vụ mùa.\nRõ từng con số.",
                    desc = "Ghi số trái, ước tính sản lượng và đối chiếu với thực thu, lần bán.",
                    footer = "SẢN LƯỢNG & THU HOẠCH",
                    onClick = { onOpenRoute("yield") },
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FeatureBigCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Map,
                    badge = "01 / KHÔNG GIAN",
                    title = "Nhìn cả vườn. Tìm đúng cây.",
                    desc = "Quản lý cây theo khu vực, xem vị trí trên bản đồ và mở hồ sơ ngay từ nơi cây đang đứng.",
                    bg = LC.softGreenBg,
                    onClick = { onOpenRoute("map") },
                )
                FeatureBigCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.CalendarMonth,
                    badge = "02 / NHẬT KÝ",
                    title = "Mỗi lần chăm sóc, đều được ghi nhớ.",
                    desc = "Lưu hoạt động, hình ảnh và ghi chú theo cây. Tra lại những gì đã làm, khi cần.",
                    bg = Color.White,
                    onClick = { onOpenRoute("records") },
                )
                FeatureCompactCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.QrCodeScanner,
                    title = "Một mã QR. Một hồ sơ cây.",
                    desc = "Tạo nhãn QR, quét và truy lại lịch sử của đúng cây trong vườn.",
                    footer = "HỒ SƠ & NHÃN QR",
                    onClick = { onOpenRoute("overview") },
                )
                FeatureCompactCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.AutoAwesome,
                    title = "Hỏi về vườn. Tìm lại dữ liệu.",
                    desc = "Trợ lý VWing giúp tra cứu hồ sơ, nhật ký và nguồn dữ liệu đã ghi nhận.",
                    footer = "TRỢ LÝ VWING",
                    onClick = { onOpenRoute("ai") },
                )
                FeatureCompactCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Insights,
                    title = "Theo dõi vụ mùa. Rõ từng con số.",
                    desc = "Ghi số trái, ước tính sản lượng và đối chiếu với thực thu, lần bán.",
                    footer = "SẢN LƯỢNG & THU HOẠCH",
                    onClick = { onOpenRoute("yield") },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF5F8248),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Nhật ký và hồ sơ lưu trên thiết bị. Bạn chủ động sao lưu và xuất dữ liệu.",
                color = LC.muted,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            if (twoCol) {
                LandingTag("Làm việc tại vườn")
            }
        }
    }
}

@Composable
private fun FeatureBigCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    badge: String,
    title: String,
    desc: String,
    bg: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, LC.line, RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE1E9D9), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color(0xFF537342), modifier = Modifier.size(19.dp))
            }
            Text(
                text = badge,
                color = Color(0xFF718260),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
            )
        }
        Text(
            text = title,
            color = LC.ink,
            fontSize = 23.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = desc,
            color = LC.muted,
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun FeatureCompactCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    desc: String,
    footer: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, LC.line, RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEEF4E7))
                .border(1.dp, Color(0xFFE1E9D9), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color(0xFF537342), modifier = Modifier.size(19.dp))
        }
        Text(
            text = title,
            color = LC.ink,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = desc,
            color = LC.muted,
            fontSize = 13.sp,
            lineHeight = 21.sp,
        )
        HorizontalDivider(thickness = 1.dp, color = LC.line, modifier = Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = footer,
                color = Color(0xFF637550),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
            Icon(
                imageVector = Icons.Filled.NorthEast,
                contentDescription = null,
                tint = Color(0xFF637550),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun LandingTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(LC.pillBg)
            .border(1.dp, Color(0xFFDBE5CE), RoundedCornerShape(5.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF52703D),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LandingSatelliteSection(
    twoCol: Boolean,
    horizontalPad: Dp,
    onScrollToHow: () -> Unit,
    onOpenSatellite: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tab = SAT_PREVIEW_TABS[selectedTab.coerceIn(SAT_PREVIEW_TABS.indices)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE7ECDF))
        if (twoCol) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad, vertical = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(0.95f)) {
                    SatelliteCopyBlock(onScrollToHow = onScrollToHow, onOpenSatellite = onOpenSatellite)
                }
                Box(modifier = Modifier.weight(1.05f)) {
                    SatellitePreviewCard(
                        tab = tab,
                        selectedIdx = selectedTab,
                        onSelectTab = { selectedTab = it },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad, vertical = 38.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SatelliteCopyBlock(onScrollToHow = onScrollToHow, onOpenSatellite = onOpenSatellite)
                SatellitePreviewCard(
                    tab = tab,
                    selectedIdx = selectedTab,
                    onSelectTab = { selectedTab = it },
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE7ECDF))
    }
}

@Composable
private fun SatelliteCopyBlock(
    onScrollToHow: () -> Unit,
    onOpenSatellite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SatelliteAlt,
                contentDescription = null,
                tint = Color(0xFF637C50),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "THÊM MỘT GÓC NHÌN TỪ TRÊN CAO",
                color = Color(0xFF637C50),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
        Text(
            text = "Hiểu biến động.\nKiểm tra đúng nơi.",
            color = LC.ink,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1.2).sp,
        )
        Text(
            text = "Đối chiếu dữ liệu vệ tinh với lịch sử chăm sóc để biết khu vực nào cần được quan sát kỹ hơn.",
            color = LC.muted,
            fontSize = 14.5.sp,
            lineHeight = 24.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "NDVI & EVI: theo dõi độ xanh của tán",
                "NDMI: tín hiệu liên quan nước trong tán",
                "Sentinel-1: nghiên cứu biến động nước mặt",
            ).forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF64864E),
                        modifier = Modifier.size(17.dp),
                    )
                    Text(text = line, color = LC.ink, fontSize = 13.5.sp)
                }
            }
        }
        Text(
            text = "Đang thử nghiệm với dữ liệu Google Earth Engine.\nChỉ số hỗ trợ quan sát, cần đối chiếu thực địa.",
            color = LC.muted,
            fontSize = 11.sp,
            lineHeight = 18.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenSatellite)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Mở phân tích vệ tinh",
                    color = LC.green,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.Filled.NorthEast, null, tint = LC.green, modifier = Modifier.size(15.dp))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onScrollToHow)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Từ dữ liệu đến hành động",
                    color = LC.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.Filled.NorthEast, null, tint = LC.ink, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun SatellitePreviewCard(
    tab: SatPreviewTab,
    selectedIdx: Int,
    onSelectTab: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAFBF7))
            .border(1.dp, LC.line, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "VÙNG MẪU · 1,21 HA",
                    color = Color(0xFF637C50),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Góc nhìn vệ tinh",
                    color = LC.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LandingTag("Dữ liệu mẫu thật")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFEAF0E3))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SAT_PREVIEW_TABS.forEachIndexed { idx, item ->
                val selected = idx == selectedIdx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { onSelectTab(idx) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.tabLabel,
                        color = if (selected) Color(0xFF32583A) else Color(0xFF657454),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE5EFDC))
                .border(1.dp, Color(0xFFCDDAC0), RoundedCornerShape(8.dp))
        ) {
            Image(
                painter = painterResource(tab.drawableRes),
                contentDescription = tab.tabLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tab.sourceBadge,
                    color = LC.ink,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Ảnh phân tích từ Earth Engine",
                    color = LC.ink,
                    fontSize = 8.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = tab.metricLabel,
                    color = Color(0xFF6F7D5E),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = tab.metricValue,
                    color = LC.ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = tab.metricCopy,
                color = LC.muted,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.End,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(tab.gradientColors))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(tab.scaleLow, color = Color(0xFF738060), fontSize = 8.5.sp)
            Text(tab.scaleTitle, color = Color(0xFF738060), fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
            Text(tab.scaleHigh, color = Color(0xFF738060), fontSize = 8.5.sp)
        }

        HorizontalDivider(thickness = 1.dp, color = LC.line)
        Text(
            text = tab.dataNote,
            color = Color(0xFF7A846E),
            fontSize = 9.5.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun LandingHowSection(
    twoCol: Boolean,
    horizontalPad: Dp,
) {
    val steps = listOf(
        Triple("01", "Tạo không gian vườn", "Thêm thông tin vườn, phân khu và hồ sơ cây. Nhập ranh giới khi cần phân tích vệ tinh."),
        Triple("02", "Ghi nhận tại vườn", "Quét QR, lưu hoạt động chăm sóc, ảnh quan sát và những thay đổi bạn nhìn thấy."),
        Triple("03", "Xem lại để quyết định", "Đối chiếu nhật ký, sản lượng và dữ liệu vệ tinh trước khi lên kế hoạch kiểm tra tiếp theo."),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = if (twoCol) 56.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "BẮT ĐẦU THẬT TỰ NHIÊN",
                color = Color(0xFF637C50),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "Ba bước nhỏ.\nMột khu vườn rõ ràng hơn.",
                color = LC.ink,
                fontSize = if (twoCol) 36.sp else 30.sp,
                lineHeight = if (twoCol) 42.sp else 36.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (twoCol) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                steps.forEach { (num, title, desc) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCDDAC0))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = num,
                            color = Color(0xFFA3B58C),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = title,
                            color = LC.ink,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = desc,
                            color = LC.muted,
                            fontSize = 13.5.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                steps.forEach { (num, title, desc) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCDDAC0))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                text = num,
                                color = Color(0xFFA3B58C),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = title,
                                    color = LC.ink,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = desc,
                                    color = LC.muted,
                                    fontSize = 13.sp,
                                    lineHeight = 21.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingFaqSection(
    twoCol: Boolean,
    horizontalPad: Dp,
) {
    val faqs = listOf(
        "VWing đã có phiên bản nào?" to "VWing đang được phát triển và thử nghiệm trên Android. Màn hình này giới thiệu tính năng và dẫn trực tiếp vào không gian quản lý vườn của bạn.",
        "Có cần internet khi làm việc trong vườn?" to "Hồ sơ và nhật ký lưu trực tiếp trên thiết bị. Các tác vụ tải bản đồ Google Maps và phân tích dữ liệu Earth Engine cần kết nối internet và máy chủ vệ tinh.",
        "Ảnh vệ tinh có cho biết cây nào đang bệnh?" to "Không thể kết luận bệnh từng cây từ các chỉ số vệ tinh. Sentinel-2 có độ phân giải 10–20 m (và AI Super-Resolution 2,5 m hỗ trợ quan sát vùng); cần kiểm tra thực địa để xác nhận từng cây.",
        "Quét QR sẽ cho tôi biết điều gì?" to "QR mở đúng hồ sơ gắn với mã ID của cây: thông tin giống, phân khu, lịch sử chăm sóc và các vấn đề cần theo dõi.",
    )
    var openIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = if (twoCol) 44.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (twoCol) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "BẠN CÓ THỂ ĐANG TÒ MÒ",
                        color = Color(0xFF637C50),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "Trước khi\nbắt đầu.",
                        color = LC.ink,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Thêm một chút thông tin\nđể chọn đúng cách sử dụng.",
                        color = LC.muted,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp,
                    )
                }
                Column(modifier = Modifier.weight(1.45f)) {
                    FaqAccordionList(
                        faqs = faqs,
                        openIndex = openIndex,
                        onToggle = { idx -> openIndex = if (openIndex == idx) -1 else idx },
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "BẠN CÓ THỂ ĐANG TÒ MÒ",
                    color = Color(0xFF637C50),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "Trước khi bắt đầu.",
                    color = LC.ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FaqAccordionList(
                    faqs = faqs,
                    openIndex = openIndex,
                    onToggle = { idx -> openIndex = if (openIndex == idx) -1 else idx },
                )
            }
        }
    }
}

@Composable
private fun FaqAccordionList(
    faqs: List<Pair<String, String>>,
    openIndex: Int,
    onToggle: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = LC.line)
        faqs.forEachIndexed { idx, (q, a) ->
            val isOpen = idx == openIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(idx) }
                    .padding(vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = q,
                        color = LC.ink,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (isOpen) "×" else "＋",
                        color = Color(0xFF6E8159),
                        fontSize = 21.sp,
                    )
                }
                AnimatedVisibility(visible = isOpen) {
                    Text(
                        text = a,
                        color = LC.muted,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp, end = 20.dp),
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = LC.line)
        }
    }
}

@Composable
private fun LandingFinalCtaSection(
    twoCol: Boolean,
    horizontalPad: Dp,
    onExploreGarden: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(LC.ctaBg)
                .border(1.dp, LC.ctaBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = if (twoCol) 42.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFDBE7C3)),
                contentAlignment = Alignment.Center,
            ) {
                LeafVectorIcon(
                    modifier = Modifier.size(22.dp),
                    color = Color(0xFF5A7842),
                    strokeWidth = 1.9f,
                )
            }
            Text(
                text = "BẮT ĐẦU TỪ VƯỜN CỦA BẠN",
                color = Color(0xFF637C50),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "Chăm vườn bằng kinh nghiệm.\nHiểu vườn thêm bằng dữ liệu.",
                color = LC.ink,
                fontSize = if (twoCol) 32.sp else 24.sp,
                lineHeight = if (twoCol) 39.sp else 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Cùng VWing kết nối từng ghi nhận nhỏ thành bức tranh lớn của khu vườn.",
                color = Color(0xFF69775A),
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LC.ink)
                    .bounceClick(onClick = onExploreGarden)
                    .padding(horizontal = 22.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Vào vườn của mình",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "Vào màn Tổng quan · Sẵn sàng làm việc tại vườn",
                color = Color(0xFF718260),
                fontSize = 9.5.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LandingBrandMark(compact = true, onClick = onExploreGarden)
            if (twoCol) {
                Text(
                    text = "Hiểu từng cây. Chăm cả khu vườn.",
                    color = Color(0xFF77816E),
                    fontSize = 11.5.sp,
                )
            }
            Text(
                text = "© 2026 VWing",
                color = Color(0xFF8E9689),
                fontSize = 10.5.sp,
            )
        }
    }
}
