// =============================================================================
// FARMBLOCK ONE 1.0.8 — FULL PORTED FEATURE SUITES FOR SMARTFARM
// Decompiled from com.farmblock.app v1.0.8 (Hermes v96 bytecode, modules 0746..2011)
// 1. WebGL 360° Panorama + 3D Pitch/Yaw Hotspots + OverviewPanel + Panoee Tour
// 2. Bản đồ Lô đất Mapbox (4 Map Styles, Polygons, 2-way Carousel, Info & QR Share)
// 3. Camera Live VLC + 24h Timeline Strip + Scrub Preview + Area Sheet + Fullscreen
// 4. Luồng IoT 19 Chỉ số (METRIC_CATALOG + RangeTrack + Open-Meteo 7d + AI) & Nhật ký Canh tác
// =============================================================================

const FB_TONE_COLORS = {
  good: { text: '#009E50', bg: '#D5F2E7', dot: '#009E50', border: '#9EE2C6' },
  warning: { text: '#C98A0A', bg: '#FCEFD2', dot: '#D79B18', border: '#F5D487' },
  critical: { text: '#E33B54', bg: '#F9D8DD', dot: '#E33B54', border: '#F2A3B0' },
  neutral: { text: '#60727C', bg: '#EEEFF0', dot: '#849199', border: '#DCE0E2' }
};

const FB_GARDENS = {
  coffee: {
    id: 'coffee',
    title: { vi: 'FarmBlock Mang Yang Cà Phê 1', en: 'FarmBlock Mang Yang Coffee 1' },
    location: { vi: 'Thôn Cây Xoài, Xã Iako, Tỉnh Gia Lai', en: 'Cay Xoai Hamlet, Iako Commune, Gia Lai' },
    areaBadge: '4.2 ha · 200 lô',
    panoramaUrl: 'assets/view360/panorama_vuon_ca_phe.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_img_home_360_card.jpg',
    overviewItems: [
      {
        id: 'cf-1',
        label: { vi: 'Giống cây: Cà phê Robusta Thiên Trường', en: 'Variety: Truong Son / Thien Truong Robusta' },
        shortVal: 'Robusta Thiên Trường',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_giong_cay.webp',
        detail: {
          vi: 'Giống cà phê vối (Robusta) dòng vô tính chọn lọc chất lượng cao, sinh trưởng khỏe, cành dẻo, quả to đồng đều, tỷ lệ tươi/nhân đạt 4.1–4.3 và kháng bệnh gỉ sắt vượt trội tại vùng đất đỏ Bazan Gia Lai.',
          en: 'High-yielding clonal Robusta variety with strong vigor, large uniform cherries, and high rust resistance.'
        }
      },
      {
        id: 'cf-2',
        label: { vi: 'Mật độ & khoảng cách trồng: 3m × 1m', en: 'Planting Density & Spacing: 3m × 1m' },
        shortVal: '3m × 1m (3.330 cây/ha)',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_mat_do.webp',
        detail: {
          vi: 'Thiết kế hàng cách hàng 3m, cây cách cây 1m giúp tối ưu hóa bức xạ mặt trời, thuận lợi cho cơ giới hóa nội khu và tăng sản lượng ngay từ năm thu bói thứ 2–3.',
          en: '3m row spacing by 1m plant spacing maximizes solar interception and early harvest yield.'
        }
      },
      {
        id: 'cf-3',
        label: { vi: 'Kỹ thuật: Đa thân hai thân', en: 'Technique: Dual-stem Multi-trunk' },
        shortVal: 'Đa thân 2 thân',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_ky_thuat_da_than.webp',
        detail: {
          vi: 'Nuôi 2 thân chính khỏe mạnh trên mỗi gốc giúp phân bố dinh dưỡng cân bằng, bộ tán thông thoáng và dễ dàng trẻ hóa vườn cây theo chu kỳ.',
          en: 'Maintaining two main stems per rootstock balances nutrient flow and canopy ventilation.'
        }
      },
      {
        id: 'cf-4',
        label: { vi: 'Kỹ thuật: Thả ngọn có kiểm soát', en: 'Technique: Controlled Free-top Growth' },
        shortVal: 'Thả ngọn kiểm soát',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_ky_thuat_tha_ngon.webp',
        detail: {
          vi: 'Áp dụng kỹ thuật nuôi ngọn có kiểm soát kết hợp tỉa cành tăm, cành vượt định kỳ để giảm công lao động hãm ngọn và tăng số đốt mang quả trên thân.',
          en: 'Controlled top growth reduces pruning labor while increasing fruit-bearing nodes.'
        }
      },
      {
        id: 'cf-5',
        label: { vi: 'Chiến lược: Mật độ theo giai đoạn', en: 'Strategy: Phased Density Management' },
        shortVal: 'Tối ưu theo tuổi cây',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_chien_luoc_mat_do.webp',
        detail: {
          vi: 'Giai đoạn kiến thiết và kinh doanh đầu giữ mật độ dày 3m×1m để thu hồi vốn nhanh; từ năm thứ 6 điều chỉnh tán để duy trì độ phủ tối ưu.',
          en: 'High initial density for rapid early yield, followed by canopy regulation in mature years.'
        }
      },
      {
        id: 'cf-6',
        label: { vi: 'Chiến lược: Đốn hạ & tái tạo cành', en: 'Strategy: Rejuvenation & Stumping' },
        shortVal: 'Luân phiên tái tạo',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_don_ha.webp',
        detail: {
          vi: 'Đốn phục hồi luân phiên từng thân sau chu kỳ khai thác đỉnh cao để vườn luôn duy trì bộ khung cành tơ năng suất ổn định.',
          en: 'Rotational stem rejuvenation maintains productive young fruiting branches.'
        }
      },
      {
        id: 'cf-7',
        label: { vi: 'Mục tiêu năng suất: 5-6 tấn/ha/năm', en: 'Target Yield: 5–6 tons/ha/year' },
        shortVal: '5–6 tấn nhân/ha/năm',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_muc_tieu.webp',
        detail: {
          vi: 'Kết hợp tưới nhỏ giọt bù áp tự động, châm phân Venturi theo cảm biến NPK/pH đất để đạt năng suất 5–6 tấn cà phê nhân/ha/năm.',
          en: 'Automated pressure-compensated drip fertigation targeting 5-6 tons of green bean per hectare annually.'
        }
      }
    ],
    hotspots: [
      {
        id: 'hs-cf-1',
        pitch: -24,
        yaw: -28,
        label: { vi: 'Hệ thống tưới nhỏ giọt', en: 'Drip Irrigation System' },
        category: 'Hạ tầng tưới',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp',
        detail: {
          vi: 'Đường ống tưới nhỏ giọt bù áp chạy dọc từng hàng cà phê 3m×1m, tích hợp van điện từ điều khiển từ xa và cảm biến lưu lượng Bơm 1 & Bơm 2.',
          en: 'Pressure-compensated drip lines along every coffee row with solenoid valves and flow telemetry.'
        }
      },
      {
        id: 'hs-cf-2',
        pitch: -10,
        yaw: 34,
        label: { vi: 'Hồ trữ nước trung tâm', en: 'Central Water Reservoir' },
        category: 'Nguồn nước',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b2_reservoir.webp',
        detail: {
          vi: 'Hồ chứa nước lót bạt HDPE dung tích lớn đảm bảo 100% nhu cầu nước tưới mùa khô Tây Nguyên, gắn cảm biến đo Nhiệt độ nước, pH nước và EC nước trực tuyến.',
          en: 'HDPE-lined central reservoir with real-time water temperature, pH, and EC monitoring.'
        }
      },
      {
        id: 'hs-cf-3',
        pitch: -32,
        yaw: 108,
        label: { vi: 'Bạt phủ chân giữ ẩm & chống cỏ', en: 'Weed-control Mulch Fabric' },
        category: 'Canh tác bền vững',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b4_mulch.webp',
        detail: {
          vi: 'Bạt địa kỹ thuật phủ gốc giúp giữ ẩm tầng đất mặt thêm 18–22%, hạn chế cỏ dại cạnh tranh dinh dưỡng mà không cần dùng thuốc diệt cỏ hóa học.',
          en: 'Geotextile mulch preserves topsoil moisture and suppresses weeds without chemical herbicides.'
        }
      },
      {
        id: 'hs-cf-4',
        pitch: -18,
        yaw: -135,
        label: { vi: 'Đường nội khu tiêu chuẩn', en: 'Internal Farm Roadway' },
        category: 'Giao thông nội khu',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b5_road.webp',
        detail: {
          vi: 'Hệ thống đường nội khu rộng 4m–6m phân chia rõ ranh giới từng lô đất, thuận tiện cho xe cơ giới vận chuyển vật tư và thu hoạch.',
          en: '4m-6m internal road network dividing plots for mechanized operations and harvest transport.'
        }
      }
    ]
  },
  coffee2: {
    id: 'coffee2',
    title: { vi: 'FarmBlock Mang Yang Cà Phê 2', en: 'FarmBlock Mang Yang Coffee 2' },
    location: { vi: 'Thôn Cây Xoài, Xã Iako, Tỉnh Gia Lai (Phân khu B)', en: 'Cay Xoai Hamlet, Iako Commune, Gia Lai (Zone B)' },
    areaBadge: '3.8 ha · Giai đoạn 2',
    panoramaUrl: 'assets/view360/panorama_vuon_ca_phe_2.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_img_map_360_card.jpg',
    overviewItems: null, // inherits coffee overviewItems with Zone B context
    hotspots: [
      {
        id: 'hs-cf2-1',
        pitch: -20,
        yaw: -15,
        label: { vi: 'Hệ thống tưới nhỏ giọt Phân khu B', en: 'Zone B Drip Fertigation' },
        category: 'Hạ tầng tưới',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp',
        detail: {
          vi: 'Cụm van tưới tự động Phân khu B kết nối trực tiếp với Trạm bơm phụ, duy trì độ ẩm đất 60–70% trong suốt mùa khô.',
          en: 'Zone B automated drip manifold connected to the secondary pump station.'
        }
      },
      {
        id: 'hs-cf2-2',
        pitch: -14,
        yaw: 52,
        label: { vi: 'Hồ trữ nước trung tâm (Góc nhìn Đông Bắc)', en: 'Central Reservoir (NE View)' },
        category: 'Nguồn nước',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b2_reservoir.webp',
        detail: {
          vi: 'Cung cấp nước tưới áp lực ổn định cho toàn bộ 200 lô đất thuộc nông trại MANG YANG 2.',
          en: 'Supplies pressurized irrigation water across all 200 plots of MANG YANG 2.'
        }
      },
      {
        id: 'hs-cf2-3',
        pitch: -28,
        yaw: 125,
        label: { vi: 'Thảm thực vật & Bạt phủ gốc', en: 'Ground Cover & Mulch Strip' },
        category: 'Canh tác bền vững',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b4_mulch.webp',
        detail: {
          vi: 'Kết hợp giữ thảm cỏ có kiểm soát giữa hai hàng 3m để chống xói mòn đất đỏ Bazan mùa mưa và trải bạt sát gốc cây.',
          en: 'Controlled inter-row grass strip prevents basalt soil erosion during heavy monsoon rains.'
        }
      },
      {
        id: 'hs-cf2-4',
        pitch: -16,
        yaw: -110,
        label: { vi: 'Trục đường chính Phân khu B', en: 'Zone B Main Arterial Road' },
        category: 'Giao thông nội khu',
        image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b5_road.webp',
        detail: {
          vi: 'Tuyến đường trục chính kết nối Khu Cà phê 2 với Trạm điều hành trung tâm và Khu Sầu riêng Musang King.',
          en: 'Main internal road linking Coffee Zone 2 with the operations center and Durian orchard.'
        }
      }
    ]
  },
  durian: {
    id: 'durian',
    title: { vi: 'FarmBlock Mang Yang Sầu Riêng 1', en: 'FarmBlock Mang Yang Durian 1' },
    location: { vi: 'Xã Ia Ko, Huyện Mang Yang, Tỉnh Gia Lai', en: 'Ia Ko Commune, Mang Yang District, Gia Lai' },
    areaBadge: '4.5 ha · Musang King',
    panoramaUrl: 'assets/view360/panorama_vuon_sau_rieng.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_img_home_360_hero.jpg',
    overviewItems: [
      {
        id: 'dr-1',
        label: { vi: 'Giống cây: Sầu riêng Musang King (Mao Shan Wang)', en: 'Variety: Musang King (Mao Shan Wang) Durian' },
        shortVal: 'Musang King D197',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_giong_cay.webp',
        detail: {
          vi: 'Giống sầu riêng Musang King (D197) cơm vàng đậm, hạt lép, hương vị béo ngậy đặc trưng, giá trị thương phẩm xuất khẩu cao hàng đầu thị trường.',
          en: 'Premium D197 Musang King durian renowned for golden creamy flesh, flat seeds, and high export value.'
        }
      },
      {
        id: 'dr-2',
        label: { vi: 'Mật độ & khoảng cách trồng: 8m × 6m', en: 'Planting Spacing: 8m × 6m' },
        shortVal: '8m × 6m (~208 hố/ha)',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_mat_do.webp',
        detail: {
          vi: 'Khoảng cách hàng 8m × cây 6m tạo không gian thông thoáng tối đa cho tán sầu riêng phát triển đều 4 hướng và đón nắng toàn diện.',
          en: '8m × 6m spacing provides optimal canopy expansion and full sunlight penetration.'
        }
      },
      {
        id: 'dr-3',
        label: { vi: 'Kỹ thuật trồng: 2 gốc / hố (Trồng kép)', en: 'Technique: Twin-rootstock (2 trees/mound)' },
        shortVal: '2 gốc / mô đất',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_ky_thuat_2_goc.webp',
        detail: {
          vi: 'Trồng 2 cây trên cùng một mô đất đắp cao thoát nước giúp tăng sức chống chịu gió bão Tây Nguyên, giảm rủi ro hao hụt và tăng sản lượng trên đơn vị diện tích.',
          en: 'Double-planting per raised mound improves wind resistance and increases early orchard productivity.'
        }
      },
      {
        id: 'dr-4',
        label: { vi: 'Giai đoạn hiện tại: Kiến thiết cơ bản (1.5 năm tuổi)', en: 'Current Stage: Vegetative Establishment (1.5 yrs)' },
        shortVal: '1.5 năm tuổi (Cây non)',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_giai_doan_hien_tai.webp',
        detail: {
          vi: 'Cây đạt 18 tháng tuổi, bộ khung cành cấp 1 phân bố đều, được bảo vệ bởi hệ thống tường bạt chắn gió và tưới nhỏ giọt bù áp tại từng gốc.',
          en: '18-month-old trees with well-structured primary branches protected by perimeter windbreaks.'
        }
      },
      {
        id: 'dr-5',
        label: { vi: 'Chu kỳ kinh doanh: 20–30 năm', en: 'Commercial Lifecycle: 20–30 Years' },
        shortVal: 'Khai thác 20–30 năm',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_chu_ky_kinh_doanh.webp',
        detail: {
          vi: 'Bắt đầu cho thu bói từ năm thứ 4–5 và bước vào giai đoạn kinh doanh ổn định kéo dài 20–30 năm với giá trị tài sản sinh học tăng trưởng bền vững.',
          en: 'First harvest at year 4-5 with a 20-30 year commercial bearing lifecycle.'
        }
      }
    ],
    hotspots: [
      {
        id: 'hs-dr-1',
        pitch: -8,
        yaw: -42,
        label: { vi: 'Hệ thống pin mặt trời', en: 'Solar Power Array' },
        category: 'Năng lượng xanh',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a1_solar.webp',
        detail: {
          vi: 'Hệ thống điện mặt trời áp mái & khung nổi cung cấp năng lượng sạch cho trạm bơm tưới, trạm cảm biến IoT và hệ thống Camera giám sát 24/7.',
          en: 'Solar PV array powering irrigation pumps, IoT telemetry stations, and 24/7 security cameras.'
        }
      },
      {
        id: 'hs-dr-2',
        pitch: -14,
        yaw: 18,
        label: { vi: 'Hồ trữ nước trung tâm', en: 'Main Irrigation Reservoir' },
        category: 'Nguồn nước',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a2_reservoir.webp',
        detail: {
          vi: 'Hồ trữ nước ngọt phục vụ tưới tiêu và điều hòa vi khí hậu cho toàn khu vực vườn Sầu riêng Musang King.',
          en: 'Freshwater reservoir supporting microclimate regulation and year-round irrigation.'
        }
      },
      {
        id: 'hs-dr-3',
        pitch: -6,
        yaw: 74,
        label: { vi: 'Nhà ở nhân sự & Kho vật tư', en: 'Staff Quarters & Material Warehouse' },
        category: 'Điều hành',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a3_housing.webp',
        detail: {
          vi: 'Khu nhà điều hành kỹ sư nông nghiệp trực chiến 24/7 và kho bảo quản phân bón hữu cơ vi sinh, thiết bị vật tư nông trại.',
          en: 'On-site agronomy team quarters and climate-protected organic input warehouse.'
        }
      },
      {
        id: 'hs-dr-4',
        pitch: -18,
        yaw: 112,
        label: { vi: 'Trạm bơm chính', en: 'Main Pump Station' },
        category: 'Hạ tầng tưới',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a4_pump_main.webp',
        detail: {
          vi: 'Trạm bơm trung tâm công suất lớn tích hợp bộ lọc đĩa tự động, bộ châm phân Venturi và đồng hồ đo lưu lượng điện tử.',
          en: 'High-capacity main pump house with disc filtration, Venturi fertigation, and digital flowmeters.'
        }
      },
      {
        id: 'hs-dr-5',
        pitch: -22,
        yaw: 156,
        label: { vi: 'Trạm bơm phụ tăng áp', en: 'Booster Sub-Pump Station' },
        category: 'Hạ tầng tưới',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a5_pump_sub.webp',
        detail: {
          vi: 'Trạm bơm phụ đảm bảo áp suất nước đồng đều cho các lô đất nằm ở cao độ 720m–735m.',
          en: 'Secondary booster pump ensuring uniform pressure across higher-elevation plots.'
        }
      },
      {
        id: 'hs-dr-6',
        pitch: -4,
        yaw: -118,
        label: { vi: 'Hệ thống tường bạt chắn gió', en: 'Perimeter Windbreak Netting' },
        category: 'Bảo vệ cây trồng',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a6_windbreak.webp',
        detail: {
          vi: 'Hệ thống lưới chắn gió chuyên dụng bao quanh lô sầu riêng giúp giảm tốc độ gió lốc, bảo vệ bộ lá non và hạn chế rụng hoa/trái non.',
          en: 'Engineered windbreak screens reducing gust velocity to protect young foliage and blossoms.'
        }
      },
      {
        id: 'hs-dr-7',
        pitch: -30,
        yaw: -75,
        label: { vi: 'Hệ thống tưới nhỏ giọt tại gốc', en: 'Root-zone Micro-sprinkler & Drip' },
        category: 'Hạ tầng tưới',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a8_drip.webp',
        detail: {
          vi: 'Vòng tưới nhỏ giọt & béc phun mưa cục bộ quanh mô sầu riêng kép (2 gốc/hố), điều chỉnh lưu lượng theo độ ẩm vùng rễ.',
          en: 'Dual-ring micro-irrigation around each twin-tree mound calibrated by soil moisture sensors.'
        }
      },
      {
        id: 'hs-dr-8',
        pitch: -19,
        yaw: -162,
        label: { vi: 'Đường nội khu vườn sầu riêng', en: 'Durian Orchard Internal Road' },
        category: 'Giao thông nội khu',
        image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a9_road.webp',
        detail: {
          vi: 'Tuyến đường cấp phối đá dăm thoát nước hai bên giúp xe kỹ thuật tiếp cận từng hàng cây trong cả mùa mưa.',
          en: 'All-weather gravel internal road with side drainage ditches.'
        }
      }
    ]
  }
};
FB_GARDENS.coffee2.overviewItems = FB_GARDENS.coffee.overviewItems;

// =============================================================================
// 19-METRIC IOT CATALOG (Decompiled from module_1795.js)
// =============================================================================
const FB_METRIC_CATALOG = [
  {
    key: 'weather',
    title: 'Thời tiết nông trại',
    subtitle: '7 chỉ số khí tượng vi khí hậu trực tiếp tại lô đất',
    items: [
      {
        key: 'air-temperature',
        title: 'Nhiệt độ không khí',
        unit: '°C',
        value: 27.4,
        variant: 'range',
        scale: { min: 0, max: 50 },
        goodRange: { min: 18, max: 35 },
        tone: 'good',
        statusLabel: 'Phù hợp',
        iconTone: '#009E50',
        info: {
          meaning: 'Phản ánh nền nhiệt không khí quanh tán cây, ảnh hưởng trực tiếp đến tốc độ quang hợp, thoát hơi nước và phân hóa mầm hoa.',
          reference: [
            { tone: 'warning', text: 'Dưới 18°C: Nhiệt độ thấp, cây chậm phát triển' },
            { tone: 'good', text: '18–35°C: Phù hợp cho sinh trưởng' },
            { tone: 'critical', text: 'Trên 35°C: Nắng nóng gay gắt, nguy cơ sốc nhiệt' }
          ],
          recommendation: 'Khi nhiệt độ vượt 35°C kéo dài, kết hợp kiểm tra độ ẩm đất để tăng tưới làm mát tiểu khí hậu.'
        }
      },
      {
        key: 'air-humidity',
        title: 'Độ ẩm không khí',
        unit: '%',
        value: 74,
        variant: 'range',
        scale: { min: 0, max: 100 },
        goodRange: { min: 60, max: 85 },
        tone: 'good',
        statusLabel: 'Phù hợp',
        iconTone: '#16A3CA',
        info: {
          meaning: 'Ảnh hưởng đến cường độ thoát hơi nước qua lá và nguy cơ phát sinh nấm bệnh (gỉ sắt, nấm hồng, thán thư).',
          reference: [
            { tone: 'warning', text: 'Dưới 60%: Không khí khô, tăng thoát hơi nước' },
            { tone: 'good', text: '60–85%: Phù hợp' },
            { tone: 'warning', text: 'Trên 85%: Độ ẩm cao, lưu ý nấm bệnh' }
          ],
          recommendation: 'Độ ẩm > 85% nhiều ngày liên tiếp cần tỉa thông thoáng tán và kiểm tra nấm bệnh trên lá.'
        }
      },
      {
        key: 'rainfall',
        title: 'Lượng mưa',
        unit: 'mm',
        value: 1.2,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Mưa nhẹ',
        iconTone: '#16A3CA',
        info: {
          meaning: 'Ghi nhận lượng mưa thực tế tại nông trại để điều chỉnh giảm hoặc tạm ngưng lịch tưới tự động.',
          reference: [
            { tone: 'neutral', text: '0 mm: Không mưa' },
            { tone: 'good', text: '1–25 mm: Mưa bổ sung ẩm tự nhiên' },
            { tone: 'warning', text: 'Trên 50 mm/ngày: Mưa lớn, kiểm tra thoát nước' }
          ],
          recommendation: 'Khi mưa > 15 mm cần tạm hoãn tưới nhỏ giọt và kiểm tra rãnh thoát nước nội khu.'
        }
      },
      {
        key: 'wind-speed',
        title: 'Tốc độ gió',
        unit: 'm/s',
        value: 2.8,
        variant: 'range',
        scale: { min: 0, max: 12 },
        goodRange: { min: 0.5, max: 5.0 },
        tone: 'good',
        statusLabel: 'Bình thường',
        iconTone: '#25976A',
        info: {
          meaning: 'Ảnh hưởng đến thông thoáng tán cây, thụ phấn và an toàn cơ học của cành/trái (đặc biệt với Sầu riêng Musang King).',
          reference: [
            { tone: 'good', text: '0–5 m/s: Gió nhẹ đến vừa, thông thoáng tốt' },
            { tone: 'warning', text: '5–8 m/s: Gió mạnh, không nên phun thuốc/bón lá' },
            { tone: 'critical', text: 'Trên 8 m/s: Gió giật mạnh, kiểm tra bạt chắn gió' }
          ],
          recommendation: 'Tránh phun chế phẩm sinh học hoặc phân bón lá khi gió > 5 m/s.'
        }
      },
      {
        key: 'wind-direction',
        title: 'Hướng gió',
        unit: '° (ĐN)',
        value: 135,
        variant: 'plain',
        tone: 'neutral',
        statusLabel: 'Đông Nam',
        iconTone: '#60727C',
        info: {
          meaning: 'Xác định hướng gió chủ đạo để bố trí hàng chắn gió và lập kế hoạch phun chế phẩm an toàn.',
          reference: [
            { tone: 'neutral', text: '0°–360°: Phương vị khí tượng tiêu chuẩn' }
          ],
          recommendation: 'Theo dõi kết hợp cùng tốc độ gió khi vận hành hệ thống tường bạt chắn gió.'
        }
      },
      {
        key: 'light-intensity',
        title: 'Cường độ ánh sáng',
        unit: 'Lux',
        value: 42500,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đủ sáng',
        iconTone: '#D79B18',
        info: {
          meaning: 'Đo bức xạ ánh sáng phục vụ quang hợp và tổng hợp chất khô cho cây trồng.',
          reference: [
            { tone: 'warning', text: 'Dưới 10.000 Lux: Trời âm u / thiếu sáng' },
            { tone: 'good', text: '20.000–65.000 Lux: Quang hợp tối ưu' },
            { tone: 'warning', text: 'Trên 80.000 Lux: Nắng gắt đỉnh trưa' }
          ],
          recommendation: 'Duy trì mật độ tán hợp lý để ánh sáng phân bố đều vào tầng cành mang quả.'
        }
      },
      {
        key: 'pressure',
        title: 'Áp suất khí quyển',
        unit: 'hPa',
        value: 1008.4,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Ổn định',
        iconTone: '#60727C',
        info: {
          meaning: 'Phản ánh biến động thời tiết tại cao nguyên Gia Lai (cao độ ~720m), hỗ trợ cảnh báo sớm mưa dông.',
          reference: [
            { tone: 'good', text: '1000–1018 hPa: Áp suất ổn định' },
            { tone: 'warning', text: 'Giảm đột ngột > 4 hPa: Khả năng có dông mưa' }
          ],
          recommendation: 'Kết hợp với dự báo Open-Meteo 7 ngày để chủ động lịch canh tác.'
        }
      }
    ]
  },
  {
    key: 'water',
    title: 'Chỉ số nước',
    subtitle: '5 chỉ số chất lượng nước hồ trung tâm & lưu lượng trạm bơm',
    items: [
      {
        key: 'water-temperature',
        title: 'Nhiệt độ nước',
        unit: '°C',
        value: 24.5,
        variant: 'range',
        scale: { min: 0, max: 50 },
        goodRange: { min: 20, max: 30 },
        tone: 'good',
        statusLabel: 'Phù hợp',
        iconTone: '#009E50',
        info: {
          meaning: 'Nhiệt độ nước tưới ảnh hưởng đến khả năng hấp thu nước và tránh gây sốc nhiệt cho lông hút bộ rễ.',
          reference: [
            { tone: 'warning', text: 'Dưới 20°C: Nước lạnh' },
            { tone: 'good', text: '20–30°C: Phù hợp tưới tiêu' },
            { tone: 'critical', text: 'Trên 32°C: Nước nóng, hạn chế tưới giữa trưa' }
          ],
          recommendation: 'Ưu tiên tưới vào sáng sớm (06:00–09:00) hoặc chiều mát khi nhiệt độ nước 22–28°C.'
        }
      },
      {
        key: 'water-ph',
        title: 'Độ pH nước',
        unit: 'pH',
        value: 6.4,
        variant: 'range',
        scale: { min: 4, max: 10 },
        goodRange: { min: 5.5, max: 7.5 },
        tone: 'good',
        statusLabel: 'Phù hợp',
        iconTone: '#25976A',
        info: {
          meaning: 'Quyết định độ hòa tan của phân bón khi châm phân qua hệ thống tưới nhỏ giọt và độ bền đường ống.',
          reference: [
            { tone: 'critical', text: 'Dưới 5.0: Nước chua (axit cao)' },
            { tone: 'good', text: '5.5–7.5: Phù hợp cho nước tưới nông nghiệp' },
            { tone: 'warning', text: 'Trên 7.5: Nước kiềm, dễ kết tủa vi lượng' }
          ],
          recommendation: 'Kiểm tra pH nước trước mỗi đợt hòa phân qua bồn Venturi.'
        }
      },
      {
        key: 'water-ec',
        title: 'Độ dẫn điện nước (EC)',
        unit: 'µS/cm',
        value: 380,
        variant: 'range',
        scale: { min: 0, max: 2000 },
        goodRange: { min: 100, max: 700 },
        tone: 'good',
        statusLabel: 'Tốt (<700)',
        iconTone: '#16A3CA',
        info: {
          meaning: 'Phản ánh tổng lượng muối khoáng hòa tan trong nước tưới.',
          reference: [
            { tone: 'good', text: 'Dưới 700 µS/cm: Nước ngọt tốt, an toàn cho mọi giai đoạn' },
            { tone: 'warning', text: '700–1500 µS/cm: Cần theo dõi nồng độ phân hòa tan' },
            { tone: 'critical', text: 'Trên 1500 µS/cm: Độ mặn/khoáng cao, nguy cơ cháy rễ' }
          ],
          recommendation: 'Khi châm phân tự động, kiểm soát tổng EC dung dịch tưới không vượt ngưỡng chịu đựng của cây non.'
        }
      },
      {
        key: 'pump-1-flow',
        title: 'Lưu lượng Bơm 1 (Trạm chính)',
        unit: 'm³/h',
        value: 14.8,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đang chạy',
        iconTone: '#009E50',
        info: {
          meaning: 'Theo dõi lưu lượng thực tế của Trạm bơm chính cấp nước cho Phân khu A.',
          reference: [
            { tone: 'neutral', text: '0 m³/h: Bơm đang nghỉ' },
            { tone: 'good', text: '10–18 m³/h: Lưu lượng vận hành chuẩn' },
            { tone: 'warning', text: 'Thấp bất thường khi bơm bật: Kiểm tra nghẹt lọc đĩa' }
          ],
          recommendation: 'Xả rửa bầu lọc đĩa định kỳ nếu lưu lượng giảm > 15% so với định mức.'
        }
      },
      {
        key: 'pump-2-flow',
        title: 'Lưu lượng Bơm 2 (Trạm phụ)',
        unit: 'm³/h',
        value: 9.2,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đang chạy',
        iconTone: '#009E50',
        info: {
          meaning: 'Theo dõi lưu lượng thực tế của Trạm bơm phụ tăng áp cho các lô đất gò cao.',
          reference: [
            { tone: 'neutral', text: '0 m³/h: Bơm đang nghỉ' },
            { tone: 'good', text: '8–14 m³/h: Lưu lượng tăng áp ổn định' }
          ],
          recommendation: 'Đảm bảo áp suất cuối đường ống nhánh đạt tối thiểu 1.2–1.5 bar.'
        }
      }
    ]
  },
  {
    key: 'soil',
    title: 'Dinh dưỡng đất',
    subtitle: '7 chỉ số cảm biến vùng rễ tầng đất đỏ Bazan (0–30 cm)',
    items: [
      {
        key: 'soil-temperature',
        title: 'Nhiệt độ đất',
        unit: '°C',
        value: 22.8,
        variant: 'range',
        scale: { min: 0, max: 40 },
        goodRange: { min: 18, max: 25 },
        tone: 'good',
        statusLabel: 'Phù hợp',
        iconTone: '#009E50',
        info: {
          meaning: 'Ảnh hưởng đến hoạt động của rễ và vi sinh vật trong đất, từ đó ảnh hưởng đến điều kiện sinh trưởng của cây.',
          reference: [
            { tone: 'warning', text: 'Dưới 18°C: Cần theo dõi' },
            { tone: 'good', text: '18–25°C: Phù hợp' },
            { tone: 'warning', text: '25–30°C: Cần theo dõi' },
            { tone: 'critical', text: 'Trên 30°C: Nhiệt độ cao' }
          ],
          recommendation: 'Theo dõi kết hợp với độ ẩm đất và nhiệt độ không khí; khi đất nóng và khô kéo dài cần kiểm tra nhu cầu tưới.'
        }
      },
      {
        key: 'soil-moisture',
        title: 'Độ ẩm đất',
        unit: '%',
        value: 64.2,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đủ ẩm',
        iconTone: '#16A3CA',
        info: {
          meaning: 'Xác định lượng nước hiện có trong vùng rễ, là cơ sở trực tiếp để quyết định thời điểm và lượng nước tưới.',
          reference: [
            { tone: 'critical', text: 'Dưới 40%: Đất khô, cần bật tưới ngay' },
            { tone: 'good', text: '55–75%: Độ ẩm vùng rễ tối ưu' },
            { tone: 'warning', text: 'Trên 80% kéo dài: Nguy cơ bí rễ / ngập úng' }
          ],
          recommendation: 'Độ ẩm thấp → đề xuất tưới. Độ ẩm cao kéo dài → theo dõi nguy cơ úng và giảm/tạm hoãn tưới.'
        }
      },
      {
        key: 'soil-ph',
        title: 'Độ pH đất',
        unit: 'pH',
        value: 6.1,
        variant: 'range',
        scale: { min: 4, max: 8 },
        goodRange: { min: 5.5, max: 6.5 },
        tone: 'good',
        statusLabel: 'Tối ưu',
        iconTone: '#25976A',
        info: {
          meaning: 'Quyết định khả năng giải phóng và hấp thu các nguyên tố đa - trung - vi lượng trong đất đỏ Bazan.',
          reference: [
            { tone: 'critical', text: 'Dưới 5.0: Đất chua nặng, bó rễ' },
            { tone: 'warning', text: '5.0–5.5: Hơi chua, cần bổ sung vôi/hữu cơ' },
            { tone: 'good', text: '5.5–6.5: Tối ưu cho Cà phê & Sầu riêng' },
            { tone: 'warning', text: 'Trên 6.8: Cần theo dõi vi lượng sắt, kẽm' }
          ],
          recommendation: 'Duy trì pH 5.5–6.5 bằng phân hữu cơ vi sinh và Dolomite định kỳ đầu/cuối mùa mưa.'
        }
      },
      {
        key: 'soil-ec',
        title: 'Độ dẫn điện đất (EC)',
        unit: 'µS/cm',
        value: 420,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Cân bằng',
        iconTone: '#16A3CA',
        info: {
          meaning: 'Đánh giá mức độ dinh dưỡng khoáng hòa tan sẵn có trong dung dịch đất vùng rễ.',
          reference: [
            { tone: 'warning', text: 'Dưới 200 µS/cm: Đất nghèo dinh dưỡng hòa tan' },
            { tone: 'good', text: '250–800 µS/cm: Mức dinh dưỡng hoạt động tốt' },
            { tone: 'critical', text: 'Trên 1200 µS/cm: Dư thừa muối phân bón' }
          ],
          recommendation: 'Chia nhỏ lượng phân bón qua hệ thống tưới nhỏ giọt để giữ EC đất ổn định.'
        }
      },
      {
        key: 'soil-nitrogen',
        title: 'Đạm dễ tiêu (N)',
        unit: 'mg/kg',
        value: 145,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đạt chuẩn',
        iconTone: '#009E50',
        info: {
          meaning: 'Thúc đẩy phát triển chồi non, cành dự trữ và diện tích bộ lá quang hợp.',
          reference: [
            { tone: 'warning', text: 'Dưới 80 mg/kg: Thiếu đạm, lá vàng nhạt' },
            { tone: 'good', text: '100–180 mg/kg: Đủ đạm cho sinh trưởng thân lá' }
          ],
          recommendation: 'Tăng cường đạm hữu cơ vào giai đoạn phục hồi sau thu hoạch và nuôi cành.'
        }
      },
      {
        key: 'soil-phosphorus',
        title: 'Lân dễ tiêu (P)',
        unit: 'mg/kg',
        value: 68,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Đạt chuẩn',
        iconTone: '#25976A',
        info: {
          meaning: 'Hỗ trợ phát triển bộ rễ tơ mạnh mẽ và kích thích quá trình phân hóa mầm hoa.',
          reference: [
            { tone: 'warning', text: 'Dưới 35 mg/kg: Cần bổ sung lân dễ tiêu' },
            { tone: 'good', text: '45–95 mg/kg: Tốt cho bộ rễ và phân hóa hoa' }
          ],
          recommendation: 'Đảm bảo pH đất ≥ 5.5 để tránh lân bị cố định bởi sắt và nhôm trong đất đỏ Bazan.'
        }
      },
      {
        key: 'soil-potassium',
        title: 'Kali trao đổi (K)',
        unit: 'mg/kg',
        value: 192,
        variant: 'plain',
        tone: 'good',
        statusLabel: 'Tối ưu',
        iconTone: '#D79B18',
        info: {
          meaning: 'Ảnh hưởng đến khả năng tạo quả và sức chống chịu của cây; liên quan đến chất lượng và năng suất.',
          reference: [
            { tone: 'warning', text: 'Dưới 100 mg/kg: Thiếu Kali, hạt lép/trái nhỏ' },
            { tone: 'good', text: '140–240 mg/kg: Tối ưu cho nuôi trái và tích lũy đường/nhân' }
          ],
          recommendation: 'Bổ sung Kali sunfat (K2SO4) vào giai đoạn nuôi nhân cà phê và vào cơm sầu riêng.'
        }
      }
    ]
  }
];

// =============================================================================
// FARMBLOCK PLOTS, CAMERAS & CARE LOGS DATA (Modules 0945, 0963, 1764..1768)
// =============================================================================
const FB_FARM_META = {
  name: 'MANG YANG 2',
  code: '6801112000',
  address: 'Thôn Cây Xoài, Xã Iako, Tỉnh Gia Lai',
  totalPlots: 200,
  totalAreaM2: 8000,
  centerLat: 13.7563,
  centerLng: 107.7519
};

const FB_PLOTS = [
  {
    id: 'LOT-IAKO-01',
    code: 'LOT-IAKO-01',
    name: 'Khu trồng Cà phê Robusta #LOT-IAKO-01',
    crop: 'Cà phê Robusta Thiên Trường',
    gardenKey: 'coffee',
    areaM2: 1250,
    treesCount: 416,
    spacing: '3m × 1m (Đa thân 2 thân)',
    status: 'Đang canh tác',
    location: 'Phân khu A · Thôn Cây Xoài, Xã Iako, Gia Lai',
    lat: 13.7568,
    lng: 107.7512,
    polygonSvg: '85,70 230,62 245,175 95,185',
    stageKey: 'young',
    plantedDate: '15/03/2025',
    expectedHarvest: 'Quý 4/2027',
    yieldLastKg: 4850,
    yieldTargetKg: 5600,
    soilType: 'Đất đỏ Bazan tầng dày (> 1.5m), giàu mùn, thoát nước tự nhiên tốt',
    elevationM: 722,
    slopeDeg: 4.5,
    nftTokenId: 'NFT-IAKO-001',
    financeTotal: 185000000,
    financePaid: 148000000,
    financeNextDue: 37000000,
    financeNextLabel: 'Đợt 4 — Phí vận hành & chăm sóc năm thứ 2',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=37000000&addInfo=FARMBLOCK%20LOT%20IAKO%2001%20DOT4'
  },
  {
    id: 'LOT-IAKO-02',
    code: 'LOT-IAKO-02',
    name: 'Khu trồng Sầu riêng Musang King #LOT-IAKO-02',
    crop: 'Sầu riêng Musang King (D197)',
    gardenKey: 'durian',
    areaM2: 2400,
    treesCount: 100,
    spacing: '8m × 6m (Trồng kép 2 gốc/hố)',
    status: 'Đang canh tác',
    location: 'Phân khu Sầu riêng · Xã Ia Ko, Mang Yang, Gia Lai',
    lat: 13.7574,
    lng: 107.7526,
    polygonSvg: '260,58 430,52 442,182 272,188',
    stageKey: 'young',
    plantedDate: '10/04/2025',
    expectedHarvest: 'Quý 3/2029',
    yieldLastKg: 0,
    yieldTargetKg: 6200,
    soilType: 'Đất đỏ Bazan tơi xốp, đắp mô kép cao 0.6m chống úng rễ',
    elevationM: 728,
    slopeDeg: 3.8,
    nftTokenId: 'NFT-IAKO-002',
    financeTotal: 320000000,
    financePaid: 256000000,
    financeNextDue: 64000000,
    financeNextLabel: 'Đợt 4 — Chăm sóc chuyên sâu Sầu riêng Musang King',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=64000000&addInfo=FARMBLOCK%20LOT%20IAKO%2002%20DOT4'
  },
  {
    id: 'LOT-IAKO-03',
    code: 'LOT-IAKO-03',
    name: 'Khu trồng Cà phê Robusta #LOT-IAKO-03',
    crop: 'Cà phê Robusta Thiên Trường',
    gardenKey: 'coffee2',
    areaM2: 1500,
    treesCount: 500,
    spacing: '3m × 1m (Thả ngọn kiểm soát)',
    status: 'Đang canh tác',
    location: 'Phân khu B · Gần Hồ trữ nước trung tâm, Ia Ko',
    lat: 13.7556,
    lng: 107.7510,
    polygonSvg: '98,200 250,192 262,315 108,325',
    stageKey: 'grow',
    plantedDate: '20/05/2024',
    expectedHarvest: 'Tháng 11/2026',
    yieldLastKg: 5120,
    yieldTargetKg: 5800,
    soilType: 'Đất đỏ Bazan ven hồ trữ nước, độ ẩm tự nhiên ổn định quanh năm',
    elevationM: 716,
    slopeDeg: 3.2,
    nftTokenId: 'NFT-IAKO-003',
    financeTotal: 210000000,
    financePaid: 210000000,
    financeNextDue: 0,
    financeNextLabel: 'Đã hoàn tất 100% các đợt thanh toán',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=10000000&addInfo=FARMBLOCK%20LOT%20IAKO%2003'
  },
  {
    id: 'LOT-IAKO-04',
    code: 'LOT-IAKO-04',
    name: 'Khu trồng Cà phê Đặc sản #LOT-IAKO-04',
    crop: 'Cà phê Robusta Fine Robusta',
    gardenKey: 'coffee',
    areaM2: 1100,
    treesCount: 366,
    spacing: '3m × 1m (Có bạt phủ gốc)',
    status: 'Đang canh tác',
    location: 'Phân khu B · Cạnh Trạm bơm chính, Xã Ia Ko',
    lat: 13.7554,
    lng: 107.7525,
    polygonSvg: '278,202 446,196 458,318 286,326',
    stageKey: 'grow',
    plantedDate: '01/06/2024',
    expectedHarvest: 'Tháng 12/2026',
    yieldLastKg: 4950,
    yieldTargetKg: 5500,
    soilType: 'Đất đỏ Bazan hữu cơ vi sinh (pH 6.2)',
    elevationM: 720,
    slopeDeg: 4.0,
    nftTokenId: 'NFT-IAKO-004',
    financeTotal: 165000000,
    financePaid: 132000000,
    financeNextDue: 33000000,
    financeNextLabel: 'Đợt 4 — Phí thu hoạch & chế biến ướt Fine Robusta',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=33000000&addInfo=FARMBLOCK%20LOT%20IAKO%2004%20DOT4'
  },
  {
    id: 'LOT-IAKO-05',
    code: 'LOT-IAKO-05',
    name: 'Khu trồng Sầu riêng Musang King #LOT-IAKO-05',
    crop: 'Sầu riêng Musang King (D197)',
    gardenKey: 'durian',
    areaM2: 1000,
    treesCount: 42,
    spacing: '8m × 6m (2 gốc/hố · Chắn gió)',
    status: 'Kiến thiết cơ bản',
    location: 'Phân khu Đông · Cạnh Trạm điện mặt trời',
    lat: 13.7565,
    lng: 107.7536,
    polygonSvg: '462,60 610,55 620,185 470,190',
    stageKey: 'plant',
    plantedDate: '12/08/2025',
    expectedHarvest: 'Quý 4/2029',
    yieldLastKg: 0,
    yieldTargetKg: 5400,
    soilType: 'Đất đỏ Bazan tầng mặt giàu hữu cơ, có lưới chắn gió 4 phía',
    elevationM: 731,
    slopeDeg: 5.1,
    nftTokenId: 'NFT-IAKO-005',
    financeTotal: 150000000,
    financePaid: 90000000,
    financeNextDue: 30000000,
    financeNextLabel: 'Đợt 3 — Hệ thống tưới bù áp & dinh dưỡng kiến thiết',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=30000000&addInfo=FARMBLOCK%20LOT%20IAKO%2005%20DOT3'
  },
  {
    id: 'LOT-IAKO-06',
    code: 'LOT-IAKO-06',
    name: 'Khu trồng Cà phê Robusta #LOT-IAKO-06',
    crop: 'Cà phê Robusta Thiên Trường',
    gardenKey: 'coffee2',
    areaM2: 750,
    treesCount: 250,
    spacing: '3m × 1m (Đa thân 2 thân)',
    status: 'Đang canh tác',
    location: 'Phân khu Đông Nam · Đường nội khu trục 2',
    lat: 13.7550,
    lng: 107.7538,
    polygonSvg: '474,204 622,198 632,320 482,328',
    stageKey: 'young',
    plantedDate: '18/02/2025',
    expectedHarvest: 'Quý 4/2027',
    yieldLastKg: 4600,
    yieldTargetKg: 5400,
    soilType: 'Đất đỏ Bazan thoát nước tốt, độ pH 6.1',
    elevationM: 725,
    slopeDeg: 4.2,
    nftTokenId: 'NFT-IAKO-006',
    financeTotal: 115000000,
    financePaid: 92000000,
    financeNextDue: 23000000,
    financeNextLabel: 'Đợt 4 — Chăm sóc định kỳ & phân bón vi sinh',
    vietQrUrl: 'https://img.vietqr.io/image/MB-0987654321-compact2.png?amount=23000000&addInfo=FARMBLOCK%20LOT%20IAKO%2006%20DOT4'
  }
];

const FB_CAMERAS = [
  {
    id: 'CAM-IAKO-01',
    name: 'CAM 01 — Cổng chính & Đường nội khu Phân khu A',
    area: 'Khu Cà phê Robusta 1 (#LOT-IAKO-01)',
    status: 'online',
    resolution: '2K QHD · 25fps · H.265',
    rtspUrl: 'rtsp://stream.farmblock.vn:8554/mangyang2/cam01',
    hlsUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    snapshotUrl: 'assets/view360/panorama_vuon_ca_phe.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b5_road.webp',
    segmentsByDate: {
      '2026-10-30': [
        { startSec: 21600, endSec: 34200, label: '06:00–09:30 · Tưới nhỏ giọt ca sáng & Kiểm tra đường ống', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp' },
        { startSec: 36900, endSec: 46800, label: '10:15–13:00 · Đội kỹ thuật kiểm tra độ ẩm & bạt phủ gốc', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b4_mulch.webp' },
        { startSec: 50400, endSec: 64800, label: '14:00–18:00 · Vận hành vật tư trên đường nội khu Khu A', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b5_road.webp' },
        { startSec: 68400, endSec: 79200, label: '19:00–22:00 · Giám sát an ninh hồng ngoại ban đêm', thumb: 'assets/farmblock/src_assets_images_img_home_360_card.jpg' }
      ],
      '2026-10-29': [
        { startSec: 23400, endSec: 39600, label: '06:30–11:00 · Bón phân hữu cơ vi sinh đợt 3', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_ky_thuat_da_than.webp' },
        { startSec: 48600, endSec: 63000, label: '13:30–17:30 · Tỉa cành vượt & vệ sinh rãnh thoát nước', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_don_ha.webp' }
      ],
      '2026-10-28': [
        { startSec: 25200, endSec: 43200, label: '07:00–12:00 · Toàn cảnh vườn sau mưa rào nhẹ', thumb: 'assets/view360/panorama_vuon_ca_phe.jpg' }
      ]
    }
  },
  {
    id: 'CAM-IAKO-02',
    name: 'CAM 02 — Hồ trữ nước trung tâm & Trạm bơm chính',
    area: 'Hạ tầng Nguồn nước & Điện mặt trời',
    status: 'online',
    resolution: '4MP PTZ 360° · 30fps',
    rtspUrl: 'rtsp://stream.farmblock.vn:8554/mangyang2/cam02',
    hlsUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    snapshotUrl: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b2_reservoir.webp',
    thumbUrl: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b2_reservoir.webp',
    segmentsByDate: {
      '2026-10-30': [
        { startSec: 19800, endSec: 36000, label: '05:30–10:00 · Bơm chính cấp nước tưới Phân khu A & B', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a4_pump_main.webp' },
        { startSec: 43200, endSec: 61200, label: '12:00–17:00 · Giám sát mực nước hồ trung tâm & Pin mặt trời', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a1_solar.webp' }
      ],
      '2026-10-29': [
        { startSec: 28800, endSec: 54000, label: '08:00–15:00 · Xả rửa bầu lọc đĩa & kiểm tra van điện từ', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a5_pump_sub.webp' }
      ],
      '2026-10-28': [
        { startSec: 21600, endSec: 50400, label: '06:00–14:00 · Vận hành trạm bơm tự động', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b2_reservoir.webp' }
      ]
    }
  },
  {
    id: 'CAM-IAKO-03',
    name: 'CAM 03 — Vườn Sầu riêng Musang King & Tường chắn gió',
    area: 'Khu Sầu riêng Musang King (#LOT-IAKO-02)',
    status: 'online',
    resolution: '2K QHD · 25fps · AI Motion',
    rtspUrl: 'rtsp://stream.farmblock.vn:8554/mangyang2/cam03',
    hlsUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    snapshotUrl: 'assets/view360/panorama_vuon_sau_rieng.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a6_windbreak.webp',
    segmentsByDate: {
      '2026-10-30': [
        { startSec: 23400, endSec: 41400, label: '06:30–11:30 · Tưới béc bù áp tại gốc Sầu riêng kép (2 gốc/hố)', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a8_drip.webp' },
        { startSec: 46800, endSec: 66600, label: '13:00–18:30 · Kiểm tra hệ thống tường bạt chắn gió & đọt non', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a6_windbreak.webp' }
      ],
      '2026-10-29': [
        { startSec: 27000, endSec: 52200, label: '07:30–14:30 · Phun phòng rầy xanh sinh học & dưỡng lá', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_panel_giai_doan_hien_tai.webp' }
      ],
      '2026-10-28': [
        { startSec: 25200, endSec: 57600, label: '07:00–16:00 · Quan sát sinh trưởng mô sầu riêng Musang King', thumb: 'assets/view360/panorama_vuon_sau_rieng.jpg' }
      ]
    }
  },
  {
    id: 'CAM-IAKO-04',
    name: 'CAM 04 — Phân khu Cà phê 2 & Nhà điều hành Vật tư',
    area: 'Khu Cà phê Robusta 2 (#LOT-IAKO-03)',
    status: 'online',
    resolution: '1080p Full HD · 25fps',
    rtspUrl: 'rtsp://stream.farmblock.vn:8554/mangyang2/cam04',
    hlsUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    snapshotUrl: 'assets/view360/panorama_vuon_ca_phe_2.jpg',
    thumbUrl: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a3_housing.webp',
    segmentsByDate: {
      '2026-10-30': [
        { startSec: 25200, endSec: 45000, label: '07:00–12:30 · Xuất vật tư phân bón hữu cơ từ Kho trung tâm', thumb: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a3_housing.webp' },
        { startSec: 52200, endSec: 68400, label: '14:30–19:00 · Chăm sóc hàng cà phê Đa thân Phân khu B', thumb: 'assets/view360/panorama_vuon_ca_phe_2.jpg' }
      ],
      '2026-10-29': [
        { startSec: 28800, endSec: 50400, label: '08:00–14:00 · Kiểm tra định kỳ đường ống nhánh Phân khu B', thumb: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp' }
      ],
      '2026-10-28': [
        { startSec: 30600, endSec: 55800, label: '08:30–15:30 · Giám sát tổng thể Phân khu Cà phê 2', thumb: 'assets/view360/panorama_vuon_ca_phe_2.jpg' }
      ]
    }
  }
];

const FB_CARE_LOGS = [
  {
    id: 'LOG-101',
    plotId: 'LOT-IAKO-01',
    bucket: 'today',
    bucketLabel: 'Hôm nay',
    dateStr: '30/10/2026 · 08:30',
    category: 'Tưới nước & Châm phân',
    title: 'Tưới nhỏ giọt định kỳ & Kiểm tra áp suất béc tưới Phân khu A',
    technician: 'KS. Trần Minh Đức',
    stageKey: 'young',
    materials: 'Nước hồ trung tâm (pH 6.4, EC 380 µS/cm) · Lưu lượng 14.8 m³/h',
    notes: 'Vận hành hệ thống tưới nhỏ giọt bù áp trong 45 phút. Độ ẩm đất vùng rễ tăng từ 56% lên 64.2%. Toàn bộ van điện từ và đường ống nhánh hoạt động ổn định.',
    image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp'
  },
  {
    id: 'LOG-102',
    plotId: 'LOT-IAKO-01',
    bucket: 'yesterday',
    bucketLabel: 'Hôm qua',
    dateStr: '29/10/2026 · 15:10',
    category: 'Bón phân & Cải tạo đất',
    title: 'Bón phân hữu cơ vi sinh & Đo kiểm tra pH đất tầng mặt',
    technician: 'KS. Nguyễn Hoàng Nam',
    stageKey: 'young',
    materials: 'Phân hữu cơ vi sinh Humic · 2.5 kg/gốc',
    notes: 'Bón bổ sung quanh tán kết hợp kiểm tra bạt phủ gốc chống cỏ. Kết quả đo cảm biến đất: pH 6.1 (Tối ưu), N 145 mg/kg, P 68 mg/kg, K 192 mg/kg.',
    image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b4_mulch.webp'
  },
  {
    id: 'LOG-103',
    plotId: 'LOT-IAKO-01',
    bucket: 'older',
    bucketLabel: 'Cũ hơn',
    dateStr: '24/10/2026 · 09:15',
    category: 'Tỉa cành & Tạo tán',
    title: 'Định hình kỹ thuật Đa thân hai thân & Thả ngọn có kiểm soát',
    technician: 'KS. Trần Minh Đức',
    stageKey: 'young',
    materials: 'Kéo cắt cành khử khuẩn + Keo liền sẹo sinh học',
    notes: 'Tỉa bỏ chồi vượt và cành tăm sát mặt đất để tập trung dinh dưỡng nuôi 2 thân chính khỏe mạnh, đảm bảo thông thoáng ánh sáng cho toàn bộ hàng 3m × 1m.',
    image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_panel_ky_thuat_da_than.webp'
  },
  {
    id: 'LOG-104',
    plotId: 'LOT-IAKO-02',
    bucket: 'today',
    bucketLabel: 'Hôm nay',
    dateStr: '30/10/2026 · 07:45',
    category: 'Bảo vệ thực vật & Chắn gió',
    title: 'Kiểm tra tường bạt chắn gió & Phun dưỡng cơi đọt Sầu riêng Musang King',
    technician: 'KS. Lê Văn Hùng',
    stageKey: 'young',
    materials: 'Amino Acid sinh học + Vi lượng Chelate Kẽm-Bo',
    notes: 'Cơi đọt thứ 4 phát triển đồng đều trên cả 2 gốc/hố. Hệ thống lưới chắn gió căng tốt, tốc độ gió nội khu duy trì 2.4 m/s.',
    image: 'assets/farmblock/src_assets_images_view360_vuon_sau_rieng_hotspot_a6_windbreak.webp'
  }
];

// =============================================================================
// RUNTIME STATE & HELPERS
// =============================================================================
const FB_STATE = {
  // 360 Panorama state
  gardenId: 'coffee',
  locale: 'vi',
  panoViewMode: '360', // '360' | 'panoee' | 'satellite'
  overviewCollapsed: false,
  autoRotate360: true,
  yawDeg: -15,
  pitchDeg: -8,
  fovDeg: 72,
  thermalNoticeVisible: true,
  activeModalHtml: '',

  // Land Plot Map state
  selectedPlotId: 'LOT-IAKO-01',
  landViewMode: 'map', // 'map' | 'list'
  mapStyleMode: 'satellite', // 'default' | 'satellite' | 'model3d' | 'aerial3d'
  plotSearchQuery: '',
  plotCropFilter: 'all',
  plotDetailTab: 'metrics', // 'metrics' | 'camera' | 'history' | 'info' | 'finance'

  // Camera Live VLC state
  selectedCameraId: 'CAM-IAKO-01',
  selectedDateKey: '2026-10-30',
  timelineSec: 30600, // 08:30:00
  isLive: true,
  isPlaying: true,
  isScrubbing: false,
  scrubSec: 30600,
  customStreamUrl: '',

  // IoT & Weather & History state
  metricGroupFilter: 'all', // 'all' | 'weather' | 'water' | 'soil'
  historyBucketFilter: 'all', // 'all' | 'today' | 'yesterday' | 'older'
  weatherForecast: [
    { day: 'Hôm nay', date: '30/10', icon: '⛅', tempMax: 29, tempMin: 21, rainProb: 15, rainMm: 1.2, windMs: 2.8, hum: 74 },
    { day: 'Thứ 7', date: '31/10', icon: '☀️', tempMax: 30, tempMin: 20, rainProb: 10, rainMm: 0.0, windMs: 2.5, hum: 68 },
    { day: 'Chủ nhật', date: '01/11', icon: '🌦️', tempMax: 27, tempMin: 19, rainProb: 65, rainMm: 8.4, windMs: 3.6, hum: 82 },
    { day: 'Thứ 2', date: '02/11', icon: '⛅', tempMax: 28, tempMin: 20, rainProb: 25, rainMm: 2.1, windMs: 3.0, hum: 75 },
    { day: 'Thứ 3', date: '03/11', icon: '☀️', tempMax: 29, tempMin: 20, rainProb: 10, rainMm: 0.0, windMs: 2.4, hum: 70 },
    { day: 'Thứ 4', date: '04/11', icon: '⛅', tempMax: 28, tempMin: 19, rainProb: 20, rainMm: 0.8, windMs: 2.7, hum: 73 },
    { day: 'Thứ 5', date: '05/11', icon: '🌦️', tempMax: 26, tempMin: 19, rainProb: 55, rainMm: 6.2, windMs: 3.2, hum: 80 }
  ],
  weatherSource: 'Open-Meteo (13.7563, 107.7519)'
};

let _fbPanoAnimId = null;
let _fbCamClockId = null;

function cleanupFarmBlockView() {
  if (_fbPanoAnimId) {
    cancelAnimationFrame(_fbPanoAnimId);
    _fbPanoAnimId = null;
  }
  if (_fbCamClockId) {
    clearInterval(_fbCamClockId);
    _fbCamClockId = null;
  }
}

function formatSecondsToHMS(sec) {
  const s = Math.max(0, Math.min(86399, Math.floor(sec)));
  const hh = String(Math.floor(s / 3600)).padStart(2, '0');
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

function findSegmentAtSecond(cam, dateKey, sec) {
  const list = (cam && cam.segmentsByDate && cam.segmentsByDate[dateKey]) || [];
  return list.find(seg => sec >= seg.startSec && sec <= seg.endSec) || null;
}

// Decompiled from module_1790.js: buildWeatherAiSuggestion
function buildWeatherAiSuggestion(forecastList, soilMoistureVal = 64.2, soilPhVal = 6.1) {
  const today = (forecastList && forecastList[0]) || { tempMax: 29, rainProb: 15, rainMm: 1.2, hum: 74 };
  const notes = [];
  if (today.rainProb >= 60 || today.rainMm >= 8) {
    notes.push(`Dự báo hôm nay có khả năng mưa cao (${today.rainProb}%, ~${today.rainMm} mm). Khuyến nghị tạm hoãn lịch tưới nhỏ giọt tự động và kiểm tra rãnh thoát nước cuối lô.`);
  } else if (today.tempMax >= 33) {
    notes.push(`Nhiệt độ ban ngày khá cao (${today.tempMax}°C). Cần duy trì tưới giữ ẩm tầng mặt vào sáng sớm (06:00–08:30) để giảm sốc nhiệt cho bộ rễ.`);
  } else {
    notes.push(`Thời tiết hôm nay thuận lợi (${today.tempMax}°C, độ ẩm KK ${today.hum}%, mưa nhẹ ${today.rainMm} mm), rất phù hợp cho quá trình quang hợp và nuôi cành dự trữ.`);
  }
  if (soilMoistureVal < 45) {
    notes.push(`Độ ẩm đất hiện tại (${soilMoistureVal}%) đang thấp hơn ngưỡng tối ưu — Đề xuất bật Trạm bơm 1 tưới bổ sung 35–45 phút.`);
  } else {
    notes.push(`Độ ẩm đất vùng rễ (${soilMoistureVal}%) và pH đất (${soilPhVal}) đang ở vùng Tối ưu. Duy trì chế độ chăm sóc định kỳ và kiểm tra độ thông thoáng tán cây.`);
  }
  return notes.join(' ');
}

let _openMeteoFetched = false;
async function fetchOpenMeteoForecast(lat = 13.7563, lng = 107.7519) {
  if (_openMeteoFetched) return;
  _openMeteoFetched = true;
  try {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max&timezone=Asia%2FBangkok`;
    const res = await fetch(url);
    if (!res.ok) return;
    const data = await res.json();
    if (data && data.daily && Array.isArray(data.daily.time)) {
      const daysVi = ['CN', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
      FB_STATE.weatherForecast = data.daily.time.slice(0, 7).map((iso, idx) => {
        const d = new Date(iso);
        const ddmm = `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
        const code = data.daily.weather_code[idx] || 0;
        const icon = code >= 61 ? '🌧️' : code >= 51 ? '🌦️' : code >= 2 ? '⛅' : '☀️';
        return {
          day: idx === 0 ? 'Hôm nay' : daysVi[d.getDay()],
          date: ddmm,
          icon,
          tempMax: Math.round(data.daily.temperature_2m_max[idx] ?? 29),
          tempMin: Math.round(data.daily.temperature_2m_min[idx] ?? 20),
          rainProb: Math.round(data.daily.precipitation_probability_max?.[idx] ?? 20),
          rainMm: Number((data.daily.precipitation_sum?.[idx] ?? 0).toFixed(1)),
          windMs: Number(((data.daily.wind_speed_10m_max?.[idx] ?? 10) / 3.6).toFixed(1)),
          hum: 72
        };
      });
      FB_STATE.weatherSource = `Open-Meteo Live API (${lat.toFixed(4)}, ${lng.toFixed(4)})`;
      if (STATE.currentScreen === 'iot-care') {
        const container = document.getElementById('mainView');
        if (container) container.innerHTML = renderFarmBlockIotCare();
      }
    }
  } catch (_) {
    // Keep realistic fallback forecast
  }
}

// =============================================================================
// OVERVIEW HUB SECTION (Injected into SmartFarm #overview)
// =============================================================================
function renderFarmBlockOverviewHub() {
  return `
    <section class="fb-overview-hub">
      <div class="fb-hub-banner">
        <div class="fb-hub-banner-left">
          <div class="fb-hub-badges">
            <span class="fb-badge-lime">FARMBLOCK ONE 1.0.8 INTEGRATED</span>
            <span class="fb-badge-outline">MANG YANG 2 · Mã: ${FB_FARM_META.code}</span>
            <span class="fb-badge-outline">${FB_FARM_META.totalPlots} Khu trồng · ${FB_FARM_META.totalAreaM2.toLocaleString('vi-VN')} m²</span>
          </div>
          <h3 class="fb-hub-title">Cà phê Mang Yang &amp; Sầu riêng Musang King — Không gian Số hóa Nông trại 360°</h3>
          <p class="fb-hub-desc">
            Theo dõi hành trình phát triển của nông trại, khám phá thực địa WebGL 360° Panorama + Hotspot, Bản đồ đa giác lô đất Mapbox 3D, Camera Live VLC với Timeline 24h và 19 chỉ số IoT thực địa.
          </p>
        </div>
        <div class="fb-hub-banner-actions">
          <button class="fb-btn-gradient" onclick="navigateTo('panorama-360')">
            <span>🌐 Khám phá 360° Ngay</span>
          </button>
          <button class="fb-btn-ghost" onclick="navigateTo('land-plots')">
            <span>🗺️ Mở Bản đồ Lô đất</span>
          </button>
        </div>
      </div>

      <div class="fb-hub-cards-grid">
        <div class="fb-hub-card" onclick="navigateTo('panorama-360')">
          <div class="fb-hub-card-thumb" style="background-image:url('assets/farmblock/src_assets_images_img_home_360_card.jpg');">
            <span class="fb-hub-card-tag">WebGL 360° + Panoee</span>
          </div>
          <div class="fb-hub-card-body">
            <strong>1. WebGL 360° Panorama &amp; Hotspot</strong>
            <p>Khối cầu 360° cho 3 vườn (Cà Phê 1, Cà Phê 2, Sầu Riêng 1), 12 Hotspot 3D Pitch/Yaw &amp; Bảng 7 tiêu chí kỹ thuật.</p>
          </div>
        </div>

        <div class="fb-hub-card" onclick="navigateTo('land-plots')">
          <div class="fb-hub-card-thumb" style="background-image:url('assets/farmblock/src_assets_images_mapstyle_img_map_mode_terrain3d.png');">
            <span class="fb-hub-card-tag">4 Chế độ Bản đồ 3D</span>
          </div>
          <div class="fb-hub-card-body">
            <strong>2. Bản đồ Lô đất Mapbox &amp; QR Share</strong>
            <p>Đa giác lô đất (#00C832), 4 kiểu bản đồ (Mặc định, Vệ tinh, Model 3D, 3D trên cao), Carousel đồng bộ &amp; VietQR.</p>
          </div>
        </div>

        <div class="fb-hub-card" onclick="navigateTo('camera-live')">
          <div class="fb-hub-card-thumb" style="background-image:url('assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b5_road.webp');">
            <span class="fb-hub-card-tag">● LIVE &amp; 24h Strip</span>
          </div>
          <div class="fb-hub-card-body">
            <strong>3. Camera Live VLC &amp; Timeline 24h</strong>
            <p>Phát trực tiếp RTSP/HLS/Snapshot, thước kéo Timeline 24h có vạch đỏ trung tâm, Scrub Preview &amp; Toàn màn hình.</p>
          </div>
        </div>

        <div class="fb-hub-card" onclick="navigateTo('iot-care')">
          <div class="fb-hub-card-thumb" style="background-image:url('assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp');">
            <span class="fb-hub-card-tag">19 Chỉ số IoT &amp; AI</span>
          </div>
          <div class="fb-hub-card-body">
            <strong>4. Luồng IoT &amp; Nhật ký Canh tác</strong>
            <p>19 chỉ số Thời tiết/Nước/Đất kèm thanh RangeTrack, Dự báo Open-Meteo 7 ngày, AI Nông học &amp; 3 giai đoạn sinh trưởng.</p>
          </div>
        </div>
      </div>
    </section>
  `;
}

// =============================================================================
// SUITE 1: WEBGL 360° PANORAMA + HOTSPOT + OVERVIEW PANEL + PANOEE TOUR
// (Ported from module_1454.js, module_1589.js, module_1260.js, module_1738.js)
// =============================================================================
function renderFarmBlockPanorama360() {
  const garden = FB_GARDENS[FB_STATE.gardenId] || FB_GARDENS.coffee;
  const loc = FB_STATE.locale;
  const items = garden.overviewItems || FB_GARDENS.coffee.overviewItems;

  return `
    <div class="fb-pano-workspace">
      <!-- Top Control Bar -->
      <div class="fb-pano-topbar">
        <div class="fb-pano-title-group">
          <div class="fb-pano-garden-tabs">
            ${Object.values(FB_GARDENS).map(g => `
              <button class="fb-garden-tab ${g.id === garden.id ? 'active' : ''}" onclick="fbSelectGarden('${g.id}')">
                <span>${g.title[loc]}</span>
                <small>${g.areaBadge}</small>
              </button>
            `).join('')}
          </div>
        </div>

        <div class="fb-pano-controls-right">
          <div class="fb-mode-pill-group">
            <button class="fb-mode-btn ${FB_STATE.panoViewMode === '360' ? 'active' : ''}" onclick="fbSetPanoMode('360')">
              🌐 360° WebGL
            </button>
            <button class="fb-mode-btn ${FB_STATE.panoViewMode === 'panoee' ? 'active' : ''}" onclick="fbSetPanoMode('panoee')">
              🧭 Phân khu Panoee
            </button>
            <button class="fb-mode-btn ${FB_STATE.panoViewMode === 'satellite' ? 'active' : ''}" onclick="fbSetPanoMode('satellite')">
              🛰️ Vệ tinh 2D
            </button>
          </div>

          <button class="fb-lang-btn" onclick="fbToggleLocale()" title="Đổi ngôn ngữ VI / EN">
            <img src="assets/farmblock/${loc === 'vi' ? 'src_assets_icons_view360_flagvi.png' : 'src_assets_icons_view360_flagen.png'}" alt="${loc}" width="18" height="18">
            <span>${loc.toUpperCase()}</span>
          </button>
        </div>
      </div>

      ${FB_STATE.thermalNoticeVisible ? `
        <div class="fb-thermal-banner">
          <span>⚠️ <strong>${loc === 'vi' ? 'Lưu ý đồ họa 360°:' : '360° Graphics Notice:'}</strong> ${
            loc === 'vi'
              ? 'Chế độ xem 360° sử dụng kết xuất đồ họa thời gian thực. Kéo chuột hoặc vuốt để xoay góc nhìn 360°, cuộn chuột để phóng to/thu nhỏ (FOV 30°–100°), chạm vào nhãn Hotspot trên cảnh để xem chi tiết.'
              : 'Drag to rotate 360°, scroll to zoom FOV (30°–100°), and click any 3D hotspot or Overview item to inspect technical details.'
          }</span>
          <button onclick="FB_STATE.thermalNoticeVisible=false; document.querySelector('.fb-thermal-banner')?.remove();">✕</button>
        </div>
      ` : ''}

      <!-- Main 360 Viewport -->
      <div class="fb-pano-stage" id="fbPanoStage">
        ${FB_STATE.panoViewMode === 'panoee' ? `
          <div class="fb-panoee-container">
            <iframe
              src="https://tour.panoee.net/iframe/6a6b3b93981c83000850b978"
              class="fb-panoee-iframe"
              allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen"
              allowfullscreen
              title="Panoee 360 Tour — Vườn cà phê Ia Ko"
            ></iframe>
            <div class="fb-panoee-overlay-badge">
              <span>📍 Phân khu: <strong>Vườn cà phê Ia Ko</strong> (Panoee 360° Virtual Tour)</span>
              <button class="fb-btn-small" onclick="fbSetPanoMode('360')">Quay lại WebGL 360° Nội bộ</button>
            </div>
          </div>
        ` : FB_STATE.panoViewMode === 'satellite' ? `
          <div class="fb-sat2d-container" style="background-image:url('assets/farmblock/src_assets_images_img_map_satellite.png');">
            <div class="fb-sat2d-header-pill">
              <strong>FARMBLOCK IAKO · Bản đồ Vệ tinh Phân khu</strong>
              <span>Tọa độ: 13.7563°N, 107.7519°E · ${garden.location[loc]}</span>
            </div>
            ${garden.hotspots.map((hs, idx) => {
              const positions = [
                { left: '28%', top: '36%' },
                { left: '54%', top: '28%' },
                { left: '66%', top: '56%' },
                { left: '38%', top: '68%' },
                { left: '74%', top: '34%' },
                { left: '22%', top: '52%' },
                { left: '48%', top: '48%' },
                { left: '60%', top: '72%' }
              ];
              const pos = positions[idx % positions.length];
              return `
                <button class="fb-sat-marker" style="left:${pos.left}; top:${pos.top};" onclick="fbOpenPanoDetailModal('hotspot', '${hs.id}')">
                  <span class="fb-sat-marker-dot">🌱</span>
                  <span class="fb-sat-marker-label">${hs.label[loc]}</span>
                </button>
              `;
            }).join('')}
          </div>
        ` : `
          <canvas id="fbPanoramaCanvas" class="fb-pano-canvas"></canvas>

          <!-- Projected 3D Hotspots Layer -->
          <div id="fbHotspotLayer" class="fb-hotspot-layer">
            ${garden.hotspots.map(hs => `
              <button
                id="fb-hs-${hs.id}"
                class="fb-hotspot-pin"
                onclick="fbOpenPanoDetailModal('hotspot', '${hs.id}')"
                title="${hs.label[loc]}"
              >
                <span class="fb-hs-pulse"></span>
                <span class="fb-hs-dot">●</span>
                <span class="fb-hs-text">${hs.label[loc]}</span>
              </button>
            `).join('')}
          </div>

          <!-- Top-right HUD Controls inside 360 -->
          <div class="fb-pano-hud-right">
            <button class="fb-hud-btn ${FB_STATE.autoRotate360 ? 'active' : ''}" onclick="fbToggleAutoRotate()" title="Tự động xoay 360°">
              ${FB_STATE.autoRotate360 ? '⏸ Dừng xoay' : '▶ Tự xoay 360°'}
            </button>
            <button class="fb-hud-btn" onclick="fbZoomPano(-10)" title="Phóng to">+</button>
            <button class="fb-hud-btn" onclick="fbZoomPano(10)" title="Thu nhỏ">−</button>
            <button class="fb-hud-btn" onclick="fbResetPanoView()" title="Đặt lại góc nhìn">⟲ Góc chuẩn</button>
            <div class="fb-hud-telemetry" id="fbPanoTelemetry">
              Yaw: ${Math.round(FB_STATE.yawDeg)}° · Pitch: ${Math.round(FB_STATE.pitchDeg)}° · FOV: ${Math.round(FB_STATE.fovDeg)}°
            </div>
          </div>

          <!-- Left Floating OverviewPanel (Decompiled from module_1589.js) -->
          <div class="fb-overview-panel ${FB_STATE.overviewCollapsed ? 'collapsed' : ''}">
            <div class="fb-ov-header" onclick="fbToggleOverviewPanel()">
              <div>
                <div class="fb-ov-eyebrow">${loc === 'vi' ? 'TỔNG QUAN KỸ THUẬT VƯỜN' : 'ORCHARD TECHNICAL OVERVIEW'}</div>
                <strong class="fb-ov-garden-name">${garden.title[loc]}</strong>
              </div>
              <button class="fb-ov-toggle-btn">${FB_STATE.overviewCollapsed ? 'Mở rộng ▾' : 'Thu gọn ▴'}</button>
            </div>
            ${!FB_STATE.overviewCollapsed ? `
              <div class="fb-ov-location">📍 ${garden.location[loc]}</div>
              <div class="fb-ov-list">
                ${items.map((item, idx) => `
                  <div class="fb-ov-item" onclick="fbOpenPanoDetailModal('overview', '${item.id}')">
                    <img src="${item.image}" alt="${item.label[loc]}" class="fb-ov-item-thumb">
                    <div class="fb-ov-item-text">
                      <span class="fb-ov-item-idx">0${idx + 1}</span>
                      <strong>${item.label[loc]}</strong>
                    </div>
                    <span class="fb-ov-item-arrow">›</span>
                  </div>
                `).join('')}
              </div>
            ` : ''}
          </div>

          <!-- Bottom Quick Hotspot Bar -->
          <div class="fb-pano-bottom-strip">
            <span class="fb-strip-label">${loc === 'vi' ? 'Điểm Hotspot 3D trong cảnh:' : '3D Scene Hotspots:'}</span>
            <div class="fb-strip-chips">
              ${garden.hotspots.map(hs => `
                <button class="fb-strip-chip" onclick="fbFocusHotspot('${hs.id}')">
                  🎯 ${hs.label[loc]}
                </button>
              `).join('')}
            </div>
          </div>
        `}
      </div>

      <!-- Detail Modal for Hotspot / Overview Item -->
      <dialog id="fbPanoDetailDialog" class="figma-modal">
        <div class="fb-modal-card" id="fbPanoDetailModalBody"></div>
      </dialog>
    </div>
  `;
}

// 3D Spherical Projection & Equirectangular Panorama Renderer (module_1454.js)
function mountFarmBlockPanorama360() {
  cleanupFarmBlockView();
  if (FB_STATE.panoViewMode !== '360') return;

  const canvas = document.getElementById('fbPanoramaCanvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const garden = FB_GARDENS[FB_STATE.gardenId] || FB_GARDENS.coffee;
  const loc = FB_STATE.locale;

  const panoImg = new Image();
  panoImg.src = garden.panoramaUrl;

  let isDragging = false;
  let lastX = 0;
  let lastY = 0;
  let velYaw = 0;
  let velPitch = 0;

  function resizeCanvas() {
    const rect = canvas.parentElement.getBoundingClientRect();
    canvas.width = Math.max(640, Math.floor(rect.width));
    canvas.height = Math.max(420, Math.floor(rect.height));
  }
  resizeCanvas();

  canvas.addEventListener('pointerdown', (e) => {
    isDragging = true;
    FB_STATE.autoRotate360 = false;
    lastX = e.clientX;
    lastY = e.clientY;
    velYaw = 0;
    velPitch = 0;
    canvas.setPointerCapture(e.pointerId);
  });

  canvas.addEventListener('pointermove', (e) => {
    if (!isDragging) return;
    const dx = e.clientX - lastX;
    const dy = e.clientY - lastY;
    lastX = e.clientX;
    lastY = e.clientY;
    const sensitivity = FB_STATE.fovDeg / 450;
    velYaw = -dx * sensitivity;
    velPitch = dy * sensitivity;
    FB_STATE.yawDeg = ((FB_STATE.yawDeg + velYaw + 540) % 360) - 180;
    FB_STATE.pitchDeg = Math.max(-75, Math.min(75, FB_STATE.pitchDeg + velPitch));
  });

  const stopDrag = () => { isDragging = false; };
  canvas.addEventListener('pointerup', stopDrag);
  canvas.addEventListener('pointercancel', stopDrag);

  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    FB_STATE.fovDeg = Math.max(30, Math.min(100, FB_STATE.fovDeg + (e.deltaY > 0 ? 4 : -4)));
  }, { passive: false });

  function renderFrame() {
    if (!document.getElementById('fbPanoramaCanvas')) return;

    if (!isDragging) {
      if (FB_STATE.autoRotate360) {
        FB_STATE.yawDeg = ((FB_STATE.yawDeg + 0.12 + 540) % 360) - 180;
      } else if (Math.abs(velYaw) > 0.01 || Math.abs(velPitch) > 0.01) {
        FB_STATE.yawDeg = ((FB_STATE.yawDeg + velYaw + 540) % 360) - 180;
        FB_STATE.pitchDeg = Math.max(-75, Math.min(75, FB_STATE.pitchDeg + velPitch));
        velYaw *= 0.92; // VELOCITY_DECAY = 0.92 from module_1454.js
        velPitch *= 0.92;
      }
    }

    const w = canvas.width;
    const h = canvas.height;
    ctx.fillStyle = '#06280C';
    ctx.fillRect(0, 0, w, h);

    if (panoImg.complete && panoImg.naturalWidth > 0) {
      const iw = panoImg.naturalWidth;
      const ih = panoImg.naturalHeight;
      const fovRatio = FB_STATE.fovDeg / 360;
      const vFovRatio = Math.min(0.85, (FB_STATE.fovDeg * (h / w)) / 180);

      const cropW = iw * fovRatio;
      const cropH = ih * vFovRatio;

      // Normalize yaw (-180..180) to (0..1)
      const normYaw = ((FB_STATE.yawDeg + 180) % 360 + 360) % 360 / 360;
      // Normalize pitch (-75..75) to (0..1) inverted for image Y
      const normPitch = 0.5 - (FB_STATE.pitchDeg / 180);

      let sx = normYaw * iw - cropW / 2;
      let sy = Math.max(0, Math.min(ih - cropH, normPitch * ih - cropH / 2));

      if (sx < 0) {
        const leftPart = -sx;
        const leftDestW = (leftPart / cropW) * w;
        ctx.drawImage(panoImg, iw - leftPart, sy, leftPart, cropH, 0, 0, leftDestW, h);
        ctx.drawImage(panoImg, 0, sy, cropW - leftPart, cropH, leftDestW, 0, w - leftDestW, h);
      } else if (sx + cropW > iw) {
        const rightPart = iw - sx;
        const rightDestW = (rightPart / cropW) * w;
        ctx.drawImage(panoImg, sx, sy, rightPart, cropH, 0, 0, rightDestW, h);
        ctx.drawImage(panoImg, 0, sy, cropW - rightPart, cropH, rightDestW, 0, w - rightDestW, h);
      } else {
        ctx.drawImage(panoImg, sx, sy, cropW, cropH, 0, 0, w, h);
      }

      // Subtle vignette for depth
      const grad = ctx.createRadialGradient(w / 2, h / 2, h * 0.35, w / 2, h / 2, w * 0.7);
      grad.addColorStop(0, 'rgba(0,0,0,0)');
      grad.addColorStop(1, 'rgba(6,40,12,0.38)');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
    } else {
      ctx.fillStyle = '#D0EEB5';
      ctx.font = '600 15px "Plus Jakarta Sans", sans-serif';
      ctx.fillText('Đang tải không gian 360° Panorama...', w / 2 - 130, h / 2);
    }

    // Project 3D Hotspots (pitch/yaw -> screen X/Y)
    const hFov = FB_STATE.fovDeg;
    const vFov = hFov * (h / w);
    for (const hs of garden.hotspots) {
      const el = document.getElementById(`fb-hs-${hs.id}`);
      if (!el) continue;
      let dYaw = ((hs.yaw - FB_STATE.yawDeg + 540) % 360) - 180;
      let dPitch = hs.pitch - FB_STATE.pitchDeg;
      if (Math.abs(dYaw) <= hFov * 0.58 && Math.abs(dPitch) <= vFov * 0.58) {
        const screenX = (0.5 + dYaw / hFov) * w;
        const screenY = (0.5 - dPitch / vFov) * h;
        el.style.display = 'inline-flex';
        el.style.left = `${Math.round(screenX)}px`;
        el.style.top = `${Math.round(screenY)}px`;
      } else {
        el.style.display = 'none';
      }
    }

    const hudEl = document.getElementById('fbPanoTelemetry');
    if (hudEl) {
      hudEl.textContent = `Yaw: ${Math.round(FB_STATE.yawDeg)}° · Pitch: ${Math.round(FB_STATE.pitchDeg)}° · FOV: ${Math.round(FB_STATE.fovDeg)}°`;
    }

    _fbPanoAnimId = requestAnimationFrame(renderFrame);
  }

  _fbPanoAnimId = requestAnimationFrame(renderFrame);
}

function fbSelectGarden(gardenId) {
  FB_STATE.gardenId = gardenId;
  FB_STATE.yawDeg = -15;
  FB_STATE.pitchDeg = -8;
  const main = document.getElementById('mainView');
  if (main) {
    main.innerHTML = renderFarmBlockPanorama360();
    mountFarmBlockPanorama360();
  }
}

function fbSetPanoMode(mode) {
  FB_STATE.panoViewMode = mode;
  const main = document.getElementById('mainView');
  if (main) {
    main.innerHTML = renderFarmBlockPanorama360();
    mountFarmBlockPanorama360();
  }
}

function fbToggleLocale() {
  FB_STATE.locale = FB_STATE.locale === 'vi' ? 'en' : 'vi';
  const main = document.getElementById('mainView');
  if (main) {
    main.innerHTML = renderFarmBlockPanorama360();
    mountFarmBlockPanorama360();
  }
}

function fbToggleOverviewPanel() {
  FB_STATE.overviewCollapsed = !FB_STATE.overviewCollapsed;
  const main = document.getElementById('mainView');
  if (main) {
    main.innerHTML = renderFarmBlockPanorama360();
    mountFarmBlockPanorama360();
  }
}

function fbToggleAutoRotate() {
  FB_STATE.autoRotate360 = !FB_STATE.autoRotate360;
  const main = document.getElementById('mainView');
  if (main) {
    main.innerHTML = renderFarmBlockPanorama360();
    mountFarmBlockPanorama360();
  }
}

function fbZoomPano(delta) {
  FB_STATE.fovDeg = Math.max(30, Math.min(100, FB_STATE.fovDeg + delta));
}

function fbResetPanoView() {
  FB_STATE.yawDeg = -15;
  FB_STATE.pitchDeg = -8;
  FB_STATE.fovDeg = 72;
  FB_STATE.autoRotate360 = false;
}

function fbFocusHotspot(hsId) {
  const garden = FB_GARDENS[FB_STATE.gardenId] || FB_GARDENS.coffee;
  const hs = garden.hotspots.find(h => h.id === hsId);
  if (!hs) return;
  FB_STATE.autoRotate360 = false;
  FB_STATE.yawDeg = hs.yaw;
  FB_STATE.pitchDeg = hs.pitch;
  fbOpenPanoDetailModal('hotspot', hsId);
}

function fbOpenPanoDetailModal(type, id) {
  const garden = FB_GARDENS[FB_STATE.gardenId] || FB_GARDENS.coffee;
  const loc = FB_STATE.locale;
  const dialog = document.getElementById('fbPanoDetailDialog');
  const body = document.getElementById('fbPanoDetailModalBody');
  if (!dialog || !body) return;

  let item = null;
  if (type === 'hotspot') {
    item = garden.hotspots.find(h => h.id === id);
  } else {
    const list = garden.overviewItems || FB_GARDENS.coffee.overviewItems;
    item = list.find(x => x.id === id);
  }
  if (!item) return;

  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">${type === 'hotspot' ? (item.category || 'Hotspot 360°') : 'Tiêu chuẩn Kỹ thuật FarmBlock'}</span>
        <h3 style="margin:6px 0 0; color:#06280C; font-size:18px;">${item.label[loc]}</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbPanoDetailDialog').close()">✕</button>
    </div>
    <div class="fb-modal-content">
      <img src="${item.image}" alt="${item.label[loc]}" class="fb-modal-hero-img">
      <p class="fb-modal-desc">${item.detail[loc]}</p>
      <div class="fb-modal-meta-row">
        <span>🌱 Khu vườn: <strong>${garden.title[loc]}</strong></span>
        ${type === 'hotspot' ? `<span>🎯 Tọa độ góc 360°: <strong>Pitch ${item.pitch}° · Yaw ${item.yaw}°</strong></span>` : ''}
      </div>
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

// =============================================================================
// SUITE 2: BẢN ĐỒ LÔ ĐẤT MAPBOX & DANH MỤC KHU ĐẤT (#land-plots)
// (Ported from module_0945.js, module_0963.js, module_1251..1256.js, module_1767..1769.js)
// =============================================================================
function getFilteredFarmBlockPlots() {
  const q = (FB_STATE.plotSearchQuery || '').trim().toLowerCase();
  const cropF = FB_STATE.plotCropFilter;
  return FB_PLOTS.filter(p => {
    if (cropF === 'coffee' && !p.crop.toLowerCase().includes('cà phê')) return false;
    if (cropF === 'durian' && !p.crop.toLowerCase().includes('sầu riêng')) return false;
    if (!q) return true;
    return (
      p.name.toLowerCase().includes(q) ||
      p.code.toLowerCase().includes(q) ||
      p.crop.toLowerCase().includes(q) ||
      p.location.toLowerCase().includes(q)
    );
  });
}

function renderFarmBlockLandPlots() {
  const filtered = getFilteredFarmBlockPlots();
  const selectedPlot = FB_PLOTS.find(p => p.id === FB_STATE.selectedPlotId) || FB_PLOTS[0];
  const styleModes = [
    { key: 'default', label: 'Mặc định', desc: 'Bản đồ đường phố & ranh giới 2D', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_default.png', bgImg: 'assets/farmblock/src_assets_images_img_map_default.png' },
    { key: 'satellite', label: 'Vệ tinh', desc: 'Ảnh vệ tinh độ phân giải cao', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_satellite.png', bgImg: 'assets/farmblock/src_assets_images_img_map_satellite.png' },
    { key: 'model3d', label: 'Bản đồ Model 3D', desc: 'Địa hình DEM 3D nổi (Pitch 60°)', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_terrain3d.png', bgImg: 'assets/farmblock/src_assets_images_img_map_satellite.png' },
    { key: 'aerial3d', label: 'Góc nhìn 3D trên cao', desc: 'Vệ tinh 3D nghiêng (Pitch 45°)', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_birdseye3d.png', bgImg: 'assets/farmblock/src_assets_images_img_map_satellite.png' }
  ];
  const currentStyle = styleModes.find(s => s.key === FB_STATE.mapStyleMode) || styleModes[1];

  return `
    <div class="fb-land-workspace">
      <!-- Search, Crop Filter & Mode Switch Bar -->
      <div class="fb-land-toolbar">
        <div class="fb-search-box">
          <img src="assets/farmblock/src_assets_icons_ic_search.png" alt="Tìm" width="16" height="16">
          <input
            type="search"
            placeholder="Tìm khu đất theo tên, mã lô (LOT-IAKO-01), giống cây..."
            value="${FB_STATE.plotSearchQuery}"
            oninput="fbUpdatePlotSearch(this.value)"
          >
        </div>

        <div class="fb-crop-chips">
          <button class="fb-chip-btn ${FB_STATE.plotCropFilter === 'all' ? 'active' : ''}" onclick="fbSetPlotCropFilter('all')">
            Tất cả (${FB_PLOTS.length})
          </button>
          <button class="fb-chip-btn ${FB_STATE.plotCropFilter === 'coffee' ? 'active' : ''}" onclick="fbSetPlotCropFilter('coffee')">
            ☕ Cà phê Robusta (4)
          </button>
          <button class="fb-chip-btn ${FB_STATE.plotCropFilter === 'durian' ? 'active' : ''}" onclick="fbSetPlotCropFilter('durian')">
            🍈 Sầu riêng Musang King (2)
          </button>
        </div>

        <div class="fb-land-actions-right">
          <div class="fb-mode-pill-group">
            <button class="fb-mode-btn ${FB_STATE.landViewMode === 'map' ? 'active' : ''}" onclick="fbSetLandViewMode('map')">
              🗺️ Bản đồ Đa giác
            </button>
            <button class="fb-mode-btn ${FB_STATE.landViewMode === 'list' ? 'active' : ''}" onclick="fbSetLandViewMode('list')">
              📋 Danh sách (${filtered.length})
            </button>
          </div>
          <button class="fb-btn-outline-green" onclick="fbOpenFarmInfoDialog()">
            ⓘ TT Nông trại
          </button>
        </div>
      </div>

      ${FB_STATE.landViewMode === 'map' ? `
        <!-- MAP MODE: Interactive Polygon Map + Floating Action Buttons + Bottom Carousel -->
        <div class="fb-map-stage mode-${FB_STATE.mapStyleMode}">
          <div class="fb-map-canvas-wrap" style="background-image:url('${currentStyle.bgImg}');">
            <div class="fb-map-3d-plane ${FB_STATE.mapStyleMode === 'model3d' ? 'tilt-60' : FB_STATE.mapStyleMode === 'aerial3d' ? 'tilt-45' : ''}">
              <svg class="fb-map-svg" viewBox="0 0 720 380" preserveAspectRatio="xMidYMid slice">
                <defs>
                  <linearGradient id="fbSelectedFill" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stop-color="#00DC3C" stop-opacity="0.62"/>
                    <stop offset="100%" stop-color="#008242" stop-opacity="0.52"/>
                  </linearGradient>
                  <filter id="fbPolyGlow">
                    <feDropShadow dx="0" dy="4" stdDeviation="4" flood-color="#06280C" flood-opacity="0.45"/>
                  </filter>
                </defs>

                <!-- Internal Farm Roads -->
                <path d="M 60 192 L 650 192" stroke="rgba(255,255,255,0.55)" stroke-width="5" stroke-dasharray="10 6" fill="none"/>
                <path d="M 256 40 L 268 345" stroke="rgba(255,255,255,0.45)" stroke-width="4" fill="none"/>
                <path d="M 455 40 L 466 345" stroke="rgba(255,255,255,0.45)" stroke-width="4" fill="none"/>

                <!-- Plot Polygons (Exact FarmBlock colors: #00C832 fill, #009E50 / #61BD1B stroke) -->
                ${filtered.map(plot => {
                  const isSel = plot.id === selectedPlot.id;
                  const pts = plot.polygonSvg.split(' ').map(pair => pair.split(',').map(Number));
                  const cx = Math.round(pts.reduce((s, p) => s + p[0], 0) / pts.length);
                  const cy = Math.round(pts.reduce((s, p) => s + p[1], 0) / pts.length);
                  return `
                    <g class="fb-plot-poly-group" onclick="fbSelectPlot('${plot.id}')" style="cursor:pointer;">
                      <polygon
                        points="${plot.polygonSvg}"
                        fill="${isSel ? 'url(#fbSelectedFill)' : 'rgba(0, 200, 50, 0.35)'}"
                        stroke="${isSel ? '#61BD1B' : '#009E50'}"
                        stroke-width="${isSel ? '3.5' : '2'}"
                        filter="${isSel ? 'url(#fbPolyGlow)' : 'none'}"
                      />
                      <rect x="${cx - 48}" y="${cy - 20}" width="96" height="24" rx="6" fill="${isSel ? '#003F20' : 'rgba(6,40,12,0.82)'}" stroke="${isSel ? '#61BD1B' : '#009E50'}" stroke-width="1.2"/>
                      <text x="${cx}" y="${cy - 4}" text-anchor="middle" fill="#FFFFFF" font-size="11" font-weight="700">${plot.code}</text>
                      <text x="${cx}" y="${cy + 18}" text-anchor="middle" fill="#D0EEB5" font-size="10" font-weight="600">${plot.areaM2.toLocaleString('vi-VN')} m²</text>
                    </g>
                  `;
                }).join('')}
              </svg>
            </div>

            <!-- Top-left Farm Coordinates Badge -->
            <div class="fb-map-coord-pill">
              <strong>🌱 ${FB_FARM_META.name}</strong>
              <span>Chế độ: <b>${currentStyle.label}</b> · Tọa độ: ${selectedPlot.lat.toFixed(4)}°N, ${selectedPlot.lng.toFixed(4)}°E · Cao độ: ${selectedPlot.elevationM}m</span>
            </div>

            <!-- Right Floating Map Controls (module_0963.js) -->
            <div class="fb-map-fab-col">
              <button class="fb-map-fab" onclick="fbOpenMapStyleSheet()" title="Chọn loại bản đồ">
                🗂️ <span>Kiểu bản đồ</span>
              </button>
              <button class="fb-map-fab" onclick="fbOpenFarmInfoDialog()" title="Thông tin nông trại">
                ⓘ <span>Thông tin</span>
              </button>
              <button class="fb-map-fab" onclick="fbOpenPlotShareDialog('${selectedPlot.id}')" title="Chia sẻ mã QR">
                🔗 <span>Mã QR</span>
              </button>
              <button class="fb-map-fab" onclick="fbOpenRenamePlotDialog('${selectedPlot.id}')" title="Đổi tên khu đất">
                ✏️ <span>Đổi tên</span>
              </button>
            </div>
          </div>

          <!-- Bottom Synchronized LandPlotCarouselCard Strip (module_1251.js) -->
          <div class="fb-carousel-strip">
            ${filtered.map(plot => {
              const isSel = plot.id === selectedPlot.id;
              return `
                <div class="fb-carousel-card ${isSel ? 'active' : ''}" onclick="fbSelectPlot('${plot.id}')">
                  <div class="fb-cc-top">
                    <span class="fb-cc-code">${plot.code}</span>
                    <span class="fb-cc-status">${plot.status}</span>
                  </div>
                  <strong class="fb-cc-title">${plot.name}</strong>
                  <div class="fb-cc-meta">
                    <span>🌱 ${plot.crop}</span>
                    <span>📐 ${plot.areaM2.toLocaleString('vi-VN')} m² (${plot.treesCount} cây)</span>
                  </div>
                  <div class="fb-cc-actions">
                    <button class="fb-cc-btn-share" onclick="event.stopPropagation(); fbOpenPlotShareDialog('${plot.id}')">
                      🔗 QR Chia sẻ
                    </button>
                    <button class="fb-cc-btn-detail" onclick="event.stopPropagation(); fbSelectPlot('${plot.id}'); document.getElementById('fbPlotDetailWorkspace')?.scrollIntoView({behavior:'smooth'});">
                      Xem 5 Tab Chi tiết →
                    </button>
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      ` : `
        <!-- LIST MODE: LandPlotListCard Grid (module_1256.js) -->
        <div class="fb-plot-list-grid">
          ${filtered.map(plot => `
            <div class="fb-plot-list-card ${plot.id === selectedPlot.id ? 'active' : ''}" onclick="fbSelectPlot('${plot.id}')">
              <div class="fb-plc-thumb" style="background-image:url('assets/farmblock/src_assets_images_img_plot_of_land.png');">
                <span class="fb-plc-badge">${plot.status}</span>
                <span class="fb-plc-nft">${plot.nftTokenId}</span>
              </div>
              <div class="fb-plc-body">
                <div class="fb-plc-code-row">
                  <strong>${plot.code}</strong>
                  <span>Cao độ ${plot.elevationM}m · Dốc ${plot.slopeDeg}°</span>
                </div>
                <h4>${plot.name}</h4>
                <p class="fb-plc-loc">📍 ${plot.location}</p>
                <div class="fb-plc-specs">
                  <div><small>Giống cây</small><strong>${plot.crop}</strong></div>
                  <div><small>Diện tích</small><strong>${plot.areaM2.toLocaleString('vi-VN')} m²</strong></div>
                  <div><small>Mật độ</small><strong>${plot.spacing}</strong></div>
                </div>
                <div class="fb-plc-footer">
                  <button class="fb-btn-small-outline" onclick="event.stopPropagation(); fbOpenPlotShareDialog('${plot.id}')">🔗 Mã QR</button>
                  <button class="fb-btn-small-outline" onclick="event.stopPropagation(); fbOpenRenamePlotDialog('${plot.id}')">✏️ Đổi tên</button>
                  <button class="fb-btn-small-primary" onclick="event.stopPropagation(); fbSelectPlot('${plot.id}'); FB_STATE.landViewMode='map'; fbRerenderLandPlots();">Trên Bản đồ →</button>
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      `}

      <!-- 5-TAB PLOT DETAIL ACTION WORKSPACE (Decompiled from PlotDetailActionBar module_1769.js) -->
      <div class="fb-plot-detail-workspace" id="fbPlotDetailWorkspace">
        <div class="fb-pd-header">
          <div>
            <span class="fb-badge-lime">CHI TIẾT LÔ ĐẤT ĐANG CHỌN · ${selectedPlot.code}</span>
            <h3>${selectedPlot.name}</h3>
            <p>📍 ${selectedPlot.location} · Diện tích: <strong>${selectedPlot.areaM2.toLocaleString('vi-VN')} m²</strong> · Giống: <strong>${selectedPlot.crop}</strong></p>
          </div>
          <div class="fb-pd-tabs" role="tablist">
            <button class="fb-pd-tab ${FB_STATE.plotDetailTab === 'metrics' ? 'active' : ''}" onclick="fbSetPlotDetailTab('metrics')">📊 Thông số IoT</button>
            <button class="fb-pd-tab ${FB_STATE.plotDetailTab === 'camera' ? 'active' : ''}" onclick="fbSetPlotDetailTab('camera')">📹 Trực tiếp (VLC)</button>
            <button class="fb-pd-tab ${FB_STATE.plotDetailTab === 'history' ? 'active' : ''}" onclick="fbSetPlotDetailTab('history')">📓 Lịch sử</button>
            <button class="fb-pd-tab ${FB_STATE.plotDetailTab === 'info' ? 'active' : ''}" onclick="fbSetPlotDetailTab('info')">🌱 Thông tin &amp; Đất</button>
            <button class="fb-pd-tab ${FB_STATE.plotDetailTab === 'finance' ? 'active' : ''}" onclick="fbSetPlotDetailTab('finance')">💳 Tài chính VietQR</button>
          </div>
        </div>

        <div class="fb-pd-body">
          ${renderSelectedPlotTabContent(selectedPlot)}
        </div>
      </div>

      <!-- Dialogs for MapStyleSelectorSheet, FarmInfo, Rename, QR Share & Soil Certificate -->
      <dialog id="fbLandModal" class="figma-modal">
        <div class="fb-modal-card" id="fbLandModalBody"></div>
      </dialog>
    </div>
  `;
}

function renderSelectedPlotTabContent(plot) {
  if (FB_STATE.plotDetailTab === 'info') {
    return `
      <div class="fb-info-two-col">
        <div class="fb-info-card">
          <h4>📋 Hồ sơ Kỹ thuật &amp; Địa chất Lô đất (${plot.code})</h4>
          <div class="fb-kv-list">
            <div class="fb-kv-row"><span>Tên khu đất</span><strong>${plot.name}</strong></div>
            <div class="fb-kv-row"><span>Mã định danh NFT</span><strong>${plot.nftTokenId}</strong></div>
            <div class="fb-kv-row"><span>Giống cây trồng</span><strong>${plot.crop}</strong></div>
            <div class="fb-kv-row"><span>Mật độ &amp; Kỹ thuật</span><strong>${plot.spacing} (${plot.treesCount} cây)</strong></div>
            <div class="fb-kv-row"><span>Tọa độ tâm GPS</span><strong>${plot.lat.toFixed(4)}°N, ${plot.lng.toFixed(4)}°E</strong></div>
            <div class="fb-kv-row"><span>Cao độ / Độ dốc</span><strong>${plot.elevationM} m · Độ dốc ${plot.slopeDeg}°</strong></div>
            <div class="fb-kv-row"><span>Phân loại thổ nhưỡng</span><strong>${plot.soilType}</strong></div>
          </div>
          <div style="margin-top:14px; display:flex; gap:10px; flex-wrap:wrap;">
            <button class="fb-btn-gradient" onclick="fbOpenSoilCertificateDialog('${plot.id}')">
              📜 Xem Giấy chứng nhận Kiểm định Thổ nhưỡng
            </button>
            <button class="fb-btn-outline-green" onclick="fbSelectGarden('${plot.gardenKey}'); navigateTo('panorama-360');">
              🌐 Xem lô đất trong Không gian 360°
            </button>
          </div>
        </div>

        <div class="fb-info-card fb-soil-cert-preview" onclick="fbOpenSoilCertificateDialog('${plot.id}')">
          <div class="fb-cert-head">
            <strong>Chứng nhận Phân loại &amp; Kiểm định Mẫu Đất (module_1794.js)</strong>
            <span class="fb-badge-lime">Đạt chuẩn VietGAP / GlobalGAP</span>
          </div>
          <img src="assets/farmblock/src_assets_images_img_soil_classification_bg.png" alt="Chứng nhận đất" class="fb-cert-img">
          <small>Nhấp vào ảnh để phóng to kết quả kiểm định thổ nhưỡng đất đỏ Bazan</small>
        </div>
      </div>
    `;
  }

  if (FB_STATE.plotDetailTab === 'finance') {
    const pct = Math.round((plot.financePaid / plot.financeTotal) * 100);
    return `
      <div class="fb-info-two-col">
        <div class="fb-info-card">
          <h4>💳 Tiến độ Đầu tư &amp; Tài chính Canh tác (${plot.code})</h4>
          <div class="fb-finance-progress-box">
            <div class="fb-fp-row">
              <span>Đã thanh toán: <strong>${plot.financePaid.toLocaleString('vi-VN')} ₫</strong> / ${plot.financeTotal.toLocaleString('vi-VN')} ₫</span>
              <strong style="color:#008242;">${pct}%</strong>
            </div>
            <div class="fb-fp-bar"><div class="fb-fp-fill" style="width:${pct}%;"></div></div>
          </div>
          <div class="fb-kv-list">
            <div class="fb-kv-row"><span>Đợt thanh toán kế tiếp</span><strong>${plot.financeNextLabel}</strong></div>
            <div class="fb-kv-row"><span>Số tiền cần thanh toán</span><strong style="color:#C98A0A;">${plot.financeNextDue.toLocaleString('vi-VN')} ₫</strong></div>
            <div class="fb-kv-row"><span>Ngân hàng thụ hưởng</span><strong>MB Bank — CTCP Nông nghiệp Số FarmBlock</strong></div>
            <div class="fb-kv-row"><span>Nội dung chuyển khoản</span><strong>FARMBLOCK ${plot.code.replace(/-/g, ' ')} DOT4</strong></div>
          </div>
        </div>

        <div class="fb-info-card" style="text-align:center;">
          <h4>📲 Quét mã VietQR Thanh toán Trực tuyến (module_1768.js)</h4>
          <img src="${plot.vietQrUrl}" alt="VietQR" class="fb-vietqr-img" onerror="this.src='https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=FARMBLOCK_${plot.code}_${plot.financeNextDue}'">
          <div style="margin-top:10px;">
            <button class="fb-btn-outline-green" onclick="navigator.clipboard?.writeText('FARMBLOCK ${plot.code} ${plot.financeNextDue}'); showToast('Đã sao chép nội dung chuyển khoản');">
              📋 Sao chép Nội dung Chuyển khoản
            </button>
          </div>
        </div>
      </div>
    `;
  }

  if (FB_STATE.plotDetailTab === 'camera') {
    return `
      <div class="fb-inline-summary-box">
        <div>
          <strong>📹 Camera Giám sát Trực tiếp cho ${plot.code}</strong>
          <p>Kết nối trực tiếp với luồng RTSP/HLS &amp; Thanh Timeline 24h có lưu phân đoạn xanh lá.</p>
        </div>
        <button class="fb-btn-gradient" onclick="navigateTo('camera-live')">Mở Trình phát Camera VLC &amp; Timeline 24h →</button>
      </div>
    `;
  }

  if (FB_STATE.plotDetailTab === 'history') {
    return `
      <div class="fb-inline-summary-box">
        <div>
          <strong>📓 Tiến trình Sinh trưởng 3 Giai đoạn &amp; Nhật ký Chăm sóc (${plot.code})</strong>
          <p>Ngày trồng: ${plot.plantedDate} · Dự kiến thu hoạch: ${plot.expectedHarvest} · Mục tiêu năng suất: ${(plot.yieldTargetKg / 1000).toFixed(1)} tấn/ha.</p>
        </div>
        <button class="fb-btn-gradient" onclick="navigateTo('iot-care')">Mở Nhật ký Canh tác &amp; Tạo Bản ghi Mới →</button>
      </div>
    `;
  }

  // Default: 'metrics' quick preview
  return `
    <div class="fb-inline-summary-box">
      <div>
        <strong>📊 Trạm Cảm biến IoT 19 Chỉ số &amp; Dự báo Open-Meteo (${plot.code})</strong>
        <p>${buildWeatherAiSuggestion(FB_STATE.weatherForecast)}</p>
      </div>
      <button class="fb-btn-gradient" onclick="navigateTo('iot-care')">Xem đầy đủ 19 Chỉ số IoT &amp; Bảng Ngưỡng →</button>
    </div>
  `;
}

function mountFarmBlockLandPlots() {
  fetchOpenMeteoForecast(FB_FARM_META.centerLat, FB_FARM_META.centerLng);
}

function fbRerenderLandPlots() {
  const main = document.getElementById('mainView');
  if (main && STATE.currentScreen === 'land-plots') {
    main.innerHTML = renderFarmBlockLandPlots();
  }
}

function fbSelectPlot(plotId) {
  FB_STATE.selectedPlotId = plotId;
  fbRerenderLandPlots();
}

function fbSetLandViewMode(mode) {
  FB_STATE.landViewMode = mode;
  fbRerenderLandPlots();
}

function fbSetPlotCropFilter(crop) {
  FB_STATE.plotCropFilter = crop;
  fbRerenderLandPlots();
}

function fbUpdatePlotSearch(val) {
  FB_STATE.plotSearchQuery = val;
  fbRerenderLandPlots();
}

function fbSetPlotDetailTab(tab) {
  FB_STATE.plotDetailTab = tab;
  fbRerenderLandPlots();
}

function fbOpenMapStyleSheet() {
  const dialog = document.getElementById('fbLandModal');
  const body = document.getElementById('fbLandModalBody');
  if (!dialog || !body) return;
  const styles = [
    { key: 'default', label: 'Mặc định', desc: 'Bản đồ đường phố tiêu chuẩn 2D', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_default.png' },
    { key: 'satellite', label: 'Vệ tinh', desc: 'Ảnh vệ tinh thực địa độ phân giải cao', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_satellite.png' },
    { key: 'model3d', label: 'Bản đồ Model 3D', desc: 'Địa hình DEM 3D nghiêng 60° & mô hình cây', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_terrain3d.png' },
    { key: 'aerial3d', label: 'Góc nhìn 3D trên cao', desc: 'Góc nhìn chim bay 3D nghiêng 45°', img: 'assets/farmblock/src_assets_images_mapstyle_img_map_mode_birdseye3d.png' }
  ];
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">MapStyleSelectorSheet · module_1252.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Chọn loại bản đồ hiển thị</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbLandModal').close()">✕</button>
    </div>
    <div class="fb-style-grid">
      ${styles.map(s => `
        <div class="fb-style-option ${FB_STATE.mapStyleMode === s.key ? 'active' : ''}" onclick="FB_STATE.mapStyleMode='${s.key}'; document.getElementById('fbLandModal').close(); fbRerenderLandPlots();">
          <img src="${s.img}" alt="${s.label}">
          <strong>${s.label}</strong>
          <small>${s.desc}</small>
        </div>
      `).join('')}
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbOpenFarmInfoDialog() {
  const dialog = document.getElementById('fbLandModal');
  const body = document.getElementById('fbLandModalBody');
  if (!dialog || !body) return;
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">LandPlotMapInfoDialog · module_1254.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Thông tin nông trại ${FB_FARM_META.name}</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbLandModal').close()">✕</button>
    </div>
    <div class="fb-kv-list" style="margin-top:12px;">
      <div class="fb-kv-row"><span>Tên nông trại</span><strong>${FB_FARM_META.name}</strong></div>
      <div class="fb-kv-row"><span>Mã định danh nông trại</span><strong>${FB_FARM_META.code}</strong></div>
      <div class="fb-kv-row"><span>Địa chỉ hành chính</span><strong>${FB_FARM_META.address}</strong></div>
      <div class="fb-kv-row"><span>Tổng số khu trồng</span><strong>${FB_FARM_META.totalPlots} lô đất</strong></div>
      <div class="fb-kv-row"><span>Diện tích tổng</span><strong>${FB_FARM_META.totalAreaM2.toLocaleString('vi-VN')} m²</strong></div>
      <div class="fb-kv-row"><span>Tọa độ trung tâm</span><strong>${FB_FARM_META.centerLat}°N, ${FB_FARM_META.centerLng}°E</strong></div>
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbOpenRenamePlotDialog(plotId) {
  const plot = FB_PLOTS.find(p => p.id === plotId) || FB_PLOTS[0];
  const dialog = document.getElementById('fbLandModal');
  const body = document.getElementById('fbLandModalBody');
  if (!dialog || !body) return;
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">LandPlotRenameDialog · module_1253.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Đổi tên khu đất (${plot.code})</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbLandModal').close()">✕</button>
    </div>
    <form onsubmit="fbSubmitRenamePlot(event, '${plot.id}')" style="margin-top:14px;">
      <div class="form-group">
        <label class="form-label">Tên hiển thị khu đất</label>
        <input type="text" id="fbRenamePlotInput" class="form-input" value="${plot.name}" required>
      </div>
      <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:14px;">
        <button type="button" class="fb-btn-outline-green" onclick="document.getElementById('fbLandModal').close()">Hủy</button>
        <button type="submit" class="fb-btn-gradient">Lưu tên mới</button>
      </div>
    </form>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbSubmitRenamePlot(e, plotId) {
  e.preventDefault();
  const plot = FB_PLOTS.find(p => p.id === plotId);
  const val = document.getElementById('fbRenamePlotInput')?.value?.trim();
  if (plot && val) {
    plot.name = val;
    document.getElementById('fbLandModal')?.close();
    showToast('Đã cập nhật tên khu đất');
    fbRerenderLandPlots();
  }
}

function fbOpenPlotShareDialog(plotId) {
  const plot = FB_PLOTS.find(p => p.id === plotId) || FB_PLOTS[0];
  const dialog = document.getElementById('fbLandModal');
  const body = document.getElementById('fbLandModalBody');
  if (!dialog || !body) return;
  const shareUrl = `https://farmblock.vn/share/plot/${plot.code}?lat=${plot.lat}&lng=${plot.lng}`;
  const qrApiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=360x360&data=${encodeURIComponent(shareUrl)}`;
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">LandShareDialog · module_1255.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Chia sẻ Mã QR Khu đất (${plot.code})</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbLandModal').close()">✕</button>
    </div>
    <div class="fb-share-qr-card" style="background-image:url('assets/farmblock/src_assets_images_img_share_land_background.png');">
      <div class="fb-share-qr-inner">
        <strong>${plot.name}</strong>
        <span>${plot.crop} · ${plot.areaM2.toLocaleString('vi-VN')} m²</span>
        <img src="${qrApiUrl}" alt="QR ${plot.code}" width="200" height="200" class="fb-qr-img">
        <code>${shareUrl}</code>
        <div style="display:flex; gap:10px; margin-top:12px;">
          <button class="fb-btn-gradient" onclick="navigator.clipboard?.writeText('${shareUrl}'); showToast('Đã sao chép liên kết chia sẻ');">
            📋 Sao chép Liên kết
          </button>
        </div>
      </div>
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbOpenSoilCertificateDialog(plotId) {
  const plot = FB_PLOTS.find(p => p.id === plotId) || FB_PLOTS[0];
  const dialog = document.getElementById('fbLandModal');
  const body = document.getElementById('fbLandModalBody');
  if (!dialog || !body) return;
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">PlotSoilCertificateDialog · module_1794.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Chứng nhận Phân loại &amp; Kiểm định Đất (${plot.code})</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbLandModal').close()">✕</button>
    </div>
    <div class="fb-modal-content">
      <img src="assets/farmblock/src_assets_images_img_soil_classification_bg.png" alt="Giấy chứng nhận đất" style="width:100%; max-height:460px; object-fit:contain; border-radius:12px; background:#06280C;">
      <p class="fb-modal-desc"><strong>Kết luận phân tích thổ nhưỡng:</strong> ${plot.soilType}. Độ pH đất đạt 6.1, hàm lượng hữu cơ tầng mặt > 3.8%, hoàn toàn đạt tiêu chuẩn canh tác Cà phê Đặc sản &amp; Sầu riêng xuất khẩu.</p>
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

// =============================================================================
// SUITE 3: CAMERA LIVE VLC & THANH TIMELINE 24H (#camera-live)
// (Ported from module_1765.js, module_1776.js, module_1780..1786.js)
// =============================================================================
function renderFarmBlockCameraLive() {
  const cam = FB_CAMERAS.find(c => c.id === FB_STATE.selectedCameraId) || FB_CAMERAS[0];
  const segments = (cam.segmentsByDate && cam.segmentsByDate[FB_STATE.selectedDateKey]) || [];
  const activeSeg = findSegmentAtSecond(cam, FB_STATE.selectedDateKey, FB_STATE.timelineSec);
  const displayImg = (!FB_STATE.isLive && activeSeg && activeSeg.thumb) ? activeSeg.thumb : cam.snapshotUrl;
  const playheadPct = ((FB_STATE.timelineSec / 86400) * 100).toFixed(2);

  return `
    <div class="fb-camera-workspace">
      <!-- Top Bar: Camera Title, Area Picker Button & Date Picker -->
      <div class="fb-cam-topbar">
        <div>
          <div class="fb-cam-badges">
            <span class="fb-badge-live ${FB_STATE.isLive ? 'is-live' : 'is-playback'}">
              ${FB_STATE.isLive ? '● TRỰC TIẾP (LIVE RTSP/HLS)' : `▶ XEM LẠI GHI HÌNH · ${formatSecondsToHMS(FB_STATE.timelineSec)}`}
            </span>
            <span class="fb-badge-outline">${cam.resolution}</span>
            <span class="fb-badge-outline">📍 ${cam.area}</span>
          </div>
          <h3 class="fb-cam-title">${cam.name}</h3>
        </div>

        <div class="fb-cam-top-actions">
          <div class="fb-date-picker-group">
            <label>📅 Ngày ghi hình:</label>
            <select class="fb-select-input" onchange="fbChangeCameraDate(this.value)">
              <option value="2026-10-30" ${FB_STATE.selectedDateKey === '2026-10-30' ? 'selected' : ''}>Hôm nay (30/10/2026) — ${(cam.segmentsByDate['2026-10-30'] || []).length} đoạn</option>
              <option value="2026-10-29" ${FB_STATE.selectedDateKey === '2026-10-29' ? 'selected' : ''}>Hôm qua (29/10/2026) — ${(cam.segmentsByDate['2026-10-29'] || []).length} đoạn</option>
              <option value="2026-10-28" ${FB_STATE.selectedDateKey === '2026-10-28' ? 'selected' : ''}>28/10/2026 — ${(cam.segmentsByDate['2026-10-28'] || []).length} đoạn</option>
            </select>
          </div>
          <button class="fb-btn-outline-green" onclick="fbOpenCameraAreaSheet()">
            📹 Chọn Khu vực Camera (${FB_CAMERAS.length})
          </button>
        </div>
      </div>

      <!-- Main Split: Video Player + Timeline (Left) & Camera Area List (Right) -->
      <div class="fb-cam-main-grid">
        <div class="fb-cam-player-col">
          <!-- CameraPlayerCard (module_1776.js) -->
          <div class="fb-player-stage" id="fbPlayerStage">
            <img src="${displayImg}" alt="${cam.name}" class="fb-player-bg-img" id="fbPlayerBgImg">
            <div class="fb-player-scanline"></div>

            <!-- OSD Telemetry Top Overlay -->
            <div class="fb-osd-top">
              <div class="fb-osd-pill">
                <span class="fb-osd-dot ${FB_STATE.isLive ? 'live' : 'rec'}"></span>
                <strong id="fbOsdModeText">${FB_STATE.isLive ? 'TRỰC TIẾP' : 'GHI HÌNH'}</strong>
                <span>| ${cam.id} | ${FB_STATE.selectedDateKey} <span id="fbOsdClock">${formatSecondsToHMS(FB_STATE.timelineSec)}</span></span>
              </div>
              <div class="fb-osd-pill">
                <span>Bitrate: 3.4 Mbps · 25 FPS · VLC Engine</span>
              </div>
            </div>

            <!-- Center Notice when paused or over unrecorded gap -->
            ${!FB_STATE.isLive && !activeSeg ? `
              <div class="fb-no-recording-overlay">
                <div class="fb-no-rec-card">
                  <strong>⚠️ Không có bản ghi tại mốc ${formatSecondsToHMS(FB_STATE.timelineSec)}</strong>
                  <p>Kéo thanh Timeline 24h bên dưới vào vùng màu xanh lá (#009E50) hoặc bấm vào một phân đoạn đã ghi để phát.</p>
                  <button class="fb-btn-gradient" onclick="fbReturnToLive()">Về phát Trực tiếp (LIVE)</button>
                </div>
              </div>
            ` : ''}

            <!-- Bottom Player Transport Bar -->
            <div class="fb-player-controls">
              <div class="fb-pc-left">
                <button class="fb-pc-btn" onclick="fbSeekCamera(-10)" title="Lùi 10 giây">⏪ -10s</button>
                <button class="fb-pc-btn primary" onclick="fbToggleCameraPlay()" title="Phát / Tạm dừng">
                  ${FB_STATE.isPlaying ? '⏸ Tạm dừng' : '▶ Phát'}
                </button>
                <button class="fb-pc-btn" onclick="fbSeekCamera(10)" title="Tiến 10 giây">+10s ⏩</button>
                <button class="fb-pc-btn ${FB_STATE.isLive ? 'active-live' : ''}" onclick="fbReturnToLive()">
                  ● Về Trực tiếp
                </button>
              </div>
              <div class="fb-pc-right">
                <span class="fb-pc-seg-label">
                  ${FB_STATE.isLive ? 'Luồng trực tiếp: ' + cam.rtspUrl : (activeSeg ? activeSeg.label : 'Khoảng trống không ghi')}
                </span>
                <button class="fb-pc-btn" onclick="fbToggleCameraFullscreen()" title="Toàn màn hình 16:9">
                  ⛶ Toàn màn hình
                </button>
              </div>
            </div>
          </div>

          <!-- 24-HOUR TIMELINE STRIP + SCRUB PREVIEW (module_1782.js & module_1783.js) -->
          <div class="fb-timeline-card">
            <div class="fb-tl-header">
              <div>
                <strong>Thanh Timeline 24 Giờ (${FB_STATE.selectedDateKey})</strong>
                <span>Kéo hoặc nhấp trên thước 00:00 → 24:00 · Vùng màu xanh lá là phân đoạn có lưu trữ video</span>
              </div>
              <div class="fb-tl-legend">
                <span><i class="fb-tl-dot green"></i> Có bản ghi (${segments.length} đoạn)</span>
                <span><i class="fb-tl-dot red"></i> Kim thời gian: <strong id="fbTimelineReadout">${formatSecondsToHMS(FB_STATE.timelineSec)}</strong></span>
              </div>
            </div>

            <!-- Interactive 24h Strip with Floating CameraScrubPreview -->
            <div class="fb-tl-strip-wrapper" id="fbTimelineStripWrapper">
              <!-- Floating Scrub Preview Tooltip (module_1783.js) -->
              <div class="fb-scrub-preview" id="fbScrubPreview" style="display:none;">
                <img id="fbScrubThumb" src="${cam.thumbUrl}" alt="Scrub">
                <div class="fb-scrub-meta">
                  <strong id="fbScrubTime">08:30:00</strong>
                  <span id="fbScrubLabel">Đang xem trước...</span>
                </div>
              </div>

              <div class="fb-tl-track" id="fbTimelineTrack">
                <!-- Hour Grid Marks 00h..24h -->
                ${Array.from({ length: 25 }, (_, h) => {
                  const leftPct = ((h / 24) * 100).toFixed(2);
                  return `
                    <div class="fb-tl-hour-tick" style="left:${leftPct}%;">
                      <span>${String(h).padStart(2, '0')}:00</span>
                    </div>
                  `;
                }).join('')}

                <!-- Recorded Playable Segments (Green blocks) -->
                ${segments.map(seg => {
                  const leftPct = ((seg.startSec / 86400) * 100).toFixed(2);
                  const widthPct = (((seg.endSec - seg.startSec) / 86400) * 100).toFixed(2);
                  return `
                    <div
                      class="fb-tl-segment"
                      style="left:${leftPct}%; width:${widthPct}%;"
                      title="${seg.label}"
                      onclick="event.stopPropagation(); fbJumpToSecond(${seg.startSec});"
                    ></div>
                  `;
                }).join('')}

                <!-- Red Playhead Indicator (#E33B54) -->
                <div class="fb-tl-playhead" id="fbTimelinePlayhead" style="left:${playheadPct}%;">
                  <div class="fb-tl-playhead-cap"></div>
                </div>
              </div>
            </div>

            <!-- Quick Segment Chips -->
            <div class="fb-tl-segments-list">
              <span>Các phân đoạn đã ghi trong ngày:</span>
              <div class="fb-tl-seg-chips">
                ${segments.map(seg => `
                  <button
                    class="fb-tl-seg-chip ${!FB_STATE.isLive && activeSeg === seg ? 'active' : ''}"
                    onclick="fbJumpToSecond(${seg.startSec})"
                  >
                    ▶ ${seg.label}
                  </button>
                `).join('')}
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: CameraAreaList (module_1785.js) -->
        <div class="fb-cam-area-col">
          <div class="fb-area-card">
            <div class="fb-area-head">
              <strong>Danh sách Camera Khu vực (${FB_CAMERAS.length})</strong>
              <span class="fb-badge-lime">Trực tuyến 100%</span>
            </div>
            <div class="fb-area-list">
              ${FB_CAMERAS.map(c => {
                const isAct = c.id === cam.id;
                const segCount = ((c.segmentsByDate && c.segmentsByDate[FB_STATE.selectedDateKey]) || []).length;
                return `
                  <div class="fb-area-item ${isAct ? 'active' : ''}" onclick="fbSelectCamera('${c.id}')">
                    <div class="fb-area-thumb" style="background-image:url('${c.thumbUrl}');">
                      <span class="fb-area-live-dot">● LIVE</span>
                    </div>
                    <div class="fb-area-info">
                      <strong>${c.name}</strong>
                      <span>${c.area}</span>
                      <small>${c.resolution} · ${segCount} đoạn ghi</small>
                    </div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        </div>
      </div>

      <!-- CameraAreaSheet Modal (module_1784.js) -->
      <dialog id="fbCameraModal" class="figma-modal">
        <div class="fb-modal-card" id="fbCameraModalBody"></div>
      </dialog>
    </div>
  `;
}

function mountFarmBlockCameraLive() {
  cleanupFarmBlockView();
  const track = document.getElementById('fbTimelineTrack');
  const preview = document.getElementById('fbScrubPreview');
  const scrubThumb = document.getElementById('fbScrubThumb');
  const scrubTime = document.getElementById('fbScrubTime');
  const scrubLabel = document.getElementById('fbScrubLabel');
  const cam = FB_CAMERAS.find(c => c.id === FB_STATE.selectedCameraId) || FB_CAMERAS[0];

  if (track) {
    let dragging = false;
    const updateScrubFromEvent = (e, commitJump = false) => {
      const rect = track.getBoundingClientRect();
      const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const sec = Math.floor(ratio * 86399);
      const seg = findSegmentAtSecond(cam, FB_STATE.selectedDateKey, sec);

      if (preview && scrubTime && scrubLabel && scrubThumb) {
        preview.style.display = 'flex';
        preview.style.left = `${Math.max(80, Math.min(rect.width - 80, ratio * rect.width))}px`;
        scrubTime.textContent = formatSecondsToHMS(sec);
        scrubLabel.textContent = seg ? seg.label : 'Không có bản ghi tại thời điểm này';
        scrubThumb.src = seg ? seg.thumb : cam.thumbUrl;
      }

      if (commitJump) {
        fbJumpToSecond(sec);
      }
    };

    track.addEventListener('pointerdown', (e) => {
      dragging = true;
      track.setPointerCapture(e.pointerId);
      updateScrubFromEvent(e, true);
    });
    track.addEventListener('pointermove', (e) => {
      updateScrubFromEvent(e, dragging);
    });
    track.addEventListener('pointerup', () => { dragging = false; });
    track.addEventListener('pointerleave', () => {
      if (!dragging && preview) preview.style.display = 'none';
    });
  }

  _fbCamClockId = setInterval(() => {
    if (!FB_STATE.isPlaying) return;
    FB_STATE.timelineSec = (FB_STATE.timelineSec + 1) % 86400;
    const clockEl = document.getElementById('fbOsdClock');
    const readoutEl = document.getElementById('fbTimelineReadout');
    const playheadEl = document.getElementById('fbTimelinePlayhead');
    if (clockEl) clockEl.textContent = formatSecondsToHMS(FB_STATE.timelineSec);
    if (readoutEl) readoutEl.textContent = formatSecondsToHMS(FB_STATE.timelineSec);
    if (playheadEl) playheadEl.style.left = `${((FB_STATE.timelineSec / 86400) * 100).toFixed(2)}%`;
  }, 1000);
}

function fbRerenderCameraLive() {
  const main = document.getElementById('mainView');
  if (main && STATE.currentScreen === 'camera-live') {
    main.innerHTML = renderFarmBlockCameraLive();
    mountFarmBlockCameraLive();
  }
}

function fbSelectCamera(camId) {
  FB_STATE.selectedCameraId = camId;
  FB_STATE.isLive = true;
  fbRerenderCameraLive();
}

function fbChangeCameraDate(dateKey) {
  FB_STATE.selectedDateKey = dateKey;
  const cam = FB_CAMERAS.find(c => c.id === FB_STATE.selectedCameraId) || FB_CAMERAS[0];
  const segs = (cam.segmentsByDate && cam.segmentsByDate[dateKey]) || [];
  if (segs.length > 0) {
    FB_STATE.timelineSec = segs[0].startSec;
    FB_STATE.isLive = false;
  }
  fbRerenderCameraLive();
}

function fbJumpToSecond(sec) {
  FB_STATE.timelineSec = Math.max(0, Math.min(86399, Math.floor(sec)));
  FB_STATE.isLive = false;
  FB_STATE.isPlaying = true;
  fbRerenderCameraLive();
}

function fbSeekCamera(deltaSec) {
  fbJumpToSecond(FB_STATE.timelineSec + deltaSec);
}

function fbToggleCameraPlay() {
  FB_STATE.isPlaying = !FB_STATE.isPlaying;
  fbRerenderCameraLive();
}

function fbReturnToLive() {
  FB_STATE.isLive = true;
  FB_STATE.isPlaying = true;
  FB_STATE.selectedDateKey = '2026-10-30';
  FB_STATE.timelineSec = 30600;
  fbRerenderCameraLive();
}

function fbToggleCameraFullscreen() {
  const stage = document.getElementById('fbPlayerStage');
  if (!stage) return;
  if (!document.fullscreenElement) {
    stage.requestFullscreen?.().catch(() => {});
  } else {
    document.exitFullscreen?.().catch(() => {});
  }
}

function fbOpenCameraAreaSheet() {
  const dialog = document.getElementById('fbCameraModal');
  const body = document.getElementById('fbCameraModalBody');
  if (!dialog || !body) return;
  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">CameraAreaSheet · module_1784.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Chọn khu vực Camera giám sát</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbCameraModal').close()">✕</button>
    </div>
    <div class="fb-area-list" style="margin-top:12px;">
      ${FB_CAMERAS.map(c => `
        <div class="fb-area-item ${c.id === FB_STATE.selectedCameraId ? 'active' : ''}" onclick="document.getElementById('fbCameraModal').close(); fbSelectCamera('${c.id}');">
          <div class="fb-area-thumb" style="background-image:url('${c.thumbUrl}');"></div>
          <div class="fb-area-info">
            <strong>${c.name}</strong>
            <span>${c.area}</span>
            <small>${c.resolution} · RTSP: ${c.rtspUrl}</small>
          </div>
        </div>
      `).join('')}
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

// =============================================================================
// SUITE 4: LUỒNG IOT 19 CHỈ SỐ & NHẬT KÝ CANH TÁC (#iot-care)
// (Ported from module_1763..1766.js, module_1789..1795.js, module_1842.js)
// =============================================================================
function renderFarmBlockIotCare() {
  const plot = FB_PLOTS.find(p => p.id === FB_STATE.selectedPlotId) || FB_PLOTS[0];
  const soilGroup = FB_METRIC_CATALOG.find(g => g.key === 'soil');
  const soilMoistureItem = soilGroup?.items.find(i => i.key === 'soil-moisture');
  const soilPhItem = soilGroup?.items.find(i => i.key === 'soil-ph');
  const aiText = buildWeatherAiSuggestion(
    FB_STATE.weatherForecast,
    soilMoistureItem ? soilMoistureItem.value : 64.2,
    soilPhItem ? soilPhItem.value : 6.1
  );

  const visibleGroups = FB_METRIC_CATALOG.filter(
    g => FB_STATE.metricGroupFilter === 'all' || g.key === FB_STATE.metricGroupFilter
  );

  const growthSteps = [
    {
      key: 'plant',
      label: '1. Trồng cây',
      desc: 'Chuẩn bị hố, bón lót hữu cơ & xuống giống chuẩn',
      activeIcon: 'assets/farmblock/src_assets_icons_history_handcoinactive.png',
      inactiveIcon: 'assets/farmblock/src_assets_icons_history_handcoininactive.png'
    },
    {
      key: 'young',
      label: '2. Cây non',
      desc: 'Kiến thiết bộ khung cành cấp 1 & tưới bù áp tự động',
      activeIcon: 'assets/farmblock/src_assets_icons_history_leafactive.png',
      inactiveIcon: 'assets/farmblock/src_assets_icons_history_leafinactive.png'
    },
    {
      key: 'grow',
      label: '3. Sinh trưởng thân lá',
      desc: 'Khép tán, phân hóa mầm hoa & nuôi quả thương phẩm',
      activeIcon: 'assets/farmblock/src_assets_icons_history_seedlingactive.png',
      inactiveIcon: 'assets/farmblock/src_assets_icons_history_seedlinginactive.png'
    }
  ];
  const stageOrder = ['plant', 'young', 'grow'];
  const currentStageIdx = Math.max(0, stageOrder.indexOf(plot.stageKey));

  const filteredLogs = FB_CARE_LOGS.filter(l => {
    if (FB_STATE.historyBucketFilter !== 'all' && l.bucket !== FB_STATE.historyBucketFilter) return false;
    return true;
  });

  return `
    <div class="fb-iot-workspace">
      <!-- Plot Selector & Telemetry Simulation Header -->
      <div class="fb-iot-topbar">
        <div class="fb-iot-plot-picker">
          <label>🌱 Đang xem Lô đất:</label>
          <select class="fb-select-input" onchange="fbSelectPlotForIot(this.value)">
            ${FB_PLOTS.map(p => `
              <option value="${p.id}" ${p.id === plot.id ? 'selected' : ''}>${p.code} — ${p.crop} (${p.areaM2.toLocaleString('vi-VN')} m²)</option>
            `).join('')}
          </select>
        </div>

        <div class="fb-iot-top-actions">
          <button class="fb-btn-outline-green" onclick="fbSimulateIotTelemetry()">
            🔄 Đồng bộ / Mô phỏng Cảm biến
          </button>
          <button class="fb-btn-gradient" onclick="fbOpenCreateCareLogModal()">
            + Ghi Nhật ký Chăm sóc Mới
          </button>
        </div>
      </div>

      <!-- ROW 1: AI Agronomy Summary + 7-Day Open-Meteo Forecast (module_1789.js & module_1790.js) -->
      <div class="fb-weather-ai-grid">
        <div class="fb-ai-advisor-card">
          <div class="fb-ai-head">
            <span class="fb-badge-lime">AI NÔNG HỌC · buildWeatherAiSuggestion</span>
            <small>Trạm IoT Mang Yang · Cập nhật tức thời</small>
          </div>
          <h4>Khuyến nghị Canh tác &amp; Điều tiết Tưới tiêu Hôm nay</h4>
          <p>${aiText}</p>
          <div class="fb-ai-tags">
            <span>💧 Độ ẩm đất: <strong>${soilMoistureItem ? soilMoistureItem.value : 64.2}%</strong></span>
            <span>🧪 pH đất: <strong>${soilPhItem ? soilPhItem.value : 6.1} pH</strong></span>
            <span>📍 Cao độ: <strong>${plot.elevationM}m</strong></span>
          </div>
        </div>

        <div class="fb-forecast-7d-card">
          <div class="fb-f7-head">
            <strong>🌤️ Dự báo Thời tiết 7 Ngày (${FB_STATE.weatherSource})</strong>
            <span>Tọa độ: ${plot.lat.toFixed(4)}°N, ${plot.lng.toFixed(4)}°E</span>
          </div>
          <div class="fb-f7-strip">
            ${FB_STATE.weatherForecast.map((d, i) => `
              <div class="fb-f7-day ${i === 0 ? 'today' : ''}">
                <span class="fb-f7-name">${d.day}</span>
                <small>${d.date}</small>
                <div class="fb-f7-icon">${d.icon}</div>
                <strong class="fb-f7-temp">${d.tempMax}° / ${d.tempMin}°C</strong>
                <span class="fb-f7-rain">💧 ${d.rainProb}% (${d.rainMm}mm)</span>
                <small class="fb-f7-wind">💨 ${d.windMs} m/s</small>
              </div>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- ROW 2: 19-METRIC IOT CATALOG WITH RANGETRACK (module_1795.js & module_1792.js) -->
      <div class="fb-metrics-section">
        <div class="fb-sec-bar">
          <div>
            <h3>📡 Hệ thống 19 Chỉ số Cảm biến IoT Thực địa (METRIC_CATALOG)</h3>
            <p>Nhấp vào bất kỳ thẻ chỉ số nào để mở <strong>MetricInfoSheet</strong> xem Ý nghĩa nông học, Bảng ngưỡng tham chiếu và Khuyến nghị xử lý</p>
          </div>
          <div class="fb-mode-pill-group">
            <button class="fb-mode-btn ${FB_STATE.metricGroupFilter === 'all' ? 'active' : ''}" onclick="fbSetMetricGroupFilter('all')">Tất cả (19)</button>
            <button class="fb-mode-btn ${FB_STATE.metricGroupFilter === 'weather' ? 'active' : ''}" onclick="fbSetMetricGroupFilter('weather')">Thời tiết (7)</button>
            <button class="fb-mode-btn ${FB_STATE.metricGroupFilter === 'water' ? 'active' : ''}" onclick="fbSetMetricGroupFilter('water')">Chỉ số nước (5)</button>
            <button class="fb-mode-btn ${FB_STATE.metricGroupFilter === 'soil' ? 'active' : ''}" onclick="fbSetMetricGroupFilter('soil')">Dinh dưỡng đất (7)</button>
          </div>
        </div>

        ${visibleGroups.map(group => `
          <div class="fb-metric-group-block">
            <div class="fb-mg-header">
              <h4>${group.title} <span>(${group.items.length} chỉ số)</span></h4>
              <small>${group.subtitle}</small>
            </div>
            <div class="fb-metric-cards-grid">
              ${group.items.map(item => {
                const toneObj = FB_TONE_COLORS[item.tone] || FB_TONE_COLORS.good;
                let rangeHtml = '';
                if (item.variant === 'range' && item.scale && item.goodRange) {
                  const span = item.scale.max - item.scale.min;
                  const goodLeft = Math.max(0, Math.min(100, ((item.goodRange.min - item.scale.min) / span) * 100));
                  const goodWidth = Math.max(5, Math.min(100 - goodLeft, ((item.goodRange.max - item.goodRange.min) / span) * 100));
                  const valPos = Math.max(2, Math.min(98, ((item.value - item.scale.min) / span) * 100));
                  rangeHtml = `
                    <div class="fb-range-track-wrap">
                      <div class="fb-range-track">
                        <div class="fb-range-good-zone" style="left:${goodLeft}%; width:${goodWidth}%;"></div>
                        <div class="fb-range-thumb" style="left:${valPos}%; background:${toneObj.dot};"></div>
                      </div>
                      <div class="fb-range-labels">
                        <span>${item.scale.min} ${item.unit}</span>
                        <span style="color:#008242; font-weight:600;">Tối ưu: ${item.goodRange.min}–${item.goodRange.max} ${item.unit}</span>
                        <span>${item.scale.max} ${item.unit}</span>
                      </div>
                    </div>
                  `;
                } else {
                  rangeHtml = `
                    <div class="fb-plain-sub-note">
                      <span>${item.info.meaning.slice(0, 78)}...</span>
                    </div>
                  `;
                }

                return `
                  <div class="fb-metric-card" onclick="fbOpenMetricInfoSheet('${item.key}')">
                    <div class="fb-mc-top">
                      <strong class="fb-mc-title">${item.title}</strong>
                      <span class="fb-mc-badge" style="background:${toneObj.bg}; color:${toneObj.text}; border:1px solid ${toneObj.border};">
                        ● ${item.statusLabel}
                      </span>
                    </div>
                    <div class="fb-mc-value-row">
                      <span class="fb-mc-val">${item.value.toLocaleString('vi-VN')}</span>
                      <span class="fb-mc-unit">${item.unit}</span>
                      <span class="fb-mc-info-hint" title="Xem bảng tham chiếu">ⓘ Chi tiết</span>
                    </div>
                    ${rangeHtml}
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        `).join('')}
      </div>

      <!-- ROW 3: GROWTH PROGRESS 3 STAGES + YIELD TRACKER + CARE LOG TIMELINE (module_1764.js & module_1766.js) -->
      <div class="fb-history-section">
        <div class="fb-growth-yield-grid">
          <!-- 3-Step Growth Stepper -->
          <div class="fb-growth-card">
            <div class="fb-gc-head">
              <div>
                <span class="fb-badge-lime">TIẾN TRÌNH SINH TRƯỞNG · module_1766.js</span>
                <h4>3 Giai đoạn Phát triển Cây trồng (${plot.code})</h4>
              </div>
              <span>Ngày trồng: <strong>${plot.plantedDate}</strong> · Thu hoạch: <strong>${plot.expectedHarvest}</strong></span>
            </div>

            <div class="fb-growth-stepper">
              ${growthSteps.map((st, idx) => {
                const isActiveOrDone = idx <= currentStageIdx;
                const isCurrent = idx === currentStageIdx;
                return `
                  <div class="fb-growth-step ${isActiveOrDone ? 'done' : ''} ${isCurrent ? 'current' : ''}" onclick="fbSetPlotGrowthStage('${plot.id}', '${st.key}')">
                    <div class="fb-gs-icon-wrap">
                      <img src="${isActiveOrDone ? st.activeIcon : st.inactiveIcon}" alt="${st.label}" width="28" height="28">
                    </div>
                    <div class="fb-gs-text">
                      <strong>${st.label}</strong>
                      <small>${st.desc}</small>
                    </div>
                    <span class="fb-gs-status">${isCurrent ? '● Đang diễn ra' : isActiveOrDone ? '✓ Hoàn thành' : '○ Tiếp theo'}</span>
                  </div>
                `;
              }).join('')}
            </div>
          </div>

          <!-- Yield Tracker Card -->
          <div class="fb-yield-card">
            <div class="fb-yc-head">
              <img src="assets/farmblock/src_assets_icons_history_seedlingyield.png" alt="Yield" width="28" height="28">
              <div>
                <strong>Thống kê Sản lượng &amp; Mục tiêu Năng suất</strong>
                <small>${plot.crop}</small>
              </div>
            </div>
            <div class="fb-yc-metrics">
              <div class="fb-yc-box">
                <span>Sản lượng vụ gần nhất</span>
                <strong>${plot.yieldLastKg > 0 ? `${plot.yieldLastKg.toLocaleString('vi-VN')} kg/ha` : 'Giai đoạn kiến thiết'}</strong>
              </div>
              <div class="fb-yc-box highlight">
                <span>Mục tiêu năng suất chuẩn</span>
                <strong>${plot.yieldTargetKg.toLocaleString('vi-VN')} kg/ha/năm</strong>
              </div>
            </div>
            <p class="fb-yc-note">
              Áp dụng quy trình canh tác chuẩn FarmBlock (Đa thân hai thân / Mô kép 2 gốc + Tưới nhỏ giọt bù áp tự động).
            </p>
          </div>
        </div>

        <!-- Care Log Timeline grouped by Hôm nay / Hôm qua / Cũ hơn -->
        <div class="fb-carelog-card">
          <div class="fb-cl-header">
            <div>
              <h3>📓 Nhật ký Chăm sóc &amp; Canh tác Thực địa</h3>
              <p>Phân nhóm tự động theo mốc thời gian: <strong>Hôm nay</strong> · <strong>Hôm qua</strong> · <strong>Cũ hơn</strong> (nhấp vào bản ghi để xem chi tiết)</p>
            </div>
            <div class="fb-cl-actions">
              <div class="fb-mode-pill-group">
                <button class="fb-mode-btn ${FB_STATE.historyBucketFilter === 'all' ? 'active' : ''}" onclick="fbSetHistoryBucketFilter('all')">Tất cả (${FB_CARE_LOGS.length})</button>
                <button class="fb-mode-btn ${FB_STATE.historyBucketFilter === 'today' ? 'active' : ''}" onclick="fbSetHistoryBucketFilter('today')">Hôm nay</button>
                <button class="fb-mode-btn ${FB_STATE.historyBucketFilter === 'yesterday' ? 'active' : ''}" onclick="fbSetHistoryBucketFilter('yesterday')">Hôm qua</button>
                <button class="fb-mode-btn ${FB_STATE.historyBucketFilter === 'older' ? 'active' : ''}" onclick="fbSetHistoryBucketFilter('older')">Cũ hơn</button>
              </div>
              <button class="fb-btn-gradient" onclick="fbOpenCreateCareLogModal()">+ Thêm Nhật ký</button>
            </div>
          </div>

          <div class="fb-cl-timeline">
            ${filteredLogs.map(log => `
              <div class="fb-cl-item" onclick="fbOpenCareLogDetailModal('${log.id}')">
                <img src="${log.image}" alt="${log.title}" class="fb-cl-thumb">
                <div class="fb-cl-body">
                  <div class="fb-cl-meta-top">
                    <span class="fb-cl-bucket-pill ${log.bucket}">${log.bucketLabel}</span>
                    <span class="fb-cl-cat">${log.category}</span>
                    <span class="fb-cl-plot">#${log.plotId}</span>
                    <span class="fb-cl-date">🕒 ${log.dateStr}</span>
                  </div>
                  <h4>${log.title}</h4>
                  <p>${log.notes}</p>
                  <div class="fb-cl-footer">
                    <span>👨‍🌾 Kỹ thuật viên: <strong>${log.technician}</strong></span>
                    <span>🧪 Vật tư / Thông số: <strong>${log.materials}</strong></span>
                  </div>
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- MetricInfoSheet / CareLogDetail / CreateCareLog Modal -->
      <dialog id="fbIotModal" class="figma-modal">
        <div class="fb-modal-card" id="fbIotModalBody"></div>
      </dialog>
    </div>
  `;
}

function mountFarmBlockIotCare() {
  const plot = FB_PLOTS.find(p => p.id === FB_STATE.selectedPlotId) || FB_PLOTS[0];
  fetchOpenMeteoForecast(plot.lat, plot.lng);
}

function fbRerenderIotCare() {
  const main = document.getElementById('mainView');
  if (main && STATE.currentScreen === 'iot-care') {
    main.innerHTML = renderFarmBlockIotCare();
  }
}

function fbSelectPlotForIot(plotId) {
  FB_STATE.selectedPlotId = plotId;
  fbRerenderIotCare();
}

function fbSetMetricGroupFilter(groupKey) {
  FB_STATE.metricGroupFilter = groupKey;
  fbRerenderIotCare();
}

function fbSetHistoryBucketFilter(bucket) {
  FB_STATE.historyBucketFilter = bucket;
  fbRerenderIotCare();
}

function fbSetPlotGrowthStage(plotId, stageKey) {
  const plot = FB_PLOTS.find(p => p.id === plotId);
  if (plot) {
    plot.stageKey = stageKey;
    showToast('Đã cập nhật giai đoạn sinh trưởng');
    fbRerenderIotCare();
  }
}

function fbSimulateIotTelemetry() {
  for (const group of FB_METRIC_CATALOG) {
    for (const item of group.items) {
      if (typeof item.value === 'number' && item.key !== 'wind-direction') {
        const delta = (Math.random() - 0.48) * (item.value * 0.04 + 0.3);
        item.value = Number(Math.max(0, item.value + delta).toFixed(1));
        if (item.goodRange) {
          if (item.value >= item.goodRange.min && item.value <= item.goodRange.max) {
            item.tone = 'good';
            item.statusLabel = 'Phù hợp';
          } else {
            item.tone = 'warning';
            item.statusLabel = 'Cần theo dõi';
          }
        }
      }
    }
  }
  showToast('Đã đồng bộ dữ liệu cảm biến IoT mới nhất');
  fbRerenderIotCare();
}

function fbOpenMetricInfoSheet(metricKey) {
  let found = null;
  let groupTitle = '';
  for (const g of FB_METRIC_CATALOG) {
    const m = g.items.find(i => i.key === metricKey);
    if (m) {
      found = m;
      groupTitle = g.title;
      break;
    }
  }
  if (!found) return;

  const dialog = document.getElementById('fbIotModal');
  const body = document.getElementById('fbIotModalBody');
  if (!dialog || !body) return;
  const toneObj = FB_TONE_COLORS[found.tone] || FB_TONE_COLORS.good;

  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">MetricInfoSheet · ${groupTitle} (module_1793.js)</span>
        <h3 style="margin:6px 0 0; color:#06280C;">${found.title} — ${found.value} ${found.unit}</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbIotModal').close()">✕</button>
    </div>
    <div class="fb-modal-content">
      <div style="margin-bottom:12px;">
        <span class="fb-mc-badge" style="background:${toneObj.bg}; color:${toneObj.text}; border:1px solid ${toneObj.border}; font-size:12px; padding:5px 12px;">
          ● Trạng thái hiện tại: ${found.statusLabel} (${found.value} ${found.unit})
        </span>
      </div>

      <div class="fb-info-sheet-block">
        <strong>1. Ý nghĩa nông học (Meaning)</strong>
        <p>${found.info.meaning}</p>
      </div>

      ${found.info.reference ? `
        <div class="fb-info-sheet-block">
          <strong>2. Ngưỡng tham chiếu tiêu chuẩn (Reference Bands)</strong>
          <div class="fb-ref-bands-list">
            ${found.info.reference.map(ref => {
              const rt = FB_TONE_COLORS[ref.tone] || FB_TONE_COLORS.neutral;
              return `
                <div class="fb-ref-band-row" style="background:${rt.bg}; color:${rt.text}; border-left:4px solid ${rt.dot};">
                  <span>${ref.text}</span>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      ` : ''}

      ${found.info.recommendation ? `
        <div class="fb-info-sheet-block">
          <strong>3. Khuyến nghị vận hành &amp; xử lý (Recommendation)</strong>
          <p>${found.info.recommendation}</p>
        </div>
      ` : ''}
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbOpenCareLogDetailModal(logId) {
  const log = FB_CARE_LOGS.find(l => l.id === logId);
  if (!log) return;
  const dialog = document.getElementById('fbIotModal');
  const body = document.getElementById('fbIotModalBody');
  if (!dialog || !body) return;

  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">PlotHistoryDetailModal · #${log.plotId} (${log.bucketLabel})</span>
        <h3 style="margin:6px 0 0; color:#06280C;">${log.title}</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbIotModal').close()">✕</button>
    </div>
    <div class="fb-modal-content">
      <img src="${log.image}" alt="${log.title}" class="fb-modal-hero-img">
      <div class="fb-kv-list" style="margin:12px 0;">
        <div class="fb-kv-row"><span>Hạng mục công việc</span><strong>${log.category}</strong></div>
        <div class="fb-kv-row"><span>Thời gian thực hiện</span><strong>${log.dateStr}</strong></div>
        <div class="fb-kv-row"><span>Kỹ sư phụ trách</span><strong>${log.technician}</strong></div>
        <div class="fb-kv-row"><span>Vật tư &amp; Định lượng</span><strong>${log.materials}</strong></div>
      </div>
      <p class="fb-modal-desc">${log.notes}</p>
    </div>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbOpenCreateCareLogModal() {
  const plot = FB_PLOTS.find(p => p.id === FB_STATE.selectedPlotId) || FB_PLOTS[0];
  const dialog = document.getElementById('fbIotModal');
  const body = document.getElementById('fbIotModalBody');
  if (!dialog || !body) return;

  body.innerHTML = `
    <div class="fb-modal-head">
      <div>
        <span class="fb-badge-lime">CreatePlotHistoryScreen · module_1842.js</span>
        <h3 style="margin:6px 0 0; color:#06280C;">Ghi nhận Nhật ký Canh tác Mới (${plot.code})</h3>
      </div>
      <button class="fmodal-close" onclick="document.getElementById('fbIotModal').close()">✕</button>
    </div>
    <form onsubmit="fbSubmitNewCareLog(event)" style="margin-top:14px; display:flex; flex-direction:column; gap:12px;">
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
        <div class="form-group">
          <label class="form-label">Khu đất áp dụng</label>
          <select id="fbLogPlotId" class="form-input">
            ${FB_PLOTS.map(p => `<option value="${p.id}" ${p.id === plot.id ? 'selected' : ''}>${p.code} — ${p.crop}</option>`).join('')}
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Loại công việc</label>
          <select id="fbLogCategory" class="form-input">
            <option value="Tưới nước & Châm phân">Tưới nước &amp; Châm phân</option>
            <option value="Bón phân & Cải tạo đất">Bón phân &amp; Cải tạo đất</option>
            <option value="Tỉa cành & Tạo tán">Tỉa cành &amp; Tạo tán</option>
            <option value="Bảo vệ thực vật & Chắn gió">Bảo vệ thực vật &amp; Chắn gió</option>
            <option value="Thu hoạch & Kiểm định">Thu hoạch &amp; Kiểm định</option>
          </select>
        </div>
      </div>

      <div class="form-group">
        <label class="form-label">Tiêu đề công việc thực địa</label>
        <input type="text" id="fbLogTitle" class="form-input" placeholder="VD: Tưới nhỏ giọt bù áp & Bón phân vi sinh đợt 4" required>
      </div>

      <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
        <div class="form-group">
          <label class="form-label">Kỹ thuật viên thực hiện</label>
          <input type="text" id="fbLogTech" class="form-input" value="KS. Trần Minh Đức" required>
        </div>
        <div class="form-group">
          <label class="form-label">Vật tư / Liều lượng</label>
          <input type="text" id="fbLogMat" class="form-input" value="Hữu cơ vi sinh Humic · 2.5 kg/gốc" required>
        </div>
      </div>

      <div class="form-group">
        <label class="form-label">Ghi chú kỹ thuật &amp; Kết quả đo thực địa</label>
        <textarea id="fbLogNotes" class="form-input" rows="3" placeholder="Mô tả chi tiết tình trạng cây, độ ẩm đất, pH..." required>Độ ẩm đất sau tưới đạt 65%, pH ổn định 6.1, bộ lá xanh dày phát triển tốt.</textarea>
      </div>

      <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:6px;">
        <button type="button" class="fb-btn-outline-green" onclick="document.getElementById('fbIotModal').close()">Hủy</button>
        <button type="submit" class="fb-btn-gradient">✓ Lưu Nhật ký Canh tác</button>
      </div>
    </form>
  `;
  dialog.showModal();
  dialog.onclick = (e) => { if (e.target === dialog) dialog.close(); };
}

function fbSubmitNewCareLog(e) {
  e.preventDefault();
  const plotId = document.getElementById('fbLogPlotId')?.value || FB_STATE.selectedPlotId;
  const category = document.getElementById('fbLogCategory')?.value || 'Chăm sóc định kỳ';
  const title = document.getElementById('fbLogTitle')?.value?.trim();
  const technician = document.getElementById('fbLogTech')?.value?.trim() || 'KS. Trần Minh Đức';
  const materials = document.getElementById('fbLogMat')?.value?.trim() || 'Định mức chuẩn';
  const notes = document.getElementById('fbLogNotes')?.value?.trim() || '';

  if (!title) return;

  const now = new Date();
  const hhmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  FB_CARE_LOGS.unshift({
    id: 'LOG-' + Date.now(),
    plotId,
    bucket: 'today',
    bucketLabel: 'Hôm nay',
    dateStr: `30/10/2026 · ${hhmm}`,
    category,
    title,
    technician,
    stageKey: 'young',
    materials,
    notes,
    image: 'assets/farmblock/src_assets_images_view360_vuon_ca_phe_hotspot_b1_drip.webp'
  });

  // Also sync into SmartFarm STATE.logs so #records screen reflects it!
  if (typeof STATE !== 'undefined' && Array.isArray(STATE.logs)) {
    STATE.logs.unshift({
      date: '30/10',
      activity: category.split(' ')[0],
      target: plotId,
      product: materials,
      dosage: title
    });
  }

  document.getElementById('fbIotModal')?.close();
  showToast('Đã lưu nhật ký canh tác mới');
  fbRerenderIotCare();
}
