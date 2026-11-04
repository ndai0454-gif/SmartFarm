"""Nguồn dữ liệu vệ tinh/UAV độ phân giải cao (<0.5m–1m/pixel), bộ dữ liệu nghiên cứu
lâm nghiệp & nông nghiệp Việt Nam, và quy trình 3 bước đóng gói Dataset Vườn Việt Nam
(PASCAL VOC XML / YOLO -> DeepForest CSV `image_path,xmin,ymin,xmax,ymax,label`).
"""

from __future__ import annotations

import glob
import json
import os
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import pandas as pd

DEEPFOREST_CSV_COLUMNS = ["image_path", "xmin", "ymin", "xmax", "ymax", "label"]

OAM_META_API_URL = "https://api.openaerialmap.org/meta"
OAM_WEB_URL = "https://openaerialmap.org/"

# Các vùng trọng điểm nông - lâm nghiệp Việt Nam trên OpenAerialMap (west, south, east, north)
VIETNAM_OAM_REGION_PRESETS: dict[str, dict[str, Any]] = {
    "vietnam_all": {
        "preset_id": "vietnam_all",
        "name_vi": "Toàn lãnh thổ Việt Nam",
        "bbox": [102.14, 8.18, 109.46, 23.39],
        "focus_crops_vi": "Dừa, sầu riêng, cà phê, cao su, cây ăn quả, rừng ngập mặn & lâm nghiệp",
    },
    "central_highlands": {
        "preset_id": "central_highlands",
        "name_vi": "Tây Nguyên (Lâm Đồng, Đắk Lắk, Gia Lai, Đắk Nông, Kon Tum)",
        "bbox": [107.20, 11.20, 108.95, 14.60],
        "focus_crops_vi": "Cà phê (Robusta/Arabica), sầu riêng, hồ tiêu, bơ, cao su, thông lâm nghiệp",
    },
    "mekong_delta": {
        "preset_id": "mekong_delta",
        "name_vi": "Đồng bằng sông Cửu Long (Tiền Giang, Bến Tre, Vĩnh Long, Cần Thơ, Đồng Tháp...)",
        "bbox": [104.45, 8.55, 106.85, 11.05],
        "focus_crops_vi": "Dừa (Bến Tre/Trà Vinh), sầu riêng (Cai Lậy), xoài, bưởi, nhãn, rừng tràm/ngập mặn",
    },
    "southeast_orchards": {
        "preset_id": "southeast_orchards",
        "name_vi": "Đông Nam Bộ (Đồng Nai, Bình Phước, Tây Ninh, Bình Dương)",
        "bbox": [105.80, 10.50, 107.60, 12.30],
        "focus_crops_vi": "Cao su, điều, bưởi/cây có múi (Long Khánh), sầu riêng, tiêu",
    },
    "northern_forestry": {
        "preset_id": "northern_forestry",
        "name_vi": "Trung du & Miền núi phía Bắc (Bắc Giang, Lào Cai, Yên Bái, Sơn La)",
        "bbox": [103.50, 20.60, 107.00, 22.90],
        "focus_crops_vi": "Vải thiều/nhãn (Lục Ngạn), chè, rừng phòng hộ và khu vực theo dõi sạt lở",
    },
}

# 1. Nguồn Dữ liệu Vệ tinh & UAV Độ phân giải cao (Mặt bằng chung Việt Nam)
HIGH_RES_IMAGERY_SOURCES_VI: list[dict[str, Any]] = [
    {
        "source_id": "openaerialmap_vietnam",
        "title": "OpenAerialMap (OAM) - Việt Nam Collection",
        "provider": "OpenAerialMap / Humanitarian OpenStreetMap Team (HOT) & Cộng đồng UAV",
        "platform_type": "Drone / UAV Orthomosaic (RGB)",
        "spatial_resolution_m": "0.03 - 0.10",
        "spatial_resolution_label_vi": "Cực kỳ cao (3 cm – 10 cm/pixel)",
        "suitable_for_individual_crown_detection": True,
        "vietnam_coverage_vi": (
            "Có sẵn nhiều khu vực chụp bằng flycam/UAV tại Việt Nam, đặc biệt là vùng "
            "Đồng bằng sông Cửu Long, Tây Nguyên và các khu vực lâm nghiệp / sạt lở."
        ),
        "description_vi": (
            "Nguồn ảnh Drone/UAV mở công khai do cộng đồng và các tổ chức phi chính phủ đăng tải. "
            "Độ phân giải 3 cm – 10 cm/pixel rất thích hợp để tách tán cây đơn lẻ bằng DeepForest."
        ),
        "access_url": OAM_WEB_URL,
        "api_endpoint": OAM_META_API_URL,
        "license_vi": "Mở công khai (CC-BY 4.0 / CC0 tùy bộ ảnh trên OAM)",
        "deepforest_role_vi": "Nguồn ảnh RGB độ phân giải siêu cao trực tiếp để chạy predict_tile và gán nhãn fine-tune.",
    },
    {
        "source_id": "planetscope_education_research",
        "title": "PlanetScope / Planet Labs (Education & Research License)",
        "provider": "Planet Labs PBC (SuperDove PSScene & SkySat)",
        "platform_type": "Chùm vệ tinh quang học chụp hàng ngày (PlanetScope 3m) & vệ tinh độ phân giải cao (SkySat 50cm)",
        "spatial_resolution_m": "0.50 (SkySat) / 3.00 (PlanetScope)",
        "spatial_resolution_label_vi": "SkySat: 50 cm/pixel (đếm tán cây) · PlanetScope: 3 m/pixel (phân vùng tán rộng)",
        "suitable_for_individual_crown_detection": True,
        "vietnam_coverage_vi": "Chụp hàng ngày trên toàn bộ lãnh thổ Việt Nam.",
        "description_vi": (
            "Cung cấp ảnh vệ tinh chụp hàng ngày trên toàn bộ lãnh thổ Việt Nam. "
            "Đăng ký tài khoản Nông nghiệp/Nghiên cứu miễn phí (Education & Research Program) để tải ảnh "
            "độ phân giải 3m (dùng cho phân vùng tán rừng rộng lớn) hoặc xin mẫu dữ liệu SkySat "
            "(50cm - nhận diện tán cây bằng DeepForest)."
        ),
        "access_url": "https://www.planet.com/markets/education-and-research/",
        "api_endpoint": "https://api.planet.com/data/v1",
        "license_vi": "Education & Research License (miễn phí cho nghiên cứu khoa học / nông nghiệp đại học)",
        "deepforest_role_vi": (
            "Dùng SkySat (50cm/pixel) để nhận diện tán cây lớn; dùng PlanetScope (3m/pixel) "
            "làm lớp nền phân vùng tán rừng/vườn rộng lớn trước khi bay drone."
        ),
    },
]

# 2. Bộ Dữ liệu Mẫu / Nghiên cứu Lâm nghiệp & Nông nghiệp Việt Nam
VIETNAM_RESEARCH_DATASETS_VI: list[dict[str, Any]] = [
    {
        "dataset_id": "gfw_hansen_vietnam_forest_cover",
        "title": "Vietnam Forest Cover & Tree Canopy Datasets (Global Forest Watch / Hansen Data)",
        "provider": "University of Maryland (GLAD) / Global Forest Watch / Google Earth Engine",
        "gee_asset": "UMD/hansen/global_forest_change_2023_v1_11",
        "gee_bands": ["treecover2000", "loss", "gain", "lossyear"],
        "spatial_resolution_m": 30.0,
        "modality": "Landsat / Sentinel Multi-temporal Canopy Cover & Forest Loss Mask",
        "description_vi": (
            "Dữ liệu bản đồ độ che phủ rừng và tán cây Việt Nam dựa trên ảnh vệ tinh Landsat/Sentinel."
        ),
        "usage_in_deepforest_vi": (
            "Dùng làm lớp mặt nạ (canopy mask) hoặc nhãn tham chiếu vùng lâm nghiệp để lọc bỏ vùng "
            "đất trống, mặt nước, khu dân cư trước khi chạy nhận diện tán cây."
        ),
        "access_url": "https://www.globalforestwatch.org/",
        "search_keywords": [
            "Vietnam Forest Cover Global Forest Watch",
            "UMD/hansen/global_forest_change_2023_v1_11",
        ],
    },
    {
        "dataset_id": "mendeley_zenodo_kaggle_vn_uav_orchards",
        "title": "Dữ liệu Ảnh Drone Cây Công nghiệp & Cây Ăn quả Việt Nam (Kaggle / Zenodo / Mendeley Data)",
        "provider": "Mendeley Data / Zenodo / Kaggle (Cộng đồng nghiên cứu UAV Nông nghiệp Việt Nam)",
        "gee_asset": None,
        "spatial_resolution_m": 0.05,
        "modality": "UAV RGB Orthomosaic + Bounding Box Annotations (PASCAL VOC XML / YOLO / CSV)",
        "description_vi": (
            "Nhiều bài báo khoa học về đếm số lượng cây dừa (Coconut detection), cà phê (Coffee canopy), "
            "cao su hoặc cây ăn quả tại Việt Nam có đính kèm file ảnh UAV gốc và tọa độ bounding box "
            "trên Zenodo, Mendeley Data và Kaggle."
        ),
        "usage_in_deepforest_vi": (
            "Tải ảnh UAV gốc và file nhãn XML/YOLO từ Zenodo/Mendeley Data, sau đó chạy hàm xml_to_csv() "
            "để chuyển sang chuẩn CSV của DeepForest (image_path, xmin, ymin, xmax, ymax, label) phục vụ fine-tune."
        ),
        "access_url": "https://zenodo.org/",
        "search_keywords": [
            "Vietnam Agricultural Drone Dataset",
            "Coconut tree detection UAV Vietnam",
            "Coffee tree canopy UAV",
            "Rubber tree crown detection UAV Vietnam",
            "Durian tree detection UAV",
        ],
        "repository_search_links": [
            {
                "repository": "Zenodo",
                "keyword": "Coconut tree detection UAV Vietnam",
                "url": "https://zenodo.org/search?q=Coconut+tree+detection+UAV+Vietnam",
            },
            {
                "repository": "Zenodo",
                "keyword": "Coffee tree canopy UAV",
                "url": "https://zenodo.org/search?q=Coffee+tree+canopy+UAV",
            },
            {
                "repository": "Mendeley Data",
                "keyword": "Vietnam Agricultural Drone Dataset",
                "url": "https://data.mendeley.com/research-data/?search=Vietnam%20Agricultural%20Drone%20Dataset",
            },
            {
                "repository": "Mendeley Data",
                "keyword": "Coconut tree detection UAV Vietnam",
                "url": "https://data.mendeley.com/research-data/?search=Coconut%20tree%20detection%20UAV%20Vietnam",
            },
            {
                "repository": "Kaggle",
                "keyword": "Vietnam Agricultural Drone Dataset",
                "url": "https://www.kaggle.com/search?q=Vietnam+Agricultural+Drone+Dataset",
            },
        ],
    },
]

# 3. Quy trình 3 bước xây dựng bộ Dataset Vườn Việt Nam cho DeepForest
VIETNAM_DATASET_WORKFLOW_STEPS_VI: list[dict[str, Any]] = [
    {
        "step": 1,
        "code": "DATA_ACQUISITION",
        "title_vi": "Bước 1: Thu thập ảnh (Data Acquisition)",
        "description_vi": (
            "Dùng flycam/drone chụp khu vực vườn cần nhận diện (Tây Nguyên: cà phê/sầu riêng, "
            "Miền Tây: dừa/cây ăn quả) ở độ phân giải 3cm – 10cm/pixel, hoặc tải ảnh Orthomosaic "
            "độ phân giải cao từ OpenAerialMap khu vực Việt Nam."
        ),
        "recommended_gsd_cm": "3 cm – 10 cm/pixel (tối đa < 50 cm/pixel)",
        "tools": ["Flycam / Drone (DJI Mavic 3M/3E, Phantom 4 RTK)", "OpenAerialMap (openaerialmap.org)", "Planet SkySat (50cm)"],
    },
    {
        "step": 2,
        "code": "BOUNDING_BOX_ANNOTATION",
        "title_vi": "Bước 2: Gán nhãn Bounding Box (Annotation)",
        "description_vi": (
            "Dùng phần mềm LabelImg hoặc Roboflow để khoanh vùng các tán cây trong ảnh và xuất ra "
            "định dạng PASCAL VOC (.xml) hoặc YOLO (.txt)."
        ),
        "tools": ["LabelImg (PASCAL VOC XML)", "Roboflow (PASCAL VOC XML / YOLO)", "CVAT"],
        "default_label": "Tree",
    },
    {
        "step": 3,
        "code": "DEEPFOREST_CSV_CONVERSION",
        "title_vi": "Bước 3: Chuyển đổi sang chuẩn CSV của DeepForest (xml_to_csv)",
        "description_vi": (
            "DeepForest yêu cầu file CSV chứa các cột: image_path, xmin, ymin, xmax, ymax, label. "
            "Sử dụng hàm xml_to_csv() hoặc endpoint POST /v1/deepforest/dataset/xml-to-csv để xuất "
            "vietnam_train.csv và vietnam_validation.csv thay thế cho dữ liệu smoke test (OSBS/SJER)."
        ),
        "required_columns": DEEPFOREST_CSV_COLUMNS,
        "output_files": ["vietnam_train.csv", "vietnam_validation.csv"],
    },
]

XML_TO_CSV_REFERENCE_SNIPPET = """import pandas as pd
import xml.etree.ElementTree as ET
import glob

def xml_to_csv(path):
    xml_list = []
    for xml_file in glob.glob(path + '/*.xml'):
        tree = ET.parse(xml_file)
        root = tree.getroot()
        for member in root.findall('object'):
            value = (root.find('filename').text,
                     int(member[0][0].text),
                     int(member[0][1].text),
                     int(member[0][2].text),
                     int(member[0][3].text),
                     member[0].text
                    )
            xml_list.append(value)
    column_name = ['image_path', 'xmin', 'ymin', 'xmax', 'ymax', 'label']
    xml_df = pd.DataFrame(xml_list, columns=column_name)
    return xml_df

# Xuất ra train.csv và validation.csv tương tự cấu trúc trong repo của bạn
train_df = xml_to_csv('path/to/vietnam_garden_labels')
train_df.to_csv('vietnam_train.csv', index=False)
"""


def _extract_pascal_voc_object(
    member: ET.Element, default_filename: str, normalize_label_to_tree: bool = False
) -> tuple[str, int, int, int, int, str]:
    """Đọc 1 thẻ `<object>` từ file XML PASCAL VOC.

    Hỗ trợ đồng thời:
    1. Cấu trúc PASCAL VOC tiêu chuẩn từ LabelImg / Roboflow:
       `<object><name>Tree</name>...<bndbox><xmin>...</xmin><ymin>...</ymin><xmax>...</xmax><ymax>...</ymax></bndbox></object>`
    2. Cấu trúc chỉ số con trực tiếp trong script mẫu của người dùng:
       `member[0][0..3]` cho `xmin, ymin, xmax, ymax` và `member[0].text` cho `label`.
    """
    bndbox = member.find("bndbox")
    name_el = member.find("name")

    if bndbox is not None and bndbox.find("xmin") is not None:
        xmin = round(float(bndbox.findtext("xmin", "0").strip()))
        ymin = round(float(bndbox.findtext("ymin", "0").strip()))
        xmax = round(float(bndbox.findtext("xmax", "0").strip()))
        ymax = round(float(bndbox.findtext("ymax", "0").strip()))
        raw_label = (name_el.text or "").strip() if name_el is not None else "Tree"
    else:
        # Hỗ trợ cấu trúc member[0][0..3] và member[0].text đúng như script mẫu
        if len(member) == 0 or len(member[0]) < 4:
            raise ValueError("Thẻ <object> thiếu <bndbox> hoặc 4 tọa độ xmin, ymin, xmax, ymax.")
        xmin = round(float((member[0][0].text or "0").strip()))
        ymin = round(float((member[0][1].text or "0").strip()))
        xmax = round(float((member[0][2].text or "0").strip()))
        ymax = round(float((member[0][3].text or "0").strip()))
        raw_label = (member[0].text or "").strip()
        if not raw_label and name_el is not None and name_el.text:
            raw_label = name_el.text.strip()

    if not raw_label:
        raw_label = "Tree"
    label = "Tree" if normalize_label_to_tree else raw_label
    return (default_filename, int(xmin), int(ymin), int(xmax), int(ymax), label)


def parse_xml_root_to_rows(
    root: ET.Element,
    fallback_filename: str = "unknown_image.png",
    normalize_label_to_tree: bool = False,
) -> list[tuple[str, int, int, int, int, str]]:
    """Trích xuất danh sách `(image_path, xmin, ymin, xmax, ymax, label)` từ một cây XML."""
    filename_el = root.find("filename")
    image_path = (
        filename_el.text.strip()
        if filename_el is not None and filename_el.text and filename_el.text.strip()
        else fallback_filename
    )
    rows: list[tuple[str, int, int, int, int, str]] = []
    for member in root.findall("object"):
        row = _extract_pascal_voc_object(
            member, image_path, normalize_label_to_tree=normalize_label_to_tree
        )
        rows.append(row)
    return rows


def xml_to_csv(
    path: str | os.PathLike[str],
    normalize_label_to_tree: bool = False,
) -> pd.DataFrame:
    """Chuyển đổi thư mục chứa các file nhãn PASCAL VOC (`.xml`) sang `DataFrame` chuẩn của DeepForest.

    Các cột đầu ra luôn theo đúng thứ tự:
    `['image_path', 'xmin', 'ymin', 'xmax', 'ymax', 'label']`.
    """
    xml_list: list[tuple[str, int, int, int, int, str]] = []
    pattern = os.path.join(str(path), "*.xml")
    for xml_file in sorted(glob.glob(pattern)):
        tree = ET.parse(xml_file)
        root = tree.getroot()
        default_img = Path(xml_file).with_suffix(".png").name
        xml_list.extend(
            parse_xml_root_to_rows(
                root,
                fallback_filename=default_img,
                normalize_label_to_tree=normalize_label_to_tree,
            )
        )
    xml_df = pd.DataFrame(xml_list, columns=DEEPFOREST_CSV_COLUMNS)
    return xml_df


def yolo_to_csv(
    labels_dir: str | os.PathLike[str],
    image_width: int,
    image_height: int,
    image_ext: str = ".png",
    class_map: dict[int, str] | None = None,
) -> pd.DataFrame:
    """Chuyển đổi thư mục nhãn YOLO (`class_id x_center y_center width height` chuẩn hóa 0..1)
    sang DataFrame chuẩn của DeepForest `['image_path', 'xmin', 'ymin', 'xmax', 'ymax', 'label']`.
    """
    if image_width <= 0 or image_height <= 0:
        raise ValueError("Kích thước ảnh image_width và image_height phải dương.")
    cmap = class_map or {0: "Tree"}
    rows: list[tuple[str, int, int, int, int, str]] = []
    pattern = os.path.join(str(labels_dir), "*.txt")
    for txt_file in sorted(glob.glob(pattern)):
        stem = Path(txt_file).stem
        if stem.lower() == "classes":
            continue
        image_name = f"{stem}{image_ext if image_ext.startswith('.') else '.' + image_ext}"
        content = Path(txt_file).read_text(encoding="utf-8").strip()
        if not content:
            continue
        for line in content.splitlines():
            parts = line.strip().split()
            if len(parts) < 5:
                continue
            cls_id = int(float(parts[0]))
            xc, yc, w, h = (float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4]))
            xmin = max(0, round((xc - w / 2.0) * image_width))
            ymin = max(0, round((yc - h / 2.0) * image_height))
            xmax = min(image_width, round((xc + w / 2.0) * image_width))
            ymax = min(image_height, round((yc + h / 2.0) * image_height))
            if xmax > xmin and ymax > ymin:
                rows.append((image_name, xmin, ymin, xmax, ymax, cmap.get(cls_id, "Tree")))
    return pd.DataFrame(rows, columns=DEEPFOREST_CSV_COLUMNS)


def convert_xml_strings_to_deepforest_csv(
    xml_documents: list[dict[str, str]],
    val_ratio: float = 0.2,
    normalize_label_to_tree: bool = False,
    output_dir: str | os.PathLike[str] | None = None,
) -> dict[str, Any]:
    """Chuyển đổi danh sách nội dung file PASCAL VOC XML sang chuẩn CSV của DeepForest,
    tự động kiểm tra tọa độ hợp lệ (`xmax > xmin`, `ymax > ymin`) và chia tập
    `vietnam_train.csv` / `vietnam_validation.csv`.
    """
    if not xml_documents:
        raise ValueError("Cần ít nhất 1 tài liệu XML PASCAL VOC để chuyển đổi.")
    all_rows: list[tuple[str, int, int, int, int, str]] = []
    invalid_boxes = 0
    for idx, doc in enumerate(xml_documents):
        xml_content = (doc.get("xml_content") or "").strip()
        filename_hint = (doc.get("filename") or f"vietnam_garden_{idx + 1:03d}.png").strip()
        if not xml_content:
            raise ValueError(f"Tài liệu XML thứ {idx + 1} trống.")
        try:
            root = ET.fromstring(xml_content)
        except ET.ParseError as exc:
            raise ValueError(f"XML thứ {idx + 1} không đúng cú pháp: {exc}") from exc
        extracted = parse_xml_root_to_rows(
            root,
            fallback_filename=filename_hint,
            normalize_label_to_tree=normalize_label_to_tree,
        )
        for img_path, xmin, ymin, xmax, ymax, label in extracted:
            if xmin < 0 or ymin < 0 or xmax <= xmin or ymax <= ymin:
                invalid_boxes += 1
                continue
            all_rows.append((img_path, xmin, ymin, xmax, ymax, label))

    df = pd.DataFrame(all_rows, columns=DEEPFOREST_CSV_COLUMNS)
    if df.empty:
        raise ValueError("Không tìm thấy bounding box hợp lệ nào (xmin < xmax, ymin < ymax) trong XML.")

    # Chia train / validation: ưu tiên chia theo ảnh độc lập nếu có >= 2 ảnh, ngược lại chia theo dòng
    unique_images = list(dict.fromkeys(df["image_path"].tolist()))
    if len(unique_images) >= 2 and 0.0 < val_ratio < 1.0:
        val_img_count = max(1, round(len(unique_images) * val_ratio))
        val_images = set(unique_images[-val_img_count:])
        train_df = df[~df["image_path"].isin(val_images)].reset_index(drop=True)
        val_df = df[df["image_path"].isin(val_images)].reset_index(drop=True)
        split_mode = "independent_images"
    elif len(df) >= 2 and 0.0 < val_ratio < 1.0:
        val_row_count = max(1, round(len(df) * val_ratio))
        train_df = df.iloc[:-val_row_count].reset_index(drop=True)
        val_df = df.iloc[-val_row_count:].reset_index(drop=True)
        split_mode = "row_split_single_image"
    else:
        train_df = df.copy().reset_index(drop=True)
        val_df = df.iloc[0:0].copy()
        split_mode = "train_only"

    saved_files: dict[str, str] = {}
    if output_dir is not None:
        out_path = Path(output_dir)
        out_path.mkdir(parents=True, exist_ok=True)
        train_file = out_path / "vietnam_train.csv"
        val_file = out_path / "vietnam_validation.csv"
        full_file = out_path / "vietnam_annotations.csv"
        df.to_csv(full_file, index=False)
        train_df.to_csv(train_file, index=False)
        val_df.to_csv(val_file, index=False)
        saved_files = {
            "full_csv_path": str(full_file.resolve()),
            "train_csv_path": str(train_file.resolve()),
            "validation_csv_path": str(val_file.resolve()),
        }

    return {
        "columns": DEEPFOREST_CSV_COLUMNS,
        "total_boxes": int(len(df)),
        "invalid_boxes_skipped": invalid_boxes,
        "unique_images_count": len(unique_images),
        "unique_images": unique_images,
        "labels_distribution": {str(k): int(v) for k, v in df["label"].value_counts().items()},
        "split_mode": split_mode,
        "train_boxes_count": int(len(train_df)),
        "validation_boxes_count": int(len(val_df)),
        "full_csv": df.to_csv(index=False),
        "vietnam_train_csv": train_df.to_csv(index=False),
        "vietnam_validation_csv": val_df.to_csv(index=False),
        "records": df.to_dict(orient="records"),
        "saved_files": saved_files,
    }


def search_openaerialmap_vietnam(
    bbox: list[float] | tuple[float, float, float, float] | None = None,
    region_preset: str | None = None,
    limit: int = 12,
    max_gsd_m: float = 0.50,
    fetcher: Any | None = None,
) -> dict[str, Any]:
    """Truy vấn danh mục ảnh Drone/UAV mở trên OpenAerialMap (OAM) tại Việt Nam.

    - Nếu `bbox` không được truyền, lấy theo `region_preset` (mặc định `vietnam_all`).
    - Đánh dấu từng ảnh OAM xem có đạt ngưỡng độ phân giải `< 0.5m/pixel` cho DeepForest hay không.
    - Nếu không kết nối được tới `api.openaerialmap.org` (môi trường offline), trả rõ trạng thái
      `live_api_reachable=False` kèm đường dẫn truy vấn trực tiếp mà không bịa kết quả giả.
    """
    preset_key = region_preset if region_preset in VIETNAM_OAM_REGION_PRESETS else "vietnam_all"
    preset = VIETNAM_OAM_REGION_PRESETS[preset_key]
    if bbox is not None and len(bbox) == 4:
        west, south, east, north = [float(x) for x in bbox]
    else:
        west, south, east, north = [float(x) for x in preset["bbox"]]

    if west >= east or south >= north:
        raise ValueError("Khung tọa độ bbox phải thỏa mãn west < east và south < north.")

    clamped_limit = max(1, min(50, int(limit)))
    params = urllib.parse.urlencode(
        {
            "bbox": f"{west},{south},{east},{north}",
            "limit": str(clamped_limit),
            "order_by": "acquisition_end",
            "sort": "desc",
        }
    )
    query_url = f"{OAM_META_API_URL}?{params}"
    browser_url = f"{OAM_WEB_URL}#/{(west + east) / 2:.4f},{(south + north) / 2:.4f},10"

    raw_results: list[dict[str, Any]] = []
    live_reachable = False
    network_note = None

    try:
        if fetcher is not None:
            payload = fetcher(query_url)
        else:
            req = urllib.request.Request(
                query_url,
                headers={"User-Agent": "VWing-SmartFarm-DeepForest/1.0"},
            )
            with urllib.request.urlopen(req, timeout=8) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        raw_results = list(payload.get("results") or [])
        live_reachable = True
    except Exception as exc:
        network_note = (
            f"Không thể kết nối trực tiếp tới OpenAerialMap API ({type(exc).__name__}). "
            f"Bạn có thể mở trực tiếp {browser_url} hoặc {query_url} để tải ảnh GeoTIFF về."
        )

    items: list[dict[str, Any]] = []
    for item in raw_results:
        gsd_m = float(item.get("gsd") or 0.0)
        props = item.get("properties") or {}
        tms_url = props.get("tms") or item.get("tms")
        uuid_val = str(item.get("_id") or item.get("uuid") or "")
        items.append(
            {
                "id": uuid_val,
                "title": item.get("title") or "OpenAerialMap UAV Imagery",
                "provider": item.get("provider") or "OpenAerialMap Community",
                "platform": item.get("platform") or "uav",
                "gsd_m": round(gsd_m, 4) if gsd_m > 0 else None,
                "gsd_cm": round(gsd_m * 100.0, 2) if gsd_m > 0 else None,
                "suitable_for_deepforest": bool(0 < gsd_m <= max_gsd_m),
                "acquisition_start": item.get("acquisition_start"),
                "acquisition_end": item.get("acquisition_end"),
                "bbox": item.get("bbox"),
                "geotiff_url": item.get("uuid"),
                "tms_url": tms_url,
                "thumbnail_url": props.get("thumbnail"),
                "viewer_url": f"https://map.openaerialmap.org/#/{uuid_val}" if uuid_val else OAM_WEB_URL,
            }
        )

    suitable_count = sum(1 for x in items if x["suitable_for_deepforest"])
    return {
        "provider": "OpenAerialMap (OAM) - Việt Nam Collection",
        "region_preset": preset_key,
        "region_name_vi": preset["name_vi"],
        "focus_crops_vi": preset["focus_crops_vi"],
        "query_bbox": {"west": west, "south": south, "east": east, "north": north},
        "query_url": query_url,
        "browser_explore_url": browser_url,
        "max_gsd_m_for_deepforest": max_gsd_m,
        "live_api_reachable": live_reachable,
        "total_returned": len(items),
        "suitable_for_deepforest_count": suitable_count,
        "results": items,
        "network_note_vi": network_note,
        "available_vietnam_presets": list(VIETNAM_OAM_REGION_PRESETS.values()),
    }


def get_vietnam_deepforest_catalog() -> dict[str, Any]:
    """Trả về toàn bộ danh mục nguồn ảnh độ phân giải cao (<0.5m–1m/pixel),
    bộ dữ liệu nghiên cứu nông-lâm nghiệp Việt Nam và quy trình 3 bước tạo dataset
    DeepForest cho vườn Việt Nam.
    """
    return {
        "title_vi": (
            "Nguồn Dữ liệu Vệ tinh / UAV Độ phân giải cao & Quy trình Xây dựng "
            "Dataset Vườn Việt Nam cho DeepForest"
        ),
        "spatial_resolution_requirement_vi": (
            "Để nhận diện và đếm tán cây đơn lẻ bằng DeepForest (RetinaNet), cần ảnh RGB độ phân giải "
            "không gian cao tốt hơn 0.5m – 1.0m/pixel (tối ưu 3cm – 10cm/pixel từ Drone/UAV hoặc 50cm từ SkySat). "
            "Ảnh Sentinel-2 (10m/pixel) hoặc PlanetScope (3m/pixel) chỉ phù hợp để phân vùng tán rừng/vườn rộng lớn."
        ),
        "smoke_test_status_note_vi": (
            "Dữ liệu mặc định trong E:\\Predict\\artifacts\\training-smoke mới chỉ có 2 ảnh mẫu NEON Hoa Kỳ "
            "(OSBS_029.png và 2018_SJER_3_252000_4107000_image_477.tif). Cần đóng gói tập vietnam_train.csv "
            "và vietnam_validation.csv theo quy trình 3 bước dưới đây cho vườn Việt Nam."
        ),
        "high_resolution_imagery_sources": HIGH_RES_IMAGERY_SOURCES_VI,
        "vietnam_forestry_agriculture_datasets": VIETNAM_RESEARCH_DATASETS_VI,
        "oam_vietnam_region_presets": list(VIETNAM_OAM_REGION_PRESETS.values()),
        "dataset_builder_workflow_steps": VIETNAM_DATASET_WORKFLOW_STEPS_VI,
        "deepforest_csv_schema": {
            "required_columns": DEEPFOREST_CSV_COLUMNS,
            "example_row": {
                "image_path": "ben_tre_coconut_uav_01.png",
                "xmin": 124,
                "ymin": 88,
                "xmax": 216,
                "ymax": 182,
                "label": "Tree",
            },
            "xml_to_csv_python_snippet": XML_TO_CSV_REFERENCE_SNIPPET,
        },
    }
