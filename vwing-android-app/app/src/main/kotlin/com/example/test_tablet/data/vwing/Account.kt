package com.example.test_tablet.data.vwing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Lỗi nghiệp vụ từ máy chủ, [message] đã là câu tiếng Việt để hiện cho người dùng. */
class AccountException(message: String, val code: Int = 0) : Exception(message)

class ApiResp(val code: Int, val body: ByteArray) {
    val text get() = body.toString(Charsets.UTF_8)
    val json: JSONObject? get() = runCatching { JSONObject(text) }.getOrNull()
}

/** Gọi HTTP chung cho đồng bộ và tài khoản. Chạy trên luồng nền. */
object Api {
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 30_000

    fun call(baseUrl: String, token: String?, method: String, path: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap()): ApiResp {
        val conn = URL(baseUrl.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method; conn.connectTimeout = CONNECT_MS; conn.readTimeout = READ_MS
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) { conn.doOutput = true; conn.setFixedLengthStreamingMode(body.size); conn.outputStream.use { it.write(body) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return ApiResp(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally { conn.disconnect() }
    }
}

/**
 * Vai trò của người đang dùng trên vườn đang đồng bộ. Đây CHỈ để ẩn/khoá nút cho gọn; quyền thật do máy chủ kiểm tra
 * (xem vwing-sync-server/rbac.js), nên sửa app cũng không vượt quyền được.
 * Chưa nối máy chủ (role rỗng) = dùng riêng trên máy: đủ quyền với dữ liệu của chính mình.
 */
object Access {
    var role by mutableStateOf("")

    private fun any(vararg r: String) = role.isBlank() || role == "admin" || role in r
    val signedIn get() = role.isNotBlank()
    val canEditGarden get() = any("owner", "manager")
    val canWriteSales get() = any("owner")
    val canSeeSales get() = any("owner", "manager", "agronomist")
    val canDelete get() = any("owner", "manager")
    /** Người nhận việc (nhân viên, phi công) mới nhận được đơn việc mở; chủ/quản lý giao việc nên không cần. */
    val canClaimJobs get() = role == "worker" || role == "pilot"
    val canManageMembers get() = role == "admin" || role == "owner" || role == "manager"
    val isOwner get() = role == "owner" || role == "admin"

    fun label(r: String) = when (r) {
        "owner" -> "Chủ vườn"; "manager" -> "Quản lý"; "worker" -> "Nhân viên"; "pilot" -> "Phi công"; "agronomist" -> "Chuyên gia"; "admin" -> "Quản trị"; else -> r
    }
    val ROLES = listOf("manager", "worker", "pilot", "agronomist")
    fun describe(r: String) = when (r) {
        "manager" -> "Điều hành hằng ngày, giao việc, xem sản lượng"
        "worker" -> "Ghi nhật ký, ảnh, quét QR; không thấy doanh thu"
        "pilot" -> "Nhập log bay, ảnh drone; theo khu và thời hạn được cấp"
        "agronomist" -> "Xem hồ sơ, ghi chú và khuyến nghị; không sửa cây"
        else -> ""
    }
}

data class GardenRef(val key: String, val name: String, val role: String, val zones: List<String>, val expiresAt: Long?)
data class Session(val token: String, val userName: String, val phone: String)
data class MemberRow(val userId: String, val name: String, val phone: String, val role: String, val zones: List<String>, val expiresAt: Long?, val expired: Boolean)
data class InviteRow(val id: String, val role: String, val zones: List<String>, val memberDays: Int?, val phone: String?, val expiresAt: Long, val code: String? = null)
data class AuditRow(val at: Long, val by: String, val role: String, val action: String, val detail: String)
data class PublicField(val id: String, val label: String)
data class PublicConfig(val enabled: Boolean, val fields: Set<String>, val story: String, val allFields: List<PublicField>, val baseUrl: String)

object Account {
    private fun fail(r: ApiResp): Nothing {
        val msg = r.json?.optString("error")?.takeIf { it.isNotBlank() }
        throw AccountException(
            when {
                r.code == 401 && msg == null -> "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."
                msg != null -> msg
                else -> "Máy chủ trả lỗi ${r.code}."
            }, r.code,
        )
    }

    /** Gọi và trả JSON; ném [AccountException] khi máy chủ từ chối, hoặc khi không kết nối được. */
    private fun req(cfg: SyncConfig, method: String, path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        SyncClient.urlProblem(cfg.url)?.let { throw AccountException(it) }
        val r = try {
            Api.call(cfg.url, cfg.token, method, path, body?.toString()?.toByteArray(), headers + if (body != null) mapOf("Content-Type" to "application/json") else emptyMap())
        } catch (e: IOException) { throw AccountException("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}") }
        if (r.code !in 200..299) fail(r)
        return r.json ?: JSONObject()
    }

    private fun zonesOf(o: JSONObject?): List<String> = o?.optJSONObject("scope")?.optJSONArray("zones")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    private fun lngOrNull(o: JSONObject, k: String): Long? = if (o.isNull(k) || !o.has(k)) null else o.getLong(k)

    fun normalizeUrl(u: String) = u.trim().trimEnd('/')

    private fun sessionFrom(o: JSONObject): Session {
        val u = o.getJSONObject("user")
        return Session(o.getString("token"), u.getString("name"), u.getString("phone"))
    }

    /** Máy chủ có bật xác thực SMS (OTP) không. Lỗi mạng coi như chưa bật để form vẫn dùng được. */
    fun otpEnabled(url: String): Boolean = try {
        SyncClient.urlProblem(url) == null && Api.call(normalizeUrl(url), null, "GET", "/v1/health").json?.optBoolean("otp") == true
    } catch (_: Exception) { false }

    /** Gửi mã SMS. purpose = register | login | reset. Trả số giây phải đợi trước khi gửi lại. */
    fun requestOtp(url: String, phone: String, purpose: String): Int =
        req(SyncConfig(url = normalizeUrl(url)), "POST", "/v1/auth/otp/request", JSONObject().put("phone", phone).put("purpose", purpose)).optInt("resendAfter", 60)

    fun register(url: String, phone: String, name: String, password: String, otp: String? = null): Session =
        sessionFrom(req(SyncConfig(url = normalizeUrl(url)), "POST", "/v1/auth/register",
            JSONObject().put("phone", phone).put("name", name).put("password", password).also { if (otp != null) it.put("otp", otp) }))

    fun loginOtp(url: String, phone: String, code: String): Session =
        sessionFrom(req(SyncConfig(url = normalizeUrl(url)), "POST", "/v1/auth/otp/login", JSONObject().put("phone", phone).put("code", code)))

    fun resetPassword(url: String, phone: String, code: String, newPassword: String): Session =
        sessionFrom(req(SyncConfig(url = normalizeUrl(url)), "POST", "/v1/auth/password/reset", JSONObject().put("phone", phone).put("code", code).put("password", newPassword)))

    fun login(url: String, phone: String, password: String): Session =
        sessionFrom(req(SyncConfig(url = normalizeUrl(url)), "POST", "/v1/auth/login", JSONObject().put("phone", phone).put("password", password)))

    fun logout(cfg: SyncConfig) { runCatching { req(cfg, "POST", "/v1/auth/logout") } }

    private fun gardenRef(o: JSONObject) = GardenRef(o.getString("key"), o.optString("name", o.getString("key")), o.optString("role"), zonesOf(o), lngOrNull(o, "expiresAt"))

    fun me(cfg: SyncConfig): List<GardenRef> {
        val o = req(cfg, "GET", "/v1/me")
        val a = o.optJSONArray("gardens") ?: JSONArray()
        return (0 until a.length()).map { gardenRef(a.getJSONObject(it)) }
    }

    fun createGarden(cfg: SyncConfig, key: String, name: String): GardenRef =
        gardenRef(req(cfg, "POST", "/v1/gardens", JSONObject().put("key", key).put("name", name)).getJSONObject("garden"))

    fun acceptInvite(cfg: SyncConfig, code: String): GardenRef =
        gardenRef(req(cfg, "POST", "/v1/invites/accept", JSONObject().put("code", code)).getJSONObject("garden"))

    fun deleteGarden(cfg: SyncConfig) { req(cfg, "DELETE", "/v1/gardens/${cfg.gardenKey}", headers = mapOf("X-Confirm" to cfg.gardenKey)) }

    private fun g(cfg: SyncConfig, tail: String) = "/v1/gardens/${cfg.gardenKey}$tail"

    fun members(cfg: SyncConfig): List<MemberRow> {
        val a = req(cfg, "GET", g(cfg, "/members")).optJSONArray("members") ?: return emptyList()
        return (0 until a.length()).map {
            val m = a.getJSONObject(it)
            MemberRow(m.getString("userId"), m.optString("name"), m.optString("phone"), m.optString("role"), zonesOf(m), lngOrNull(m, "expiresAt"), m.optBoolean("expired"))
        }
    }

    /** Chuyển quyền chủ cho một thành viên đang hoạt động; phải nhập lại mật khẩu. Chủ cũ trở thành quản lý. */
    fun transferOwner(cfg: SyncConfig, userId: String, password: String) {
        req(cfg, "POST", g(cfg, "/transfer"), JSONObject().put("userId", userId).put("password", password))
    }

    fun revokeMember(cfg: SyncConfig, userId: String) { req(cfg, "DELETE", g(cfg, "/members/$userId")) }

    fun updateMember(cfg: SyncConfig, userId: String, role: String? = null, zones: List<String>? = null, expiresAt: Long? = null, clearExpiry: Boolean = false) {
        val b = JSONObject()
        role?.let { b.put("role", it) }
        zones?.let { b.put("scope", JSONObject().put("zones", JSONArray(it))) }
        if (clearExpiry) b.put("expiresAt", JSONObject.NULL) else expiresAt?.let { b.put("expiresAt", it) }
        req(cfg, "PATCH", g(cfg, "/members/$userId"), b)
    }

    private fun inviteRow(o: JSONObject) = InviteRow(
        o.getString("id"), o.optString("role"), zonesOf(o), if (o.isNull("memberDays") || !o.has("memberDays")) null else o.getInt("memberDays"),
        o.optString("phone").takeIf { it.isNotBlank() && it != "null" }, o.getLong("expiresAt"), o.optString("code").takeIf { it.isNotBlank() },
    )

    fun invite(cfg: SyncConfig, role: String, zones: List<String>, memberDays: Int?, phone: String): InviteRow {
        val b = JSONObject().put("role", role)
        if (zones.isNotEmpty()) b.put("scope", JSONObject().put("zones", JSONArray(zones)))
        if (memberDays != null) b.put("memberDays", memberDays)
        if (phone.isNotBlank()) b.put("phone", phone)
        return inviteRow(req(cfg, "POST", g(cfg, "/invites"), b))
    }

    fun invites(cfg: SyncConfig): List<InviteRow> {
        val a = req(cfg, "GET", g(cfg, "/invites")).optJSONArray("invites") ?: return emptyList()
        return (0 until a.length()).map { inviteRow(a.getJSONObject(it)) }
    }

    fun revokeInvite(cfg: SyncConfig, id: String) { req(cfg, "DELETE", g(cfg, "/invites/$id")) }

    fun audit(cfg: SyncConfig, limit: Int = 100): List<AuditRow> {
        val a = req(cfg, "GET", g(cfg, "/audit?limit=$limit")).optJSONArray("entries") ?: return emptyList()
        return (0 until a.length()).map {
            val e = a.getJSONObject(it)
            AuditRow(e.getLong("at"), e.optString("by"), e.optString("role"), e.optString("action"), AuditText.describe(e.optString("action"), e.optJSONObject("detail")))
        }
    }

    fun publicConfig(cfg: SyncConfig): PublicConfig {
        val o = req(cfg, "GET", g(cfg, "/public"))
        val p = o.optJSONObject("public") ?: JSONObject()
        val f = p.optJSONArray("fields") ?: JSONArray()
        val all = o.optJSONArray("allFields") ?: JSONArray()
        return PublicConfig(
            p.optBoolean("enabled"), (0 until f.length()).map { f.getString(it) }.toSet(), p.optString("story"),
            (0 until all.length()).map { all.getJSONObject(it).let { x -> PublicField(x.getString("id"), x.getString("label")) } }, o.optString("baseUrl"),
        )
    }

    fun savePublicConfig(cfg: SyncConfig, enabled: Boolean, fields: Set<String>, story: String) {
        req(cfg, "PUT", g(cfg, "/public"), JSONObject().put("enabled", enabled).put("fields", JSONArray(fields.toList())).put("story", story))
    }
}

data class LivePerson(val userId: String, val name: String, val role: String, val lat: Double, val lng: Double, val accM: Double?, val at: Long, val until: Long)
data class LiveState(val people: List<LivePerson>, val sharingUntil: Long?)

/** Chia sẻ vị trí trực tiếp: người dùng tự bật, có hẹn giờ; máy chủ chỉ giữ trong bộ nhớ và chỉ chủ/quản lý xem được người khác. */
object LiveShare {
    private fun g(cfg: SyncConfig, tail: String) = "/v1/gardens/${cfg.gardenKey}/live$tail"
    private fun call(cfg: SyncConfig, method: String, path: String, body: JSONObject? = null): JSONObject {
        SyncClient.urlProblem(cfg.url)?.let { throw AccountException(it) }
        val r = try { Api.call(cfg.url, cfg.token, method, path, body?.toString()?.toByteArray(), if (body != null) mapOf("Content-Type" to "application/json") else emptyMap()) }
        catch (e: IOException) { throw AccountException("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}") }
        if (r.code !in 200..299) throw AccountException(r.json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Máy chủ trả lỗi ${r.code}.", r.code)
        return r.json ?: JSONObject()
    }
    fun start(cfg: SyncConfig, minutes: Int): Long = call(cfg, "PUT", g(cfg, "/me"), JSONObject().put("minutes", minutes)).getLong("until")
    fun stop(cfg: SyncConfig) { call(cfg, "DELETE", g(cfg, "/me")) }
    fun post(cfg: SyncConfig, lat: Double, lng: Double, accM: Float?) {
        val b = JSONObject().put("lat", lat).put("lng", lng); if (accM != null) b.put("acc", accM.toDouble())
        call(cfg, "POST", g(cfg, "/me/position"), b)
    }
    fun get(cfg: SyncConfig): LiveState {
        val o = call(cfg, "GET", g(cfg, ""))
        val a = o.optJSONArray("people") ?: JSONArray()
        val people = (0 until a.length()).map {
            val p = a.getJSONObject(it)
            LivePerson(p.getString("userId"), p.optString("name"), p.optString("role"), p.getDouble("lat"), p.getDouble("lng"),
                if (p.isNull("acc") || !p.has("acc")) null else p.getDouble("acc"), p.getLong("at"), p.getLong("until"))
        }
        return LiveState(people, if (o.isNull("sharing") || !o.has("sharing")) null else o.getLong("sharing"))
    }
}

/** Câu mô tả dễ đọc cho từng dòng nhật ký kiểm toán. */
object AuditText {
    fun describe(action: String, d: JSONObject?): String {
        fun s(k: String) = d?.optString(k).orEmpty()
        return when (action) {
            "garden.create" -> "Tạo vườn"
            "garden.delete" -> "Xoá vườn và toàn bộ dữ liệu"
            "member.invite" -> "Tạo lời mời vai trò ${Access.label(s("role"))}" + s("phone").let { if (it.isNotBlank() && it != "null") " cho $it" else "" }
            "member.join" -> "Tham gia vườn với vai trò ${Access.label(s("role"))}"
            "member.revoke" -> "Thu hồi quyền của ${s("name")} (${Access.label(s("role"))})"
            "member.update" -> "Đổi quyền của ${s("name")}"
            "live.start" -> "Bật chia sẻ vị trí ${d?.optInt("minutes") ?: 0} phút"
            "live.stop" -> "Tắt chia sẻ vị trí"
            "invite.revoke" -> "Thu hồi một lời mời"
            "owner.transfer" -> "Chuyển quyền chủ vườn cho ${s("toName")}"
            "owner.assign" -> "Quản trị cấp chủ vườn"
            "public.update" -> if (d?.optBoolean("enabled") == true) "Bật trang công khai (${d.optJSONArray("fields")?.length() ?: 0} trường)" else "Tắt trang công khai"
            "snapshot.put" -> {
                val del = d?.optJSONObject("deleted")
                val parts = buildList {
                    add("Đồng bộ: nhận ${d?.optInt("accepted") ?: 0} thay đổi")
                    if (del != null && del.length() > 0) add("xoá " + del.keys().asSequence().sumOf { del.optInt(it) } + " bản ghi")
                    val rj = d?.optInt("rejectedCount") ?: 0
                    if (rj > 0) add("TỪ CHỐI $rj thay đổi do không đủ quyền")
                }
                parts.joinToString(", ")
            }
            else -> action
        }
    }
}

/** Mã QR mời: vwing://invite/<MÃ>?server=<địa chỉ máy chủ>; người nhận chỉ cần quét. */
object InviteLink {
    private const val PREFIX = "vwing://invite/"
    fun payload(serverUrl: String, code: String) = PREFIX + code + "?server=" + URLEncoder.encode(Account.normalizeUrl(serverUrl), "UTF-8")

    /** Trả (mã, địa chỉ máy chủ nếu có) hoặc null nếu không phải QR mời. Cũng nhận mã trần do người dùng gõ. */
    fun parse(raw: String): Pair<String, String?>? {
        val t = raw.trim()
        if (t.startsWith(PREFIX)) {
            val rest = t.removePrefix(PREFIX)
            val code = rest.substringBefore('?').uppercase()
            val server = rest.substringAfter("server=", "").substringBefore('&').takeIf { it.isNotBlank() }?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            return if (Regex("^[A-Z2-7]{10}$").matches(code)) code to server else null
        }
        val bare = t.uppercase().replace(Regex("[\\s-]"), "")
        return if (Regex("^[A-Z2-7]{10}$").matches(bare)) bare to null else null
    }
}

/** Địa chỉ trang công khai của một cây: dán vào mã QR in trên sản phẩm cho người mua. */
fun publicTreeUrl(baseUrl: String, gardenKey: String, treeId: String) = Account.normalizeUrl(baseUrl) + "/p/" + gardenKey + "/" + treeId
