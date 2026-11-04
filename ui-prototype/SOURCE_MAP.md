# Nguồn tham chiếu giao diện

| Thành phần | Tệp hoặc bằng chứng trong project | Cách sử dụng |
|---|---|---|
| Phiên bản / nền tảng | `app/build.gradle`: com.utc.ag, versionName 4.20.0; assets/index.android.bundle là Hermes | Nhận diện bản Android giải biên dịch, không coi là source React Native có thể build lại |
| Màu xanh | `app/src/main/res/values/colors.xml`: calendar_view_dotColor #32b768, LIGHT_GREEN #46ca7b | Accent xanh và trạng thái chọn |
| Icon tab | `app/src/main/config.xxhdpi.apk/res/drawable-xxhdpi-v4/tab_{home,land,device,flight,me}_{normal,selected}.png` | Sao chép nguyên tài nguyên vào assets |
| Nền thời tiết | `src_assets_img_home_weather_default_bg.png` trong cùng thư mục drawable | Nền khối thời tiết |
| Avatar đội bay | `src_assets_img_team_default_avatar.png` | Đầu trang đội bay |
| Thửa đất | Bundle có agworkflow.land.landDetail, landManagementMap, measure, tag, flightRecordsList; tài nguyên land_card_address/date/edit/share | Màn thửa đất và truy cập nhật ký |
| Thiết bị | Bundle có agworkflow.manage.deviceList, drone.changeDroneName, drone.dronePartsSN | Danh sách và chi tiết thiết bị; không thực hiện liên kết SN thật |
| Đội bay | Bundle có agworkflow.manage.team, TeamManagement, AG_manage_team_member_* | Thành viên và phân công |
| Công việc | Bundle có agworkflow.job, job.new, job.weather, AG_manage_work_task_add_* | Biểu mẫu công việc mẫu và thời tiết |
| Nhật ký | Bundle có agworkflow.workrecord, agworkflow.job.FlightRecordDetail, FlightRecordFilter* | Tra cứu, lọc và màn chi tiết |
| Phát lại | `app/src/main/com.utc.ag.apk/res/layout/flight_record_replay_card.xml` và `flight_record_replay_view.xml`; chuỗi route_replay_work_duration/area/count | Bảng tối, thời gian, diện tích, số lượt bay và tiến độ |

Tên trong bundle được tìm từ chuỗi nhị phân; đây là bằng chứng về chức năng, không đủ suy ra toàn bộ bố cục. Prototype không gọi các URL tìm thấy trong bundle, không sử dụng khóa dịch vụ, không đăng nhập hoặc gửi dữ liệu ra ngoài.
