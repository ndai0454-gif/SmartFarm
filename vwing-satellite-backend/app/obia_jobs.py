"""Workspace + job OBIA: lưu trên đĩa, chạy nền bằng ThreadPoolExecutor, sống sót qua khởi động lại.

Mỗi workspace là một thư mục uuid (input/, state/, out/, jobs/). Mỗi workspace chỉ chạy một job tại một
thời điểm; hàng đợi toàn cục có giới hạn. Trạng thái job được ghi ra jobs/<id>.json mỗi lần đổi, nên ứng dụng
vẫn đọc được kết quả cũ sau khi máy chủ khởi động lại (job đang chạy lúc tắt máy sẽ báo "interrupted").
"""
from __future__ import annotations

import json
import re
import shutil
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Callable, Optional
from uuid import uuid4

from . import obia_pipeline as pipe

WS_RE = re.compile(r"^[0-9a-f]{32}$")
FILE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,80}$")
INPUT_KINDS = {"ortho": "ortho.tif", "dsm": "dsm.tif", "reference": "reference.csv"}
ACTIVE = ("queued", "running")


class WorkspaceNotFound(Exception):
    pass


class JobNotFound(Exception):
    pass


class WorkspaceBusy(Exception):
    pass


class QueueFull(Exception):
    pass


class PrerequisiteMissing(Exception):
    pass


class UploadTooLarge(Exception):
    pass


class BadUpload(Exception):
    pass


def _now() -> float:
    return time.time()


class ObiaJobManager:
    def __init__(self, root: Path, *, runner: Optional[Callable] = None, max_workers: int = 1,
                 max_queue: int = 8, ttl_seconds: float = 72 * 3600, max_upload_bytes: int = 600 * 1024 * 1024):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._runner = runner or pipe.run_stage
        self._ttl = ttl_seconds
        self._max_queue = max_queue
        self.max_upload_bytes = max_upload_bytes
        self._pool = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="obia")
        self._lock = threading.RLock()
        self._ctx: dict[str, pipe.StageContext] = {}
        self._recover()

    # ------------------------------------------------------------------ housekeeping
    def close(self) -> None:
        for ctx in list(self._ctx.values()):
            ctx.request_cancel()
        self._pool.shutdown(wait=False, cancel_futures=True)

    def _recover(self) -> None:
        for jf in self.root.glob("*/jobs/*.json"):
            try:
                job = json.loads(jf.read_text(encoding="utf-8"))
            except Exception:
                continue
            if job.get("status") in ACTIVE:
                job.update(status="failed", error="interrupted: máy chủ đã khởi động lại khi job đang chạy.",
                           finished=_now())
                self._save_job(job)

    def purge_expired(self) -> int:
        n = 0
        with self._lock:
            for d in self.root.iterdir():
                if not (d.is_dir() and WS_RE.match(d.name)):
                    continue
                meta = self._read_meta(d)
                if meta is None or self._active_job(d.name) is not None:
                    continue
                if _now() - meta.get("updated", meta.get("created", 0)) > self._ttl:
                    shutil.rmtree(d, ignore_errors=True)
                    n += 1
        return n

    # ------------------------------------------------------------------ workspaces
    def ws_path(self, ws_id: str) -> Path:
        if not WS_RE.match(ws_id or ""):
            raise WorkspaceNotFound(ws_id)
        p = self.root / ws_id
        if not (p / "meta.json").is_file():
            raise WorkspaceNotFound(ws_id)
        return p

    @staticmethod
    def _read_meta(p: Path) -> Optional[dict]:
        try:
            return json.loads((p / "meta.json").read_text(encoding="utf-8"))
        except Exception:
            return None

    def _touch(self, p: Path) -> None:
        meta = self._read_meta(p) or {}
        meta["updated"] = _now()
        (p / "meta.json").write_text(json.dumps(meta), encoding="utf-8")

    def create_workspace(self, name: str = "") -> dict:
        self.purge_expired()
        ws_id = uuid4().hex
        p = self.root / ws_id
        for sub in ("input", "state", "out", "jobs"):
            (p / sub).mkdir(parents=True)
        meta = {"id": ws_id, "name": name.strip()[:80], "created": _now(), "updated": _now()}
        (p / "meta.json").write_text(json.dumps(meta), encoding="utf-8")
        return self.describe_workspace(ws_id)

    def delete_workspace(self, ws_id: str) -> None:
        p = self.ws_path(ws_id)
        with self._lock:
            if self._active_job(ws_id) is not None:
                raise WorkspaceBusy(ws_id)
            shutil.rmtree(p, ignore_errors=True)

    def describe_workspace(self, ws_id: str) -> dict:
        p = self.ws_path(ws_id)
        meta = self._read_meta(p) or {}
        inputs = {k: (p / "input" / f).is_file() for k, f in INPUT_KINDS.items()}
        state = p / "state"
        stages = {
            "segment": (state / "labels.npy").is_file(),
            "discover": (state / "groups.csv").is_file(),
            "classify": (state / "classify.json").is_file(),
        }
        files = sorted(f.name for f in (p / "out").iterdir() if f.is_file())
        grid = None
        if (state / "grid.json").is_file():
            grid = json.loads((state / "grid.json").read_text(encoding="utf-8"))
        active = self._active_job(ws_id)
        return {"id": ws_id, "name": meta.get("name", ""), "created": meta.get("created"), "inputs": inputs,
                "stages_done": stages, "files": files, "grid": grid,
                "active_job": active["id"] if active else None}

    # ------------------------------------------------------------------ uploads
    def upload_paths(self, ws_id: str, kind: str) -> tuple[Path, Path]:
        p = self.ws_path(ws_id)
        if kind not in INPUT_KINDS:
            raise BadUpload(f"Loại tệp '{kind}' không hợp lệ (ortho, dsm, reference).")
        if self._active_job(ws_id) is not None:
            raise WorkspaceBusy(ws_id)
        final = p / "input" / INPUT_KINDS[kind]
        return final.with_name(final.name + f".{uuid4().hex[:8]}.part"), final

    def finish_upload(self, ws_id: str, kind: str, part: Path, final: Path) -> dict:
        """Kiểm tra tệp vừa nhận rồi đổi tên nguyên tử; hỏng thì xoá và báo lỗi."""
        try:
            if part.stat().st_size == 0:
                raise BadUpload("Tệp rỗng.")
            if kind in ("ortho", "dsm"):
                self._check_raster(part, kind)
            else:
                self._check_reference(part)
        except BadUpload:
            part.unlink(missing_ok=True)
            raise
        part.replace(final)
        # nhập mới làm các kết quả cũ không còn khớp
        p = self.ws_path(ws_id)
        if kind in ("ortho", "dsm"):
            for stale in ("labels.npy", "features.csv", "grid.json", "groups.csv", "groups.json", "deep.npz",
                          "classify.json", "training.json"):
                (p / "state" / stale).unlink(missing_ok=True)
        self._touch(p)
        return {"kind": kind, "bytes": final.stat().st_size}

    @staticmethod
    def _check_raster(path: Path, kind: str) -> None:
        import rasterio
        try:
            with rasterio.open(path) as src:
                if src.crs is None:
                    raise BadUpload("Ảnh chưa có hệ toạ độ (CRS); cần GeoTIFF đã georeference.")
                if kind == "ortho" and src.count < 3:
                    raise BadUpload("Orthomosaic cần ít nhất 3 băng (RGB).")
                if src.width < 64 or src.height < 64:
                    raise BadUpload("Ảnh quá nhỏ (tối thiểu 64×64 pixel).")
        except BadUpload:
            raise
        except Exception as exc:
            raise BadUpload("Không đọc được tệp như GeoTIFF.") from exc

    @staticmethod
    def _check_reference(path: Path) -> None:
        try:
            head = path.read_text(encoding="utf-8-sig").splitlines()[:1]
        except Exception as exc:
            raise BadUpload("File tham chiếu phải là CSV UTF-8.") from exc
        cols = {c.strip().lower() for c in (head[0].split(",") if head else [])}
        if not ({"x", "lon", "longitude", "easting"} & cols and {"y", "lat", "latitude", "northing"} & cols
                and {"class", "reference_class", "label", "class_name"} & cols):
            raise BadUpload("CSV cần cột x, y và class (hoặc lon/lat, label...). Toạ độ theo CRS của orthomosaic.")

    # ------------------------------------------------------------------ jobs
    def _job_file(self, ws_id: str, job_id: str) -> Path:
        return self.root / ws_id / "jobs" / f"{job_id}.json"

    def _save_job(self, job: dict) -> None:
        f = self._job_file(job["workspace_id"], job["id"])
        if not f.parent.is_dir():
            return
        tmp = f.with_suffix(".tmp")
        tmp.write_text(json.dumps(job, ensure_ascii=False), encoding="utf-8")
        tmp.replace(f)

    def _all_jobs(self, ws_id: str) -> list[dict]:
        out = []
        for jf in (self.root / ws_id / "jobs").glob("*.json"):
            try:
                out.append(json.loads(jf.read_text(encoding="utf-8")))
            except Exception:
                pass
        return sorted(out, key=lambda j: j.get("created", 0))

    def _active_job(self, ws_id: str) -> Optional[dict]:
        for j in self._all_jobs(ws_id) if (self.root / ws_id / "jobs").is_dir() else []:
            if j["status"] in ACTIVE:
                return j
        return None

    def list_jobs(self, ws_id: str) -> list[dict]:
        self.ws_path(ws_id)
        return [self._public(j) for j in self._all_jobs(ws_id)]

    @staticmethod
    def _public(job: dict) -> dict:
        return {k: v for k, v in job.items() if k != "params_internal"}

    def _check_prereq(self, p: Path, stage: str) -> None:
        state, inp, out = p / "state", p / "input", p / "out"
        need = {
            "segment": [(inp / "ortho.tif", "Chưa tải orthomosaic lên.")],
            "discover": [(state / "labels.npy", "Chưa chạy bước segment.")],
            "classify": [(state / "labels.npy", "Chưa chạy bước segment.")],
            "sample": [(state / "classify.json", "Chưa chạy bước classify.")],
            "accuracy": [(state / "classify.json", "Chưa chạy bước classify."),
                         (inp / "reference.csv", "Chưa tải file điểm tham chiếu.")],
        }[stage]
        for path, msg in need:
            if not path.is_file():
                raise PrerequisiteMissing(msg)

    def submit(self, ws_id: str, stage: str, params: dict) -> dict:
        if stage not in pipe.STAGES:
            raise KeyError(stage)
        p = self.ws_path(ws_id)
        with self._lock:
            if self._active_job(ws_id) is not None:
                raise WorkspaceBusy(ws_id)
            queued = sum(1 for d in self.root.iterdir() if d.is_dir() and WS_RE.match(d.name)
                         and (a := self._active_job(d.name)) is not None)
            if queued >= self._max_queue:
                raise QueueFull()
            self._check_prereq(p, stage)
            job = {"id": uuid4().hex, "workspace_id": ws_id, "stage": stage, "status": "queued",
                   "progress": 0.0, "message": "Đang chờ", "params": params, "created": _now(),
                   "started": None, "finished": None, "error": None, "result": None}
            self._save_job(job)
            ctx = pipe.StageContext()
            ctx._progress = lambda f, m, jid=job["id"], w=ws_id: self._progress(w, jid, f, m)
            self._ctx[job["id"]] = ctx
            self._touch(p)
            self._pool.submit(self._run, job["id"], ws_id)
        return self._public(job)

    def _progress(self, ws_id: str, job_id: str, fraction: float, message: str) -> None:
        with self._lock:
            try:
                job = json.loads(self._job_file(ws_id, job_id).read_text(encoding="utf-8"))
            except Exception:
                return
            if job["status"] == "running":
                job.update(progress=round(fraction, 3), message=message)
                self._save_job(job)

    def _update(self, ws_id: str, job_id: str, **changes) -> Optional[dict]:
        with self._lock:
            try:
                job = json.loads(self._job_file(ws_id, job_id).read_text(encoding="utf-8"))
            except Exception:
                return None
            job.update(changes)
            self._save_job(job)
            return job

    def _run(self, job_id: str, ws_id: str) -> None:
        ctx = self._ctx.get(job_id)
        job = self._update(ws_id, job_id, status="running", started=_now(), message="Đang chạy")
        if job is None or ctx is None:
            return
        if ctx.cancelled():
            self._update(ws_id, job_id, status="cancelled", finished=_now(), message="Đã huỷ")
            return
        p = self.root / ws_id
        try:
            result = self._runner(job["stage"], p, job["params"], ctx)
            result = dict(result or {}, attribution=pipe.ATTRIBUTION, notice=pipe.ENGINE_NOTICE)
            self._update(ws_id, job_id, status="succeeded", progress=1.0, message="Xong", result=result,
                         finished=_now())
        except pipe.ObiaCancelled:
            self._update(ws_id, job_id, status="cancelled", finished=_now(), message="Đã huỷ")
        except Exception as exc:
            if type(exc).__name__ == "Cancelled":          # obia_engine.Cancelled
                self._update(ws_id, job_id, status="cancelled", finished=_now(), message="Đã huỷ")
            elif isinstance(exc, (pipe.ObiaInputError, pipe.ObiaUnavailable)):
                self._update(ws_id, job_id, status="failed", finished=_now(), error=str(exc), message="Lỗi đầu vào")
            else:
                import logging
                logging.getLogger("vwing.obia").exception("Job OBIA %s lỗi", job_id)
                self._update(ws_id, job_id, status="failed", finished=_now(),
                             error="Lỗi nội bộ khi chạy OBIA; xem nhật ký máy chủ.", message="Lỗi")
        finally:
            self._ctx.pop(job_id, None)
            try:
                self._touch(p)
            except Exception:
                pass

    def _find_job(self, job_id: str) -> tuple[str, dict]:
        if not WS_RE.match(job_id or ""):
            raise JobNotFound(job_id)
        for d in self.root.iterdir():
            f = d / "jobs" / f"{job_id}.json"
            if d.is_dir() and WS_RE.match(d.name) and f.is_file():
                return d.name, json.loads(f.read_text(encoding="utf-8"))
        raise JobNotFound(job_id)

    def get_job(self, job_id: str) -> dict:
        return self._public(self._find_job(job_id)[1])

    def cancel(self, job_id: str) -> dict:
        ws_id, job = self._find_job(job_id)
        if job["status"] in ACTIVE:
            ctx = self._ctx.get(job_id)
            if ctx is not None:
                ctx.request_cancel()
            if job["status"] == "queued":
                job = self._update(ws_id, job_id, status="cancelled", finished=_now(), message="Đã huỷ") or job
        return self._public(job)

    # ------------------------------------------------------------------ files
    def output_file(self, ws_id: str, name: str) -> tuple[Path, str]:
        p = self.ws_path(ws_id)
        if not FILE_RE.match(name):
            raise WorkspaceNotFound(name)
        f = p / "out" / name
        if not f.is_file() or f.resolve().parent != (p / "out").resolve():
            raise WorkspaceNotFound(name)
        media = {".png": "image/png", ".tif": "image/tiff", ".geojson": "application/geo+json",
                 ".json": "application/json", ".csv": "text/csv", ".qml": "application/xml",
                 ".gpkg": "application/geopackage+sqlite3"}.get(f.suffix.lower(), "application/octet-stream")
        return f, media
