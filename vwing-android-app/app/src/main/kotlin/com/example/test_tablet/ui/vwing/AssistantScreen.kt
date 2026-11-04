package com.example.test_tablet.ui.vwing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tablet.data.vwing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Lịch sử hội thoại giữ trong bộ nhớ tiến trình. */
object ChatMemory {
    val answers = mutableStateListOf<AiAnswer>()
    val feedback = mutableStateMapOf<Int, Boolean>()
    val createdDrafts = mutableStateMapOf<String, Boolean>()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(c: VCtx) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = c.d
    val geminiSettings = remember { GeminiSettings(context.filesDir, context) }
    var geminiCfg by remember { mutableStateOf(geminiSettings.load()) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var busyAsking by remember { mutableStateOf(false) }

    var input by remember { mutableStateOf("") }
    val voiceSpeaker = rememberVoiceSpeaker(c)
    val todayStr = Dates.fmt(Dates.today())
    val unlimitedAi = c.isFeatureEnabled(VFeature.UNLIMITED_AI)
    val remainingAi = c.uiSettings.remainingAiQuestions(todayStr)

    fun askQuestion(q: String) {
        val clean = q.trim()
        if (clean.isBlank() || busyAsking) return
        if (!c.isFeatureEnabled(VFeature.UNLIMITED_AI) && c.uiSettings.remainingAiQuestions(todayStr) <= 0) {
            c.requestFeature(VFeature.UNLIMITED_AI)
            return
        }
        c.updateUiSettings { it.recordAiQuestion(todayStr) }
        busyAsking = true
        scope.launch {
            val ans = withContext(Dispatchers.IO) {
                GeminiClient.ask(clean, c.store.data, geminiCfg)
            }
            ChatMemory.answers.add(ans)
            busyAsking = false
            if (geminiCfg.autoSpeakResponse) {
                val readText = ans.summary + ". " + ans.facts.take(2).joinToString(". ") { it.text }
                voiceSpeaker.speak(readText)
            }
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
        if (initial.isNotBlank()) {
            askQuestion(initial)
            c.nav.stack[c.nav.stack.lastIndex] = "ai"
        }
    }

    val isEngineerMode = !c.simpleMode && !VUi.simpleMode

    if (isEngineerMode) {
        PageHeader(
            Icons.Filled.AutoAwesome,
            "Trợ lý AI ngắn gọn · Tìm kiếm sản phẩm trên Internet",
            "Trả lời súc tích + Giới thiệu tất cả sản phẩm trên thị trường.",
            "Mô hình Gemini AI (${geminiCfg.model}) trả lời ngắn gọn định lượng phân bón/thuốc xịt và tìm kiếm toàn bộ sản phẩm nông nghiệp trên Internet.",
        ) {
            Tag("🤖 ${geminiCfg.model.uppercase()}", VC.lime)
            VButton("⚙ Model & Giọng đọc", { showGeminiDialog = true }, primary = false)
        }
    }

    if (showGeminiDialog && isEngineerMode) {
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VCard(Modifier.fillMaxWidth()) {
            // Chỉ hiển thị thanh trạng thái cấu hình Model AI ở Chế độ Kỹ sư (isEngineerMode)
            if (isEngineerMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VC.elevated)
                        .border(1.dp, VC.lime.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✅ Gemini AI (${geminiCfg.model}) · Trả lời ngắn gọn & Tìm sản phẩm qua Internet",
                        color = VC.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showGeminiDialog = true }) {
                        Text("Tùy chọn", color = VC.lime, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!unlimitedAi) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (remainingAi > 0) VC.pine else VC.amber.copy(alpha = 0.15f))
                        .border(1.dp, if (remainingAi > 0) VC.mint.copy(alpha = 0.55f) else VC.amber.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (remainingAi > 0) "🌱 Gói Free: còn $remainingAi/${VFeature.FREE_MAX_AI_PER_DAY} câu hỏi Kỹ sư AI hôm nay"
                        else "🔒 Đã hết ${VFeature.FREE_MAX_AI_PER_DAY} câu hỏi miễn phí hôm nay",
                        color = if (remainingAi > 0) VC.mint else VC.amber,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { c.requestFeature(VFeature.UNLIMITED_AI) }) {
                        Text("✦ Mở khóa Pro", color = VC.gold, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SectionLabel("Chạm câu hỏi nhanh hoặc bấm 🎤 Nói bằng giọng nói")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SUGGESTED_QUESTIONS.forEach { q ->
                    Text(
                        q,
                        color = VC.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(VC.elevated)
                            .border(1.dp, VC.border, RoundedCornerShape(18.dp))
                            .clickable { askQuestion(q) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VButton("🎤 Nói & Hỏi ngay", speakAndSendNow, icon = Icons.Filled.Mic, enabled = !busyAsking)
                VButton("🎙 Nói để sửa chữ", speakToFill, primary = false, enabled = !busyAsking)
                if (voiceSpeaker.isSpeaking) {
                    VButton("⏹ Dừng đọc", { voiceSpeaker.stop() }, primary = false, danger = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VField(input, { input = it }, "Hỏi ngắn gọn về phân bón, thuốc xịt, tên sản phẩm…", Modifier.weight(1f))
                VButton("HỎI AI", { askQuestion(input); input = "" }, enabled = input.isNotBlank() && !busyAsking)
            }

            if (busyAsking) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VC.elevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = VC.lime, strokeWidth = 2.5.dp)
                    Text(
                        "Đang trả lời ngắn gọn & tìm kiếm sản phẩm trên Internet…",
                        color = VC.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            var openEvent by remember { mutableStateOf<String?>(null) }
            var draftToCreate by remember { mutableStateOf<Pair<Int, TaskDraft>?>(null) }

            ChatMemory.answers.reversed().forEachIndexed { idx, a ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    Text(
                        a.question,
                        color = VC.onLime,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(VC.lime)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                AnswerBubble(
                    c = c,
                    idx = idx,
                    a = a,
                    onSpeakAloud = {
                        val readText = a.summary + ". " + a.facts.take(3).joinToString(". ") { it.text }
                        voiceSpeaker.speak(readText)
                    },
                    onOpenEvent = { openEvent = it },
                    onDraft = { draftToCreate = idx to it },
                )
            }

            openEvent?.let { EventDialog(c, it) { openEvent = null } }
            draftToCreate?.let { (idx, draft) ->
                AlertDialog(
                    onDismissRequest = { draftToCreate = null },
                    containerColor = VC.card,
                    title = { Text("Tạo công việc?", color = VC.text) },
                    text = {
                        Text("\"${draft.title}\" sẽ được thêm vào danh sách công việc.", color = VC.text2)
                    },
                    confirmButton = {
                        VButton("Tạo công việc", {
                            val r = c.store.addTask(draft.title, draft.treeIds, null, d.garden.operatorName, Dates.daysAgo(-1), fromAi = true)
                            if (r.ok) {
                                ChatMemory.createdDrafts["$idx|${draft.title}"] = true
                                c.toast("Đã tạo công việc.")
                            }
                            draftToCreate = null
                        })
                    },
                    dismissButton = { TextButton({ draftToCreate = null }) { Text("Hủy", color = VC.text2) } },
                )
            }
        }

        // Khu vực Tìm kiếm & Giới thiệu TẤT CẢ Sản phẩm Nông nghiệp qua Internet
        InternetProductShowcase(c = c)
    }
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
                Eyebrow("🌐 TÌM KIẾM SẢN PHẨM NÔNG NGHIỆP QUA INTERNET")
                Text(
                    "Danh mục Tất cả Sản phẩm Phân bón & Thuốc BVTV (${filteredProducts.size} sản phẩm)",
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
        // Thanh tìm kiếm trực tiếp trên Internet + Giọng nói
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VField(
                value = searchKeyword,
                onChange = { searchKeyword = it },
                label = "🔍 Tìm tên phân bón, thuốc BVTV, hoạt chất trên Internet…",
                modifier = Modifier.weight(1f),
            )
            VButton("🎤 Nói", speakProductSearch, primary = false, icon = Icons.Filled.Mic)
            VButton(
                "🌐 Tìm Internet",
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
        // Bộ lọc nhóm sản phẩm
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) VC.lime else VC.elevated)
                        .border(1.dp, if (active) VC.lime else VC.border, RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        cat,
                        color = if (active) VC.onLime else VC.text,
                        fontSize = 12.5.sp,
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
        // Lưới hiển thị tất cả sản phẩm
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

@Composable
private fun AnswerBubble(
    c: VCtx,
    idx: Int,
    a: AiAnswer,
    onSpeakAloud: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onDraft: (TaskDraft) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VC.elevated)
            .border(1.dp, VC.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🤖 GEMINI AI · TRẢ LỜI NGẮN GỌN", color = VC.mint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VC.card)
                    .border(1.dp, VC.border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onSpeakAloud)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Đọc to", tint = VC.lime, modifier = Modifier.size(15.dp))
                Text("🔊 Đọc to", color = VC.lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Tóm tắt 1 dòng to rõ
        Text(a.summary, color = VC.text, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)

        // Tối đa 3 gạch đầu dòng ngắn gọn
        a.facts.take(3).forEach { f ->
            Text("• ${f.text}", color = VC.text2, fontSize = 14.sp)
        }

        // Gợi ý nhanh các sản phẩm tìm được trên Internet ngay trong bong bóng chat
        if (a.products.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "🛒 Sản phẩm gợi ý từ Internet (${a.products.size} sản phẩm · Chạm để xem trên Google):",
                color = VC.lime,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                a.products.take(8).forEach { p ->
                    Column(
                        Modifier
                            .width(230.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VC.card)
                            .border(1.dp, VC.lime.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(p.googleSearchUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                            .padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(p.name, color = VC.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(p.dosageForGarden, color = VC.lime, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${p.referencePrice} · 🌐 Chạm mở Google", color = VC.amber, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
