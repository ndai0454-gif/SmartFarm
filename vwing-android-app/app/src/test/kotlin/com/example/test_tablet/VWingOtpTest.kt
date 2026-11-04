package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/** Đăng ký/đăng nhập bằng mã SMS với máy chủ Node thật ở chế độ OTP "dev" (mã được ghi vào nhật ký máy chủ, không gửi SMS thật). */
class VWingOtpTest {
    @get:Rule val tmp = TemporaryFolder()
    private var proc: Process? = null
    private lateinit var url: String
    private val log = CopyOnWriteArrayList<String>()

    @Before fun start() {
        val script = listOf("E:/New folder/SmartFarm/vwing-sync-server/server.js", System.getenv("VWING_SYNC_SERVER") ?: "").map(::File).firstOrNull { it.isFile }
        assumeTrue("Không thấy vwing-sync-server/server.js", script != null)
        val port = ServerSocket(0).use { it.localPort }
        val pb = ProcessBuilder("node", script!!.absolutePath).redirectErrorStream(true)
        pb.environment().apply { put("PORT", port.toString()); put("VWING_TOKEN", "test-token-123456"); put("DATA_DIR", tmp.newFolder("srv").absolutePath); put("OTP_PROVIDER", "dev"); put("OTP_RESEND_SECONDS", "1") }
        proc = try { pb.start() } catch (e: Exception) { null }
        assumeTrue("Không chạy được node", proc != null)
        Thread { proc!!.inputStream.bufferedReader().forEachLine { log += it } }.apply { isDaemon = true }.start()
        url = "http://127.0.0.1:$port"
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            try { if ((URL("$url/v1/health").openConnection() as java.net.HttpURLConnection).responseCode == 200) return } catch (_: Exception) { Thread.sleep(150) }
        }
        fail("Máy chủ không khởi động được")
    }

    @After fun stop() { proc?.destroyForcibly() }

    private fun codeFor(phone: String): String {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            log.lastOrNull { it.contains("[OTP dev] $phone") }?.let { return Regex("(\\d{6})").find(it.substringAfter(phone))!!.value }
            Thread.sleep(50)
        }
        fail("Không thấy mã OTP trong nhật ký máy chủ"); return ""
    }

    @Test fun `may chu bao otp bat va dang ky phai co ma sms`() {
        assertTrue(Account.otpEnabled(url))
        assertFalse(Account.otpEnabled("http://127.0.0.1:1"))                         // không kết nối được: coi như chưa bật
        val phone = "0933000001"
        assertTrue(runCatching { Account.register(url, phone, "An", "matkhau-12345") }.exceptionOrNull() is AccountException)   // thiếu mã
        assertTrue(Account.requestOtp(url, phone, "register") >= 1)
        val s = Account.register(url, phone, "An", "matkhau-12345", codeFor(phone))
        assertEquals("An", s.userName)
        assertTrue(runCatching { Account.requestOtp(url, phone, "register") }.exceptionOrNull()?.message!!.contains("đã đăng ký"))
    }

    @Test fun `dang nhap bang ma sms va dat lai mat khau`() {
        val phone = "0933000002"
        Account.requestOtp(url, phone, "register"); Account.register(url, phone, "Bình", "matkhau-12345", codeFor(phone))
        Thread.sleep(1100)
        Account.requestOtp(url, phone, "login")
        assertEquals("Bình", Account.loginOtp(url, phone, codeFor(phone)).userName)
        assertTrue(runCatching { Account.loginOtp(url, phone, "000000") }.exceptionOrNull() is AccountException)
        Thread.sleep(1100)
        Account.requestOtp(url, phone, "reset")
        Account.resetPassword(url, phone, codeFor(phone), "matkhau-moi-99")
        assertTrue(runCatching { Account.login(url, phone, "matkhau-12345") }.exceptionOrNull() is AccountException)
        assertEquals("Bình", Account.login(url, phone, "matkhau-moi-99").userName)
    }
}
