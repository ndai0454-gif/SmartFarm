package com.example.test_tablet.ui.vwing

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class DevSupportTicket(
    val code: String,
    val content: String,
    val phone: String,
    val gardenName: String,
    val planLabel: String,
    val createdAt: String,
)

/** Lịch sử hội thoại giữ trong bộ nhớ tiến trình, tách riêng 2 kênh: Nông nghiệp (AGRI) & Hỗ trợ Kỹ thuật (TECH_SUPPORT). */
object ChatMemory {
    val answers = mutableStateListOf<AiAnswer>()
    val techAnswers = mutableStateListOf<AiAnswer>()
    val feedback = mutableStateMapOf<String, Boolean>()
    val createdDrafts = mutableStateMapOf<String, Boolean>()
    val submittedTickets = mutableStateListOf<DevSupportTicket>()
    var hasSeenAgriIntro by mutableStateOf(false)
    var hasSeenTechIntro by mutableStateOf(false)
}

/**
 * Quả cầu 3D AI phát sáng (Cloned từ 70a–70f "Joi Orb", chuyển sang bảng màu Sinh thái VWing):
 * - Chế độ Nông nghiệp (`AGRI`): Ngọc lục bảo Rừng nhiệt đới (`#A7F3D0` -> `#10B981` -> `#064E3B`).
 * - Chế độ Hỗ trợ Kỹ thuật & Dev (`TECH_SUPPORT`): Ngọc lam Công nghệ (`#BAE6FD` -> `#0EA5E9` -> `#0C4A6E`).
 */
@Composable
fun VWingAiOrb(
    channel: ChatBotChannel,
    size: Dp = 88.dp,
    isDarkStyle: Boolean = VC.isPro,
    animatePulse: Boolean = true,
) {
    val inf = rememberInfiniteTransition(label = "ai_orb")
    val breath by inf.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_breath",
    )
    val scale = if (animatePulse) breath else 1f

    val highlightColor = if (channel == ChatBotChannel.AGRI) {
        if (isDarkStyle) Color(0xFFD9F99D) else Color(0xFFA7F3D0)
    } else {
        if (isDarkStyle) Color(0xFFE0F2FE) else Color(0xFFBAE6FD)
    }
    val midColor = if (channel == ChatBotChannel.AGRI) {
        if (isDarkStyle) Color(0xFF34D399) else Color(0xFF16A34A)
    } else {
        if (isDarkStyle) Color(0xFF38BDF8) else Color(0xFF0284C7)
    }
    val deepColor = if (channel == ChatBotChannel.AGRI) {
        if (isDarkStyle) Color(0xFF064E3B) else Color(0xFF0F2B1D)
    } else {
        if (isDarkStyle) Color(0xFF0C4A6E) else Color(0xFF0F3B52)
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = (this.size.minDimension / 2f) * scale
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Bóng đổ mềm phía dưới quả cầu (như 70a, 70b, 70c)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        midColor.copy(alpha = if (isDarkStyle) 0.42f else 0.28f),
                        Color.Transparent,
                    ),
                    center = Offset(center.x, center.y + r * 0.78f),
                    radius = r * 0.95f,
                ),
                topLeft = Offset(center.x - r * 0.85f, center.y + r * 0.45f),
                size = androidx.compose.ui.geometry.Size(r * 1.7f, r * 0.65f),
            )

            // Khối cầu 3D với điểm sáng lệch góc trên-trái
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        highlightColor,
                        midColor,
                        deepColor,
                    ),
                    center = Offset(center.x - r * 0.32f, center.y - r * 0.34f),
                    radius = r * 1.38f,
                ),
                radius = r * 0.86f,
                center = center,
            )

            // Viền phản quang mỏng quanh quả cầu
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.Transparent,
                        highlightColor.copy(alpha = 0.35f),
                    ),
                ),
                radius = r * 0.86f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (r * 0.04f).coerceAtLeast(1.5f)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(c: VCtx) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val exportAiMode = activity?.intent?.getStringExtra("export_ai_mode")
    val scope = rememberCoroutineScope()
    val d = c.d
    val geminiSettings = remember { GeminiSettings(context.filesDir, context) }
    var geminiCfg by remember { mutableStateOf(geminiSettings.load()) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var showSavedNotesDialog by remember { mutableStateOf(false) }
    var showProductsCatalog by remember { mutableStateOf(exportAiMode == "catalog") }
    var busyAsking by remember { mutableStateOf(false) }

    // 2 kênh Chatbot AI độc lập: Nông nghiệp (AGRI) và Hỗ trợ Kỹ thuật & Liên hệ Nhà phát triển (TECH_SUPPORT)
    var activeChannel by rememberSaveable {
        mutableStateOf(if (exportAiMode == "70f") ChatBotChannel.TECH_SUPPORT else ChatBotChannel.AGRI)
    }
    // Cho phép xem màn hình Giới thiệu 70a/70b hoặc đi thẳng vào khung chat 70c/70d/70e/70f
    var showIntroCard by rememberSaveable {
        mutableStateOf(exportAiMode == "70a" || exportAiMode == "70b")
    }
    // Cho phép xem thử cả 2 chế độ Sáng (70a/70d) & Tối Aurora (70b/70e) ngay trong màn hình Chat
    var darkPreviewOverride by remember {
        mutableStateOf<Boolean?>(
            when (exportAiMode) {
                "70a", "70d" -> false
                "70b", "70c", "70e", "70f", "catalog" -> true
                else -> null
            }
        )
    }
    val isDarkChat = darkPreviewOverride ?: VC.isPro

    androidx.compose.runtime.LaunchedEffect(exportAiMode) {
        when (exportAiMode) {
            "70c" -> {
                ChatMemory.answers.clear()
            }
            "70d", "70e" -> {
                ChatMemory.answers.clear()
                ChatMemory.answers.add(Assistant.answer("Bón phân gì & lưu lượng bao nhiêu?", d))
            }
            "70f" -> {
                ChatMemory.techAnswers.clear()
                ChatMemory.techAnswers.add(Assistant.answerTechSupport("Gửi góp ý / Liên hệ Đội phát triển VWing", d))
            }
            "catalog" -> {
                ChatMemory.answers.clear()
                ChatMemory.answers.add(Assistant.answer("Giới thiệu tất cả sản phẩm phân bón & thuốc BVTV?", d))
            }
        }
    }

    var input by remember { mutableStateOf("") }
    val voiceSpeaker = rememberVoiceSpeaker(c)
    val todayStr = Dates.fmt(Dates.today())
    val unlimitedAi = c.isFeatureEnabled(VFeature.UNLIMITED_AI)
    val remainingAi = c.uiSettings.remainingAiQuestions(todayStr)

    val currentAnswers = if (activeChannel == ChatBotChannel.AGRI) ChatMemory.answers else ChatMemory.techAnswers

    fun askQuestion(q: String, targetChannel: ChatBotChannel = activeChannel) {
        val clean = q.trim()
        if (clean.isBlank() || busyAsking) return
        if (targetChannel == ChatBotChannel.AGRI && !c.isFeatureEnabled(VFeature.UNLIMITED_AI) && c.uiSettings.remainingAiQuestions(todayStr) <= 0) {
            c.requestFeature(VFeature.UNLIMITED_AI)
            return
        }
        if (targetChannel == ChatBotChannel.AGRI) {
            c.updateUiSettings { it.recordAiQuestion(todayStr) }
        }
        showIntroCard = false
        busyAsking = true
        scope.launch {
            val ans = withContext(Dispatchers.IO) {
                if (targetChannel == ChatBotChannel.AGRI) {
                    GeminiClient.ask(clean, c.store.data, geminiCfg)
                } else {
                    GeminiClient.askTechSupport(clean, c.store.data, geminiCfg)
                }
            }
            if (targetChannel == ChatBotChannel.AGRI) {
                ChatMemory.answers.add(ans)
            } else {
                ChatMemory.techAnswers.add(ans)
            }
            busyAsking = false
            if (geminiCfg.autoSpeakResponse) {
                val readText = ans.summary + ". " + ans.facts.take(2).joinToString(". ") { it.text }
                voiceSpeaker.speak(readText)
            }
        }
    }

    fun handleFeatureCardAction(card: ChatFeatureCard) {
        val r = card.actionRoute
        when {
            r == "action:plan_dialog" -> c.requestFeature(null)
            r == "action:phone_auth" -> c.openFarmerAuth()
            r.startsWith("action:submit_ticket|") -> {
                val parts = r.split("|")
                val code = parts.getOrNull(1) ?: "VW-2026"
                val content = parts.getOrNull(2) ?: card.subtitle
                val phone = FirebaseFarmerRepo.currentFarmer?.displayPhone ?: "Chưa gắn SĐT"
                val planLbl = if (c.uiSettings.planTier == PlanTier.ENTERPRISE) "Pro Account (499K)" else "Basic Account (149K)"
                if (ChatMemory.submittedTickets.none { it.code == code }) {
                    ChatMemory.submittedTickets.add(
                        0,
                        DevSupportTicket(
                            code = code,
                            content = content,
                            phone = phone,
                            gardenName = d.garden.name,
                            planLabel = planLbl,
                            createdAt = Dates.fmt(Dates.now()),
                        )
                    )
                }
                c.toast("✅ Đã gửi Phiếu #$code tới Đội ngũ Phát triển VWing!")
            }
            r.isNotBlank() -> c.nav.push(r)
        }
    }

    val speakToFill = rememberVoiceInput(c) { recognized ->
        input = recognized
    }
    val speakAndSendNow = rememberVoiceInput(c) { recognized ->
        input = ""
        askQuestion(recognized)
    }

    val initial = c.nav.arg
    LaunchedEffect(initial) {
        when {
            initial.equals("support", ignoreCase = true) -> {
                activeChannel = ChatBotChannel.TECH_SUPPORT
                showIntroCard = false
                c.nav.stack[c.nav.stack.lastIndex] = "ai"
            }
            initial.equals("intro", ignoreCase = true) -> {
                showIntroCard = true
                c.nav.stack[c.nav.stack.lastIndex] = "ai"
            }
            initial.equals("agri", ignoreCase = true) -> {
                activeChannel = ChatBotChannel.AGRI
                showIntroCard = false
                c.nav.stack[c.nav.stack.lastIndex] = "ai"
            }
            initial.isNotBlank() -> {
                askQuestion(initial)
                c.nav.stack[c.nav.stack.lastIndex] = "ai"
            }
        }
    }

    // Bảng màu thích ứng theo chuẩn VWing SmartFarm cho cả 70a/70c/70d/70f (Light) & 70b/70e (Dark Aurora)
    val shellBg = if (isDarkChat) {
        Brush.verticalGradient(
            listOf(Color(0xFF071A24), Color(0xFF05131B), Color(0xFF041017))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFEAF4EE), Color(0xFFE3EFE8), Color(0xFFEDF6F0))
        )
    }
    val cardBg = if (isDarkChat) Color(0xFF0D2430) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkChat) {
        if (activeChannel == ChatBotChannel.AGRI) Color(0xFF34D399).copy(alpha = 0.35f)
        else Color(0xFF38BDF8).copy(alpha = 0.38f)
    } else {
        if (activeChannel == ChatBotChannel.AGRI) Color(0xFFB7DEC7)
        else Color(0xFFB6D8EA)
    }
    val primaryAccent = if (activeChannel == ChatBotChannel.AGRI) {
        if (isDarkChat) Color(0xFF4ADE80) else Color(0xFF15803D)
    } else {
        if (isDarkChat) Color(0xFF38BDF8) else Color(0xFF0284C7)
    }
    val primaryBubbleBg = if (activeChannel == ChatBotChannel.AGRI) {
        if (isDarkChat) Color(0xFF166534) else Color(0xFF15803D)
    } else {
        if (isDarkChat) Color(0xFF075985) else Color(0xFF0284C7)
    }
    val primaryCtaBg = if (activeChannel == ChatBotChannel.AGRI) {
        if (isDarkChat) Color(0xFF86EFAC) else Color(0xFF15803D)
    } else {
        if (isDarkChat) Color(0xFF7DD3FC) else Color(0xFF0284C7)
    }
    val primaryCtaText = if (isDarkChat) Color(0xFF052014) else Color.White
    val textPrimary = if (isDarkChat) Color(0xFFF4F8FA) else Color(0xFF0F2419)
    val textSecondary = if (isDarkChat) Color(0xFFBED5DF) else Color(0xFF355444)
    val pillSurface = if (isDarkChat) Color(0xFF123040) else Color(0xFFDDF0E5)

    var openEvent by remember { mutableStateOf<String?>(null) }
    var draftToCreate by remember { mutableStateOf<Pair<Int, TaskDraft>?>(null) }

    if (showGeminiDialog) {
        GeminiConfigDialog(
            initial = geminiCfg,
            onDismiss = { showGeminiDialog = false },
            onSave = { updated ->
                geminiSettings.save(updated)
                geminiCfg = updated
                showGeminiDialog = false
                c.toast("Đã chọn mô hình ${updated.model}!")
            },
        )
    }

    if (showSavedNotesDialog) {
        SavedNotesAndTicketsDialog(
            c = c,
            onDismiss = { showSavedNotesDialog = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Thanh chuyển đổi 2 Chatbot AI chuyên biệt + Công cụ xem nhanh 6 màn hình (70a–70f)
        VCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatBotChannel.entries.forEach { ch ->
                    val selected = activeChannel == ch
                    val tabColor = if (ch == ChatBotChannel.AGRI) VC.lime else VC.cyan
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("tab_ai_${ch.id}")
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) VC.pine else VC.elevated)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) tabColor else VC.border,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .bounceClick {
                                activeChannel = ch
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                ch.shortLabel,
                                color = if (selected) Color.White else VC.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (ch == ChatBotChannel.AGRI) "Phân thuốc · Sâu bệnh · Mùa vụ"
                                else "Thay thế SĐT & Email · Liên hệ Dev",
                                color = if (selected) Color(0xFFA7F3D0) else VC.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Thanh điều khiển chế độ màn hình (70a/70b Giới thiệu · 70c–70f Chat · Sáng/Tối · Danh mục VTNN)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallModeChip(
                    label = if (showIntroCard) "✨ Đang xem: Giới thiệu (70a/70b)" else "✨ Xem màn Giới thiệu (70a/70b)",
                    active = showIntroCard,
                    onClick = { showIntroCard = !showIntroCard },
                )
                SmallModeChip(
                    label = if (isDarkChat) "🌙 Giao diện Tối Aurora (70b/70e)" else "☀️ Giao diện Sáng (70a/70d)",
                    active = isDarkChat,
                    onClick = { darkPreviewOverride = !isDarkChat },
                )
                if (activeChannel == ChatBotChannel.AGRI) {
                    SmallModeChip(
                        label = if (showProductsCatalog) "🛒 Ẩn Danh mục Phân thuốc" else "🛒 Mở Danh mục Phân thuốc Internet",
                        active = showProductsCatalog,
                        onClick = { showProductsCatalog = !showProductsCatalog },
                    )
                }
                SmallModeChip(
                    label = "⚙ Model AI (${geminiCfg.model})",
                    active = false,
                    onClick = { showGeminiDialog = true },
                )
            }
        }

        // KHUNG CLONE 6 MÀN HÌNH 70a – 70f VỚI TONE MÀU VWING SMARTFARM
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(shellBg)
                .border(1.5.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. TOP HEADER BAR (Giống hệt 70a–70f: Nút `<` | Orb nhỏ + Tên Bot + Badge `AI 24/7` | Nút `🔖` | Nút `♡ Hỗ trợ`)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Nút `<` tròn
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg.copy(alpha = 0.9f))
                                .border(1.dp, cardBorder, CircleShape)
                                .bounceClick {
                                    if (!showIntroCard && currentAnswers.isNotEmpty()) {
                                        showIntroCard = true
                                    } else {
                                        c.nav.pop()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Quả cầu 3D nhỏ + Tên Bot + Huy hiệu BETA / 24/7
                        VWingAiOrb(
                            channel = activeChannel,
                            size = 30.dp,
                            isDarkStyle = isDarkChat,
                            animatePulse = false,
                        )
                        Text(
                            activeChannel.title,
                            color = textPrimary,
                            fontFamily = VDisplayFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillSurface)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(
                                activeChannel.badge,
                                color = primaryAccent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Nút Bookmark `🔖` (Ghi chú đã lưu & Phiếu liên hệ Đội Phát Triển)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg.copy(alpha = 0.9f))
                                .border(1.dp, cardBorder, CircleShape)
                                .bounceClick { showSavedNotesDialog = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.BookmarkBorder,
                                contentDescription = "Ghi chú & Phiếu hỗ trợ",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            if (ChatMemory.submittedTickets.isNotEmpty() || currentAnswers.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent)
                                )
                            }
                        }

                        // Nút Pill `♡ Support` góc phải trên (Chuyển nhanh giữa Kỹ sư Nông nghiệp ⇄ Hỗ trợ Kỹ thuật / Liên hệ Dev)
                        Row(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(pillSurface)
                                .border(1.dp, cardBorder, RoundedCornerShape(999.dp))
                                .bounceClick {
                                    activeChannel = if (activeChannel == ChatBotChannel.AGRI) {
                                        ChatBotChannel.TECH_SUPPORT
                                    } else {
                                        ChatBotChannel.AGRI
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                if (activeChannel == ChatBotChannel.AGRI) Icons.Filled.SupportAgent else Icons.Filled.Park,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                if (activeChannel == ChatBotChannel.AGRI) "Hỗ trợ KT" else "Kỹ sư AI",
                                color = textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }

                // Thanh hạn mức gói Free (chỉ áp dụng cho AI Nông nghiệp; AI Hỗ trợ Kỹ thuật miễn phí 100% 24/7)
                if (activeChannel == ChatBotChannel.AGRI && !unlimitedAi) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (remainingAi > 0) VC.pine else VC.amber.copy(alpha = 0.15f))
                            .border(
                                1.dp,
                                if (remainingAi > 0) VC.mint.copy(alpha = 0.55f) else VC.amber.copy(alpha = 0.55f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (remainingAi > 0) "🌱 Gói Basic: còn $remainingAi/${VFeature.FREE_MAX_AI_PER_DAY} câu hỏi Kỹ sư AI hôm nay"
                            else "🔒 Đã hết ${VFeature.FREE_MAX_AI_PER_DAY} câu hỏi miễn phí hôm nay",
                            color = if (remainingAi > 0) Color(0xFFA7F3D0) else VC.amber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { c.requestFeature(VFeature.UNLIMITED_AI) }) {
                            Text("✦ Mở khóa Pro", color = Color(0xFFFDE047), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // NỘI DUNG CHÍNH: CHUYỂN ĐỔI MƯỢT GIỮA (70a/70b) -> (70c) -> (70d/70e/70f)
                AnimatedContent(
                    targetState = showIntroCard,
                    label = "chat_screen_state",
                ) { isIntro ->
                    if (isIntro) {
                        // =========================================================================
                        // MÀN HÌNH 70a (LIGHT) & 70b (DARK): FIRST-TIME WELCOME & 3-ROW INFO CARD
                        // =========================================================================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            VWingAiOrb(
                                channel = activeChannel,
                                size = 104.dp,
                                isDarkStyle = isDarkChat,
                            )

                            Text(
                                if (activeChannel == ChatBotChannel.AGRI) "Xin chào, tôi là Kỹ sư VWing"
                                else "Xin chào, tôi là Hỗ trợ Kỹ thuật AI",
                                color = textPrimary,
                                fontFamily = VDisplayFont,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                if (activeChannel == ChatBotChannel.AGRI) {
                                    "Tôi luôn sẵn sàng bất cứ khi nào vườn của bác cần chẩn đoán sâu bệnh, tính lượng phân bón, pha thuốc BVTV hay lập lịch tưới."
                                } else {
                                    "Thay thế hoàn toàn việc gọi SĐT hay gửi Email chờ đợi — tôi giải đáp mọi thắc mắc kỹ thuật và gửi phiếu trực tiếp tới Đội ngũ Phát triển VWing 24/7."
                                },
                                color = textSecondary,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )

                            // Thẻ 3 dòng tính năng có đường kẻ ngăn cách (Cloned từ 70a & 70b)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
                                    .padding(vertical = 6.dp),
                            ) {
                                val rows = if (activeChannel == ChatBotChannel.AGRI) {
                                    listOf(
                                        Triple(
                                            Icons.Filled.AutoAwesome,
                                            "Chuyên gia Nông học & Định lượng chính xác",
                                            "Tính sẵn số gam NPK/gốc, số bình xịt 20L/phuy 200L, lượng nước tưới FAO-56 và tra cứu giá phân thuốc trên Internet cho ${d.activeTrees.size} cây của vườn.",
                                        ),
                                        Triple(
                                            Icons.Filled.BookmarkBorder,
                                            "Ghi nhớ lịch sử chăm sóc từng gốc cây",
                                            "Tự động lưu nội dung tư vấn, liên kết mã cây (A-03, A-04, B-05) và tạo nháp công việc để bác xem lại hoặc xóa bất cứ lúc nào.",
                                        ),
                                        Triple(
                                            Icons.Filled.FavoriteBorder,
                                            "Kết nối Hỗ trợ Kỹ thuật chỉ với một chạm",
                                            "Nếu cần hỗ trợ về ứng dụng, quét QR hoặc nâng cấp gói tài khoản, bấm nút Hỗ trợ KT ở góc phải trên để chuyển sang Trợ lý Kỹ thuật ngay.",
                                        ),
                                    )
                                } else {
                                    listOf(
                                        Triple(
                                            Icons.Filled.AutoAwesome,
                                            "Hỗ trợ Kỹ thuật 24/7 thay thế SĐT & Email",
                                            "Giải đáp tức thì cách khoanh vườn vệ tinh Google Earth Engine 7 chỉ số, quét mã QR 3 chế độ và đồng bộ SĐT giữa Điện thoại & Tablet.",
                                        ),
                                        Triple(
                                            Icons.Filled.BookmarkBorder,
                                            "Tự động đóng gói Phiếu Hỗ trợ & Báo lỗi",
                                            "Ghi nhớ cấu hình vườn (${d.garden.name}), trạng thái gói Basic/Pro và lịch sử thao tác để bác không phải mô tả lại từ đầu.",
                                        ),
                                        Triple(
                                            Icons.Filled.FavoriteBorder,
                                            "Chuyển tiếp trực tiếp tới Đội Phát Triển VWing",
                                            "Tạo phiếu góp ý tính năng, yêu cầu hỗ trợ doanh nghiệp/HTX hoặc báo lỗi ưu tiên ngay trong khung chat chỉ với 1 chạm.",
                                        ),
                                    )
                                }

                                rows.forEachIndexed { rIdx, (ic, rTitle, rBody) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(pillSurface),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                ic,
                                                contentDescription = null,
                                                tint = primaryAccent,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                rTitle,
                                                color = textPrimary,
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                            Text(
                                                rBody,
                                                color = textSecondary,
                                                fontSize = 13.5.sp,
                                                lineHeight = 19.sp,
                                            )
                                        }
                                    }
                                    if (rIdx < rows.lastIndex) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .height(1.dp)
                                                .background(cardBorder.copy(alpha = 0.45f))
                                        )
                                    }
                                }
                            }

                            // Nút Pill toàn chiều rộng dưới đáy ("Start talking" trong 70a/70b)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(primaryCtaBg)
                                    .bounceClick {
                                        if (activeChannel == ChatBotChannel.AGRI) ChatMemory.hasSeenAgriIntro = true
                                        else ChatMemory.hasSeenTechIntro = true
                                        showIntroCard = false
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (activeChannel == ChatBotChannel.AGRI) "Bắt đầu hỏi Kỹ sư AI"
                                    else "Bắt đầu chat Hỗ trợ Kỹ thuật",
                                    color = primaryCtaText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    } else {
                        // =========================================================================
                        // MÀN HÌNH 70c (EMPTY STATE) & 70d / 70e / 70f (CONVERSATION + FEATURE CARDS)
                        // =========================================================================
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (currentAnswers.isEmpty()) {
                                // -----------------------------------------------------------------
                                // MÀN HÌNH 70c: NOTHING SAID YET ("What's on your mind?" + 2x2 Chips)
                                // -----------------------------------------------------------------
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    VWingAiOrb(
                                        channel = activeChannel,
                                        size = 82.dp,
                                        isDarkStyle = isDarkChat,
                                    )

                                    Text(
                                        if (activeChannel == ChatBotChannel.AGRI) "Hôm nay vườn của bác cần hỗ trợ gì?"
                                        else "Bạn cần hỗ trợ kỹ thuật vấn đề gì?",
                                        color = textPrimary,
                                        fontFamily = VDisplayFont,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                    )

                                    Text(
                                        if (activeChannel == ChatBotChannel.AGRI) "Việc lớn hay nhỏ trong vườn (${d.garden.name}), tôi đều sẵn sàng lắng nghe."
                                        else "Thay thế SĐT & Email truyền thống — Hỏi tính năng, gói cước hoặc gửi phiếu cho Đội Phát Triển.",
                                        color = textSecondary,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    // Lưới 2 cột các nút gợi ý bo tròn (Cloned từ 70c)
                                    val suggestions = if (activeChannel == ChatBotChannel.AGRI) {
                                        SUGGESTED_QUESTIONS
                                    } else {
                                        TECH_SUGGESTED_QUESTIONS
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        suggestions.chunked(2).forEach { pair ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                pair.forEach { qText ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 50.dp)
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(cardBg)
                                                            .border(1.dp, cardBorder, RoundedCornerShape(999.dp))
                                                            .bounceClick { askQuestion(qText) }
                                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            qText,
                                                            color = textPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            textAlign = TextAlign.Center,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // -----------------------------------------------------------------
                                // MÀN HÌNH 70d (LIGHT), 70e (DARK), 70f (FEATURE CARD IN CHAT)
                                // -----------------------------------------------------------------
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // Nhãn "Hôm nay" ở giữa (Giống "Today" trong 70d & 70e)
                                    Text(
                                        "Hôm nay · ${todayStr}",
                                        color = textSecondary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    // Dải gợi ý nhanh thu gọn để người dùng hỏi tiếp bất cứ lúc nào
                                    val quickList = if (activeChannel == ChatBotChannel.AGRI) SUGGESTED_QUESTIONS else TECH_SUGGESTED_QUESTIONS
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        quickList.forEach { qText ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(cardBg)
                                                    .border(1.dp, cardBorder, RoundedCornerShape(999.dp))
                                                    .bounceClick { askQuestion(qText) }
                                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                            ) {
                                                Text(
                                                    qText,
                                                    color = textPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }

                                    // Hiển thị danh sách hội thoại theo chuẩn 70d / 70e / 70f
                                    currentAnswers.reversed().forEachIndexed { idx, a ->
                                        JoiStyleConversationTurn(
                                            c = c,
                                            turnKey = "${activeChannel.id}|$idx|${a.asOf}",
                                            idx = idx,
                                            answer = a,
                                            channel = activeChannel,
                                            isDarkChat = isDarkChat,
                                            cardBg = cardBg,
                                            cardBorder = cardBorder,
                                            pillSurface = pillSurface,
                                            primaryAccent = primaryAccent,
                                            primaryBubbleBg = primaryBubbleBg,
                                            primaryCtaBg = primaryCtaBg,
                                            primaryCtaText = primaryCtaText,
                                            textPrimary = textPrimary,
                                            textSecondary = textSecondary,
                                            onSpeakAloud = {
                                                val readText = a.summary + ". " + a.facts.take(3).joinToString(". ") { it.text }
                                                voiceSpeaker.speak(readText)
                                            },
                                            onDraft = { draftToCreate = idx to it },
                                            onFeatureCardClick = { handleFeatureCardAction(it) },
                                        )
                                    }
                                }
                            }

                            if (busyAsking) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    VWingAiOrb(
                                        channel = activeChannel,
                                        size = 26.dp,
                                        isDarkStyle = isDarkChat,
                                    )
                                    CircularProgressIndicator(Modifier.size(18.dp), color = primaryAccent, strokeWidth = 2.2.dp)
                                    Text(
                                        if (activeChannel == ChatBotChannel.AGRI) "Kỹ sư VWing AI đang phân tích định lượng & tra cứu sản phẩm…"
                                        else "AI Hỗ trợ Kỹ thuật đang xử lý yêu cầu & đóng gói phiếu…",
                                        color = textPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // -----------------------------------------------------------------
                            // THANH CÔNG CỤ GIỌNG NÓI + COMPOSER BAR DƯỚI ĐÁY (Giống 70c, 70d, 70e, 70f)
                            // -----------------------------------------------------------------
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SmallActionPill(
                                    label = "🎤 Nói & Hỏi ngay",
                                    isDarkChat = isDarkChat,
                                    accent = primaryAccent,
                                    onClick = speakAndSendNow,
                                )
                                SmallActionPill(
                                    label = "🎙️ Nói để điền chữ",
                                    isDarkChat = isDarkChat,
                                    accent = textSecondary,
                                    onClick = speakToFill,
                                )
                                if (voiceSpeaker.isSpeaking) {
                                    SmallActionPill(
                                        label = "⏹ Dừng đọc loa",
                                        isDarkChat = isDarkChat,
                                        accent = VC.red,
                                        onClick = { voiceSpeaker.stop() },
                                    )
                                }
                                if (activeChannel == ChatBotChannel.TECH_SUPPORT) {
                                    SmallActionPill(
                                        label = "📩 Gửi Phiếu Liên Hệ Dev ngay",
                                        isDarkChat = isDarkChat,
                                        accent = primaryAccent,
                                        onClick = {
                                            val msg = input.ifBlank { "Yêu cầu Đội ngũ Phát triển VWing hỗ trợ kỹ thuật cho vườn ${d.garden.name}" }
                                            input = ""
                                            askQuestion(msg, ChatBotChannel.TECH_SUPPORT)
                                        },
                                    )
                                }
                            }

                            // Ô nhập tin nhắn bo tròn + Nút gửi tròn `➤` (Cloned từ "Message Joi" trong 70c–70f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    VField(
                                        value = input,
                                        onChange = { input = it },
                                        label = if (activeChannel == ChatBotChannel.AGRI) {
                                            "Nhắn cho Kỹ sư VWing (phân bón, sâu bệnh, tưới nước…)"
                                        } else {
                                            "Nhắn cho Hỗ trợ Kỹ thuật / Đội ngũ Phát triển VWing…"
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(if (input.isNotBlank() && !busyAsking) primaryCtaBg else pillSurface)
                                        .border(1.dp, cardBorder, CircleShape)
                                        .bounceClick(enabled = input.isNotBlank() && !busyAsking) {
                                            val q = input
                                            input = ""
                                            askQuestion(q)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Gửi tin nhắn",
                                        tint = if (input.isNotBlank() && !busyAsking) primaryCtaText else primaryAccent,
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }

                            // Dòng chú thích cuối trang (Cloned từ "Joi is an AI and can make mistakes." trong 70c–70f)
                            Text(
                                if (activeChannel == ChatBotChannel.AGRI) {
                                    "VWing AI có thể có sai sót nhỏ · Vui lòng kiểm tra nhãn thuốc & thời tiết thực tế trước khi phun tưới."
                                } else {
                                    "Kênh AI Hỗ trợ Kỹ thuật 24/7 thay thế hoàn toàn SĐT & Email · Mọi phiếu góp ý được chuyển thẳng tới Đội Phát Triển."
                                },
                                color = textSecondary.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        openEvent?.let { EventDialog(c, it) { openEvent = null } }
        draftToCreate?.let { (idx, draft) ->
            AlertDialog(
                onDismissRequest = { draftToCreate = null },
                containerColor = VC.card,
                title = { Text("Tạo công việc vào sổ vườn?", color = VC.text, fontWeight = FontWeight.ExtraBold) },
                text = {
                    Text("\"${draft.title}\" sẽ được thêm vào danh sách công việc chăm sóc vườn.", color = VC.text2)
                },
                confirmButton = {
                    VButton("Tạo công việc", {
                        val r = c.store.addTask(draft.title, draft.treeIds, null, d.garden.operatorName, Dates.daysAgo(-1), fromAi = true)
                        if (r.ok) {
                            ChatMemory.createdDrafts["$idx|${draft.title}"] = true
                            c.toast("Đã tạo công việc vào sổ vườn.")
                        }
                        draftToCreate = null
                    })
                },
                dismissButton = { TextButton({ draftToCreate = null }) { Text("Hủy", color = VC.text2) } },
            )
        }

        // Khu vực Tìm kiếm & Giới thiệu TẤT CẢ Sản phẩm Nông nghiệp qua Internet (khi ở kênh Nông nghiệp)
        if (activeChannel == ChatBotChannel.AGRI && (showProductsCatalog || currentAnswers.isNotEmpty())) {
            InternetProductShowcase(c = c)
        }
    }
}

/**
 * Một lượt hội thoại theo đúng bố cục 70d (Light), 70e (Dark) và 70f (Feature Card in Chat):
 * 1. Bong bóng câu hỏi của người dùng căn phải (bo góc mềm).
 * 2. Phản hồi của AI căn trái kèm quả cầu 3D nhỏ (`VWingAiOrb` 28.dp) ở góc trên-trái.
 * 3. Thẻ hành động tương tác nhúng trực tiếp trong luồng chat (Cloned từ "Box breathing · Start" ở 70d/70e & "Self-Care and Anxiety log · Open" ở 70f).
 * 4. Hàng đánh giá `👍 Hữu ích` / `👎 Chưa sát` (khi bấm hiện dòng cảm ơn `"Cảm ơn bác, phản hồi giúp VWing AI hỗ trợ tốt hơn."` giống 70e).
 */
@Composable
private fun JoiStyleConversationTurn(
    c: VCtx,
    turnKey: String,
    idx: Int,
    answer: AiAnswer,
    channel: ChatBotChannel,
    isDarkChat: Boolean,
    cardBg: Color,
    cardBorder: Color,
    pillSurface: Color,
    primaryAccent: Color,
    primaryBubbleBg: Color,
    primaryCtaBg: Color,
    primaryCtaText: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSpeakAloud: () -> Unit,
    onDraft: (TaskDraft) -> Unit,
    onFeatureCardClick: (ChatFeatureCard) -> Unit,
) {
    val context = LocalContext.current
    val feedbackVal = ChatMemory.feedback[turnKey]
    val featureCards = remember(answer, channel) {
        if (channel == ChatBotChannel.AGRI) Assistant.buildAgriFeatureCards(answer, c.d)
        else answer.featureCards
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. User Message Bubble (Căn phải giống 70d, 70e, 70f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 6.dp,
                        )
                    )
                    .background(primaryBubbleBg)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    answer.question,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                )
            }
        }

        // 2. AI Response Row (Quả cầu 3D 28.dp bên trái + Nội dung + Embedded Feature Cards + Helpful/Not helpful)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VWingAiOrb(
                channel = channel,
                size = 28.dp,
                isDarkStyle = isDarkChat,
                animatePulse = false,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Dòng tiêu đề + Nút đọc to bằng giọng nói
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        answer.scope,
                        color = primaryAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(999.dp))
                            .bounceClick(onClick = onSpeakAloud)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Đọc to", tint = primaryAccent, modifier = Modifier.size(14.dp))
                        Text("Đọc to", color = textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Tóm tắt chính (Chữ rõ nét như đoạn văn của Joi trong 70d/70e)
                Text(
                    answer.summary,
                    color = textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp,
                )

                // Các ý chi tiết ngắn gọn
                answer.facts.take(3).forEach { f ->
                    Text(
                        "• ${f.text}",
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                // 3A. Thẻ Công Việc Nháp (Cloned từ thẻ "Box breathing · In, hold, out, hold · [Start]" trong 70d & 70e)
                answer.drafts.forEach { draft ->
                    val draftKey = "$idx|${draft.title}"
                    val alreadyCreated = ChatMemory.createdDrafts[draftKey] == true
                    EmbeddedActionCard70d(
                        icon = Icons.Filled.AutoAwesome,
                        title = draft.title,
                        subtitle = if (alreadyCreated) "Đã lưu vào danh sách công việc của vườn" else "Lịch chăm sóc khuyến nghị · ${draft.reason}",
                        buttonText = if (alreadyCreated) "Đã tạo ✓" else "Tạo việc",
                        isDarkChat = isDarkChat,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        pillSurface = pillSurface,
                        primaryAccent = primaryAccent,
                        primaryCtaBg = primaryCtaBg,
                        primaryCtaText = primaryCtaText,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { if (!alreadyCreated) onDraft(draft) },
                    )
                }

                // 3B. Thẻ Chức Năng Trong Luồng Chat (Cloned từ thẻ "Self-Care and Anxiety log · [Open]" trong 70f)
                featureCards.forEach { fc ->
                    val fcIcon = when (fc.iconType) {
                        "ticket" -> Icons.Filled.SupportAgent
                        "plan" -> Icons.Filled.Tune
                        "qr" -> Icons.Filled.QrCodeScanner
                        "map" -> Icons.Filled.Map
                        "sync" -> Icons.Filled.Sync
                        "log" -> Icons.Filled.BookmarkBorder
                        else -> Icons.Filled.Build
                    }
                    val isSubmittedTicket = fc.actionRoute.startsWith("action:submit_ticket|") &&
                        ChatMemory.submittedTickets.any { fc.actionRoute.contains(it.code) }

                    EmbeddedActionCard70d(
                        icon = fcIcon,
                        title = fc.title,
                        subtitle = if (isSubmittedTicket) "✓ Đã chuyển tới Đội ngũ Phát triển VWing · Đang xử lý ưu tiên" else fc.subtitle,
                        buttonText = if (isSubmittedTicket) "Đã gửi ✓" else fc.buttonLabel,
                        isDarkChat = isDarkChat,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        pillSurface = pillSurface,
                        primaryAccent = primaryAccent,
                        primaryCtaBg = primaryCtaBg,
                        primaryCtaText = primaryCtaText,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { onFeatureCardClick(fc) },
                    )
                }

                // 3C. Dải sản phẩm gợi ý từ Internet (khi ở kênh Nông nghiệp)
                if (channel == ChatBotChannel.AGRI && answer.products.isNotEmpty()) {
                    Text(
                        "🛒 Vật tư & Thuốc BVTV gợi ý (${answer.products.size} loại · Chạm để xem giá Google):",
                        color = primaryAccent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        answer.products.take(6).forEach { p ->
                            Column(
                                Modifier
                                    .width(225.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                    .bounceClick {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(p.googleSearchUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(p.name, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.dosageForGarden, color = primaryAccent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.referencePrice} · 🌐 Mở Google", color = VC.amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                // 4. Dòng đánh giá "👍 Hữu ích" · "👎 Chưa sát" (Cloned từ 70d, 70e, 70f)
                if (feedbackVal != null) {
                    Text(
                        "Cảm ơn bác, phản hồi này giúp ${channel.title} hỗ trợ ngày càng chuẩn xác hơn.",
                        color = textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { ChatMemory.feedback[turnKey] = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.ThumbUpOffAlt, contentDescription = null, tint = textSecondary, modifier = Modifier.size(15.dp))
                            Text("Hữu ích", color = textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { ChatMemory.feedback[turnKey] = false }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.ThumbDownOffAlt, contentDescription = null, tint = textSecondary, modifier = Modifier.size(15.dp))
                            Text("Chưa sát", color = textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thẻ tính năng nhúng trong hội thoại (Cloned từ "Box breathing · [Start]" ở 70d/70e và "Self-Care and Anxiety log · [Open]" ở 70f).
 */
@Composable
private fun EmbeddedActionCard70d(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    isDarkChat: Boolean,
    cardBg: Color,
    cardBorder: Color,
    pillSurface: Color,
    primaryAccent: Color,
    primaryCtaBg: Color,
    primaryCtaText: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkChat) 0.dp else 4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.2.dp, cardBorder, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(pillSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(primaryCtaBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                buttonText,
                color = primaryCtaText,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun SmallModeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) VC.pine else VC.elevated)
            .border(1.dp, if (active) VC.lime else VC.border, RoundedCornerShape(999.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else VC.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SmallActionPill(
    label: String,
    isDarkChat: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (isDarkChat) Color(0xFF0D2430) else Color.White)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isDarkChat) Color(0xFFF4F8FA) else Color(0xFF0F2419),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Hộp thoại Ghi chú đã lưu & Danh sách Phiếu Liên hệ Đội ngũ Phát triển (Khi bấm nút `🔖` trên thanh tiêu đề 70a–70f).
 */
@Composable
private fun SavedNotesAndTicketsDialog(
    c: VCtx,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VC.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = VC.lime)
                Text(
                    "Ghi chú AI & Phiếu Liên hệ Đội Phát Triển",
                    color = VC.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "🛠️ Phiếu hỗ trợ kỹ thuật & góp ý gửi Đội Phát Triển (${ChatMemory.submittedTickets.size}):",
                    color = VC.cyan,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (ChatMemory.submittedTickets.isEmpty()) {
                    Text(
                        "Chưa có phiếu nào. Bạn có thể chat ở tab \"🛠️ AI Hỗ trợ Kỹ thuật\" để gửi yêu cầu trực tiếp cho đội phát triển mà không cần gọi SĐT hay gửi Email.",
                        color = VC.text2,
                        fontSize = 13.sp,
                    )
                } else {
                    ChatMemory.submittedTickets.forEach { tk ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VC.elevated)
                                .border(1.dp, VC.cyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Mã phiếu #${tk.code}", color = VC.text, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                Tag("ĐÃ GỬI DEV", VC.lime)
                            }
                            Text(tk.content, color = VC.text2, fontSize = 13.sp)
                            Text("SĐT: ${tk.phone} · ${tk.planLabel} · ${tk.createdAt}", color = VC.muted, fontSize = 11.5.sp)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "🌱 Lịch sử tư vấn Nông nghiệp & Kỹ thuật (${ChatMemory.answers.size + ChatMemory.techAnswers.size} câu):",
                    color = VC.lime,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                val allSaved = (ChatMemory.answers + ChatMemory.techAnswers).takeLast(5).reversed()
                if (allSaved.isEmpty()) {
                    Text("Chưa có câu hỏi nào được lưu trong phiên này.", color = VC.text2, fontSize = 13.sp)
                } else {
                    allSaved.forEach { ans ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VC.elevated)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Hỏi: ${ans.question}", color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Đáp: ${ans.summary}", color = VC.text2, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (ChatMemory.answers.isNotEmpty() || ChatMemory.techAnswers.isNotEmpty()) {
                TextButton(onClick = {
                    ChatMemory.answers.clear()
                    ChatMemory.techAnswers.clear()
                    c.toast("Đã xóa lịch sử trò chuyện tạm thời.")
                    onDismiss()
                }) {
                    Text("Xóa lịch sử chat", color = VC.red, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            VButton("Đóng", onDismiss)
        },
    )
}

/**
 * Khu vực tìm kiếm & hiển thị đầy đủ tất cả các sản phẩm Phân bón, Thuốc BVTV, Vi lượng, Cải tạo đất
 * thông qua Internet (Google Search / Google Shopping / CSDL Vật tư Nông nghiệp).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InternetProductShowcase(
    c: VCtx,
    initialProducts: List<AgriProduct>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = c.d
    var selectedCategory by remember { mutableStateOf("TẤT CẢ") }
    var searchKeyword by remember { mutableStateOf("") }
    var searchingWeb by remember { mutableStateOf(false) }
    var webProducts by remember(d.activeTrees.size, d.garden.crop) {
        mutableStateOf(initialProducts ?: Assistant.buildAllRecommendedProducts(d))
    }

    val speakProductSearch = rememberVoiceInput(c) { spoken ->
        searchKeyword = spoken
        searchingWeb = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                GeminiClient.searchInternetProducts(spoken, d)
            }
            webProducts = found
            searchingWeb = false
        }
    }

    fun openExternalUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            c.toast("Không mở được trình duyệt: ${it.message}")
        }
    }

    val filteredProducts = remember(webProducts, selectedCategory, searchKeyword) {
        webProducts.filter { p ->
            val catOk = selectedCategory == "TẤT CẢ" || p.category == selectedCategory || p.category == "TÌM TRÊN INTERNET"
            val q = searchKeyword.trim().lowercase()
            val qOk = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.activeIngredient.lowercase().contains(q) ||
                p.category.lowercase().contains(q) ||
                p.benefitShort.lowercase().contains(q)
            catOk && qOk
        }
    }

    VCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("🌐 TRA CỨU PHÂN BÓN & THUỐC TRỪ SÂU BỆNH")
                Text(
                    "Danh mục Phân bón & Thuốc trừ sâu bệnh (${filteredProducts.size} loại)",
                    color = VC.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (searchKeyword.isNotBlank()) {
                VButton(
                    "🌐 Tìm Google \"$searchKeyword\"",
                    {
                        val qUrl = "https://www.google.com/search?q=" + URLEncoder.encode("$searchKeyword phân bón thuốc nông nghiệp giá", "UTF-8")
                        openExternalUrl(qUrl)
                    },
                    primary = false,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VField(
                value = searchKeyword,
                onChange = { searchKeyword = it },
                label = "🔍 Nhập tên phân bón, thuốc trừ nấm, sâu rầy cần tìm…",
                modifier = Modifier.weight(1f),
            )
            VButton("🎤 Nói", speakProductSearch, primary = false, icon = Icons.Filled.Mic)
            VButton(
                "🌐 Tra cứu giá",
                {
                    searchingWeb = true
                    scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            GeminiClient.searchInternetProducts(searchKeyword, d)
                        }
                        webProducts = found
                        searchingWeb = false
                        c.toast("Đã cập nhật ${found.size} sản phẩm từ Internet!")
                    }
                },
                enabled = !searchingWeb,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PRODUCT_CATEGORIES.forEach { cat ->
                val active = selectedCategory == cat
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) VC.lime else VC.elevated)
                        .border(1.dp, if (active) VC.lime else VC.border, RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        humanize(cat),
                        color = if (active) VC.onLime else VC.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        if (searchingWeb) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = VC.lime, strokeWidth = 2.dp)
                Text("Đang tìm kiếm sản phẩm trên Internet…", color = VC.text2, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filteredProducts.forEach { p ->
                val catColor = when (p.category) {
                    "PHÂN BÓN NPK" -> VC.lime
                    "HỮU CƠ & HẠ PHÈN" -> VC.mint
                    "THUỐC TRỪ NẤM" -> VC.amber
                    "THUỐC TRỪ SÂU RẦY" -> VC.red
                    "TÌM TRÊN INTERNET" -> VC.cyan
                    else -> VC.teal
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VC.elevated)
                        .border(1.dp, catColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.name,
                            color = VC.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                        )
                        Tag(p.category, catColor)
                    }
                    Text(
                        "Hoạt chất: ${p.activeIngredient} · Hãng: ${p.brandOrigin}",
                        color = VC.text2,
                        fontSize = 12.5.sp,
                    )
                    Text(
                        "📌 Định lượng cho vườn: ${p.dosageForGarden}",
                        color = VC.lime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "💡 Công dụng: ${p.benefitShort}",
                        color = VC.text,
                        fontSize = 12.5.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "💰 ${p.referencePrice}",
                            color = VC.amber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VC.card)
                                    .border(1.dp, VC.cyan, RoundedCornerShape(10.dp))
                                    .clickable { openExternalUrl(p.googleSearchUrl) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Public, contentDescription = null, tint = VC.cyan, modifier = Modifier.size(14.dp))
                                    Text("Xem trên Google", color = VC.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VC.lime)
                                    .clickable { openExternalUrl(p.shoppingSearchUrl) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = VC.onLime, modifier = Modifier.size(14.dp))
                                    Text("Báo giá Mua", color = VC.onLime, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiConfigDialog(
    initial: GeminiConfig,
    onDismiss: () -> Unit,
    onSave: (GeminiConfig) -> Unit,
) {
    var selectedModel by remember { mutableStateOf(initial.model.ifBlank { DEV_ENV_GEMINI_MODEL }) }
    var autoSpeak by remember { mutableStateOf(initial.autoSpeakResponse) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VC.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = VC.lime)
                Text("Tùy chọn Mô hình Gemini AI & Giọng nói", color = VC.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "🔒 Khóa kết nối Gemini API đã được nạp sẵn từ file .env (Chế độ Dev).",
                    color = VC.mint,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                VPicker(
                    label = "Chọn Mô hình Gemini AI",
                    options = GEMINI_MODELS.map { "${it.id} · ${it.label}" },
                    selected = GEMINI_MODELS.firstOrNull { it.id == selectedModel }?.let { "${it.id} · ${it.label}" }
                        ?: "${GEMINI_MODELS.first().id} · ${GEMINI_MODELS.first().label}",
                    onSelect = { picked ->
                        selectedModel = picked.substringBefore(" · ").trim()
                    },
                )
                ChipRow(
                    listOf(true, false),
                    autoSpeak,
                    { if (it) "🔊 Tự động đọc to câu trả lời" else "🔇 Chỉ đọc khi bấm nút Loa" },
                    { autoSpeak = it },
                )
            }
        },
        confirmButton = {
            VButton("Lưu tùy chọn", {
                onSave(
                    GeminiConfig(
                        apiKey = initial.apiKey.ifBlank { DEV_ENV_GEMINI_API_KEY },
                        model = selectedModel,
                        enabled = true,
                        autoSpeakResponse = autoSpeak,
                    )
                )
            })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = VC.text2) }
        },
    )
}
