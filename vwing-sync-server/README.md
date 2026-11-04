# Máy chủ VWing

Máy chủ nhỏ (Node, không cần cài thư viện): tài khoản, phân quyền theo vườn, mời thành viên, nhật ký kiểm toán, đồng bộ nhiều thiết bị, xoá lan truyền và trang truy xuất công khai cho người mua.

## Chạy

```
set VWING_TOKEN=chuoi-bi-mat-it-nhat-12-ky-tu
node server.js
```

Cổng mặc định 8790 (`PORT`), dữ liệu ở `./data` (`DATA_DIR`). `PUBLIC_BASE_URL` (tuỳ chọn) là địa chỉ công khai ghi vào mã QR cho người mua, ví dụ `https://vwing.example.com`.
`VWING_TOKEN` là mã **quản trị hệ thống** (R7), không phải mã dùng hằng ngày: dùng để cấp chủ cho vườn cũ (`PUT /v1/admin/gardens/:key/owner {phone}`) và hỗ trợ.

## Tài khoản & phân quyền (kiểm tra ở máy chủ)

- Đăng ký / đăng nhập bằng **số điện thoại + mật khẩu** (scrypt; khoá 15 phút sau 5 lần sai). Chưa có OTP qua SMS vì cần nhà cung cấp SMS.
- `Membership(user, garden, role, scope, expiry)` lưu ở `data/<vườn>/members.json`. Vai trò: `owner`, `manager`, `worker`, `pilot`, `agronomist`. `scope.zones` giới hạn theo khu; `expiresAt` tự thu hồi khi hết hạn. Quyền được kiểm tra lại ở **mỗi** yêu cầu nên thu hồi có hiệu lực ngay.
- Ma trận quyền: `rbac.js` (theo `ui-prototype/docs/VWING_TECHNOLOGY_PLAN_AND_ROLES.md` mục C3). Ví dụ nhân viên không thấy doanh thu, không sửa thông tin vườn, không xoá, không đóng vấn đề; phi công chỉ thấy/sửa khu được cấp.
- Người ngoài vườn nhận 404 cho mọi mã vườn (không lộ vườn nào tồn tại).
- Thay đổi vượt quyền **không làm hỏng cả lượt đồng bộ**: phần hợp lệ được nhận, phần vi phạm trả về trong `rejected` và ghi vào nhật ký kiểm toán.

## API chính

| | |
|---|---|
| `POST /v1/auth/register` `login` `logout`, `GET /v1/me` | tài khoản, danh sách vườn và vai trò |
| `POST /v1/gardens {key,name}` | tạo vườn (người tạo là chủ) |
| `DELETE /v1/gardens/:key` + `X-Confirm: <key>` | chủ xoá vườn: dữ liệu, ảnh, thành viên, lời mời |
| `GET/PUT /v1/gardens/:key/snapshot` | đồng bộ (`If-Match: rev`); GET đã lọc theo vai trò và khu |
| `GET/POST /v1/gardens/:key/invites`, `DELETE …/invites/:id` | mời bằng mã/QR (10 ký tự, dùng một lần, hạn 7 ngày, có thể khoá theo SĐT) |
| `POST /v1/invites/accept {code}` | tham gia vườn |
| `GET /v1/gardens/:key/members`, `PATCH/DELETE …/members/:userId` | xem, đổi quyền, thu hồi (quản lý chỉ động tới cấp thấp hơn) |
| `GET /v1/gardens/:key/audit` | nhật ký kiểm toán (chỉ chủ) |
| `GET/PUT /v1/gardens/:key/public` | chủ chọn trường công khai |
| `GET /p/:key/:treeId`, `GET /v1/public/:key/trees/:treeId` | trang/JSON công khai cho người mua, không cần đăng nhập |

## Xoá lan truyền

Bản chụp mang theo `tombstones` (loại, ID, thời điểm, người xoá). Quy tắc (cùng mã ở app: `Cascade.kt`): xoá cây → xoá ghi chép/vấn đề/công việc/số đo chỉ gắn cây đó, phần còn lại chỉ bỏ liên kết (tăng phiên bản để không thành xung đột); xoá vấn đề → xoá công việc của nó; xoá khu → xoá cây trong khu. "Vắng mặt" không bao giờ được coi là xoá, và tombstone chặn thiết bị cũ đưa bản ghi đã xoá trở lại. Ảnh không còn được tham chiếu bị xoá khỏi máy chủ.

## Trang công khai (R6)

Mặc định **tắt**. Chủ bật và chọn từng trường (danh sách trắng trong `public.js`): thông tin vườn, mã/khu/giống/năm trồng/tình trạng/vị trí làm tròn, lịch sử chăm sóc, vật tư, thu hoạch, ghi chú, ảnh, lần kiểm tra gần nhất. Tên người ghi, vấn đề, công việc, doanh thu, nhật ký sửa đổi không bao giờ hiện. Trang có CSP chặt, `noindex`, giới hạn 60 yêu cầu/phút/IP; tắt là biến mất ngay.

## OTP qua SMS (tuỳ chọn)
Không đặt `OTP_PROVIDER` thì tắt (đăng ký bằng SĐT + mật khẩu). Đặt để bật: đăng ký bắt buộc có mã 6 số, thêm đăng nhập bằng mã SMS và quên mật khẩu (đặt lại xong mọi phiên cũ bị đăng xuất).
- `OTP_PROVIDER=dev`: không gửi SMS thật, ghi mã vào nhật ký máy chủ (dùng thử nội bộ/kiểm thử; không bao giờ trả mã qua HTTP).
- `OTP_PROVIDER=twilio` + `TWILIO_SID`, `TWILIO_TOKEN`, `TWILIO_FROM` (hoặc `TWILIO_MESSAGING_SERVICE`): gửi SMS thật. **Chưa kiểm chứng với tài khoản Twilio thật.**
- `OTP_PROVIDER=webhook` + `OTP_WEBHOOK_URL` (+ `OTP_WEBHOOK_TOKEN`): máy chủ POST `{phone, text}` để nối với nhà cung cấp SMS trong nước.
Mã hết hạn 5 phút, dùng một lần, tối đa 5 lần thử, gửi lại sau 60 giây, tối đa 5 mã/giờ/số và 20 mã/giờ/IP; số chưa đăng ký không bị lộ.

## Chuyển quyền chủ, đơn việc mở
- `POST /v1/gardens/:key/transfer {userId, password}`: chỉ chủ, nhập lại mật khẩu, người nhận là thành viên đang hoạt động; chủ cũ thành quản lý; ghi kiểm toán.
- Đơn việc mở (`open: true`, chưa giao ai): nhân viên/phi công tự nhận, trả lại, hoàn tất việc của mình. Hai người nhận cùng lúc thì người đồng bộ trước được giữ, người sau bị từ chối. Không sửa được nội dung việc.

## Giới hạn hiện tại

- HTTP không mã hoá: app chỉ cho dùng `http://` trong mạng nội bộ; ra Internet cần `https://` (reverse proxy/tunnel).
- OTP SMS thật cần cấu hình nhà cung cấp (xem trên); sửa một mục lẻ trong bản ghi vẫn theo phiên bản cả bản ghi.
- Ảnh tải về theo tên tệp (UUID), chưa lọc theo khu.
- Dữ liệu lưu dạng tệp JSON, phù hợp một vườn/nhóm nhỏ; nhiều vườn lớn nên chuyển sang CSDL.
- Nhân viên sửa trái phép trên app bị từ chối ở máy chủ nhưng bản ghi cục bộ của họ vẫn ở trạng thái "chưa đồng bộ" (mỗi lượt đồng bộ lại thêm một dòng kiểm toán).

## Kiểm thử

`node --test test.js` (14 kiểm thử, thêm chuyển quyền chủ, đơn việc mở, chia sẻ vị trí) và `node --test test-otp.js` (5 kiểm thử OTP). Đã gồm: đăng nhập, RBAC, phạm vi khu, hết hạn, lời mời, thu hồi, xoá lan truyền, dọn ảnh, trang công khai). Ứng dụng khách được kiểm thử với máy chủ thật trong `VWingSyncTest`, `VWingAccountTest`, `CascadeTest` (E:\Android).
