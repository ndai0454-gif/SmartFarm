package com.example.test_tablet

import com.example.test_tablet.data.vwing.*
import com.example.test_tablet.ui.vwing.fromMeters
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Đợt C (ảnh theo thời gian, nước/đất/thời tiết) và Đợt D (NDVI). */
class VWingCDTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(dir: File = tmp.newFolder()) = VWingStore(dir, null, false)
    private fun VWingStore.tree(code: String, pos: GeoPoint? = null, zone: String = "Khu A"): Tree {
        check(addTree(code, zone, pos = pos, posSource = PosSource.ESTIMATED).ok); return data.treeByCode(code)!!
    }

    private val sw = GeoPoint(11.5000, 107.8000)
    private fun at(xM: Double, yM: Double) = fromMeters(xM, yM, sw)
    private fun boundsM(wM: Double, hM: Double) = GeoBounds(sw.lat, sw.lng, at(0.0, hM).lat, at(wM, 0.0).lng)

    private fun img(
        kind: ImageKind = ImageKind.ORTHO, sensor: SensorKind = SensorKind.RGB, bounds: GeoBounds? = boundsM(100.0, 100.0),
        w: Int = 100, h: Int = 100, date: Long = Dates.daysAgo(3), name: String = "Bay 1", file: String = "a.img",
    ) = ImageSet(newId(), name, kind, date, "Đối tác bay", sensor, "", file, w, h, bounds)

    // ---------- P1-02: ảnh ----------

    @Test fun `bo anh khong hop le bi tu choi voi ly do ro rang`() {
        assertNull(Imagery.validate(img()))
        assertNotNull(Imagery.validate(img(bounds = null)))                           // trực giao cần định vị
        assertNull(Imagery.validate(img(kind = ImageKind.FIELD, bounds = null)))      // ảnh thực địa không cần
        assertTrue(Imagery.validate(img(kind = ImageKind.BAND_NIR, sensor = SensorKind.RGB))!!.contains("đa phổ"))
        assertNotNull(Imagery.validate(img(bounds = GeoBounds(11.6, 107.8, 11.5, 107.9))))                    // nam > bắc
        assertNotNull(Imagery.validate(img().copy(capturedAt = Dates.now() + 5 * 86_400_000L)))
        assertNotNull(Imagery.validate(img().copy(source = " ")))
    }

    @Test fun `nhap lai cung diem ung vien khong nhan doi va chua ghep thi khong doi cay`() {
        val s = store(); val t = s.tree("A-01", at(10.0, 10.0))
        val set = img(); assertTrue(s.addImageSet(set).ok)
        val pts = listOf(at(10.5, 10.0), at(40.0, 40.0))
        assertEquals(2, s.importDetections(set.id, pts, "YOLO", "v1").getOrThrow())
        assertEquals(0, s.importDetections(set.id, pts, "YOLO", "v1").getOrThrow())      // nhập lại
        assertEquals(1, s.importDetections(set.id, pts + at(70.0, 70.0), "YOLO", "v1").getOrThrow())
        assertEquals(3, s.data.imagery.single().detections.size)
        assertEquals("YOLO", s.data.imagery.single().detectionModel)
        assertTrue(s.data.imagery.single().detections.all { it.status == DetStatus.PENDING })
        assertEquals(t, s.data.treeByCode("A-01"))                                       // chưa xác nhận → cây không đổi
        assertEquals(1, s.data.trees.size)
    }

    @Test fun `goi y ghep cay phan biet duy nhat nhap nhang va khong co`() {
        val s = store()
        s.tree("A-01", at(10.0, 10.0)); s.tree("A-02", at(10.0, 12.0)); s.tree("A-03", at(50.0, 50.0))
        val d = s.data
        val unique = Imagery.suggest(d, Detection("d1", at(50.5, 50.0)))
        assertTrue(unique is Imagery.Match.Unique && unique.tree.code == "A-03")
        assertTrue(Imagery.suggest(d, Detection("d2", at(10.0, 11.0))) is Imagery.Match.Ambiguous)    // giữa hai cây
        assertEquals(Imagery.Match.None, Imagery.suggest(d, Detection("d3", at(90.0, 90.0))))
    }

    @Test fun `xac nhan ghep can cay khong cho hai diem cung mot cay va khong tu dong`() {
        val s = store(); val t1 = s.tree("A-01", at(10.0, 10.0)); val t2 = s.tree("A-02", at(30.0, 30.0))
        val set = img(); s.addImageSet(set)
        s.importDetections(set.id, listOf(at(10.2, 10.0), at(10.4, 10.5)), "m", "1")
        val (d1, d2) = s.data.imagery.single().detections
        assertFalse(s.resolveDetection(set.id, d1.id, DetStatus.CONFIRMED, null).ok)                    // thiếu cây
        assertTrue(s.resolveDetection(set.id, d1.id, DetStatus.CONFIRMED, t1.id).ok)
        assertFalse(s.resolveDetection(set.id, d2.id, DetStatus.CONFIRMED, t1.id).ok)                  // cây đã ghép
        assertTrue(s.resolveDetection(set.id, d2.id, DetStatus.REJECTED, null).ok)
        val got = s.data.imagery.single().detections
        assertEquals(DetStatus.CONFIRMED, got[0].status); assertEquals(t1.id, got[0].treeId); assertNotNull(got[0].by)
        assertEquals(DetStatus.REJECTED, got[1].status)
        assertEquals(setOf(t1, t2).size, 2)
        assertTrue(s.data.imagery.single().audit.size >= 3)                                            // có dấu vết sửa tay
    }

    @Test fun `phat hien anh bi dich he thong so voi ho so cay`() {
        val s = store()
        val trees = (0 until 6).map { s.tree("A-0$it", at(10.0 + it * 12.0, 20.0)) }
        val set = img(); s.addImageSet(set)
        s.importDetections(set.id, trees.map { at(10.0 + trees.indexOf(it) * 12.0 + 2.5, 20.0 + 1.0) }, "m", "1")   // lệch ~2,7 m
        val off = Imagery.systematicOffsetM(s.data, s.data.imagery.single())!!
        assertEquals(2.7, off, 0.3)
        val few = img(); s.addImageSet(few); s.importDetections(few.id, listOf(at(10.0, 20.0)), "m", "1")
        assertNull(Imagery.systematicOffsetM(s.data, s.data.imagery.first { it.id == few.id }))          // ít hơn 5 điểm → không kết luận
    }

    @Test fun `kich thuoc diem anh suy ra tu khung dia ly hoac khai bao`() {
        assertEquals(1.0, Imagery.pixelSizeM(img(bounds = boundsM(100.0, 100.0), w = 100, h = 100))!!, 0.05)
        assertEquals(0.05, Imagery.pixelSizeM(img().copy(gsdCm = 5.0))!!, 1e-9)
        assertNull(Imagery.pixelSizeM(img(bounds = null)))
    }

    // ---------- P1-03: nước, đất, thời tiết ----------

    private fun rain(daysAgo: Int, mm: Double?, origin: MeasureOrigin = MeasureOrigin.MEASURED, id: String = newId(), created: Long = Dates.now()) = Measure(
        id, MeasureKind.RAIN, origin, mm, "mm", Dates.daysAgo(daysAgo), createdAt = created,
    )

    @Test fun `quy tac nhap so do tu choi gia tri sai`() {
        val d = store().also { it.tree("A-01") }.data
        assertNull(Env.validate(rain(1, 12.0), d))
        assertNotNull(Env.validate(rain(1, null), d))                                              // thiếu ≠ 0
        assertNotNull(Env.validate(rain(1, -3.0), d))
        assertNotNull(Env.validate(rain(-5, 10.0), d))                                              // tương lai (số đo thực tế)
        assertNull(Env.validate(rain(-3, 10.0, MeasureOrigin.FORECAST), d))                         // dự báo được phép
        val moist = Measure(newId(), MeasureKind.SOIL_MOISTURE, MeasureOrigin.MEASURED, 120.0, "%", Dates.now())
        assertNotNull(Env.validate(moist, d))
        assertNull(Env.validate(moist.copy(value = 35.0), d))
        assertNotNull(Env.validate(moist.copy(value = 35.0, origin = MeasureOrigin.SENSOR), d))     // cảm biến cần khai báo hiệu chuẩn
        assertNull(Env.validate(moist.copy(value = 35.0, origin = MeasureOrigin.SENSOR, calibration = Calibration.UNCALIBRATED), d))
        assertNotNull(Env.validate(moist.copy(value = 35.0, calibration = Calibration.CALIBRATED), d))   // hiệu chuẩn chỉ cho cảm biến
        assertNotNull(Env.validate(moist.copy(value = 35.0, unit = "mm"), d))
        val irr = Measure(newId(), MeasureKind.IRRIGATION, MeasureOrigin.FORECAST, 100.0, "lít", Dates.now())
        assertNotNull(Env.validate(irr, d))                                                          // dự báo chỉ cho mưa
        assertNotNull(Env.validate(moist.copy(value = 30.0, scopeZone = "Khu Z"), d))
    }

    @Test fun `phan tich dat can phong thi nghiem chi tieu va vi tri lay mau`() {
        val d = store().data
        val lab = Measure(newId(), MeasureKind.SOIL_LAB, MeasureOrigin.LAB, null, "", Dates.daysAgo(2), labName = "Viện Thổ nhưỡng",
            lab = listOf(LabValue("pH", 5.8, ""), LabValue("Hữu cơ", 3.1, "%")), point = at(5.0, 5.0))
        assertNull(Env.validate(lab, d))
        assertNotNull(Env.validate(lab.copy(lab = emptyList()), d))
        assertNotNull(Env.validate(lab.copy(lab = listOf(LabValue("pH", 15.0, ""))), d))
        assertNotNull(Env.validate(lab.copy(point = null), d))
        assertNotNull(Env.validate(lab.copy(labName = ""), d))
        assertNotNull(Env.validate(lab.copy(origin = MeasureOrigin.MEASURED), d))
    }

    @Test fun `tong mua chi tinh so do thuc te toan vuon va khong cong du bao hay tuoi`() {
        val s = store()
        s.addMeasure(rain(1, 10.0)); s.addMeasure(rain(3, 5.0))
        s.addMeasure(rain(1, 99.0, id = "x", created = Dates.now() + 10))     // cùng ngày: lấy bản mới nhất, không cộng lặp
        s.addMeasure(rain(-2, 40.0, MeasureOrigin.FORECAST))
        s.addMeasure(Measure(newId(), MeasureKind.IRRIGATION, MeasureOrigin.MEASURED, 2000.0, "lít", Dates.daysAgo(2)))
        val r = Env.rain(s.data, 7)
        assertEquals(104.0, r.totalMm!!, 1e-9)                                  // 99 (ngày 1, bản mới) + 5 (ngày 3)
        assertEquals(2, r.coveredDays); assertEquals(5, r.missingDays)          // 5 ngày thiếu dữ liệu, không coi là 0 mm
        assertEquals(40.0, r.forecastMm!!, 1e-9)                                // dự báo tách riêng
        assertEquals(2000.0, Env.irrigationLitres(s.data, 7)!!, 1e-9)           // lít riêng, không cộng vào mm
        assertNull(Env.rain(store().data, 7).totalMm)                           // chưa có số đo → null, không phải 0
        assertNull(Env.irrigationLitres(store().data, 7))
    }

    @Test fun `mua tich luy nhieu ngay tinh dung so ngay bao phu`() {
        val s = store()
        s.addMeasure(rain(0, 30.0).copy(periodDays = 3))
        val r = Env.rain(s.data, 7)
        assertEquals(30.0, r.totalMm!!, 1e-9); assertEquals(3, r.coveredDays); assertEquals(4, r.missingDays)
    }

    @Test fun `nhac kiem tra chi voi loai da tung ghi va da cu`() {
        val s = store()
        assertTrue(Env.reminders(s.data).isEmpty())                              // chưa từng ghi → không nhắc
        s.addMeasure(Measure(newId(), MeasureKind.SOIL_MOISTURE, MeasureOrigin.MEASURED, 30.0, "%", Dates.daysAgo(20)))
        assertEquals(1, Env.reminders(s.data).size)
        s.addMeasure(Measure(newId(), MeasureKind.SOIL_MOISTURE, MeasureOrigin.MEASURED, 28.0, "%", Dates.daysAgo(1)))
        assertTrue(Env.reminders(s.data).isEmpty())
        val latest = Env.latestMoisture(s.data); assertEquals(1, latest.size); assertEquals(1, Env.ageDays(latest.single()))
    }

    @Test fun `ghi chu cho dong nuoc va kho han len ban do`() {
        val s = store()
        assertTrue(s.addSpotNote(FeatureKind.WATERLOG, at(10.0, 10.0), "Đọng sau mưa").ok)
        assertTrue(s.addSpotNote(FeatureKind.DRY, at(20.0, 10.0), "").ok)
        assertFalse(s.addSpotNote(FeatureKind.ROAD, at(1.0, 1.0), "").ok)
        assertFalse(s.addSpotNote(FeatureKind.DRY, GeoPoint(120.0, 0.0), "").ok)
        assertEquals(setOf(FeatureKind.WATERLOG, FeatureKind.DRY), s.data.features.map { it.kind }.toSet())
    }

    // ---------- P2-01: NDVI ----------

    @Test fun `ndvi tinh dung cong thuc va danh dau thieu du lieu`() {
        val g = Ndvi.compute(intArrayOf(50, 0, 100, 10), intArrayOf(150, 0, 100, 0), 2, 2)
        assertEquals(0.5, g.value(0, 0)!!, 0.01)
        assertNull(g.value(1, 0))                                                // cả hai bằng 0 → thiếu dữ liệu
        assertEquals(0.0, g.value(0, 1)!!, 0.01)
        assertEquals(-1.0, g.value(1, 1)!!, 0.01)
    }

    private fun band(kind: ImageKind, sensor: SensorKind = SensorKind.MULTISPECTRAL, b: GeoBounds? = boundsM(100.0, 100.0), w: Int = 100, h: Int = 100, date: Long = Dates.daysAgo(2)) =
        img(kind, sensor, b, w, h, date, name = kind.name)

    @Test fun `anh rgb khong the tinh ndvi va hai bang phai khop nhau`() {
        assertNull(Ndvi.checkBands(band(ImageKind.BAND_RED), band(ImageKind.BAND_NIR)))
        assertTrue(Ndvi.checkBands(band(ImageKind.BAND_RED, SensorKind.RGB), band(ImageKind.BAND_NIR))!!.contains("đa phổ"))
        assertNotNull(Ndvi.checkBands(band(ImageKind.FIELD), band(ImageKind.BAND_NIR)))                        // không phải băng Red
        assertNotNull(Ndvi.checkBands(band(ImageKind.BAND_RED), band(ImageKind.BAND_NIR, w = 50)))             // lệch kích thước
        assertNotNull(Ndvi.checkBands(band(ImageKind.BAND_RED), band(ImageKind.BAND_NIR, b = boundsM(120.0, 100.0))))   // lệch khung
        assertNotNull(Ndvi.checkBands(band(ImageKind.BAND_RED), band(ImageKind.BAND_NIR, date = Dates.daysAgo(9))))     // lệch ngày
        assertNotNull(Ndvi.checkBands(band(ImageKind.BAND_RED), band(ImageKind.BAND_NIR, b = null)))
    }

    private fun provided(stats: List<IndexStat>, res: Double? = 0.5, sensor: SensorKind = SensorKind.MULTISPECTRAL, pre: Preprocess = Preprocess.REFLECTANCE, date: Long = Dates.daysAgo(1)) =
        VegIndex(newId(), "NDVI " + Dates.fmt(date), IndexSource.PROVIDED, date, "Công ty bay", sensor, pre, res, boundsM(100.0, 100.0), stats = stats)

    @Test fun `ban ghi ndvi nhap san bi tu choi khi khong co red nir hoac do phan giai khong du tach cay`() {
        val s = store(); val t = s.tree("A-01", at(10.0, 10.0))
        val ok = IndexStat("zone:Khu A", 0.62, 0.5, 0.7, 40, 100.0)
        assertTrue(s.addVegIndex(provided(listOf(ok))).ok)
        assertFalse(s.addVegIndex(provided(listOf(ok), sensor = SensorKind.RGB)).ok)                         // RGB không thể là NDVI
        assertFalse(s.addVegIndex(provided(listOf(IndexStat("zone:Khu A", 1.4, 1.2, 1.5, 4, 100.0)))).ok)    // ngoài [-1,1]
        val tree = IndexStat("tree:${t.id}", 0.6, 0.5, 0.7, 12, 90.0)
        assertTrue(s.addVegIndex(provided(listOf(tree), res = 0.5)).ok)                                       // 0,5 m ≤ tán 3 m
        val landsat = s.addVegIndex(provided(listOf(tree), res = 30.0))
        assertFalse(landsat.ok); assertTrue(landsat.message!!.contains("không nhận giá trị theo từng cây"))   // 30 m/pixel
        assertFalse(s.addVegIndex(provided(listOf(tree), res = null)).ok)
        assertEquals(2, s.data.indices.size)
    }

    /** Lưới 100×100 trên khung 100 m × 100 m (1 m/pixel): nửa trái NDVI 0.8, nửa phải 0.2, cột 90..99 thiếu dữ liệu. */
    private fun syntheticGrid(): Ndvi.Grid {
        val red = IntArray(10_000); val nir = IntArray(10_000)
        for (y in 0 until 100) for (x in 0 until 100) {
            val i = y * 100 + x
            if (x >= 90) { red[i] = 0; nir[i] = 0 }
            else if (x < 50) { red[i] = 20; nir[i] = 180 }      // (180-20)/200 = 0.8
            else { red[i] = 100; nir[i] = 150 }                  // (150-100)/250 = 0.2
        }
        return Ndvi.compute(red, nir, 100, 100)
    }

    @Test fun `ndvi theo cay va khu va ghi ro vung thieu du lieu`() {
        val s = store()
        val left = s.tree("A-01", at(20.0, 50.0)); val right = s.tree("B-01", at(70.0, 50.0), "Khu B")
        s.tree("C-01", at(500.0, 500.0), "Khu C")                         // ngoài ảnh
        s.tree("D-01", null, "Khu D")                                      // chưa có vị trí
        val r = Ndvi.summarize(s.data, syntheticGrid(), boundsM(100.0, 100.0), 3.0)
        val byScope = r.stats.associateBy { it.scope }
        assertEquals(0.8, byScope["tree:${left.id}"]!!.mean, 0.02)
        assertEquals(0.2, byScope["tree:${right.id}"]!!.mean, 0.02)
        assertEquals(0.8, byScope["zone:Khu A"]!!.mean, 0.02)
        assertEquals(0.533, byScope["garden"]!!.mean, 0.01)               // (50×0,8 + 40×0,2) / 90 cột hợp lệ
        assertEquals(90.0, byScope["garden"]!!.validPct, 0.5)              // 10% thiếu dữ liệu
        assertTrue(r.notes.any { it.contains("Vùng thiếu dữ liệu") })
        assertTrue(r.notes.any { it.contains("ngoài vùng ảnh") })
        assertTrue(r.notes.any { it.contains("chưa có vị trí") })
        assertNull(byScope["tree:" + s.data.treeByCode("C-01")!!.id])
    }

    @Test fun `do phan giai tho khong tach duoc cay chi co thong ke toan vuon`() {
        val s = store(); s.tree("A-01", at(20.0, 50.0))
        val coarse = Ndvi.compute(IntArray(16) { 50 }, IntArray(16) { 150 }, 4, 4)              // 4×4 trên 100 m = 25 m/pixel
        val r = Ndvi.summarize(s.data, coarse, boundsM(100.0, 100.0), 3.0)
        assertEquals(listOf("garden"), r.stats.map { it.scope })
        assertTrue(r.notes.any { it.contains("không tách được từng cây") })
    }

    @Test fun `chuoi thoi gian chi so sanh khi ca hai la phan xa da hieu chinh`() {
        val a = SeriesLike(0.50, Preprocess.REFLECTANCE, 30); val b = SeriesLike(0.58, Preprocess.REFLECTANCE, 10); val c = SeriesLike(0.70, Preprocess.RAW_DN, 2)
        val s = store()
        listOf(a, b, c).forEach { x -> s.addVegIndex(provided(listOf(IndexStat("zone:Khu A", x.v, x.v - .05, x.v + .05, 20, 100.0)), pre = x.pre, date = Dates.daysAgo(x.daysAgo))) }
        val ser = Ndvi.series(s.data, "zone:Khu A")
        assertEquals(listOf(0.50, 0.58, 0.70), ser.map { it.stat.mean })                         // sắp theo ngày
        assertEquals(0.08, Ndvi.delta(ser[0], ser[1])!!, 1e-9)
        assertNull(Ndvi.delta(ser[1], ser[2]))                                                   // có giá trị thô (DN) → không so sánh
    }
    private class SeriesLike(val v: Double, val pre: Preprocess, val daysAgo: Int)

    // ---------- lưu trữ, hợp nhất ----------

    @Test fun `du lieu dot c va d con nguyen sau khi mo lai va qua json`() {
        val dir = tmp.newFolder(); val a = store(dir)
        val set = img(); a.addImageSet(set); a.importDetections(set.id, listOf(at(5.0, 5.0)), "m", "2")
        a.addMeasure(Measure(newId(), MeasureKind.SOIL_LAB, MeasureOrigin.LAB, null, "", Dates.daysAgo(1), labName = "PTN", lab = listOf(LabValue("pH", 5.5, "")), point = at(1.0, 1.0)))
        a.addVegIndex(provided(listOf(IndexStat("garden", 0.5, 0.1, 0.9, 100, 95.0))))
        a.flush()
        val b = store(dir)
        assertEquals(a.data.imagery, b.data.imagery); assertEquals(a.data.measures, b.data.measures); assertEquals(a.data.indices, b.data.indices)
        assertEquals(1, b.data.imagery.single().detections.size)
        assertEquals(3, b.data.unsyncedCount())
        assertTrue(b.referencedFiles().contains("a.img"))
    }

    @Test fun `hop nhat hai thiet bi cho ca ba loai moi khong nhan doi`() {
        val a = store(); val b = store()
        val set = img(); a.addImageSet(set)
        val m = rain(1, 12.0); a.addMeasure(m)
        val v = provided(listOf(IndexStat("garden", 0.5, 0.1, 0.9, 100, 95.0))); a.addVegIndex(v)
        val rep = b.mergeRemote(a.data)
        assertEquals(3, rep.added)
        val again = b.mergeRemote(a.data); assertEquals(0, again.added); assertEquals(3, again.skipped)
        assertEquals(1, b.data.imagery.size); assertEquals(1, b.data.measures.size); assertEquals(1, b.data.indices.size)
        // cùng sửa số đo ở hai nơi → xung đột, không ghi đè
        val ma = a.data.measures.single(); val mb = b.data.measures.single()
        a.editMeasure(ma.id, ma.version, "sửa A") { it.copy(value = 20.0) }
        b.editMeasure(mb.id, mb.version, "sửa B") { it.copy(value = 30.0) }
        val rep2 = b.mergeRemote(a.data)
        assertEquals(1, rep2.conflicts); assertEquals(30.0, b.data.measures.single().value!!, 1e-9)
        b.resolveConflict(b.data.conflicts.single().id, takeRemote = true)
        assertEquals(20.0, b.data.measures.single().value!!, 1e-9)
        assertEquals(0, b.mergeRemote(a.data).conflicts)                                            // đã giải quyết thì không tái xung đột
    }
}
