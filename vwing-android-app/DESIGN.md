# DESIGN.md — VWing Design System (Taste Skill v2.1 × Impeccable v4.8)

## 1. Design Direction & Archetype
- **Mode:** `Operate` (Impeccable) + `App Screen — Framed Sheet & Data Terminal` (Taste Skill v2.1).
- **The Move:**
  - **Framed Sheet Shell (Tablet):** Sidebar (`236dp`) và Top Bar nằm trên cùng một nền thực vật dịu (`#F4F6F5`), vùng nội dung chính đặt trên mặt phẳng nét mảnh (`1dp` `#DCE4E0`), không đổ bóng trang trí ở trạng thái nghỉ.
  - **Colour Budget (≤ 5%):** Màu sắc chỉ đặt trên dữ liệu, trạng thái sinh trưởng của cây (`Ổn định`, `Theo dõi`, `Cần kiểm tra`) và tối đa 1 nút hành động chính trên mỗi màn hình.
  - **Signature Element:** *Dải Địa Tầng & Quang Phổ Thực Địa (Orchard Canopy & Telemetry Strip)* — hiển thị trực tiếp tỷ lệ phủ tán OBIA, sức khỏe cây và số đo tầng rễ ngay trên màn hình đầu tiên.

## 2. Typography & Numeric Discipline
- **Font Family:**
  - Nội dung & Điều khiển: `Be Vietnam Pro` (`VFont`) — dấu tiếng Việt tách bạch, dễ đọc ngoài nắng.
  - Số liệu & Tiêu đề chính: `Plus Jakarta Sans` (`VDisplayFont`) với `fontFeatureSettings = "tnum"` (Tabular Figures) bắt buộc trên mọi con số, bảng giá và chỉ số GIS.
- **Type Scale (Fixed `sp`):**
  - Page Title (`h1`): `22sp` SemiBold/Bold (Tablet) · `18sp` Bold (Phone).
  - KPI Value: `26sp` Bold (`tnum`), đơn vị đi kèm `13sp` màu xám trầm (`#52665B`).
  - Card Title: `16sp` SemiBold (Sentence case — cấm dùng chữ in hoa toàn bộ `UPPERCASE` làm eyebrow/kicker).
  - Body / Table Cells: `14sp` Regular/Medium, chiều cao dòng `20sp`.
  - Labels / Meta / Axis Ticks: `12sp` Medium, độ tương phản `≥ 4.8:1`.

## 3. Color Tokens
### Native Android (Highland Botanical Daylight — `VC`)
- `--ground` (`VC.bg`): `#F4F6F5` (Nền khung tổng thể dịu mắt ngoài nắng)
- `--surface` (`VC.card`): `#FFFFFF` (Mặt phẳng thẻ dữ liệu)
- `--raised` (`VC.elevated`): `#ECF1EE` (Nền phụ / ô chọn thụ động)
- `--active`: `#E3EBE6` (Viên thuốc chọn mục trên Sidebar — trung tính theo chuẩn Taste Skill §3.2)
- `--border` (`VC.border`): `#D5E0DA` (Viền 1dp sắc nét, không dùng drop-shadow trên thẻ tĩnh)
- `--rule` (`VC.line`): `#E8EFEA` (Đường kẻ chia cột KPI và chia hàng bảng 1dp)
- `--text` (`VC.text`): `#0D1F16` (Mực chính, tương phản 15.8:1)
- `--muted` (`VC.text2`): `#344B3F` (Mực phụ, tương phản 8.9:1)
- `--faint` (`VC.muted`): `#52665B` (Nhãn meta, tương phản 5.6:1 ≥ 4.5:1)
- `--primary` (`VC.pine`): `#0F291E` (Nút hành động chính — đen rừng thẫm)
- `--accent` (`VC.lime`): `#14633A` (Điểm nhấn dữ liệu nông học & trạng thái chọn)
- `--positive` (`VC.mint`): `#1E6B3B` · Nền pill `#DCFCE7` · Chữ `#064E3B`
- `--warning` (`VC.amber`): `#92400E` · Nền pill `#FEF3C7` · Chữ `#78350F`
- `--negative` (`VC.red`): `#B42318` · Nền pill `#FEE2E2` · Chữ `#7F1D1D`

### WebGL & Dark Telemetry (`ui-prototype` — `Data Terminal`)
- `--ground`: `#111417` · `--surface`: `#161A1E` · `--raised`: `#1E2328`
- `--border`: `#262C33` · `--rule`: `#1D2228`
- `--text`: `#F3F5F7` · `--muted`: `#9BA3AE` · `--accent`: `#38BDF8` / `#3FC07A`

## 4. Craft Floor & Anti-Slop Rules (Enforced)
1. **KPIs là chữ số, không phải ô màu lòe loẹt:** Gom các chỉ số KPI vào một dải khung chia vách `1dp` (`TasteKpiStrip`), nhãn xám phía trên, số `tnum` căn thẳng hàng phía dưới, không đặt icon trong vòng tròn màu trên mỗi KPI.
2. **Không dùng cả Border lẫn Shadow trên thẻ tĩnh:** Thẻ dữ liệu (`VCard`) dùng viền `1dp` sắc nét trên nền phẳng, bán kính bo góc chuẩn kiến trúc `14dp` (thẻ) và `8dp` (nút/ô nhập).
3. **Không dùng Emoji làm hệ thống biểu tượng điều hướng:** Mọi thanh điều hướng, nút chuyển chế độ, tiêu đề bước đều dùng biểu tượng vector nét mảnh đồng nhất.
4. **Không dùng Eyebrow chữ in hoa (`UPPERCASE`):** Mọi tiêu đề thẻ dùng chữ thường đầu câu (`Sentence case`), phân cấp bằng cỡ chữ và độ đậm.
5. **Bảng số liệu chuẩn xác:** Không kẻ sọc ngựa vằn (`zebra striping`), tiêu đề cột `12sp` chữ thường màu xám, toàn bộ cột số căn phải (`text-align: right`) với chữ số đẳng rộng (`tabular-nums`).
