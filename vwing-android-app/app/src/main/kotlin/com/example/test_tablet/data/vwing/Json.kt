package com.example.test_tablet.data.vwing

import org.json.JSONArray
import org.json.JSONObject

/** Chuyển đổi thủ công sang org.json (không thêm thư viện). Trường thiếu được đọc với giá trị mặc định. */
object VJson {
    const val SCHEMA = 1

    private fun JSONObject.str(k: String, d: String = ""): String = if (has(k) && !isNull(k)) getString(k) else d
    private fun JSONObject.strN(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.lng(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.int(k: String, d: Int): Int = if (has(k) && !isNull(k)) getInt(k) else d
    private fun JSONObject.intN(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.bool(k: String, d: Boolean): Boolean = if (has(k) && !isNull(k)) getBoolean(k) else d
    private inline fun <reified E : Enum<E>> JSONObject.enum(k: String, d: E): E =
        strN(k)?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: d
    private fun JSONObject.put2(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)
    private fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
    private fun strings(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
    private fun arr(items: Iterable<Any?>): JSONArray = JSONArray().also { a -> items.forEach { a.put(it) } }

    // --- phụ trợ ---
    private fun pointJ(p: GeoPoint) = JSONObject().put("lat", p.lat).put("lng", p.lng)
    private fun pointOf(o: JSONObject) = GeoPoint(o.getDouble("lat"), o.getDouble("lng"))
    private fun auditJ(a: AuditEntry) = JSONObject().put("at", a.at).put("by", a.by).put("summary", a.summary)
    private fun auditOf(o: JSONObject) = AuditEntry(o.getLong("at"), o.str("by"), o.str("summary"))
    private fun photoJ(p: Photo) = JSONObject().put("path", p.path).put2("takenAt", p.takenAt).put("uploadedAt", p.uploadedAt)
    private fun photoOf(o: JSONObject) = Photo(o.str("path"), o.lng("takenAt"), o.lng("uploadedAt") ?: 0L)

    // --- cây ---
    fun treeJ(t: Tree) = JSONObject()
        .put("id", t.id).put("code", t.code).put("zone", t.zone).put("variety", t.variety)
        .put2("plantedYear", t.plantedYear).put2("fruits", t.fruits).put2("fruitsAt", t.fruitsAt)
        .put("condition", t.condition.name)
        .put2("pos", t.pos?.let(::pointJ)).put("posSource", t.posSource.name).put2("posAt", t.posAt)
        .put2("replacesId", t.replacesId).put2("replacedById", t.replacedById)
        .put("active", t.active).put("createdAt", t.createdAt)
        .put("audit", arr(t.audit.map(::auditJ)))
        .put("version", t.version).put("baseVersion", t.baseVersion).put("updatedAt", t.updatedAt)
        .put("opId", t.opId).put("sync", t.sync.name)

    fun treeOf(o: JSONObject) = Tree(
        id = o.getString("id"), code = o.str("code"), zone = o.str("zone"), variety = o.str("variety"),
        plantedYear = o.intN("plantedYear"), fruits = o.intN("fruits"), fruitsAt = o.lng("fruitsAt"),
        condition = o.enum("condition", Condition.UNASSESSED),
        pos = o.optJSONObject("pos")?.let(::pointOf), posSource = o.enum("posSource", PosSource.NONE), posAt = o.lng("posAt"),
        replacesId = o.strN("replacesId"), replacedById = o.strN("replacedById"),
        active = o.bool("active", true), createdAt = o.lng("createdAt") ?: 0L,
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- sự kiện ---
    fun eventJ(e: Event) = JSONObject()
        .put("id", e.id).put("treeIds", arr(e.treeIds)).put2("scopeZone", e.scopeZone)
        .put("type", e.type.name).put("activity", e.activity)
        .put("occurredAt", e.occurredAt).put("recordedAt", e.recordedAt).put("author", e.author).put("note", e.note)
        .put2("product", e.product).put2("dosage", e.dosage).put2("amountKg", e.amountKg)
        .put("photos", arr(e.photos.map(::photoJ))).put("source", e.source.name)
        .put2("issueId", e.issueId).put2("result", e.result).put2("confirmedBy", e.confirmedBy)
        .put2("conditionSet", e.conditionSet?.name)
        .put("audit", arr(e.audit.map(::auditJ)))
        .put("version", e.version).put("baseVersion", e.baseVersion).put("updatedAt", e.updatedAt)
        .put("opId", e.opId).put("sync", e.sync.name)

    fun eventOf(o: JSONObject) = Event(
        id = o.getString("id"), treeIds = strings(o.optJSONArray("treeIds")), scopeZone = o.strN("scopeZone"),
        type = o.enum("type", EventType.OBSERVATION), activity = o.str("activity"),
        occurredAt = o.lng("occurredAt") ?: 0L, recordedAt = o.lng("recordedAt") ?: 0L,
        author = o.str("author"), note = o.str("note"),
        product = o.strN("product"), dosage = o.strN("dosage"), amountKg = o.dbl("amountKg"),
        photos = o.optJSONArray("photos")?.map(::photoOf) ?: emptyList(),
        source = o.enum("source", EvidenceSource.OWNER_NOTE),
        issueId = o.strN("issueId"), result = o.strN("result"), confirmedBy = o.strN("confirmedBy"),
        conditionSet = o.strN("conditionSet")?.let { s -> Condition.values().firstOrNull { it.name == s } },
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- vấn đề ---
    fun issueJ(i: Issue) = JSONObject()
        .put("id", i.id).put("number", i.number).put("treeIds", arr(i.treeIds)).put2("scopeZone", i.scopeZone)
        .put("symptom", i.symptom).put("priority", i.priority.name).put("status", i.status.name)
        .put("assignee", i.assignee).put2("dueAt", i.dueAt).put("createdAt", i.createdAt)
        .put2("closedAt", i.closedAt).put2("confirmedBy", i.confirmedBy).put2("originEventId", i.originEventId)
        .put("audit", arr(i.audit.map(::auditJ)))
        .put("version", i.version).put("baseVersion", i.baseVersion).put("updatedAt", i.updatedAt)
        .put("opId", i.opId).put("sync", i.sync.name)

    fun issueOf(o: JSONObject) = Issue(
        id = o.getString("id"), number = o.int("number", 0), treeIds = strings(o.optJSONArray("treeIds")),
        scopeZone = o.strN("scopeZone"), symptom = o.str("symptom"),
        priority = o.enum("priority", Priority.MEDIUM), status = o.enum("status", IssueStatus.NEW),
        assignee = o.str("assignee"), dueAt = o.lng("dueAt"), createdAt = o.lng("createdAt") ?: 0L,
        closedAt = o.lng("closedAt"), confirmedBy = o.strN("confirmedBy"), originEventId = o.strN("originEventId"),
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- công việc ---
    fun taskJ(t: Task) = JSONObject()
        .put("id", t.id).put("title", t.title).put("treeIds", arr(t.treeIds)).put2("scopeZone", t.scopeZone)
        .put("assignee", t.assignee).put2("dueAt", t.dueAt).put("done", t.done).put2("doneAt", t.doneAt)
        .put2("doneBy", t.doneBy).put2("issueId", t.issueId).put("fromAi", t.fromAi).put("open", t.open)
        .put("version", t.version).put("baseVersion", t.baseVersion).put("updatedAt", t.updatedAt)
        .put("opId", t.opId).put("sync", t.sync.name)

    fun taskOf(o: JSONObject) = Task(
        id = o.getString("id"), title = o.str("title"), treeIds = strings(o.optJSONArray("treeIds")),
        scopeZone = o.strN("scopeZone"), assignee = o.str("assignee"), dueAt = o.lng("dueAt"),
        done = o.bool("done", false), doneAt = o.lng("doneAt"), doneBy = o.strN("doneBy"),
        issueId = o.strN("issueId"), fromAi = o.bool("fromAi", false), open = o.bool("open", false),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- bán hàng ---
    fun saleJ(s: Sale) = JSONObject()
        .put("id", s.id).put("date", s.date).put("kg", s.kg).put("pricePerKg", s.pricePerKg).put("note", s.note)
        .put("version", s.version).put("baseVersion", s.baseVersion).put("updatedAt", s.updatedAt)
        .put("opId", s.opId).put("sync", s.sync.name)

    fun saleOf(o: JSONObject) = Sale(
        id = o.getString("id"), date = o.lng("date") ?: 0L, kg = o.getDouble("kg"), pricePerKg = o.getDouble("pricePerKg"),
        note = o.str("note"),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- đối tượng bản đồ ---
    fun featureJ(f: GeoFeature) = JSONObject()
        .put("id", f.id).put("kind", f.kind.name).put("name", f.name).put("points", arr(f.points.map(::pointJ)))
        .put("note", f.note).put("source", f.source).put("importedAt", f.importedAt)
        .put("version", f.version).put("baseVersion", f.baseVersion).put("updatedAt", f.updatedAt)
        .put("opId", f.opId).put("sync", f.sync.name)

    fun featureOf(o: JSONObject) = GeoFeature(
        id = o.getString("id"), kind = o.enum("kind", FeatureKind.OBSTACLE), name = o.str("name"),
        points = o.optJSONArray("points")?.map(::pointOf) ?: emptyList(),
        note = o.str("note"), source = o.str("source"), importedAt = o.lng("importedAt") ?: 0L,
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- bộ ảnh (Đợt C) ---
    private fun boundsJ(b: GeoBounds) = JSONObject().put("south", b.south).put("west", b.west).put("north", b.north).put("east", b.east)
    private fun boundsOf(o: JSONObject) = GeoBounds(o.getDouble("south"), o.getDouble("west"), o.getDouble("north"), o.getDouble("east"))
    private fun detJ(d: Detection) = JSONObject().put("id", d.id).put("point", pointJ(d.point)).put("status", d.status.name)
        .put2("treeId", d.treeId).put2("by", d.by).put2("at", d.at)
    private fun detOf(o: JSONObject) = Detection(o.getString("id"), pointOf(o.getJSONObject("point")), o.enum("status", DetStatus.PENDING),
        o.strN("treeId"), o.strN("by"), o.lng("at"))

    fun imageJ(i: ImageSet) = JSONObject()
        .put("id", i.id).put("name", i.name).put("kind", i.kind.name).put("capturedAt", i.capturedAt)
        .put("source", i.source).put("sensor", i.sensor.name).put("sensorModel", i.sensorModel).put("file", i.file)
        .put("widthPx", i.widthPx).put("heightPx", i.heightPx).put2("bounds", i.bounds?.let(::boundsJ)).put2("gsdCm", i.gsdCm)
        .put("quality", i.quality).put("detectionModel", i.detectionModel).put("detectionVersion", i.detectionVersion)
        .put("detections", arr(i.detections.map(::detJ))).put("createdAt", i.createdAt)
        .put("audit", arr(i.audit.map(::auditJ)))
        .put("version", i.version).put("baseVersion", i.baseVersion).put("updatedAt", i.updatedAt)
        .put("opId", i.opId).put("sync", i.sync.name)

    fun imageOf(o: JSONObject) = ImageSet(
        id = o.getString("id"), name = o.str("name"), kind = o.enum("kind", ImageKind.FIELD), capturedAt = o.lng("capturedAt") ?: 0L,
        source = o.str("source"), sensor = o.enum("sensor", SensorKind.UNKNOWN), sensorModel = o.str("sensorModel"), file = o.str("file"),
        widthPx = o.int("widthPx", 0), heightPx = o.int("heightPx", 0), bounds = o.optJSONObject("bounds")?.let(::boundsOf), gsdCm = o.dbl("gsdCm"),
        quality = o.str("quality"), detectionModel = o.str("detectionModel"), detectionVersion = o.str("detectionVersion"),
        detections = o.optJSONArray("detections")?.map(::detOf) ?: emptyList(), createdAt = o.lng("createdAt") ?: 0L,
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- số đo nước/đất/thời tiết (Đợt C) ---
    fun measureJ(m: Measure) = JSONObject()
        .put("id", m.id).put("kind", m.kind.name).put("origin", m.origin.name).put2("value", m.value).put("unit", m.unit)
        .put("measuredAt", m.measuredAt).put("periodDays", m.periodDays).put2("scopeZone", m.scopeZone).put("treeIds", arr(m.treeIds))
        .put2("point", m.point?.let(::pointJ)).put("calibration", m.calibration.name).put("labName", m.labName)
        .put("lab", arr(m.lab.map { JSONObject().put("name", it.name).put("value", it.value).put("unit", it.unit) }))
        .put("note", m.note).put("author", m.author).put("createdAt", m.createdAt)
        .put("audit", arr(m.audit.map(::auditJ)))
        .put("version", m.version).put("baseVersion", m.baseVersion).put("updatedAt", m.updatedAt)
        .put("opId", m.opId).put("sync", m.sync.name)

    fun measureOf(o: JSONObject) = Measure(
        id = o.getString("id"), kind = o.enum("kind", MeasureKind.RAIN), origin = o.enum("origin", MeasureOrigin.MEASURED),
        value = o.dbl("value"), unit = o.str("unit"), measuredAt = o.lng("measuredAt") ?: 0L, periodDays = o.int("periodDays", 1),
        scopeZone = o.strN("scopeZone"), treeIds = strings(o.optJSONArray("treeIds")), point = o.optJSONObject("point")?.let(::pointOf),
        calibration = o.enum("calibration", Calibration.NA), labName = o.str("labName"),
        lab = o.optJSONArray("lab")?.map { LabValue(it.str("name"), it.getDouble("value"), it.str("unit")) } ?: emptyList(),
        note = o.str("note"), author = o.str("author"), createdAt = o.lng("createdAt") ?: 0L,
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- chỉ số thực vật (Đợt D) ---
    private fun statJ(s: IndexStat) = JSONObject().put("scope", s.scope).put("mean", s.mean).put("min", s.min).put("max", s.max)
        .put("n", s.n).put("validPct", s.validPct)
    private fun statOf(o: JSONObject) = IndexStat(o.str("scope"), o.getDouble("mean"), o.getDouble("min"), o.getDouble("max"), o.int("n", 0), o.dbl("validPct") ?: 0.0)

    fun indexJ(v: VegIndex) = JSONObject()
        .put("id", v.id).put("name", v.name).put("source", v.source.name).put("date", v.date).put("provider", v.provider)
        .put("sensor", v.sensor.name).put("preprocess", v.preprocess.name).put2("resolutionM", v.resolutionM)
        .put2("bounds", v.bounds?.let(::boundsJ)).put2("redSetId", v.redSetId).put2("nirSetId", v.nirSetId)
        .put2("gridFile", v.gridFile).put("gridW", v.gridW).put("gridH", v.gridH).put("canopyRadiusM", v.canopyRadiusM)
        .put("stats", arr(v.stats.map(::statJ))).put("notes", arr(v.notes)).put("createdAt", v.createdAt)
        .put("audit", arr(v.audit.map(::auditJ)))
        .put("version", v.version).put("baseVersion", v.baseVersion).put("updatedAt", v.updatedAt)
        .put("opId", v.opId).put("sync", v.sync.name)

    fun indexOf(o: JSONObject) = VegIndex(
        id = o.getString("id"), name = o.str("name"), source = o.enum("source", IndexSource.PROVIDED), date = o.lng("date") ?: 0L,
        provider = o.str("provider"), sensor = o.enum("sensor", SensorKind.UNKNOWN), preprocess = o.enum("preprocess", Preprocess.UNKNOWN),
        resolutionM = o.dbl("resolutionM"), bounds = o.optJSONObject("bounds")?.let(::boundsOf),
        redSetId = o.strN("redSetId"), nirSetId = o.strN("nirSetId"), gridFile = o.strN("gridFile"),
        gridW = o.int("gridW", 0), gridH = o.int("gridH", 0), canopyRadiusM = o.dbl("canopyRadiusM") ?: 3.0,
        stats = o.optJSONArray("stats")?.map(::statOf) ?: emptyList(), notes = strings(o.optJSONArray("notes")),
        createdAt = o.lng("createdAt") ?: 0L, audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- nhật ký bay DJI đã nhập (xem DjiFlightLog.kt) ---
    fun droneFlightJ(f: DroneFlight) = JSONObject()
        .put("id", f.id).put("name", f.name).put("importedAt", f.importedAt).put("file", f.file)
        .put("fileSizeBytes", f.fileSizeBytes).put("frameCount", f.frameCount).put("parseOk", f.parseOk)
        .put("parseError", f.parseError).put("note", f.note).put("createdAt", f.createdAt)
        .put("audit", arr(f.audit.map(::auditJ)))
        .put("version", f.version).put("baseVersion", f.baseVersion).put("updatedAt", f.updatedAt)
        .put("opId", f.opId).put("sync", f.sync.name)

    fun droneFlightOf(o: JSONObject) = DroneFlight(
        id = o.getString("id"), name = o.str("name"), importedAt = o.lng("importedAt") ?: 0L, file = o.str("file"),
        fileSizeBytes = o.lng("fileSizeBytes") ?: 0L, frameCount = o.int("frameCount", 0), parseOk = o.bool("parseOk", false),
        parseError = o.str("parseError"), note = o.str("note"), createdAt = o.lng("createdAt") ?: 0L,
        audit = o.optJSONArray("audit")?.map(::auditOf) ?: emptyList(),
        version = o.int("version", 1), baseVersion = o.int("baseVersion", 0), updatedAt = o.lng("updatedAt") ?: 0L,
        opId = o.str("opId", newId()), sync = o.enum("sync", SyncState.LOCAL),
    )

    // --- vườn / khu / xung đột ---
    fun gardenJ(g: Garden) = JSONObject()
        .put("name", g.name).put("location", g.location).put("crop", g.crop).put2("areaHa", g.areaHa)
        .put("harvestCycle", g.harvestCycle).put("checkIntervalDays", g.checkIntervalDays)
        .put("operatorName", g.operatorName).put2("avgWeightKg", g.avgWeightKg)
        .put("commercialRatio", g.commercialRatio).put("spreadPct", g.spreadPct)
        .put2("pricePerKg", g.pricePerKg).put("priceSource", g.priceSource).put("priceRegion", g.priceRegion)
        .put("priceGrade", g.priceGrade).put2("priceDate", g.priceDate).put("updatedAt", g.updatedAt)

    fun gardenOf(o: JSONObject) = Garden(
        name = o.str("name", "Vườn của tôi"), location = o.str("location"), crop = o.str("crop"), areaHa = o.dbl("areaHa"),
        harvestCycle = o.str("harvestCycle"), checkIntervalDays = o.int("checkIntervalDays", 14),
        operatorName = o.str("operatorName", "Người làm vườn"), avgWeightKg = o.dbl("avgWeightKg"),
        commercialRatio = o.dbl("commercialRatio") ?: 1.0, spreadPct = o.dbl("spreadPct") ?: 10.0,
        pricePerKg = o.dbl("pricePerKg"), priceSource = o.str("priceSource"), priceRegion = o.str("priceRegion"),
        priceGrade = o.str("priceGrade"), priceDate = o.lng("priceDate"), updatedAt = o.lng("updatedAt") ?: 0L,
    )

    private fun tombJ(t: Tombstone) = JSONObject().put("type", t.type).put("id", t.id).put("at", t.at).put("by", t.by)
    private fun tombOf(o: JSONObject) = Tombstone(o.str("type"), o.str("id"), o.lng("at") ?: 0L, o.str("by"))

    private fun zoneJ(z: Zone) = JSONObject().put("name", z.name).put("desc", z.desc)
    private fun zoneOf(o: JSONObject) = Zone(o.str("name"), o.str("desc"))
    private fun conflictJ(c: Conflict) = JSONObject().put("id", c.id).put("entityType", c.entityType)
        .put("entityId", c.entityId).put("label", c.label).put("localJson", c.localJson)
        .put("remoteJson", c.remoteJson).put("at", c.at)
    private fun conflictOf(o: JSONObject) = Conflict(o.getString("id"), o.str("entityType"), o.str("entityId"),
        o.str("label"), o.str("localJson"), o.str("remoteJson"), o.lng("at") ?: 0L)

    // --- toàn bộ ---
    fun toJson(d: GardenData): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("garden", gardenJ(d.garden))
        .put("zones", arr(d.zones.map(::zoneJ)))
        .put("trees", arr(d.trees.map(::treeJ)))
        .put("events", arr(d.events.map(::eventJ)))
        .put("issues", arr(d.issues.map(::issueJ)))
        .put("tasks", arr(d.tasks.map(::taskJ)))
        .put("sales", arr(d.sales.map(::saleJ)))
        .put("features", arr(d.features.map(::featureJ)))
        .put("imagery", arr(d.imagery.map(::imageJ)))
        .put("measures", arr(d.measures.map(::measureJ)))
        .put("indices", arr(d.indices.map(::indexJ)))
        .put("droneFlights", arr(d.droneFlights.map(::droneFlightJ)))
        .put("conflicts", arr(d.conflicts.map(::conflictJ)))
        .put("tombstones", arr(d.tombstones.map(::tombJ)))
        .put("isSample", d.isSample).put("configured", d.configured)

    fun fromJson(o: JSONObject) = GardenData(
        garden = o.optJSONObject("garden")?.let(::gardenOf) ?: Garden(),
        zones = o.optJSONArray("zones")?.map(::zoneOf) ?: emptyList(),
        trees = o.optJSONArray("trees")?.map(::treeOf) ?: emptyList(),
        events = o.optJSONArray("events")?.map(::eventOf) ?: emptyList(),
        issues = o.optJSONArray("issues")?.map(::issueOf) ?: emptyList(),
        tasks = o.optJSONArray("tasks")?.map(::taskOf) ?: emptyList(),
        sales = o.optJSONArray("sales")?.map(::saleOf) ?: emptyList(),
        features = o.optJSONArray("features")?.map(::featureOf) ?: emptyList(),
        imagery = o.optJSONArray("imagery")?.map(::imageOf) ?: emptyList(),
        measures = o.optJSONArray("measures")?.map(::measureOf) ?: emptyList(),
        indices = o.optJSONArray("indices")?.map(::indexOf) ?: emptyList(),
        droneFlights = o.optJSONArray("droneFlights")?.map(::droneFlightOf) ?: emptyList(),
        conflicts = o.optJSONArray("conflicts")?.map(::conflictOf) ?: emptyList(),
        tombstones = o.optJSONArray("tombstones")?.map(::tombOf) ?: emptyList(),
        isSample = o.bool("isSample", false), configured = o.bool("configured", false),
    )
}
