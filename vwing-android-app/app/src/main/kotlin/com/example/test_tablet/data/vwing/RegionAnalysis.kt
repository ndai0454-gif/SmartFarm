package com.example.test_tablet.data.vwing

/** Only an explicit polygon can define an analysis region; a tree filter is not a boundary. */
object RegionAnalysis {
    fun regions(d: GardenData, zone: String): List<GeoFeature> = d.features
        .filter {
            if (zone.isBlank()) it.kind == FeatureKind.BOUNDARY || it.kind == FeatureKind.ZONE
            else it.kind == FeatureKind.ZONE && it.name == zone
        }
        .sortedBy { if (it.kind == FeatureKind.BOUNDARY) 0 else 1 }

    fun problem(region: GeoFeature?): String? {
        if (region == null) return "Chưa có polygon cho vùng này. Vẽ ranh vườn hoặc nhập polygon khu bằng KML/GeoJSON."
        val ring = Geo.open(region.points)
        return when {
            ring.size < 3 || ring.distinct().size < 3 -> "Ranh giới cần ít nhất 3 đỉnh khác nhau."
            ring.size > 2000 -> "Ranh giới quá nhiều đỉnh (tối đa 2.000). Hãy đơn giản hóa polygon."
            ring.any { !it.lat.isFinite() || !it.lng.isFinite() || it.lat !in -90.0..90.0 || it.lng !in -180.0..180.0 } -> "Tọa độ ranh giới không hợp lệ."
            Geo.selfIntersects(ring) -> "Ranh giới tự cắt; hãy sửa trước khi phân tích."
            Geo.polygonAreaHa(ring) < .01 -> "Diện tích vùng phải từ 0,01 ha."
            Geo.polygonAreaHa(ring) > 500 -> "Diện tích vùng vượt giới hạn 500 ha."
            else -> null
        }
    }
}
