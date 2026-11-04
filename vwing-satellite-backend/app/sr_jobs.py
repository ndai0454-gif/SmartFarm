"""Bounded, single-worker jobs and authenticated artifact storage for real SR."""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from dataclasses import dataclass
import logging
import os
from pathlib import Path
import re
import shutil
import threading
import time
from typing import Callable
from uuid import uuid4

from .schemas import SuperResolutionRequest
from .sr_errors import SrInputError

log = logging.getLogger("vwing.sat.sr")
API_PREFIX = "/v1/satellite/super-resolution"
FILES = {
    "original_preview": ("original.png", "image/png"),
    "enhanced_preview": ("enhanced.png", "image/png"),
    "original_geotiff": ("original.tif", "image/tiff"),
    "enhanced_geotiff": ("enhanced.tif", "image/tiff"),
    "metadata": ("metadata.json", "application/json"),
}
MEDIA_TYPES = {filename: mime for filename, mime in FILES.values()}
Generator = Callable[[SuperResolutionRequest, list, Path], dict]


class QueueFull(Exception):
    pass


class JobNotFound(Exception):
    pass


@dataclass
class _Job:
    job_id: str
    created: float
    state: str = "queued"
    result: dict | None = None
    error: str | None = None
    readers: int = 0

    def snapshot(self):
        return deepcopy({"job_id": self.job_id, "state": self.state,
                         "result": self.result, "error": self.error})


class SrJobManager:
    def __init__(self, output_dir: Path, generator: Generator | None = None,
                 *, max_jobs: int = 20, ttl_seconds: float = 86400):
        self.output_dir = Path(output_dir).resolve()
        self.generator = generator
        self.max_jobs = max_jobs
        self.ttl_seconds = ttl_seconds
        self._jobs: dict[str, _Job] = {}
        self._lock = threading.RLock()
        self._executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="vwing-sr")
        self._closed = False
        self._storage_lock = None

    def _remove_artifacts(self, job_id: str) -> bool:
        path = self.output_dir / job_id
        # Never recursively remove a symlink or a path outside this store.
        if not re.fullmatch(r"[a-f0-9]{32}", job_id) or path.is_symlink() or path.resolve().parent != self.output_dir:
            return False
        try:
            if path.exists():
                shutil.rmtree(path)
            return True
        except OSError:
            log.warning("Could not remove expired SR artifacts: %s", job_id)
            return False

    def _acquire_store(self):
        if self._storage_lock is not None:
            return
        self.output_dir.mkdir(parents=True, exist_ok=True)
        handle = (self.output_dir / ".worker.lock").open("a+b")
        try:
            # Hold one OS lock for the entire manager lifetime, including downloads.
            # This prevents a second server worker from pruning this worker's files.
            handle.seek(0)
            if os.name == "nt":
                import msvcrt
                if handle.read(1) == b"":
                    handle.write(b"0")
                    handle.flush()
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            handle.close()
            raise QueueFull() from None
        self._storage_lock = handle
        # Jobs are intentionally session-local. After a restart, old UUID folders
        # are inaccessible; remove them only after obtaining exclusive ownership.
        for child in self.output_dir.iterdir():
            if re.fullmatch(r"[a-f0-9]{32}", child.name) and not self._remove_artifacts(child.name):
                self._release_store()
                raise QueueFull()

    def _release_store(self):
        handle, self._storage_lock = self._storage_lock, None
        if handle is None:
            return
        try:
            handle.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        finally:
            handle.close()

    def _prune(self, *, make_room: bool = False):
        now = time.monotonic()
        for job in sorted(self._jobs.values(), key=lambda j: j.created):
            if job.state in ("queued", "running") or job.readers:
                continue
            if now - job.created < self.ttl_seconds and not (make_room and len(self._jobs) >= self.max_jobs):
                continue
            if self._remove_artifacts(job.job_id):
                del self._jobs[job.job_id]

    def submit(self, req: SuperResolutionRequest, ring: list) -> dict:
        with self._lock:
            if self._closed or sum(j.state in ("queued", "running") for j in self._jobs.values()) >= 2:
                raise QueueFull()
            self._acquire_store()
            self._prune(make_room=True)
            if len(self._jobs) >= self.max_jobs:
                raise QueueFull()
            job_id = uuid4().hex
            (self.output_dir / job_id).mkdir()
            job = _Job(job_id, time.monotonic())
            self._jobs[job_id] = job
            # Capture queued state before the worker can acquire the same lock.
            snapshot = job.snapshot()
            self._executor.submit(self._run, job, req.model_copy(deep=True), deepcopy(ring))
            return snapshot

    def _run(self, job: _Job, req: SuperResolutionRequest, ring: list):
        with self._lock:
            job.state = "running"
        try:
            generate = self.generator
            if generate is None:
                from .sr_engine import generate_sr
                generate = generate_sr
            directory = self.output_dir / job.job_id
            result = deepcopy(generate(req, ring, directory))
            # The engine cannot expose arbitrary filenames or paths through the API.
            if result.get("files") != {key: filename for key, (filename, _) in FILES.items()}:
                raise ValueError("Unexpected SR artifact manifest")
            for filename, _ in FILES.values():
                path = directory / filename
                if path.is_symlink() or path.resolve().parent != directory or not path.is_file():
                    raise ValueError("Missing or unsafe SR artifact")
            result["files"] = {key: f"{API_PREFIX}/{job.job_id}/files/{filename}"
                               for key, (filename, _) in FILES.items()}
            with self._lock:
                job.result = result
                job.state = "succeeded"
        except SrInputError as error:
            log.warning("Super-resolution input rejected: %s: %s", job.job_id, error.user_message)
            with self._lock:
                job.state = "failed"
                job.error = error.user_message
        except Exception:
            log.exception("Super-resolution job failed: %s", job.job_id)
            with self._lock:
                job.state = "failed"
                job.error = "Không thể tạo ảnh Super-Resolution. Kiểm tra cấu hình mô hình/dữ liệu vệ tinh hoặc thử lại sau."

    def _find(self, job_id: str) -> _Job:
        if not re.fullmatch(r"[a-f0-9]{32}", job_id):
            raise JobNotFound()
        job = self._jobs.get(job_id)
        if job is None:
            raise JobNotFound()
        return job

    def get(self, job_id: str) -> dict:
        with self._lock:
            self._prune()
            return self._find(job_id).snapshot()

    def acquire_artifact(self, job_id: str, name: str) -> tuple[Path, str, Callable]:
        with self._lock:
            self._prune()
            job = self._find(job_id)
            if job.state != "succeeded" or name not in MEDIA_TYPES:
                raise JobNotFound()
            directory = self.output_dir / job_id
            path = directory / name
            if path.is_symlink() or path.resolve().parent != directory or not path.is_file():
                raise JobNotFound()
            job.readers += 1

        def release():
            with self._lock:
                job.readers -= 1

        return path, MEDIA_TYPES[name], release

    def close(self):
        with self._lock:
            self._closed = True
        self._executor.shutdown(wait=True)
        with self._lock:
            self._release_store()
