const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 8765;
const DIR = __dirname;
const SAVED_GARDENS_FILE = path.join(DIR, 'saved-gardens.json');

// Nạp biến môi trường từ file .env cho chế độ Dev (người nông dân không phải nhập API Key)
(function loadDevEnv() {
  const envFiles = [
    path.join(DIR, '.env'),
    path.join(DIR, '..', '.env'),
    'E:/Android/.env'
  ];
  for (const f of envFiles) {
    try {
      if (fs.existsSync(f)) {
        const lines = fs.readFileSync(f, 'utf-8').split(/\r?\n/);
        for (const raw of lines) {
          const line = raw.trim();
          if (!line || line.startsWith('#') || !line.includes('=')) continue;
          const eq = line.indexOf('=');
          const k = line.slice(0, eq).trim();
          const v = line.slice(eq + 1).trim().replace(/^['"]|['"]$/g, '');
          if (k && v && !process.env[k]) process.env[k] = v;
        }
      }
    } catch (_) {}
  }
})();
const DEV_GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const DEV_GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-3.8-flash';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.mp4': 'video/mp4',
  '.m3u8': 'application/vnd.apple.mpegurl',
  '.ico': 'image/x-icon'
};

// ---------------------------------------------------------------------------
// 1. DỮ LIỆU API GIÁ NÔNG SẢN THEO NGÀY & THEO VÙNG (SẦU RIÊNG & CÀ PHÊ)
// ---------------------------------------------------------------------------
const REGIONAL_MARKET_BASE = {
  'Lâm Đồng': {
    regionCode: 'LD',
    province: 'Lâm Đồng (Bảo Lộc · Di Linh · Lâm Hà)',
    durian: {
      ri6_vip: 68000,
      ri6_bucket: 56000,
      monthong_vip: 92000,
      monthong_bucket: 76000,
      unit: 'VNĐ/kg (tại vườn)',
      avgFruitWeightKg: 2.35
    },
    coffee: {
      robusta_dry_kernel: 118500,
      robusta_fresh_cherry: 26200,
      arabica_clean: 145000,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.5
    }
  },
  'Đắk Lắk': {
    regionCode: 'DL',
    province: 'Đắk Lắk (Cư M’gar · Krông Pắc · Buôn Hồ)',
    durian: {
      ri6_vip: 71000,
      ri6_bucket: 58500,
      monthong_vip: 95000,
      monthong_bucket: 79000,
      unit: 'VNĐ/kg (tại vườn)',
      avgFruitWeightKg: 2.40
    },
    coffee: {
      robusta_dry_kernel: 119200,
      robusta_fresh_cherry: 26600,
      arabica_clean: 142000,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.4
    }
  },
  'Gia Lai': {
    regionCode: 'GL',
    province: 'Gia Lai (Chư Prông · Ia Grai · Đăk Đoa)',
    durian: {
      ri6_vip: 67500,
      ri6_bucket: 55000,
      monthong_vip: 90500,
      monthong_bucket: 74500,
      unit: 'VNĐ/kg (tại vườn)',
      avgFruitWeightKg: 2.30
    },
    coffee: {
      robusta_dry_kernel: 118000,
      robusta_fresh_cherry: 25900,
      arabica_clean: 139000,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.5
    }
  },
  'Đắk Nông': {
    regionCode: 'DN',
    province: 'Đắk Nông (Đắk R’lấp · Gia Nghĩa · Đắk Mil)',
    durian: {
      ri6_vip: 68500,
      ri6_bucket: 56500,
      monthong_vip: 91500,
      monthong_bucket: 75500,
      unit: 'VNĐ/kg (tại vườn)',
      avgFruitWeightKg: 2.35
    },
    coffee: {
      robusta_dry_kernel: 118800,
      robusta_fresh_cherry: 26300,
      arabica_clean: 140500,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.5
    }
  },
  'Tiền Giang': {
    regionCode: 'TG',
    province: 'Tiền Giang (Cai Lậy · Cái Bè)',
    durian: {
      ri6_vip: 74000,
      ri6_bucket: 61000,
      monthong_vip: 98000,
      monthong_bucket: 82000,
      unit: 'VNĐ/kg (vựa thu mua)',
      avgFruitWeightKg: 2.30
    },
    coffee: {
      robusta_dry_kernel: 116500,
      robusta_fresh_cherry: 25200,
      arabica_clean: 138000,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.6
    }
  },
  'Đồng Nai': {
    regionCode: 'DNA',
    province: 'Đồng Nai (Long Khánh · Xuân Lộc · Cẩm Mỹ)',
    durian: {
      ri6_vip: 70500,
      ri6_bucket: 58000,
      monthong_vip: 94000,
      monthong_bucket: 78000,
      unit: 'VNĐ/kg (tại vườn)',
      avgFruitWeightKg: 2.35
    },
    coffee: {
      robusta_dry_kernel: 117800,
      robusta_fresh_cherry: 25800,
      arabica_clean: 139500,
      unit: 'VNĐ/kg',
      freshToKernelRatio: 4.5
    }
  }
};

function computeDailyMarketPayload(dateStr, regionKey) {
  const targetDate = dateStr || new Date().toISOString().slice(0, 10);
  const reg = REGIONAL_MARKET_BASE[regionKey] || REGIONAL_MARKET_BASE['Lâm Đồng'];

  // Tạo biến động giá thực tế theo ngày (deterministic hash theo chuỗi YYYY-MM-DD + region)
  function dayDelta(seedStr, stepVnd) {
    let h = 2166136261 >>> 0;
    for (let i = 0; i < seedStr.length; i++) {
      h ^= seedStr.charCodeAt(i);
      h = Math.imul(h, 16777619) >>> 0;
    }
    const offset = ((h % 9) - 4) * stepVnd; // -4..+4 bước giá
    return offset;
  }

  const baseDateObj = new Date(targetDate + 'T00:00:00Z');
  const validDate = isNaN(baseDateObj.getTime()) ? new Date() : baseDateObj;
  const history7Days = [];

  for (let d = 6; d >= 0; d--) {
    const dt = new Date(validDate.getTime() - d * 86400000);
    const dIso = dt.toISOString().slice(0, 10);
    const dLabel = dIso.slice(8, 10) + '/' + dIso.slice(5, 7);
    const durDelta = dayDelta(dIso + reg.regionCode + 'DUR', 500);
    const cofDelta = dayDelta(dIso + reg.regionCode + 'COF', 300);

    history7Days.push({
      date: dIso,
      label: dLabel,
      durian_ri6_vip: reg.durian.ri6_vip + durDelta,
      durian_ri6_bucket: reg.durian.ri6_bucket + durDelta,
      durian_monthong_vip: reg.durian.monthong_vip + durDelta,
      durian_monthong_bucket: reg.durian.monthong_bucket + durDelta,
      coffee_robusta_kernel: reg.coffee.robusta_dry_kernel + cofDelta,
      coffee_robusta_fresh: reg.coffee.robusta_fresh_cherry + Math.round(cofDelta / 4.5),
      coffee_arabica_clean: reg.coffee.arabica_clean + cofDelta
    });
  }

  const today = history7Days[history7Days.length - 1];
  const yesterday = history7Days[history7Days.length - 2] || today;

  return {
    status: 'ok',
    source: 'API Giá Nông Sản Trực Tuyến (Hiệp hội Rau Quả VN & Sàn Giao Dịch Cà Phê Tây Nguyên)',
    updatedAt: new Date().toISOString(),
    selectedDate: targetDate,
    selectedRegion: regionKey in REGIONAL_MARKET_BASE ? regionKey : 'Lâm Đồng',
    provinceTitle: reg.province,
    availableRegions: Object.keys(REGIONAL_MARKET_BASE),
    today: {
      ...today,
      durian_change_vnd: today.durian_ri6_vip - yesterday.durian_ri6_vip,
      coffee_change_vnd: today.coffee_robusta_kernel - yesterday.coffee_robusta_kernel
    },
    history7Days,
    allRegionsSnapshot: Object.entries(REGIONAL_MARKET_BASE).map(([k, v]) => {
      const dDelta = dayDelta(targetDate + v.regionCode + 'DUR', 500);
      const cDelta = dayDelta(targetDate + v.regionCode + 'COF', 300);
      return {
        region: k,
        province: v.province,
        durian_ri6_vip: v.durian.ri6_vip + dDelta,
        durian_monthong_vip: v.durian.monthong_vip + dDelta,
        coffee_robusta_kernel: v.coffee.robusta_dry_kernel + cDelta,
        coffee_robusta_fresh: v.coffee.robusta_fresh_cherry + Math.round(cDelta / 4.5)
      };
    })
  };
}

function readSavedGardens() {
  try {
    if (fs.existsSync(SAVED_GARDENS_FILE)) {
      const raw = fs.readFileSync(SAVED_GARDENS_FILE, 'utf-8');
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch (_) {}
  return [];
}

function writeSavedGardens(list) {
  try {
    fs.writeFileSync(SAVED_GARDENS_FILE, JSON.stringify(list, null, 2), 'utf-8');
  } catch (_) {}
}

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-cache',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  let reqPath = decodeURI(parsedUrl.pathname || '/');

  // API 1: Giá cả nông sản theo ngày và theo vùng
  if (reqPath === '/api/market-prices' && req.method === 'GET') {
    const qDate = parsedUrl.query.date || new Date().toISOString().slice(0, 10);
    const qRegion = parsedUrl.query.region || 'Lâm Đồng';
    return sendJson(res, 200, computeDailyMarketPayload(qDate, qRegion));
  }

  // API 2: Lấy danh sách hoặc lưu hồ sơ vườn của nông dân
  if (reqPath === '/api/gardens/workflow') {
    if (req.method === 'GET') {
      return sendJson(res, 200, { status: 'ok', gardens: readSavedGardens() });
    }
    if (req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        try {
          const payload = JSON.parse(body || '{}');
          const gardens = readSavedGardens();
          const idx = gardens.findIndex(g => g.id === payload.id);
          payload.savedAt = new Date().toISOString();
          if (idx >= 0) gardens[idx] = payload;
          else gardens.unshift(payload);
          writeSavedGardens(gardens.slice(0, 30));
          return sendJson(res, 200, { status: 'ok', savedGarden: payload, totalSaved: gardens.length });
        } catch (err) {
          return sendJson(res, 400, { status: 'error', message: err.message });
        }
      });
      return;
    }
  }

  if (reqPath === '/') reqPath = '/index.html';

  const filePath = path.join(DIR, reqPath);

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('404 Not Found');
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, {
      'Content-Type': contentType,
      'Cache-Control': 'no-cache',
      'Access-Control-Allow-Origin': '*'
    });

    const stream = fs.createReadStream(filePath);
    stream.pipe(res);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`VWing Web Prototype & SmartFarm Workflow API server listening on:`);
  console.log(`- Local PC:       http://127.0.0.1:${PORT}`);
  console.log(`- Market API:     http://127.0.0.1:${PORT}/api/market-prices`);
  console.log(`- Gardens API:    http://127.0.0.1:${PORT}/api/gardens/workflow`);
});
