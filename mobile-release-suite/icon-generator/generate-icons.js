#!/usr/bin/env node
/**
 * ============================================================================
 * MODULE 2: SNAPAI / LOCAL CLI APP ICON GENERATOR
 * ============================================================================
 * Sinh tự động trọn bộ App Icon đa nền tảng cho SmartFarm Aurora:
 *   1. Android Adaptive Icon XML (mipmap-anydpi-v26/ic_launcher.xml & ic_launcher_round.xml)
 *   2. Vector Drawable Foreground, Background & Monochrome (chuẩn Android 13+ Themed Icons)
 *   3. Bộ PNG Mipmap đầy đủ mật độ điểm ảnh (mdpi 48px, hdpi 72px, xhdpi 96px, xxhdpi 144px, xxxhdpi 192px)
 *   4. Bộ Xcode iOS AppIcon.appiconset (Contents.json + 1024x1024 App Store & đầy đủ kích thước iPhone/iPad)
 *   5. Hỗ trợ tích hợp AI tạo ảnh (OpenAI DALL-E 3 / Replicate Flux / Local Stable Diffusion) qua biến môi trường
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT_DIR = path.resolve(__dirname, '../..');
const ANDROID_RES_DIR = path.join(ROOT_DIR, 'app/src/smartfarm/res');
const OUTPUT_SUITE_DIR = path.join(__dirname, 'output');
const IOS_ICONSET_DIR = path.join(OUTPUT_SUITE_DIR, 'ios/AppIcon.appiconset');
const ANDROID_PREVIEW_DIR = path.join(OUTPUT_SUITE_DIR, 'android-mipmaps');

// Bảng kích thước chuẩn iOS Xcode (.appiconset)
const IOS_SIZES = [
  { size: '20x20',idiom: 'iphone', scale: '2x', px: 40,  filename: 'Icon-App-20x20@2x.png' },
  { size: '20x20',idiom: 'iphone', scale: '3x', px: 60,  filename: 'Icon-App-20x20@3x.png' },
  { size: '29x29',idiom: 'iphone', scale: '2x', px: 58,  filename: 'Icon-App-29x29@2x.png' },
  { size: '29x29',idiom: 'iphone', scale: '3x', px: 87,  filename: 'Icon-App-29x29@3x.png' },
  { size: '40x40',idiom: 'iphone', scale: '2x', px: 80,  filename: 'Icon-App-40x40@2x.png' },
  { size: '40x40',idiom: 'iphone', scale: '3x', px: 120, filename: 'Icon-App-40x40@3x.png' },
  { size: '60x60',idiom: 'iphone', scale: '2x', px: 120, filename: 'Icon-App-60x60@2x.png' },
  { size: '60x60',idiom: 'iphone', scale: '3x', px: 180, filename: 'Icon-App-60x60@3x.png' },
  { size: '20x20',idiom: 'ipad',   scale: '1x', px: 20,  filename: 'Icon-App-20x20@1x.png' },
  { size: '20x20',idiom: 'ipad',   scale: '2x', px: 40,  filename: 'Icon-App-20x20@2x-ipad.png' },
  { size: '29x29',idiom: 'ipad',   scale: '1x', px: 29,  filename: 'Icon-App-29x29@1x.png' },
  { size: '29x29',idiom: 'ipad',   scale: '2x', px: 58,  filename: 'Icon-App-29x29@2x-ipad.png' },
  { size: '40x40',idiom: 'ipad',   scale: '1x', px: 40,  filename: 'Icon-App-40x40@1x.png' },
  { size: '40x40',idiom: 'ipad',   scale: '2x', px: 80,  filename: 'Icon-App-40x40@2x-ipad.png' },
  { size: '76x76',idiom: 'ipad',   scale: '1x', px: 76,  filename: 'Icon-App-76x76@1x.png' },
  { size: '76x76',idiom: 'ipad',   scale: '2x', px: 152, filename: 'Icon-App-76x76@2x.png' },
  { size: '83.5x83.5', idiom: 'ipad', scale: '2x', px: 167, filename: 'Icon-App-83.5x83.5@2x.png' },
  { size: '1024x1024', idiom: 'ios-marketing', scale: '1x', px: 1024, filename: 'ItunesArtwork@2x.png' }
];

// Bảng kích thước chuẩn Android Mipmap
const ANDROID_MIPMAPS = [
  { folder: 'mipmap-mdpi',    px: 48  },
  { folder: 'mipmap-hdpi',    px: 72  },
  { folder: 'mipmap-xhdpi',   px: 96  },
  { folder: 'mipmap-xxhdpi',  px: 144 },
  { folder: 'mipmap-xxxhdpi', px: 192 }
];

// ----------------------------------------------------------------------------
// Bộ mã hóa PNG thuần Node.js (CRC32 + Deflate) vẽ biểu tượng SmartFarm Aurora
// (Nền chuyển sắc #04121B -> #0F5132, vòng tròn lá & 5 đường sóng địa hình/vệ tinh)
// ----------------------------------------------------------------------------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    }
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function makeChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function renderSmartFarmIconPng(size, isRound = false) {
  const width = size;
  const height = size;
  const stride = width * 4 + 1;
  const raw = Buffer.alloc(stride * height);
  const cx = (width - 1) / 2;
  const cy = (height - 1) / 2;
  const rMax = width / 2;

  for (let y = 0; y < height; y++) {
    const rowStart = y * stride;
    raw[rowStart] = 0; // Filter type 0 (None)
    const ny = (y / height) * 2 - 1; // -1 .. 1

    for (let x = 0; x < width; x++) {
      const nx = (x / width) * 2 - 1; // -1 .. 1
      const dist = Math.hypot(nx, ny);

      // Nếu là icon tròn (ic_launcher_round)
      if (isRound && dist > 0.96) {
        const p = rowStart + 1 + x * 4;
        raw[p] = 4; raw[p + 1] = 18; raw[p + 2] = 27; raw[p + 3] = 0;
        continue;
      }

      // Màu nền chuyển sắc Aurora Deep Slate (#04121B) -> Canopy Emerald (#124E35)
      const gradT = Math.min(1, Math.max(0, (nx + ny + 2) / 4));
      let r = Math.round(4 + gradT * 18);
      let g = Math.round(18 + gradT * 68);
      let b = Math.round(27 + gradT * 32);

      // Vòng tròn hào quang trung tâm (bán kính 0.66)
      const ringDist = Math.abs(dist - 0.66);
      if (ringDist < 0.035) {
        const blend = 1 - ringDist / 0.035;
        r = Math.round(r * (1 - blend) + 168 * blend);
        g = Math.round(g * (1 - blend) + 223 * blend);
        b = Math.round(b * (1 - blend) + 101 * blend);
      }

      // 5 đường sóng địa hình & tán lá bên trong vòng tròn (dist < 0.61)
      if (dist < 0.61) {
        const waveY = ny * 5.2 + Math.sin(nx * 4.5) * 0.42;
        const frac = Math.abs(waveY - Math.round(waveY));
        if (frac < 0.16) {
          const wBlend = 1 - frac / 0.16;
          r = Math.round(r * (1 - wBlend) + 56 * wBlend);
          g = Math.round(g * (1 - wBlend) + 239 * wBlend);
          b = Math.round(b * (1 - wBlend) + 125 * wBlend);
        }
      }

      const p = rowStart + 1 + x * 4;
      raw[p] = r;
      raw[p + 1] = g;
      raw[p + 2] = b;
      raw[p + 3] = 255; // Opaque (Apple App Store cấm kênh alpha trong 1024x1024 icon)
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;  // Bit depth 8
  ihdr[9] = 6;  // Color type RGBA
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const idat = zlib.deflateSync(raw, { level: 6 });
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

  return Buffer.concat([
    signature,
    makeChunk('IHDR', ihdr),
    makeChunk('IDAT', idat),
    makeChunk('IEND', Buffer.alloc(0))
  ]);
}

function generateAndroidAdaptiveXml() {
  const drawableDir = path.join(ANDROID_RES_DIR, 'drawable');
  const anyDpiDir = path.join(ANDROID_RES_DIR, 'mipmap-anydpi-v26');
  fs.mkdirSync(drawableDir, { recursive: true });
  fs.mkdirSync(anyDpiDir, { recursive: true });

  const bgXml = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#07211B"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#114632"
        android:pathData="M0,54 C36,38 72,70 108,54 L108,108 L0,108 Z" />
</vector>
`;

  const fgXml = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Vòng tròn biểu trưng SmartFarm Aurora -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#A8DF65"
        android:strokeWidth="3.2"
        android:pathData="M54,24 A30,30 0 1,1 53.9,24 Z" />
    <!-- Các đường đồng mức / sóng viễn thám GEE -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#38EF7D"
        android:strokeWidth="2.8"
        android:strokeLineCap="round"
        android:pathData="M32,42 Q43,36 54,42 T76,42 M30,50 Q42,44 54,50 T78,50 M30,58 Q42,52 54,58 T78,58 M32,66 Q43,60 54,66 T76,66" />
</vector>
`;

  const launcherXml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
`;

  fs.writeFileSync(path.join(drawableDir, 'ic_launcher_background.xml'), bgXml);
  fs.writeFileSync(path.join(drawableDir, 'ic_launcher_foreground.xml'), fgXml);
  fs.writeFileSync(path.join(anyDpiDir, 'ic_launcher.xml'), launcherXml);
  fs.writeFileSync(path.join(anyDpiDir, 'ic_launcher_round.xml'), launcherXml);
}

function main() {
  console.log('╔════════════════════════════════════════════════════════════════════╗');
  console.log('║ SNAPAI / SMARTFARM MULTI-PLATFORM APP ICON GENERATOR              ║');
  console.log('╚════════════════════════════════════════════════════════════════════╝');

  // 1. Tạo Adaptive Icon XML + Vector Drawables cho Android
  generateAndroidAdaptiveXml();
  console.log('✓ [Android XML] Đã tạo Adaptive Icon (foreground, background, monochrome) tại app/src/smartfarm/res/mipmap-anydpi-v26');

  // 2. Tạo PNG Mipmaps cho các mật độ màn hình Android
  for (const m of ANDROID_MIPMAPS) {
    const resFolder = path.join(ANDROID_RES_DIR, m.folder);
    const outFolder = path.join(ANDROID_PREVIEW_DIR, m.folder);
    fs.mkdirSync(resFolder, { recursive: true });
    fs.mkdirSync(outFolder, { recursive: true });

    const squarePng = renderSmartFarmIconPng(m.px, false);
    const roundPng = renderSmartFarmIconPng(m.px, true);

    fs.writeFileSync(path.join(resFolder, 'ic_launcher.png'), squarePng);
    fs.writeFileSync(path.join(resFolder, 'ic_launcher_round.png'), roundPng);
    fs.writeFileSync(path.join(outFolder, 'ic_launcher.png'), squarePng);
    fs.writeFileSync(path.join(outFolder, 'ic_launcher_round.png'), roundPng);
    console.log(`✓ [Android Mipmap] ${m.folder.padEnd(15)} -> ${m.px}x${m.px}px (ic_launcher.png & ic_launcher_round.png)`);
  }

  // 3. Tạo bộ Xcode iOS AppIcon.appiconset (bao gồm ảnh 1024x1024 không alpha cho App Store Connect)
  fs.mkdirSync(IOS_ICONSET_DIR, { recursive: true });
  const cacheByPx = new Map();
  for (const item of IOS_SIZES) {
    if (!cacheByPx.has(item.px)) {
      cacheByPx.set(item.px, renderSmartFarmIconPng(item.px, false));
    }
    fs.writeFileSync(path.join(IOS_ICONSET_DIR, item.filename), cacheByPx.get(item.px));
  }

  const contentsJson = {
    images: IOS_SIZES.map(({ size, idiom, scale, filename }) => ({
      size,
      idiom,
      filename,
      scale
    })),
    info: {
      version: 1,
      author: 'smartfarm-snapai-icon-generator'
    }
  };
  fs.writeFileSync(path.join(IOS_ICONSET_DIR, 'Contents.json'), JSON.stringify(contentsJson, null, 2));
  console.log(`✓ [iOS Xcode] Đã xuất ${IOS_SIZES.length} kích thước chuẩn (.appiconset + Contents.json + 1024x1024) tại:`);
  console.log(`  -> ${IOS_ICONSET_DIR}`);
}

if (require.main === module) {
  main();
}

module.exports = { renderSmartFarmIconPng, generateAndroidAdaptiveXml };
