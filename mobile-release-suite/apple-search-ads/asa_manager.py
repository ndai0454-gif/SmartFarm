#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
============================================================================
MODULE 5: APPLE SEARCH ADS (ASA API v5) & CUSTOM PRODUCT PAGES AUTOMATION
(Dựa trên kiến trúc SearchAds/api-python-sdk & apple-search-ads-management-scripts)
============================================================================
Tự động hóa quản lý chiến dịch Apple Search Ads cho SmartFarm Aurora:
  1. Kiến trúc 4 Chiến dịch chuẩn (Brand, Category/Generic, Competitor, Discovery)
  2. Tự động đồng bộ Negative Keywords chéo (chống tự đấu giá chồng lấn giữa Discovery & Exact)
  3. Thuật toán tối ưu giá thầu CPT (Cost-Per-Tap) theo mục tiêu CPA (Cost-Per-Acquisition)
  4. Cấu hình Ad Variations gắn với 3 trang Custom Product Pages (CPP) chuyên biệt trên App Store
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

# Đảm bảo in tiếng Việt UTF-8 trên Windows PowerShell / CMD
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SUITE_DIR = Path(__file__).resolve().parent

# ----------------------------------------------------------------------------
# 1. CẤU HÌNH 3 TRANG SẢN PHẨM TÙY BIẾN (CUSTOM PRODUCT PAGES - CPP)
# ----------------------------------------------------------------------------
CUSTOM_PRODUCT_PAGES = [
    {
        "cppId": "cpp-smartfarm-gee-durian-01",
        "name": "CPP 1 — Viễn thám Vệ tinh GEE & Đếm cây Sầu riêng",
        "deepLink": "smartfarm://aurora/satellite?preset=durian-baoloc",
        "primaryAudience": "Chủ vườn sầu riêng, bơ, cà phê tại Tây Nguyên & ĐBSCL",
        "screenshotsFocus": [
            "Bản đồ 7 chỉ số phổ Sentinel-2 (NDVI, EVI, NDRE, NDMI)",
            "Đếm cây tự động DeepForest & Dự báo sản lượng thu hoạch",
            "Trạm khí tượng cảnh báo mưa dông & giờ phun thuốc an toàn",
        ],
    },
    {
        "cppId": "cpp-smartfarm-vn2000-gis-02",
        "name": "CPP 2 — Quy đổi Sổ Đỏ VN-2000 & Bản đồ Quy hoạch 34 Tỉnh",
        "deepLink": "smartfarm://aurora/regions?tab=vn2000",
        "primaryAudience": "Kỹ sư trắc địa, địa chính nông lâm và chủ trang trại đo ranh đất",
        "screenshotsFocus": [
            "Quy đổi bảng tọa độ góc ranh Sổ đỏ VN-2000 sang WGS84 (7 tham số Helmert)",
            "Tra cứu Kinh tuyến trục L0 của 34 tỉnh mới sau sáp nhập & 63 tỉnh gốc",
            "Chồng lớp bản đồ KML, KMZ, GeoJSON trực tiếp trên thực địa",
        ],
    },
    {
        "cppId": "cpp-smartfarm-3d-rtk-cad-03",
        "name": "CPP 3 — Địa hình 3D, Thủy văn Ngập lụt & Trạm đo GNSS RTK",
        "deepLink": "smartfarm://aurora/datumapp?tab=flood",
        "primaryAudience": "Kỹ sư thủy lợi, thiết kế san lấp lô vườn, người dùng máy RTK/Drone",
        "screenshotsFocus": [
            "Mô phỏng mực nước dâng & Sóng 3-Octave (DatumRiverSlopeWater)",
            "Mặt cắt địa hình Hermite Spline & Đường đồng mức Marching Squares",
            "Giải mã NMEA-0183 GNSS RTK, Bố trí điểm thực địa (Stakeout) & Xuất AutoCAD DXF",
        ],
    },
]

# ----------------------------------------------------------------------------
# 2. DỮ LIỆU CHIẾN DỊCH & TỪ KHÓA MẪU ĐỂ TỐI ƯU HÓA BID & NEGATIVE KEYWORDS
# ----------------------------------------------------------------------------
CAMPAIGN_BLUEPRINTS = [
    {
        "campaignName": "VN_SmartFarm_01_Brand_Exact",
        "campaignType": "BRAND",
        "dailyBudgetUsd": 25.0,
        "targetCpaUsd": 1.20,
        "searchMatch": False,
        "cppId": "cpp-smartfarm-gee-durian-01",
        "keywords": [
            {"text": "smartfarm aurora", "matchType": "EXACT", "currentCpt": 0.65, "impressions": 1420, "taps": 310, "installs": 195},
            {"text": "vwing smartfarm", "matchType": "EXACT", "currentCpt": 0.60, "impressions": 890, "taps": 215, "installs": 140},
            {"text": "dji smartfarm", "matchType": "EXACT", "currentCpt": 0.85, "impressions": 2650, "taps": 340, "installs": 145},
        ],
    },
    {
        "campaignName": "VN_SmartFarm_02_Generic_Satellite_NDVI",
        "campaignType": "GENERIC_GEE",
        "dailyBudgetUsd": 45.0,
        "targetCpaUsd": 2.10,
        "searchMatch": False,
        "cppId": "cpp-smartfarm-gee-durian-01",
        "keywords": [
            {"text": "bản đồ vệ tinh nông nghiệp", "matchType": "EXACT", "currentCpt": 0.90, "impressions": 3200, "taps": 290, "installs": 135},
            {"text": "chỉ số ndvi", "matchType": "EXACT", "currentCpt": 0.85, "impressions": 1950, "taps": 210, "installs": 112},
            {"text": "chăm sóc sầu riêng", "matchType": "EXACT", "currentCpt": 0.75, "impressions": 4100, "taps": 395, "installs": 182},
        ],
    },
    {
        "campaignName": "VN_SmartFarm_03_Generic_VN2000_GIS",
        "campaignType": "GENERIC_GIS",
        "dailyBudgetUsd": 40.0,
        "targetCpaUsd": 1.85,
        "searchMatch": False,
        "cppId": "cpp-smartfarm-vn2000-gis-02",
        "keywords": [
            {"text": "chuyển tọa độ vn2000", "matchType": "EXACT", "currentCpt": 0.95, "impressions": 5400, "taps": 610, "installs": 340},
            {"text": "đo diện tích đất", "matchType": "EXACT", "currentCpt": 0.80, "impressions": 6200, "taps": 540, "installs": 215},
            {"text": "đọc file kml quy hoạch", "matchType": "EXACT", "currentCpt": 0.70, "impressions": 2800, "taps": 260, "installs": 148},
        ],
    },
    {
        "campaignName": "VN_SmartFarm_04_Technical_3D_RTK",
        "campaignType": "GENERIC_3D_RTK",
        "dailyBudgetUsd": 30.0,
        "targetCpaUsd": 2.40,
        "searchMatch": False,
        "cppId": "cpp-smartfarm-3d-rtk-cad-03",
        "keywords": [
            {"text": "đo rtk gnss", "matchType": "EXACT", "currentCpt": 1.10, "impressions": 1500, "taps": 180, "installs": 88},
            {"text": "đường đồng mức địa hình", "matchType": "EXACT", "currentCpt": 0.85, "impressions": 1250, "taps": 140, "installs": 69},
        ],
    },
    {
        "campaignName": "VN_SmartFarm_05_Discovery_Broad_SearchMatch",
        "campaignType": "DISCOVERY",
        "dailyBudgetUsd": 30.0,
        "targetCpaUsd": 2.50,
        "searchMatch": True,
        "cppId": "cpp-smartfarm-gee-durian-01",
        "keywords": [
            {"text": "nông nghiệp thông minh", "matchType": "BROAD", "currentCpt": 0.70, "impressions": 4800, "taps": 220, "installs": 68},
            {"text": "bản đồ quy hoạch đất đai", "matchType": "BROAD", "currentCpt": 0.80, "impressions": 5100, "taps": 190, "installs": 32},
        ],
    },
]


def optimize_keyword_bids(campaigns):
    """
    Thuật toán tối ưu giá thầu CPT (Cost-Per-Tap) chuẩn Apple Search Ads:
      - Tính TTR (Tap-Through Rate) = taps / impressions
      - Tính CR (Conversion Rate) = installs / taps
      - Tính CPA thực tế = (taps * currentCpt) / installs = currentCpt / CR
      - CPT mục tiêu = targetCpaUsd * CR (giới hạn bước nhảy ±25% mỗi chu kỳ để ổn định thuật toán đấu giá)
    """
    recommendations = []
    exact_keywords_pool = set()

    for camp in campaigns:
        target_cpa = camp["targetCpaUsd"]
        for kw in camp["keywords"]:
            if kw["matchType"] == "EXACT":
                exact_keywords_pool.add(kw["text"].lower())

            taps = max(1, kw["taps"])
            installs = kw["installs"]
            current_cpt = kw["currentCpt"]
            ttr = kw["taps"] / max(1, kw["impressions"])
            cr = installs / taps
            actual_cpa = (taps * current_cpt) / max(1, installs)

            ideal_cpt = target_cpa * cr
            min_step = current_cpt * 0.75
            max_step = current_cpt * 1.25
            recommended_cpt = round(max(0.25, min(max_step, max(min_step, ideal_cpt))), 2)

            if recommended_cpt > current_cpt + 0.02:
                action = "TĂNG BID (+SCALE)"
            elif recommended_cpt < current_cpt - 0.02:
                action = "GIẢM BID (-OPTIMIZE CPA)"
            else:
                action = "GIỮ NGUYÊN (STABLE)"

            recommendations.append({
                "campaign": camp["campaignName"],
                "cppId": camp["cppId"],
                "keyword": kw["text"],
                "matchType": kw["matchType"],
                "ttrPct": round(ttr * 100, 2),
                "crPct": round(cr * 100, 2),
                "actualCpaUsd": round(actual_cpa, 2),
                "targetCpaUsd": target_cpa,
                "currentCptUsd": current_cpt,
                "recommendedCptUsd": recommended_cpt,
                "action": action,
            })

    # Sinh danh sách Negative Keywords tự động cho chiến dịch Discovery (chống tự cạnh tranh)
    discovery_negatives = [
        {"text": kw_text, "matchType": "EXACT", "reason": "Đã chạy Exact ở chiến dịch chuyên biệt"}
        for kw_text in sorted(exact_keywords_pool)
    ]

    return recommendations, discovery_negatives


def main():
    parser = argparse.ArgumentParser(description="Apple Search Ads (ASA API v5) Automation Suite")
    parser.add_argument("--simulate", action="store_true", help="Chạy mô phỏng tối ưu hóa giá thầu & xuất cấu hình ASA API v5")
    args = parser.parse_args()

    print("╔══════════════════════════════════════════════════════════════════════════════╗")
    print("║ APPLE SEARCH ADS (ASA API v5) & CUSTOM PRODUCT PAGES AUTOMATION SUITE        ║")
    print("╚══════════════════════════════════════════════════════════════════════════════╝")

    recommendations, discovery_negatives = optimize_keyword_bids(CAMPAIGN_BLUEPRINTS)

    print("\n[1] CẤU HÌNH 3 TRANG SẢN PHẨM TÙY BIẾN (CUSTOM PRODUCT PAGES - CPP):")
    for cpp in CUSTOM_PRODUCT_PAGES:
        print(f"  • {cpp['cppId']} | {cpp['name']}")
        print(f"    └─ DeepLink: {cpp['deepLink']} | Đối tượng: {cpp['primaryAudience']}")

    print("\n[2] BẢNG ĐỀ XUẤT TỐI ƯU GIÁ THẦU TỪ KHÓA (CPT BID OPTIMIZATION):")
    for rec in recommendations:
        print(
            f"  • [{rec['matchType']:5}] {rec['keyword']:<28} | "
            f"CR: {rec['crPct']:>5.1f}% | CPA: ${rec['actualCpaUsd']:>4.2f} (Mục tiêu ${rec['targetCpaUsd']:>4.2f}) | "
            f"CPT: ${rec['currentCptUsd']:>4.2f} -> ${rec['recommendedCptUsd']:>4.2f} ({rec['action']})"
        )

    print(f"\n[3] TỰ ĐỘNG CÁCH LY TỪ KHÓA PHỦ ĐỊNH CHO CHIẾN DỊCH DISCOVERY ({len(discovery_negatives)} EXACT NEGATIVES):")
    print("  • Đã thêm tự động: " + ", ".join(f'"{n["text"]}"' for n in discovery_negatives))

    output_payload = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "apiVersion": "v5",
        "orgName": "SmartFarm Aurora Vietnam",
        "customProductPages": CUSTOM_PRODUCT_PAGES,
        "campaigns": CAMPAIGN_BLUEPRINTS,
        "bidOptimizationRecommendations": recommendations,
        "discoveryNegativeKeywords": discovery_negatives,
    }
    output_path = SUITE_DIR / "asa-optimization-plan.json"
    output_path.write_text(json.dumps(output_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    print("────────────────────────────────────────────────────────────────────────────────")
    print(f"✓ Đã xuất kế hoạch tối ưu chiến dịch ASA API v5 tại: {output_path}")


if __name__ == "__main__":
    main()
