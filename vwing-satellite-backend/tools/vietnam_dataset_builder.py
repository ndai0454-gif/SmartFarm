"""Công cụ xây dựng bộ Dataset Vườn Việt Nam chuẩn CSV cho DeepForest (Bước 3).

Hỗ trợ:
1. Hàm `xml_to_csv(path)` chuẩn PASCAL VOC XML (từ LabelImg / Roboflow) -> DataFrame
   với 6 cột `['image_path', 'xmin', 'ymin', 'xmax', 'ymax', 'label']`.
2. Hàm `yolo_to_csv(labels_dir, image_width, image_height)` từ nhãn YOLO `.txt`.
3. Tự động xuất `vietnam_train.csv` và `vietnam_validation.csv` tương thích trực tiếp
   với `finetune.py` và `/v1/deepforest/finetune`.
"""

from __future__ import annotations

import argparse
import glob
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pandas as pd

# Cho phép import trực tiếp từ thư mục gốc vwing-satellite-backend
ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.vietnam_deepforest_sources import (  # noqa: E402
    DEEPFOREST_CSV_COLUMNS,
    get_vietnam_deepforest_catalog,
    search_openaerialmap_vietnam,
    xml_to_csv,
    yolo_to_csv,
)


def xml_to_csv_classic(path: str) -> pd.DataFrame:
    """Hàm `xml_to_csv(path)` nguyên bản theo cấu trúc PASCAL VOC XML cho DeepForest."""
    xml_list = []
    for xml_file in glob.glob(path + "/*.xml"):
        tree = ET.parse(xml_file)
        root = tree.getroot()
        filename_node = root.find("filename")
        filename = (
            filename_node.text.strip()
            if filename_node is not None and filename_node.text
            else Path(xml_file).with_suffix(".png").name
        )
        for member in root.findall("object"):
            bndbox = member.find("bndbox")
            name_node = member.find("name")
            if bndbox is not None and bndbox.find("xmin") is not None:
                value = (
                    filename,
                    int(float(bndbox.find("xmin").text)),
                    int(float(bndbox.find("ymin").text)),
                    int(float(bndbox.find("xmax").text)),
                    int(float(bndbox.find("ymax").text)),
                    name_node.text.strip() if name_node is not None and name_node.text else "Tree",
                )
            else:
                value = (
                    filename,
                    int(float(member[0][0].text)),
                    int(float(member[0][1].text)),
                    int(float(member[0][2].text)),
                    int(float(member[0][3].text)),
                    (member[0].text or "Tree").strip(),
                )
            xml_list.append(value)
    column_name = ["image_path", "xmin", "ymin", "xmax", "ymax", "label"]
    xml_df = pd.DataFrame(xml_list, columns=column_name)
    return xml_df


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--labels-dir",
        type=Path,
        help="Thư mục chứa các file nhãn PASCAL VOC (.xml) hoặc YOLO (.txt) của vườn Việt Nam",
    )
    parser.add_argument(
        "--train-out",
        type=Path,
        default=Path("vietnam_train.csv"),
        help="Đường dẫn file CSV tập huấn luyện đầu ra (mặc định: vietnam_train.csv)",
    )
    parser.add_argument(
        "--val-out",
        type=Path,
        default=Path("vietnam_validation.csv"),
        help="Đường dẫn file CSV tập kiểm định đầu ra (mặc định: vietnam_validation.csv)",
    )
    parser.add_argument(
        "--val-ratio",
        type=float,
        default=0.2,
        help="Tỷ lệ chia tập validation (0.0 - 0.5, mặc định: 0.2)",
    )
    parser.add_argument(
        "--normalize-tree-label",
        action="store_true",
        help="Chuẩn hóa mọi nhãn lớp về 'Tree' theo yêu cầu mặc định của DeepForest",
    )
    parser.add_argument(
        "--list-vietnam-sources",
        action="store_true",
        help="In danh mục nguồn ảnh vệ tinh/UAV Việt Nam (OAM, PlanetScope/SkySat, GFW/Hansen, Zenodo/Mendeley)",
    )
    parser.add_argument(
        "--search-oam",
        type=str,
        default="",
        help="Tìm kiếm ảnh UAV OpenAerialMap theo vùng Việt Nam (central_highlands, mekong_delta, vietnam_all...)",
    )
    args = parser.parse_args()

    if args.list_vietnam_sources:
        import json

        print(json.dumps(get_vietnam_deepforest_catalog(), ensure_ascii=False, indent=2))
        return

    if args.search_oam:
        import json

        res = search_openaerialmap_vietnam(region_preset=args.search_oam)
        print(json.dumps(res, ensure_ascii=False, indent=2))
        return

    if not args.labels_dir or not args.labels_dir.is_dir():
        parser.error("Cần truyền --labels-dir trỏ tới thư mục chứa các file .xml nhãn vườn Việt Nam.")

    df = xml_to_csv(args.labels_dir, normalize_label_to_tree=args.normalize_tree_label)
    if df.empty:
        raise SystemExit(f"Không tìm thấy nhãn XML nào trong {args.labels_dir}")

    unique_imgs = list(dict.fromkeys(df["image_path"].tolist()))
    if len(unique_imgs) >= 2 and 0.0 < args.val_ratio < 1.0:
        n_val = max(1, round(len(unique_imgs) * args.val_ratio))
        val_imgs = set(unique_imgs[-n_val:])
        train_df = df[~df["image_path"].isin(val_imgs)].reset_index(drop=True)
        val_df = df[df["image_path"].isin(val_imgs)].reset_index(drop=True)
    elif len(df) >= 2 and 0.0 < args.val_ratio < 1.0:
        n_val = max(1, round(len(df) * args.val_ratio))
        train_df = df.iloc[:-n_val].reset_index(drop=True)
        val_df = df.iloc[-n_val:].reset_index(drop=True)
    else:
        train_df = df
        val_df = pd.DataFrame(columns=DEEPFOREST_CSV_COLUMNS)

    args.train_out.parent.mkdir(parents=True, exist_ok=True)
    train_df.to_csv(args.train_out, index=False)
    print(f"Đã xuất {len(train_df)} box ra {args.train_out}")

    if not val_df.empty:
        args.val_out.parent.mkdir(parents=True, exist_ok=True)
        val_df.to_csv(args.val_out, index=False)
        print(f"Đã xuất {len(val_df)} box ra {args.val_out}")


if __name__ == "__main__":
    main()
