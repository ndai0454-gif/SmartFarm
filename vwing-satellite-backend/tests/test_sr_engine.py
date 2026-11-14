"""Raster correctness checks and a real learned-model smoke (when weights installed)."""
import json
from pathlib import Path
import numpy as np
import pytest

rasterio = pytest.importorskip("rasterio")
from rasterio.transform import from_origin
from app import sr_engine as sr
from app.sr_errors import SrInputError


@pytest.mark.parametrize("coverage", [0., .219752854, .4999])
@pytest.mark.parametrize("context", [False, True])
def test_cloud_rejection_explains_coverage_and_retry_without_weakening_threshold(coverage, context):
    with pytest.raises(SrInputError) as error:
        sr._require_clear_coverage(coverage, context=context)
    assert f"{coverage * 100:.1f}%" in error.value.user_message
    assert "50%" in error.value.user_message and "93 ngày" in error.value.user_message
    assert ("khung ảnh lân cận" in error.value.user_message) == context


@pytest.mark.parametrize("coverage", [.5, .95, 1.])
def test_clear_coverage_threshold_is_inclusive(coverage):
    sr._require_clear_coverage(coverage)


@pytest.mark.parametrize("coverage", [float('nan'), float('inf'), -.1, 1.1])
def test_invalid_coverage_cannot_be_used_as_a_clear_scene(coverage):
    with pytest.raises(SrInputError, match="Không xác định"):
        sr._require_clear_coverage(coverage)


def test_real_model_is_finite_4x_and_not_plain_interpolation():
    pytest.importorskip("torch")
    pytest.importorskip("sen2sr")
    if not (sr.ROOT / "models/sen2srlite-rgbn-x4/provenance.json").is_file():
        pytest.skip("Install pinned pretrained weights using setup_sr.py for model smoke")
    x = np.random.default_rng(7).uniform(.04, .3, (4,128,128)).astype("float32")
    y, provenance = sr.infer(x)
    assert y.shape == (4,512,512) and np.isfinite(y).all()
    assert provenance["revision"] == sr.REVISION
    assert np.max(np.abs(y - x.repeat(4,1).repeat(4,2))) > .01


def test_geotiff_crops_polygon_and_preserves_crs_nodata_bands(tmp_path):
    a = np.full((4,10,10), .2, dtype="float32")
    t = from_origin(500000, 1200000, 10, 10)
    polygon = {"type":"Polygon", "coordinates":[[[500020,1199980],[500080,1199980],[500020,1199920],[500020,1199980]]]}
    valid = np.ones((10,10), dtype=bool); valid[3,3] = False
    path = tmp_path / "clipped.tif"
    sr.write_geotiff(path,a,valid,t,"EPSG:32648",polygon,{"inferred":"true"})
    with rasterio.open(path) as ds:
        assert ds.crs.to_epsg() == 32648
        assert ds.count == 4 and ds.width == ds.height == 6
        assert ds.transform.a == 10 and ds.transform.e == -10
        assert ds.descriptions == tuple(sr.BANDS)
        assert np.isnan(ds.nodata)
        values = ds.read()
        assert np.isnan(values).any() and np.isfinite(values).any()
        assert ds.tags()["inferred"] == "true"


@pytest.mark.parametrize("grid,expected_size", [(2.5,24), (1.,60)])
def test_export_grid_records_true_model_scale_and_same_preview_bounds(tmp_path, monkeypatch, grid, expected_size):
    from types import SimpleNamespace
    native = np.full((4,128,128),.15,dtype="float32")
    valid = np.ones((128,128),dtype=bool)
    t = from_origin(500000,1200000,10,10)
    polygon = {"type":"Polygon","coordinates":[[[500020,1199980],[500080,1199980],[500080,1199920],[500020,1199920],[500020,1199980]]]}
    scene = {"system:index":"TEST_FIXTURE_ONLY","system:time_start":1785542400000}
    monkeypatch.setattr(sr,"source_patch",lambda *a: (native,valid,t,"EPSG:32648",polygon,1.,scene))
    # Deterministic non-AI fixture for IO only; separate test above loads actual model.
    monkeypatch.setattr(sr,"infer",lambda a: (a.repeat(4,1).repeat(4,2),{"sha256":{"test":"fixture"}}))
    result = sr.generate_sr(SimpleNamespace(output_pixel_size_m=grid), [], tmp_path)
    with rasterio.open(tmp_path / "enhanced.tif") as ds:
        assert ds.width == ds.height == expected_size
        assert ds.transform.a == grid and ds.transform.e == -grid
        assert ds.tags()["model_pixel_size_m"] == "2.5"
    with rasterio.open(tmp_path / "original.tif") as ds:
        assert ds.transform.a == 10 and ds.tags()["inferred"] == "false"
    assert result["native_pixel_size_m"] == 10 and result["model_pixel_size_m"] == 2.5
    assert any("1 m" in w for w in result["warnings"]) == (grid == 1.)
    from PIL import Image
    assert Image.open(tmp_path / "original.png").size == Image.open(tmp_path / "enhanced.png").size
    metadata = json.loads((tmp_path / "metadata.json").read_text(encoding="utf-8"))
    assert metadata["output_pixel_size_m"] == grid
