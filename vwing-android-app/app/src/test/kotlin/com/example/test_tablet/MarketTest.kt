package com.example.test_tablet

import com.example.test_tablet.data.vwing.MarketClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MarketTest {
    private fun payload(): JSONObject {
        val quote = JSONObject().put("id", "coffee-average").put("commodity", "coffee").put("label", "Cà phê nhân")
            .put("market", "Tây Nguyên").put("price", 91400).put("unit", "VNĐ/kg").put("change", -400)
            .put("source_id", "giacaphe").put("source_url", "https://giacaphe.com/gia-ca-phe-noi-dia/")
            .put("as_of", "2026-10-22").put("as_of_label", "22/10/2026").put("kind", "domestic").put("stale", false)
        val source = JSONObject().put("id", "giacaphe").put("name", "Giacaphe.com").put("url", "https://giacaphe.com/gia-ca-phe-noi-dia/")
            .put("category", "Trong nước").put("commodities", JSONArray().put("coffee")).put("status", "public")
            .put("note", "Giá công khai").put("fetched_at", "2026-10-22T00:00:00+00:00")
        return JSONObject().put("fetched_at", "2026-10-22T00:00:00Z").put("stale", false)
            .put("sources", JSONArray().put(source)).put("quotes", JSONArray().put(quote)).put("news", JSONArray())
    }
    @Test fun `prices retain source day unit negative movement and domestic classification`() {
        val data = MarketClient.parse(payload().toString()); val q = data.quotes.single()
        assertEquals(91400.0, q.price, .001); assertEquals(-400.0, q.change!!, .001)
        assertEquals("VNĐ/kg", q.unit); assertEquals("2026-10-22", q.asOf); assertEquals("domestic", q.kind)
        assertFalse(data.stale); assertFalse(q.stale)
    }
    @Test fun `unsafe publisher schemes credentials lookalikes and ports are rejected`() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "http://giacaphe.com/", "https://giacaphe.com.evil.test/",
            "https://giacaphe.com@evil.test/", "https://user:pass@giacaphe.com/", "https://giacaphe.com:8443/").forEach {
            assertFalse(it, MarketClient.safeSourceUrl(it))
        }
        assertTrue(MarketClient.safeSourceUrl("https://vn.investing.com/commodities/"))
    }
    @Test fun `invalid numeric quotes unknown sources and cross-publisher links are excluded`() {
        val base = payload(); val good = base.getJSONArray("quotes").getJSONObject(0)
        val negative = JSONObject(good.toString()).put("price", -1)
        val unknown = JSONObject(good.toString()).put("source_id", "unverified")
        val wrongLink = JSONObject(good.toString()).put("source_url", "https://tradingeconomics.com/commodities")
        val noUnit = JSONObject(good.toString()).put("unit", "")
        base.put("quotes", JSONArray().put(good).put(negative).put(unknown).put(wrongLink).put(noUnit))
        assertEquals(1, MarketClient.parse(base.toString()).quotes.size)
    }
    @Test fun `missing source date remains missing rather than inferred from fetch time`() {
        val base = payload(); base.getJSONArray("quotes").getJSONObject(0).put("as_of", JSONObject.NULL)
        assertNull(MarketClient.parse(base.toString()).quotes.single().asOf)
        assertTrue(MarketClient.oldPrice(null))
    }
    @Test fun `international units are not converted into farm gate prices`() {
        val base = payload(); base.getJSONArray("quotes").getJSONObject(0).put("kind", "futures").put("unit", "USD cent/lb").put("price", 316.15)
        val q = MarketClient.parse(base.toString()).quotes.single()
        assertEquals("futures", q.kind); assertEquals("USD cent/lb", q.unit); assertEquals(316.15, q.price, .001)
    }
    @Test fun `offline cache is explicitly stale and malformed cache cannot crash screen`() {
        val dir = kotlin.io.path.createTempDirectory("vwing-market-test").toFile()
        try {
            val file = File(dir, "vwing_market_cache.json"); file.writeText(payload().toString())
            assertTrue(MarketClient.loadCache(dir)!!.stale)
            file.writeText("corrupt"); assertNull(MarketClient.loadCache(dir))
        } finally { dir.deleteRecursively() }
    }
}
