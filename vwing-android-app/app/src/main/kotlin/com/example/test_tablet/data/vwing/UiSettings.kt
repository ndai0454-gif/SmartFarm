package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Chu kỳ thanh toán gói tài khoản:
 * - [MONTHLY]: Thanh toán theo Tháng (`Month`)
 * - [YEARLY]: Thanh toán theo Năm (`Year` — tiết kiệm ~2 tháng cước)
 */
enum class BillingCycle(
    val id: String,
    val labelVi: String,
    val shortVi: String,
    val unitVi: String,
) {
    MONTHLY(
        id = "monthly",
        labelVi = "Theo Tháng (Month)",
        shortVi = "Month",
        unitVi = "/ tháng",
    ),
    YEARLY(
        id = "yearly",
        labelVi = "Theo Năm (Year · Ưu đãi)",
        shortVi = "Year",
        unitVi = "/ năm",
    );

    companion object {
        fun fromId(raw: String?): BillingCycle =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: MONTHLY
    }
}

/**
 * Hai gói tài khoản chính thức của VWing SmartFarm:
 * - [FREE] (Basic Account): `149.000 đ / tháng` | `1.499.000 đ / năm`
 *   Dành cho hộ nông dân cá nhân (giao diện Trắng sáng ban ngày, thời tiết 6 ngày,
 *   bản đồ cây, lịch thăm vườn, thu hoạch cơ bản, tối đa 100 cây QR & 3 câu hỏi AI/ngày).
 * - [ENTERPRISE] (Pro Account): `499.000 đ / tháng` | `4.999.000 đ / năm`
 *   Dành cho trang trại lớn, hợp tác xã & kỹ sư nông nghiệp (giao diện Aurora Liquid-Glass,
 *   mở khóa Vệ tinh GEE, Drone VRA, Địa tầng 3D, Đồng bộ Cloud đa nhân sự, Nhập/Xuất CAD/GIS & không giới hạn).
 */
enum class PlanTier(
    val id: String,
    val itemName: String,
    val label: String,
    val shortBadge: String,
    val monthlyPriceVi: String,
    val yearlyPriceVi: String,
    val yearlySavingsVi: String,
    val tagline: String,
) {
    FREE(
        id = "free",
        itemName = "Basic Account",
        label = "Basic Account (Nông hộ)",
        shortBadge = "Basic",
        monthlyPriceVi = "149.000 đ",
        yearlyPriceVi = "1.499.000 đ",
        yearlySavingsVi = "Tiết kiệm 289.000 đ/năm (~16%)",
        tagline = "Quản lý vườn chuẩn mực · Tối đa 100 cây QR · Hỏi AI 3 câu/ngày",
    ),
    ENTERPRISE(
        id = "enterprise",
        itemName = "Pro Account",
        label = "Pro Account (Trang trại & Kỹ sư)",
        shortBadge = "Pro",
        monthlyPriceVi = "499.000 đ",
        yearlyPriceVi = "4.999.000 đ",
        yearlySavingsVi = "Tiết kiệm 989.000 đ/năm (~17%)",
        tagline = "Mở khóa Vệ tinh GEE, Drone VRA, Địa tầng 3D, Đồng bộ nhóm & Không giới hạn",
    );

    fun priceFor(cycle: BillingCycle): String =
        if (cycle == BillingCycle.YEARLY) yearlyPriceVi else monthlyPriceVi

    companion object {
        fun fromId(raw: String?): PlanTier {
            val k = raw?.trim()?.lowercase() ?: return FREE
            return when (k) {
                "enterprise", "pro", "pro_account" -> ENTERPRISE
                "free", "basic", "basic_account" -> FREE
                else -> entries.firstOrNull { it.id.equals(k, ignoreCase = true) } ?: FREE
            }
        }
    }
}

/**
 * Danh mục các nhóm tính năng có thể Bật/Tắt (Feature Flags) theo gói Basic Account / Pro Account
 * hoặc bật/tắt riêng lẻ từng mô-đun trong phần Cài đặt Gói cước.
 */
enum class VFeature(
    val key: String,
    val title: String,
    val summary: String,
    val freeLimitNote: String,
    val defaultInFree: Boolean = false,
) {
    ENGINEER_MODE(
        key = "engineer_mode",
        title = "Chế độ Kỹ sư & Phân tích GIS",
        summary = "Mở khóa giao diện Kỹ sư với đầy đủ chỉ số quang phổ, lớp bản đồ địa hình và công cụ đo đạc.",
        freeLimitNote = "Basic Account sử dụng Chế độ Nông dân tối giản",
    ),
    SATELLITE_GEE(
        key = "satellite_gee",
        title = "Vệ tinh Google Earth Engine (7 Chỉ số)",
        summary = "Theo dõi sức khỏe lá (NDVI), thiếu nước (NDMI) và lịch sử hạn hán 26 năm từ vệ tinh.",
        freeLimitNote = "Chỉ có trong Pro Account (499.000 đ/tháng)",
    ),
    DRONE_VRA(
        key = "drone_vra",
        title = "Bay Drone Đa phổ & Phun thuốc VRA",
        summary = "Phân lớp tán cây OBIA và tạo bản đồ phun thuốc biến thiên tiết kiệm 30–40% phân thuốc.",
        freeLimitNote = "Chỉ có trong Pro Account (499.000 đ/tháng)",
    ),
    WORKFLOW_3D(
        key = "workflow_3d",
        title = "Quy trình 6 Bước & Địa tầng 3D 7 Tầng",
        summary = "Mô phỏng địa hình 3D, dòng chảy thoát nước, đếm tán tự động và xuất bản vẽ kỹ thuật.",
        freeLimitNote = "Chỉ có trong Pro Account (499.000 đ/tháng)",
    ),
    BULK_IMPORT_EXPORT(
        key = "bulk_import_export",
        title = "Nhập CSV hàng loạt & Xuất GIS/GeoJSON/KML",
        summary = "Nhập hàng nghìn cây từ Excel/CSV và xuất dữ liệu tọa độ chuẩn GIS cho kỹ sư.",
        freeLimitNote = "Basic Account hỗ trợ thêm từng cây & sao lưu .zip",
    ),
    CLOUD_TEAM(
        key = "cloud_team",
        title = "Đồng bộ Cloud & Phân quyền Nhân công",
        summary = "Mời nhân viên/phi công bằng QR, phân quyền theo khu vực, thời hạn và nhật ký kiểm toán.",
        freeLimitNote = "Basic Account lưu trữ Offline + SĐT cá nhân",
    ),
    UNLIMITED_TREES(
        key = "unlimited_trees",
        title = "Quản lý trang trại quy mô lớn (>100 cây)",
        summary = "Không giới hạn số lượng cây gắn mã QR, số khu canh tác và diện tích vườn.",
        freeLimitNote = "Basic Account tối đa 100 cây",
    ),
    UNLIMITED_AI(
        key = "unlimited_ai",
        title = "Hỏi Kỹ sư AI Gemini không giới hạn",
        summary = "Hỏi đáp bệnh cây, tra cứu thuốc bảo vệ thực vật và phác đồ chăm sóc 24/7 không giới hạn lượt.",
        freeLimitNote = "Basic Account tối đa 3 câu hỏi/ngày",
    );

    companion object {
        const val FREE_MAX_TREES = 100
        const val FREE_MAX_AI_PER_DAY = 3

        fun forRoute(route: String): VFeature? = when (route.substringBefore('|')) {
            "satellite", "satelliteAdvanced", "imagery", "ndvi" -> SATELLITE_GEE
            "drone" -> DRONE_VRA
            "workflow" -> WORKFLOW_3D
            else -> null
        }
    }
}

/**
 * Tùy chọn hiển thị & cấu hình Gói cước (Basic Account / Pro Account + Chu kỳ Month/Year + Feature Flags).
 */
data class UiSettings(
    val simpleMode: Boolean = true,
    val planTier: PlanTier = PlanTier.FREE,
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val enabledFeatures: Set<String> = emptySet(),
    val disabledFeatures: Set<String> = emptySet(),
    val aiQuestionsToday: Int = 0,
    val aiQuotaDate: String = "",
) {
    fun isFeatureEnabled(f: VFeature): Boolean {
        if (f.key in disabledFeatures) return false
        if (f.key in enabledFeatures) return true
        return if (planTier == PlanTier.ENTERPRISE) true else f.defaultInFree
    }

    fun toggleFeatureOverride(f: VFeature, enabled: Boolean): UiSettings {
        val nextEnabled = enabledFeatures.toMutableSet()
        val nextDisabled = disabledFeatures.toMutableSet()
        if (enabled) {
            nextEnabled.add(f.key)
            nextDisabled.remove(f.key)
        } else {
            nextDisabled.add(f.key)
            nextEnabled.remove(f.key)
        }
        return copy(enabledFeatures = nextEnabled, disabledFeatures = nextDisabled)
    }

    fun remainingAiQuestions(todayStr: String): Int {
        if (isFeatureEnabled(VFeature.UNLIMITED_AI)) return Int.MAX_VALUE
        val used = if (aiQuotaDate == todayStr) aiQuestionsToday else 0
        return (VFeature.FREE_MAX_AI_PER_DAY - used).coerceAtLeast(0)
    }

    fun recordAiQuestion(todayStr: String): UiSettings {
        val used = if (aiQuotaDate == todayStr) aiQuestionsToday + 1 else 1
        return copy(aiQuestionsToday = used, aiQuotaDate = todayStr)
    }
}

class UiSettingsStore(dir: File) {
    private val file = File(dir, "vwing_ui_settings.json")

    fun load(): UiSettings = try {
        val o = JSONObject(file.readText())
        val enabled = mutableSetOf<String>()
        o.optJSONArray("enabledFeatures")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { enabled.add(it) }
        }
        val disabled = mutableSetOf<String>()
        o.optJSONArray("disabledFeatures")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { disabled.add(it) }
        }
        UiSettings(
            simpleMode = if (o.has("simpleMode")) o.getBoolean("simpleMode") else true,
            planTier = PlanTier.fromId(o.optString("planTier", "free")),
            billingCycle = BillingCycle.fromId(o.optString("billingCycle", "monthly")),
            enabledFeatures = enabled,
            disabledFeatures = disabled,
            aiQuestionsToday = o.optInt("aiQuestionsToday", 0),
            aiQuotaDate = o.optString("aiQuotaDate", ""),
        )
    } catch (_: Exception) {
        UiSettings()
    }

    fun save(s: UiSettings) {
        val tmp = File(file.parentFile, "vwing_ui_settings.json.tmp")
        val o = JSONObject()
            .put("simpleMode", s.simpleMode)
            .put("planTier", s.planTier.id)
            .put("billingCycle", s.billingCycle.id)
            .put("enabledFeatures", JSONArray(s.enabledFeatures.toList()))
            .put("disabledFeatures", JSONArray(s.disabledFeatures.toList()))
            .put("aiQuestionsToday", s.aiQuestionsToday)
            .put("aiQuotaDate", s.aiQuotaDate)
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
