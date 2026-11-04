"""Cấu hình đọc từ biến môi trường. Không có khóa hay thông tin xác thực nào nằm trong mã nguồn."""
import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    token: str
    mode: str                 # "live" = Earth Engine thật, "mock" = dữ liệu mô phỏng (không cần tài khoản)
    ee_project: str
    max_area_ha: float
    max_composite_days: int
    max_series_days: int
    public_base_url: str      # địa chỉ mà ứng dụng truy cập được (dùng cho ảnh mô phỏng)


def load() -> Settings:
    mode = os.environ.get("VWING_EE_MODE", "mock").strip().lower()
    if mode not in ("live", "mock"):
        raise RuntimeError("VWING_EE_MODE phải là 'live' hoặc 'mock'.")
    token = os.environ.get("VWING_SAT_TOKEN", "")
    if len(token) < 12:
        raise RuntimeError("Cần đặt VWING_SAT_TOKEN (tối thiểu 12 ký tự).")
    project = os.environ.get("EE_PROJECT", "")
    if mode == "live" and not project:
        raise RuntimeError("Chế độ live cần EE_PROJECT (Cloud project đã đăng ký Earth Engine).")
    return Settings(
        token=token, mode=mode, ee_project=project,
        max_area_ha=float(os.environ.get("VWING_MAX_AREA_HA", "500")),
        max_composite_days=int(os.environ.get("VWING_MAX_COMPOSITE_DAYS", "93")),
        max_series_days=int(os.environ.get("VWING_MAX_SERIES_DAYS", "400")),
        public_base_url=os.environ.get("VWING_PUBLIC_URL", "http://127.0.0.1:8000").rstrip("/"),
    )
