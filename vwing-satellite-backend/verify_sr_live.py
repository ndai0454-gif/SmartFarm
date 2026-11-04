"""End-to-end real SR job against the polygon already selected on the Android tablet."""
from pathlib import Path
import json
import time
from urllib.request import Request, urlopen
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parent
token = (ROOT.parent / ".satellite-token").read_text().strip()
garden = json.loads(Path("E:/Android/app/build/outputs/region-analysis/device-data-before.json").read_text(encoding="utf-8"))
feature = next(f for f in garden["features"] if f["kind"] == "BOUNDARY")
base = "http://127.0.0.1:8000"
headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
def request(path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    with urlopen(Request(base + path, data=data, headers=headers), timeout=30) as response:
        return json.load(response)

records = []
for grid in (2.5, 1.):
    body = {"region":{"ring":[[p["lng"],p["lat"]] for p in feature["points"]]},"start":"2026-09-13","end":"2026-10-13","output_pixel_size_m":grid}
    job = request("/v1/satellite/super-resolution", body)
    deadline = time.monotonic() + 600
    while job["state"] in ("queued", "running") and time.monotonic() < deadline:
        time.sleep(2)
        job = request("/v1/satellite/super-resolution/" + job["job_id"])
    if job["state"] != "succeeded":
        raise RuntimeError(json.dumps(job, ensure_ascii=False))
    result = job["result"]
    folder = ROOT / "artifacts/verification" / f"sr-{grid}m"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "response.json").write_text(json.dumps(job, ensure_ascii=False, indent=2), encoding="utf-8")
    for key, path in result["files"].items():
        assert path.startswith("/v1/satellite/super-resolution/" + job["job_id"] + "/files/")
        with urlopen(Request(urljoin(base, path), headers=headers), timeout=60) as response:
            data = response.read(64 * 1024 * 1024 + 1)
        assert len(data) <= 64 * 1024 * 1024
        (folder / path.rsplit("/",1)[-1]).write_bytes(data)
    import rasterio
    import numpy as np
    from PIL import Image
    with rasterio.open(folder / "original.tif") as source, rasterio.open(folder / "enhanced.tif") as enhanced:
        assert source.transform.a == 10 and enhanced.transform.a == grid
        assert source.count == enhanced.count == 4 and source.crs == enhanced.crs
        assert np.isfinite(enhanced.read()).any() and np.isnan(enhanced.read()).any()
        assert enhanced.tags()["model_pixel_size_m"] == "2.5"
    assert Image.open(folder / "original.png").size == Image.open(folder / "enhanced.png").size
    records.append({key: result[key] for key in ("source_scene","source_date","model","native_pixel_size_m","model_pixel_size_m","output_pixel_size_m","valid_coverage_pct","width","height")})
    print(json.dumps(records[-1], ensure_ascii=False), flush=True)
print("Real inference + source/enhanced GeoTIFF and PNG downloads verified for both export grids")
