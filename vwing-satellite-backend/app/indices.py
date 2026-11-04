"""Định nghĩa chỉ số Sentinel-2 và kiểm tra công thức bằng spyndex (bộ công thức awesome-spectral-indices).

Công thức không viết tay ở nhiều nơi: Earth Engine tính bằng eemont (cùng danh mục công thức), còn đây là bản tính
bằng NumPy dùng để kiểm thử và để mô phỏng. Hai bên phải cho cùng một kết quả (xem tests/test_indices.py).
"""
from dataclasses import dataclass

import numpy as np
import spyndex

# Sentinel-2 SR: DN/10000 = phản xạ bề mặt. Chuyển đổi này phải làm TRƯỚC khi tính EVI (có hằng số cộng).
S2_SCALE = 0.0001

# Băng Sentinel-2 → tên băng chuẩn của awesome-spectral-indices
S2_BAND_MAP = {"B2": "B", "B3": "G", "B4": "R", "B8": "N", "B11": "S1"}

# Lớp SCL bị loại: 0 no data, 1 bão hòa/lỗi, 3 bóng mây, 8-9 mây trung bình/cao, 10 mây ti, 11 tuyết/băng.
SCL_MASKED = (0, 1, 3, 8, 9, 10, 11)


@dataclass(frozen=True)
class IndexDef:
    key: str
    title: str
    description: str
    vmin: float
    vmax: float
    palette: tuple
    caution: str


INDICES = {
    "NDVI": IndexDef(
        "NDVI", "NDVI", "Độ xanh của tán lá (NIR − Red) / (NIR + Red).", -0.2, 0.9,
        ("a1784b", "e6c846", "8cc850", "1e8232"),
        "Phản ánh độ xanh/phản xạ của tán, không phải bệnh hay dinh dưỡng. Ảnh 10 m không đủ để kết luận từng cây.",
    ),
    "EVI": IndexDef(
        "EVI", "EVI", "Chỉ số thực vật tăng cường: ít bão hòa hơn NDVI ở tán dày.", -0.2, 1.0,
        ("a1784b", "e6c846", "8cc850", "1e8232"),
        "EVI cần phản xạ đã nhân hệ số 0,0001; giá trị khác thang đo sẽ sai.",
    ),
    "NDMI": IndexDef(
        "NDMI", "NDMI", "Độ ẩm tán lá (NIR − SWIR1) / (NIR + SWIR1).", -0.4, 0.6,
        ("b45f3c", "e6c846", "78c8a0", "1d6fa5"),
        "Phản ánh độ ẩm của tán lá, không phải độ ẩm đất vùng rễ; cần đối chiếu thực địa.",
    ),
}


def compute_numpy(index: str, bands: dict) -> np.ndarray:
    """Tính chỉ số bằng spyndex trên phản xạ (0..1). `bands` dùng khóa B,G,R,N,S1."""
    if index not in INDICES:
        raise KeyError(index)
    params = {k: np.asarray(v, dtype="float64") for k, v in bands.items()}
    if index == "EVI":
        params.update(g=2.5, C1=6.0, C2=7.5, L=1.0)
    return np.asarray(spyndex.computeIndex(index, **params))


def required_bands(index: str) -> list[str]:
    """Băng đầu vào cần cho chỉ số (theo danh mục), bỏ các hằng số."""
    consts = {"g", "C1", "C2", "L", "alpha", "beta", "gamma", "omega", "sla", "slb", "kNN", "kNR", "kNB", "kNL", "kGG", "kGR", "kGB", "kGL", "kBB", "kBR", "kBL", "kRR", "kRB", "kRL", "kLL", "k", "sigma", "p", "c", "lambdaN", "lambdaG", "lambdaR", "lambdaS1", "lambdaS2", "lambdaWV", "fdelta", "cexp", "nexp", "gamma", "PAR", "epsilon"}
    return [b for b in spyndex.indices[index].bands if b not in consts]
