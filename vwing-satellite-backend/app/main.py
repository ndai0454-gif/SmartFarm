"""API vệ tinh của VWING. Chạy: VWING_SAT_TOKEN=... VWING_EE_MODE=mock uvicorn app.main:app --host 127.0.0.1 --port 8000"""
import hashlib
import hmac
import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Callable, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, FileResponse

from . import config, geo, indices as ix, mock_service
from .schemas import IndexRequest, IndexResult, SeriesResult, WaterChangeRequest, WaterResult
from .schemas import SuperResolutionRequest, SrJob
from .schemas import RegionWorkflowRequest, RegionWorkflowResult
from .schemas import GardenAnalysisRequest, GardenAnalysisResult, GeeGardenClarityRequest, GeeGardenClarityResult
from . import region_workflow, gis_tools, geocode, garden_analysis, routing, dem, open_gis_data, gis_curriculum, drought_monitoring, drone_multispectral, deepforest_engine, ag_research_9papers, global_ag_and_3d, tof_mapper_engine
from .dem import DemRequest
from .sr_jobs import SrJobManager, QueueFull, JobNotFound, Generator
from . import market
from . import weather
from . import hazards
from . import nws_ffc_map
from . import deeplabv3plus_engine
from . import obia_api
from .obia_jobs import ObiaJobManager

log = logging.getLogger("vwing.sat")
ALGO_VERSION = "3"            # đổi khi đổi thuật toán để bỏ bộ nhớ đệm cũ


class _ArtifactResponse(FileResponse):
    """Release the storage lease even when the client disconnects mid-download."""
    def __init__(self, *args, release: Callable, **kwargs):
        super().__init__(*args, **kwargs)
        self._release = release

    async def __call__(self, scope, receive, send):
        try:
            await super().__call__(scope, receive, send)
        finally:
            self._release()


class TTLCache:
    def __init__(self):
        self._d: dict[str, tuple[float, object]] = {}

    def get_or_set(self, key: str, ttl: float, make: Callable[[], object]):
        now = time.time()
        hit = self._d.get(key)
        if hit and hit[0] > now:
            return hit[1]
        val = make()
        self._d[key] = (now + ttl, val)
        if len(self._d) > 500:
            for k in [k for k, (t, _) in self._d.items() if t <= now]:
                self._d.pop(k, None)
        return val


def create_app(settings: Optional[config.Settings] = None, *,
               sr_generator: Generator | None = None, sr_output_dir: Path | None = None, geocode_fetch=None, routing_post=None,
               obia_dir: Path | None = None, obia_runner=None) -> FastAPI:
    st = settings or config.load()
    sr_jobs = SrJobManager(sr_output_dir or Path(os.environ.get(
        "VWING_SR_OUTPUT_DIR", str(Path(__file__).resolve().parents[1] / "artifacts" / "super-resolution"))),
        generator=sr_generator)
    obia_jobs = ObiaJobManager(obia_dir or Path(os.environ.get(
        "VWING_OBIA_DIR", str(Path(__file__).resolve().parents[1] / "artifacts" / "obia"))), runner=obia_runner)

    @asynccontextmanager
    async def lifespan(app):
        yield
        sr_jobs.close()
        obia_jobs.close()

    app = FastAPI(title="VWING Satellite API", version="0.1.0", lifespan=lifespan)
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    @app.middleware("http")
    async def _default_json_content_type(request, call_next):
        if request.method in ("POST", "PUT", "PATCH"):
            ct = (request.headers.get("content-type") or "").lower()
            if not ct or "application/x-www-form-urlencoded" in ct:
                request.scope["headers"] = [
                    (k, v) for (k, v) in request.scope["headers"] if k.lower() != b"content-type"
                ] + [(b"content-type", b"application/json")]
        return await call_next(request)

    app.state.sr_jobs = sr_jobs
    app.state.obia_jobs = obia_jobs
    cache = TTLCache()
    ee_service = None
    if st.mode == "live":
        try:
            from . import ee_service as _ee_service  # nhập muộn: cần earthengine-api đã xác thực
            _ee_service.init(st.ee_project)
            ee_service = _ee_service
        except Exception as exc:
            if os.environ.get("VWING_EE_STRICT") == "1":
                raise
            log.warning("Earth Engine live init failed (%s); falling back to resilient offline/mock mode.", exc)
            import dataclasses
            st = dataclasses.replace(st, mode="mock")

    def auth(authorization: str = Header(default="")):
        given = authorization[7:] if authorization.startswith("Bearer ") else ""
        if not hmac.compare_digest(given.encode(), st.token.encode()):
            raise HTTPException(401, "Sai hoặc thiếu mã truy cập.")

    def prepare(region, days_limit: int, days: int) -> list:
        try:
            ring = geo.validate_ring(region.ring)
        except geo.GeoError as e:
            raise HTTPException(422, str(e))
        ha = geo.area_ha(ring)
        if ha > st.max_area_ha:
            raise HTTPException(422, f"Diện tích {ha:.0f} ha vượt giới hạn {st.max_area_ha:.0f} ha.")
        if ha < 0.01:
            raise HTTPException(422, "Ranh giới quá nhỏ (dưới 0,01 ha).")
        if days > days_limit:
            raise HTTPException(422, f"Khoảng ngày tối đa {days_limit} ngày.")
        return ring

    def key(*parts) -> str:
        return hashlib.sha256(json.dumps(parts, default=str, sort_keys=True).encode()).hexdigest()

    def live(fn, *a):
        try:
            return fn(*a)
        except ee_service.NoScenes as e:
            raise HTTPException(404, str(e))
        except HTTPException:
            raise
        except Exception:                      # lỗi Earth Engine: không để lộ chi tiết nội bộ cho ứng dụng
            log.exception("Lỗi Earth Engine")
            raise HTTPException(502, "Earth Engine trả lỗi. Thử lại sau hoặc giảm vùng/khoảng ngày.")

    app.include_router(nws_ffc_map.router, dependencies=[Depends(auth)])
    app.include_router(deeplabv3plus_engine.router, dependencies=[Depends(auth)])
    app.include_router(gis_tools.make_router(auth))
    app.include_router(obia_api.make_router(auth, obia_jobs))
    app.include_router(market.make_router(auth))
    app.include_router(weather.make_router(auth))
    app.include_router(hazards.make_router(auth))
    app.include_router(open_gis_data.make_router(auth))
    app.include_router(dem.make_router(auth, lambda: st.mode, cache))
    app.include_router(gis_curriculum.make_router(auth, lambda: st.mode, cache))
    app.include_router(drought_monitoring.make_router(auth, lambda: st.mode, cache))
    app.include_router(drone_multispectral.make_router(auth, lambda: st.mode, cache))
    app.include_router(deepforest_engine.make_router(auth, lambda: st.mode, cache))
    app.include_router(tof_mapper_engine.make_router(auth, lambda: st.mode, cache))
    app.include_router(global_ag_and_3d.make_router(auth))
    app.include_router(geocode.make_router(auth, geocode_fetch or geocode.http_fetch))
    app.include_router(routing.make_router(auth, routing_post or routing.http_post))

    @app.post("/v1/terrain/dem", dependencies=[Depends(auth)])
    def terrain_dem(req: DemRequest):
        ring = prepare(req.region, st.max_composite_days, 0)
        try:
            g = dem.grid_for(ring, req.buffer_m)
        except dem.DemError as e:
            raise HTTPException(422, str(e))
        src_id = req.source_id if req.source_id in dem.DEM_SOURCE_MAP else "NASA_SRTM_30M"
        k = key("dem-2", st.mode, g, src_id)
        if st.mode == "mock":
            return cache.get_or_set(k, 3600, lambda: dem.mock_dem(g, src_id))

        def execute():
            try:
                return dem.live_dem(g, src_id)
            except dem.DemError as e:
                raise HTTPException(502, str(e))

        return cache.get_or_set(k, 24 * 3600, lambda: live(execute))

    @app.get("/v1/health")
    def health():
        return {"ok": True, "mode": st.mode, "ee_project": st.ee_project or None}

    @app.get("/v1/gee/status", dependencies=[Depends(auth)])
    def gee_status():
        cred_path = Path.home() / ".config" / "earthengine" / "credentials"
        return {
            "mode": st.mode,
            "live": st.mode == "live",
            "ee_project": st.ee_project or None,
            "credentials_found": cred_path.is_file(),
        }

    @app.post("/v1/gee/configure", dependencies=[Depends(auth)])
    def gee_configure(payload: dict):
        nonlocal st, ee_service
        import dataclasses
        proj = str(payload.get("ee_project") or "").strip()
        if not proj:
            raise HTTPException(422, "Vui lòng nhập mã Google Cloud Project (EE_PROJECT) đã bật Earth Engine API.")
        try:
            from . import ee_service as _ee_service
            _ee_service.init(proj)
            ee_service = _ee_service
            st = dataclasses.replace(st, mode="live", ee_project=proj)
            cache._d.clear()
            env_file = Path(__file__).resolve().parents[1] / ".env"
            lines = env_file.read_text(encoding="utf-8").splitlines() if env_file.is_file() else []
            lines = [ln for ln in lines if not ln.strip().startswith(("EE_PROJECT=", "VWING_EE_MODE="))]
            lines.append(f"EE_PROJECT={proj}")
            lines.append("VWING_EE_MODE=live")
            env_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
            return {"ok": True, "mode": st.mode, "ee_project": st.ee_project}
        except Exception as exc:
            raise HTTPException(400, f"Không thể khởi tạo Google Earth Engine với project '{proj}': {exc}")

    @app.get("/v1/satellite/indices", dependencies=[Depends(auth)])
    def list_indices():
        return [{"key": d.key, "title": d.title, "description": d.description, "caution": d.caution, "bands": ix.required_bands(d.key)} for d in ix.INDICES.values()]

    @app.post("/v1/satellite/index", response_model=IndexResult, dependencies=[Depends(auth)])
    def index(req: IndexRequest):
        ring = prepare(req.region, st.max_composite_days, (req.end - req.start).days)
        k = key("idx", ALGO_VERSION, st.mode, ring, req.index, req.start, req.end, req.max_cloud_pct)
        if st.mode == "mock":
            return cache.get_or_set(k, 600, lambda: mock_service.index_result(ring, req.index, req.start, req.end, req.max_cloud_pct, st.public_base_url))
        return cache.get_or_set(k, 900, lambda: live(ee_service.index_result, ring, req.index, req.start, req.end, req.max_cloud_pct))

    @app.post("/v1/satellite/timeseries", response_model=SeriesResult, dependencies=[Depends(auth)])
    def timeseries(req: IndexRequest):
        ring = prepare(req.region, st.max_series_days, (req.end - req.start).days)
        k = key("ser", ALGO_VERSION, st.mode, ring, req.index, req.start, req.end, req.max_cloud_pct)
        if st.mode == "mock":
            return cache.get_or_set(k, 3600, lambda: mock_service.series_result(ring, req.index, req.start, req.end, req.max_cloud_pct))
        return cache.get_or_set(k, 6 * 3600, lambda: live(ee_service.series_result, ring, req.index, req.start, req.end, req.max_cloud_pct))

    @app.post("/v1/satellite/water-change", response_model=WaterResult, dependencies=[Depends(auth)])
    def water_change(req: WaterChangeRequest):
        span = max((req.baseline_end - req.baseline_start).days, (req.event_end - req.event_start).days)
        ring = prepare(req.region, st.max_composite_days, span)
        k = key("wat", ALGO_VERSION, st.mode, ring, req.baseline_start, req.baseline_end, req.event_start, req.event_end)
        if st.mode == "mock":
            return cache.get_or_set(k, 600, lambda: mock_service.water_change(ring, req.baseline_start, req.baseline_end, req.event_start, req.event_end, st.public_base_url))
        return cache.get_or_set(k, 900, lambda: live(ee_service.water_change, ring, req.baseline_start, req.baseline_end, req.event_start, req.event_end))

    @app.post("/v1/satellite/region-workflow", response_model=RegionWorkflowResult,
              dependencies=[Depends(auth)])
    def region_report(req: RegionWorkflowRequest):
        span = max((req.baseline_end - req.baseline_start).days,
                   (req.comparison_end - req.comparison_start).days)
        ring = prepare(req.region, min(93, st.max_composite_days), span)
        k = key("region-workflow-1", st.mode, ring, req.model_dump(mode="json"))
        if st.mode == "mock":
            return cache.get_or_set(k, 600, lambda: region_workflow.mock_result(ring, req))

        def execute():
            try:
                return region_workflow.live_result(ring, req)
            except ValueError as error:
                raise HTTPException(422, str(error))

        return cache.get_or_set(k, 900, lambda: live(execute))

    @app.get("/v1/satellite/land-cover/sources", dependencies=[Depends(auth)])
    def lulc_sources():
        return garden_analysis.list_lulc_sources()

    @app.get("/v1/satellite/crop-profiles", dependencies=[Depends(auth)])
    def crop_profiles():
        return garden_analysis.list_crop_profiles()

    @app.post("/v1/garden/spacing-grid-qr", dependencies=[Depends(auth)])
    def garden_spacing_grid_qr_endpoint(payload: dict):
        raw_ring = payload.get("ring") or (payload.get("region") or {}).get("ring") or []
        try:
            ring = geo.validate_ring(raw_ring)
        except geo.GeoError as e:
            raise HTTPException(422, str(e))
        crop_type = str(payload.get("crop_type") or "durian")
        region_name = str(payload.get("region_name") or "Vùng đã khoanh")
        max_trees = int(payload.get("max_returned_trees") or 5000)
        return garden_analysis.compute_area_spacing_trees_and_qr(
            ring=ring,
            crop_type=crop_type,
            region_name=region_name,
            max_returned_trees=max_trees,
        )

    @app.post("/v1/satellite/garden-analysis", response_model=GardenAnalysisResult,
              dependencies=[Depends(auth)])
    def analyze_garden_endpoint(req: GardenAnalysisRequest):
        ring = prepare(req.region, st.max_composite_days, (req.end - req.start).days)
        k = key("garden-analysis-3", ALGO_VERSION, st.mode, ring, req.model_dump(mode="json"))
        if st.mode == "mock":
            return cache.get_or_set(k, 600, lambda: garden_analysis.mock_analyze(ring, req, st.public_base_url))
        return cache.get_or_set(k, 900, lambda: live(garden_analysis.live_analyze, ring, req, st.public_base_url))

    @app.post("/v1/satellite/gee-garden-clarity", response_model=GeeGardenClarityResult,
              dependencies=[Depends(auth)])
    def gee_garden_clarity_endpoint(req: GeeGardenClarityRequest):
        days = (req.end - req.start).days if (req.start and req.end and req.end > req.start) else 30
        ring = prepare(req.region, st.max_composite_days, min(days, st.max_composite_days))
        k = key("gee-garden-clarity-3", ALGO_VERSION, st.mode, ring, req.model_dump(mode="json"))
        if st.mode == "mock":
            return cache.get_or_set(k, 600, lambda: garden_analysis.mock_gee_garden_clarity(ring, req, st.public_base_url))
        return cache.get_or_set(k, 900, lambda: live(garden_analysis.live_gee_garden_clarity, ring, req, st.public_base_url))

    @app.get("/v1/satellite/ag-research-catalog", dependencies=[Depends(auth)])
    def ag_research_catalog_endpoint():
        return ag_research_9papers.list_research_catalog()

    @app.post("/v1/satellite/ag-research-suite", dependencies=[Depends(auth)])
    def ag_research_suite_endpoint(req: ag_research_9papers.AgResearchSuiteRequest):
        ring = prepare(req.region, st.max_composite_days, 30)
        k = key("ag-research-suite-1", ALGO_VERSION, st.mode, ring, req.model_dump(mode="json"))
        return cache.get_or_set(
            k,
            600,
            lambda: ag_research_9papers.analyze_ag_research_suite(
                ring,
                req,
                base_url=st.public_base_url,
                live_mode=(st.mode == "live"),
            ),
        )

    @app.get("/v1/mock/image/{image_id}.png", dependencies=[Depends(auth)])
    def mock_image(image_id: str):
        png = mock_service.get_image(image_id)
        if png is None:
            raise HTTPException(404, "Không có ảnh.")
        return Response(png, media_type="image/png", headers={"Cache-Control": "no-store"})

    def require_sr_live():
        if st.mode != "live":
            raise HTTPException(503, "Super-Resolution cần backend live và mô hình AI thật; chế độ mock không tạo ảnh AI.")

    @app.post("/v1/satellite/super-resolution", response_model=SrJob, status_code=202,
              dependencies=[Depends(auth), Depends(require_sr_live)])
    def super_resolution(req: SuperResolutionRequest):
        ring = prepare(req.region, 93, (req.end - req.start).days)
        if geo.area_ha(ring) > 25:
            raise HTTPException(422, "Super-Resolution hỗ trợ vùng tối đa 25 ha.")
        try:
            return sr_jobs.submit(req, ring)
        except QueueFull:
            raise HTTPException(429, "Hàng đợi Super-Resolution đã đầy. Thử lại sau.")

    @app.get("/v1/satellite/super-resolution/{job_id}", response_model=SrJob,
             dependencies=[Depends(auth), Depends(require_sr_live)])
    def super_resolution_job(job_id: str):
        try:
            return sr_jobs.get(job_id)
        except JobNotFound:
            raise HTTPException(404, "Không tìm thấy tác vụ Super-Resolution hoặc tác vụ đã hết hạn.")

    @app.get("/v1/satellite/super-resolution/{job_id}/files/{name}",
             dependencies=[Depends(auth), Depends(require_sr_live)])
    def super_resolution_file(job_id: str, name: str):
        try:
            path, media_type, release = sr_jobs.acquire_artifact(job_id, name)
        except JobNotFound:
            raise HTTPException(404, "Không tìm thấy tệp Super-Resolution.")
        return _ArtifactResponse(path, media_type=media_type, filename=name,
                                 headers={"Cache-Control": "no-store"}, release=release)

    return app


def _default_app() -> FastAPI:
    return create_app()


try:
    app = _default_app()
except RuntimeError:       # thiếu biến môi trường: vẫn import được mô-đun (ví dụ khi chạy pytest)
    app = None
