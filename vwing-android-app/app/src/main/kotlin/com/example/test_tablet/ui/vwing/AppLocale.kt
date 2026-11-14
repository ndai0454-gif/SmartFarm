package com.example.test_tablet.ui.vwing

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AppCountry(
    val langCode: String,
    val countryCode: String,
    val flag: String,
    val countryName: String,
    val languageName: String,
    val sampleLocation: String,
    val sampleLat: Double,
    val sampleLng: Double,
) {
    VN("vi", "VN", "🇻🇳", "Việt Nam", "Tiếng Việt", "Bảo Lộc, Lâm Đồng", 11.5511, 107.7996),
    US("en", "US", "🇺🇸", "United States", "English", "Fresno, California", 36.7378, -119.7871),
    ID("id", "ID", "🇮🇩", "Indonesia", "Bahasa Indonesia", "Bandung, West Java", -6.9175, 107.6191),
    TH("th", "TH", "🇹🇭", "ประเทศไทย", "ภาษาไทย", "Chanthaburi, Thailand", 12.6114, 102.1039),
    JP("ja", "JP", "🇯🇵", "日本", "日本語", "Shizuoka, Japan", 34.9756, 138.3828),
    KR("ko", "KR", "🇰🇷", "대한민국", "한국어", "Jeju, South Korea", 33.4996, 126.5312),
    BR("pt", "BR", "🇧🇷", "Brasil", "Português", "Campinas, São Paulo", -22.9099, -47.0626);

    fun toLocale(): Locale = Locale(langCode, countryCode)
}

object AppLocaleStore {
    private const val PREFS = "vwing_locale_prefs"
    private const val KEY_COUNTRY = "selected_country"

    fun load(context: Context): AppCountry {
        val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COUNTRY, AppCountry.VN.name)
        return AppCountry.entries.firstOrNull { it.name == code } ?: AppCountry.VN
    }

    fun save(context: Context, country: AppCountry) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTRY, country.name)
            .apply()
    }
}

data class LocalizedDashboardStrings(
    val hello: String,
    val pickerTitle: String,
    val pickerSubtitle: String,
    val closeLabel: String,
    val switchedToast: (AppCountry) -> String,
    val todayWord: String,
    val forecastChip: (String) -> String,
    val windLabel: String,
    val humidityLabel: String,
    val rainLabel: String,
    val highLowLabel: (Int, Int) -> String,
    val sunClear1: String,
    val sunClear2: String,
    val cloudy1: String,
    val cloudy2: String,
    val fog1: String,
    val fog2: String,
    val rain1: String,
    val rain2: String,
    val heavyRain1: String,
    val heavyRain2: String,
    val storm1: String,
    val storm2: String,
    val adviceRain: (Int, Int, Int, Int) -> String,
    val adviceHot: (Int, Int) -> String,
    val adviceMild: (Int, Int, Int) -> String,
    val weekdays: List<String>, // Sun..Sat (Calendar.SUNDAY=1 .. SATURDAY=7)
)

object AppStrings {
    fun tr(text: String, country: AppCountry = VUi.country): String = AppPhraseTranslator.translate(text, country)

    fun forCountry(c: AppCountry): LocalizedDashboardStrings = when (c) {
        AppCountry.VN -> LocalizedDashboardStrings(
            hello = "Xin chào,",
            pickerTitle = "Chọn Quốc gia & Ngôn ngữ",
            pickerSubtitle = "Đổi ngôn ngữ giao diện và khu vực thời tiết thử nghiệm",
            closeLabel = "Đóng",
            switchedToast = { "${it.flag} Đã chuyển sang ${it.countryName} (${it.languageName})" },
            todayWord = "Hôm nay",
            forecastChip = { "Dự báo · $it" },
            windLabel = "Gió",
            humidityLabel = "Ẩm",
            rainLabel = "Mưa",
            highLowLabel = { hi, lo -> "Cao ${hi}°  Thấp ${lo}°" },
            sunClear1 = "Nắng ấm", sunClear2 = "& Quang mây",
            cloudy1 = "Nhiều mây", cloudy2 = "& Nắng nhẹ",
            fog1 = "Sương mù", fog2 = "Độ ẩm cao",
            rain1 = "Mưa rào", rain2 = "Rải rác",
            heavyRain1 = "Mưa rào", heavyRain2 = "& Gió mạnh",
            storm1 = "Mưa rào", storm2 = "& Dông chiều",
            adviceRain = { rain, lo, hi, needs -> "Khả năng mưa $rain% ($lo°–$hi°C) — Khoan phun thuốc, ưu tiên khơi rãnh thoát nước gốc và thăm $needs cây cần chú ý." },
            adviceHot = { hi, rain -> "Trời nắng ấm $hi°C (mưa $rain%) — Kiểm tra giữ ẩm gốc buổi sáng sớm, thuận lợi tỉa cành." },
            adviceMild = { lo, hi, rain -> "Trời mát $lo°–$hi°C (mưa $rain%) — Thời tiết thuận lợi để đi thăm vườn, bón phân và chăm sóc cây." },
            weekdays = listOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"),
        )
        AppCountry.US -> LocalizedDashboardStrings(
            hello = "Hello,",
            pickerTitle = "Select Country & Language",
            pickerSubtitle = "Switch app language and regional weather feed",
            closeLabel = "Close",
            switchedToast = { "${it.flag} Switched to ${it.countryName} (${it.languageName})" },
            todayWord = "Today",
            forecastChip = { "Forecast · $it" },
            windLabel = "Wind",
            humidityLabel = "Humidity",
            rainLabel = "Rain",
            highLowLabel = { hi, lo -> "High ${hi}°  Low ${lo}°" },
            sunClear1 = "Sunny", sunClear2 = "& Clear Sky",
            cloudy1 = "Partly", cloudy2 = "Cloudy",
            fog1 = "Misty", fog2 = "High Humidity",
            rain1 = "Light", rain2 = "Showers",
            heavyRain1 = "Rain", heavyRain2 = "& Gusts",
            storm1 = "Storm", storm2 = "& Thunder",
            adviceRain = { rain, lo, hi, needs -> "Rain chance $rain% ($lo°–$hi°C) — Hold spraying, clear root drainage and inspect $needs flagged trees." },
            adviceHot = { hi, rain -> "Warm $hi°C ($rain% rain) — Check morning soil moisture and schedule light pruning." },
            adviceMild = { lo, hi, rain -> "Mild $lo°–$hi°C ($rain% rain) — Ideal conditions for orchard inspection and fertilizing." },
            weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
        )
        AppCountry.ID -> LocalizedDashboardStrings(
            hello = "Halo,",
            pickerTitle = "Pilih Negara & Bahasa",
            pickerSubtitle = "Ubah bahasa aplikasi dan cuaca wilayah uji coba",
            closeLabel = "Tutup",
            switchedToast = { "${it.flag} Beralih ke ${it.countryName} (${it.languageName})" },
            todayWord = "Hari ini",
            forecastChip = { "Prakiraan · $it" },
            windLabel = "Angin",
            humidityLabel = "Lembap",
            rainLabel = "Hujan",
            highLowLabel = { hi, lo -> "Maks ${hi}°  Min ${lo}°" },
            sunClear1 = "Cerah", sunClear2 = "& Terang",
            cloudy1 = "Berawan", cloudy2 = "& Sejuk",
            fog1 = "Berkabut", fog2 = "Lembap Tinggi",
            rain1 = "Hujan", rain2 = "Ringan",
            heavyRain1 = "Hujan", heavyRain2 = "& Angin",
            storm1 = "Badai", storm2 = "& Petir",
            adviceRain = { rain, lo, hi, needs -> "Peluang hujan $rain% ($lo°–$hi°C) — Tunda penyemprotan, bersihkan saluran air dan periksa $needs pohon." },
            adviceHot = { hi, rain -> "Cerah hangat $hi°C (hujan $rain%) — Cek kelembapan tanah pagi hari." },
            adviceMild = { lo, hi, rain -> "Sejuk $lo°–$hi°C (hujan $rain%) — Waktu ideal untuk perawatan kebun dan pemupukan." },
            weekdays = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"),
        )
        AppCountry.TH -> LocalizedDashboardStrings(
            hello = "สวัสดี,",
            pickerTitle = "เลือกประเทศและภาษา",
            pickerSubtitle = "เปลี่ยนภาษาและข้อมูลสภาพอากาศตามประเทศ",
            closeLabel = "ปิด",
            switchedToast = { "${it.flag} เปลี่ยนเป็น ${it.countryName} (${it.languageName})" },
            todayWord = "วันนี้",
            forecastChip = { "พยากรณ์ · $it" },
            windLabel = "ลม",
            humidityLabel = "ความชื้น",
            rainLabel = "ฝน",
            highLowLabel = { hi, lo -> "สูงสุด ${hi}°  ต่ำสุด ${lo}°" },
            sunClear1 = "แดดจ้า", sunClear2 = "ท้องฟ้าโปร่ง",
            cloudy1 = "มีเมฆมาก", cloudy2 = "แดดอ่อน",
            fog1 = "มีหมอก", fog2 = "ความชื้นสูง",
            rain1 = "ฝนโปรย", rain2 = "กระจาย",
            heavyRain1 = "ฝนตก", heavyRain2 = "และลมแรง",
            storm1 = "พายุฝน", storm2 = "ฟ้าคะนอง",
            adviceRain = { rain, lo, hi, needs -> "โอกาสฝน $rain% ($lo°–$hi°C) — งดพ่นยา ระบายน้ำโคนต้น และตรวจ $needs ต้นที่ต้องดูแล" },
            adviceHot = { hi, rain -> "อากาศอบอุ่น $hi°C (ฝน $rain%) — ตรวจความชื้นดินช่วงเช้าและตัดแต่งกิ่ง" },
            adviceMild = { lo, hi, rain -> "อากาศสบาย $lo°–$hi°C (ฝน $rain%) — เหมาะสำหรับเดินตรวจสวนและใส่ปุ๋ย" },
            weekdays = listOf("อาทิตย์", "จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์"),
        )
        AppCountry.JP -> LocalizedDashboardStrings(
            hello = "こんにちは、",
            pickerTitle = "国と言語を選択",
            pickerSubtitle = "アプリの表示言語と地域の天気を切り替えます",
            closeLabel = "閉じる",
            switchedToast = { "${it.flag} ${it.countryName} (${it.languageName}) に切り替えました" },
            todayWord = "今日",
            forecastChip = { "予報 · $it" },
            windLabel = "風",
            humidityLabel = "湿度",
            rainLabel = "降水",
            highLowLabel = { hi, lo -> "最高 ${hi}°  最低 ${lo}°" },
            sunClear1 = "晴れ", sunClear2 = "快晴",
            cloudy1 = "くもり", cloudy2 = "薄日",
            fog1 = "霧", fog2 = "高湿度",
            rain1 = "にわか雨", rain2 = "所により",
            heavyRain1 = "雨", heavyRain2 = "強風",
            storm1 = "雷雨", storm2 = "突風注意",
            adviceRain = { rain, lo, hi, needs -> "降水確率 $rain% ($lo°–$hi°C) — 散布は控え、排水溝と要確認の木 $needs 本を点検してください。" },
            adviceHot = { hi, rain -> "暖かい晴天 $hi°C (降水 $rain%) — 朝の土壌水分と剪定作業に適しています。" },
            adviceMild = { lo, hi, rain -> "穏やかな気候 $lo°–$hi°C (降水 $rain%) — 園地の見回りと施肥に最適です。" },
            weekdays = listOf("日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日"),
        )
        AppCountry.KR -> LocalizedDashboardStrings(
            hello = "안녕하세요,",
            pickerTitle = "국가 및 언어 선택",
            pickerSubtitle = "앱 언어와 테스트 지역 날씨를 변경합니다",
            closeLabel = "닫기",
            switchedToast = { "${it.flag} ${it.countryName} (${it.languageName})(으)로 전환됨" },
            todayWord = "오늘",
            forecastChip = { "예보 · $it" },
            windLabel = "바람",
            humidityLabel = "습도",
            rainLabel = "강수",
            highLowLabel = { hi, lo -> "최고 ${hi}°  최저 ${lo}°" },
            sunClear1 = "맑음", sunClear2 = "화창함",
            cloudy1 = "구름 많음", cloudy2 = "약한 햇볕",
            fog1 = "안개", fog2 = "높은 습도",
            rain1 = "소나기", rain2 = "산발적",
            heavyRain1 = "비", heavyRain2 = "강풍 동반",
            storm1 = "뇌우", storm2 = "돌풍 주의",
            adviceRain = { rain, lo, hi, needs -> "강수 확률 $rain% ($lo°–$hi°C) — 방제를 미루고 배수로 정비 및 주의 나무 ${needs}그루를 점검하세요." },
            adviceHot = { hi, rain -> "따뜻한 날씨 $hi°C (강수 $rain%) — 오전 토양 수분 확인 및 가지치기에 적합합니다." },
            adviceMild = { lo, hi, rain -> "쾌적한 날씨 $lo°–$hi°C (강수 $rain%) — 과수원 순찰과 비료 주기에 좋습니다." },
            weekdays = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"),
        )
        AppCountry.BR -> LocalizedDashboardStrings(
            hello = "Olá,",
            pickerTitle = "Selecionar País e Idioma",
            pickerSubtitle = "Alterar idioma do app e clima da região de teste",
            closeLabel = "Fechar",
            switchedToast = { "${it.flag} Alterado para ${it.countryName} (${it.languageName})" },
            todayWord = "Hoje",
            forecastChip = { "Previsão · $it" },
            windLabel = "Vento",
            humidityLabel = "Umidade",
            rainLabel = "Chuva",
            highLowLabel = { hi, lo -> "Máx ${hi}°  Mín ${lo}°" },
            sunClear1 = "Ensolarado", sunClear2 = "& Céu Limpo",
            cloudy1 = "Nublado", cloudy2 = "& Sol Fraco",
            fog1 = "Neblina", fog2 = "Alta Umidade",
            rain1 = "Pancadas", rain2 = "Isoladas",
            heavyRain1 = "Chuva", heavyRain2 = "& Vento Forte",
            storm1 = "Tempestade", storm2 = "& Trovoadas",
            adviceRain = { rain, lo, hi, needs -> "Chance de chuva $rain% ($lo°–$hi°C) — Evite pulverizar, limpe a drenagem e verifique $needs árvores." },
            adviceHot = { hi, rain -> "Dia quente $hi°C (chuva $rain%) — Verifique a umidade do solo pela manhã." },
            adviceMild = { lo, hi, rain -> "Clima ameno $lo°–$hi°C (chuva $rain%) — Ótimo para inspeção do pomar e adubação." },
            weekdays = listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"),
        )
    }

    fun weekdayLabel(country: AppCountry, dateStr: String?, offsetDays: Int): String {
        val cal = Calendar.getInstance()
        if (dateStr != null) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.let { cal.time = it }
            }.onFailure { cal.add(Calendar.DAY_OF_YEAR, offsetDays) }
        } else {
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        val names = forCountry(country).weekdays
        val idx = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, names.lastIndex)
        return names[idx]
    }

    fun shell(country: AppCountry): ShellStrings = when (country) {
        AppCountry.VN -> ShellStrings(
            more = "Thêm",
            back = "Quay lại",
            farmerFull = "Chế độ Nông dân",
            farmerShort = "Nông dân",
            engineerFull = "Chế độ Kỹ sư",
            engineerShort = "Kỹ sư",
            healthyBadge = { h, t -> "$h/$t cây khỏe" },
            soilMoistSub = "Đất đủ ẩm 64%",
            kpiWaterLabel = "Nước trong đất",
            kpiWaterSub = "Đủ ẩm · Chưa cần tưới",
            kpiSoilLabel = "Độ tốt của đất",
            kpiSoilOk = "Đất tốt · Không bị chua",
            kpiHealthLabel = "Sức khỏe vườn cây",
            kpiHealthUnit = "cây khỏe",
            kpiHealthNeeds = { n -> "$n cây cần bác ra thăm" },
            kpiHealthAllOk = "Toàn bộ cây đều khỏe",
            periodLabels = listOf("Hôm nay", "Tuần", "Tháng", "Vụ mùa"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "KHÔNG GIAN LÀM VIỆC",
                "TRỢ LÝ THÔNG MINH" to "TRỢ LÝ THÔNG MINH",
                "DỮ LIỆU THỰC ĐỊA" to "DỮ LIỆU THỰC ĐỊA",
                "THỰC ĐỊA & CÔNG CỤ" to "THỰC ĐỊA & CÔNG CỤ",
            ),
            routes = emptyMap(), // uses default Vietnamese NAV
        )
        AppCountry.US -> ShellStrings(
            more = "More",
            back = "Back",
            farmerFull = "Farmer Mode",
            farmerShort = "Farmer",
            engineerFull = "Engineer Mode",
            engineerShort = "Engineer",
            healthyBadge = { h, t -> "$h/$t healthy" },
            soilMoistSub = "Soil moisture 64%",
            kpiWaterLabel = "Soil Moisture",
            kpiWaterSub = "Optimal · No irrigation needed",
            kpiSoilLabel = "Soil Health",
            kpiSoilOk = "Balanced · Optimal pH",
            kpiHealthLabel = "Orchard Health",
            kpiHealthUnit = "healthy",
            kpiHealthNeeds = { n -> "$n trees need inspection" },
            kpiHealthAllOk = "All trees healthy",
            periodLabels = listOf("Today", "Week", "Month", "Season"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "WORKSPACE",
                "TRỢ LÝ THÔNG MINH" to "AI ASSISTANT",
                "DỮ LIỆU THỰC ĐỊA" to "FIELD DATA",
                "THỰC ĐỊA & CÔNG CỤ" to "FIELD & TOOLS",
            ),
            routes = mapOf(
                "overview" to ("Overview" to "My Orchard"),
                "map" to ("Map & GEE" to "Orchard Map"),
                "qr" to ("QR Scan" to "Scan Tree QR"),
                "issues" to ("Issues" to "Trees to Check"),
                "yield" to ("Yield" to "Harvest & Revenue"),
                "ai" to ("Ask AI" to "Ask Agronomist AI"),
                "workflow" to ("3D Workflow" to "6-Step & 7-Layer 3D"),
                "satellite" to ("Satellite" to "Satellite Imagery"),
                "weather" to ("Weather" to "Today's Weather"),
                "drone" to ("Drone VRA" to "Drone & Spraying"),
                "log" to ("Field Log" to "Log Activity"),
                "market" to ("Market" to "Market Prices"),
                "disease" to ("Disease" to "Disease Forecast"),
                "records" to ("History" to "Care History"),
                "imagery" to ("NDVI" to "Leaf Imagery"),
                "env" to ("Water & Soil" to "Irrigation & Soil"),
                "setup" to ("Settings" to "Orchard Settings"),
                "tree" to ("Tree Profile" to "Tree Profile"),
                "issue" to ("Issue Detail" to "Issue Detail"),
                "imageset" to ("Image Set" to "Image Set"),
                "ndvi" to ("NDVI Index" to "Leaf Health"),
            ),
        )
        AppCountry.ID -> ShellStrings(
            more = "Lainnya",
            back = "Kembali",
            farmerFull = "Mode Petani",
            farmerShort = "Petani",
            engineerFull = "Mode Insinyur",
            engineerShort = "Insinyur",
            healthyBadge = { h, t -> "$h/$t sehat" },
            soilMoistSub = "Kelembapan tanah 64%",
            kpiWaterLabel = "Kelembapan Tanah",
            kpiWaterSub = "Cukup · Belum perlu irigasi",
            kpiSoilLabel = "Kualitas Tanah",
            kpiSoilOk = "Subur · pH optimal",
            kpiHealthLabel = "Kesehatan Kebun",
            kpiHealthUnit = "pohon sehat",
            kpiHealthNeeds = { n -> "$n pohon perlu diperiksa" },
            kpiHealthAllOk = "Semua pohon sehat",
            periodLabels = listOf("Hari ini", "Minggu", "Bulan", "Musim"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "RUANG KERJA",
                "TRỢ LÝ THÔNG MINH" to "ASISTEN AI",
                "DỮ LIỆU THỰC ĐỊA" to "DATA LAPANGAN",
                "THỰC ĐỊA & CÔNG CỤ" to "ALAT & PENGATURAN",
            ),
            routes = mapOf(
                "overview" to ("Ringkasan" to "Kebun Saya"),
                "map" to ("Peta Kebun" to "Peta & GEE"),
                "qr" to ("Pindai QR" to "Pindai QR Pohon"),
                "issues" to ("Periksa" to "Pohon Bermasalah"),
                "yield" to ("Panen" to "Hasil & Pendapatan"),
                "ai" to ("Tanya AI" to "Tanya Ahli AI"),
                "workflow" to ("Alur 3D" to "Alur 6 Langkah & 3D"),
                "satellite" to ("Satelit" to "Citra Satelit"),
                "weather" to ("Cuaca" to "Cuaca Hari Ini"),
                "drone" to ("Drone" to "Drone & Semprot"),
                "log" to ("Catatan" to "Catat Aktivitas"),
                "market" to ("Harga" to "Harga Pasar"),
                "disease" to ("Penyakit" to "Prakiraan Penyakit"),
                "records" to ("Riwayat" to "Riwayat Perawatan"),
                "imagery" to ("NDVI" to "Citra Daun"),
                "env" to ("Air & Tanah" to "Irigasi & Tanah"),
                "setup" to ("Pengaturan" to "Pengaturan Kebun"),
                "tree" to ("Profil Pohon" to "Profil Pohon"),
                "issue" to ("Detail Masalah" to "Detail Masalah"),
                "imageset" to ("Kumpulan Foto" to "Kumpulan Foto"),
                "ndvi" to ("Indeks NDVI" to "Kesehatan Daun"),
            ),
        )
        AppCountry.TH -> ShellStrings(
            more = "เพิ่มเติม",
            back = "กลับ",
            farmerFull = "โหมดเกษตรกร",
            farmerShort = "เกษตรกร",
            engineerFull = "โหมดวิศวกร",
            engineerShort = "วิศวกร",
            healthyBadge = { h, t -> "แข็งแรง $h/$t" },
            soilMoistSub = "ความชื้นดิน 64%",
            kpiWaterLabel = "ความชื้นในดิน",
            kpiWaterSub = "เหมาะสม · ยังไม่ต้องรดน้ำ",
            kpiSoilLabel = "คุณภาพดิน",
            kpiSoilOk = "ดินดี · ค่า pH สมดุล",
            kpiHealthLabel = "สุขภาพสวน",
            kpiHealthUnit = "ต้นแข็งแรง",
            kpiHealthNeeds = { n -> "ต้องตรวจ $n ต้น" },
            kpiHealthAllOk = "ทุกต้นแข็งแรงดี",
            periodLabels = listOf("วันนี้", "สัปดาห์", "เดือน", "ฤดูกาล"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "พื้นที่ทำงาน",
                "TRỢ LÝ THÔNG MINH" to "ผู้ช่วย AI",
                "DỮ LIỆU THỰC ĐỊA" to "ข้อมูลภาคสนาม",
                "THỰC ĐỊA & CÔNG CỤ" to "เครื่องมือและตั้งค่า",
            ),
            routes = mapOf(
                "overview" to ("ภาพรวม" to "สวนของฉัน"),
                "map" to ("แผนที่สวน" to "แผนที่และ GEE"),
                "qr" to ("สแกน QR" to "สแกน QR ต้นไม้"),
                "issues" to ("ต้องตรวจ" to "ปัญหาและตรวจสวน"),
                "yield" to ("ผลผลิต" to "ผลผลิตและรายได้"),
                "ai" to ("ถาม AI" to "ถามผู้เชี่ยวชาญ AI"),
                "workflow" to ("เวิร์กโฟลว์ 3D" to "6 ขั้นตอน & 3D 7 ชั้น"),
                "satellite" to ("ดาวเทียม" to "ภาพถ่ายดาวเทียม"),
                "weather" to ("สภาพอากาศ" to "อากาศวันนี้"),
                "drone" to ("โดรน" to "โดรนและพ่นยา"),
                "log" to ("บันทึก" to "บันทึกกิจกรรม"),
                "market" to ("ราคาตลาด" to "ราคาตลาด"),
                "disease" to ("โรคพืช" to "พยากรณ์โรคพืช"),
                "records" to ("ประวัติ" to "ประวัติการดูแล"),
                "imagery" to ("NDVI" to "ภาพถ่ายใบพืช"),
                "env" to ("น้ำและดิน" to "น้ำและดิน"),
                "setup" to ("ตั้งค่า" to "ตั้งค่าสวน"),
                "tree" to ("ข้อมูลต้นไม้" to "ข้อมูลต้นไม้"),
                "issue" to ("รายละเอียดปัญหา" to "รายละเอียดปัญหา"),
                "imageset" to ("ชุดภาพถ่าย" to "ชุดภาพถ่าย"),
                "ndvi" to ("ดัชนี NDVI" to "สุขภาพใบพืช"),
            ),
        )
        AppCountry.JP -> ShellStrings(
            more = "その他",
            back = "戻る",
            farmerFull = "農家モード",
            farmerShort = "農家",
            engineerFull = "技術者モード",
            engineerShort = "技術者",
            healthyBadge = { h, t -> "健全 $h/$t" },
            soilMoistSub = "土壌水分 64%",
            kpiWaterLabel = "土壌水分",
            kpiWaterSub = "適正 · 灌水不要",
            kpiSoilLabel = "土壌状態",
            kpiSoilOk = "良好 · 適正pH",
            kpiHealthLabel = "園地の健康",
            kpiHealthUnit = "本が健全",
            kpiHealthNeeds = { n -> "要確認 $n 本" },
            kpiHealthAllOk = "すべて健全",
            periodLabels = listOf("今日", "週間", "月間", "シーズン"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "ワークスペース",
                "TRỢ LÝ THÔNG MINH" to "AIアシスタント",
                "DỮ LIỆU THỰC ĐỊA" to "フィールドデータ",
                "THỰC ĐỊA & CÔNG CỤ" to "ツール・設定",
            ),
            routes = mapOf(
                "overview" to ("概要" to "マイ農園"),
                "map" to ("マップ" to "農園マップ・GEE"),
                "qr" to ("QR読取" to "樹木QRスキャン"),
                "issues" to ("要確認" to "課題・点検"),
                "yield" to ("収穫" to "収量・収益"),
                "ai" to ("AI相談" to "AI農学者に相談"),
                "workflow" to ("3D解析" to "6ステップ＆3D 7層"),
                "satellite" to ("衛星" to "衛星画像解析"),
                "weather" to ("天気" to "今日の天気"),
                "drone" to ("ドローン" to "ドローン散布"),
                "log" to ("記録" to "作業記録"),
                "market" to ("市場価格" to "市場価格"),
                "disease" to ("病害予測" to "病害予測"),
                "records" to ("履歴" to "管理履歴"),
                "imagery" to ("NDVI" to "葉色画像"),
                "env" to ("水・土壌" to "水と土壌"),
                "setup" to ("設定" to "農園設定"),
                "tree" to ("樹木詳細" to "樹木詳細"),
                "issue" to ("課題詳細" to "課題詳細"),
                "imageset" to ("画像セット" to "画像セット"),
                "ndvi" to ("NDVI指数" to "葉の健康度"),
            ),
        )
        AppCountry.KR -> ShellStrings(
            more = "더보기",
            back = "뒤로",
            farmerFull = "농부 모드",
            farmerShort = "농부",
            engineerFull = "엔지니어 모드",
            engineerShort = "엔지니어",
            healthyBadge = { h, t -> "건강 $h/$t" },
            soilMoistSub = "토양 수분 64%",
            kpiWaterLabel = "토양 수분",
            kpiWaterSub = "적정 · 관수 불필요",
            kpiSoilLabel = "토양 상태",
            kpiSoilOk = "양호 · 적정 pH",
            kpiHealthLabel = "과수원 건강",
            kpiHealthUnit = "그루 건강",
            kpiHealthNeeds = { n -> "점검 필요 ${n}그루" },
            kpiHealthAllOk = "모든 나무 건강",
            periodLabels = listOf("오늘", "주간", "월간", "시즌"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "작업 공간",
                "TRỢ LÝ THÔNG MINH" to "AI 어시스턴트",
                "DỮ LIỆU THỰC ĐỊA" to "현장 데이터",
                "THỰC ĐỊA & CÔNG CỤ" to "현장 및 도구",
            ),
            routes = mapOf(
                "overview" to ("개요" to "내 과수원"),
                "map" to ("지도" to "과수원 지도"),
                "qr" to ("QR 스캔" to "나무 QR 스캔"),
                "issues" to ("점검" to "문제 및 점검"),
                "yield" to ("수확" to "수확량 및 수익"),
                "ai" to ("AI 질문" to "AI 농학자 질문"),
                "workflow" to ("3D 분석" to "6단계 & 3D 7층"),
                "satellite" to ("위성" to "위성 영상 분석"),
                "weather" to ("날씨" to "오늘 날씨"),
                "drone" to ("드론" to "드론 방제"),
                "log" to ("기록" to "작업 일지"),
                "market" to ("시세" to "시장 가격"),
                "disease" to ("병해 예측" to "병해충 예측"),
                "records" to ("이력" to "관리 이력"),
                "imagery" to ("NDVI" to "잎 영상 비교"),
                "env" to ("물·토양" to "관수 및 토양"),
                "setup" to ("설정" to "과수원 설정"),
                "tree" to ("나무 정보" to "나무 정보"),
                "issue" to ("문제 상세" to "문제 상세"),
                "imageset" to ("이미지 모음" to "이미지 모음"),
                "ndvi" to ("NDVI 지수" to "잎 건강도"),
            ),
        )
        AppCountry.BR -> ShellStrings(
            more = "Mais",
            back = "Voltar",
            farmerFull = "Modo Produtor",
            farmerShort = "Produtor",
            engineerFull = "Modo Engenheiro",
            engineerShort = "Engenheiro",
            healthyBadge = { h, t -> "$h/$t saudáveis" },
            soilMoistSub = "Umidade do solo 64%",
            kpiWaterLabel = "Umidade do Solo",
            kpiWaterSub = "Ideal · Sem irrigação",
            kpiSoilLabel = "Qualidade do Solo",
            kpiSoilOk = "Bom · pH equilibrado",
            kpiHealthLabel = "Saúde do Pomar",
            kpiHealthUnit = "saudáveis",
            kpiHealthNeeds = { n -> "$n árvores p/ revisar" },
            kpiHealthAllOk = "Todas saudáveis",
            periodLabels = listOf("Hoje", "Semana", "Mês", "Safra"),
            sections = mapOf(
                "KHÔNG GIAN LÀM VIỆC" to "ÁREA DE TRABALHO",
                "TRỢ LÝ THÔNG MINH" to "ASSISTENTE IA",
                "DỮ LIỆU THỰC ĐỊA" to "DADOS DE CAMPO",
                "THỰC ĐỊA & CÔNG CỤ" to "FERRAMENTAS",
            ),
            routes = mapOf(
                "overview" to ("Início" to "Meu Pomar"),
                "map" to ("Mapa" to "Mapa & GEE"),
                "qr" to ("Ler QR" to "Ler QR da Árvore"),
                "issues" to ("Alertas" to "Árvores p/ Revisar"),
                "yield" to ("Colheita" to "Produção & Receita"),
                "ai" to ("IA" to "Perguntar à IA"),
                "workflow" to ("Fluxo 3D" to "6 Etapas & 3D 7 Camadas"),
                "satellite" to ("Satélite" to "Imagens de Satélite"),
                "weather" to ("Clima" to "Clima Hoje"),
                "drone" to ("Drone" to "Drone & Pulverização"),
                "log" to ("Registrar" to "Registrar Atividade"),
                "market" to ("Mercado" to "Preços de Mercado"),
                "disease" to ("Doenças" to "Previsão de Doenças"),
                "records" to ("Histórico" to "Histórico de Manejo"),
                "imagery" to ("NDVI" to "Imagens das Folhas"),
                "env" to ("Água & Solo" to "Irrigação & Solo"),
                "setup" to ("Ajustes" to "Configurar Pomar"),
                "tree" to ("Perfil da Árvore" to "Perfil da Árvore"),
                "issue" to ("Detalhe do Alerta" to "Detalhe do Alerta"),
                "imageset" to ("Fotos" to "Conjunto de Fotos"),
                "ndvi" to ("Índice NDVI" to "Saúde Foliar"),
            ),
        )
    }
}

data class ShellStrings(
    val more: String,
    val back: String,
    val farmerFull: String,
    val farmerShort: String,
    val engineerFull: String,
    val engineerShort: String,
    val healthyBadge: (Int, Int) -> String,
    val soilMoistSub: String,
    val kpiWaterLabel: String,
    val kpiWaterSub: String,
    val kpiSoilLabel: String,
    val kpiSoilOk: String,
    val kpiHealthLabel: String,
    val kpiHealthUnit: String,
    val kpiHealthNeeds: (Int) -> String,
    val kpiHealthAllOk: String,
    val periodLabels: List<String>,
    val sections: Map<String, String>,
    val routes: Map<String, Pair<String, String>>, // route -> (short, full)
) {
    fun shortLabel(route: String, fallback: String): String = routes[route]?.first ?: fallback
    fun fullLabel(route: String, fallback: String): String = routes[route]?.second ?: fallback
    fun sectionLabel(sec: String): String = sections[sec] ?: sec
}

data class CardStrings(
    val quickCheck: (Int) -> String,
    val quickLog: String,
    val quickMap: String,
    val quickAi: String,
    val zoneA: String,
    val treeA04: String,
    val visitRoot: String,
    val zoneB: String,
    val treeB05: String,
    val recovering: String,
    val estHarvestTitle: (Int) -> String,
    val estHarvestVal: (String) -> String,
    val estRevVal: (String) -> String,
    val kpiYieldLabel: String,
    val kpiYieldUnit: String,
    val kpiYieldSub: String,
    val orchardToday: String,
    val treesToVisitToday: (Int) -> String,
    val allTreesHealthy: String,
    val tapTreeHint: String,
    val scanQrBtn: String,
    val noTreesToday: String,
    val treePrefix: String,
    val statusPrefix: String,
    val loginLabel: String,
    val searchHint: String,
)

fun AppStrings.cards(country: AppCountry): CardStrings = when (country) {
    AppCountry.VN -> CardStrings(
        quickCheck = { "Cây cần thăm ($it)" },
        quickLog = "Ghi sổ vườn",
        quickMap = "Bản đồ vườn",
        quickAi = "Hỏi Kỹ sư AI",
        zoneA = "Khu A · Gần lối vào",
        treeA04 = "Cây A-04 · Hơi vàng lá",
        visitRoot = "Cần thăm gốc · Chạm để xem",
        zoneB = "Khu B · Đất dốc",
        treeB05 = "Cây B-05 · Mới bón phân",
        recovering = "Đang hồi phục · Chạm để xem",
        estHarvestTitle = { "Ước thu ($it cây)" },
        estHarvestVal = { "Ước thu $it" },
        estRevVal = { "Thu về khoảng $it" },
        kpiYieldLabel = "Ước thu vụ này",
        kpiYieldUnit = "tấn",
        kpiYieldSub = "Theo số trái trên cây",
        orchardToday = "Vườn cây hôm nay",
        treesToVisitToday = { "Cây cần ra thăm hôm nay ($it cây)" },
        allTreesHealthy = "Tất cả cây trong vườn đều đang khỏe mạnh.",
        tapTreeHint = "Chạm vào từng cây để xem vị trí và cách chăm sóc",
        scanQrBtn = "Quét QR gốc cây",
        noTreesToday = "Hôm nay không có cây nào cần xử lý.",
        treePrefix = "Cây",
        statusPrefix = "Tình trạng:",
        loginLabel = "Đăng nhập",
        searchHint = "Tìm cây, khu vực, tính năng...",
    )
    AppCountry.US -> CardStrings(
        quickCheck = { "Trees to Check ($it)" },
        quickLog = "Field Log",
        quickMap = "Orchard Map",
        quickAi = "Ask AI",
        zoneA = "Zone A · Near Gate",
        treeA04 = "Tree A-04 · Mild Chlorosis",
        visitRoot = "Root check needed · Tap to view",
        zoneB = "Zone B · Slope",
        treeB05 = "Tree B-05 · Recently Fertilized",
        recovering = "Recovering · Tap to view",
        estHarvestTitle = { "Est. Yield ($it trees)" },
        estHarvestVal = { "Est. $it" },
        estRevVal = { "Est. revenue $it" },
        kpiYieldLabel = "Season Est. Yield",
        kpiYieldUnit = "tons",
        kpiYieldSub = "Based on fruit count",
        orchardToday = "Orchard Today",
        treesToVisitToday = { "Trees to Inspect Today ($it)" },
        allTreesHealthy = "All trees in the orchard are healthy.",
        tapTreeHint = "Tap any tree to view location and care steps",
        scanQrBtn = "Scan Tree QR",
        noTreesToday = "No trees need attention today.",
        treePrefix = "Tree",
        statusPrefix = "Status:",
        loginLabel = "Sign In",
        searchHint = "Search trees, zones, tools...",
    )
    AppCountry.ID -> CardStrings(
        quickCheck = { "Perlu Periksa ($it)" },
        quickLog = "Catat Kebun",
        quickMap = "Peta Kebun",
        quickAi = "Tanya AI",
        zoneA = "Zona A · Dekat Pintu",
        treeA04 = "Pohon A-04 · Daun Menguning",
        visitRoot = "Perlu cek akar · Ketuk untuk lihat",
        zoneB = "Zona B · Lahan Miring",
        treeB05 = "Pohon B-05 · Baru Dipupuk",
        recovering = "Memulih · Ketuk untuk lihat",
        estHarvestTitle = { "Est. Panen ($it pohon)" },
        estHarvestVal = { "Est. $it" },
        estRevVal = { "Perkiraan hasil $it" },
        kpiYieldLabel = "Perkiraan Panen",
        kpiYieldUnit = "ton",
        kpiYieldSub = "Berdasarkan jumlah buah",
        orchardToday = "Kebun Hari Ini",
        treesToVisitToday = { "Pohon Perlu Diperiksa Hari Ini ($it)" },
        allTreesHealthy = "Semua pohon di kebun dalam kondisi sehat.",
        tapTreeHint = "Ketuk pohon untuk melihat lokasi dan perawatan",
        scanQrBtn = "Pindai QR Pohon",
        noTreesToday = "Tidak ada pohon yang perlu ditangani hari ini.",
        treePrefix = "Pohon",
        statusPrefix = "Kondisi:",
        loginLabel = "Masuk",
        searchHint = "Cari pohon, zona, fitur...",
    )
    AppCountry.TH -> CardStrings(
        quickCheck = { "ต้นต้องตรวจ ($it)" },
        quickLog = "บันทึกสวน",
        quickMap = "แผนที่สวน",
        quickAi = "ถาม AI",
        zoneA = "โซน A · ใกล้ทางเข้า",
        treeA04 = "ต้น A-04 · ใบเริ่มเหลือง",
        visitRoot = "ต้องตรวจโคนต้น · แตะเพื่อดู",
        zoneB = "โซน B · พื้นที่ลาด",
        treeB05 = "ต้น B-05 · เพิ่งใส่ปุ๋ย",
        recovering = "กำลังฟื้นตัว · แตะเพื่อดู",
        estHarvestTitle = { "คาดการณ์ผลผลิต ($it ต้น)" },
        estHarvestVal = { "คาดการณ์ $it" },
        estRevVal = { "รายได้ประมาณ $it" },
        kpiYieldLabel = "ผลผลิตคาดการณ์",
        kpiYieldUnit = "ตัน",
        kpiYieldSub = "คำนวณจากจำนวนผล",
        orchardToday = "สวนวันนี้",
        treesToVisitToday = { "ต้นไม้ที่ต้องตรวจวันนี้ ($it ต้น)" },
        allTreesHealthy = "ต้นไม้ทุกต้นในสวนแข็งแรงดี",
        tapTreeHint = "แตะที่ต้นไม้เพื่อดูตำแหน่งและวิธีดูแล",
        scanQrBtn = "สแกน QR ต้นไม้",
        noTreesToday = "วันนี้ไม่มีต้นไม้ที่ต้องจัดการ",
        treePrefix = "ต้น",
        statusPrefix = "สถานะ:",
        loginLabel = "เข้าสู่ระบบ",
        searchHint = "ค้นหาต้นไม้ โซน เครื่องมือ...",
    )
    AppCountry.JP -> CardStrings(
        quickCheck = { "要確認の木 ($it)" },
        quickLog = "作業記録",
        quickMap = "農園マップ",
        quickAi = "AIに相談",
        zoneA = "エリアA · 入口付近",
        treeA04 = "木 A-04 · 葉の黄変",
        visitRoot = "根元点検が必要 · タップで表示",
        zoneB = "エリアB · 傾斜地",
        treeB05 = "木 B-05 · 施肥済み",
        recovering = "回復中 · タップで表示",
        estHarvestTitle = { "予想収量 ($it 本)" },
        estHarvestVal = { "予想 $it" },
        estRevVal = { "予想収益 $it" },
        kpiYieldLabel = "今期の予想収量",
        kpiYieldUnit = "トン",
        kpiYieldSub = "着果数に基づく推計",
        orchardToday = "今日の農園",
        treesToVisitToday = { "今日点検が必要な木 ($it 本)" },
        allTreesHealthy = "農園のすべての木が健全です。",
        tapTreeHint = "木をタップして位置と管理方法を確認",
        scanQrBtn = "QRをスキャン",
        noTreesToday = "今日対応が必要な木はありません。",
        treePrefix = "木",
        statusPrefix = "状態:",
        loginLabel = "ログイン",
        searchHint = "木・エリア・機能を検索...",
    )
    AppCountry.KR -> CardStrings(
        quickCheck = { "점검 필요 ($it)" },
        quickLog = "작업 기록",
        quickMap = "과수원 지도",
        quickAi = "AI 질문",
        zoneA = "A구역 · 입구 근처",
        treeA04 = "나무 A-04 · 잎 황변",
        visitRoot = "뿌리 점검 필요 · 탭하여 보기",
        zoneB = "B구역 · 경사지",
        treeB05 = "나무 B-05 · 시비 완료",
        recovering = "회복 중 · 탭하여 보기",
        estHarvestTitle = { "예상 수확량 (${it}그루)" },
        estHarvestVal = { "예상 $it" },
        estRevVal = { "예상 수익 $it" },
        kpiYieldLabel = "예상 수확량",
        kpiYieldUnit = "톤",
        kpiYieldSub = "열매 수 기반 추정",
        orchardToday = "오늘의 과수원",
        treesToVisitToday = { "오늘 점검할 나무 (${it}그루)" },
        allTreesHealthy = "과수원의 모든 나무가 건강합니다.",
        tapTreeHint = "나무를 탭하여 위치와 관리 방법을 확인하세요",
        scanQrBtn = "나무 QR 스캔",
        noTreesToday = "오늘 조치할 나무가 없습니다.",
        treePrefix = "나무",
        statusPrefix = "상태:",
        loginLabel = "로그인",
        searchHint = "나무, 구역, 기능 검색...",
    )
    AppCountry.BR -> CardStrings(
        quickCheck = { "Revisar Árvores ($it)" },
        quickLog = "Registrar",
        quickMap = "Mapa do Pomar",
        quickAi = "Perguntar IA",
        zoneA = "Zona A · Perto da Entrada",
        treeA04 = "Árvore A-04 · Folha Amarelada",
        visitRoot = "Verificar raiz · Toque para ver",
        zoneB = "Zona B · Encosta",
        treeB05 = "Árvore B-05 · Recém-adubada",
        recovering = "Em recuperação · Toque para ver",
        estHarvestTitle = { "Colheita Est. ($it árv.)" },
        estHarvestVal = { "Est. $it" },
        estRevVal = { "Receita est. $it" },
        kpiYieldLabel = "Colheita Estimada",
        kpiYieldUnit = "ton",
        kpiYieldSub = "Com base nos frutos",
        orchardToday = "Pomar Hoje",
        treesToVisitToday = { "Árvores para Revisar Hoje ($it)" },
        allTreesHealthy = "Todas as árvores do pomar estão saudáveis.",
        tapTreeHint = "Toque em uma árvore para ver local e manejo",
        scanQrBtn = "Ler QR da Árvore",
        noTreesToday = "Nenhuma árvore precisa de ação hoje.",
        treePrefix = "Árvore",
        statusPrefix = "Status:",
        loginLabel = "Entrar",
        searchHint = "Buscar árvores, zonas, ferramentas...",
    )
}

@Composable
fun CountryPickerDialog(
    current: AppCountry,
    onSelect: (AppCountry) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = AppStrings.forCountry(current)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D2231),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE2E8F0),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.pickerTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(s.pickerSubtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.72f))
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AppCountry.entries) { country ->
                    val selected = country == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color(0xFF1D4E4E) else Color.White.copy(alpha = 0.06f))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) Color(0xFF38EF7D) else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelect(country) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(country.flag, fontSize = 24.sp)
                            Column {
                                Text(country.countryName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    "${country.languageName} · ${country.sampleLocation}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.72f),
                                )
                            }
                        }
                        if (selected) {
                            Text("✓", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38EF7D))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.closeLabel, color = Color(0xFF38EF7D), fontWeight = FontWeight.Bold)
            }
        },
    )
}
