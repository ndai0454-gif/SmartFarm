package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Cấu hình máy chủ đồng bộ, lưu trong thư mục riêng của app (mã truy cập không được đưa vào tệp sao lưu). */
data class SyncConfig(
    val url: String = "",
    val gardenKey: String = "",
    val token: String = "",
    val lastSyncAt: Long? = null,
    val lastMessage: String? = null,
    /** Tài khoản đang đăng nhập (token là mã phiên, không phải mật khẩu). role do máy chủ trả về ở lần đồng bộ/chọn vườn gần nhất. */
    val userName: String = "",
    val phone: String = "",
    val role: String = "",
) {
    val configured get() = url.isNotBlank() && gardenKey.isNotBlank() && token.isNotBlank()
    val signedIn get() = url.isNotBlank() && token.isNotBlank()
}

class SyncSettings(dir: File) {
    private val file = File(dir, "vwing_sync.json")

    fun load(): SyncConfig = try {
        val o = JSONObject(file.readText())
        SyncConfig(o.optString("url"), o.optString("gardenKey"), o.optString("token"),
            if (o.has("lastSyncAt")) o.getLong("lastSyncAt") else null, o.optString("lastMessage").ifBlank { null },
            o.optString("userName"), o.optString("phone"), o.optString("role"))
    } catch (_: Exception) { SyncConfig() }

    fun save(c: SyncConfig) {
        val o = JSONObject().put("url", c.url).put("gardenKey", c.gardenKey).put("token", c.token).put("lastMessage", c.lastMessage ?: "")
            .put("userName", c.userName).put("phone", c.phone).put("role", c.role)
        c.lastSyncAt?.let { o.put("lastSyncAt", it) }
        val tmp = File(file.parentFile, "vwing_sync.json.tmp")
        tmp.writeText(o.toString()); if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

sealed class SyncResult {
    data class Success(
        val pulled: MergeReport, val photosUp: Int, val photosDown: Int,
        /** Vai trò của tài khoản trên vườn này (máy chủ trả về). */
        val role: String? = null,
        /** Thay đổi bị máy chủ từ chối vì không đủ quyền (đã ghi nhật ký kiểm toán). */
        val rejected: List<String> = emptyList(),
    ) : SyncResult()
    /** Còn xung đột chưa giải quyết: không gửi gì lên máy chủ để tránh ghi đè bản của thiết bị khác. */
    data class Blocked(val conflicts: Int) : SyncResult()
    data class Failure(val message: String) : SyncResult()
}

object SyncClient {
    private const val MAX_ATTEMPTS = 4

    /** HTTP chỉ chấp nhận trong mạng nội bộ; ngoài Internet bắt buộc HTTPS vì mã truy cập đi kèm mỗi yêu cầu. */
    fun urlProblem(url: String): String? {
        val u = try { URL(url.trim()) } catch (_: Exception) { return "Địa chỉ máy chủ không hợp lệ (ví dụ http://192.168.1.10:8790)." }
        return when {
            u.protocol == "https" -> null
            u.protocol != "http" -> "Địa chỉ phải bắt đầu bằng http:// hoặc https://."
            isPrivateHost(u.host) -> null
            else -> "Ngoài mạng nội bộ cần dùng https:// để bảo vệ mã truy cập."
        }
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local")) return true
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || h.split('.').size != 4) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    private fun call(cfg: SyncConfig, method: String, path: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap()): ApiResp =
        Api.call(cfg.url, cfg.token, method, "/v1/gardens/" + cfg.gardenKey + path, body, headers)

    private fun photoNames(d: GardenData) = (d.events.flatMap { it.photos }.map { File(it.path).name } + d.imagery.map { it.file } + d.indices.mapNotNull { it.gridFile }).filter { it.isNotBlank() }.toSet()

    /**
     * Một lượt đồng bộ: kéo bản của máy chủ về → hợp nhất (không ghi đè âm thầm) → gửi ảnh → gửi bản đã hợp nhất kèm If-Match.
     * Nếu máy chủ vừa có bản mới hơn (409) thì lặp lại từ đầu. Chạy trên luồng nền.
     */
    fun run(store: VWingStore, cfg: SyncConfig): SyncResult {
        urlProblem(cfg.url)?.let { return SyncResult.Failure(it) }
        if (!cfg.configured) return SyncResult.Failure("Chưa đăng nhập hoặc chưa chọn vườn.")
        if (store.data.isSample) return SyncResult.Failure("Đang là dữ liệu mẫu. Hãy chọn \"Bắt đầu vườn của tôi\" trước khi đồng bộ để không đẩy dữ liệu mẫu lên máy chủ.")
        var pulled = MergeReport(); var up = 0; var down = 0; var role: String? = null
        try {
            repeat(MAX_ATTEMPTS) {
                // 1. kéo về
                val g = call(cfg, "GET", "/snapshot")
                val rev: Int
                when (g.code) {
                    200 -> {
                        val o = JSONObject(g.text); rev = o.getInt("rev"); role = o.optString("role").ifBlank { null }
                        val remote = VJson.fromJson(o.getJSONObject("data"))
                        down += downloadPhotos(store, cfg, remote)
                        pulled = store.mergeRemote(remote)
                    }
                    // 404 có "rev" = vườn của bạn nhưng chưa có dữ liệu; 404 không có "rev" = sai mã vườn hoặc không có quyền.
                    404 -> if (g.json?.has("rev") == true) { rev = 0; role = g.json?.optString("role")?.ifBlank { null } }
                           else return SyncResult.Failure(g.json?.optString("error")?.ifBlank { null } ?: "Không tìm thấy vườn, hoặc bạn không có quyền.")
                    401 -> return SyncResult.Failure("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.")
                    else -> return SyncResult.Failure("Máy chủ trả lỗi ${g.code}.")
                }
                // 2. có xung đột chưa giải quyết thì dừng
                val cur = store.data
                if (cur.conflicts.isNotEmpty()) return SyncResult.Blocked(cur.conflicts.size)
                // 3. gửi ảnh còn thiếu, rồi gửi bản chụp (xây từ đúng phiên bản `cur` để đánh dấu đồng bộ chính xác)
                up += uploadPhotos(store, cfg, cur)
                val text = VJson.toJson(cur.copy(conflicts = emptyList())).toString()
                val p = call(cfg, "PUT", "/snapshot", text.toByteArray(), mapOf("If-Match" to rev.toString(), "Content-Type" to "application/json"))
                when (p.code) {
                    200 -> {
                        store.markSynced(cur)
                        val rj = p.json?.optJSONArray("rejected")
                        val rejected = if (rj == null) emptyList() else (0 until rj.length()).map { rj.getJSONObject(it).let { x -> x.optString("reason") } }.distinct()
                        return SyncResult.Success(pulled, up, down, role, rejected)
                    }
                    409 -> Unit                       // máy chủ có bản mới hơn → thử lại từ bước kéo
                    401 -> return SyncResult.Failure("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.")
                    else -> return SyncResult.Failure("Máy chủ từ chối (${p.code}): " + runCatching { JSONObject(p.text).optString("error") }.getOrDefault(""))
                }
            }
            return SyncResult.Failure("Máy chủ liên tục thay đổi, hãy thử lại sau ít phút.")
        } catch (e: IOException) {
            return SyncResult.Failure("Không kết nối được máy chủ: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: org.json.JSONException) {
            return SyncResult.Failure("Phản hồi của máy chủ không đọc được.")
        }
    }

    private fun downloadPhotos(store: VWingStore, cfg: SyncConfig, remote: GardenData): Int {
        var n = 0
        for (name in photoNames(remote)) {
            val dest = File(store.photoDir, name)
            if (dest.exists() || !Regex("^[A-Za-z0-9._-]{1,100}$").matches(name)) continue
            val r = call(cfg, "GET", "/photos/$name")
            if (r.code == 200) { dest.writeBytes(r.body); n++ }
        }
        return n
    }

    private fun uploadPhotos(store: VWingStore, cfg: SyncConfig, d: GardenData): Int {
        val names = photoNames(d).filter { Regex("^[A-Za-z0-9._-]{1,100}$").matches(it) && File(store.photoDir, it).exists() }
        if (names.isEmpty()) return 0
        val l = call(cfg, "GET", "/photos")
        val have = if (l.code == 200) JSONObject(l.text).getJSONArray("names").let { a: JSONArray -> (0 until a.length()).map { a.getString(it) }.toSet() } else emptySet()
        var n = 0
        for (name in names) {
            if (name in have) continue
            val r = call(cfg, "PUT", "/photos/$name", File(store.photoDir, name).readBytes(), mapOf("Content-Type" to "image/jpeg"))
            if (r.code == 200) n++ else throw IOException("Không tải được ảnh $name (${r.code}).")
        }
        return n
    }
}
