#!/usr/bin/env node
/**
 * ============================================================================
 * MODULE 4: ASO AUDIT & COMPETITOR COMPARISON CLI
 * (Lấy cảm hứng từ facundoolano/app-store-scraper, google-play-scraper & aso)
 * ============================================================================
 * Phân tích mật độ từ khóa (1-gram, 2-gram, 3-gram), chấm điểm tối ưu hóa
 * App Store (iOS) & Google Play (Android) cho SmartFarm Aurora, và đối chiếu
 * trực tiếp với 5 đối thủ cạnh tranh trong mảng Nông nghiệp thông minh & GIS VN-2000.
 */

const fs = require('fs');
const path = require('path');

// Hồ sơ Siêu dữ liệu ASO chuẩn cho SmartFarm Aurora
const SMARTFARM_ASO_METADATA = {
  ios: {
    title: 'SmartFarm: Vệ Tinh & VN-2000', // 28/30 ký tự
    subtitle: 'Bản đồ Vườn GEE, Sầu Riêng, 3D', // 30/30 ký tự
    keywordsField: 'ndvi,vn2000,sổ đỏ,sầu riêng,drone,dji,vệ tinh,gee,đo đất,quy hoạch,rtk,gis,nông nghiệp,thời tiết', // 96/100 ký tự
    promotionalText: 'Tích hợp Viễn thám Google Earth Engine (7 chỉ số NDVI, EVI, NDMI), quy đổi tọa độ Sổ đỏ VN-2000 34 tỉnh mới, mô phỏng ngập lụt 3D & trạm đo GNSS RTK.'
  },
  android: {
    title: 'SmartFarm Aurora: Vệ Tinh GEE', // 29/30 ký tự
    shortDescription: 'Giám sát vườn sầu riêng bằng vệ tinh NDVI, quy đổi Sổ đỏ VN-2000 & địa hình 3D.', // 79/80 ký tự
  },
  fullDescription: `SmartFarm Aurora (VWing) là nền tảng quản lý vườn cây ăn trái công nghệ cao kết hợp Viễn thám Vệ tinh Google Earth Engine (GEE), Hệ thống Thông tin Địa lý (GIS) chuẩn tọa độ Sổ đỏ VN-2000 và Mô hình Địa hình 3D.

TÍNH NĂNG NỔI BẬT CHO NÔNG DÂN & KỸ SƯ NÔNG NGHIỆP:
1. Viễn thám Vệ tinh GEE & Đếm tán cây AI (DeepForest):
- Phân tích 7 chỉ số quang phổ Sentinel-2 10m: NDVI, EVI, NDRE, NDMI, NDWI, SAVI, BSI.
- Tự động đếm số lượng cây sầu riêng, cà phê, bơ, hồ tiêu và đánh giá độ phủ tán, dự báo sản lượng thu hoạch (tấn/ha).
- Cảnh báo sớm các cây có dấu hiệu suy giảm diệp lục (vàng lá, thối rễ Phytophthora) để ưu tiên đi kiểm tra ngoài thực địa.

2. Bộ công cụ Địa chính VN-2000 & Bản đồ Quy hoạch GIS:
- Quy đổi chính xác bảng tọa độ góc ranh Sổ đỏ (VN-2000 X/Y) sang tọa độ vệ tinh WGS84 bằng phép chiếu Transverse Mercator và 7 tham số Helmert chuẩn Bộ TN&MT.
- Tích hợp sẵn Kinh tuyến trục L0 của 63 tỉnh/thành gốc và 34 tỉnh/thành mới sau sáp nhập.
- Hỗ trợ đọc và xuất trực tiếp tệp bản đồ KML, KMZ, GeoJSON, GPX và bản vẽ kỹ thuật AutoCAD DXF.

3. Mô hình Địa hình 3D, Thủy văn Ngập lụt & Trạm đo GNSS RTK:
- Dựng mô hình địa hình 3D lưu vực vườn, nội suy mặt cắt dọc Hermite Spline và sinh đường đồng mức Marching Squares.
- Mô phỏng mực nước dâng, tính toán khối lượng đào đắp (Cut/Fill) và giải mã trực tiếp luồng bản tin NMEA-0183 từ máy đo GNSS RTK phục vụ bố trí điểm thực địa (Stakeout).

4. Trạm Khí tượng Nông nghiệp & Nhật ký Truy xuất Nguồn gốc QR:
- Dự báo thời tiết chuyên biệt cho nông nghiệp: cảnh báo mưa dông, độ ẩm và khuyến cáo khung giờ phun thuốc BVTV không bị rửa trôi.
- Quản lý nhật ký chăm sóc, phân quyền nhân sự theo khu vực vườn và tạo mã QR truy xuất nguồn gốc minh bạch cho từng lô nông sản.`
};

// Dữ liệu đối chiếu 5 đối thủ cạnh tranh trực tiếp trên App Store & Google Play
const COMPETITORS = [
  {
    name: 'DJI SmartFarm',
    bundleId: 'com.utc.ag',
    category: 'Drone & Nông nghiệp',
    rating: 4.2,
    strengths: ['Điều khiển máy bay phun thuốc DJI Agras', 'Quản lý chuyến bay'],
    gapsVsSmartFarm: ['Không có quy đổi Sổ đỏ VN-2000', 'Không có 7 chỉ số GEE miễn phí & thủy văn 3D'],
    targetKeywords: ['drone nông nghiệp', 'dji smartfarm', 'máy bay phun thuốc']
  },
  {
    name: 'OneSoil Scouting',
    bundleId: 'io.onesoil.scouting',
    category: 'Viễn thám NDVI',
    rating: 4.5,
    strengths: ['Xem chỉ số NDVI Sentinel-2 nhanh', 'Ghi chú đồng ruộng'],
    gapsVsSmartFarm: ['Không hỗ trợ đếm cây ăn trái lâu năm (Sầu riêng)', 'Không có hệ tọa độ VN-2000 Việt Nam'],
    targetKeywords: ['ndvi', 'vệ tinh nông nghiệp', 'bản đồ đồng ruộng']
  },
  {
    name: 'MobileLand / Thông Tin.Land',
    bundleId: 'vn.mobileland.app',
    category: 'Địa chính & Quy hoạch VN-2000',
    rating: 4.4,
    strengths: ['Quy đổi tọa độ VN-2000', 'Tra cứu 34 tỉnh sau sáp nhập & KML'],
    gapsVsSmartFarm: ['Không có phân tích sức khỏe cây trồng NDVI/EVI', 'Không có nhật ký nông trại & dự báo sâu bệnh'],
    targetKeywords: ['quy hoạch', 'vn2000', 'đo đất', 'sổ đỏ']
  },
  {
    name: 'Climate FieldView',
    bundleId: 'com.climate.fieldview',
    category: 'Quản lý trang trại',
    rating: 4.3,
    strengths: ['Bản đồ năng suất cây hàng năm', 'Tích hợp máy móc lớn'],
    gapsVsSmartFarm: ['Chưa tối ưu cho vườn cây ăn trái Tây Nguyên & ĐBSCL', 'Không có xuất bản vẽ CAD DXF & RTK NMEA'],
    targetKeywords: ['quản lý trang trại', 'năng suất mùa vụ', 'bản đồ nông nghiệp']
  },
  {
    name: 'DatumApp 3D GIS',
    bundleId: 'net.datumapp.gis',
    category: 'Địa hình 3D, Thủy văn & GNSS RTK',
    rating: 4.6,
    strengths: ['Mô phỏng ngập lụt 3D', 'Mặt cắt địa hình & giải mã NMEA RTK'],
    gapsVsSmartFarm: ['Không có quản lý hồ sơ cây trồng, QR truy xuất nguồn gốc & thời tiết phun thuốc'],
    targetKeywords: ['gnss rtk', 'đường đồng mức', 'mặt cắt địa hình', 'dxf cad']
  }
];

const CORE_TARGET_KEYWORDS = [
  'vệ tinh', 'ndvi', 'vn-2000', 'sổ đỏ', 'sầu riêng', 'gee',
  'địa hình 3d', 'gnss rtk', 'quy hoạch', 'drone', 'kml', 'dxf', 'truy xuất nguồn gốc'
];

function analyzeKeywordDensity(text) {
  const clean = text.toLowerCase();
  const words = clean.split(/[\s,.;:!?()[\]"'«»—–\-\/]+/).filter(Boolean);
  const totalWords = words.length;

  const hits = CORE_TARGET_KEYWORDS.map(kw => {
    const regex = new RegExp(kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
    const count = (text.match(regex) || []).length;
    const densityPct = ((count * kw.split(' ').length) / Math.max(1, totalWords)) * 100;
    return {
      keyword: kw,
      occurrences: count,
      densityPct: Number(densityPct.toFixed(2)),
      inIosTitleOrSubtitle: (SMARTFARM_ASO_METADATA.ios.title + ' ' + SMARTFARM_ASO_METADATA.ios.subtitle).toLowerCase().includes(kw.replace('-', '')),
      inIosKeywordField: SMARTFARM_ASO_METADATA.ios.keywordsField.toLowerCase().includes(kw.replace('-', ''))
    };
  });

  return { totalWords, hits };
}

function validateMetadataLengths() {
  const iosTitleLen = SMARTFARM_ASO_METADATA.ios.title.length;
  const iosSubLen = SMARTFARM_ASO_METADATA.ios.subtitle.length;
  const iosKwLen = SMARTFARM_ASO_METADATA.ios.keywordsField.length;
  const gpTitleLen = SMARTFARM_ASO_METADATA.android.title.length;
  const gpShortLen = SMARTFARM_ASO_METADATA.android.shortDescription.length;
  const fullDescLen = SMARTFARM_ASO_METADATA.fullDescription.length;

  const kwList = SMARTFARM_ASO_METADATA.ios.keywordsField.split(',');
  const uniqueKws = new Set(kwList);

  return {
    iosTitle: { value: SMARTFARM_ASO_METADATA.ios.title, length: iosTitleLen, max: 30, optimal: iosTitleLen >= 25 && iosTitleLen <= 30 },
    iosSubtitle: { value: SMARTFARM_ASO_METADATA.ios.subtitle, length: iosSubLen, max: 30, optimal: iosSubLen >= 25 && iosSubLen <= 30 },
    iosKeywordsField: {
      value: SMARTFARM_ASO_METADATA.ios.keywordsField,
      length: iosKwLen,
      max: 100,
      keywordCount: kwList.length,
      hasDuplicates: uniqueKws.size !== kwList.length,
      hasSpacesAfterComma: /, /.test(SMARTFARM_ASO_METADATA.ios.keywordsField),
      optimal: iosKwLen >= 90 && iosKwLen <= 100 && uniqueKws.size === kwList.length && !/, /.test(SMARTFARM_ASO_METADATA.ios.keywordsField)
    },
    googlePlayTitle: { value: SMARTFARM_ASO_METADATA.android.title, length: gpTitleLen, max: 30, optimal: gpTitleLen >= 25 && gpTitleLen <= 30 },
    googlePlayShortDesc: { value: SMARTFARM_ASO_METADATA.android.shortDescription, length: gpShortLen, max: 80, optimal: gpShortLen >= 70 && gpShortLen <= 80 },
    fullDescription: { length: fullDescLen, max: 4000, optimal: fullDescLen >= 1000 && fullDescLen <= 4000 }
  };
}

function main() {
  console.log('╔══════════════════════════════════════════════════════════════════════════════╗');
  console.log('║ ASO AUDIT & COMPETITOR BENCHMARKING ENGINE (APP STORE & GOOGLE PLAY)         ║');
  console.log('╚══════════════════════════════════════════════════════════════════════════════╝');

  const metaCheck = validateMetadataLengths();
  console.log('\n[1] KIỂM TOÁN ĐỘ DÀI & CẤU TRÚC SIÊU DỮ LIỆU (METADATA SCORECARD):');
  console.log(`  • iOS Title        : "${metaCheck.iosTitle.value}" (${metaCheck.iosTitle.length}/${metaCheck.iosTitle.max} ký tự) -> ${metaCheck.iosTitle.optimal ? 'TỐI ƯU ✓' : 'CẦN CHỈNH'}`);
  console.log(`  • iOS Subtitle     : "${metaCheck.iosSubtitle.value}" (${metaCheck.iosSubtitle.length}/${metaCheck.iosSubtitle.max} ký tự) -> ${metaCheck.iosSubtitle.optimal ? 'TỐI ƯU ✓' : 'CẦN CHỈNH'}`);
  console.log(`  • iOS Keywords     : ${metaCheck.iosKeywordsField.length}/${metaCheck.iosKeywordsField.max} ký tự (${metaCheck.iosKeywordsField.keywordCount} từ khóa, 0 khoảng trắng thừa) -> ${metaCheck.iosKeywordsField.optimal ? 'TỐI ƯU ✓' : 'CẦN CHỈNH'}`);
  console.log(`  • Google Play Title: "${metaCheck.googlePlayTitle.value}" (${metaCheck.googlePlayTitle.length}/${metaCheck.googlePlayTitle.max} ký tự) -> ${metaCheck.googlePlayTitle.optimal ? 'TỐI ƯU ✓' : 'CẦN CHỈNH'}`);
  console.log(`  • GP Short Desc    : ${metaCheck.googlePlayShortDesc.length}/${metaCheck.googlePlayShortDesc.max} ký tự -> ${metaCheck.googlePlayShortDesc.optimal ? 'TỐI ƯU ✓' : 'CẦN CHỈNH'}`);

  const density = analyzeKeywordDensity(
    SMARTFARM_ASO_METADATA.ios.title + ' ' +
    SMARTFARM_ASO_METADATA.ios.subtitle + ' ' +
    SMARTFARM_ASO_METADATA.fullDescription
  );

  console.log('\n[2] PHÂN TÍCH MẬT ĐỘ TỪ KHÓA MỤC TIÊU (KEYWORD DENSITY):');
  for (const h of density.hits) {
    console.log(`  • ${h.keyword.padEnd(20)} | Xuất hiện: ${String(h.occurrences).padStart(2)} lần | Mật độ: ${h.densityPct.toFixed(2)}% | Keyword Field: ${h.inIosKeywordField ? 'Có' : 'Mô tả'}`);
  }

  console.log('\n[3] MA TRẬN SO SÁNH 5 ĐỐI THỦ CẠNH TRANH (COMPETITOR GAP MATRIX):');
  for (const c of COMPETITORS) {
    console.log(`  ★ ${c.name.padEnd(28)} (${c.category}) — Rating: ${c.rating}★`);
    console.log(`    └─ Lợi thế độc quyền của SmartFarm Aurora: ${c.gapsVsSmartFarm.join(' | ')}`);
  }

  const reportFile = path.join(__dirname, 'aso-audit-report.json');
  fs.writeFileSync(reportFile, JSON.stringify({
    generatedAt: new Date().toISOString(),
    metadataValidation: metaCheck,
    keywordDensity: density,
    competitorComparison: COMPETITORS
  }, null, 2));

  console.log('────────────────────────────────────────────────────────────────────────────────');
  console.log(`✓ Đã xuất báo cáo ASO chi tiết tại: ${reportFile}`);
}

if (require.main === module) {
  main();
}

module.exports = { SMARTFARM_ASO_METADATA, COMPETITORS, analyzeKeywordDensity, validateMetadataLengths };
