/**
 * VWing — Minimalist Boardroom UI (Tern by Mahid · Dribbble #27814390)
 */

const STATE = {
  period: 'month',
  displayUnit: 'VND',
  activeGeeIndex: 'NDVI',
  mapLayer: 'satellite',

  polygonVertices: [
    { x: 118, y: 66,  lat: 11.5511, lng: 107.7996 },
    { x: 468, y: 62,  lat: 11.5512, lng: 107.8012 },
    { x: 548, y: 158, lat: 11.5504, lng: 107.8016 },
    { x: 446, y: 252, lat: 11.5497, lng: 107.8011 },
    { x: 136, y: 238, lat: 11.5498, lng: 107.7997 }
  ],

  garden: {
    name: 'Vườn Tây Nguyên',
    location: 'Bảo Lộc',
    crop: 'Sầu riêng',
    cropKey: 'durian',
    area: 1.8,
    perimeterM: 542,
    avgFruitWeight: 2.2,
    pricePerKg: 62000,
    priceSource: 'Thương lái Bảo Lộc'
  },

  geeAnalysis: {
    runwayDays: 18.4,
    estimatedTrees: 185,
    canopyCoverPct: 76.2,
    healthyTrees: 146,
    moderateTrees: 28,
    stressedTrees: 11,
    indices: [
      { index: 'NDVI', label: 'Độ xanh tán', mean: 0.74 },
      { index: 'EVI',  label: 'Sinh khối',   mean: 0.58 },
      { index: 'NDRE', label: 'Diệp lục',    mean: 0.44 },
      { index: 'NDMI', label: 'Độ ẩm tán',   mean: 0.28 },
      { index: 'SAVI', label: 'Che phủ đất', mean: 0.52 },
      { index: 'BSI',  label: 'Đất trống',   mean: -0.24 }
    ],
    lulcSources: [
      { name: 'Dynamic World 10m', treePct: 76, cropPct: 12, otherPct: 12 },
      { name: 'ESA WorldCover 10m', treePct: 78, cropPct: 11, otherPct: 11 },
      { name: 'JAXA Vietnam 10m', treePct: 76, cropPct: 13, otherPct: 11 },
      { name: 'SERVIR-Mekong 30m', treePct: 74, cropPct: 15, otherPct: 11 }
    ]
  },

  trees: [
    { id: 'A-01', zone: 'Khu A', fruits: 54, ndvi: 0.78, status: 'stable', note: 'Ổn định' },
    { id: 'A-02', zone: 'Khu A', fruits: 58, ndvi: 0.81, status: 'stable', note: 'Ổn định' },
    { id: 'A-03', zone: 'Khu A', fruits: 48, ndvi: 0.64, status: 'watch',  note: 'Theo dõi ẩm' },
    { id: 'A-04', zone: 'Khu A', fruits: 46, ndvi: 0.52, status: 'alert',  note: 'Kiểm tra lá' },
    { id: 'A-05', zone: 'Khu A', fruits: 50, ndvi: 0.75, status: 'stable', note: 'Ổn định' },
    { id: 'A-06', zone: 'Khu A', fruits: 52, ndvi: 0.77, status: 'stable', note: 'Ổn định' },

    { id: 'B-01', zone: 'Khu B', fruits: 60, ndvi: 0.82, status: 'stable', note: 'Ổn định' },
    { id: 'B-02', zone: 'Khu B', fruits: 55, ndvi: 0.79, status: 'stable', note: 'Ổn định' },
    { id: 'B-03', zone: 'Khu B', fruits: 49, ndvi: 0.63, status: 'watch',  note: 'Đất dốc' },
    { id: 'B-04', zone: 'Khu B', fruits: 53, ndvi: 0.76, status: 'stable', note: 'Ổn định' },
    { id: 'B-05', zone: 'Khu B', fruits: 46, ndvi: 0.54, status: 'alert',  note: 'Sau bón phân' },
    { id: 'B-06', zone: 'Khu B', fruits: 57, ndvi: 0.80, status: 'stable', note: 'Ổn định' },

    { id: 'C-01', zone: 'Khu C', fruits: 56, ndvi: 0.79, status: 'stable', note: 'Ổn định' },
    { id: 'C-02', zone: 'Khu C', fruits: 52, ndvi: 0.76, status: 'stable', note: 'Ổn định' },
    { id: 'C-03', zone: 'Khu C', fruits: 50, ndvi: 0.66, status: 'watch',  note: 'Theo dõi đọt' },
    { id: 'C-04', zone: 'Khu C', fruits: 54, ndvi: 0.78, status: 'stable', note: 'Ổn định' },
    { id: 'C-05', zone: 'Khu C', fruits: 53, ndvi: 0.77, status: 'stable', note: 'Ổn định' },
    { id: 'C-06', zone: 'Khu C', fruits: 55, ndvi: 0.79, status: 'stable', note: 'Ổn định' },

    { id: 'D-01', zone: 'Khu D', fruits: 52, ndvi: 0.75, status: 'stable', note: 'Ổn định' },
    { id: 'D-02', zone: 'Khu D', fruits: 44, ndvi: 0.49, status: 'alert',  note: 'Cần xác nhận' },
    { id: 'D-03', zone: 'Khu D', fruits: 48, ndvi: 0.65, status: 'watch',  note: 'Tỉa cành' },
    { id: 'D-04', zone: 'Khu D', fruits: 51, ndvi: 0.74, status: 'stable', note: 'Ổn định' },
    { id: 'D-05', zone: 'Khu D', fruits: 53, ndvi: 0.76, status: 'stable', note: 'Ổn định' },
    { id: 'D-06', zone: 'Khu D', fruits: 58, ndvi: 0.81, status: 'stable', note: 'Ổn định' }
  ],

  logs: [
    { date: '10/10', activity: 'Kiểm tra', target: 'A-04', product: 'Quan sát lá', dosage: '1 lần' },
    { date: '08/10', activity: 'Bón phân', target: 'B-05', product: 'Hữu cơ vi sinh', dosage: '2 kg' },
    { date: '05/10', activity: 'Tưới nước', target: 'Khu C', product: 'Tưới nhỏ giọt', dosage: '45 phút' },
    { date: '02/10', activity: 'Phun thuốc', target: 'D-02', product: 'Sinh học', dosage: '1 lần' }
  ],

  chatMessages: [
    { sender: 'user', text: 'Tóm tắt tình trạng vườn hôm nay?' },
    {
      sender: 'assistant',
      intro: '185 cây (GEE 10m) · Độ phủ tán 76% · 3 cây cần kiểm tra: A-04, B-05, D-02.',
      items: [
        { tree: 'A-04', zone: 'Khu A', issue: 'NDVI 0.52' },
        { tree: 'B-05', zone: 'Khu B', issue: 'NDVI 0.54' },
        { tree: 'D-02', zone: 'Khu D', issue: 'NDVI 0.49' }
      ]
    }
  ],

  selectedTreeId: 'A-04',
  currentScreen: 'overview'
};

const FIGMA_SCREENS = [
  { id: 'dribbble_tern', title: '★ Dribbble Tern UI (#27814390)', file: 'figma-screens/dribbble_tern_reference.png' },
  { id: '01_tong_quan', title: '01. Tổng quan', file: 'figma-screens/01_tong_quan.png' },
  { id: '02_khoanh_vung_gmap', title: '02. Khoanh vùng Google Map', file: 'figma-screens/02_khoanh_vung_google_map.png' },
  { id: '02_ban_do_vuon', title: '03. Bản đồ GEE & Đếm cây', file: 'figma-screens/02_ban_do_vuon.png' },
  { id: '02b_ho_so_cay', title: '04. Hồ sơ cây', file: 'figma-screens/02b_ho_so_cay.png' },
  { id: '03_canh_bao', title: '05. Kiểm tra', file: 'figma-screens/06_canh_bao_kiem_tra.png' },
  { id: '04_nhat_ky', title: '06. Nhật ký', file: 'figma-screens/03_nhat_ky_cham_soc.png' },
  { id: '05_du_bao', title: '07. Dự báo', file: 'figma-screens/04_san_luong_gia.png' },
  { id: '06_bao_cao_ai', title: '08. Báo cáo AI', file: 'figma-screens/05_tro_ly_ai.png' },
  { id: '07_ghi_chep', title: '09. Ghi chép', file: 'figma-screens/08_ghi_hoat_dong_cham_soc.png' },
  { id: '08_cai_dat', title: '10. Cài đặt', file: 'figma-screens/00_thiet_lap_du_lieu_vuon.png' }
];

function getSummaryStats() {
  const totalTrees = STATE.trees.length;
  const totalFruits = STATE.trees.reduce((acc, t) => acc + t.fruits, 0);
  const alertTrees = STATE.trees.filter(t => t.status === 'alert');
  const watchTrees = STATE.trees.filter(t => t.status === 'watch');
  const stableTrees = STATE.trees.filter(t => t.status === 'stable');

  const totalKg = totalFruits * STATE.garden.avgFruitWeight;
  const totalTon = (totalKg / 1000).toFixed(2);
  const totalRevenue = totalKg * STATE.garden.pricePerKg;
  const meanNdvi = (STATE.trees.reduce((acc, t) => acc + (t.ndvi || 0.74), 0) / totalTrees).toFixed(2);
  const healthyRatioPct = Math.round(((stableTrees.length + watchTrees.length * 0.5) / totalTrees) * 100);

  return {
    totalTrees,
    totalFruits,
    alertCount: alertTrees.length,
    watchCount: watchTrees.length,
    stableCount: stableTrees.length,
    totalKg,
    totalTon,
    totalRevenue,
    alertTrees,
    watchTrees,
    meanNdvi,
    healthyRatioPct
  };
}

function formatVND(amount) {
  return new Intl.NumberFormat('vi-VN').format(Math.round(amount)) + ' ₫';
}

function showToast(msg) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.textContent = msg;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 2600);
}

function updateSidebarBatteryAndBadges() {
  const stats = getSummaryStats();
  const badge = document.getElementById('sidebarAlertBadge');
  if (badge) badge.textContent = stats.alertCount;

  const runwayEl = document.getElementById('sidebarRunwayDays');
  if (runwayEl) runwayEl.textContent = STATE.geeAnalysis.runwayDays.toFixed(1);

  const barsContainer = document.getElementById('sidebarBatteryBars');
  if (barsContainer) {
    const totalBars = 22;
    const activeBars = Math.min(totalBars, Math.max(4, Math.round((stats.healthyRatioPct / 100) * totalBars)));
    let html = '';
    for (let i = 0; i < totalBars; i++) {
      html += `<span class="${i < activeBars ? 'bar on' : 'bar'}"></span>`;
    }
    barsContainer.innerHTML = html;
  }
}

function setDashboardPeriod(period, btnEl) {
  STATE.period = period;
  document.querySelectorAll('.period-pill').forEach(b => b.classList.remove('active'));
  if (btnEl) btnEl.classList.add('active');
  if (STATE.currentScreen === 'overview') {
    document.getElementById('mainView').innerHTML = renderOverview();
  }
}

function toggleDisplayUnit() {
  const order = ['VND', 'TẤN', 'CÂY'];
  STATE.displayUnit = order[(order.indexOf(STATE.displayUnit) + 1) % order.length];
  const lbl = document.getElementById('unitPillLabel');
  if (lbl) lbl.textContent = STATE.displayUnit;
  if (STATE.currentScreen === 'overview') {
    document.getElementById('mainView').innerHTML = renderOverview();
  }
}

function openQuickSearch() {
  const modal = document.getElementById('quickSearchModal');
  if (!modal) return;
  modal.showModal();
  const inp = document.getElementById('quickSearchInput');
  if (inp) {
    inp.value = '';
    renderQuickSearchResults('');
    setTimeout(() => inp.focus(), 40);
  }
}

function renderQuickSearchResults(query) {
  const container = document.getElementById('quickSearchResults');
  if (!container) return;
  const q = (query || '').trim().toLowerCase();
  const list = STATE.trees.filter(t => !q || t.id.toLowerCase().includes(q) || t.zone.toLowerCase().includes(q)).slice(0, 4);

  const fbItems = [
    { title: '🌐 Không gian 360° Panorama & Hotspot (3 Vườn)', sub: 'Cà Phê 1 · Cà Phê 2 · Sầu Riêng Musang King · Panoee Tour', right: 'WebGL 360°', screen: 'panorama-360' },
    { title: '🗺️ Bản đồ Lô đất Mapbox & Chia sẻ QR', sub: 'LOT-IAKO-01..06 · 4 chế độ bản đồ 3D · VietQR', right: '6 Lô đất', screen: 'land-plots' },
    { title: '📹 Camera Live VLC & Thanh Timeline 24h', sub: 'CAM-IAKO-01..04 · Tua nhanh Scrub Preview · Toàn màn hình', right: 'LIVE 24h', screen: 'camera-live' },
    { title: '📊 19 Chỉ số IoT Thực địa & Nhật ký Canh tác', sub: 'Thời tiết (7) · Nước (5) · Đất (7) · Dự báo Open-Meteo 7 ngày', right: '19 Metrics', screen: 'iot-care' }
  ].filter(item => !q || item.title.toLowerCase().includes(q) || item.sub.toLowerCase().includes(q));

  container.innerHTML = [
    ...fbItems.map(item => `
      <div class="queue-item" onclick="document.getElementById('quickSearchModal').close(); navigateTo('${item.screen}')">
        <div>
          <div class="queue-item-title">${item.title}</div>
          <div class="queue-item-sub">${item.sub}</div>
        </div>
        <div class="queue-item-right" style="color:#008242; font-weight:700;">${item.right}</div>
      </div>
    `),
    ...list.map(t => `
      <div class="queue-item" onclick="document.getElementById('quickSearchModal').close(); selectTreeAndGo('${t.id}')">
        <div>
          <div class="queue-item-title">Cây ${t.id}</div>
          <div class="queue-item-sub">${t.zone} · ${t.note}</div>
        </div>
        <div class="queue-item-right">${t.fruits} trái</div>
      </div>
    `)
  ].join('');
}

// =============================================================================
// 01. TỔNG QUAN (ULTRA-MINIMAL TERN BOARDROOM VIEW — FITS 1 SCREEN)
// =============================================================================
function renderOverview() {
  const stats = getSummaryStats();
  const gee = STATE.geeAnalysis;
  const mult = { today: 0.04, week: 0.25, month: 1.0, season: 1.0 }[STATE.period] || 1.0;

  let heroTitle = 'Tổng giá trị vụ mùa';
  let heroMainValue = formatVND(stats.totalRevenue * mult);

  if (STATE.displayUnit === 'TẤN') {
    heroTitle = 'Tổng sản lượng';
    heroMainValue = `${(stats.totalTon * mult).toFixed(2)} tấn`;
  } else if (STATE.displayUnit === 'CÂY') {
    heroTitle = 'Quy mô vườn (GEE)';
    heroMainValue = `${gee.estimatedTrees} cây`;
  }

  const zones = ['Khu A', 'Khu B', 'Khu C', 'Khu D'].map((zName, idx) => {
    const zTrees = STATE.trees.filter(t => t.zone === zName);
    const zFruits = zTrees.reduce((a, b) => a + b.fruits, 0);
    const zRev = zFruits * STATE.garden.avgFruitWeight * STATE.garden.pricePerKg;
    const colors = ['#0D2117', '#2B503F', '#487D49', '#7B9A61'];
    return {
      name: zName,
      fruits: zFruits,
      valText: STATE.displayUnit === 'VND' ? `${(zRev / 1000000).toFixed(1)} tr ₫` : `${zFruits} trái`,
      color: colors[idx]
    };
  });

  const matrixLevels = [4, 4, 3, 3, 2, 2, 3, 4, 4, 5, 5, 6];

  return `
    <!-- ROW 1: HERO + FLOATING GLASS CARD -->
    <section class="tern-hero">
      <svg class="tern-hero-bleed-art" viewBox="0 0 500 360" fill="none" aria-hidden="true">
        <g stroke="#2B503F" stroke-width="1.1" opacity="0.55" stroke-linecap="round">
          <path d="M20 300C90 270 130 300 200 262S320 190 390 214 470 262 490 240"/>
          <path d="M30 262C95 228 140 262 205 224S318 150 388 176 462 224 486 202"/>
          <path d="M44 224C100 190 148 222 210 186S316 112 386 138 454 186 480 164"/>
          <path d="M62 186C108 154 154 182 216 148S314 78 384 100 446 148 472 126"/>
          <path d="M84 148C120 120 162 142 222 110S312 46 380 64 438 110 462 90"/>
          <path d="M112 110C140 88 172 102 228 74S310 18 372 30 424 72 448 56"/>
        </g>
      </svg>

      <div class="tern-hero-left">
        <div>
          <div class="hero-eyebrow">${heroTitle}</div>
          <div class="hero-giant-number">${heroMainValue}</div>
          <div class="hero-delta-row">
            <span class="hero-delta-positive">+7.9%</span> tháng này
          </div>
        </div>

        <div class="hero-sub-kpis">
          <div class="hero-sub-kpi-item" onclick="navigateTo('yield')">
            <div class="hero-sub-kpi-val">${stats.totalTon} tấn</div>
            <div class="hero-sub-kpi-label">Sản lượng</div>
          </div>

          <div class="hero-sub-kpi-item" onclick="navigateTo('map')">
            <div class="hero-sub-kpi-val">${gee.estimatedTrees} cây</div>
            <div class="hero-sub-kpi-label">Đếm cây GEE</div>
          </div>

          <div class="hero-sub-kpi-item" onclick="navigateTo('map')">
            <div class="hero-sub-kpi-val">${gee.canopyCoverPct}%</div>
            <div class="hero-sub-kpi-label">Độ phủ tán</div>
          </div>
        </div>
      </div>

      <div class="tern-glass-rail-card">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Khu vực vườn</span>
            <button class="tcard-arrow" onclick="navigateTo('map')">↗</button>
          </div>

          <div class="segmented-volume-bar">
            <span style="width: 26%; background: #0D2117;"></span>
            <span style="width: 25%; background: #366536;"></span>
            <span style="width: 24%; background: #487D49;"></span>
            <span style="width: 16%; background: #7B9A61;"></span>
            <span style="width: 9%; background: #BAC3C1;"></span>
          </div>

          <div class="rail-list">
            ${zones.map(z => `
              <div class="rail-row" onclick="navigateTo('map')">
                <div class="rail-row-left">
                  <span class="rail-dot" style="background: ${z.color};"></span>
                  <span>${z.name}</span>
                </div>
                <span class="rail-row-right">${z.valText}</span>
              </div>
            `).join('')}
            <div class="rail-row" onclick="navigateTo('map')">
              <div class="rail-row-left">
                <span class="rail-dot" style="background: #BAC3C1;"></span>
                <span>Đất trống (BSI)</span>
              </div>
              <span class="rail-row-right">0.16 ha · 9%</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ROW 2: 3 BENTO CARDS -->
    <section class="tern-bento-grid">
      <!-- Card 1: Forecast -->
      <div class="tern-forecast-card">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Dự báo tán (NDVI)</span>
            <button class="tcard-arrow" onclick="navigateTo('map')">↗</button>
          </div>
          <div class="bento-big-metric">${stats.meanNdvi}</div>
          <div class="bento-sub-caption">Thấp nhất: Tuần 6</div>
        </div>

        <div class="pill-matrix-chart">
          ${matrixLevels.map((lvl, colIdx) => {
            const isLowPoint = colIdx === 5;
            let cells = '';
            for (let r = 1; r <= 6; r++) {
              let cls = 'matrix-cell';
              if (r <= lvl) cls += isLowPoint ? ' low-point' : ' filled';
              cells += `<div class="${cls}"></div>`;
            }
            return `<div class="matrix-col">${cells}</div>`;
          }).join('')}
        </div>

        <div class="forecast-legend">
          <span><i class="rail-dot" style="background:#DCE5E0;"></i> Dự báo</span>
          <span><i class="rail-dot" style="background:#A8DF65;"></i> Điểm thấp</span>
        </div>
      </div>

      <!-- Card 2: Inspection Queue -->
      <div class="tern-light-bento-card">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Lịch kiểm tra</span>
            <button class="tcard-arrow" onclick="navigateTo('alerts')">↗</button>
          </div>
          <div class="bento-big-metric">${stats.alertCount} cây</div>
          <div class="bento-sub-caption">Trong 7 ngày tới</div>

          <div class="queue-list">
            ${stats.alertTrees.slice(0, 3).map((tree, idx) => `
              <div class="queue-item" onclick="selectTreeAndGo('${tree.id}')">
                <div>
                  <div class="queue-item-title">Cây ${tree.id}</div>
                  <div class="queue-item-sub ${idx === 2 ? 'needs-approval' : ''}">
                    ${idx === 2 ? 'Cần xác nhận sau phun thuốc' : tree.zone}
                  </div>
                </div>
                <div class="queue-item-right">NDVI ${tree.ndvi.toFixed(2)}</div>
              </div>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- Card 3: LULC Treemap -->
      <div class="tern-light-bento-card">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Lớp phủ GEE</span>
            <button class="tcard-arrow" onclick="navigateTo('map')">↗</button>
          </div>
          <div class="bento-big-metric">${STATE.garden.area.toFixed(2)} ha</div>
          <div class="bento-sub-caption">Tháng này</div>
        </div>

        <div class="spend-treemap-grid">
          <div class="treemap-box primary" onclick="navigateTo('map')">
            <span class="tm-label">Tán cây</span>
            <strong class="tm-val">76%</strong>
          </div>
          <div class="treemap-box secondary" onclick="navigateTo('map')">
            <span class="tm-label">Xen canh</span>
            <strong class="tm-val">12%</strong>
          </div>
          <div class="treemap-mid-split">
            <div class="treemap-box tertiary" onclick="navigateTo('map')">
              <span class="tm-label">Cỏ</span>
              <strong class="tm-val" style="font-size:15px;">5%</strong>
            </div>
            <div class="treemap-box tertiary" onclick="navigateTo('map')">
              <span class="tm-label">Đất</span>
              <strong class="tm-val" style="font-size:15px;">4%</strong>
            </div>
          </div>
          <div class="treemap-box quaternary" onclick="navigateTo('map')">
            <span class="tm-label">Khác</span>
            <strong class="tm-val">3%</strong>
          </div>
        </div>
      </div>
    </section>

    ${typeof renderFarmBlockOverviewHub === 'function' ? renderFarmBlockOverviewHub() : ''}
  `;
}

// =============================================================================
// 02. KHOANH VÙNG VƯỜN TRÊN GOOGLE MAPS (#google-map)
// =============================================================================
function renderGoogleMapMarking() {
  const pts = STATE.polygonVertices;
  const gee = STATE.geeAnalysis;

  // Scale vertices to 900x756 full-height Google Map canvas
  const fullPts = pts.map(p => ({
    x: Math.round(p.x * 1.38),
    y: Math.round(p.y * 2.35),
    lat: p.lat,
    lng: p.lng
  }));
  const polyAttr = fullPts.map(p => `${p.x},${p.y}`).join(' ');

  // Edge midpoints & lengths for Google Maps distance labels
  const edgeDistances = ['142 m', '118 m', '134 m', '96 m', '52 m'];
  const edgeMidpoints = fullPts.map((p, i) => {
    const next = fullPts[(i + 1) % fullPts.length];
    return {
      x: Math.round((p.x + next.x) / 2),
      y: Math.round((p.y + next.y) / 2),
      label: edgeDistances[i % edgeDistances.length]
    };
  });

  // 4x3 Google Satellite Hybrid tiles (z=18 around Bảo Lộc, Lâm Đồng)
  const fullTiles = [
    { x: 209563, y: 122791 }, { x: 209564, y: 122791 }, { x: 209565, y: 122791 }, { x: 209566, y: 122791 },
    { x: 209563, y: 122792 }, { x: 209564, y: 122792 }, { x: 209565, y: 122792 }, { x: 209566, y: 122792 },
    { x: 209563, y: 122793 }, { x: 209564, y: 122793 }, { x: 209565, y: 122793 }, { x: 209566, y: 122793 }
  ];

  return `
    <div class="gmap-full-layout">
      <!-- Left: Full-Height Interactive Google Maps Polygon-Marking Canvas -->
      <div class="gmap-full-canvas" id="gmapFullCanvas" onclick="handleFullGmapClick(event)">
        <div class="gmap-full-tiles">
          ${fullTiles.map(t => `
            <img src="https://mt1.google.com/vt/lyrs=y&x=${t.x}&y=${t.y}&z=18" alt="Google Maps Satellite" loading="eager" onerror="this.style.opacity='0'">
          `).join('')}
        </div>

        <!-- Google Maps Floating Search Bar -->
        <div class="gmap-search-floating" onclick="event.stopPropagation()">
          <span style="color:#EA4335; font-size:16px;">📍</span>
          <input type="text" value="Bảo Lộc, Lâm Đồng (11.5505°N, 107.8005°E)" readonly>
          <span class="tag-activity" style="background:#0D2117; color:#A8DF65;">Google Maps</span>
        </div>

        <!-- Google Maps Drawing Mode Toolbar -->
        <div class="gmap-draw-toolbar" onclick="event.stopPropagation()">
          <button class="gmap-tool-btn active">✏️ Chấm đỉnh đa giác (${pts.length})</button>
          <button class="gmap-tool-btn" onclick="undoLastVertex()">↩ Xóa đỉnh</button>
          <button class="gmap-tool-btn" onclick="resetSamplePolygon()">⟳ Vùng mẫu</button>
        </div>

        <!-- Interactive SVG Polygon, Distance Labels, InfoWindow & Google Maps Pins -->
        <svg class="gmap-overlay-svg" viewBox="0 0 900 756" preserveAspectRatio="none">
          <!-- Marked Polygon Area -->
          <polygon
            points="${polyAttr}"
            fill="rgba(168, 223, 101, 0.24)"
            stroke="#A8DF65"
            stroke-width="3.5"
            stroke-linejoin="round"
          />

          <!-- Edge Distance Labels (142 m, 118 m, ...) -->
          ${edgeMidpoints.map(em => `
            <g transform="translate(${em.x}, ${em.y})">
              <rect x="-24" y="-11" width="48" height="20" rx="6" fill="rgba(13, 33, 23, 0.88)" stroke="#A8DF65" stroke-width="1" />
              <text y="3" text-anchor="middle" fill="#FFFFFF" font-size="11" font-weight="700">${em.label}</text>
            </g>
          `).join('')}

          <!-- Center Google Maps InfoWindow Badge inside Polygon -->
          <g transform="translate(435, 360)">
            <rect x="-155" y="-44" width="310" height="84" rx="16" fill="rgba(13, 33, 23, 0.90)" stroke="#A8DF65" stroke-width="1.8" />
            <text x="0" y="-16" text-anchor="middle" fill="#A8DF65" font-size="11.5" font-weight="700" letter-spacing="0.6">ĐÃ ĐÁNH DẤU RANH GIỚI VƯỜN TRÊN GOOGLE MAP</text>
            <text x="0" y="8" text-anchor="middle" fill="#FFFFFF" font-size="17" font-weight="700">${STATE.garden.name} · ${STATE.garden.area.toFixed(2)} ha (${Math.round(STATE.garden.area * 10000).toLocaleString('vi-VN')} m²)</text>
            <text x="0" y="28" text-anchor="middle" fill="#DCE5E0" font-size="12">Chu vi ${STATE.garden.perimeterM} m · ${pts.length} đỉnh GPS · Sẵn sàng phân tích GEE</text>
          </g>

          <!-- Numbered Google Maps Vertex Pins (Đỉnh 1..N) -->
          ${fullPts.map((p, idx) => `
            <g transform="translate(${p.x}, ${p.y})">
              <circle r="15" fill="#0D2117" stroke="#A8DF65" stroke-width="3" />
              <text y="4.5" text-anchor="middle" fill="#A8DF65" font-size="12" font-weight="700">${idx + 1}</text>
              <rect x="18" y="-12" width="138" height="22" rx="6" fill="rgba(13, 33, 23, 0.86)" />
              <text x="87" y="3" text-anchor="middle" fill="#FFFFFF" font-size="10.5" font-weight="600">${p.lat.toFixed(4)}°N, ${p.lng.toFixed(4)}°E</text>
            </g>
          `).join('')}
        </svg>

        <!-- Bottom Google Maps Attribution, Scale & Zoom -->
        <div class="gmap-bottom-bar" onclick="event.stopPropagation()">
          <div class="gmap-attrib">
            <strong style="color:#A8DF65; font-size:13px;">Google Maps</strong>
            <span>Dữ liệu bản đồ ©2026 Google · Ảnh vệ tinh CNES / Airbus & Maxar · Bảo Lộc, Lâm Đồng</span>
          </div>
          <div class="gmap-controls-right">
            <div class="gmap-scale">20 m</div>
            <div class="gmap-zoom-box">
              <button class="gmap-zoom-btn" onclick="showToast('Zoom 19x')">+</button>
              <button class="gmap-zoom-btn" onclick="showToast('Zoom 18x')">−</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: 3-Step Google Maps Polygon-Marking & GEE Trigger Rail -->
      <div class="gmap-right-rail">
        <!-- Card 1: Dark Forest Polygon Coordinates Card -->
        <div class="tern-forecast-card" style="flex:1.15;">
          <div>
            <div class="tcard-head">
              <span class="tcard-title">01 · Đánh dấu đa giác Google Maps</span>
              <span class="tag-activity" style="background:rgba(168,223,101,0.2); color:#A8DF65;">${pts.length} đỉnh GPS</span>
            </div>
            <div class="bento-big-metric">${STATE.garden.area.toFixed(2)} ha</div>
            <div class="bento-sub-caption">Chu vi ${STATE.garden.perimeterM} m · ${Math.round(STATE.garden.area * 10000).toLocaleString('vi-VN')} m² (Tính qua RustGIS)</div>

            <div class="rail-list" style="margin-top:8px;">
              ${pts.slice(0, 5).map((p, idx) => `
                <div class="rail-row" style="border-color:rgba(255,255,255,0.12); padding:8px 0;">
                  <span style="color:#DCE5E0; font-size:12.5px;">Đỉnh ${idx + 1}</span>
                  <span style="color:#A8DF65; font-weight:600; font-size:12.5px;">${p.lat.toFixed(4)}°N, ${p.lng.toFixed(4)}°E</span>
                </div>
              `).join('')}
            </div>
          </div>

          <div style="display:flex; gap:8px; margin-top:12px;">
            <button class="btn-secondary" style="flex:1;" onclick="undoLastVertex()">Xóa đỉnh cuối</button>
            <button class="btn-secondary" style="flex:1;" onclick="resetSamplePolygon()">Khôi phục mẫu</button>
          </div>
        </div>

        <!-- Card 2: Step 2 & 3 Trigger GEE Analysis -->
        <div class="tern-light-bento-card" style="flex:0.85;">
          <div>
            <div class="tcard-head">
              <span class="tcard-title">02 · Phân tích vùng khoanh qua GEE</span>
            </div>
            <div class="form-group" style="margin-bottom:10px;">
              <label class="form-label">Cây trồng (ước tính mật độ & đếm cây)</label>
              <select class="form-input" onchange="changeCropProfile(this.value)">
                <option value="durian" ${STATE.garden.cropKey === 'durian' ? 'selected' : ''}>Sầu riêng (Chuẩn 8×8 m · ~156 cây/ha)</option>
                <option value="coffee" ${STATE.garden.cropKey === 'coffee' ? 'selected' : ''}>Cà phê (Chuẩn 3×3 m · ~1.111 cây/ha)</option>
                <option value="citrus" ${STATE.garden.cropKey === 'citrus' ? 'selected' : ''}>Bưởi / Cam (Chuẩn 5×5 m · ~400 cây/ha)</option>
                <option value="mango_avocado" ${STATE.garden.cropKey === 'mango_avocado' ? 'selected' : ''}>Xoài / Bơ (Chuẩn 6×6 m · ~278 cây/ha)</option>
              </select>
            </div>

            <div class="rail-list">
              <div class="rail-row">
                <span style="color:var(--text-secondary);">Số cây phát hiện (GEE)</span>
                <span class="rail-row-right" style="color:var(--forest-mid);">${gee.estimatedTrees} cây</span>
              </div>
              <div class="rail-row">
                <span style="color:var(--text-secondary);">Độ phủ tán & Chỉ số NDVI</span>
                <span class="rail-row-right">${gee.canopyCoverPct}% · NDVI 0.74</span>
              </div>
            </div>
          </div>

          <button class="btn-action-primary" style="width:100%; padding:13px; font-size:14px; margin-top:10px;" onclick="navigateTo('map')">
            Phân tích GEE & Xem ${gee.estimatedTrees} tán cây →
          </button>
        </div>
      </div>
    </div>
  `;
}

function handleFullGmapClick(event) {
  const wrap = document.getElementById('gmapFullCanvas');
  if (!wrap) return;
  const rect = wrap.getBoundingClientRect();
  const x = Math.round(((event.clientX - rect.left) / rect.width) * 640);
  const y = Math.round(((event.clientY - rect.top) / rect.height) * 300);

  const lat = +(11.5515 - (y / 300) * 0.0022).toFixed(4);
  const lng = +(107.7990 + (x / 640) * 0.0032).toFixed(4);

  STATE.polygonVertices.push({ x, y, lat, lng });
  recalcPolygonMetrics();
  showToast(`Đã đánh dấu Đỉnh ${STATE.polygonVertices.length} (${lat}°N, ${lng}°E)`);
  document.getElementById('mainView').innerHTML = renderGoogleMapMarking();
}

// =============================================================================
// 02A. BẢN ĐỒ GEE — KẾT QUẢ ĐẾM TÁN CÂY & CHỈ SỐ VÙNG ĐẤT (#map)
// =============================================================================
function renderMap() {
  const gee = STATE.geeAnalysis;
  const pts = STATE.polygonVertices;
  const polyPointsAttr = pts.map(p => `${p.x},${p.y}`).join(' ');

  // Detected tree crown positions inside the polygon (with 3 highlighted alert trees)
  const crownNodes = [
    { x: 185, y: 92,  status: 'stable' },
    { x: 240, y: 88,  status: 'stable' },
    { x: 298, y: 84,  status: 'watch'  },
    { x: 356, y: 80,  status: 'stable' },
    { x: 418, y: 78,  status: 'stable' },
    { x: 175, y: 138, status: 'alert', id: 'A-04' },
    { x: 238, y: 135, status: 'stable' },
    { x: 302, y: 132, status: 'stable' },
    { x: 368, y: 128, status: 'alert', id: 'B-05' },
    { x: 432, y: 126, status: 'stable' },
    { x: 486, y: 142, status: 'watch'  },
    { x: 188, y: 188, status: 'stable' },
    { x: 252, y: 184, status: 'stable' },
    { x: 318, y: 182, status: 'watch'  },
    { x: 382, y: 180, status: 'stable' },
    { x: 445, y: 186, status: 'alert', id: 'D-02' },
    { x: 224, y: 222, status: 'stable' },
    { x: 294, y: 220, status: 'stable' },
    { x: 366, y: 222, status: 'stable' }
  ];

  // Google Satellite Hybrid tiles (z=18 around Bảo Lộc, Lâm Đồng)
  const tiles = [
    { x: 209563, y: 122792 }, { x: 209564, y: 122792 }, { x: 209565, y: 122792 }, { x: 209566, y: 122792 },
    { x: 209563, y: 122793 }, { x: 209564, y: 122793 }, { x: 209565, y: 122793 }, { x: 209566, y: 122793 }
  ];

  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <!-- Left: Interactive Google Maps Satellite Polygon-Marking Canvas -->
      <div class="v-card gmap-card">
        <div class="tcard-head" style="margin-bottom:2px;">
          <span class="tcard-title">Google Maps Satellite · Khoanh vùng vườn (${STATE.garden.location})</span>
          <span class="tag-activity">${STATE.garden.area.toFixed(2)} ha · Chu vi ${STATE.garden.perimeterM} m</span>
        </div>

        <div class="gmap-canvas-wrap" id="gmapCanvasWrap" onclick="handleGmapClick(event)">
          <!-- Real Google Satellite Hybrid Tiles + High-Contrast Orchard Fallback -->
          <div class="gmap-tile-grid">
            ${tiles.map(t => `
              <img src="https://mt1.google.com/vt/lyrs=y&x=${t.x}&y=${t.y}&z=18" alt="Google Satellite Tile" loading="eager" onerror="this.style.opacity='0'">
            `).join('')}
          </div>

          <div class="gmap-layer-tint ${STATE.mapLayer}"></div>

          <!-- Top Floating Google Maps Controls -->
          <div class="gmap-toolbar-top" onclick="event.stopPropagation()">
            <div class="gmap-mode-pill">
              <span class="gmap-mode-dot"></span>
              <span>Đang khoanh vùng · Click bản đồ để chấm đỉnh (${pts.length} đỉnh)</span>
            </div>

            <div class="gmap-layer-pills">
              <button class="gmap-layer-btn ${STATE.mapLayer === 'satellite' ? 'active' : ''}" onclick="setGmapLayer('satellite')">Vệ tinh</button>
              <button class="gmap-layer-btn ${STATE.mapLayer === 'ndvi' ? 'active' : ''}" onclick="setGmapLayer('ndvi')">NDVI</button>
              <button class="gmap-layer-btn ${STATE.mapLayer === 'ndmi' ? 'active' : ''}" onclick="setGmapLayer('ndmi')">NDMI</button>
              <button class="gmap-layer-btn ${STATE.mapLayer === 'lulc' ? 'active' : ''}" onclick="setGmapLayer('lulc')">LULC 10m</button>
            </div>
          </div>

          <!-- Interactive Polygon & Detected Tree Crowns SVG Overlay -->
          <svg class="gmap-overlay-svg" id="gmapOverlaySvg" viewBox="0 0 640 300" preserveAspectRatio="none">
            <defs>
              <pattern id="orchardGrid" width="32" height="32" patternUnits="userSpaceOnUse">
                <circle cx="16" cy="16" r="6" fill="rgba(45, 92, 52, 0.32)" />
              </pattern>
            </defs>

            <!-- Subtle parcel grid & road reference -->
            <rect width="640" height="300" fill="url(#orchardGrid)" />
            <path d="M 0 32 L 640 14 M 72 0 L 42 300" stroke="rgba(255,255,255,0.22)" stroke-width="6" fill="none" />

            <!-- Marked Garden Boundary Polygon -->
            ${pts.length >= 3 ? `
              <polygon
                points="${polyPointsAttr}"
                fill="rgba(168, 223, 101, 0.24)"
                stroke="#A8DF65"
                stroke-width="2.5"
                stroke-linejoin="round"
              />
            ` : ''}

            <!-- Internal Zone Divider Lines (Khu A, B, C, D) -->
            ${pts.length >= 4 ? `
              <line x1="312" y1="48" x2="306" y2="246" stroke="rgba(255,255,255,0.42)" stroke-width="1.2" stroke-dasharray="4 4" />
              <line x1="128" y1="148" x2="542" y2="156" stroke="rgba(255,255,255,0.42)" stroke-width="1.2" stroke-dasharray="4 4" />
              <text x="195" y="74" fill="#FFFFFF" font-size="11" font-weight="700" opacity="0.88">KHU A</text>
              <text x="365" y="70" fill="#FFFFFF" font-size="11" font-weight="700" opacity="0.88">KHU B</text>
              <text x="195" y="214" fill="#FFFFFF" font-size="11" font-weight="700" opacity="0.88">KHU C</text>
              <text x="365" y="214" fill="#FFFFFF" font-size="11" font-weight="700" opacity="0.88">KHU D</text>
            ` : ''}

            <!-- Detected Tree Crowns (GEE 10m + Canopy Segmentation) -->
            ${crownNodes.map(c => {
              const fill = c.status === 'alert' ? '#FC8181' : (c.status === 'watch' ? '#F6AD55' : '#A8DF65');
              return `
                <g transform="translate(${c.x}, ${c.y})" style="cursor:pointer;" onclick="event.stopPropagation(); ${c.id ? `selectTreeAndGo('${c.id}')` : `showToast('Tán cây khỏe · NDVI 0.79')`}">
                  <circle r="${c.id ? 9 : 6}" fill="${fill}" fill-opacity="0.32" stroke="${fill}" stroke-width="1.6" />
                  <circle r="3" fill="${fill}" />
                  ${c.id ? `
                    <rect x="10" y="-9" width="34" height="16" rx="5" fill="#0D2117" stroke="${fill}" stroke-width="1"/>
                    <text x="27" y="2" text-anchor="middle" fill="#FFFFFF" font-size="9.5" font-weight="700">${c.id}</text>
                  ` : ''}
                </g>
              `;
            }).join('')}

            <!-- Polygon Vertex Pins (Đỉnh 1..N) -->
            ${pts.map((p, idx) => `
              <g transform="translate(${p.x}, ${p.y})">
                <circle r="10" fill="#0D2117" stroke="#A8DF65" stroke-width="2.2" />
                <text y="3.5" text-anchor="middle" fill="#A8DF65" font-size="10" font-weight="700">${idx + 1}</text>
              </g>
            `).join('')}
          </svg>

          <!-- Bottom Google Maps Attribution & Scale Bar -->
          <div class="gmap-bottom-bar" onclick="event.stopPropagation()">
            <div class="gmap-attrib">
              <strong style="color:#A8DF65;">Google</strong>
              <span>Ảnh vệ tinh CNES / Airbus & Sentinel-2 10m · 11.5505°N, 107.8005°E</span>
            </div>
            <div class="gmap-controls-right">
              <div class="gmap-scale">20 m</div>
              <div class="gmap-zoom-box">
                <button class="gmap-zoom-btn" onclick="showToast('Zoom 19x')">+</button>
                <button class="gmap-zoom-btn" onclick="showToast('Zoom 18x')">−</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Polygon Coordinates & GEE Tree Counter Controls -->
      <div class="tern-glass-rail-card">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Khoanh vùng & Đếm cây GEE</span>
            <select class="filter-select" onchange="changeCropProfile(this.value)">
              <option value="durian" ${STATE.garden.cropKey === 'durian' ? 'selected' : ''}>Sầu riêng</option>
              <option value="coffee" ${STATE.garden.cropKey === 'coffee' ? 'selected' : ''}>Cà phê</option>
              <option value="citrus" ${STATE.garden.cropKey === 'citrus' ? 'selected' : ''}>Bưởi / Cam</option>
              <option value="mango_avocado" ${STATE.garden.cropKey === 'mango_avocado' ? 'selected' : ''}>Xoài / Bơ</option>
            </select>
          </div>

          <div class="vertex-chip-grid">
            ${pts.slice(0, 6).map((p, i) => `
              <div class="vertex-chip">
                <span class="vertex-badge">${i + 1}</span>
                <span style="font-weight:600; color:var(--pine-deep);">${p.lat.toFixed(4)}°N, ${p.lng.toFixed(4)}°E</span>
              </div>
            `).join('')}
          </div>

          <div class="rail-list">
            <div class="rail-row">
              <span style="color:var(--text-secondary);">Diện tích (RustGIS)</span>
              <span class="rail-row-right">${STATE.garden.area.toFixed(2)} ha (${Math.round(STATE.garden.area * 10000).toLocaleString('vi-VN')} m²)</span>
            </div>
            <div class="rail-row">
              <span style="color:var(--text-secondary);">Số cây phát hiện (GEE)</span>
              <span class="rail-row-right" style="color:var(--forest-mid);">${gee.estimatedTrees} cây · Độ phủ ${gee.canopyCoverPct}%</span>
            </div>
            <div class="rail-row" onclick="navigateTo('trees')">
              <span style="color:var(--text-secondary);">Cây cần kiểm tra</span>
              <span class="rail-row-right" style="color:var(--accent-amber);">A-04 · B-05 · D-02 ↗</span>
            </div>
          </div>
        </div>

        <div style="display:flex; gap:8px; margin-top:12px;">
          <button class="btn-secondary" onclick="undoLastVertex()">Xóa đỉnh</button>
          <button class="btn-secondary" onclick="resetSamplePolygon()">Vùng mẫu</button>
          <button class="btn-action-primary" style="flex:1;" onclick="runGeePolygonAnalysis()">Phân tích GEE →</button>
        </div>
      </div>
    </div>

    <!-- Bottom Minimal 3-Bento GEE Summary -->
    <div class="tern-bento-grid">
      <div class="tern-forecast-card">
        <div class="tcard-head">
          <span class="tcard-title">Đếm cây GEE (Sentinel-2)</span>
          <button class="tcard-arrow" onclick="navigateTo('trees')">↗</button>
        </div>
        <div class="bento-big-metric">${gee.estimatedTrees} cây</div>
        <div class="bento-sub-caption">Khỏe ${gee.healthyTrees} · TB ${gee.moderateTrees} · Yếu ${gee.stressedTrees}</div>
        <div class="segmented-volume-bar" style="height:8px; margin:0;">
          <span style="width:79%; background:#A8DF65;"></span>
          <span style="width:15%; background:#DCE5E0;"></span>
          <span style="width:6%; background:#F6AD55;"></span>
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Chỉ số vùng đất (GEE)</span></div>
        <div class="rail-list">
          ${gee.indices.slice(0, 4).map(ix => `
            <div class="rail-row">
              <span style="color:var(--text-secondary);">${ix.index} · ${ix.label}</span>
              <span class="rail-row-right">${ix.mean}</span>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Nguồn lớp phủ VN (9 bộ LULC)</span></div>
        <div class="rail-list">
          ${gee.lulcSources.map(s => `
            <div class="rail-row">
              <span style="color:var(--text-secondary);">${s.name}</span>
              <span class="rail-row-right">${s.treePct}% tán</span>
            </div>
          `).join('')}
        </div>
      </div>
    </div>
  `;
}

function handleGmapClick(event) {
  const wrap = document.getElementById('gmapCanvasWrap');
  if (!wrap) return;
  const rect = wrap.getBoundingClientRect();
  const x = Math.round(((event.clientX - rect.left) / rect.width) * 640);
  const y = Math.round(((event.clientY - rect.top) / rect.height) * 300);

  const lat = +(11.5515 - (y / 300) * 0.0022).toFixed(4);
  const lng = +(107.7990 + (x / 640) * 0.0032).toFixed(4);

  STATE.polygonVertices.push({ x, y, lat, lng });
  recalcPolygonMetrics();
  showToast(`Đã chấm Đỉnh ${STATE.polygonVertices.length} (${lat}°N, ${lng}°E)`);
  document.getElementById('mainView').innerHTML = renderMap();
}

function undoLastVertex() {
  if (STATE.polygonVertices.length <= 3) {
    showToast('Đa giác cần tối thiểu 3 đỉnh');
    return;
  }
  STATE.polygonVertices.pop();
  recalcPolygonMetrics();
  if (STATE.currentScreen === 'google-map') {
    document.getElementById('mainView').innerHTML = renderGoogleMapMarking();
  } else {
    document.getElementById('mainView').innerHTML = renderMap();
  }
}

function resetSamplePolygon() {
  STATE.polygonVertices = [
    { x: 118, y: 66,  lat: 11.5511, lng: 107.7996 },
    { x: 468, y: 62,  lat: 11.5512, lng: 107.8012 },
    { x: 548, y: 158, lat: 11.5504, lng: 107.8016 },
    { x: 446, y: 252, lat: 11.5497, lng: 107.8011 },
    { x: 136, y: 238, lat: 11.5498, lng: 107.7997 }
  ];
  STATE.garden.area = 1.8;
  STATE.garden.perimeterM = 542;
  changeCropProfile(STATE.garden.cropKey);
  showToast('Đã khôi phục vùng khoanh mẫu (1.80 ha)');
}

function recalcPolygonMetrics() {
  const pts = STATE.polygonVertices;
  if (pts.length < 3) return;
  let areaPx = 0;
  let perimPx = 0;
  for (let i = 0; i < pts.length; i++) {
    const j = (i + 1) % pts.length;
    areaPx += pts[i].x * pts[j].y - pts[j].x * pts[i].y;
    perimPx += Math.hypot(pts[j].x - pts[i].x, pts[j].y - pts[i].y);
  }
  areaPx = Math.abs(areaPx) / 2;
  // Normalize around 1.80 ha for sample polygon
  const ha = Math.max(0.4, +((areaPx / 76500) * 1.8).toFixed(2));
  STATE.garden.area = ha;
  STATE.garden.perimeterM = Math.round(perimPx * 0.48);
  changeCropProfile(STATE.garden.cropKey);
}

function setGmapLayer(layer) {
  STATE.mapLayer = layer;
  document.getElementById('mainView').innerHTML = renderMap();
}

function runGeePolygonAnalysis() {
  recalcPolygonMetrics();
  showToast(`GEE + RustGIS: Phát hiện ${STATE.geeAnalysis.estimatedTrees} cây trên ${STATE.garden.area.toFixed(2)} ha`);
}

// =============================================================================
// 02B. HỒ SƠ CÂY & SƠ ĐỒ 4 KHU (#trees)
// =============================================================================
function renderTrees() {
  const currentTree = STATE.trees.find(t => t.id === STATE.selectedTreeId) || STATE.trees[0];
  const gee = STATE.geeAnalysis;

  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <!-- Left: Visual 4-Zone Tree Grid -->
      <div class="v-card">
        <div class="tcard-head">
          <span class="tcard-title">Sơ đồ 4 khu · 24 cây đại diện (Trên tổng ${gee.estimatedTrees} cây GEE)</span>
          <button class="btn-secondary" onclick="navigateTo('map')">← Bản đồ GEE</button>
        </div>

        <div class="garden-visual-grid">
          ${['Khu A', 'Khu B', 'Khu C', 'Khu D'].map(zoneName => {
            const zoneTrees = STATE.trees.filter(t => t.zone === zoneName);
            return `
              <div class="garden-zone">
                <div class="zone-label">
                  <span>${zoneName}</span>
                  <span>6 cây</span>
                </div>
                <div class="trees-cluster">
                  ${zoneTrees.map(tree => `
                    <div class="tree-node ${STATE.selectedTreeId === tree.id ? 'selected' : ''}" onclick="selectTree('${tree.id}')">
                      <div class="tree-node-id">${tree.id}</div>
                      <div class="tree-dot ${tree.status}"></div>
                    </div>
                  `).join('')}
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Right: Clean Tree Profile Card -->
      <div class="v-card" style="display:flex; flex-direction:column; justify-content:space-between;">
        <div>
          <div class="tcard-head">
            <span class="tcard-title">Hồ sơ cây</span>
            <span class="tag-activity ${currentTree.status === 'alert' ? 'warn' : ''}">${currentTree.note}</span>
          </div>
          <div class="bento-big-metric" style="margin-bottom:4px;">Cây ${currentTree.id}</div>
          <div class="bento-sub-caption">${currentTree.zone} · NDVI ${currentTree.ndvi}</div>

          <form onsubmit="handleUpdateTree(event, '${currentTree.id}')">
            <div class="form-group">
              <label class="form-label">Số trái</label>
              <input type="number" id="inpTreeFruits" class="form-input" value="${currentTree.fruits}">
            </div>
            <div class="form-group">
              <label class="form-label">Trạng thái</label>
              <select id="selTreeStatus" class="form-input">
                <option value="stable" ${currentTree.status === 'stable' ? 'selected' : ''}>Ổn định</option>
                <option value="watch" ${currentTree.status === 'watch' ? 'selected' : ''}>Theo dõi</option>
                <option value="alert" ${currentTree.status === 'alert' ? 'selected' : ''}>Cần kiểm tra</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Ghi chú</label>
              <input type="text" id="txtTreeNote" class="form-input" value="${currentTree.note}">
            </div>
            <div style="display:flex; gap:8px; margin-top:16px;">
              <button type="submit" class="btn-secondary" style="flex:1;">Lưu</button>
              <button type="button" class="btn-action-primary" style="flex:1;" onclick="goToLogForTree('${currentTree.id}')">+ Ghi nhật ký</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Bottom Minimal 3-Bento Summary -->
    <div class="tern-bento-grid">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Sức khỏe tán (GEE)</span></div>
        <div class="bento-big-metric">${gee.estimatedTrees} cây</div>
        <div class="bento-sub-caption">Khỏe ${gee.healthyTrees} · TB ${gee.moderateTrees} · Yếu ${gee.stressedTrees}</div>
        <div class="segmented-volume-bar" style="height:8px; margin:0;">
          <span style="width:79%; background:#A8DF65;"></span>
          <span style="width:15%; background:#DCE5E0;"></span>
          <span style="width:6%; background:#F6AD55;"></span>
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Chỉ số vùng đất</span></div>
        <div class="rail-list">
          ${gee.indices.slice(0, 4).map(ix => `
            <div class="rail-row">
              <span style="color:var(--text-secondary);">${ix.index} · ${ix.label}</span>
              <span class="rail-row-right">${ix.mean}</span>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Nguồn lớp phủ VN</span></div>
        <div class="rail-list">
          ${gee.lulcSources.map(s => `
            <div class="rail-row">
              <span style="color:var(--text-secondary);">${s.name}</span>
              <span class="rail-row-right">${s.treePct}% tán</span>
            </div>
          `).join('')}
        </div>
      </div>
    </div>
  `;
}

function changeCropProfile(cropKey) {
  const profiles = {
    durian: { label: 'Sầu riêng', density: 156 },
    coffee: { label: 'Cà phê', density: 1111 },
    citrus: { label: 'Bưởi / Cam', density: 400 },
    mango_avocado: { label: 'Xoài / Bơ', density: 278 }
  };
  const p = profiles[cropKey] || profiles.durian;
  STATE.garden.cropKey = cropKey;
  STATE.garden.crop = p.label;
  const est = Math.round(STATE.garden.area * (STATE.geeAnalysis.canopyCoverPct / 100) * p.density * 0.86);
  STATE.geeAnalysis.estimatedTrees = est;
  STATE.geeAnalysis.healthyTrees = Math.round(est * 0.79);
  STATE.geeAnalysis.moderateTrees = Math.round(est * 0.15);
  STATE.geeAnalysis.stressedTrees = Math.max(1, est - STATE.geeAnalysis.healthyTrees - STATE.geeAnalysis.moderateTrees);
  if (STATE.currentScreen === 'google-map') {
    document.getElementById('mainView').innerHTML = renderGoogleMapMarking();
  } else if (STATE.currentScreen === 'map') {
    document.getElementById('mainView').innerHTML = renderMap();
  } else if (STATE.currentScreen === 'trees') {
    document.getElementById('mainView').innerHTML = renderTrees();
  }
}

// =============================================================================
// 03. KIỂM TRA (MINIMAL QUEUE)
// =============================================================================
function renderAlerts() {
  const stats = getSummaryStats();

  return `
    <div class="tern-bento-grid" style="margin-bottom:18px;">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Cần kiểm tra</span></div>
        <div class="bento-big-metric">${stats.alertCount} cây</div>
        <div class="bento-sub-caption">${stats.alertTrees.map(t => t.id).join(' · ') || 'Hoàn tất'}</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Đang theo dõi</span></div>
        <div class="bento-big-metric">${stats.watchCount} cây</div>
        <div class="bento-sub-caption">${stats.watchTrees.map(t => t.id).join(' · ')}</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Ổn định</span></div>
        <div class="bento-big-metric">${stats.stableCount} cây</div>
        <div class="bento-sub-caption">NDVI ≥ 0.74</div>
      </div>
    </div>

    <div class="v-card">
      <div class="tcard-head"><span class="tcard-title">Danh sách ưu tiên</span></div>
      <div class="queue-list">
        ${[...stats.alertTrees, ...stats.watchTrees].map(tree => `
          <div class="queue-item" onclick="selectTreeAndGo('${tree.id}')">
            <div style="display:flex; align-items:center; gap:10px;">
              <span class="tree-dot ${tree.status}"></span>
              <div>
                <div class="queue-item-title">Cây ${tree.id} · ${tree.zone}</div>
                <div class="queue-item-sub">${tree.note} · NDVI ${tree.ndvi}</div>
              </div>
            </div>
            <button class="btn-secondary" onclick="event.stopPropagation(); markTreeVerified('${tree.id}')">✓ Xác nhận</button>
          </div>
        `).join('')}
      </div>
    </div>
  `;
}

function markTreeVerified(treeId) {
  const tree = STATE.trees.find(t => t.id === treeId);
  if (!tree) return;
  tree.status = 'stable';
  tree.ndvi = 0.78;
  tree.note = 'Ổn định';
  updateSidebarBatteryAndBadges();
  showToast(`Đã xác nhận ${treeId}`);
  document.getElementById('mainView').innerHTML = renderAlerts();
}

// =============================================================================
// 04. NHẬT KÝ (MINIMAL 3-BENTO + TABLE)
// =============================================================================
function renderRecords() {
  const totalLogs = STATE.logs.length;
  return `
    <div class="tern-bento-grid" style="margin-bottom:18px;">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Hoạt động tháng</span></div>
        <div class="bento-big-metric">${totalLogs} lần</div>
        <div class="bento-sub-caption">Gần nhất: 10/10 · Cây A-04</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Chăm sóc định kỳ</span></div>
        <div class="bento-big-metric">2 khu</div>
        <div class="bento-sub-caption">Khu B (Bón phân) · Khu C (Tưới)</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Xử lý cảnh báo</span></div>
        <div class="bento-big-metric">2 cây</div>
        <div class="bento-sub-caption">A-04 (Kiểm tra) · D-02 (Phun)</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <input type="search" id="logSearchInput" class="search-input" placeholder="Tìm cây hoặc hoạt động..." oninput="handleLogFilter()">
        <button class="btn-action-primary" onclick="navigateTo('log-form')">+ Ghi chép</button>
      </div>
      <table class="v-table">
        <thead>
          <tr>
            <th>NGÀY</th>
            <th>HOẠT ĐỘNG</th>
            <th>ĐỐI TƯỢNG</th>
            <th>VẬT TƯ</th>
            <th>LIỀU LƯỢNG</th>
          </tr>
        </thead>
        <tbody id="logsTableBody">
          ${renderLogRows(STATE.logs)}
        </tbody>
      </table>
    </div>
  `;
}

function renderLogRows(list) {
  return list.map(log => `
    <tr>
      <td><strong>${log.date}</strong></td>
      <td><span class="tag-activity ${log.activity === 'Kiểm tra' ? 'warn' : ''}">${log.activity}</span></td>
      <td><strong>${log.target}</strong></td>
      <td>${log.product}</td>
      <td>${log.dosage}</td>
    </tr>
  `).join('');
}

// =============================================================================
// 05. DỰ BÁO (MINIMAL 2-COLUMN + 3 BENTO CARDS)
// =============================================================================
function renderYield() {
  const stats = getSummaryStats();
  const zoneYields = ['Khu A', 'Khu B', 'Khu C', 'Khu D'].map(zName => {
    const fruits = STATE.trees.filter(t => t.zone === zName).reduce((a, b) => a + b.fruits, 0);
    const kg = Math.round(fruits * STATE.garden.avgFruitWeight);
    return { name: zName, fruits, kg };
  });

  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <div class="tern-forecast-card">
        <div>
          <div class="tcard-head"><span class="tcard-title">Doanh thu dự kiến</span></div>
          <div id="dispRevenue" class="hero-giant-number" style="color:#FFFFFF; margin:12px 0;">${formatVND(stats.totalRevenue)}</div>
          <div id="dispCalcDetail" class="bento-sub-caption">${stats.totalKg.toLocaleString('vi-VN')} kg × ${STATE.garden.pricePerKg.toLocaleString('vi-VN')} ₫/kg</div>
        </div>
        <div style="display:grid; grid-template-columns:repeat(3,1fr); gap:16px; padding-top:20px; border-top:1px solid rgba(255,255,255,0.12);">
          <div>
            <div style="font-size:20px; font-weight:600;">${stats.totalFruits}</div>
            <div style="font-size:12px; opacity:0.7;">Tổng trái</div>
          </div>
          <div>
            <div id="dispWeight" style="font-size:20px; font-weight:600;">${STATE.garden.avgFruitWeight} kg</div>
            <div style="font-size:12px; opacity:0.7;">TB / trái</div>
          </div>
          <div>
            <div id="dispTotalWeight" style="font-size:20px; font-weight:600;">${stats.totalTon} tấn</div>
            <div style="font-size:12px; opacity:0.7;">Sản lượng</div>
          </div>
        </div>
      </div>

      <div class="v-card">
        <div class="tcard-head"><span class="tcard-title">Giả định tính toán</span></div>
        <div class="form-group">
          <label class="form-label">Khối lượng (kg/trái)</label>
          <input type="number" step="0.1" id="inpAvgWeight" class="form-input" value="${STATE.garden.avgFruitWeight}" oninput="recalcYield()">
        </div>
        <div class="form-group">
          <label class="form-label">Đơn giá (₫/kg)</label>
          <input type="number" step="1000" id="inpPricePerKg" class="form-input" value="${STATE.garden.pricePerKg}" oninput="recalcYield()">
        </div>
        <button class="btn-action-primary" style="width:100%; margin-top:8px;" onclick="showToast('Đã lưu')">Lưu</button>
      </div>
    </div>

    <div class="tern-bento-grid">
      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Sản lượng theo khu</span></div>
        <div class="rail-list">
          ${zoneYields.map(z => `
            <div class="rail-row">
              <span style="color:var(--text-secondary);">${z.name} · ${z.fruits} trái</span>
              <span class="rail-row-right">${z.kg} kg</span>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Phân hạng trái</span></div>
        <div class="bento-big-metric">82% Loại 1</div>
        <div class="bento-sub-caption">Loại 2: 14% · Hao hụt: 4%</div>
        <div class="segmented-volume-bar" style="height:8px; margin:0;">
          <span style="width:82%; background:#366536;"></span>
          <span style="width:14%; background:#7B9A61;"></span>
          <span style="width:4%; background:#BAC3C1;"></span>
        </div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Giá thu mua</span></div>
        <div class="bento-big-metric">62.000 ₫</div>
        <div class="bento-sub-caption">${STATE.garden.priceSource}</div>
        <div class="rail-list">
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Biên lợi nhuận</span>
            <span class="rail-row-right" style="color:var(--forest-mid);">+74.5%</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Kỳ thu hoạch</span>
            <span class="rail-row-right">Tháng 11</span>
          </div>
        </div>
      </div>
    </div>
  `;
}

function recalcYield() {
  const avgW = parseFloat(document.getElementById('inpAvgWeight').value) || 2.2;
  const price = parseFloat(document.getElementById('inpPricePerKg').value) || 62000;
  STATE.garden.avgFruitWeight = avgW;
  STATE.garden.pricePerKg = price;

  const totalFruits = STATE.trees.reduce((acc, t) => acc + t.fruits, 0);
  const totalKg = totalFruits * avgW;
  const totalRevenue = totalKg * price;

  document.getElementById('dispWeight').textContent = `${avgW} kg`;
  document.getElementById('dispTotalWeight').textContent = `${(totalKg / 1000).toFixed(2)} tấn`;
  document.getElementById('dispRevenue').textContent = formatVND(totalRevenue);
  document.getElementById('dispCalcDetail').textContent = `${totalKg.toLocaleString('vi-VN')} kg × ${price.toLocaleString('vi-VN')} ₫/kg`;
}

// =============================================================================
// 06. BÁO CÁO AI (MINIMAL 2-COLUMN + 3 BENTO CARDS)
// =============================================================================
function renderAI() {
  const gee = STATE.geeAnalysis;
  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <div class="v-card" style="display:flex; flex-direction:column; justify-content:space-between;">
        <div>
          <div class="tcard-head"><span class="tcard-title">Trợ lý VWing AI</span></div>
          <div style="display:flex; flex-direction:column; gap:12px; margin-bottom:18px;">
            ${STATE.chatMessages.map(msg => msg.sender === 'user'
              ? `<div style="align-self:flex-end; background:var(--pine-deep); color:#FFF; padding:10px 16px; border-radius:16px;">${msg.text}</div>`
              : `<div style="background:#FFF; padding:14px 18px; border-radius:16px; border:1px solid var(--line);">${msg.intro}</div>`
            ).join('')}
          </div>
        </div>
        <form style="display:flex; gap:8px;" onsubmit="handleSendAI(event)">
          <input type="text" id="aiInputText" class="form-input" placeholder="Hỏi nhanh về vườn...">
          <button type="submit" class="btn-action-primary">Gửi</button>
        </form>
      </div>

      <div class="tern-glass-rail-card">
        <div class="tcard-head"><span class="tcard-title">Tóm tắt GEE & RustGIS</span></div>
        <div class="rail-list">
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Tổng cây (GEE 10m)</span>
            <span class="rail-row-right">${gee.estimatedTrees} cây</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Độ phủ tán</span>
            <span class="rail-row-right">${gee.canopyCoverPct}%</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Độ bền ẩm (NDMI)</span>
            <span class="rail-row-right">${gee.runwayDays} ngày</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Cây cần kiểm tra</span>
            <span class="rail-row-right" style="color:var(--accent-amber);">A-04 · B-05 · D-02</span>
          </div>
        </div>
      </div>
    </div>

    <div class="tern-bento-grid">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Điểm sức khỏe AI</span></div>
        <div class="bento-big-metric">88 / 100</div>
        <div class="bento-sub-caption">146 cây khỏe · 11 cây yếu</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Khuyến nghị tưới</span></div>
        <div class="bento-big-metric">Khu A · Khu D</div>
        <div class="bento-sub-caption">Bổ sung ẩm trong 5 ngày tới</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Nguồn đối chiếu</span></div>
        <div class="bento-big-metric">9 bộ LULC</div>
        <div class="bento-sub-caption">Sentinel-2 · JAXA · ESA 10m</div>
      </div>
    </div>
  `;
}

function handleSendAI(e) {
  e.preventDefault();
  const input = document.getElementById('aiInputText');
  const text = input.value.trim();
  if (!text) return;
  const stats = getSummaryStats();
  STATE.chatMessages.push({ sender: 'user', text });
  STATE.chatMessages.push({
    sender: 'assistant',
    intro: `Vườn có ${STATE.geeAnalysis.estimatedTrees} cây GEE (${stats.totalTon} tấn) · ${stats.alertCount} cây cần khám.`
  });
  document.getElementById('mainView').innerHTML = renderAI();
}

// =============================================================================
// 07. GHI CHÉP & 08. CÀI ĐẶT (MINIMAL 2-ROW TERN LAYOUT)
// =============================================================================
function renderLogForm() {
  const currentTree = STATE.trees.find(t => t.id === STATE.selectedTreeId) || STATE.trees[0];
  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <div class="v-card">
        <div class="tcard-head"><span class="tcard-title">Ghi hoạt động chăm sóc</span></div>
        <form onsubmit="handleSaveLog(event)">
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
            <div class="form-group">
              <label class="form-label">Hoạt động</label>
              <select id="logActivity" class="form-input">
                <option value="Bón phân">Bón phân</option>
                <option value="Tưới nước">Tưới nước</option>
                <option value="Phun thuốc">Phun thuốc</option>
                <option value="Kiểm tra">Kiểm tra</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Đối tượng</label>
              <input type="text" id="logTarget" class="form-input" value="${STATE.selectedTreeId}">
            </div>
          </div>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
            <div class="form-group">
              <label class="form-label">Vật tư</label>
              <input type="text" id="logProduct" class="form-input" value="Hữu cơ vi sinh">
            </div>
            <div class="form-group">
              <label class="form-label">Liều lượng</label>
              <input type="text" id="logDosage" class="form-input" value="2 kg">
            </div>
          </div>
          <button type="submit" class="btn-action-primary" style="width:100%; margin-top:6px;">Lưu nhật ký</button>
        </form>
      </div>

      <div class="tern-glass-rail-card">
        <div class="tcard-head"><span class="tcard-title">Cây đang chọn · ${currentTree.id}</span></div>
        <div class="rail-list">
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Khu vực</span>
            <span class="rail-row-right">${currentTree.zone}</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Số trái hiện tại</span>
            <span class="rail-row-right">${currentTree.fruits} trái</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Chỉ số NDVI</span>
            <span class="rail-row-right">${currentTree.ndvi}</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Ghi chú</span>
            <span class="rail-row-right">${currentTree.note}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="tern-bento-grid">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Mẫu nhanh · Bón phân</span></div>
        <div class="bento-big-metric">2 kg / gốc</div>
        <div class="bento-sub-caption">Hữu cơ vi sinh · Chu kỳ 30 ngày</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Mẫu nhanh · Tưới nhỏ giọt</span></div>
        <div class="bento-big-metric">45 phút</div>
        <div class="bento-sub-caption">Toàn khu · Giữ ẩm tầng mặt</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Mẫu nhanh · Phun sinh học</span></div>
        <div class="bento-big-metric">1 lần</div>
        <div class="bento-sub-caption">Phòng nấm lá & rầy xanh</div>
      </div>
    </div>
  `;
}

function handleSaveLog(e) {
  e.preventDefault();
  STATE.logs.unshift({
    date: '10/10',
    activity: document.getElementById('logActivity').value,
    target: document.getElementById('logTarget').value,
    product: document.getElementById('logProduct').value,
    dosage: document.getElementById('logDosage').value
  });
  showToast('Đã lưu');
  navigateTo('records');
}

function renderSetup() {
  return `
    <div class="dash-two-col" style="margin-bottom:18px;">
      <div class="v-card">
        <div class="tcard-head"><span class="tcard-title">Thông tin vườn</span></div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
          <div class="form-group">
            <label class="form-label">Tên vườn</label>
            <input type="text" class="form-input" value="${STATE.garden.name}">
          </div>
          <div class="form-group">
            <label class="form-label">Địa điểm</label>
            <input type="text" class="form-input" value="${STATE.garden.location}">
          </div>
        </div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
          <div class="form-group">
            <label class="form-label">Cây trồng</label>
            <input type="text" class="form-input" value="${STATE.garden.crop}">
          </div>
          <div class="form-group">
            <label class="form-label">Diện tích (ha)</label>
            <input type="text" class="form-input" value="${STATE.garden.area}">
          </div>
        </div>
        <button class="btn-action-primary" style="width:100%; margin-top:6px;" onclick="showToast('Đã lưu cài đặt')">Lưu cài đặt</button>
      </div>

      <div class="tern-glass-rail-card">
        <div class="tcard-head"><span class="tcard-title">Kết nối hệ thống</span></div>
        <div class="rail-list">
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Google Earth Engine</span>
            <span class="rail-row-right" style="color:var(--forest-mid);">● Hoạt động</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">RustGIS MCP v0.3.0</span>
            <span class="rail-row-right" style="color:var(--forest-mid);">● 11 công cụ</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Dữ liệu lớp phủ VN</span>
            <span class="rail-row-right">9 nguồn LULC</span>
          </div>
          <div class="rail-row">
            <span style="color:var(--text-secondary);">Đồng bộ Offline</span>
            <span class="rail-row-right">Tự động</span>
          </div>
        </div>
      </div>
    </div>

    <div class="tern-bento-grid">
      <div class="tern-forecast-card">
        <div class="tcard-head"><span class="tcard-title">Độ phân giải vệ tinh</span></div>
        <div class="bento-big-metric">10m / pixel</div>
        <div class="bento-sub-caption">Sentinel-2 · Chu kỳ 5 ngày</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Mật độ chuẩn</span></div>
        <div class="bento-big-metric">156 cây/ha</div>
        <div class="bento-sub-caption">Sầu riêng · Cự ly 8 × 8 m</div>
      </div>

      <div class="tern-light-bento-card">
        <div class="tcard-head"><span class="tcard-title">Phiên bản</span></div>
        <div class="bento-big-metric">VWing 4.20</div>
        <div class="bento-sub-caption">Tern UI Design (#27814390)</div>
      </div>
    </div>
  `;
}

// =============================================================================
// NAVIGATION
// =============================================================================
function navigateTo(screenName) {
  STATE.currentScreen = screenName;
  window.location.hash = screenName;

  const titles = {
    overview: 'Tổng quan',
    'google-map': 'Khoanh vùng Google Maps',
    map: 'Bản đồ GEE',
    trees: 'Hồ sơ cây',
    records: 'Nhật ký',
    yield: 'Dự báo',
    ai: 'Báo cáo AI',
    alerts: 'Kiểm tra',
    'log-form': 'Ghi chép',
    setup: 'Cài đặt',
    'panorama-360': 'Không gian 360° Panorama & Hotspot (FarmBlock)',
    'land-plots': 'Bản đồ Lô đất Mapbox & Danh mục Khu trồng (FarmBlock)',
    'camera-live': 'Camera Live VLC & Thanh Timeline 24h (FarmBlock)',
    'iot-care': '19 Chỉ số IoT Thực địa & Nhật ký Canh tác (FarmBlock)'
  };
  const crumb = document.getElementById('crumbTitle');
  if (crumb) crumb.textContent = titles[screenName] || 'Tổng quan';

  document.querySelectorAll('.nav-item').forEach(link => {
    link.classList.toggle('active', link.dataset.screen === screenName);
  });
  document.querySelectorAll('.bnav-item').forEach(link => {
    link.classList.toggle('active', link.dataset.screen === screenName);
  });

  updateSidebarBatteryAndBadges();

  if (typeof cleanupFarmBlockView === 'function') {
    cleanupFarmBlockView();
  }

  const main = document.getElementById('mainView');
  switch (screenName) {
    case 'google-map': main.innerHTML = renderGoogleMapMarking(); break;
    case 'map': main.innerHTML = renderMap(); break;
    case 'trees': main.innerHTML = renderTrees(); break;
    case 'records': main.innerHTML = renderRecords(); break;
    case 'yield': main.innerHTML = renderYield(); break;
    case 'ai': main.innerHTML = renderAI(); break;
    case 'alerts': main.innerHTML = renderAlerts(); break;
    case 'log-form': main.innerHTML = renderLogForm(); break;
    case 'setup': main.innerHTML = renderSetup(); break;
    case 'panorama-360':
      main.innerHTML = renderFarmBlockPanorama360();
      if (typeof mountFarmBlockPanorama360 === 'function') mountFarmBlockPanorama360();
      break;
    case 'land-plots':
      main.innerHTML = renderFarmBlockLandPlots();
      if (typeof mountFarmBlockLandPlots === 'function') mountFarmBlockLandPlots();
      break;
    case 'camera-live':
      main.innerHTML = renderFarmBlockCameraLive();
      if (typeof mountFarmBlockCameraLive === 'function') mountFarmBlockCameraLive();
      break;
    case 'iot-care':
      main.innerHTML = renderFarmBlockIotCare();
      if (typeof mountFarmBlockIotCare === 'function') mountFarmBlockIotCare();
      break;
    default: main.innerHTML = renderOverview();
  }
}

function selectTree(treeId) {
  STATE.selectedTreeId = treeId;
  navigateTo('trees');
}

function selectTreeAndGo(treeId) {
  STATE.selectedTreeId = treeId;
  navigateTo('trees');
}

function goToLogForTree(treeId) {
  STATE.selectedTreeId = treeId;
  navigateTo('log-form');
}

function handleUpdateTree(e, treeId) {
  e.preventDefault();
  const tree = STATE.trees.find(t => t.id === treeId);
  if (!tree) return;
  tree.fruits = parseInt(document.getElementById('inpTreeFruits').value) || tree.fruits;
  tree.status = document.getElementById('selTreeStatus').value;
  tree.note = document.getElementById('txtTreeNote').value;
  updateSidebarBatteryAndBadges();
  showToast('Đã lưu');
  document.getElementById('mainView').innerHTML = renderTrees();
}

function handleLogFilter() {
  const q = (document.getElementById('logSearchInput')?.value || '').toLowerCase();
  const filtered = STATE.logs.filter(l => !q || l.activity.toLowerCase().includes(q) || l.target.toLowerCase().includes(q));
  document.getElementById('logsTableBody').innerHTML = renderLogRows(filtered);
}

function initFigmaModal() {
  const modal = document.getElementById('figmaModal');
  const tabsContainer = document.getElementById('figmaScreenTabs');
  const imgViewer = document.getElementById('figmaModalImg');
  if (!modal || !tabsContainer || !imgViewer) return;

  tabsContainer.innerHTML = FIGMA_SCREENS.map((sc, i) => `
    <button class="ftab-btn ${i === 0 ? 'active' : ''}" onclick="switchFigmaTab('${sc.id}', '${sc.file}', this)">
      ${sc.title}
    </button>
  `).join('');

  imgViewer.src = FIGMA_SCREENS[0].file;
  document.getElementById('btnOpenFigmaModal').onclick = () => modal.showModal();
  document.getElementById('btnCloseFigmaModal').onclick = () => modal.close();
  modal.onclick = (e) => { if (e.target === modal) modal.close(); };
}

function switchFigmaTab(id, file, btn) {
  document.querySelectorAll('.ftab-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('figmaModalImg').src = file;
}

function init() {
  try { initFigmaModal(); } catch (e) {}
  const toggleBtn = document.getElementById('btnSidebarToggle');
  const sidebar = document.querySelector('.sidebar');
  if (toggleBtn && sidebar) toggleBtn.onclick = () => sidebar.classList.toggle('open');

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      openQuickSearch();
    }
  });

  const qModal = document.getElementById('quickSearchModal');
  if (qModal) qModal.onclick = (e) => { if (e.target === qModal) qModal.close(); };

  navigateTo(window.location.hash.replace('#', '') || 'overview');
  window.addEventListener('hashchange', () => navigateTo(window.location.hash.replace('#', '') || 'overview'));
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
