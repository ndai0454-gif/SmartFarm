from datetime import date
from typing import Literal, Optional

from pydantic import BaseModel, Field, model_validator


class Region(BaseModel):
    """Ranh giới vườn: vòng GeoJSON [[lng, lat], ...] (kinh độ trước)."""
    ring: list[list[float]] = Field(min_length=3)


class IndexRequest(BaseModel):
    region: Region
    index: Literal["NDVI", "EVI", "NDMI"] = "NDVI"
    start: date
    end: date            # loại trừ (end exclusive)
    max_cloud_pct: float = Field(default=80, ge=0, le=100)

    @model_validator(mode="after")
    def _order(self):
        if self.end <= self.start:
            raise ValueError("Ngày kết thúc phải sau ngày bắt đầu.")
        return self


class SuperResolutionRequest(BaseModel):
    region: Region
    start: date
    end: date  # exclusive
    max_cloud_pct: float = Field(default=80, ge=0, le=100)
    output_pixel_size_m: Literal[1.0, 2.5] = 2.5

    @model_validator(mode="after")
    def _order(self):
        if self.end <= self.start:
            raise ValueError("Ngày kết thúc phải sau ngày bắt đầu.")
        if (self.end - self.start).days > 93:
            raise ValueError("Khoảng ngày tối đa 93 ngày.")
        if any(len(point) != 2 for point in self.region.ring):
            raise ValueError("Mỗi đỉnh phải có đúng [kinh độ, vĩ độ].")
        return self


class SrJob(BaseModel):
    job_id: str
    state: Literal["queued", "running", "succeeded", "failed"]
    result: Optional[dict] = None
    error: Optional[str] = None


class WaterChangeRequest(BaseModel):
    region: Region
    baseline_start: date
    baseline_end: date
    event_start: date
    event_end: date

    @model_validator(mode="after")
    def _order(self):
        if self.baseline_end <= self.baseline_start or self.event_end <= self.event_start:
            raise ValueError("Mỗi khoảng ngày phải có ngày kết thúc sau ngày bắt đầu.")
        if self.event_start < self.baseline_end:
            raise ValueError("Kỳ sự kiện phải bắt đầu sau kỳ nền.")
        return self


class Bounds(BaseModel):
    south: float
    west: float
    north: float
    east: float


class RegionWorkflowRequest(BaseModel):
    region: Region
    baseline_start: date
    baseline_end: date
    comparison_start: date
    comparison_end: date
    max_cloud_pct: float = Field(default=80, ge=0, le=100)
    threshold: float = Field(default=0.1, ge=0.01, le=1)

    @model_validator(mode="after")
    def _periods(self):
        for start, end in ((self.baseline_start, self.baseline_end),
                           (self.comparison_start, self.comparison_end)):
            if not 0 < (end - start).days <= 93:
                raise ValueError("Mỗi kỳ cần từ 1 đến 93 ngày; ngày kết thúc được loại trừ.")
        if self.comparison_start < self.baseline_end:
            raise ValueError("Kỳ so sánh phải bắt đầu từ ngày kết thúc kỳ nền trở đi.")
        if any(len(point) != 2 for point in self.region.ring):
            raise ValueError("Mỗi đỉnh phải có đúng [kinh độ, vĩ độ].")
        return self


class Legend(BaseModel):
    min: float
    max: float
    palette: list[str]
    label: str


class WorkflowIndexStats(BaseModel):
    index: Literal["NDVI", "EVI", "NDMI"]
    mean: Optional[float]
    p5: Optional[float]
    p95: Optional[float]
    valid_coverage_pct: float
    nominal_pixel_size_m: Literal[10, 20]


class WorkflowPeriod(BaseModel):
    start: date
    end_exclusive: date
    scene_count: int
    scene_ids: list[str]
    valid_coverage_pct: float
    indices: list[WorkflowIndexStats]


class WorkflowChange(BaseModel):
    index: Literal["NDVI"] = "NDVI"
    threshold: float
    mean_delta: Optional[float]
    common_valid_coverage_pct: float
    common_valid_area_ha: Optional[float]
    increase_ha: Optional[float]
    stable_ha: Optional[float]
    decrease_ha: Optional[float]
    nominal_pixel_size_m: Literal[10] = 10
    image_url: Optional[str]
    legend: Legend


class RegionWorkflowResult(BaseModel):
    dataset: str
    source: str
    simulated: bool
    area_ha: float
    bounds: Bounds
    baseline: WorkflowPeriod
    comparison: WorkflowPeriod
    change: WorkflowChange
    method: str
    warnings: list[str]
    caution: str


class IndexResult(BaseModel):
    index: str
    dataset: str
    source: str
    simulated: bool
    start: date
    end_exclusive: date
    scene_count: int
    mean: Optional[float]
    p5: Optional[float]
    p95: Optional[float]
    valid_coverage_pct: Optional[float]
    nominal_pixel_size_m: int
    method: str
    area_ha: float
    bounds: Bounds
    image_url: Optional[str]
    legend: Legend
    warnings: list[str]
    caution: str


class SeriesPoint(BaseModel):
    date: date
    mean: Optional[float]
    valid_coverage_pct: float


class SeriesResult(BaseModel):
    index: str
    dataset: str
    source: str
    simulated: bool
    start: date
    end_exclusive: date
    points: list[SeriesPoint]
    skipped_scenes: int
    nominal_pixel_size_m: int
    method: str
    warnings: list[str]
    caution: str


class WaterResult(BaseModel):
    dataset: str
    source: str
    simulated: bool
    baseline_scenes: int
    event_scenes: int
    new_water_ha: Optional[float]
    permanent_water_ha: Optional[float]
    receded_ha: Optional[float]
    area_ha: float
    bounds: Bounds
    image_url: Optional[str]
    method: str
    warnings: list[str]
    caution: str


class GardenAnalysisRequest(BaseModel):
    region: Region
    start: date
    end: date
    max_cloud_pct: float = Field(default=80, ge=0, le=100)
    crop_type: str = Field(default="durian", max_length=40)
    tree_spacing_m: Optional[float] = Field(default=None, gt=0.5, le=25.0)
    crown_diameter_m: Optional[float] = Field(default=None, gt=0.5, le=20.0)
    zones: Optional[dict] = None

    @model_validator(mode="after")
    def _order(self):
        if self.end <= self.start:
            raise ValueError("Ngày kết thúc phải sau ngày bắt đầu.")
        if (self.end - self.start).days > 93:
            raise ValueError("Khoảng ngày tối đa 93 ngày.")
        return self


class LandCoverClassStat(BaseModel):
    code: int | str
    key: str
    label_vi: str
    area_ha: float
    pct: float


class LandCoverSourceBreakdown(BaseModel):
    source_id: str
    title: str
    dataset: str
    resolution_m: int
    dominant_class: str
    dominant_label_vi: str
    tree_canopy_pct: float
    crop_pct: float
    water_pct: float
    bare_or_built_pct: float
    classes: list[LandCoverClassStat]
    probabilities: dict[str, float] = Field(default_factory=dict)


class LandIndexStat(BaseModel):
    index: str
    title: str
    mean: Optional[float]
    p5: Optional[float]
    p95: Optional[float]
    valid_coverage_pct: float
    nominal_pixel_size_m: int
    status_vi: str
    interpretation_vi: str


class TerrainMetrics(BaseModel):
    dataset: str
    elevation_mean_m: Optional[float]
    elevation_min_m: Optional[float]
    elevation_max_m: Optional[float]
    slope_mean_deg: Optional[float]
    slope_max_deg: Optional[float]
    aspect_mean_deg: Optional[float]
    terrain_class_vi: str


class TreeHealthBreakdown(BaseModel):
    healthy_trees: int
    moderate_trees: int
    stressed_trees: int
    healthy_ha: float
    moderate_ha: float
    stressed_ha: float
    non_canopy_ha: float


class TreeEstimation(BaseModel):
    crop_type: str
    crop_label_vi: str
    assumed_spacing_m: float
    assumed_crown_diameter_m: float
    reference_density_per_ha: float
    effective_density_per_ha: float
    canopy_area_ha: float
    canopy_cover_pct: float
    estimated_trees: int
    estimated_trees_min: int
    estimated_trees_max: int
    detected_canopy_peaks: int
    health_breakdown: TreeHealthBreakdown
    zone_tree_counts: list[dict] = Field(default_factory=list)
    sample_tree_points_geojson: dict
    detection_method_vi: str = "Hybrid Google Satellite 0,3m RGB Crown-NMS + Quang phổ GEE Sentinel-2 10m"
    overall_accuracy_pct: float = 91.5
    crown_detection_confidence_pct: float = 90.2
    spectral_confidence_pct: float = 93.0
    spatial_precision_m: float = 0.45
    highres_resolution_m: float = 0.30
    highres_detected_crowns: int = 0
    spectral_estimated_trees: int = 0
    cross_validation_agreement_pct: float = 92.0


class SoilAndMoistureMetrics(BaseModel):
    ndmi_mean: Optional[float]
    ndwi_mean: Optional[float]
    bsi_mean: Optional[float]
    savi_mean: Optional[float]
    moisture_status_vi: str
    bare_soil_exposure_pct: float
    irrigation_advice_vi: str


class GardenAnalysisResult(BaseModel):
    dataset: str
    source: str
    simulated: bool
    start: date
    end_exclusive: date
    scene_count: int
    valid_coverage_pct: float
    area_ha: float
    perimeter_m: float
    bounds: Bounds
    geometry: dict
    terrain: TerrainMetrics
    land_cover: dict[str, LandCoverSourceBreakdown]
    vietnam_lulc_reference: list[dict]
    indices: list[LandIndexStat]
    tree_estimation: TreeEstimation
    soil_and_moisture: SoilAndMoistureMetrics
    image_url: Optional[str]
    legend: Legend
    recommendations: list[str]
    method: str
    warnings: list[str]
    caution: str


class GeeGardenClarityRequest(BaseModel):
    region: Region
    start: Optional[date] = None
    end: Optional[date] = None
    max_cloud_pct: float = Field(default=65.0, ge=0.0, le=100.0)
    buffer_m: float = Field(default=0.0, ge=0.0, le=300.0)
    crop_type: str = Field(default="durian", max_length=40)


class GeeVisualLayerItem(BaseModel):
    layer_id: str
    title_vi: str
    subtitle_vi: str
    bands_desc: str
    resolution_m: float
    image_url: Optional[str] = None
    tile_url_template: Optional[str] = None
    bounds: Bounds


class GeeGardenClarityResult(BaseModel):
    dataset: str
    source: str
    simulated: bool
    best_scene_id: Optional[str] = None
    best_scene_date: Optional[str] = None
    best_scene_cloud_pct: Optional[float] = None
    scene_count: int
    clear_sky_pct: float
    area_ha: float
    perimeter_m: float
    garden_bounds: Bounds
    context_bounds: Bounds
    canopy_cover_pct: float
    estimated_trees: int
    mean_ndvi: Optional[float] = None
    mean_ndre: Optional[float] = None
    mean_ndmi: Optional[float] = None
    elevation_mean_m: Optional[float] = None
    slope_mean_deg: Optional[float] = None
    layers: list[GeeVisualLayerItem]
    sample_tree_points_geojson: dict = Field(default_factory=dict)
    summary_vi: str


