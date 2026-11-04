# Photon riêng cho VWing (tìm địa điểm / tra địa chỉ)

Photon (https://github.com/komoot/photon, Apache-2.0) chạy trên máy tính này với dữ liệu OpenStreetMap Việt Nam (~461.000 địa điểm, ~180 MB).
App không gọi Photon trực tiếp: máy chủ vệ tinh (`app/geocode.py`) gọi `PHOTON_URL`, nhớ kết quả 6 giờ, giới hạn 40 lượt/phút.
Nếu Photon riêng tắt, máy chủ vệ tinh tự dùng máy chủ demo `photon.komoot.io` (đặt `PHOTON_FALLBACK_URL=` rỗng để tắt dự phòng).

## Thư mục `E:\Photon`
- `photon-1.3.0.jar`, `photon_data\` (cơ sở dữ liệu), `vn-dump.jsonl.zst` (bản dữ liệu gốc đã tải), `unzst.py` (giải nén).

## Chạy
- Tự động cùng `Start-Public.ps1`, hoặc riêng: `powershell -File app\Start-Photon.ps1` (cổng 2322, chỉ nghe 127.0.0.1, bộ nhớ 2 GB).
- Kiểm tra: `curl "http://127.0.0.1:2322/api?q=buon+ma+thuot&limit=2"`

## Cài lại / cập nhật dữ liệu (nên làm 1–3 tháng/lần)
1. Dừng Photon. Đổi tên `photon_data` thành `photon_data_old` (KHÔNG giải nén đè lên dữ liệu cũ).
2. Tải bản mới: `https://download1.graphhopper.com/public/asia/vietnam/photon-dump-vietnam-release-<ngày>.jsonl.zst` (xem trang https://download1.graphhopper.com/public/asia/vietnam/), kiểm tra `.md5`.
3. Nhập (mất ~80 giây): `cd E:\Photon` rồi
   `python unzst.py vn-dump.jsonl.zst | java -Xmx6G -jar photon-1.3.0.jar import -import-file - -languages vi,en`
4. Khởi động lại Photon, thử tìm, rồi xoá `photon_data_old`.
Dùng jar Photon khác phiên bản thì bản dữ liệu phải khớp phiên bản (xem docs của Photon).

## Giấy phép và nguồn
Dữ liệu © người đóng góp OpenStreetMap (ODbL); app hiện dòng ghi nguồn dưới kết quả tìm.
