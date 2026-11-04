"""Real selected-tablet-polygon workflow evidence, direct EE or authenticated running API."""
import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--direct", action="store_true")
    parser.add_argument("--project", default="gen-lang-client-0598596007")
    args = parser.parse_args()
    garden = json.loads(Path("E:/Android/app/build/outputs/region-analysis/device-data-before.json").read_text(encoding="utf-8"))
    feature = next(f for f in garden["features"] if f["kind"] == "BOUNDARY")
    body = {"region": {"ring": [[p["lng"], p["lat"]] for p in feature["points"]]},
            "baseline_start": "2026-08-14", "baseline_end": "2026-09-13",
            "comparison_start": "2026-09-13", "comparison_end": "2026-10-13",
            "max_cloud_pct": 80, "threshold": .1}
    if args.direct:
        from app import ee_service, geo, region_workflow
        from app.schemas import RegionWorkflowRequest
        ee_service.init(args.project)
        req = RegionWorkflowRequest(**body)
        result = region_workflow.live_result(geo.validate_ring(req.region.ring), req).model_dump(mode="json")
    else:
        token = (ROOT.parent / ".satellite-token").read_text().strip()
        headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        with urlopen(Request("http://127.0.0.1:8000/v1/satellite/region-workflow",
                             data=json.dumps(body).encode(), headers=headers), timeout=240) as response:
            result = json.load(response)
    out = ROOT / "artifacts/verification/region-workflow"
    out.mkdir(parents=True, exist_ok=True)
    (out / ("direct-response.json" if args.direct else "response.json")).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    assert result["simulated"] is False
    assert all(result[p]["scene_count"] == len(result[p]["scene_ids"]) > 0 for p in ("baseline", "comparison"))
    for p in ("baseline", "comparison"):
        assert {s["index"]: s["nominal_pixel_size_m"] for s in result[p]["indices"]} == {"NDVI": 10, "EVI": 10, "NDMI": 20}
    change = result["change"]
    assert change["mean_delta"] is not None
    assert abs(sum(change[k] for k in ("increase_ha", "stable_ha", "decrease_ha")) - change["common_valid_area_ha"]) <= 3e-6
    assert change["common_valid_coverage_pct"] <= min(result[p]["valid_coverage_pct"] for p in ("baseline", "comparison")) + .02
    (out / "request.json").write_text(json.dumps(body, ensure_ascii=False, indent=2), encoding="utf-8")
    with urlopen(change["image_url"], timeout=90) as response:
        png = response.read(12 * 1024 * 1024 + 1)
    assert png.startswith(b"\x89PNG") and len(png) < 12 * 1024 * 1024
    (out / "delta-ndvi.png").write_bytes(png)
    print(json.dumps({"simulated": result["simulated"], "area_ha": result["area_ha"],
        "baseline": result["baseline"], "comparison": result["comparison"],
        "change": {k:v for k,v in change.items() if k != "image_url"}, "png_bytes": len(png)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
