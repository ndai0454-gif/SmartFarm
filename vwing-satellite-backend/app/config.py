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


def _read_dotenv_fallback() -> dict[str, str]:
    from pathlib import Path
    out: dict[str, str] = {}
    for p in (Path(__file__).resolve().parents[1] / ".env", Path(__file__).resolve().parents[2] / ".env"):
        if p.is_file():
            try:
                for line in p.read_text(encoding="utf-8").splitlines():
                    s = line.strip()
                    if not s or s.startswith("#") or "=" not in s:
                        continue
                    k, v = s.split("=", 1)
                    out.setdefault(k.strip(), v.strip().strip('"').strip("'"))
            except Exception:
                pass
    return out


def load() -> Settings:
    dotenv_vals = _read_dotenv_fallback() if "VWING_EE_MODE" not in os.environ else {}
    raw_mode = os.environ.get("VWING_EE_MODE") or dotenv_vals.get("VWING_EE_MODE") or ("live" if dotenv_vals.get("EE_PROJECT") else "mock")
    mode = raw_mode.strip().lower()
    if mode not in ("live", "mock"):
        raise RuntimeError("VWING_EE_MODE phải là 'live' hoặc 'mock'.")
    token = os.environ.get("VWING_SAT_TOKEN") or dotenv_vals.get("VWING_SAT_TOKEN") or ""
    if len(token) < 12:
        raise RuntimeError("Cần đặt VWING_SAT_TOKEN (tối thiểu 12 ký tự).")
    project = os.environ.get("EE_PROJECT") if "EE_PROJECT" in os.environ else dotenv_vals.get("EE_PROJECT", "")
    if mode == "live" and not project:
        raise RuntimeError("Chế độ live cần EE_PROJECT (Cloud project đã đăng ký Earth Engine).")
    return Settings(
        token=token, mode=mode, ee_project=project,
        max_area_ha=float(os.environ.get("VWING_MAX_AREA_HA", "500")),
        max_composite_days=int(os.environ.get("VWING_MAX_COMPOSITE_DAYS", "93")),
        max_series_days=int(os.environ.get("VWING_MAX_SERIES_DAYS", "400")),
        public_base_url=os.environ.get("VWING_PUBLIC_URL", "http://127.0.0.1:8000").rstrip("/"),
    )
