"""OBIA: giao dịch/lưu trữ/job bằng runner giả; bài chạy động cơ thật dùng ảnh GeoTIFF TỔNG HỢP (không phải dữ liệu drone)
và tự bỏ qua nếu máy không có LITECLASSIFY_DIR hoặc thiếu thư viện. Test này kiểm tra đường ống, KHÔNG đánh giá chất lượng phân lớp."""
import csv
import io
import threading
import time

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app import config, obia_pipeline
from app.main import create_app
from app.obia_jobs import ObiaJobManager

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}
API = "/v1/obia"


def settings():
    return config.Settings(token=TOKEN, mode="mock", ee_project="", max_area_ha=500, max_composite_days=93,
                           max_series_days=400, public_base_url="http://t")


def synthetic_ortho(path, size=240, crs="EPSG:32648", with_crs=True, bands=3):
    """4 góc: thảm xanh, đất trần, nước tối, đường xám; có nhiễu. Trả về (x0, y0, pixel) để đặt điểm mẫu."""
    import rasterio
    from rasterio.transform import from_origin
    rng = np.random.default_rng(7)
    img = np.zeros((bands, size, size), np.uint8)
    base = {"veg": (40, 150, 45), "soil": (150, 110, 70), "water": (20, 50, 110), "road": (140, 140, 140)}
    half = size // 2
    quads = {"veg": (slice(0, half), slice(0, half)), "soil": (slice(0, half), slice(half, size)),
             "water": (slice(half, size), slice(0, half)), "road": (slice(half, size), slice(half, size))}
    for name, (rs, cs) in quads.items():
        for b in range(bands):
            img[b][rs, cs] = np.clip(base[name][b] + rng.normal(0, 9, (img[b][rs, cs].shape)), 0, 255)
    x0, y0, px = 740000.0, 1277000.0, 0.5
    kw = dict(driver="GTiff", height=size, width=size, count=bands, dtype="uint8", transform=from_origin(x0, y0, px, px))
    if with_crs:
        kw["crs"] = crs
    with rasterio.open(path, "w", **kw) as dst:
        dst.write(img)
    return x0, y0, px, size


def wait(client, job_id, timeout=240):
    end = time.time() + timeout
    while time.time() < end:
        j = client.get(f"{API}/jobs/{job_id}", headers=H).json()
        if j["status"] not in ("queued", "running"):
            return j
        time.sleep(0.2)
    raise AssertionError("job không kết thúc kịp")


def put(client, ws, kind, data):
    return client.put(f"{API}/workspaces/{ws}/inputs/{kind}", content=data,
                      headers={**H, "Content-Type": "application/octet-stream"})


def tif_bytes(tmp_path, **kw):
    p = tmp_path / "t.tif"
    synthetic_ortho(p, **kw)
    return p.read_bytes()


# ----------------------------------------------------------------------------- vận chuyển, bằng runner giả
@pytest.fixture
def fake(tmp_path):
    gate = threading.Event()
    seen = []

    def runner(stage, ws, params, ctx):
        seen.append((stage, params))
        ctx.progress(0.5, "giữa chừng")
        if params.get("block"):
            while not gate.is_set():
                ctx.check()
                time.sleep(0.02)
        (ws / "out" / f"{stage}.json").write_text("{}")
        return {"stage": stage}

    app = create_app(settings(), obia_dir=tmp_path / "obia", obia_runner=runner)
    with TestClient(app) as c:
        c.gate, c.seen, c.app = gate, seen, app
        yield c
    gate.set()


def new_ws(c):
    r = c.post(f"{API}/workspaces", json={"name": "vườn thử"}, headers=H)
    assert r.status_code == 201
    return r.json()["id"]


def test_workspace_lifecycle_and_unknown_ids(fake):
    ws = new_ws(fake)
    d = fake.get(f"{API}/workspaces/{ws}", headers=H).json()
    assert d["inputs"] == {"ortho": False, "dsm": False, "reference": False} and d["active_job"] is None
    assert fake.get(f"{API}/workspaces/{'0' * 32}", headers=H).status_code == 404
    assert fake.get(f"{API}/workspaces/../../etc", headers=H).status_code == 404
    assert fake.delete(f"{API}/workspaces/{ws}", headers=H).status_code == 204
    assert fake.get(f"{API}/workspaces/{ws}", headers=H).status_code == 404
    assert fake.get(f"{API}/jobs/{'0' * 32}", headers=H).status_code == 404


def test_upload_validation(fake, tmp_path):
    ws = new_ws(fake)
    assert put(fake, ws, "ortho", b"khong phai tiff").status_code == 422
    assert put(fake, ws, "ortho", b"").status_code == 422
    assert put(fake, ws, "ortho", tif_bytes(tmp_path, with_crs=False)).status_code == 422       # thiếu CRS
    assert put(fake, ws, "ortho", tif_bytes(tmp_path, bands=1)).status_code == 422               # không đủ băng
    assert put(fake, ws, "bogus", b"x").status_code == 422
    assert put(fake, ws, "reference", b"a,b\n1,2\n").status_code == 422
    ok = put(fake, ws, "ortho", tif_bytes(tmp_path))
    assert ok.status_code == 200 and ok.json()["bytes"] > 0
    assert put(fake, ws, "reference", b"x,y,class\n1,2,veg\n").status_code == 200
    d = fake.get(f"{API}/workspaces/{ws}", headers=H).json()
    assert d["inputs"]["ortho"] and d["inputs"]["reference"]
    # không để tệp .part mồ côi sau các lần hỏng
    assert not list((fake.app.state.obia_jobs.root / ws / "input").glob("*.part"))


def test_upload_size_limit(fake):
    ws = new_ws(fake)
    fake.app.state.obia_jobs.max_upload_bytes = 1000
    assert put(fake, ws, "dsm", b"0" * 5000).status_code == 413
    assert not list((fake.app.state.obia_jobs.root / ws / "input").glob("*"))


def test_prerequisites_validation_and_extra_fields(fake, tmp_path):
    ws = new_ws(fake)
    assert fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={}, headers=H).status_code == 409     # chưa có ảnh
    assert fake.post(f"{API}/workspaces/{ws}/jobs/discover", json={}, headers=H).status_code == 409
    put(fake, ws, "ortho", tif_bytes(tmp_path))
    assert fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={"n_segments": 5}, headers=H).status_code == 422
    assert fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={"hack": 1}, headers=H).status_code == 422
    assert fake.post(f"{API}/workspaces/{ws}/jobs/classify", json={"classes": {"a": {}}}, headers=H).status_code == 422
    assert fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={"device": "tpu"}, headers=H).status_code == 422
    assert fake.post(f"{API}/workspaces/nope/jobs/segment", json={}, headers=H).status_code == 404


def test_job_runs_progress_files_and_path_safety(fake, tmp_path):
    ws = new_ws(fake)
    put(fake, ws, "ortho", tif_bytes(tmp_path))
    r = fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={}, headers=H)
    assert r.status_code == 202
    job = wait(fake, r.json()["id"])
    assert job["status"] == "succeeded" and job["result"]["stage"] == "segment" and job["progress"] == 1.0
    assert job["result"]["attribution"].startswith("Based on Lite Classify by L. D. Barua")
    assert fake.seen[0][1]["n_segments"] == 5000                      # mặc định được điền
    f = fake.get(f"{API}/workspaces/{ws}/files/segment.json", headers=H)
    assert f.status_code == 200
    for bad in ("..%2f..%2fmeta.json", "meta.json", "nope.png", ".hidden", "a/b"):
        assert fake.get(f"{API}/workspaces/{ws}/files/{bad}", headers=H).status_code == 404
    assert [j["id"] for j in fake.get(f"{API}/workspaces/{ws}/jobs", headers=H).json()] == [job["id"]]


def test_one_job_per_workspace_cancel_and_delete_guard(fake, tmp_path):
    ws = new_ws(fake)
    put(fake, ws, "ortho", tif_bytes(tmp_path))
    # tiêm cờ block qua tham số hợp lệ không có → dùng manager trực tiếp
    mgr = fake.app.state.obia_jobs
    job = mgr.submit(ws, "segment", {"block": True})
    time.sleep(0.2)
    assert fake.post(f"{API}/workspaces/{ws}/jobs/segment", json={}, headers=H).status_code == 409
    assert put(fake, ws, "ortho", b"x").status_code == 409
    assert fake.delete(f"{API}/workspaces/{ws}", headers=H).status_code == 409
    assert fake.get(f"{API}/workspaces/{ws}", headers=H).json()["active_job"] == job["id"]
    assert fake.post(f"{API}/jobs/{job['id']}/cancel", headers=H).status_code == 200
    assert wait(fake, job["id"])["status"] == "cancelled"
    assert fake.delete(f"{API}/workspaces/{ws}", headers=H).status_code == 204


def test_failures_do_not_leak_internals_and_restart_marks_interrupted(tmp_path):
    def boom(stage, ws, params, ctx):
        raise RuntimeError("/etc/secret-path khong duoc lo")

    mgr = ObiaJobManager(tmp_path / "o", runner=boom)
    ws = mgr.create_workspace()["id"]
    (tmp_path / "o" / ws / "input" / "ortho.tif").write_bytes(b"x")
    job = mgr.submit(ws, "segment", {})
    for _ in range(100):
        j = mgr.get_job(job["id"])
        if j["status"] not in ("queued", "running"):
            break
        time.sleep(0.05)
    assert j["status"] == "failed" and "secret" not in j["error"]
    # giả lập tắt máy khi đang chạy
    j["status"] = "running"
    mgr._save_job(j)
    mgr.close()
    again = ObiaJobManager(tmp_path / "o", runner=boom)
    assert again.get_job(job["id"])["status"] == "failed" and "interrupted" in again.get_job(job["id"])["error"]
    again.close()


def test_expired_workspaces_are_purged(tmp_path):
    mgr = ObiaJobManager(tmp_path / "o", runner=lambda *a: {}, ttl_seconds=0.05)
    ws = mgr.create_workspace()["id"]
    time.sleep(0.15)
    assert mgr.purge_expired() == 1
    assert not (tmp_path / "o" / ws).exists()
    mgr.close()


def test_status_reports_engine_availability(fake, monkeypatch):
    monkeypatch.delenv("LITECLASSIFY_DIR", raising=False)
    monkeypatch.setattr(obia_pipeline, "_mods", None)
    s = fake.get(f"{API}/status", headers=H).json()
    assert s["available"] is False and "LITECLASSIFY_DIR" in s["reason"]
    assert s["attribution"].startswith("Based on Lite Classify")


# ----------------------------------------------------------------------------- động cơ thật trên ảnh tổng hợp
def _engine_ok():
    try:
        obia_pipeline.engine_modules()
        return True
    except obia_pipeline.ObiaUnavailable:
        return False


@pytest.fixture
def real(tmp_path):
    if not _engine_ok():
        pytest.skip("Không có Lite Classify (đặt LITECLASSIFY_DIR) hoặc thiếu thư viện")
    app = create_app(settings(), obia_dir=tmp_path / "obia")
    with TestClient(app) as c:
        yield c


def test_full_pipeline_on_synthetic_ortho(real, tmp_path):
    c = real
    ws = new_ws(c)
    src = tmp_path / "ortho.tif"
    x0, y0, px, size = synthetic_ortho(src)
    assert put(c, ws, "ortho", src.read_bytes()).status_code == 200

    seg = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/segment",
                         json={"n_segments": 300, "device": "cpu"}, headers=H).json()["id"])
    assert seg["status"] == "succeeded", seg.get("error")
    assert seg["result"]["segments"] > 50 and len(seg["result"]["bounds_wgs84"]) == 4
    assert c.get(f"{API}/workspaces/{ws}/files/ortho_preview.png", headers=H).content[:4] == b"\x89PNG"

    disc = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/discover",
                          json={"n_groups": 6, "use_deep": True}, headers=H).json()["id"])
    assert disc["status"] == "succeeded", disc.get("error")
    groups = disc["result"]["groups"]
    assert 2 <= len(groups) <= 6 and all(g["examples"] for g in groups)
    assert disc["result"]["deep_used"] in (True, False)

    def pt(col_frac, row_frac):
        return [x0 + col_frac * size * px, y0 - row_frac * size * px]

    classes = {"Thảm xanh": {"points": [pt(0.15, 0.15), pt(0.3, 0.2), pt(0.2, 0.4), pt(0.4, 0.4)]},
               "Đất trần": {"points": [pt(0.65, 0.15), pt(0.8, 0.2), pt(0.7, 0.4), pt(0.9, 0.4)]},
               "Nước": {"points": [pt(0.15, 0.65), pt(0.3, 0.8), pt(0.2, 0.9), pt(0.4, 0.7)], "color": "#1e88e5"},
               "Đường": {"points": [pt(0.65, 0.65), pt(0.8, 0.8), pt(0.7, 0.9), pt(0.9, 0.7)]}}
    cls = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/classify", json={"classes": classes, "sieve_px": 4},
                         headers=H).json()["id"])
    assert cls["status"] == "succeeded", cls.get("error")
    stats = {s["class"]: s for s in cls["result"]["classes"]}
    assert set(stats) == set(classes)
    total_ha = 240 * 240 * 0.25 / 1e4
    assert abs(sum(s["area_ha"] for s in stats.values()) - total_ha) < 0.2 * total_ha            # phủ gần hết ảnh
    for s in stats.values():
        assert 0.10 < s["share"] < 0.40, s                                                      # mỗi góc ≈ 25 %

    import rasterio
    tif = tmp_path / "out.tif"
    tif.write_bytes(c.get(f"{API}/workspaces/{ws}/files/classified.tif", headers=H).content)
    with rasterio.open(tif) as r:
        arr = r.read(1)
        assert r.crs.to_string() == "EPSG:32648" and r.nodata == 0 and arr.shape == (size, size)
    # 4 góc thật sự được gán đúng lớp (nhãn tạo từ bố cục ảnh, độc lập với bộ phân lớp)
    names = list(classes)
    for (rs, cs), name in zip(((slice(10, 100), slice(10, 100)), (slice(10, 100), slice(140, 230)),
                               (slice(140, 230), slice(10, 100)), (slice(140, 230), slice(140, 230))), names):
        block = arr[rs, cs]
        assert (block == names.index(name) + 1).mean() > 0.9, name
    assert b"Th" in c.get(f"{API}/workspaces/{ws}/files/classified.qml", headers=H).content

    samp = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/sample", json={"total": 80, "min_per_class": 10},
                          headers=H).json()["id"])
    assert samp["status"] == "succeeded", samp.get("error")
    rows = list(csv.DictReader(io.StringIO(c.get(f"{API}/workspaces/{ws}/files/accuracy_sample.csv",
                                                 headers=H).text)))
    assert len(rows) >= 40

    # nhãn thực địa độc lập: lấy từ BỐ CỤC ẢNH tại chính các điểm mẫu, không từ bản đồ phân lớp
    def truth(x, y):
        col, row = (x - x0) / px, (y0 - y) / px
        return names[(0 if row < size / 2 else 2) + (0 if col < size / 2 else 1)]

    ref = io.StringIO()
    w = csv.writer(ref)
    w.writerow(["x", "y", "class"])
    for r in rows:
        w.writerow([r["x"], r["y"], truth(float(r["x"]), float(r["y"]))])
    assert put(c, ws, "reference", ref.getvalue().encode("utf-8")).status_code == 200
    acc = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/accuracy", json={}, headers=H).json()["id"])
    assert acc["status"] == "succeeded", acc.get("error")
    res = acc["result"]
    assert res["sample_size"] >= 40 and res["overall_accuracy"] > 0.9
    assert set(res["users_accuracy"]) == set(names)
    assert c.get(f"{API}/workspaces/{ws}/files/accuracy_report.csv", headers=H).status_code == 200


def test_classify_input_errors_are_reported_not_crashing(real, tmp_path):
    c = real
    ws = new_ws(c)
    src = tmp_path / "o.tif"
    x0, y0, px, size = synthetic_ortho(src)
    put(c, ws, "ortho", src.read_bytes())
    assert wait(c, c.post(f"{API}/workspaces/{ws}/jobs/segment", json={"n_segments": 200, "device": "cpu"},
                          headers=H).json()["id"])["status"] == "succeeded"
    far = [x0 + 99999, y0]
    bad = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/classify", headers=H, json={"classes": {
        "a": {"points": [far, far]}, "b": {"segments": [1, 2]}}}).json()["id"])
    assert bad["status"] == "failed" and "ngoài ảnh" in bad["error"]
    dup = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/classify", headers=H, json={"classes": {
        "a": {"segments": [1, 2]}, "b": {"segments": [2, 3]}}}).json()["id"])
    assert dup["status"] == "failed" and "cả 'a' và 'b'" in dup["error"]
    nogrp = wait(c, c.post(f"{API}/workspaces/{ws}/jobs/classify", headers=H, json={"classes": {
        "a": {"groups": ["G01"]}, "b": {"segments": [1, 2]}}}).json()["id"])
    assert nogrp["status"] == "failed" and "discover" in nogrp["error"]


def test_analyze_region_direct_endpoint(real):
    c = real
    ring = [[107.8055, 11.5488], [107.8068, 11.5491], [107.8071, 11.5479], [107.8057, 11.5476]]
    r = c.post(f"{API}/analyze-region", headers=H, json={
        "ring": ring,
        "crop_type": "durian",
        "n_segments": 80,
        "compactness": 0.15,
        "flight_altitude_m": 60.0,
    })
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["simulated"] is False
    assert body["attribution"].startswith("Based on Lite Classify by L. D. Barua")
    assert len(body["segments"]) >= 20
    assert len(body["dino_clusters"]) >= 2
    assert "<qgis" in body["qgis_qml_preview"]
