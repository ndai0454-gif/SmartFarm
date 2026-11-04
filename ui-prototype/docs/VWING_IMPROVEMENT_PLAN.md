# Đề xuất cải tiến VWING từ nghiên cứu thị trường và bộ link GIS/drone/AI

Ngày: 12/10/2026. Trạng thái: đề xuất sản phẩm và tiêu chí nghiệm thu, chưa triển khai.

## 1. Cơ sở và phạm vi

- Slide người dùng: `E:/MARKET RESEARCH_CP2_Group 23_VWING.pdf`, 19 trang; ưu tiên định hướng kết luận trang 16–18: bộ nhớ số của vườn + lịch sử từng cây + AI hỗ trợ quyết định, chủ vườn xác nhận. Khách hàng đầu tiên: vườn cây lâu năm giá trị cao 1–5 ha, đặc biệt sầu riêng.
- Báo cáo Facebook: `C:/Users/ndai0/.codex/visualizations/2026/10/12/01a12f30-a012-7de3-9233-fe733f0dbc24/phan-tich-facebook-gis-drone.md`. Số mục tham chiếu bên dưới theo 42 URL duy nhất trong báo cáo. Video chỉ được phân tích qua caption và khung hình quan sát; không xem các quảng cáo là kết quả kiểm chứng.
- Kiểm tra mã nguồn prototype: `index.html`, `app.js`, `README.md`, `SOURCE_MAP.md` trong `E:/New folder/SmartFarm/ui-prototype`. Đây là kiểm tra tĩnh, chưa chạy lại ứng dụng hoặc kiểm thử ngoài vườn. README còn mô tả bản SmartFarm cũ; mã hiện tại đã mang tên VWing và có màn hình AI. Dùng mã hiện tại làm cơ sở nhận xét hành vi.

Mục tiêu cải tiến: **Mỗi quan sát phải gắn được với cây/khu vực, thời điểm, nguồn bằng chứng, công việc xử lý và kết quả kiểm tra lại.** GIS/drone mở rộng khả năng thu thập và xem dữ liệu; lịch sử và quy trình xử lý tạo giá trị hằng ngày.

## 2. Khoảng cách với prototype hiện có

| Đã có trong mã | Khoảng cách cần xử lý |
|---|---|
| 24 cây mẫu, 4 khu, trạng thái và ghi chú | Chưa có vị trí địa lý và hồ sơ quan sát theo thời gian đầy đủ |
| Màn hình bản đồ | Đang xếp cây bằng các nhóm/lưới HTML, chưa phải bản đồ GIS |
| Nhật ký và biểu mẫu | Ghi vào `STATE.logs`; chưa có cơ chế lưu bền vững hoặc đồng bộ trong mã đã đọc |
| Cảnh báo | Phần lớn dựa trên trạng thái cây; chưa có hồ sơ vấn đề, phân công và xác nhận kết quả đầy đủ |
| Trợ lý AI | `askAIQuick` chọn câu trả lời theo từ khóa, nhiều số liệu/mã cây cố định; chưa truy vấn hồ sơ thực |
| Nhập giọng nói | Handler hiển thị thông báo rồi gửi câu hỏi mẫu; chưa nhận dạng lời nói |
| Ước tính sản lượng/doanh thu | Phép tính từ số trái, khối lượng trung bình và giá; cần thể hiện rõ giả định và phân biệt với thực thu |
| Thiết lập vườn | Nút “Lưu dữ liệu và tạo bản đồ” hiện chuyển màn hình; cần lưu thật các thông tin người dùng nhập |

Các nhận xét chỉ áp dụng cho prototype đã đọc; không kết luận mọi phiên bản VWING đều thiếu các chức năng này.

## 3. Các cải tiến ưu tiên

### P0-01. Lưu dữ liệu bền vững và ghi nhận khi mất mạng

**Hành vi:** ghi nhật ký, ảnh và vấn đề vào bộ nhớ lưu trữ trên thiết bị trước; có trạng thái đã lưu tại máy/chờ đồng bộ/đã đồng bộ/lỗi. Khi có dịch vụ máy chủ, đồng bộ theo ID thao tác để tránh lặp. Cho phép sao lưu và xuất dữ liệu.

**Vì sao:** bộ nhớ số chỉ có giá trị nếu ghi chép không biến mất khi tải lại. Phù hợp rào cản kết nối và nhập liệu trong slide trang 3/12.

**Nghiệm thu:** đóng/mở app vẫn thấy bản ghi; mất mạng vẫn thêm được bản ghi và ảnh; gửi lại một thao tác không tạo hai bản ghi; hai người sửa cùng hồ sơ phải có cách giải quyết xung đột, không âm thầm ghi đè. Lưu trên trình duyệt chưa thay thế sao lưu hoặc đồng bộ.

### P0-02. Hồ sơ cây có lịch sử và bằng chứng

**Hành vi:** mỗi cây có ID bền vững, mã dễ đọc/QR, giống và năm trồng nếu biết, khu vực, vị trí, người xác nhận. Timeline gồm quan sát, ảnh, chăm sóc, vấn đề, kết quả tái kiểm tra và thu hoạch. Có bộ lọc theo thời gian/loại sự kiện và so sánh ảnh trước–sau.

Cho phép ghi theo một cây, nhiều cây hoặc khu vực. Bản ghi theo khu lưu phạm vi áp dụng tại thời điểm ghi; không tự chuyển lịch sử sang những cây mới được thêm vào khu sau này. Chỉnh sửa có dấu vết; đổi mã hoặc vị trí không làm mất liên kết lịch sử. Cây thay thế được tạo ID mới và liên kết cây cũ, không kế thừa nhầm hồ sơ.

**Liên hệ:** slide trang 15–17; báo cáo Facebook mục 24/26/29.

**Nghiệm thu:** mở A-04 thấy đúng ảnh, nhật ký và công việc của cây; đổi tên vẫn quét QR ra đúng hồ sơ; một hoạt động cho nhiều cây không bị tính chi phí lặp; ảnh lấy ngày chụp/ngày tải lên riêng nếu có thông tin.

### P0-03. Quy trình vấn đề từ phát hiện đến xác nhận kết quả

**Hành vi:** tạo hồ sơ vấn đề gồm biểu hiện quan sát, cây/phạm vi, mức ưu tiên, bằng chứng, người phụ trách, hạn kiểm tra và các bước đã làm. Trạng thái đề xuất: mới ghi nhận → chờ xác minh → đang xử lý → chờ tái kiểm tra → đã xác nhận kết quả; cho phép mở lại.

Tách rõ ba thông tin: tình trạng cây, trạng thái công việc và chất lượng dữ liệu. Thêm “chưa đánh giá”; không xem cây chưa được quan sát là “ổn định”. Hoàn tất việc bón/phun không tự xác nhận cây đã hồi phục.

**Liên hệ:** slide trang 10/13/15; báo cáo mục 28/35/36 nhấn mạnh kiểm chứng thực địa.

**Nghiệm thu:** sau khi nhân viên hoàn tất công việc, vấn đề vẫn chờ kiểm tra lại; có bằng chứng/người xác nhận khi đóng; cây có nhiều vấn đề chỉ được tổng hợp trạng thái theo quy tắc rõ; công việc quá hạn xuất hiện trên danh sách hôm nay.

### P0-04. AI trả lời từ hồ sơ và mở được nguồn

**Hành vi:** bắt đầu với câu hỏi có thể trả lời bằng dữ liệu có cấu trúc: cây nào quá hạn kiểm tra, A-04 đã được chăm sóc gì, khu nào có nhiều vấn đề được ghi nhận, ghi chép nào còn thiếu kết quả. AI tổng hợp kết quả truy vấn và dẫn tới từng bản ghi.

Mỗi câu trả lời gồm: phạm vi/thời điểm dữ liệu, kết luận có căn cứ, nguồn mở được, thông tin thiếu và bước kiểm tra tiếp theo. Nếu chưa có dữ liệu, trả lời chưa đủ dữ liệu; không tự suy bệnh hoặc tự chuyển sang trả lời doanh thu cho câu hỏi không hiểu. Số liệu tổng hợp do truy vấn/phép tính xác định, không để mô hình tự đếm.

Gợi ý công việc được tạo thành bản nháp để người dùng xem và xác nhận. Phân biệt ghi chép của chủ vườn, kết quả đo, nội dung tài liệu và suy luận AI. Truy vấn chỉ được lấy dữ liệu vườn mà tài khoản có quyền truy cập. Không hiện phần trăm tin cậy tùy ý.

**Liên hệ:** slide trang 10/16/17; báo cáo mục 26/27.

**Nghiệm thu:** thêm/sửa bản ghi làm câu trả lời cập nhật; hỏi cây không tồn tại không sinh hồ sơ giả; câu hỏi ngoài phạm vi được giải thích hoặc hỏi lại; dẫn nguồn đến đúng sự kiện; gợi ý AI chưa được duyệt không thay đổi trạng thái cây.

### P0-05. Giảm nhập liệu và ưu tiên công việc hằng ngày

**Hành vi:** màn hình đầu tập trung vào việc đến hạn, cây chờ xác minh, vấn đề đang mở và ghi chép chưa đồng bộ. Có nút quét QR/ghi nhanh; biểu mẫu thay đổi theo hoạt động. Kiểm tra lá không bắt buộc nhập vật tư/liều lượng; tưới ghi lượng/thời gian khi biết; bón/phun mới cần vật tư phù hợp.

Cho phép tạo hồ sơ tối thiểu cho cây có vấn đề trước, rồi bổ sung toàn vườn dần. Nhập hàng loạt từ bảng tính với bước xem trước và kiểm tra trùng. Nhận dạng giọng nói chỉ bật khi thực sự hoạt động và luôn cho người dùng sửa văn bản trước khi lưu.

**Nghiệm thu:** ghi một quan sát ngắn mà không phải điền vật tư giả; tìm được cây bằng QR hoặc mã khi không định vị được; trạng thái chưa đánh giá và quá hạn phân biệt rõ; các số đếm trên trang được tính từ dữ liệu thay vì gắn cố định.

### P1-01. Bản đồ địa lý có thể sửa và nhiều lớp

**Hành vi:** ranh vườn, khu, cây, đường đi, nguồn nước và ghi chú vật cản gắn với tọa độ. Nhập KML/GeoJSON theo thứ tự ưu tiên; các định dạng khác chỉ thêm khi cần. Có xem trước, kiểm tra hệ tọa độ, diện tích, cây trùng và vị trí ngoài ranh trước khi chấp nhận. Ghi nguồn, thời điểm và chất lượng vị trí.

Giữ chế độ sơ đồ đơn giản cho người chưa có tọa độ. Phân biệt vị trí ước lượng với vị trí đã xác minh; QR/ID là cách nhận diện chính khi GPS không phân biệt chắc chắn các cây gần nhau. Không tự coi độ chính xác GPS trên điện thoại là sai số đo độc lập đã kiểm chứng.

Lớp hiển thị: cây chờ kiểm tra, công việc quá hạn, tuổi bản ghi, ảnh nền theo ngày. Màu kèm chữ/biểu tượng. Chạm một điểm mở hồ sơ cây và lịch sử tương ứng.

**Liên hệ:** báo cáo mục 5/10/24/29/42; slide trang 9/18.

**Nghiệm thu:** tọa độ nhập xuất không bị đảo kinh/vĩ; chỉnh vị trí không đổi ID cây; ranh giao nhau hoặc dữ liệu sai định dạng có thông báo; cây mới xuất hiện trong khu tùy ý, không chỉ bốn khu cố định.

### P1-02. Ảnh theo thời gian và tiếp nhận sản phẩm drone

**Hành vi:** trước tiên nhận ảnh chụp thực địa và ảnh trực giao đã xử lý, có ngày chụp, nguồn, cảm biến và thông tin định vị. Xem hai thời điểm, gắn quan sát vào cây/khu và chuyển sang công việc kiểm tra. Chấp nhận ảnh drone từ đối tác để chủ vườn không phải mua thiết bị mới ngay.

AI đếm cây/trích hàng trồng là bước tiếp theo, chỉ tạo đối tượng ứng viên. Người dùng xác nhận ghép với ID cây hiện có; không tự tạo lại toàn bộ cây mỗi lần tải ảnh. Lưu phiên bản mô hình, nguồn ảnh và các trường hợp sửa tay.

**Liên hệ:** báo cáo mục 17/28/37/38/40.

**Nghiệm thu:** hai lần nhập cùng bộ ảnh không nhân đôi cây; dịch vị trí ảnh không âm thầm ghép sai lịch sử; báo cáo sai đếm/bỏ sót trên ảnh vườn chưa dùng để huấn luyện; thay đổi màu giữa ảnh không được tự gắn nhãn bệnh hoặc thiếu dinh dưỡng.

### P1-03. Ghi nhận nước, đất và thời tiết có nguồn

**Hành vi:** nhật ký mưa, tưới, độ ẩm đo và mẫu xét nghiệm đất theo cây/khu, có đơn vị, thời điểm, nguồn và tình trạng hiệu chuẩn nếu là cảm biến. Phân biệt dự báo với lượng mưa đã đo; phân biệt đo dinh dưỡng đất với giá trị nội suy. Cho người dùng ghi nơi từng đọng nước/khô hạn lên bản đồ.

Trước mắt hỗ trợ xem lịch sử và nhắc kiểm tra. Khi đủ dữ liệu cây, đất và khí tượng mới thử mô hình cân bằng nước vùng rễ; chưa tự động ra lượng tưới chỉ từ ảnh hoặc poster.

**Liên hệ:** báo cáo mục 7/8/13/31/35. Cơ sở kỹ thuật: [FAO-56 chương 8](https://www.fao.org/4/X0490E/x0490e0e.htm).

**Nghiệm thu:** số đo cũ được thể hiện tuổi dữ liệu; mm mưa và lít tưới không bị cộng trực tiếp; dữ liệu thiếu không biến thành 0; xem được mẫu đất và vị trí lấy mẫu làm nguồn.

### P2-01. Chỉ số phổ và mô hình chuyên sâu có điều kiện dữ liệu

**Hành vi:** NDVI chỉ được tính/nhập khi xác nhận dữ liệu có Red và NIR phù hợp, tiền xử lý và thời điểm. Lưu nguồn, độ phân giải và chất lượng; cho thấy biến thiên theo thời gian và phạm vi không gian mà dữ liệu hỗ trợ.

USGS nêu NDVI dùng Red và NIR; các băng Red/NIR Landsat 8/9 có độ phân giải 30 m. Vì vậy không lấy một pixel phủ nhiều đối tượng để gán kết luận chắc chắn cho từng cây. Nhận định về khả năng tách cây là suy luận từ độ phân giải và kích thước đối tượng, cần kiểm tra tại vườn. [USGS NDVI](https://www.usgs.gov/landsat-missions/landsat-normalized-difference-vegetation-index), [USGS băng phổ](https://www.usgs.gov/faqs/what-are-band-designations-landsat-satellites).

LiDAR, hyperspectral và mô hình phân đoạn lớn chỉ thử khi có câu hỏi cụ thể mà dữ liệu hiện tại chưa giải quyết, ví dụ chiều cao tán hoặc địa hình dưới tán. Cần chứng minh giá trị bổ sung so với công cụ đơn giản và chi phí dữ liệu/tính toán.

**Liên hệ:** báo cáo mục 2/4/21/30/33/39. **Nghiệm thu:** ảnh RGB thông thường không được gắn thành NDVI; kết quả không tự chẩn đoán bệnh hay kích hoạt xử lý; bản đồ ghi đúng độ phân giải, ngày và vùng thiếu dữ liệu.

### P1-04. Sản lượng, doanh thu và giá có giả định rõ

**Hành vi:** tách số trái quan sát, sản lượng ước tính, sản lượng thu thực tế và tiền đã bán. Cho chỉnh giả định khối lượng/tỷ lệ thương phẩm; xem kịch bản thấp/cơ sở/cao từ đầu vào cụ thể. Ghi nguồn giá, khu vực, loại hàng và ngày cập nhật. Doanh thu không được trình bày như lợi nhuận nếu chưa trừ chi phí.

**Nghiệm thu:** số trái bằng 0 lưu được; không mặc định cây mới có 50 trái khi chưa đếm; tổng được tính lại khi dữ liệu thay đổi; không dùng số cố định trong AI khác số tính trên dashboard; chênh lệch dự kiến–thực tế được truy về giả định.

## 4. Luồng dùng mẫu để nghiệm thu toàn bộ

Ví dụ minh họa, không phải khuyến nghị kỹ thuật canh tác:

1. Chủ vườn tạo khu A và cây A-04, in/gắn QR; có thể bổ sung tọa độ sau.
2. Nhân viên quét QR khi không có mạng, ghi “lá vàng”, thêm ảnh và thời điểm quan sát. App lưu trên thiết bị.
3. Chủ vườn thấy vấn đề mới khi dữ liệu đồng bộ, giao người kiểm tra và đặt hạn.
4. Nhân viên bổ sung kết quả quan sát; nếu thực hiện chăm sóc, ghi riêng hoạt động, phạm vi và vật tư thực tế.
5. Công việc hoàn tất nhưng vấn đề vẫn chờ tái kiểm tra. Kết quả được ghi bằng ảnh/ghi chú và người xác nhận.
6. Chủ vườn hỏi AI “A-04 từng bị vậy chưa?”. Câu trả lời mở được các sự kiện liên quan và nói rõ phần chưa có dữ liệu.
7. Khi nhập ảnh drone mới, đối tượng cây được ghép với A-04 sau xác nhận; lịch sử cũ vẫn giữ nguyên.

Giá trị cần đo: thời gian ghi/tìm lịch sử, tỷ lệ hoàn tất kiểm tra lại, lượng ghi chép thực có nguồn và mức sử dụng lặp lại.

## 5. Dữ liệu tối thiểu để hỗ trợ hành vi

Đây là cấu trúc đề xuất, không phải schema đã triển khai.

| Đối tượng | Trường quan trọng | Hành vi được phục vụ |
|---|---|---|
| Vườn/khu | ID, ranh, cây trồng, quyền thành viên | Phạm vi dữ liệu và người thao tác |
| Cây | ID bền vững, mã/QR, khu, vị trí và nguồn | Nhận diện, sửa bản đồ, giữ lịch sử |
| Quan sát | Cây/khu, thời điểm quan sát/ghi, người, biểu hiện, nguồn | Phân biệt dữ liệu hiện trường và suy luận |
| Vấn đề | Quan sát gốc, mức ưu tiên, trạng thái, người phụ trách | Theo dõi từ phát hiện đến kết quả |
| Công việc/chăm sóc | Đối tượng áp dụng, hạn, người, thực hiện, vật tư và đơn vị | Giao việc, nhật ký, chi phí |
| Bằng chứng | Ảnh/tệp, ngày, nguồn, liên kết sự kiện | Mở nguồn và so sánh theo thời gian |
| Kiểm tra lại | Vấn đề, ngày, kết quả, bằng chứng, người xác nhận | Phân biệt làm xong và đạt kết quả |
| Bộ ảnh/lớp bản đồ | Ngày chụp, tọa độ, độ phân giải, cảm biến, chất lượng | Nhập drone/viễn thám đúng phạm vi |
| Câu trả lời AI | Câu hỏi, nguồn, thời điểm, phiên bản, phản hồi người dùng | Truy vết và đánh giá trả lời |

## 6. Thứ tự triển khai và điều kiện chuyển bước

| Đợt | Phạm vi | Điều kiện hoàn tất |
|---|---|---|
| A: MVP dùng ngoài vườn | P0-01/02/03/05; trả lời dữ liệu có cấu trúc thuộc P0-04 | Chạy trọn luồng QR–ghi–giao–tái kiểm tra; đóng/mở không mất dữ liệu; không có số liệu cố định sai |
| B: Bản đồ và AI có nguồn | P1-01; hoàn thiện P0-04; P1-04 | Định vị/nhập ranh và sửa được; AI dẫn đúng sự kiện; giả định sản lượng kiểm tra được |
| C: Thu thập nâng cao | P1-02/03 | Có bộ ảnh/số đo thực, đồng nhất ID cây và kiểm chứng ngoài vườn |
| D: Nghiên cứu chuyên sâu | P2-01 | Chứng minh lợi ích bổ sung, dữ liệu và tài nguyên đáp ứng |

Không gán thời gian/cost chắc chắn khi chưa biết đội ngũ, dịch vụ máy chủ, dữ liệu và phạm vi triển khai. Một tính năng không cần đợi cả bản đồ GIS mới phục vụ được cây bằng QR.

## 7. Pilot và kiểm chứng giá trị

Theo hướng phỏng vấn/pilot trong slide, thử với 3–5 chủ vườn mục tiêu. Đo quy trình cũ trước, dùng thử với các công việc lặp lại, rồi so sánh. Đây là pilot học nhu cầu, không phải mẫu đại diện toàn thị trường.

- Thời gian trung vị để tìm đúng lịch sử một cây và ghi một quan sát.
- Tỷ lệ vấn đề có nguồn, người phụ trách, hạn và kết quả tái kiểm tra.
- Tỷ lệ ghi chép còn nguyên sau khởi động lại/đồng bộ; số bản ghi trùng hoặc xung đột bị mất.
- Tỷ lệ câu trả lời AI đúng, dẫn đúng nguồn và từ chối đúng khi thiếu dữ liệu, trên bộ câu hỏi có đáp án đối chứng.
- Người dùng tiếp tục ghi nhận ngoài buổi hướng dẫn và chủ vườn dùng lịch sử để ra quyết định nào.
- Khả năng trả tiền sau khi đã dùng, thử 2–3 gói; tính cả chi phí hỗ trợ, lưu ảnh, AI và đồng bộ. Chưa suy doanh thu hoặc lợi nhuận từ phản hồi thiện chí.

Mục tiêu số cụ thể phải chốt trước pilot sau khi có đường cơ sở; không báo cáo là hiệu quả đã đạt.

## 8. Điều chỉnh thông điệp sản phẩm

Đề xuất: **“VWING lưu lại câu chuyện của từng cây, giúp bạn biết việc gì đã xảy ra, việc gì cần làm và vì sao.”**

Trong demo, ưu tiên cho người xem trải nghiệm một cây từ lúc ghi nhận vấn đề đến khi xác nhận kết quả, rồi hỏi AI về lịch sử. Các bản đồ drone/NDVI là lớp bằng chứng bổ sung khi có dữ liệu phù hợp. Khi thuyết trình, đồng bộ mô tả trang đầu với hướng kết luận trang 17 và làm rõ biểu đồ khảo sát dùng 69 phản hồi hay 55 mẫu hợp lệ.

## 9. Nguồn tham khảo theo tính năng

- Ranh và tọa độ: [bài ranh thửa](https://www.facebook.com/share/p/1CKT9Z5Zds/), [Google Earth → QGIS](https://www.facebook.com/share/v/1BzmyQzz4W/), [chuyển tọa độ](https://www.facebook.com/share/p/1CooAMttWc/).
- Chuỗi dữ liệu và AI: [workflow nông nghiệp](https://www.facebook.com/share/p/1Hi1s9amW9/), [vai trò người kiểm tra](https://www.facebook.com/share/p/1CWWyVJguv/).
- Ảnh và hàng cây: [GeoAI](https://www.facebook.com/share/p/1E13A4Pf1V/), [3D](https://www.facebook.com/share/r/1bU4qpReay/), [trích hàng trồng](https://www.facebook.com/share/r/1BpnSujmPk/).
- Mưa/đất: [đo mưa](https://www.facebook.com/share/p/1C6jTz8YKt/), [soil fertility](https://www.facebook.com/share/r/18sL2B46Hs/); công thức vận hành cần đối chiếu [FAO](https://www.fao.org/4/X0490E/x0490e0e.htm).
- Phổ và độ phân giải: [USGS NDVI](https://www.usgs.gov/landsat-missions/landsat-normalized-difference-vegetation-index), [USGS các băng Landsat](https://www.usgs.gov/faqs/what-are-band-designations-landsat-satellites).

Các nguồn Facebook gợi ý bài toán và cách thể hiện; tiêu chí nghiệm thu, phân kỳ và hành vi cụ thể trong tài liệu này là đề xuất của báo cáo.
