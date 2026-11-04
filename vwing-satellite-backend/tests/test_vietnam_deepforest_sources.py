"""Kiểm thử danh mục nguồn ảnh độ phân giải cao Việt Nam (OAM, PlanetScope/SkySat),
bộ dữ liệu nghiên cứu (GFW/Hansen, Zenodo/Mendeley) và quy trình chuyển đổi
PASCAL VOC XML / YOLO -> CSV chuẩn DeepForest (`xml_to_csv`).
"""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import config, vietnam_deepforest_sources as vds
from app.main import create_app

TOKEN = "test-token-123456"
H = {"Authorization": f"Bearer {TOKEN}"}


@pytest.fixture()
def client():
    st = config.Settings(
        token=TOKEN,
        mode="mock",
        ee_project="",
        max_area_ha=500,
        max_composite_days=93,
        max_series_days=400,
        public_base_url="http://testserver",
    )
    return TestClient(create_app(st))


def test_vietnam_sources_catalog_endpoint(client):
    r = client.get("/v1/deepforest/vietnam-sources", headers=H)
    assert r.status_code == 200
    body = r.json()

    # 1. Nguồn ảnh độ phân giải cao (<0.5m - 1m/pixel): OAM & PlanetScope/SkySat
    src_ids = {s["source_id"] for s in body["high_resolution_imagery_sources"]}
    assert {"openaerialmap_vietnam", "planetscope_education_research"} <= src_ids

    # 2. Bộ dữ liệu nghiên cứu Lâm nghiệp & Nông nghiệp Việt Nam
    ds_ids = {d["dataset_id"] for d in body["vietnam_forestry_agriculture_datasets"]}
    assert {"gfw_hansen_vietnam_forest_cover", "mendeley_zenodo_kaggle_vn_uav_orchards"} <= ds_ids

    uav_ds = next(
        d
        for d in body["vietnam_forestry_agriculture_datasets"]
        if d["dataset_id"] == "mendeley_zenodo_kaggle_vn_uav_orchards"
    )
    assert "Vietnam Agricultural Drone Dataset" in uav_ds["search_keywords"]
    assert "Coconut tree detection UAV Vietnam" in uav_ds["search_keywords"]
    assert "Coffee tree canopy UAV" in uav_ds["search_keywords"]

    # 3. Quy trình 3 bước & schema CSV chuẩn DeepForest
    steps = body["dataset_builder_workflow_steps"]
    assert [s["step"] for s in steps] == [1, 2, 3]
    assert body["deepforest_csv_schema"]["required_columns"] == [
        "image_path",
        "xmin",
        "ymin",
        "xmax",
        "ymax",
        "label",
    ]
    assert "def xml_to_csv(path):" in body["deepforest_csv_schema"]["xml_to_csv_python_snippet"]


def test_xml_to_csv_supports_both_standard_pascal_voc_and_user_script_structure(tmp_path: Path):
    # File 1: Cấu trúc PASCAL VOC tiêu chuẩn từ LabelImg / Roboflow
    xml_standard = """<annotation>
        <filename>ben_tre_coconut_01.png</filename>
        <object>
            <name>Tree</name>
            <bndbox>
                <xmin>10</xmin>
                <ymin>20</ymin>
                <xmax>80</xmax>
                <ymax>95</ymax>
            </bndbox>
        </object>
    </annotation>"""

    # File 2: Cấu trúc chỉ số member[0][0..3] và member[0].text như trong đoạn script mẫu của người dùng
    xml_indexed = """<annotation>
        <filename>tay_nguyen_coffee_02.png</filename>
        <object>
            <label>Tree<xmin>120</xmin><ymin>140</ymin><xmax>200</xmax><ymax>220</ymax></label>
        </object>
    </annotation>"""

    (tmp_path / "a_standard.xml").write_text(xml_standard, encoding="utf-8")
    (tmp_path / "b_indexed.xml").write_text(xml_indexed, encoding="utf-8")

    df = vds.xml_to_csv(tmp_path)
    assert list(df.columns) == ["image_path", "xmin", "ymin", "xmax", "ymax", "label"]
    assert len(df) == 2
    assert df.iloc[0].to_dict() == {
        "image_path": "ben_tre_coconut_01.png",
        "xmin": 10,
        "ymin": 20,
        "xmax": 80,
        "ymax": 95,
        "label": "Tree",
    }
    assert df.iloc[1].to_dict() == {
        "image_path": "tay_nguyen_coffee_02.png",
        "xmin": 120,
        "ymin": 140,
        "xmax": 200,
        "ymax": 220,
        "label": "Tree",
    }


def test_dataset_xml_to_csv_endpoint_and_train_val_split(client):
    payload = {
        "val_ratio": 0.5,
        "normalize_label_to_tree": True,
        "xml_documents": [
            {
                "filename": "mekong_coconut_01.png",
                "xml_content": """<annotation>
                    <filename>mekong_coconut_01.png</filename>
                    <object><name>Coconut</name><bndbox><xmin>12</xmin><ymin>18</ymin><xmax>64</xmax><ymax>72</ymax></bndbox></object>
                    <object><name>Coconut</name><bndbox><xmin>90</xmin><ymin>20</ymin><xmax>142</xmax><ymax>76</ymax></bndbox></object>
                </annotation>""",
            },
            {
                "filename": "daklak_coffee_02.png",
                "xml_content": """<annotation>
                    <filename>daklak_coffee_02.png</filename>
                    <object><name>Coffee</name><bndbox><xmin>30</xmin><ymin>40</ymin><xmax>85</xmax><ymax>95</ymax></bndbox></object>
                </annotation>""",
            },
        ],
    }
    r = client.post("/v1/deepforest/dataset/xml-to-csv", headers=H, json=payload)
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["columns"] == ["image_path", "xmin", "ymin", "xmax", "ymax", "label"]
    assert j["total_boxes"] == 3
    assert j["split_mode"] == "independent_images"
    assert j["train_boxes_count"] == 2
    assert j["validation_boxes_count"] == 1
    assert j["labels_distribution"] == {"Tree": 3}
    assert "image_path,xmin,ymin,xmax,ymax,label" in j["vietnam_train_csv"]
    assert "mekong_coconut_01.png,12,18,64,72,Tree" in j["vietnam_train_csv"]
    assert "daklak_coffee_02.png,30,40,85,95,Tree" in j["vietnam_validation_csv"]


def test_yolo_to_csv_conversion(tmp_path: Path):
    (tmp_path / "garden_uav_01.txt").write_text("0 0.5 0.5 0.2 0.2\n", encoding="utf-8")
    df = vds.yolo_to_csv(tmp_path, image_width=1000, image_height=1000, image_ext=".png")
    assert list(df.columns) == ["image_path", "xmin", "ymin", "xmax", "ymax", "label"]
    assert len(df) == 1
    assert df.iloc[0].to_dict() == {
        "image_path": "garden_uav_01.png",
        "xmin": 400,
        "ymin": 400,
        "xmax": 600,
        "ymax": 600,
        "label": "Tree",
    }


def test_oam_search_vietnam_with_mock_fetcher_and_endpoint_validation(client):
    fake_oam_response = {
        "results": [
            {
                "_id": "oam-vn-mekong-001",
                "title": "Ben Tre Coconut UAV Orthomosaic",
                "provider": "Vietnam UAV Community",
                "platform": "uav",
                "gsd": 0.045,
                "acquisition_start": "2025-03-10T02:00:00Z",
                "acquisition_end": "2025-03-10T03:00:00Z",
                "bbox": [106.35, 10.22, 106.37, 10.24],
                "uuid": "https://oin-hotosm.s3.amazonaws.com/ben_tre_uav.tif",
                "properties": {
                    "tms": "https://tiles.openaerialmap.org/ben_tre/{z}/{x}/{y}.png",
                    "thumbnail": "https://tiles.openaerialmap.org/ben_tre/thumb.png",
                },
            }
        ]
    }
    res = vds.search_openaerialmap_vietnam(
        region_preset="mekong_delta",
        fetcher=lambda url: fake_oam_response,
    )
    assert res["live_api_reachable"] is True
    assert res["total_returned"] == 1
    assert res["suitable_for_deepforest_count"] == 1
    assert res["results"][0]["gsd_cm"] == 4.5
    assert res["results"][0]["suitable_for_deepforest"] is True

    # Kiểm tra lỗi 422 khi truyền thiếu tọa độ bbox trên endpoint
    r_bad = client.get("/v1/deepforest/oam/search", headers=H, params={"west": 106.0})
    assert r_bad.status_code == 422
