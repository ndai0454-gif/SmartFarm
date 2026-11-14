"""API OBIA cho ứng dụng: /v1/obia/*. Mọi đường đều qua `auth` (Bearer) như các router còn lại.

Luồng dùng: tạo workspace → PUT ảnh (octet-stream, không cần python-multipart) → POST job từng bước →
GET job đến khi xong → tải tệp kết quả (GeoTIFF/PNG/QML/CSV).
"""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field

from . import obia_pipeline as pipe
from .obia_jobs import (BadUpload, JobNotFound, ObiaJobManager, PrerequisiteMissing, QueueFull, UploadTooLarge,
                        WorkspaceBusy, WorkspaceNotFound)


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class WorkspaceCreate(Strict):
    name: str = Field(default="", max_length=80)


class SegmentParams(Strict):
    n_segments: int = Field(default=5000, ge=100, le=300000)
    compactness: float = Field(default=10.0, gt=0, le=100)
    sigma: float = Field(default=1.0, ge=0, le=10)
    device: str = Field(default="auto", pattern="^(auto|cpu|gpu)$")
    max_pixels: Optional[int] = Field(default=None, ge=1_000_000, le=400_000_000)
    extent: Optional[list[int]] = Field(default=None, min_length=4, max_length=4,
                                        description="[cột, hàng, rộng, cao] theo pixel gốc của ảnh")


class DiscoverParams(Strict):
    n_groups: int = Field(default=12, ge=2, le=40)
    use_deep: bool = True
    use_features: bool = True
    exclude_edge: bool = True
    examples_per_group: int = Field(default=5, ge=1, le=20)


class ClassSpec(Strict):
    color: Optional[str] = Field(default=None, max_length=7)
    groups: list[str] = Field(default_factory=list, max_length=40)
    segments: list[int] = Field(default_factory=list, max_length=20000)
    points: list[tuple[float, float]] = Field(default_factory=list, max_length=20000,
                                              description="(x, y) theo CRS của orthomosaic")


class ClassifyParams(Strict):
    classes: dict[str, ClassSpec] = Field(min_length=2, max_length=30)
    n_trees: int = Field(default=100, ge=10, le=500)
    sieve_px: int = Field(default=0, ge=0, le=500)
    use_deep: bool = True


class SampleParams(Strict):
    total: int = Field(default=200, ge=20, le=5000)
    min_per_class: int = Field(default=20, ge=1, le=500)
    seed: int = Field(default=42, ge=0, le=2**31 - 1)


class AccuracyParams(Strict):
    class_map: Optional[dict[str, Optional[str]]] = None
    only_inside: bool = True


class AnalyzeRegionRequest(Strict):
    ring: list[list[float]] = Field(default_factory=list, description="Danh sách [lon, lat] của đa giác vùng vườn")
    crop_type: str = Field(default="durian", max_length=40)
    n_segments: int = Field(default=168, ge=40, le=500)
    compactness: float = Field(default=0.15, gt=0.0, le=100.0)
    flight_altitude_m: float = Field(default=60.0, gt=5.0, le=500.0)


PARAM_MODELS = {"segment": SegmentParams, "discover": DiscoverParams, "classify": ClassifyParams,
                "sample": SampleParams, "accuracy": AccuracyParams}


def make_router(auth, manager: ObiaJobManager) -> APIRouter:
    router = APIRouter(prefix="/v1/obia", tags=["obia"], dependencies=[Depends(auth)])

    def ws_or_404(fn, *a):
        try:
            return fn(*a)
        except WorkspaceNotFound:
            raise HTTPException(404, "Không tìm thấy workspace OBIA hoặc đã hết hạn.")

    def submit(ws_id: str, stage: str, model: BaseModel):
        try:
            return manager.submit(ws_id, stage, model.model_dump(mode="json"))
        except WorkspaceNotFound:
            raise HTTPException(404, "Không tìm thấy workspace OBIA hoặc đã hết hạn.")
        except WorkspaceBusy:
            raise HTTPException(409, "Workspace đang có job chạy. Đợi xong hoặc huỷ job đó.")
        except QueueFull:
            raise HTTPException(429, "Hàng đợi OBIA đã đầy. Thử lại sau.")
        except PrerequisiteMissing as exc:
            raise HTTPException(409, str(exc))

    @router.get("/status")
    def status():
        return pipe.engine_status()

    @router.post("/analyze-region")
    async def analyze_region(req: AnalyzeRegionRequest):
        try:
            return await run_in_threadpool(
                pipe.analyze_region_direct,
                req.ring,
                req.crop_type,
                req.n_segments,
                req.compactness,
                req.flight_altitude_m,
            )
        except pipe.ObiaUnavailable as exc:
            raise HTTPException(503, str(exc))
        except pipe.ObiaInputError as exc:
            raise HTTPException(422, str(exc))

    @router.post("/workspaces", status_code=201)
    def create_workspace(req: WorkspaceCreate):
        return manager.create_workspace(req.name)

    @router.get("/workspaces/{ws_id}")
    def get_workspace(ws_id: str):
        return ws_or_404(manager.describe_workspace, ws_id)

    @router.delete("/workspaces/{ws_id}", status_code=204)
    def delete_workspace(ws_id: str):
        try:
            manager.delete_workspace(ws_id)
        except WorkspaceNotFound:
            raise HTTPException(404, "Không tìm thấy workspace OBIA.")
        except WorkspaceBusy:
            raise HTTPException(409, "Workspace đang có job chạy.")

    @router.put("/workspaces/{ws_id}/inputs/{kind}")
    async def upload_input(ws_id: str, kind: str, request: Request):
        """Thân yêu cầu là byte thô (Content-Type: application/octet-stream)."""
        try:
            part, final = manager.upload_paths(ws_id, kind)
        except WorkspaceNotFound:
            raise HTTPException(404, "Không tìm thấy workspace OBIA hoặc đã hết hạn.")
        except WorkspaceBusy:
            raise HTTPException(409, "Workspace đang có job chạy.")
        except BadUpload as exc:
            raise HTTPException(422, str(exc))
        declared = request.headers.get("content-length")
        if declared and declared.isdigit() and int(declared) > manager.max_upload_bytes:
            raise HTTPException(413, f"Tệp vượt giới hạn {manager.max_upload_bytes // (1024 * 1024)} MB.")
        size = 0
        try:
            with open(part, "wb") as fh:
                async for chunk in request.stream():
                    size += len(chunk)
                    if size > manager.max_upload_bytes:
                        raise UploadTooLarge()
                    fh.write(chunk)
            return await run_in_threadpool(manager.finish_upload, ws_id, kind, part, final)
        except UploadTooLarge:
            part.unlink(missing_ok=True)
            raise HTTPException(413, f"Tệp vượt giới hạn {manager.max_upload_bytes // (1024 * 1024)} MB.")
        except BadUpload as exc:
            raise HTTPException(422, str(exc))
        except BaseException:
            part.unlink(missing_ok=True)
            raise

    @router.post("/workspaces/{ws_id}/jobs/segment", status_code=202)
    def job_segment(ws_id: str, req: SegmentParams):
        return submit(ws_id, "segment", req)

    @router.post("/workspaces/{ws_id}/jobs/discover", status_code=202)
    def job_discover(ws_id: str, req: DiscoverParams):
        return submit(ws_id, "discover", req)

    @router.post("/workspaces/{ws_id}/jobs/classify", status_code=202)
    def job_classify(ws_id: str, req: ClassifyParams):
        return submit(ws_id, "classify", req)

    @router.post("/workspaces/{ws_id}/jobs/sample", status_code=202)
    def job_sample(ws_id: str, req: SampleParams):
        return submit(ws_id, "sample", req)

    @router.post("/workspaces/{ws_id}/jobs/accuracy", status_code=202)
    def job_accuracy(ws_id: str, req: AccuracyParams):
        return submit(ws_id, "accuracy", req)

    @router.get("/workspaces/{ws_id}/jobs")
    def list_jobs(ws_id: str):
        return ws_or_404(manager.list_jobs, ws_id)

    @router.get("/jobs/{job_id}")
    def get_job(job_id: str):
        try:
            return manager.get_job(job_id)
        except JobNotFound:
            raise HTTPException(404, "Không tìm thấy job OBIA.")

    @router.post("/jobs/{job_id}/cancel")
    def cancel_job(job_id: str):
        try:
            return manager.cancel(job_id)
        except JobNotFound:
            raise HTTPException(404, "Không tìm thấy job OBIA.")

    @router.get("/workspaces/{ws_id}/files/{name}")
    def get_file(ws_id: str, name: str):
        try:
            path, media = manager.output_file(ws_id, name)
        except WorkspaceNotFound:
            raise HTTPException(404, "Không tìm thấy tệp kết quả.")
        return FileResponse(path, media_type=media, filename=name, headers={"Cache-Control": "no-store"})

    return router
