"""Synthetic artifacts below test transport/storage only, never AI image quality."""
import asyncio
import json
from pathlib import Path
import struct
import threading
import time
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app import config, ee_service, geo, mock_service
from app.main import _ArtifactResponse, create_app
from app.schemas import SuperResolutionRequest
from app.sr_errors import SrInputError
from app.sr_jobs import FILES, JobNotFound, QueueFull, SrJobManager

TOKEN = "test-token-123456"
HEADERS = {"Authorization": f"Bearer {TOKEN}"}
API = "/v1/satellite/super-resolution"
RING = [[107.8, 11.55], [107.801, 11.55], [107.801, 11.551], [107.8, 11.551]]
PNG = mock_service._png(2, 2, lambda x, y: (x * 80, y * 80, 100))
# Valid little-endian, uncompressed 1x1 grayscale TIFF test fixture.
TIFF_TAGS = [(256, 4, 1, 1), (257, 4, 1, 1), (258, 3, 1, 8),
             (259, 3, 1, 1), (262, 3, 1, 1), (273, 4, 1, 122),
             (277, 3, 1, 1), (278, 4, 1, 1), (279, 4, 1, 1)]
TIFF = b"II*\x00" + struct.pack("<I", 8) + struct.pack("<H", len(TIFF_TAGS))
TIFF += b"".join(struct.pack("<HHII", *tag) for tag in TIFF_TAGS) + b"\x00" * 4 + b"\x80"


def request_body(**overrides):
    body = {"region": {"ring": RING}, "start": "2026-08-01", "end": "2026-09-01"}
    body.update(overrides)
    return body


def settings(mode="live"):
    return config.Settings(token=TOKEN, mode=mode, ee_project="unit-test-project",
                           max_area_ha=500, max_composite_days=93, max_series_days=400,
                           public_base_url="http://testserver")


def synthetic_generator(req, ring, directory: Path):
    assert ring[0] == ring[-1]
    for filename, _ in FILES.values():
        data = PNG if filename.endswith(".png") else TIFF
        if filename.endswith(".json"):
            data = json.dumps({"fixture": "synthetic transport test", "ring": ring}).encode()
        (directory / filename).write_bytes(data)
    return {"model": "synthetic-test-only", "source_date": str(req.start),
            "output_pixel_size_m": req.output_pixel_size_m,
            "files": {key: name for key, (name, _) in FILES.items()}}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(ee_service, "init", lambda project: None)
    with TestClient(create_app(settings(), sr_generator=synthetic_generator, sr_output_dir=tmp_path)) as value:
        yield value


def wait_finished(get_job):
    deadline = time.monotonic() + 3
    while time.monotonic() < deadline:
        job = get_job()
        if job["state"] in ("succeeded", "failed"):
            return job
        time.sleep(0.005)
    pytest.fail("SR worker did not finish within test deadline")


def submit_and_wait(client, **overrides):
    response = client.post(API, headers=HEADERS, json=request_body(**overrides))
    assert response.status_code == 202, response.text
    snapshot = response.json()
    assert snapshot["state"] == "queued" and snapshot["result"] is None
    job_id = snapshot["job_id"]
    assert len(job_id) == 32
    return wait_finished(lambda: client.get(f"{API}/{job_id}", headers=HEADERS).json())


def test_auth_on_submission_poll_and_download(client):
    for method, url, body in [("POST", API, request_body()),
                              ("GET", f"{API}/{uuid4().hex}", None),
                              ("GET", f"{API}/{uuid4().hex}/files/enhanced.png", None)]:
        assert client.request(method, url, json=body).status_code == 401
        assert client.request(method, url, json=body, headers={"Authorization": "Bearer invalid"}).status_code == 401


def test_mock_explicitly_rejects_sr_even_with_injected_generator(tmp_path):
    def must_not_run(*args):
        pytest.fail("Mock mode must never generate synthetic AI results")
    with TestClient(create_app(settings("mock"), sr_generator=must_not_run, sr_output_dir=tmp_path)) as client:
        response = client.post(API, headers=HEADERS, json=request_body())
        assert response.status_code == 503 and "mock" in response.json()["detail"]
        assert client.get(f"{API}/{uuid4().hex}", headers=HEADERS).status_code == 503
        assert list(tmp_path.iterdir()) == []


@pytest.mark.parametrize("changes", [
    {"end": "2026-08-01"}, {"end": "2026-07-31"},
    {"start": "2026-01-01", "end": "2026-04-05"},
    {"max_cloud_pct": -1}, {"max_cloud_pct": 101},
    {"output_pixel_size_m": 10}, {"output_pixel_size_m": 0.5},
    {"region": {"ring": [[107, 11], [107.01, 11], [107.01, 11.01], [107, 11.01]]}},
    {"region": {"ring": [[200, 11], [201, 11], [200, 12]]}},
    {"region": {"ring": [[0, 0], [0.001, 0.001], [0.001, 0], [0, 0.001]]}},
    {"region": {"ring": [[107, 11], [107, 11], [107, 11]]}},
    {"region": {"ring": [[107, 11, 5], [107.001, 11], [107, 11.001]]}},
    {"region": {"ring": [[107], [108, 11], [107, 12]]}},
])
def test_invalid_input_rejected(client, changes):
    assert client.post(API, headers=HEADERS, json=request_body(**changes)).status_code == 422


@pytest.mark.parametrize("pixel_size", [1.0, 2.5])
def test_job_completes_and_artifacts_are_authenticated(client, pixel_size):
    job = submit_and_wait(client, output_pixel_size_m=pixel_size)
    assert job["state"] == "succeeded" and job["error"] is None
    assert job["result"]["output_pixel_size_m"] == pixel_size
    assert job["result"]["source_date"] == "2026-08-01"
    for key, (filename, mime) in FILES.items():
        url = job["result"]["files"][key]
        assert url == f"{API}/{job['job_id']}/files/{filename}"
        response = client.get(url, headers=HEADERS)
        assert response.status_code == 200 and response.headers["content-type"].startswith(mime)
        assert response.headers["cache-control"] == "no-store"
        assert client.get(url).status_code == 401
        if filename.endswith(".json"):
            assert json.loads(response.content)["fixture"] == "synthetic transport test"
        else:
            assert response.content == (PNG if filename.endswith(".png") else TIFF)


def test_unknown_job_and_arbitrary_files_are_rejected(client):
    job = submit_and_wait(client)
    assert client.get(f"{API}/{uuid4().hex}", headers=HEADERS).status_code == 404
    assert client.get(f"{API}/invalid-id", headers=HEADERS).status_code == 404
    for name in ["secret.txt", "sr_engine.py", "..%2Fmetadata.json", "%2E%2E%2F%2E%2E%2Fconfig.py"]:
        assert client.get(f"{API}/{job['job_id']}/files/{name}", headers=HEADERS).status_code == 404


@pytest.mark.parametrize("error_type", [RuntimeError, ValueError])
def test_failed_state_does_not_leak_secrets_or_partial_files(tmp_path, monkeypatch, error_type):
    monkeypatch.setattr(ee_service, "init", lambda project: None)
    def failure(req, ring, output_dir):
        (output_dir / "enhanced.png").write_bytes(PNG)
        raise error_type("secret-token-DO-NOT-EXPOSE")
    with TestClient(create_app(settings(), sr_generator=failure, sr_output_dir=tmp_path)) as client:
        job = submit_and_wait(client)
        assert job["state"] == "failed" and job["result"] is None
        assert "secret-token" not in job["error"]
        assert client.get(f"{API}/{job['job_id']}/files/enhanced.png", headers=HEADERS).status_code == 404


def test_curated_input_error_is_actionable_without_exposing_partial_files(tmp_path, monkeypatch):
    monkeypatch.setattr(ee_service, "init", lambda project: None)
    message = "Không có ảnh vệ tinh đạt ngưỡng mây. Hãy mở rộng khoảng ngày hoặc tăng ngưỡng mây."

    def failure(req, ring, output_dir):
        (output_dir / "enhanced.png").write_bytes(PNG)
        raise SrInputError(message)

    with TestClient(create_app(settings(), sr_generator=failure, sr_output_dir=tmp_path)) as client:
        job = submit_and_wait(client)
        assert job["state"] == "failed" and job["result"] is None
        assert job["error"] == message
        # Both a file actually written before failure and other declared artifacts
        # remain inaccessible after an actionable input rejection.
        assert (tmp_path / job["job_id"] / "enhanced.png").is_file()
        for filename, _ in FILES.values():
            assert client.get(f"{API}/{job['job_id']}/files/{filename}", headers=HEADERS).status_code == 404


def test_only_one_worker_and_two_outstanding_jobs(tmp_path, monkeypatch):
    monkeypatch.setattr(ee_service, "init", lambda project: None)
    started, release = threading.Event(), threading.Event()
    calls = []
    def blocking(req, ring, output_dir):
        calls.append(output_dir.name)
        started.set()
        assert release.wait(3)
        return synthetic_generator(req, ring, output_dir)
    with TestClient(create_app(settings(), sr_generator=blocking, sr_output_dir=tmp_path)) as client:
        try:
            first = client.post(API, headers=HEADERS, json=request_body()).json()
            assert started.wait(1)
            second = client.post(API, headers=HEADERS, json=request_body()).json()
            assert client.get(f"{API}/{first['job_id']}", headers=HEADERS).json()["state"] == "running"
            assert client.get(f"{API}/{second['job_id']}", headers=HEADERS).json()["state"] == "queued"
            assert client.get(f"{API}/{first['job_id']}/files/original.png", headers=HEADERS).status_code == 404
            assert len(calls) == 1
            assert client.post(API, headers=HEADERS, json=request_body()).status_code == 429
        finally:
            release.set()
        for job in [first, second]:
            assert wait_finished(lambda: client.get(f"{API}/{job['job_id']}", headers=HEADERS).json())["state"] == "succeeded"
        assert len(calls) == 2


def test_retention_bound_preserves_download_leases_and_expires_jobs(tmp_path):
    manager = SrJobManager(tmp_path, synthetic_generator, max_jobs=2)
    request = SuperResolutionRequest(**request_body())
    ring = geo.validate_ring(RING)
    def submit():
        job_id = manager.submit(request, ring)["job_id"]
        assert wait_finished(lambda: manager.get(job_id))["state"] == "succeeded"
        return job_id
    try:
        first = submit()
        path, _, release = manager.acquire_artifact(first, "original.png")
        second = submit()
        third = submit()
        assert path.read_bytes() == PNG
        assert manager.get(first)["state"] == "succeeded"
        assert manager.get(third)["state"] == "succeeded"
        with pytest.raises(JobNotFound):
            manager.get(second)
        assert len([path for path in tmp_path.iterdir() if path.is_dir()]) == 2
        release()
        manager.ttl_seconds = 0
        with pytest.raises(JobNotFound):
            manager.get(first)
        assert not any(path.is_dir() for path in tmp_path.iterdir())
    finally:
        manager.close()


def test_store_has_one_process_owner_and_restart_cleans_orphans(tmp_path):
    first_manager = SrJobManager(tmp_path, synthetic_generator)
    second_manager = SrJobManager(tmp_path, synthetic_generator)
    request = SuperResolutionRequest(**request_body())
    ring = geo.validate_ring(RING)
    try:
        first = first_manager.submit(request, ring)["job_id"]
        assert wait_finished(lambda: first_manager.get(first))["state"] == "succeeded"
        with pytest.raises(QueueFull):
            second_manager.submit(request, ring)
        assert (tmp_path / first / "enhanced.png").read_bytes() == PNG
        first_manager.close()
        second = second_manager.submit(request, ring)["job_id"]
        assert wait_finished(lambda: second_manager.get(second))["state"] == "succeeded"
        assert not (tmp_path / first).exists()
        assert len([path for path in tmp_path.iterdir() if path.is_dir()]) == 1
    finally:
        first_manager.close()
        second_manager.close()


def test_unsafe_engine_manifest_fails_closed(tmp_path):
    def unsafe(req, ring, output_dir):
        result = synthetic_generator(req, ring, output_dir)
        result["files"]["metadata"] = "../../secret.json"
        return result
    manager = SrJobManager(tmp_path, unsafe)
    try:
        job = manager.submit(SuperResolutionRequest(**request_body()), geo.validate_ring(RING))
        assert wait_finished(lambda: manager.get(job["job_id"]))["state"] == "failed"
    finally:
        manager.close()


def test_disconnected_download_releases_retention_lease(tmp_path):
    manager = SrJobManager(tmp_path, synthetic_generator)
    try:
        job_id = manager.submit(SuperResolutionRequest(**request_body()), geo.validate_ring(RING))["job_id"]
        assert wait_finished(lambda: manager.get(job_id))["state"] == "succeeded"
        path, media_type, release = manager.acquire_artifact(job_id, "original.png")
        response = _ArtifactResponse(path, media_type=media_type, release=release)
        async def disconnected_send(message):
            raise RuntimeError("test client disconnected")
        async def receive():
            return {"type": "http.disconnect"}
        with pytest.raises(RuntimeError, match="disconnected"):
            asyncio.run(response({"type": "http", "method": "GET", "headers": []}, receive, disconnected_send))
        manager.ttl_seconds = 0
        with pytest.raises(JobNotFound):
            manager.get(job_id)
        assert not path.exists()
    finally:
        manager.close()
