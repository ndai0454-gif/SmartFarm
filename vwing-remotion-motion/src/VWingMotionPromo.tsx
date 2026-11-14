import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Easing,
  Img,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import {
  DigitRoll,
  FlashCut,
  INTRO_DURATION_FRAMES,
  ShotcraftPerimeterBeam,
  ShotcraftRecipeHud,
  ShotcraftScanBracketOverlay,
  ShotcraftStageAtmosphere,
  ShotcraftVoiceWaveformPill,
  VWingBrandLogo,
  VWingSatelliteLogoIntro,
} from './shotcraft/ShotcraftKit';

export interface SubScreenSpec {
  file: string;
  focusX: number;
  focusY: number;
  focusW: number;
  focusH: number;
  actionLabel: string;
  tabletFile?: string;
  cardIndex?: 0 | 1 | 2;
  cardOverride?: {
    title: string;
    body: string;
    metric: string;
  };
  captionOverride?: string;
}

export interface FeatureCardSpec {
  title: string;
  body: string;
  metric: string;
  progress: number;
}

export interface SceneSpec {
  id: string;
  tag: string;
  shotcraftRecipe: string;
  title: string;
  subtitle: string;
  caption: string;
  theme: 'daylight' | 'pricing' | 'aurora';
  motionMode:
    | 'weather_wave'
    | 'pricing_shimmer'
    | 'qr_laser'
    | 'gee_radar'
    | 'ai_waveform'
    | 'aurora_ribbon'
    | 'multi_device';
  startSec: number;
  endSec: number;
  screens: SubScreenSpec[];
  cards: FeatureCardSpec[];
  badge: string;
}

export const VWING_FPS = 24;
export const VWING_INTRO_FRAMES = INTRO_DURATION_FRAMES; // 144f = 6.0s Satellite + Logo Intro
export const VWING_TOTAL = 2181 + VWING_INTRO_FRAMES; // 2325f = 96.88s @ 24fps

export const SCENES: SceneSpec[] = [
  {
    id: 'scene_01_intro',
    tag: 'SHOTCRAFT · SPOTLIGHT-HERO-CARD · KHỞI TẠO & TỔNG QUAN VƯỜN',
    shotcraftRecipe: 'opening/spotlight-hero-card + PageCam 2.5D',
    title: 'VWing SmartFarm — Khởi Tạo SĐT & Tổng Quan Nông Trại Số',
    subtitle: 'Đăng nhập Sổ Vườn theo SĐT • Tổng quan 21/24 cây khỏe • Biểu đồ thời tiết 6 ngày',
    caption:
      'Chào mừng bạn đến với VWing SmartFarm — nền tảng quản trị nông trại thông minh gắn liền với số điện thoại nhà nông, giúp theo dõi sức khỏe từng gốc cây và thời tiết theo thời gian thực.',
    theme: 'daylight',
    motionMode: 'weather_wave',
    startSec: 0.0,
    endSec: 10.22,
    screens: [
      {
        file: 'exports/phone/01_landing_welcome.png',
        tabletFile: 'exports/tablet/01_landing_welcome.png',
        focusX: 20,
        focusY: 430,
        focusW: 1020,
        focusH: 700,
        cardIndex: 0,
        cardOverride: {
          title: '🌱 Chào Mừng Đến Với VWing SmartFarm',
          body: 'Hiểu từng cây, Chăm cả khu vườn • Kết nối bản đồ vườn & lịch sử chăm sóc',
          metric: 'Bắt đầu',
        },
        actionLabel: 'Màn Chào mừng: "Hiểu từng cây. Chăm cả khu vườn" • Bấm [Vào vườn của mình →]',
        captionOverride:
          'Chào mừng bạn đến với VWing SmartFarm — người bạn đồng hành của nhà vườn: hiểu từng cây, chăm cả khu vườn ngay trên điện thoại và máy tính bảng.',
      },
      {
        file: 'exports/phone/02_login_phone_otp.png',
        tabletFile: 'exports/tablet/02_login_phone_otp.png',
        focusX: 30,
        focusY: 950,
        focusW: 1020,
        focusH: 550,
        cardIndex: 0,
        cardOverride: {
          title: '📱 Mở Sổ Vườn Theo Số Điện Thoại',
          body: 'Nhập số điện thoại của bác và bấm [✦ Lấy mã 6 số] để mở ngay sổ vườn',
          metric: 'Mã 6 số',
        },
        actionLabel: 'Vào Sổ Vườn: Nhập số điện thoại của bác • Bấm [✦ Lấy mã 6 số] để mở sổ vườn',
        captionOverride:
          'Đăng nhập Sổ Vườn chỉ bằng số điện thoại của nhà nông và mã xác thực 6 số, tự động đồng bộ toàn bộ hồ sơ cây trồng.',
      },
      {
        file: 'exports/phone/04_overview_basic_daylight.png',
        tabletFile: 'exports/tablet/04_overview_basic_daylight.png',
        focusX: 25,
        focusY: 410,
        focusW: 1030,
        focusH: 620,
        cardIndex: 1,
        cardOverride: {
          title: '🌦️ Cảnh Báo Mưa Rào Đông Chiều (Ẩm 98%)',
          body: 'Vườn Sầu Riêng Bảo Lộc (21/24 cây khỏe) • Khoan phun thuốc & thăm 3 cây',
          metric: 'Ẩm 98%',
        },
        actionLabel: 'Vườn Sầu Riêng Bảo Lộc (21/24 cây khỏe • Đất ẩm 64%): Mưa rào Đông chiều (ẩm 98%)',
        captionOverride:
          'Màn hình Tổng quan Vườn hiển thị 21/24 cây khỏe, cảnh báo Mưa rào Đông chiều (ẩm 98%) và khuyên khoan phun thuốc, ưu tiên thăm 3 cây.',
      },
      {
        file: 'exports/phone/20_weather_today.png',
        tabletFile: 'exports/tablet/20_weather_today.png',
        focusX: 25,
        focusY: 1200,
        focusW: 1030,
        focusH: 620,
        cardIndex: 2,
        cardOverride: {
          title: '🌡️ Biểu Đồ Thời Tiết Nông Vụ 6 Ngày (21°C–29°C)',
          body: 'Dự báo sóng nhiệt 6 ngày tại Xã Đamb’ri, TP. Bảo Lộc, Lâm Đồng',
          metric: '21°C–29°C',
        },
        actionLabel: 'Biểu đồ sóng nhiệt 6 ngày (21°C – 29°C) từ Thứ Bảy đến Thứ Năm tại Bảo Lộc',
        captionOverride:
          'Theo dõi biểu đồ sóng nhiệt 6 ngày liên tiếp từ 21°C đến 29°C theo đúng vị trí Xã Đamb’ri, TP. Bảo Lộc để chủ động lịch phun tưới.',
      },
    ],
    cards: [
      {title: '📱 Mở Sổ Vườn Theo Số Điện Thoại (Màn 01, 02)', body: 'Nhập số điện thoại của bác • Nhận mã 6 số đồng bộ tức thì', metric: 'Mã 6 số', progress: 1.0},
      {title: '🌦️ Cảnh Báo Mưa Rào Đông Chiều (Màn 04)', body: '21/24 cây khỏe • Ẩm 98% — Khoan phun thuốc & thăm 3 cây', metric: 'Ẩm 98%', progress: 0.98},
      {title: '🌡️ Biểu Đồ Thời Tiết Nông Vụ 6 Ngày (Màn 20)', body: 'Theo dõi sóng nhiệt 21°C–29°C tại Xã Đamb’ri, Bảo Lộc', metric: '21°C–29°C', progress: 0.92},
    ],
    badge: '✓ ĐỒNG BỘ SĐT NHÀ NÔNG • 21/24 CÂY KHỎE • BÁO MƯA 98%',
  },
  {
    id: 'scene_02_pricing',
    tag: 'SHOTCRAFT · DECK-DEAL-FLYIN · GÓI BASIC 149K & PRO ACCOUNT 499K',
    shotcraftRecipe: 'ui-entrance/deck-deal-flyin + DigitRoll',
    title: 'Bảng Giá Basic (149K) & Pro Account (499K) • Đồng Bộ SĐT',
    subtitle: 'Basic: 149.000đ/tháng (1.499K/năm) • Pro: 499.000đ/tháng (4.999K/năm · Tiết kiệm 989K)',
    caption:
      'Ứng dụng mang đến hai gói tài khoản linh hoạt: Gói Basic Account chỉ 149.000 đồng một tháng hoặc 1.499.000 đồng một năm; và gói Pro Account 499.000 đồng một tháng, hoặc 4.999.000 đồng một năm tiết kiệm ngay 989.000 đồng.',
    theme: 'pricing',
    motionMode: 'pricing_shimmer',
    startSec: 10.22,
    endSec: 26.65,
    screens: [
      {
        file: 'screens/02_pricing_banner_month.png',
        tabletFile: 'exports/tablet/29_dialog_plan_feature_flags.png',
        focusX: 45,
        focusY: 605,
        focusW: 990,
        focusH: 640,
        cardIndex: 0,
        cardOverride: {
          title: '🌱 Basic Account (149.000 đ/tháng · 1.499K/năm)',
          body: 'Tiết kiệm 289.000 đ/năm • Quản lý 100 cây QR, Thời tiết 6 ngày & Hỏi AI',
          metric: '149K',
        },
        actionLabel: 'Bảng so sánh: Basic (149.000 đ | 1.499.000 đ) & Pro (499.000 đ | 4.999.000 đ)',
        captionOverride:
          'Bảng giá minh bạch theo Tháng hoặc Năm (-17%): Basic Account 149.000 đ/tháng (1.499.000 đ/năm) và Pro Account 499.000 đ/tháng (4.999.000 đ/năm).',
      },
      {
        file: 'screens/04_pricing_cards_basic_pro.png',
        tabletFile: 'exports/tablet/29_dialog_plan_feature_flags.png',
        focusX: 45,
        focusY: 735,
        focusW: 990,
        focusH: 660,
        cardIndex: 1,
        cardOverride: {
          title: '✦ Pro Account (499.000 đ/tháng · 4.999K/năm)',
          body: 'Ưu đãi năm tiết kiệm 989.000 đ • Mở khóa Aurora, Vệ tinh GEE & Drone VRA',
          metric: '499K',
        },
        actionLabel: 'Thẻ Pro Account (4.999.000 đ/năm • Tiết kiệm 989K): Aurora, GEE 7 chỉ số & Drone',
        captionOverride:
          'Gói Pro Account (499.000 đ/tháng hoặc 4.999.000 đ/năm tiết kiệm 989.000 đ) mở khóa giao diện Aurora, Vệ tinh GEE 7 chỉ số và Bay Drone VRA.',
      },
      {
        file: 'exports/phone/29_dialog_plan_feature_flags.png',
        tabletFile: 'exports/tablet/29_dialog_plan_feature_flags.png',
        focusX: 35,
        focusY: 740,
        focusW: 1010,
        focusH: 640,
        cardIndex: 1,
        cardOverride: {
          title: '🎛️ Hộp Thoại Gói Tài Khoản & Bật/Tắt Tính Năng',
          body: 'Chuyển đổi linh hoạt giữa gói Month (Tháng) và Year (Năm -17%) ngay trong App',
          metric: '-17%',
        },
        actionLabel: 'Hộp thoại Gói Tài Khoản: So sánh trực tiếp Basic (149K) & Pro Account (499K)',
        captionOverride:
          'Hộp thoại Quản lý Gói Tài Khoản cho phép nhà vườn kiểm tra gói đang dùng và chuyển đổi tức thì giữa chu kỳ Tháng hoặc Năm (-17%).',
      },
      {
        file: 'exports/phone/30_dialog_farmer_phone_sync.png',
        tabletFile: 'exports/tablet/30_dialog_farmer_phone_sync.png',
        focusX: 35,
        focusY: 560,
        focusW: 1010,
        focusH: 640,
        cardIndex: 2,
        cardOverride: {
          title: '☁️ Đồng Bộ Đa Tài Khoản Theo Số Điện Thoại',
          body: 'Chuyển nhanh giữa 0909 999 999 (Pro Account) và 0901 111 111 (Basic Account)',
          metric: '2 SĐT',
        },
        actionLabel: 'Các số điện thoại đã lưu: 0909 999 999 (Pro Account) & 0901 111 111 (Basic Account)',
        captionOverride:
          'Mọi gói tài khoản (Pro Account và Basic Account) được lưu và chuyển đổi tức thì chỉ bằng một chạm vào số điện thoại đã lưu.',
      },
    ],
    cards: [
      {title: '🌱 Basic Account (149.000 đ/tháng)', body: 'Gói năm 1.499.000 đ/năm (tiết kiệm 289K) • Tối đa 100 cây QR & Thời tiết 6 ngày', metric: '149K', progress: 0.72},
      {title: '✦ Pro Account (499.000 đ/tháng)', body: 'Gói năm 4.999.000 đ/năm (tiết kiệm 989K) • Vệ tinh GEE 7 chỉ số, Drone & AI 24/7', metric: '499K', progress: 1.0},
      {title: '☁️ Đồng Bộ Đa Tài Khoản Theo SĐT (Màn 30)', body: 'Lưu sẵn 0909 999 999 (Pro) & 0901 111 111 (Basic) • Chuyển đổi 1 chạm', metric: '2 SĐT', progress: 1.0},
    ],
    badge: '★ TIẾT KIỆM 989.000 Đ/NĂM KHI ĐĂNG KÝ GÓI PRO ACCOUNT THEO NĂM',
  },
  {
    id: 'scene_03_qr',
    tag: 'SHOTCRAFT · SCAN-BRACKET-SWEEP · QR CÂY, SÂU BỆNH & NHẬT KÝ',
    shotcraftRecipe: 'effects/scan-bracket-sweep + row-embed',
    title: 'Quét Mã QR Cây, Hồ Sơ Gốc Cây A-01, Sâu Bệnh & Nhật Ký',
    subtitle: 'Giải mã 3 chế độ QR • Hồ sơ Cây A-01 (54 trái) • 3 cây cần kiểm tra • 25/25 ghi chép',
    caption:
      'Khi quét mã QR gắn trên từng cây, bạn nhận ngay ba lớp thông tin: Hồ sơ sáu nhóm dữ liệu nội bộ, tự động gán tọa độ lưới khoảng cách tám mét, và trang truy xuất nguồn gốc nông sản xác thực cho người mua.',
    theme: 'daylight',
    motionMode: 'qr_laser',
    startSec: 26.65,
    endSec: 38.99,
    screens: [
      {
        file: 'exports/phone/06_qr_scanner_3modes.png',
        tabletFile: 'exports/tablet/06_qr_scanner_3modes.png',
        focusX: 35,
        focusY: 740,
        focusW: 1010,
        focusH: 760,
        cardIndex: 0,
        cardOverride: {
          title: '📷 Khung Quét Mã QR Gốc Cây & Xuất Tờ QR',
          body: 'Laser AI Scan tự động giải mã 3 chế độ QR • Nút [Xuất tờ QR cả vườn] in hàng loạt',
          metric: 'QR Scan',
        },
        actionLabel: 'Khung camera Quét mã QR gốc cây • [Xuất tờ QR cả vườn] • Laser AI Scan 3 chế độ',
        captionOverride:
          'Đưa mã QR dán trên thân cây vào khung camera Laser AI Scan để mở ngay hồ sơ cây trồng, hoặc bấm Xuất tờ QR cả vườn để in.',
      },
      {
        file: 'screens/06_qr_content_explained.png',
        tabletFile: 'exports/tablet/06_qr_scanner_3modes.png',
        focusX: 35,
        focusY: 1255,
        focusW: 1010,
        focusH: 680,
        cardIndex: 0,
        cardOverride: {
          title: '🔍 3 Chế Độ Dữ Liệu Trả Về Khi Quét QR',
          body: '1. QR Nội bộ (vwing://tree) • 2. QR Lưới 8m×8m (64m²) • 3. Thẻ truy xuất người mua',
          metric: '3 Chế độ',
        },
        actionLabel: 'Nội dung nhận được khi quét QR: 3 chế độ dữ liệu trả về tự động (QR Nội bộ vwing://tree)',
        captionOverride:
          'Khi quét mã QR gắn trên thân cây, hệ thống tự động giải mã 3 lớp dữ liệu: Hồ sơ 6 nhóm nội bộ, QR lưới 8m×8m và Truy xuất người mua.',
      },
      {
        file: 'exports/phone/07_tree_profile_detail.png',
        tabletFile: 'exports/tablet/07_tree_profile_detail.png',
        focusX: 25,
        focusY: 1265,
        focusW: 1030,
        focusH: 645,
        cardIndex: 1,
        cardOverride: {
          title: '🌳 Hồ Sơ Chi Tiết Cây A-01 (54 Trái • Ổn Định)',
          body: 'Giống Sầu riêng • Kiểm tra 6 ngày trước • Tọa độ GPS: 11.549065, 107.807074',
          metric: '54 Trái',
        },
        actionLabel: 'Thông tin Cây A-01: Số trái ghi nhận 54 trái • Tình trạng Ổn định • GPS 11.549065, 107.807074',
        captionOverride:
          'Hồ sơ chi tiết từng gốc cây (Cây A-01) ghi nhận 54 trái, tình trạng Ổn định, kiểm tra 6 ngày trước kèm tọa độ GPS thực địa.',
      },
      {
        file: 'exports/phone/08_issues_check_list.png',
        tabletFile: 'exports/tablet/08_issues_check_list.png',
        focusX: 25,
        focusY: 735,
        focusW: 1030,
        focusH: 430,
        cardIndex: 1,
        cardOverride: {
          title: '🩺 Theo Dõi Vấn Đề & Cây Cần Thăm (Màn 08)',
          body: 'Cần kiểm tra: 3 cây • Đang theo dõi: 4 cây • Ổn định: 15 cây • Chưa đánh giá: 2 cây',
          metric: '3 Cây',
        },
        actionLabel: 'Thống kê Cây cần thăm: Cần kiểm tra 3 cây • Theo dõi 4 cây • Ổn định 15 cây',
        captionOverride:
          'Bảng theo dõi vấn đề phân loại rõ ràng: 3 cây Cần kiểm tra, 4 cây Đang theo dõi và 15 cây Ổn định để không bỏ sót cây bệnh.',
      },
      {
        file: 'exports/phone/25_records_care_history.png',
        tabletFile: 'exports/tablet/25_records_care_history.png',
        focusX: 25,
        focusY: 1600,
        focusW: 1030,
        focusH: 600,
        cardIndex: 2,
        cardOverride: {
          title: '📓 Lịch Sử Chăm Sóc 25/25 Ghi Chép (Màn 25)',
          body: '04/11/2026 Kiểm tra A-04 (lá vàng) • 03/11/2026 Kiểm tra A-01 (tán lá dày)',
          metric: '25/25',
        },
        actionLabel: 'Lịch sử chăm sóc (25/25 ghi chép): Kiểm tra A-04 (lá vàng) & Kiểm tra A-01 (tán dày)',
        captionOverride:
          'Mọi lần chăm sóc và kiểm tra (như A-04 lá vàng, A-01 phát triển tốt) đều được lưu vào 25/25 ghi chép lịch sử minh bạch.',
      },
    ],
    cards: [
      {title: '🔍 Quét Mã QR & Giải Mã 3 Chế Độ (Màn 06)', body: '1. QR Nội bộ (vwing://tree) • 2. QR Lưới 8m×8m • 3. Thẻ truy xuất người mua', metric: '3 Chế độ', progress: 1.0},
      {title: '🌳 Hồ Sơ Cây A-01 & Cây Cần Thăm (Màn 07, 08)', body: 'Cây A-01: 54 trái (Ổn định) • Toàn vườn: 3 cây Cần kiểm tra, 4 cây Theo dõi', metric: '54 Trái', progress: 0.94},
      {title: '📓 Lịch Sử Chăm Sóc 25/25 Ghi Chép (Màn 25)', body: '04/11 Kiểm tra A-04 (lá vàng) • 03/11 Kiểm tra A-01 (phát triển tốt, tán dày)', metric: '25/25', progress: 1.0},
    ],
    badge: '🔍 GIẢI MÃ 3 CHẾ ĐỘ QR • HỒ SƠ CÂY A-01 (54 TRÁI) • 25/25 NHẬT KÝ',
  },
  {
    id: 'scene_04_map_gee',
    tag: 'SHOTCRAFT · FUI-HUD-MOVES · BẢN ĐỒ GEE, 3D 7 TẦNG & DRONE VRA',
    shotcraftRecipe: 'effects/fui-hud-moves + space-camera-moves',
    title: 'Khoanh Vườn GEE 7 Chỉ Số, Quy Trình 6 Bước 3D & Drone VRA',
    subtitle: 'GEE LIVE: Tán 92,1% (~194 cây) • NDVI 0,64 • OBIA 454 cây • Camera đa phổ Drone VRA',
    caption:
      'Tính năng Bản đồ khoanh vườn kết hợp vệ tinh Google Earth Engine phân tích bảy chỉ số: đếm tự động 194 tán cây, chỉ số sinh trưởng NDVI 0 phẩy 64, cùng bố cục hiển thị thông minh không che khuất tầm nhìn.',
    theme: 'aurora',
    motionMode: 'gee_radar',
    startSec: 38.99,
    endSec: 52.32,
    screens: [
      {
        file: 'screens/09_map_boundary_gee.png',
        tabletFile: 'exports/tablet/05_map_gee_boundary.png',
        focusX: 25,
        focusY: 1475,
        focusW: 1030,
        focusH: 625,
        cardIndex: 0,
        cardOverride: {
          title: '🛰️ Vệ Tinh Google Earth Engine 7 Chỉ Số (GEE LIVE)',
          body: 'Tán 92,1% • ~194 cây • NDVI 0,64 • Cao độ 833m • 4 góc vườn 2,91 ha (29.073 m²)',
          metric: '92,1%',
        },
        actionLabel: 'GEE LIVE: Tán 92,1% (~194 cây) • NDVI 0,64 • Cao độ 833m • 4 góc vườn 2,91 ha',
        captionOverride:
          'Tính năng Khoanh vườn kết hợp vệ tinh Google Earth Engine (GEE LIVE) phân tích tự động: độ phủ tán 92,1% (~194 cây), NDVI 0,64 trên diện tích 2,91 ha.',
      },
      {
        file: 'exports/phone/18_workflow_6steps_3d.png',
        tabletFile: 'exports/tablet/18_workflow_6steps_3d.png',
        focusX: 25,
        focusY: 1200,
        focusW: 1030,
        focusH: 680,
        cardIndex: 1,
        cardOverride: {
          title: '📐 Quy Trình 6 Bước & Phân Tách OBIA (Màn 18)',
          body: 'Vùng vườn 2,91 ha • Phân tách 454 cây (SR-001..SR-454) • 29.510 trái • 5.972,8 triệu đ',
          metric: '454 Cây',
        },
        actionLabel: 'Quy trình 6 bước: Vùng vườn 2,91 ha • OBIA 454 cây • 29.510 trái (64,92 tấn) • 5.972,8 triệu đ',
        captionOverride:
          'Quy trình 6 bước tự động hóa từ khoanh vùng 2,91 ha, phân tách OBIA 454 gốc sầu riêng (SR-001..SR-454) đến dự toán 29.510 trái (5.972,8 triệu đồng).',
      },
      {
        file: 'exports/phone/19_satellite_workspace.png',
        tabletFile: 'exports/tablet/19_satellite_workspace.png',
        focusX: 25,
        focusY: 650,
        focusW: 1030,
        focusH: 600,
        cardIndex: 0,
        cardOverride: {
          title: '🌍 Phân Tích Vườn Từ Ảnh Vệ Tinh 7 Chế Độ (Màn 19)',
          body: 'Tổng hợp chỉ số, lớp phủ, địa hình và ước tính cây theo ranh giới (30 / 60 ngày qua)',
          metric: '7 Chế độ',
        },
        actionLabel: 'Phương pháp phân tích (7 chế độ): Tổng hợp chỉ số, lớp phủ, địa hình & ước tính cây',
        captionOverride:
          'Không gian Ảnh vệ tinh vườn cung cấp 7 chế độ phân tích chuyên sâu về chỉ số sinh trưởng, lớp phủ và địa hình theo chu kỳ 30 hoặc 60 ngày.',
      },
      {
        file: 'exports/phone/21_drone_vra_spray.png',
        tabletFile: 'exports/tablet/21_drone_vra_spray.png',
        focusX: 25,
        focusY: 1170,
        focusW: 1030,
        focusH: 590,
        cardIndex: 2,
        cardOverride: {
          title: '🚁 Camera Đa Phổ Drone & Bản Đồ Phun VRA (Màn 21)',
          body: 'Xử lý 5 kênh phổ Drone (NDVI · GNDVI · NDRE · EVI · VARI), đếm cây & lập bản đồ VRA',
          metric: '5 Phổ VRA',
        },
        actionLabel: 'Hệ thống camera đa phổ Drone (NDVI · GNDVI · NDRE · EVI · VARI) & Bản đồ Kê đơn Phun VRA',
        captionOverride:
          'Tích hợp hệ thống camera đa phổ Drone (NDVI, GNDVI, NDRE, EVI, VARI) giúp đếm cây Stand Count và lập Bản đồ Kê đơn Phun thuốc biến thiên VRA.',
      },
    ],
    cards: [
      {title: '🛰️ Vệ Tinh Google Earth Engine 7 Chỉ Số (Màn 05, 19)', body: 'Tán 92,1% (~194 cây) • NDVI 0,64 • Cao độ 833m • Diện tích 2,91 ha', metric: '92,1%', progress: 0.921},
      {title: '📐 Quy Trình 6 Bước & Phân Tách OBIA (Màn 18)', body: 'Phân tách 454 cây (SR-001..SR-454) • 29.510 trái (64,92 tấn) • 5.972,8 triệu đ', metric: '454 Cây', progress: 0.95},
      {title: '🚁 Camera Đa Phổ Drone & Phun VRA (Màn 21)', body: 'Xử lý 5 phổ NDVI/GNDVI/NDRE/EVI/VARI • Đếm cây & Bản đồ phun thuốc VRA', metric: '5 Phổ VRA', progress: 1.0},
    ],
    badge: '🛰️ GEE LIVE: TÁN 92,1% (~194 CÂY) • NDVI 0,64 • OBIA 454 CÂY & DRONE VRA',
  },
  {
    id: 'scene_05_ai_flags',
    tag: 'SHOTCRAFT · VOICE-WAVEFORM-LIVE · HỎI KỸ SƯ AI (70a–70f & 8 TÍNH NĂNG)',
    shotcraftRecipe: 'interaction/voice-waveform-live + theme-switch-moves',
    title: 'Hỏi Kỹ Sư AI (70a–70f) — Kiểm Thử Trực Tiếp 8 Tính Năng',
    subtitle: '6 nút gợi ý 70c Hub • Bón NPK 850g • Xịt 8 bình 20L • Tưới 80L • 19 sản phẩm • Phiếu #VW-3949',
    caption:
      'Trợ lý Kỹ sư AI hoạt động hai mươi bốn trên bảy giúp chẩn đoán bệnh lá và lập lịch tưới phân; đi kèm AI Hỗ trợ Kỹ thuật tự động đóng gói phiếu liên hệ đội ngũ phát triển.',
    theme: 'aurora',
    motionMode: 'ai_waveform',
    startSec: 52.32,
    endSec: 63.1,
    screens: [
      {
        file: 'screens/10_ai_step0_hub_70c.png',
        tabletFile: 'exports/tablet/13_ai_70c_hub_6buttons.png',
        focusX: 30,
        focusY: 1085,
        focusW: 1020,
        focusH: 625,
        cardIndex: 0,
        cardOverride: {
          title: '🤖 Trung Tâm Hỏi Kỹ Sư AI (Màn 13 · 70c Hub)',
          body: '"Hôm nay vườn của bác cần hỗ trợ gì?" • 6 nút gợi ý thông minh (Bón phân, Xịt thuốc, Tưới...)',
          metric: '6 Gợi ý',
        },
        actionLabel: 'Màn Hỏi Kỹ sư AI (70c Hub): "Hôm nay vườn của bác cần hỗ trợ gì?" & 6 nút gợi ý',
        captionOverride:
          'Tại trung tâm Hỏi Kỹ sư AI (70c), nhà nông chỉ cần chạm vào 1 trong 6 nút gợi ý để nhận ngay phác đồ chuẩn xác cho vườn sầu riêng 24 cây.',
      },
      {
        file: 'screens/10_ai_step1_bon_phan.png',
        tabletFile: 'exports/tablet/15_ai_70e_agri_dark.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 610,
        cardIndex: 0,
        cardOverride: {
          title: '🌱 Test AI [1/6]: Bón Phân Gì & Lưu Lượng?',
          body: 'Bón NPK 12-11-18 (SOP): 850 g/gốc (Tổng 20,4 kg/24 cây) + Hữu cơ 8 kg/gốc • [Tạo việc]',
          metric: '850g/gốc',
        },
        actionLabel: 'Bón NPK 12-11-18 (SOP): 850 g/gốc (Tổng 20,4 kg cho 24 cây) + Hữu cơ 8 kg/gốc [Tạo việc]',
        captionOverride:
          'Khi bấm "Bón phân gì & lưu lượng bao nhiêu?", AI tính ngay: NPK 12-11-18 liều 850 g/gốc (tổng 20,4 kg cho 24 cây) kèm nút Tạo việc.',
      },
      {
        file: 'screens/10_ai_step2_xit_thuoc.png',
        tabletFile: 'exports/tablet/15_ai_70e_agri_dark.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 610,
        cardIndex: 0,
        cardOverride: {
          title: '🧪 Test AI [2/6]: Xịt Thuốc Gì & Pha Bình 20L?',
          body: 'Phun 6,0 Lít/cây -> Tổng 144 Lít (8 bình 20L) • Agri-Fos 400 (50ml) + Ridomil Gold (50g)',
          metric: '8 Bình 20L',
        },
        actionLabel: 'Phun 6,0 Lít/cây -> Tổng 144 Lít (8 bình 20L) • Agri-Fos 400 (50ml/20L) + Ridomil Gold',
        captionOverride:
          'Khi hỏi "Xịt thuốc gì & pha bao nhiêu ml/bình 20L?", AI kê đơn: phun 6,0 Lít/cây (tổng 144 Lít = 8 bình 20L) với Agri-Fos 400 và Ridomil Gold.',
      },
      {
        file: 'screens/10_ai_step3_tuoi_nuoc.png',
        tabletFile: 'exports/tablet/15_ai_70e_agri_dark.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 480,
        cardIndex: 0,
        cardOverride: {
          title: '💧 Test AI [3/6]: Tưới Bao Nhiêu Lít/Gốc?',
          body: 'Tưới 80 Lít/gốc/lần -> Tổng 1,92 m³ toàn vườn (24 cây) • Định mức FAO-56 (Ẩm rễ 62%)',
          metric: '80 L/gốc',
        },
        actionLabel: 'Tưới 80 Lít/gốc/lần -> Tổng 1,92 m³ toàn vườn (24 cây) • Mở béc bù áp 60–80 phút (FAO-56)',
        captionOverride:
          'Khi hỏi "Tưới bao nhiêu Lít/gốc?", AI tính theo chuẩn FAO-56: tưới 80 Lít/gốc/lần (tổng 1,92 m³ cho 24 cây), mở béc bù áp 60–80 phút.',
      },
      {
        file: 'screens/10_ai_step4_san_pham.png',
        tabletFile: 'exports/tablet/17_ai_product_catalog.png',
        focusX: 30,
        focusY: 1310,
        focusW: 1020,
        focusH: 605,
        cardIndex: 1,
        cardOverride: {
          title: '🛒 Test AI [4/6]: Danh Mục 19 Sản Phẩm Phân Thuốc',
          body: 'Nhóm Phân bón NPK & Hữu cơ (YaraMila, Đầu Trâu) • Trừ nấm (Agri-Fos 400) • Trừ sâu rầy',
          metric: '19 Sản phẩm',
        },
        actionLabel: 'Danh mục 19 sản phẩm Phân bón & Thuốc BVTV chính hãng cho Vườn Sầu Riêng Bảo Lộc',
        captionOverride:
          'Khi hỏi "Giới thiệu tất cả sản phẩm phân bón & thuốc BVTV?", AI liệt kê đầy đủ 19 sản phẩm chính hãng chia theo 3 nhóm chuyên biệt.',
      },
      {
        file: 'screens/10_ai_step5_cay_van_de.png',
        tabletFile: 'exports/tablet/08_issues_check_list.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 610,
        cardIndex: 1,
        cardOverride: {
          title: '🩺 Test AI [5/6]: Cây Nào Đang Có Vấn Đề?',
          body: 'Có 3 cây Cần kiểm tra (A-04, B-03) và 4 cây Đang theo dõi (A-03) • Bấm [Mở hồ sơ A-03]',
          metric: '3 Cây bệnh',
        },
        actionLabel: 'Có 3 cây Cần kiểm tra (A-04, B-03) và 4 cây Đang theo dõi • Thẻ [Hồ sơ Cây A-03 | Mở hồ sơ]',
        captionOverride:
          'Khi hỏi "Cây nào đang có vấn đề?", AI tổng hợp ngay 3 cây Cần kiểm tra (A-04, B-03) và 4 cây Đang theo dõi kèm nút Mở hồ sơ Cây A-03.',
      },
      {
        file: 'screens/10_ai_step6_san_luong.png',
        tabletFile: 'exports/tablet/10_yield_revenue_forecast.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 410,
        cardIndex: 1,
        cardOverride: {
          title: '💰 Test AI [6/6]: Dự Kiến Sản Lượng & Doanh Thu?',
          body: 'Sản lượng ~2.759 kg (2,76 tấn) • Doanh thu ~171.045.600 đ (1.254 trái × 2,2 kg × 62K)',
          metric: '171,05 Tr',
        },
        actionLabel: 'Sản lượng ~2.759 kg (2,76 tấn) · Doanh thu ~171.045.600 đ (1.254 trái × 2,20 kg/trái)',
        captionOverride:
          'Khi hỏi "Dự kiến sản lượng & doanh thu?", AI tính từ 1.254 trái × 2,20 kg/trái đạt 2.759 kg (2,76 tấn), tương đương doanh thu 171.045.600 đồng.',
      },
      {
        file: 'screens/10_ai_step7_ho_tro_kt.png',
        tabletFile: 'exports/tablet/16_ai_70f_tech_support.png',
        focusX: 30,
        focusY: 1230,
        focusW: 1020,
        focusH: 615,
        cardIndex: 2,
        cardOverride: {
          title: '🛠️ AI Hỗ Trợ Kỹ Thuật & Liên Hệ Dev (Màn 16 · 70f)',
          body: 'Đã tiếp nhận thông tin (Mã phiếu #VW-3949) • Bấm [Gửi phiếu] gửi thẳng đội ngũ Dev',
          metric: '#VW-3949',
        },
        actionLabel: 'Đã tiếp nhận thông tin cho Đội ngũ Phát triển VWing (Mã phiếu #VW-3949) • [Gửi phiếu]',
        captionOverride:
          'Chuyển sang tab AI Hỗ trợ Kỹ thuật (70f): thay thế hoàn toàn SĐT và Email, tự động đóng gói Phiếu Liên Hệ #VW-3949 gửi thẳng đội ngũ Phát triển.',
      },
    ],
    cards: [
      {title: '🌱 Phác Đồ Bón Phân, Xịt Thuốc & Tưới (Màn 13–15)', body: 'Bón NPK 850g/gốc (20,4kg) • Phun 8 bình 20L • Tưới 80 Lít/gốc (1,92 m³)', metric: '6 Gợi ý', progress: 1.0},
      {title: '🛒 19 Sản Phẩm, Cây Bệnh & Sản Lượng (Màn 17)', body: '19 sản phẩm BVTV • 3 cây Cần kiểm tra (A-04) • Sản lượng 2,76 tấn (171,05 Tr)', metric: '19 Sản phẩm', progress: 1.0},
      {title: '🛠️ AI Hỗ Trợ Kỹ Thuật & Liên Hệ Dev (Màn 16 · 70f)', body: 'Thay thế hoàn toàn SĐT & Email • Tự động đóng gói Phiếu #VW-3949 [Gửi phiếu]', metric: '#VW-3949', progress: 1.0},
    ],
    badge: '⚡ KIỂM THỬ TRỰC TIẾP 8 TÍNH NĂNG AI NÔNG NGHIỆP & HỖ TRỢ KỸ THUẬT (#VW-3949)',
  },
  {
    id: 'scene_06_pro_aurora',
    tag: 'SHOTCRAFT · AURORA-BLOOM-BG-FLIP · DỰ BÁO BỆNH, THU HOẠCH & PRO AURORA',
    shotcraftRecipe: 'effects/aurora-bloom-bg-flip + spotlight-sweep',
    title: 'Dự Báo Sâu Bệnh 3D, Sản Lượng, Giá Thị Trường & Pro Aurora',
    subtitle: 'Mô hình 3D 7 Tầng (OA = 89,2%) • Sản lượng 2,76 tấn (171 Tr) • Giá thị trường 88.400 đ/kg',
    caption:
      'Chỉ với một chạm chuyển sang Pro Account, giao diện Aurora Liquid-Glass hiện đại lập tức kích hoạt, mở khóa mô hình 3D dự báo sâu bệnh 7 ngày, dự toán doanh thu và bảng giá thị trường.',
    theme: 'aurora',
    motionMode: 'aurora_ribbon',
    startSec: 63.1,
    endSec: 75.88,
    screens: [
      {
        file: 'exports/phone/24_disease_forecast.png',
        tabletFile: 'exports/tablet/24_disease_forecast.png',
        focusX: 25,
        focusY: 1495,
        focusW: 1030,
        focusH: 560,
        cardIndex: 0,
        cardOverride: {
          title: '🦠 Dự Báo Sâu Bệnh & Mô Hình 3D 7 Tầng (Màn 24)',
          body: 'Lưu vực vườn 2,91 ha (Chu vi 188m) • Hiệu chuẩn AI DeepLabV3+ (OA = 89,2%)',
          metric: 'OA 89,2%',
        },
        actionLabel: 'Mô hình 3D & lưu vực vườn (2,91 ha): 1. Mô hình 3D (7 Tầng) • DeepLabV3+ (OA = 89,2%)',
        captionOverride:
          'Màn hình Dự báo sâu bệnh 7 ngày tích hợp Mô hình 3D 7 Tầng lưu vực vườn 2,91 ha, đã hiệu chuẩn bằng AI DeepLabV3+ đạt độ chính xác 89,2%.',
      },
      {
        file: 'exports/phone/10_yield_revenue_forecast.png',
        tabletFile: 'exports/tablet/10_yield_revenue_forecast.png',
        focusX: 25,
        focusY: 880,
        focusW: 1030,
        focusH: 660,
        cardIndex: 1,
        cardOverride: {
          title: '💰 Thu Hoạch & Tiền: Sản Lượng 2.759 kg (Màn 10)',
          body: '1.254 trái (24/24 cây) × 2,2 kg × 100% = 2.759 kg (≈ 2,76 tấn) • 171.045.600 đ',
          metric: '2,76 Tấn',
        },
        actionLabel: '1 · Sản lượng ước tính: 1.254 trái × 2,2 kg = 2.759 kg (≈ 2,76 tấn) • Doanh thu 171.045.600 đ',
        captionOverride:
          'Màn hình Thu hoạch & Tiền tự động nhân 1.254 trái đã đếm với trọng lượng 2,2 kg/trái, cho ra sản lượng 2.759 kg (2,76 tấn) và doanh thu 171.045.600 đồng.',
      },
      {
        file: 'exports/phone/23_market_prices.png',
        tabletFile: 'exports/tablet/23_market_prices.png',
        focusX: 25,
        focusY: 1530,
        focusW: 1030,
        focusH: 570,
        cardIndex: 1,
        cardOverride: {
          title: '📈 Giá Thị Trường: 134 Mức Giá Tham Khảo (Màn 23)',
          body: 'Cà phê nội địa trung bình Việt Nam: 88.400 VND/kg (Giacaphe.com • Có nguồn & ngày giá)',
          metric: '88.400 đ',
        },
        actionLabel: 'Bảng giá tham khảo (134 mức giá): Cà phê nội địa trung bình Việt Nam 88.400 VND/kg',
        captionOverride:
          'Màn hình Giá thị trường cập nhật 134 mức giá nông sản có nguồn và ngày giá rõ ràng (như giá 88.400 VND/kg), giữ nguyên đơn vị gốc tại vườn.',
      },
      {
        file: 'exports/phone/03_overview_pro_aurora.png',
        tabletFile: 'exports/tablet/03_overview_pro_aurora.png',
        focusX: 25,
        focusY: 180,
        focusW: 1030,
        focusH: 760,
        cardIndex: 2,
        cardOverride: {
          title: '✦ Giao Diện Pro Aurora Liquid-Glass (Màn 03 · 499K)',
          body: 'Nền tối sinh thái rừng đêm • Hiển thị rõ nét ngoài nắng • Đồng bộ Điện thoại & Tablet',
          metric: 'Pro 499K',
        },
        actionLabel: 'Giao diện Pro Aurora (✦ Pro · 499K): Vườn Sầu Riêng Bảo Lộc (21/24 cây khỏe • Ẩm 64%)',
        captionOverride:
          'Chỉ với một chạm kích hoạt Pro Account (499K), giao diện Aurora Liquid-Glass sang trọng lập tức đồng bộ trên cả Điện thoại và Máy tính bảng.',
      },
    ],
    cards: [
      {title: '🦠 Dự Báo Sâu Bệnh & Mô Hình 3D 7 Tầng (Màn 24)', body: 'Lưu vực vườn 2,91 ha (Chu vi 188m) • Hiệu chuẩn AI DeepLabV3+ (OA = 89,2%)', metric: 'OA 89,2%', progress: 0.96},
      {title: '💰 Sản Lượng 2,76 Tấn & Giá Thị Trường (Màn 10, 23)', body: '1.254 trái = 2.759 kg (171.045.600 đ) • Bảng giá 134 mức giá (88.400 VND/kg)', metric: '171,05 Tr', progress: 1.0},
      {title: '✦ Giao Diện Pro Aurora Liquid-Glass (Màn 03)', body: 'Nền tối sinh thái rừng đêm • Độ tương phản cao & đồng bộ đa thiết bị', metric: 'Pro 499K', progress: 1.0},
    ],
    badge: '✦ MÔ HÌNH 3D DEEPLABV3+ (89,2%) • DOANH THU 171 TRIỆU • PRO AURORA',
  },
  {
    id: 'scene_07_multiplatform',
    tag: 'SHOTCRAFT · OUTRO-GROUP-PHOTO-LAUNCH · FULL 30 MÀN HÌNH PHONE & TABLET',
    shotcraftRecipe: 'rhythm/quad-split + outro/outro-group-photo-launch',
    title: 'Trình Diễn Đa Nền Tảng: 30 Màn Hình Điện Thoại & Tablet',
    subtitle: 'Bố cục Sidebar 2 cột rộng rãi trên Tablet TB321FU (2200×1600) • Đồng bộ tức thì với Điện thoại (1080×2400)',
    caption:
      'Đặc biệt, VWing SmartFarm tối ưu đa nền tảng: từ điện thoại gọn nhẹ khi đi thăm từng gốc cây, đến giao diện hai cột rộng rãi trên máy tính bảng Tablet, đồng bộ tức thì theo số điện thoại. Hãy trải nghiệm VWing SmartFarm ngay hôm nay!',
    theme: 'aurora',
    motionMode: 'multi_device',
    startSec: 75.88,
    endSec: 90.85,
    screens: [
      {
        file: 'exports/phone/01_landing_welcome.png',
        tabletFile: 'exports/tablet/01_landing_welcome.png',
        focusX: 30,
        focusY: 420,
        focusW: 1020,
        focusH: 630,
        actionLabel: '01/10 · Màn hình Chào mừng (Landing) đồng bộ trên Tablet TB321FU & Điện thoại',
      },
      {
        file: 'exports/phone/03_overview_pro_aurora.png',
        tabletFile: 'exports/tablet/03_overview_pro_aurora.png',
        focusX: 25,
        focusY: 205,
        focusW: 1030,
        focusH: 640,
        actionLabel: '02/10 · Tổng quan Vườn (Pro Aurora): Bố cục 2 cột rộng rãi trên Tablet',
      },
      {
        file: 'exports/phone/05_map_gee_boundary.png',
        tabletFile: 'exports/tablet/05_map_gee_boundary.png',
        focusX: 30,
        focusY: 960,
        focusW: 1020,
        focusH: 630,
        actionLabel: '03/10 · Khoanh vườn & Vệ tinh GEE 2,91 ha trên Tablet & Điện thoại',
      },
      {
        file: 'exports/phone/06_qr_scanner_3modes.png',
        tabletFile: 'exports/tablet/06_qr_scanner_3modes.png',
        focusX: 40,
        focusY: 680,
        focusW: 1000,
        focusH: 620,
        actionLabel: '04/10 · Quét mã QR cây trồng & Giải mã 3 chế độ dữ liệu đa thiết bị',
      },
      {
        file: 'exports/phone/07_tree_profile_detail.png',
        tabletFile: 'exports/tablet/07_tree_profile_detail.png',
        focusX: 30,
        focusY: 620,
        focusW: 1020,
        focusH: 630,
        actionLabel: '05/10 · Hồ sơ chi tiết Cây A-01 (54 trái) đồng bộ tức thì qua đám mây',
      },
      {
        file: 'exports/phone/10_yield_revenue_forecast.png',
        tabletFile: 'exports/tablet/10_yield_revenue_forecast.png',
        focusX: 30,
        focusY: 920,
        focusW: 1020,
        focusH: 630,
        actionLabel: '06/10 · Dự toán Sản lượng 2,76 tấn & Doanh thu 171,05 triệu đồng',
      },
      {
        file: 'exports/phone/13_ai_70c_hub_6buttons.png',
        tabletFile: 'exports/tablet/13_ai_70c_hub_6buttons.png',
        focusX: 40,
        focusY: 1120,
        focusW: 1000,
        focusH: 615,
        actionLabel: '07/10 · Hỏi Kỹ sư AI (70c Hub): 6 nút gợi ý thông minh trên Tablet & Phone',
      },
      {
        file: 'exports/phone/16_ai_70f_tech_support.png',
        tabletFile: 'exports/tablet/16_ai_70f_tech_support.png',
        focusX: 40,
        focusY: 1010,
        focusW: 1000,
        focusH: 615,
        actionLabel: '08/10 · AI Hỗ trợ Kỹ thuật 24/7 & Phiếu #VW-3949 gửi đội ngũ Phát triển',
      },
      {
        file: 'exports/phone/18_workflow_6steps_3d.png',
        tabletFile: 'exports/tablet/18_workflow_6steps_3d.png',
        focusX: 30,
        focusY: 1120,
        focusW: 1020,
        focusH: 630,
        actionLabel: '09/10 · Quy trình 6 Bước & Không gian 3D 7 Tầng (454 cây • 29,95 tấn)',
      },
      {
        file: 'exports/phone/21_drone_vra_spray.png',
        tabletFile: 'exports/tablet/21_drone_vra_spray.png',
        focusX: 30,
        focusY: 1060,
        focusW: 1020,
        focusH: 630,
        actionLabel: '10/10 · Drone Đa phổ & Bản đồ Phun thuốc VRA trên Tablet & Điện thoại',
      },
    ],
    cards: [
      {title: '💻 Trọn Bộ 30 Màn Hình Tablet Wide (2200×1600)', body: 'Thanh điều hướng Sidebar bên trái • Bàn làm việc rộng rãi cho Kỹ sư & Chủ vườn', metric: '30 Màn Tab', progress: 1.0},
      {title: '📱 Trọn Bộ 30 Màn Hình Điện Thoại (1080×2400)', body: 'Thao tác 1 tay quét QR gốc cây • Hỏi 2 AI Chatbot (70a–70f) ngoài thực địa', metric: '30 Màn ĐT', progress: 1.0},
      {title: '☁️ Đồng Bộ Tức Thì 60 Giao Diện Qua SĐT', body: 'Đăng nhập cùng SĐT trên Điện thoại & Tablet • Dữ liệu và Gói Pro/Basic tự đồng bộ', metric: '60/60 Live', progress: 1.0},
    ],
    badge: '⇄ TRỌN BỘ 30 MÀN HÌNH PHONE + 30 MÀN HÌNH TABLET LIVE',
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// SINGLE SOURCE OF TRUTH FOR MOTION WORKBENCH & JIANYING (CAPCUT CN) EXPORT
// (video-shotcraft/references/workbench.md §2 & jianying-export/aifl_promo.py)
// ─────────────────────────────────────────────────────────────────────────────
export const VWING_SHOTS = Object.fromEntries(
  SCENES.map((sc) => [
    sc.id,
    {
      from: Math.round(sc.startSec * VWING_FPS),
      duration: Math.max(1, Math.round((sc.endSec - sc.startSec) * VWING_FPS)),
      label: sc.title,
      recipe: sc.shotcraftRecipe,
    },
  ]),
) as Record<string, {from: number; duration: number; label: string; recipe: string}>;

// Warm/emerald FlashCut transitions straddling every hard scene cut (cut - 5, 10f long)
export const FLASH_CUTS: number[] = [
  VWING_INTRO_FRAMES,
  ...SCENES.slice(1).map((sc) => VWING_INTRO_FRAMES + Math.round(sc.startSec * VWING_FPS)),
];

// Narration captions table (exported for Motion Workbench & JianYing text track)
export const CAPTIONS = [
  {
    id: 'cap_scene_00_satellite_intro',
    from: 4,
    duration: VWING_INTRO_FRAMES - 8,
    text: 'VWing SmartFarm — Định vị vệ tinh từ ngoài không gian thu phóng trực tiếp đến khu vườn sầu riêng đã khoanh vùng tại Di Linh, Lâm Đồng.',
  },
  ...SCENES.map((sc) => ({
    id: `cap_${sc.id}`,
    from: VWING_INTRO_FRAMES + Math.round(sc.startSec * VWING_FPS) + 4,
    duration: Math.max(10, Math.round((sc.endSec - sc.startSec) * VWING_FPS) - 8),
    text: sc.caption,
  })),
];

export const INTRO_SFX: {from: number; src: string; volume: number; note: string}[] = [
  {from: 2, src: 'riser-cine.mp3', volume: 0.44, note: 'VWing Logo Intro & Z5 Vietnam Orbital Lock'},
  {from: 28, src: 'whoosh-big.mp3', volume: 0.46, note: 'Satellite Dive to Z10 Lam Dong Highlands'},
  {from: 56, src: 'whoosh-fast.mp3', volume: 0.42, note: 'Satellite Dive to Z15 Di Linh Valley'},
  {from: 84, src: 'click-camera.mp3', volume: 0.48, note: 'Lock onto Z18 Orchard & Draw Demarcated Polygon'},
  {from: 98, src: 'impact-cine.mp3', volume: 0.48, note: 'Demarcated Farm 1.54 ha + 24 Trees Confirmed'},
  {from: 114, src: 'sparkle.mp3', volume: 0.36, note: 'NDVI 0.78 Emerald Polygon Shimmer'},
];

// Beat-pinned cinematic sound design (video-shotcraft vocabulary:
// transition-soft, whoosh-fast, whoosh-big, sparkle, transition-snap,
// swoosh-quick, keyboard, click-camera, pop, riser-cine, impact-cine)
export const SFX: {from: number; src: string; volume: number; note: string}[] = [
  // Scene 1: Spotlight Hero Card Open (0s - 10.22s -> f0 - f245)
  {from: 4, src: 'transition-soft.mp3', volume: 0.38, note: 'Scene 1 spotlight stage lamp on'},
  {from: 14, src: 'whoosh-big.mp3', volume: 0.42, note: '3D Phone hero card entrance'},
  {from: 28, src: 'sparkle.mp3', volume: 0.30, note: 'Perimeter specular beam lap 1'},
  {from: 122, src: 'swoosh-quick.mp3', volume: 0.36, note: 'Focus view pan to 6-day weather wave'},
  {from: 235, src: 'transition-snap.mp3', volume: 0.42, note: 'Scene 1 lock before cut'},

  // Scene 2: Deck Deal Flyin Pricing (10.22s - 26.65s -> f245 - f640)
  {from: 245, src: 'whoosh-big.mp3', volume: 0.45, note: 'Scene 2 pricing table deal-in'},
  {from: 256, src: 'pop.mp3', volume: 0.36, note: 'Basic Account 149K card deal'},
  {from: 264, src: 'pop.mp3', volume: 0.38, note: 'Pro Account 499K card deal'},
  {from: 272, src: 'sparkle.mp3', volume: 0.32, note: 'Save 989K golden shimmer'},
  {from: 376, src: 'swoosh-quick.mp3', volume: 0.36, note: 'Switch to Yearly billing crop'},
  {from: 508, src: 'click-camera.mp3', volume: 0.42, note: 'Lock onto Pro Account card'},

  // Scene 3: Scan Bracket Sweep QR (26.65s - 38.99s -> f640 - f936)
  {from: 640, src: 'transition-soft.mp3', volume: 0.40, note: 'Scene 3 QR viewfinder open'},
  {from: 650, src: 'click-camera.mp3', volume: 0.48, note: 'Corner L-brackets snap onto QR'},
  {from: 668, src: 'whoosh-fast.mp3', volume: 0.35, note: 'Laser scan pass 1'},
  {from: 738, src: 'pop.mp3', volume: 0.38, note: 'Row-embed QR mode 1'},
  {from: 746, src: 'pop.mp3', volume: 0.35, note: 'Row-embed QR mode 2 (8m x 8m)'},
  {from: 754, src: 'pop.mp3', volume: 0.32, note: 'Row-embed QR mode 3 (Verified)'},
  {from: 838, src: 'transition-snap.mp3', volume: 0.40, note: 'Tree A-03 profile reveal'},

  // Scene 4: FUI HUD Moves GEE Satellite (38.99s - 52.32s -> f936 - f1256)
  {from: 936, src: 'whoosh-big.mp3', volume: 0.45, note: 'Scene 4 GEE satellite orbital zoom'},
  {from: 952, src: 'sparkle.mp3', volume: 0.30, note: '194 tree canopies + NDVI 0.64 lock'},
  {from: 1096, src: 'swoosh-quick.mp3', volume: 0.38, note: 'Switch to Pro Aurora Map HUD'},

  // Scene 5: Voice Waveform Live + Dual AI 70a-70f (52.32s - 63.10s -> f1256 - f1514)
  {from: 1256, src: 'transition-soft.mp3', volume: 0.42, note: 'Scene 5 Dual AI 70a Light Intro'},
  {from: 1268, src: 'sparkle.mp3', volume: 0.34, note: '3D Emerald AI Orb pulse'},
  {from: 1320, src: 'keyboard.mp3', volume: 0.32, note: '70d Agri AI typing NPK 850g prescription'},
  {from: 1385, src: 'whoosh-fast.mp3', volume: 0.42, note: 'Theme flip to 70f Dark Tech Support AI'},
  {from: 1396, src: 'click-camera.mp3', volume: 0.45, note: 'Dev Support Ticket #VW-3949 packaged'},
  {from: 1450, src: 'pop.mp3', volume: 0.38, note: 'Feature Flags 8-module toggle'},

  // Scene 6: Aurora Bloom BG Flip Pro Account (63.10s - 75.88s -> f1514 - f1821)
  {from: 1514, src: 'riser-cine.mp3', volume: 0.42, note: 'Scene 6 Aurora Liquid-Glass build-up'},
  {from: 1538, src: 'impact-cine.mp3', volume: 0.48, note: 'Pro Account 499K Aurora activation'},
  {from: 1554, src: 'sparkle.mp3', volume: 0.35, note: 'Aurora glass specular sweep'},
  {from: 1665, src: 'swoosh-quick.mp3', volume: 0.36, note: '2.76 tons harvest forecast focus'},

  // Scene 7: Multi-Platform Phone + Tablet TB321FU & Outro Group-Photo Sign-off (75.88s - 90.85s -> f1821 - f2181)
  {from: 1821, src: 'whoosh-big.mp3', volume: 0.48, note: 'Scene 7 Dual-Device Tablet TB321FU + Phone enter'},
  {from: 1910, src: 'swoosh-quick.mp3', volume: 0.36, note: 'Multi-platform Pricing sync'},
  {from: 2000, src: 'swoosh-quick.mp3', volume: 0.36, note: 'Multi-platform GEE Map sync'},
  {from: 2085, src: 'riser-cine.mp3', volume: 0.48, note: 'Outro group-photo launch riser'},
  {from: 2120, src: 'impact-cine.mp3', volume: 0.54, note: 'VWing SmartFarm finale stamp'},
  {from: 2138, src: 'sparkle.mp3', volume: 0.35, note: 'Finale golden-emerald sparkle hold'},
];

export const sfxDuration = (s: {from: number; src: string}) =>
  s.src === 'keyboard.mp3' ? 36 : 72;

const InPhoneVectorOverlay: React.FC<{
  mode: SceneSpec['motionMode'];
  scrIdx: number;
  localFrame: number;
  durationInFrames: number;
  focusRect: {x: number; y: number; w: number; h: number};
}> = ({mode, scrIdx, localFrame, durationInFrames, focusRect}) => {
  // Map 1080x2400 focusRect onto exact 9:20 (405x900) phone viewport (scale = 0.375 exact)
  const fx = Math.max(2, (focusRect.x / 1080) * 405);
  const fy = Math.max(2, (focusRect.y / 2400) * 900);
  const fw = Math.min(403 - fx, (focusRect.w / 1080) * 405);
  const fh = Math.min(898 - fy, (focusRect.h / 2400) * 900);
  const pulse = 0.65 + 0.35 * Math.sin(localFrame * 0.22);

  return (
    <svg
      width={405}
      height={900}
      style={{position: 'absolute', top: 0, left: 0, pointerEvents: 'none'}}
    >
      {/* Glowing Target Region Frame on the Phone Screen */}
      <rect
        x={fx}
        y={fy}
        width={fw}
        height={fh}
        rx={14}
        fill="rgba(253, 224, 71, 0.06)"
        stroke={`rgba(253, 224, 71, ${pulse.toFixed(2)})`}
        strokeWidth={2.5}
        strokeDasharray="10 5"
      />

      {/* video-shotcraft ScanBracketSweep L-Corner Viewfinders + Velocity Scan Beam */}
      <ShotcraftScanBracketOverlay
        x={fx}
        y={fy}
        w={fw}
        h={fh}
        frame={localFrame}
        durationInFrames={durationInFrames}
        scanRgb={mode === 'qr_laser' ? '74, 222, 128' : '253, 224, 71'}
      />

      {mode === 'gee_radar' && (
        <>
          {(() => {
            const deg = (localFrame * 4.5) % 360;
            const rad = (deg * Math.PI) / 180;
            const cx = 202.5;
            const cy = 385;
            const r = 92;
            const ex = cx + r * Math.cos(rad);
            const ey = cy + r * Math.sin(rad);
            return (
              <>
                <circle
                  cx={cx}
                  cy={cy}
                  r={r}
                  fill="rgba(74, 222, 128, 0.07)"
                  stroke="#4ADE80"
                  strokeWidth={2}
                />
                <line
                  x1={cx}
                  y1={cy}
                  x2={ex}
                  y2={ey}
                  stroke="#6EE7B7"
                  strokeWidth={2.8}
                  strokeLinecap="round"
                />
                <circle cx={cx} cy={cy} r={4.5} fill="#4ADE80" />
              </>
            );
          })()}
        </>
      )}
    </svg>
  );
};

export interface SceneViewOverrideProps {
  title?: string;
  subtitle?: string;
  badge?: string;
  caption?: string;
  accentColor?: string;
}

export const SceneView: React.FC<{
  scene: SceneSpec;
  sceneIndex: number;
  totalScenes: number;
  hideCaption?: boolean;
  overrides?: SceneViewOverrideProps;
}> = ({scene, sceneIndex, totalScenes, hideCaption = false, overrides}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const durationInFrames = Math.max(1, Math.round((scene.endSec - scene.startSec) * fps));
  const progress = Math.min(1, Math.max(0, frame / durationInFrames));

  const numScreens = scene.screens.length;
  const subPos = progress * numScreens;
  const scrIdx = Math.min(numScreens - 1, Math.floor(subPos));
  const activeScreen = scene.screens[scrIdx];
  const isMultiDevice = scene.motionMode === 'multi_device';

  const effTitle = overrides?.title || scene.title;
  const effSubtitle = overrides?.subtitle || scene.subtitle;
  const effBadge = overrides?.badge || scene.badge;
  const effCaption = overrides?.caption || activeScreen.captionOverride || scene.caption;

  // Absolute frame & beat number (124 BPM = 2.0667 beats/sec)
  const absFrame = Math.round(scene.startSec * fps) + frame;
  const beatNumber = Math.floor((absFrame / fps) * (124 / 60)) + 1;

  const headerSpring = spring({
    frame,
    fps,
    config: {damping: 200, stiffness: 120},
  });

  // Fixed, completely steady device coordinates (exact 9:20 aspect ratio: 405x900 inside 441x936)
  const phoneLeft = 1418;
  const phoneTop = 54;

  // Compute exact anchor on the left edge of the highlighted focus region on the phone screen
  const focusCenterYInPhone = ((activeScreen.focusY + activeScreen.focusH / 2) / 2400) * 900;
  const focusLeftXInPhone = (activeScreen.focusX / 1080) * 405;
  const targetAnchorX = phoneLeft + 18 + Math.max(6, focusLeftXInPhone);
  const targetAnchorY = phoneTop + 18 + focusCenterYInPhone;

  const ripplePhase = (frame % 28) / 28;
  const rippleRadius = 10 + ripplePhase * 28;
  const rippleOpacity = 1 - ripplePhase;

  // Remotion Focus Callout Box dimensions & exact 1:1 corner-to-corner bounding-box transform
  const lensLeft = 975;
  const lensWidth = 400;
  const lensHeaderH = 34;
  const lensViewportH = Math.round(lensWidth * (activeScreen.focusH / activeScreen.focusW));
  const lensTotalH = lensHeaderH + lensViewportH;
  const lensTop = activeScreen.tabletFile
    ? 338
    : Math.max(130, Math.min(460, targetAnchorY - lensTotalH / 2));

  // Corner-to-corner exact mapping: (focusX, focusY) -> (0, 0) and (focusX+focusW, focusY+focusH) -> (lensWidth, lensViewportH)
  const zoomScaleX = lensWidth / activeScreen.focusW;
  const zoomScaleY = lensViewportH / activeScreen.focusH;
  const zoomTx = -activeScreen.focusX * zoomScaleX;
  const zoomTy = -activeScreen.focusY * zoomScaleY;

  const showPricingTable = sceneIndex === 1 || sceneIndex === 5;
  const shimmerOffset = ((frame * 14) % 920) - 120;

  const bgGradient =
    scene.theme === 'daylight'
      ? 'radial-gradient(circle at 80% 20%, rgba(52, 211, 153, 0.22), transparent 48%), linear-gradient(160deg, #0D2E22 0%, #061712 100%)'
      : scene.theme === 'pricing'
        ? 'radial-gradient(circle at 75% 25%, rgba(56, 189, 248, 0.20), transparent 48%), linear-gradient(160deg, #102830 0%, #07131C 100%)'
        : 'radial-gradient(circle at 80% 25%, rgba(74, 222, 128, 0.25), transparent 52%), linear-gradient(160deg, #071C21 0%, #030B10 100%)';

  const leftColWidth = isMultiDevice ? 790 : 885;
  const activeCardIdx = activeScreen.cardIndex ?? Math.min(scene.cards.length - 1, scrIdx);

  return (
    <AbsoluteFill
      style={{
        background: bgGradient,
        fontFamily: '"Segoe UI", Inter, Roboto, sans-serif',
        color: '#FFFFFF',
        overflow: 'hidden',
      }}
    >
      {/* video-shotcraft Volumetric Spotlight Cone, Rising Golden-Emerald Dust & Bokeh */}
      <ShotcraftStageAtmosphere
        frame={absFrame}
        spotTargetX={isMultiDevice ? 1320 : 1520}
        spotTargetY={460}
        accentRgb={scene.theme === 'pricing' ? '56, 189, 248' : '110, 231, 183'}
      />

      {/* Cyber-Botanical Grid Overlay */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)',
          backgroundSize: '72px 72px',
          pointerEvents: 'none',
        }}
      />

      {/* Left Column: Kinetic Typography & Staggered Cards */}
      <div
        style={{
          position: 'absolute',
          left: 60,
          top: 38,
          width: leftColWidth,
          transform: `translateY(${(1 - headerSpring) * 28}px)`,
          opacity: headerSpring,
        }}
      >
        <div style={{display: 'flex', alignItems: 'center', gap: 10}}>
          <VWingBrandLogo size={38} />
          <div
            style={{
              background: 'rgba(34, 110, 78, 0.92)',
              border: '2px solid #6EE7B7',
              borderRadius: 999,
              padding: '5px 15px',
              fontSize: isMultiDevice ? 14 : 14.5,
              fontWeight: 800,
              color: '#D1FAE5',
              letterSpacing: 0.4,
              whiteSpace: 'nowrap',
            }}
          >
            {scene.tag}
          </div>
          <div
            style={{
              background: 'rgba(18, 42, 34, 0.9)',
              border: '1px solid rgba(253, 224, 71, 0.5)',
              borderRadius: 999,
              padding: '5px 14px',
              fontSize: isMultiDevice ? 14 : 14.5,
              fontWeight: 800,
              color: '#FDE047',
              whiteSpace: 'nowrap',
            }}
          >
            SCENE {sceneIndex + 1} / {totalScenes}
          </div>
        </div>

        <h1
          style={{
            margin: '12px 0 6px 0',
            fontSize: isMultiDevice ? 27.5 : 29,
            fontWeight: 800,
            color: '#FFFFFF',
            letterSpacing: -0.5,
            whiteSpace: 'nowrap',
          }}
        >
          {effTitle}
        </h1>
        <p
          style={{
            margin: 0,
            fontSize: isMultiDevice ? 16.5 : 17.5,
            color: '#A7F3D0',
            fontWeight: 500,
            whiteSpace: 'nowrap',
          }}
        >
          {effSubtitle}
        </p>
      </div>

      {/* Pricing Comparison Table with DigitRoll Odometer (Scene 2 & Scene 6) */}
      {showPricingTable && (
        <div
          style={{
            position: 'absolute',
            left: 60,
            top: 196,
            width: leftColWidth,
            height: 186,
            borderRadius: 20,
            background: 'rgba(14, 38, 32, 0.95)',
            border: '2px solid #52D694',
            padding: 12,
            boxSizing: 'border-box',
            overflow: 'hidden',
            boxShadow: '0 16px 40px rgba(0,0,0,0.35)',
          }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1.3fr 1fr 1.1fr',
              background: '#183E32',
              borderRadius: 12,
              padding: '9px 18px',
              fontSize: 17,
              fontWeight: 800,
              color: '#A7F3D0',
            }}
          >
            <span>GÓI TÀI KHOẢN (ITEM)</span>
            <span>MONTH (THÁNG)</span>
            <span style={{color: '#FDE047'}}>YEAR (NĂM -17%) ★</span>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1.3fr 1fr 1.1fr',
              alignItems: 'center',
              background: 'rgba(20, 48, 40, 0.9)',
              borderRadius: 12,
              padding: '9px 18px',
              marginTop: 8,
            }}
          >
            <span style={{fontSize: 23, fontWeight: 800}}>🌱 Basic Account</span>
            <DigitRoll value="149.000 đ" delay={4} fontSize={26} color="#86EFAC" />
            <DigitRoll value="1.499.000 đ" delay={8} fontSize={26} color="#FEF08A" />
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1.3fr 1fr 1.1fr',
              alignItems: 'center',
              background: 'rgba(28, 74, 58, 0.96)',
              border: '2px solid #6EE7B7',
              borderRadius: 12,
              padding: '8px 18px',
              marginTop: 8,
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            <span style={{fontSize: 23, fontWeight: 800, color: '#A7F3D0'}}>✦ Pro Account</span>
            <DigitRoll value="499.000 đ" delay={10} fontSize={26} color="#6EE7B7" />
            <DigitRoll value="4.999.000 đ" delay={14} fontSize={26} color="#FDE047" />
            <div
              style={{
                position: 'absolute',
                top: 0,
                bottom: 0,
                left: shimmerOffset,
                width: 110,
                background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.22), transparent)',
                transform: 'skewX(-20deg)',
              }}
            />
          </div>
        </div>
      )}

      {/* Staggered Feature Cards with Dynamic Per-Screen Wording Synchronization */}
      <div
        style={{
          position: 'absolute',
          left: 60,
          top: showPricingTable ? 398 : 208,
          width: leftColWidth,
          display: 'flex',
          flexDirection: 'column',
          gap: showPricingTable ? 12 : 16,
        }}
      >
        {scene.cards.map((card, idx) => {
          const cue = 4 + idx * 5;
          const cardSpring = spring({
            frame: Math.max(0, frame - cue),
            fps,
            config: {damping: 200, stiffness: 120},
          });
          const barWidth = interpolate(
            frame,
            [6 + idx * 4, 28 + idx * 4],
            [0, card.progress * 100],
            {extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic)},
          );
          const isHighlighted = idx === activeCardIdx;
          const displayTitle = isHighlighted && activeScreen.cardOverride ? activeScreen.cardOverride.title : card.title;
          const displayBody = isHighlighted && activeScreen.cardOverride ? activeScreen.cardOverride.body : card.body;
          const displayMetric = isHighlighted && activeScreen.cardOverride ? activeScreen.cardOverride.metric : card.metric;

          return (
            <div
              key={idx}
              style={{
                background: isHighlighted ? 'rgba(20, 58, 46, 0.95)' : 'rgba(14, 36, 30, 0.86)',
                border: isHighlighted ? '2px solid #6EE7B7' : '1px solid rgba(110, 231, 183, 0.35)',
                borderRadius: 18,
                padding: showPricingTable ? '12px 20px' : '16px 20px',
                transform: `translateY(${(1 - cardSpring) * 24}px)`,
                opacity: cardSpring,
                position: 'relative',
                boxShadow: isHighlighted ? '0 10px 28px rgba(16, 185, 129, 0.22)' : 'none',
              }}
            >
              <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12}}>
                <div>
                  <div style={{fontSize: isMultiDevice ? 21 : 22, fontWeight: 800, color: '#FFFFFF'}}>
                    {displayTitle}
                  </div>
                  <div style={{fontSize: isMultiDevice ? 17 : 17.5, color: '#BAE6FD', marginTop: 4}}>
                    {displayBody}
                  </div>
                </div>
                <div
                  style={{
                    background: 'rgba(30, 88, 66, 0.95)',
                    border: '1.5px solid #6EE7B7',
                    borderRadius: 12,
                    padding: '6px 14px',
                    minWidth: 96,
                    textAlign: 'center',
                    whiteSpace: 'nowrap',
                  }}
                >
                  <DigitRoll
                    value={displayMetric}
                    delay={cue + 2}
                    fontSize={isMultiDevice ? 20 : 21}
                    color="#FDE047"
                  />
                </div>
              </div>

              <div
                style={{
                  marginTop: 10,
                  height: 6,
                  borderRadius: 3,
                  background: 'rgba(255,255,255,0.12)',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${barWidth}%`,
                    height: '100%',
                    background: 'linear-gradient(90deg, #34D399, #38BDF8)',
                    borderRadius: 3,
                  }}
                />
              </div>
            </div>
          );
        })}
      </div>

      {/* Gold Highlight Badge + Optional Live Voice Waveform Pill for Scene 5 */}
      <div
        style={{
          position: 'absolute',
          left: 60,
          top: 782,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
        }}
      >
        <div
          style={{
            background: 'linear-gradient(90deg, #F59E0B, #FBBF24)',
            border: '2px solid #FEF08A',
            borderRadius: 999,
            padding: '9px 22px',
            fontSize: 16.5,
            fontWeight: 800,
            color: '#062016',
            boxShadow: '0 8px 24px rgba(245, 158, 11, 0.35)',
          }}
        >
          {effBadge}
        </div>
        {scene.motionMode === 'ai_waveform' && (
          <ShotcraftVoiceWaveformPill
            frame={frame}
            channelLabel={scrIdx < numScreens - 1 ? '🌱 KỸ SƯ AI (70c/70e)' : '🛠️ HỖ TRỢ KT & DEV (70f)'}
          />
        )}
      </div>

      {isMultiDevice ? (
        /* SCENE 7: MULTI-PLATFORM DUAL-DEVICE SHOWCASE (Wide Tablet TB321FU + Smartphone) */
        <>
          {/* Wide 16:10 Tablet TB321FU Mockup (Steady, Zero Shake) */}
          <div
            style={{
              position: 'absolute',
              left: 885,
              top: 64,
              width: 760,
              height: 492,
              borderRadius: 30,
              background: '#14201C',
              border: '3.5px solid #6EE7B7',
              padding: 14,
              boxSizing: 'border-box',
              boxShadow: '0 28px 65px rgba(0,0,0,0.68), 0 0 38px rgba(74, 222, 128, 0.22)',
            }}
          >
            <div
              style={{
                width: '100%',
                height: '100%',
                borderRadius: 18,
                overflow: 'hidden',
                position: 'relative',
                background: '#05120E',
              }}
            >
              <Img
                src={staticFile(activeScreen.tabletFile || 'screens/tab_05_overview_pro_aurora.png')}
                style={{width: '100%', height: '100%', objectFit: 'cover'}}
              />
              <div
                style={{
                  position: 'absolute',
                  top: 12,
                  right: 14,
                  background: 'rgba(6, 26, 20, 0.92)',
                  border: '1.5px solid #4ADE80',
                  borderRadius: 999,
                  padding: '5px 16px',
                  fontSize: 15,
                  fontWeight: 800,
                  color: '#A7F3D0',
                }}
              >
                💻 TABLET LENOVO TB321FU · 2200×1600 LIVE
              </div>
            </div>
            <ShotcraftPerimeterBeam width={760} height={492} radius={30} frame={frame} />
          </div>

          {/* Overlapping Companion Smartphone Mockup (Steady, Zero Shake) */}
          <div
            style={{
              position: 'absolute',
              left: 1515,
              top: 325,
              width: 340,
              height: 695,
              borderRadius: 38,
              background: '#162420',
              border: '3px solid #FDE047',
              padding: 13,
              boxSizing: 'border-box',
              boxShadow: '0 26px 60px rgba(0,0,0,0.78), 0 0 32px rgba(253, 224, 71, 0.25)',
            }}
          >
            <div
              style={{
                width: '100%',
                height: '100%',
                borderRadius: 26,
                overflow: 'hidden',
                position: 'relative',
                background: '#061510',
              }}
            >
              <Img
                src={staticFile(activeScreen.file)}
                style={{width: '100%', height: '100%', objectFit: 'cover'}}
              />
              <div
                style={{
                  position: 'absolute',
                  top: 10,
                  left: 12,
                  background: 'rgba(6, 26, 20, 0.92)',
                  border: '1.5px solid #FDE047',
                  borderRadius: 999,
                  padding: '4px 12px',
                  fontSize: 13,
                  fontWeight: 800,
                  color: '#FDE047',
                }}
              >
                📱 PHONE · 1080×2400
              </div>
            </div>
          </div>

          {/* Cloud Sync Bridge Pill between Tablet and Phone */}
          <div
            style={{
              position: 'absolute',
              left: 895,
              top: 584,
              width: 590,
              background: 'rgba(10, 36, 28, 0.96)',
              border: '2px solid #6EE7B7',
              borderRadius: 18,
              padding: '14px 22px',
              boxShadow: '0 14px 34px rgba(0,0,0,0.45)',
            }}
          >
            <div style={{fontSize: 18, fontWeight: 800, color: '#6EE7B7'}}>
              ⇄ REALTIME CLOUD SYNC · ĐỒNG BỘ ĐIỆN THOẠI & TABLET TB321FU
            </div>
            <div style={{fontSize: 17, fontWeight: 700, color: '#FFFFFF', marginTop: 6}}>
              {activeScreen.actionLabel}
            </div>
          </div>
        </>
      ) : (
        /* SCENES 1-6: Tablet Wide Companion Window + Precision 1:1 Bounding-Box Remotion Focus Lens + Smartphone */
        <>
          {/* Tablet Wide Companion Window (2200×1600 Live View) */}
          {activeScreen.tabletFile && (
            <div
              style={{
                position: 'absolute',
                left: lensLeft,
                top: 54,
                width: lensWidth,
                height: 270,
                borderRadius: 20,
                border: '2.5px solid #38BDF8',
                background: '#06151D',
                overflow: 'hidden',
                boxShadow: '0 16px 38px rgba(0,0,0,0.6), 0 0 22px rgba(56, 189, 248, 0.2)',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div
                style={{
                  height: 32,
                  background: 'linear-gradient(90deg, #0C2D3E, #113C52)',
                  borderBottom: '1.5px solid rgba(56, 189, 248, 0.45)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '0 12px',
                  boxSizing: 'border-box',
                }}
              >
                <span style={{fontSize: 13, fontWeight: 800, color: '#7DD3FC', letterSpacing: 0.3}}>
                  💻 TABLET TB321FU VIEW
                </span>
                <span style={{fontSize: 12, fontWeight: 700, color: '#BAE6FD'}}>
                  2200×1600
                </span>
              </div>
              <div style={{width: lensWidth, height: 238, position: 'relative', overflow: 'hidden', background: '#051218'}}>
                <Img
                  src={staticFile(activeScreen.tabletFile)}
                  style={{width: '100%', height: '100%', objectFit: 'cover'}}
                />
              </div>
            </div>
          )}

          {/* Remotion Focus Window — Exact 1:1 Bounding-Box Crop */}
          <div
            style={{
              position: 'absolute',
              left: lensLeft,
              top: lensTop,
              width: lensWidth,
              height: lensTotalH,
              borderRadius: 20,
              border: '3px solid #6EE7B7',
              background: '#071A14',
              overflow: 'hidden',
              boxShadow: '0 20px 44px rgba(0,0,0,0.65), 0 0 24px rgba(110, 231, 183, 0.22)',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            {/* Dedicated Top Header Bar (Never overlaps the zoomed UI content!) */}
            <div
              style={{
                height: lensHeaderH,
                background: 'linear-gradient(90deg, #0D3326, #144636)',
                borderBottom: '1.5px solid rgba(110, 231, 183, 0.5)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '0 12px',
                boxSizing: 'border-box',
              }}
            >
              <span style={{fontSize: 13.5, fontWeight: 800, color: '#6EE7B7', letterSpacing: 0.4}}>
                🔍 REMOTION FOCUS VIEW
              </span>
              <span style={{fontSize: 12.5, fontWeight: 700, color: '#FDE047'}}>
                LIVE CROP {scrIdx + 1}/{numScreens}
              </span>
            </div>

            {/* Exact Corner-to-Corner Bounding-Box Cropped Viewport */}
            <div
              style={{
                width: lensWidth,
                height: lensViewportH,
                position: 'relative',
                overflow: 'hidden',
                background: '#0A1E17',
              }}
            >
              <div
                style={{
                  width: 1080,
                  height: 2400,
                  transformOrigin: '0 0',
                  transform: `translate(${zoomTx.toFixed(2)}px, ${zoomTy.toFixed(2)}px) scale(${zoomScaleX.toFixed(4)}, ${zoomScaleY.toFixed(4)})`,
                }}
              >
                <Img src={staticFile(activeScreen.file)} style={{width: 1080, height: 2400}} />
              </div>
            </div>
            {/* video-shotcraft SpotlightHeroCard Double-Lap Perimeter Specular Beam */}
            <ShotcraftPerimeterBeam width={lensWidth} height={lensTotalH} radius={20} frame={frame} />
          </div>

          {/* Smartphone Mockup (Steady, Zero Shake, Exact 9:20 Aspect Ratio 405×900) */}
          <div
            style={{
              position: 'absolute',
              left: phoneLeft,
              top: phoneTop,
              width: 441,
              height: 936,
              borderRadius: 48,
              background: '#16221E',
              border: '3.5px solid #6EE7B7',
              boxShadow: '0 28px 65px rgba(0,0,0,0.65), 0 0 35px rgba(74, 222, 128, 0.22)',
              padding: 18,
              boxSizing: 'border-box',
            }}
          >
            <div
              style={{
                width: 405,
                height: 900,
                borderRadius: 36,
                overflow: 'hidden',
                position: 'relative',
                background: '#061510',
              }}
            >
              <Img
                src={staticFile(activeScreen.file)}
                style={{width: 405, height: 900}}
              />
              <InPhoneVectorOverlay
                mode={scene.motionMode}
                scrIdx={scrIdx}
                localFrame={frame}
                durationInFrames={durationInFrames}
                focusRect={{
                  x: activeScreen.focusX,
                  y: activeScreen.focusY,
                  w: activeScreen.focusW,
                  h: activeScreen.focusH,
                }}
              />
            </div>
          </div>

          {/* Connector Line from Remotion Focus Window to Highlighted Box on Phone */}
          <svg
            width={1920}
            height={1080}
            style={{position: 'absolute', inset: 0, pointerEvents: 'none'}}
          >
            <line
              x1={lensLeft + lensWidth}
              y1={lensTop + lensTotalH / 2}
              x2={targetAnchorX}
              y2={targetAnchorY}
              stroke="#FDE047"
              strokeWidth={2.5}
              strokeDasharray="8 6"
            />
            <circle
              cx={targetAnchorX}
              cy={targetAnchorY}
              r={rippleRadius}
              fill="none"
              stroke={`rgba(253, 224, 71, ${rippleOpacity.toFixed(2)})`}
              strokeWidth={2.5}
            />
            <circle cx={targetAnchorX} cy={targetAnchorY} r={7} fill="#FDE047" />
          </svg>

          {/* Action Callout Label below Focus Window */}
          <div
            style={{
              position: 'absolute',
              left: lensLeft,
              top: lensTop + lensTotalH + 10,
              width: lensWidth,
              background: 'rgba(10, 32, 26, 0.96)',
              border: '2px solid #6EE7B7',
              borderRadius: 12,
              padding: '8px 14px',
              boxSizing: 'border-box',
              fontSize: 15,
              fontWeight: 700,
              color: '#ECFDF5',
              lineHeight: 1.35,
            }}
          >
            {activeScreen.actionLabel}
          </div>
        </>
      )}

      {/* Broadcast Subtitle Bar at Bottom (Hidden when rendering clean plate for JianYing) */}
      {!hideCaption && (
        <div
          style={{
            position: 'absolute',
            left: 60,
            bottom: 28,
            width: isMultiDevice ? 1280 : 1200,
            background: 'rgba(6, 18, 14, 0.92)',
            border: '1.5px solid rgba(82, 214, 148, 0.7)',
            borderRadius: 16,
            padding: '14px 24px',
            fontSize: 22.5,
            fontWeight: 700,
            lineHeight: 1.4,
            color: '#FFFFFF',
            boxShadow: '0 12px 32px rgba(0,0,0,0.45)',
          }}
        >
          {effCaption}
        </div>
      )}

      {/* video-shotcraft Recipe & Beat-Sync Telemetry Pill */}
      <ShotcraftRecipeHud recipeName={scene.shotcraftRecipe} beatNumber={beatNumber} bpm={124} />
    </AbsoluteFill>
  );
};

export const SingleSceneShowcase: React.FC<{sceneIndex: number}> = ({sceneIndex}) => {
  const sc = SCENES[sceneIndex] || SCENES[0];
  return <SceneView scene={sc} sceneIndex={sceneIndex} totalScenes={SCENES.length} />;
};

export interface VWingPromoProps {
  bgm?: boolean;
  sfx?: boolean;
  voiceover?: boolean;
  plate?: boolean;
}

export const VWingMotionPromo: React.FC<VWingPromoProps> = ({
  bgm = true,
  sfx = true,
  voiceover = true,
  plate = false,
}) => {
  const {fps, durationInFrames} = useVideoConfig();
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill style={{backgroundColor: '#04100C'}}>
      {/* 0. Cinematic Opening Intro: VWing Logo + 4-Stage Satellite Zoom to Demarcated Orchard (f0 - f144) */}
      <Sequence from={0} durationInFrames={VWING_INTRO_FRAMES}>
        <VWingSatelliteLogoIntro hideCaption={plate} />
      </Sequence>

      {/* 1. Vietnamese Narration Track (Starts right after 6.0s Satellite Intro so all 7 scenes stay 100% synced) */}
      {!plate && voiceover && (
        <Sequence from={VWING_INTRO_FRAMES}>
          <Audio src={staticFile('audio/narration_vi.mp3')} volume={0.96} />
        </Sequence>
      )}

      {/* 2. Beat-Synced Background Music Bed (video-shotcraft Rule #5: toggleable via bgm prop) */}
      {!plate && bgm && (
        <Audio src={staticFile('audio/bgm-tech-house.mp3')} volume={0.12} />
      )}

      {/* 3. Beat-Pinned Cinematic Sound Effects (Intro SFX + 36 Scene SFX cues) */}
      {!plate &&
        sfx &&
        INTRO_SFX.map((s, i) => (
          <Sequence key={`intro-sfx-${i}`} from={s.from} durationInFrames={sfxDuration(s)}>
            <Audio src={staticFile(`audio/${s.src}`)} volume={s.volume} />
          </Sequence>
        ))}
      {!plate &&
        sfx &&
        SFX.map((s, i) => (
          <Sequence key={`sfx-${i}`} from={VWING_INTRO_FRAMES + s.from} durationInFrames={sfxDuration(s)}>
            <Audio src={staticFile(`audio/${s.src}`)} volume={s.volume} />
          </Sequence>
        ))}

      {/* 4. Scene Sequences (Scenes 1-7 following the 6.0s Satellite Intro) */}
      {SCENES.map((sc, idx) => {
        const fromFrame = VWING_INTRO_FRAMES + Math.round(sc.startSec * fps);
        const durFrames = Math.max(1, Math.round((sc.endSec - sc.startSec) * fps));
        return (
          <Sequence key={sc.id} from={fromFrame} durationInFrames={durFrames}>
            <SceneView
              scene={sc}
              sceneIndex={idx}
              totalScenes={SCENES.length}
              hideCaption={plate}
            />
          </Sequence>
        );
      })}

      {/* 5. Warm/Emerald Flash Cuts Straddling Scene Changes (video-shotcraft FlashCut) */}
      {!plate &&
        FLASH_CUTS.map((cut) => (
          <Sequence key={`flash-${cut}`} from={Math.max(0, cut - 5)} durationInFrames={10}>
            <FlashCut duration={10} />
          </Sequence>
        ))}

      {/* 6. Global Top Progress Bar */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 6,
          background: 'rgba(255,255,255,0.15)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: `${(frame / durationInFrames) * 100}%`,
          height: 6,
          background: 'linear-gradient(90deg, #34D399, #FDE047)',
        }}
      />
    </AbsoluteFill>
  );
};
