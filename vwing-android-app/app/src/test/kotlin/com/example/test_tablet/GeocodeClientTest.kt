package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

/** Ứng dụng khách tìm địa điểm với một máy chủ giả trả đúng định dạng của /v1/geo (xem vwing-satellite-backend/app/geocode.py). */
class GeocodeClientTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<String>()
    private lateinit var cfg: SatelliteConfig

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/geo") { ex ->
            seen += ex.requestURI.rawQuery.orEmpty() + "|" + (ex.requestHeaders.getFirst("Authorization") ?: "")
            val path = ex.requestURI.path
            val (code, body) = when {
                ex.requestHeaders.getFirst("Authorization") != "Bearer tok-123456789012" -> 401 to """{"detail":"Sai"}"""
                path.endsWith("/search") && ex.requestURI.rawQuery.contains("q=l%E1%BB%97i") -> 429 to """{"detail":"Tìm quá nhanh"}"""
                path.endsWith("/search") -> 200 to """{"results":[{"name":"Buôn Ma Thuột","label":"Buôn Ma Thuột, Đắk Lắk","admin":"Buôn Ma Thuột, Đắk Lắk","lat":12.68,"lng":108.038,"kind":"city","country":"VN","bbox":{"west":108.0,"north":12.75,"east":108.1,"south":12.62}},{"name":"Quán","label":"Quán","admin":"","lat":12.7,"lng":108.0,"kind":"cafe","bbox":null}]}"""
                else -> 200 to """{"result":{"name":"","label":"Hẻm 209","admin":"Tổ dân phố 6, Buôn Ma Thuột, Đắk Lắk","lat":12.68,"lng":108.037,"kind":"residential","bbox":null}}"""
            }
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        cfg = SatelliteConfig("http://127.0.0.1:${server.address.port}", "tok-123456789012")
    }

    @After fun stop() { server.stop(0) }

    @Test fun `tim dia diem ma hoa dau tieng viet va doc bbox`() {
        val r = GeocodeClient.search(cfg, "  Buôn Ma Thuột ", GeoPoint(12.7, 108.1))
        assertEquals(2, r.size)
        assertEquals("Buôn Ma Thuột, Đắk Lắk", r[0].label); assertEquals(108.038, r[0].lng, 1e-9)
        assertEquals(GeoBounds(12.62, 108.0, 12.75, 108.1), r[0].bbox); assertNull(r[1].bbox)
        val q = seen.single().substringBefore("|")
        assertEquals("Buôn Ma Thuột", URLDecoder.decode(q.substringAfter("q=").substringBefore("&"), "UTF-8"))
        assertTrue(q.contains("lat=12.7") && q.contains("lng=108.1"))
        assertTrue(seen.single().endsWith("Bearer tok-123456789012"))
    }

    @Test fun `khong goi mang voi tu khoa qua ngan va tra ngoc ra dia chi hanh chinh`() {
        assertTrue(GeocodeClient.search(cfg, "a").isEmpty()); assertTrue(seen.isEmpty())
        val p = GeocodeClient.reverse(cfg, GeoPoint(12.68, 108.037))!!
        assertEquals("Tổ dân phố 6, Buôn Ma Thuột, Đắk Lắk", p.admin)
    }

    @Test fun `loi may chu duoc dich ra thong bao ro rang`() {
        val e429 = runCatching { GeocodeClient.search(cfg, "lỗi") }.exceptionOrNull()
        assertTrue(e429 is GisException && e429.message!!.contains("nhanh"))
        val e401 = runCatching { GeocodeClient.search(SatelliteConfig(cfg.url, "tok-sai-sai-sai1"), "abc") }.exceptionOrNull()
        assertTrue(e401 is GisException && e401.message!!.contains("Sai mã"))
        assertTrue(runCatching { GeocodeClient.search(SatelliteConfig("", ""), "abc") }.exceptionOrNull() is GisException)
    }
}
