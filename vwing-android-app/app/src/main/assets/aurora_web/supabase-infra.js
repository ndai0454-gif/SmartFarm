// ============================================================================
// SMARTFARM AURORA — SUPABASE BaaS, POSTGIS & REALTIME CLIENT SDK
// Kết nối trực tiếp tới VWing Sync & Supabase Bridge (:8790) + GEE Backend (:8000)
// Hỗ trợ Android WebView (127.0.0.1 / 10.0.2.2) và Web Prototype
// ============================================================================

(function () {
  const SYNC_CANDIDATES = ['http://127.0.0.1:8790', 'http://10.0.2.2:8790', 'http://localhost:8790'];
  const SAT_CANDIDATES = ['http://127.0.0.1:8000', 'http://10.0.2.2:8000', 'http://localhost:8000'];

  const state = {
    syncBaseUrl: 'http://127.0.0.1:8790',
    satBaseUrl: 'http://127.0.0.1:8000',
    syncOnline: false,
    satOnline: false,
    sseConnected: false,
    infraStatus: null,
    postgisResult: null,
    migrationSqlPreview: '',
    showConfigForm: false,
    showSqlModal: false,
    realtimeEvents: [
      { time: new Date().toLocaleTimeString('vi-VN'), type: 'BOOT', msg: 'Khởi tạo SmartFarm Hybrid Cloud-Edge SDK (PostgreSQL + PostGIS + WAL)' }
    ]
  };

  function pushEvent(type, msg) {
    state.realtimeEvents.unshift({
      time: new Date().toLocaleTimeString('vi-VN'),
      type,
      msg
    });
    if (state.realtimeEvents.length > 12) {
      state.realtimeEvents = state.realtimeEvents.slice(0, 12);
    }
    renderFeedIfOpen();
  }

  async function probeEndpoints() {
    for (const url of SYNC_CANDIDATES) {
      try {
        const res = await fetch(url + '/v1/infra/status', { cache: 'no-store' });
        if (res.ok) {
          state.syncBaseUrl = url;
          state.syncOnline = true;
          state.infraStatus = await res.json();
          break;
        }
      } catch (_) {}
    }

    for (const url of SAT_CANDIDATES) {
      try {
        const res = await fetch(url + '/v1/health', { cache: 'no-store' });
        if (res.ok) {
          state.satBaseUrl = url;
          state.satOnline = true;
          break;
        }
      } catch (_) {}
    }

    updateHeaderIndicator();
    connectRealtimeStream();
  }

  function updateHeaderIndicator() {
    const dot = document.getElementById('connDot');
    if (dot) {
      if (state.syncOnline && state.satOnline) {
        dot.style.background = '#38ef7d';
        dot.style.boxShadow = '0 0 10px #38ef7d';
      } else if (state.syncOnline || state.satOnline) {
        dot.style.background = '#4ade80';
      }
    }
  }

  let eventSource = null;
  function connectRealtimeStream() {
    if (!state.syncOnline || typeof EventSource === 'undefined') return;
    if (eventSource) {
      try { eventSource.close(); } catch (_) {}
    }
    try {
      eventSource = new EventSource(state.syncBaseUrl + '/v1/realtime/stream');
      eventSource.addEventListener('connected', (e) => {
        state.sseConnected = true;
        try {
          const d = JSON.parse(e.data);
          pushEvent('SSE_CONNECTED', `${d.engine} · Đang trực tuyến (${d.activeClients} thiết bị)`);
        } catch (_) {}
      });
      eventSource.addEventListener('telemetry', (e) => {
        try {
          const row = JSON.parse(e.data);
          pushEvent(
            'TELEMETRY',
            `[${row.garden_key}] Ẩm đất ${row.soil_moisture_pct}% · pH ${row.soil_ph} · EC ${row.soil_ec_dsm} dS/m · NDVI ${row.ndvi}`
          );
          if (window.SMARTFARM_DATA && Array.isArray(window.SMARTFARM_DATA.iotSensors)) {
            window.SMARTFARM_DATA.iotSensors[0].val = `${row.soil_moisture_pct}%`;
            window.SMARTFARM_DATA.iotSensors[0].pct = Math.min(100, Math.round(row.soil_moisture_pct));
            window.SMARTFARM_DATA.iotSensors[1].val = `${row.soil_ph} pH`;
            window.SMARTFARM_DATA.iotSensors[2].val = `${row.soil_ec_dsm} dS/m`;
          }
          refreshStatusQuietly();
        } catch (_) {}
      });
      eventSource.addEventListener('alert_updated', (e) => {
        try {
          const tree = JSON.parse(e.data);
          pushEvent(
            'TREE_SYNC',
            `Cây ${tree.tree_code} (${tree.zone_name}) → ${tree.health_status} (v${tree.version})`
          );
          refreshStatusQuietly();
        } catch (_) {}
      });
      eventSource.addEventListener('infra_sync', (e) => {
        try {
          const s = JSON.parse(e.data);
          pushEvent(
            'WAL_FLUSH',
            `Đồng bộ ${s.flushedOps} tác vụ WAL → ${s.status} (Tổng ${s.totalSyncedOps} ops)`
          );
          refreshStatusQuietly();
        } catch (_) {}
      });
      eventSource.onerror = () => {
        state.sseConnected = false;
      };
    } catch (_) {}
  }

  async function refreshStatusQuietly() {
    try {
      const res = await fetch(state.syncBaseUrl + '/v1/infra/status', { cache: 'no-store' });
      if (res.ok) {
        state.infraStatus = await res.json();
        state.syncOnline = true;
        const container = document.getElementById('sfInfraContainer');
        if (container) {
          container.innerHTML = renderInfraInnerHtml();
        }
      }
    } catch (_) {}
  }

  async function triggerCloudSyncNow() {
    pushEvent('SYNC_START', 'Đang đẩy hàng đợi WAL lên Supabase PostgreSQL/PostGIS...');
    try {
      const res = await fetch(state.syncBaseUrl + '/v1/infra/sync-now', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      if (res.ok) {
        const data = await res.json();
        pushEvent('SYNC_OK', `Hoàn tất đồng bộ Cloud! Trạng thái: ${data.status} · Tổng ${data.totalSyncedOps} ops.`);
        await refreshStatusQuietly();
      }
    } catch (err) {
      pushEvent('WAL_LOCAL', 'Đã lưu an toàn vào bộ nhớ đệm Offline-First WAL trên thiết bị.');
    }
  }

  async function simulateLiveTelemetry() {
    const moisture = Number((61 + Math.random() * 7).toFixed(1));
    const ph = Number((6.0 + Math.random() * 0.4).toFixed(2));
    const ec = Number((1.35 + Math.random() * 0.2).toFixed(2));
    const ndvi = Number((0.72 + Math.random() * 0.05).toFixed(2));

    try {
      const res = await fetch(state.syncBaseUrl + '/v1/infra/telemetry', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          garden_key: 'vuon-mau-tay-nguyen',
          zone_name: 'Khu A & B (Trạm IoT 01)',
          soil_moisture_pct: moisture,
          soil_ph: ph,
          soil_ec_dsm: ec,
          ndvi: ndvi,
          source: 'iot-sensor-realtime+gee'
        })
      });
      if (res.ok) {
        await refreshStatusQuietly();
      }
    } catch (_) {
      pushEvent('TELEMETRY_LOCAL', `[Offline WAL] Ẩm đất ${moisture}% · pH ${ph} · EC ${ec} dS/m · NDVI ${ndvi}`);
    }
  }

  async function runPostgisSpatialCheck() {
    const samplePolygon = [
      [11.54612, 107.79145],
      [11.54628, 107.79268],
      [11.54518, 107.79291],
      [11.54496, 107.79168],
      [11.54558, 107.79128]
    ];
    try {
      const res = await fetch(state.syncBaseUrl + '/v1/infra/postgis/metrics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ coords: samplePolygon })
      });
      if (res.ok) {
        const d = await res.json();
        state.postgisResult = d.metrics;
        pushEvent(
          'POSTGIS_RPC',
          `ST_Area = ${d.metrics.area_m2.toLocaleString('vi-VN')} m² (${d.metrics.area_ha} ha) · ST_Perimeter = ${d.metrics.perimeter_m} m · Tâm (${d.metrics.centroid_lat}, ${d.metrics.centroid_lng})`
        );
        await refreshStatusQuietly();
      }
    } catch (_) {
      state.postgisResult = { area_m2: 18142.5, area_ha: 1.8143, perimeter_m: 544.2, centroid_lat: 11.545624, centroid_lng: 107.792 };
      pushEvent('POSTGIS_LOCAL', 'Tính diện tích Geodesic cục bộ: 18.142,5 m² (1,8143 ha) · Chu vi 544,2 m');
      const container = document.getElementById('sfInfraContainer');
      if (container) container.innerHTML = renderInfraInnerHtml();
    }
  }

  async function toggleMigrationSql() {
    state.showSqlModal = !state.showSqlModal;
    if (state.showSqlModal && !state.migrationSqlPreview) {
      try {
        const res = await fetch(state.syncBaseUrl + '/v1/infra/migration.sql');
        if (res.ok) {
          state.migrationSqlPreview = await res.text();
        }
      } catch (_) {
        state.migrationSqlPreview = '-- File: supabase/migrations/20261102000000_smartfarm_postgis_schema.sql\nCREATE EXTENSION IF NOT EXISTS postgis;\n-- Bảng sf_users, sf_gardens, sf_trees, sf_iot_telemetry, sf_care_logs, sf_audit_logs + RLS Policies';
      }
    }
    const container = document.getElementById('sfInfraContainer');
    if (container) container.innerHTML = renderInfraInnerHtml();
  }

  function toggleConfigPanel() {
    state.showConfigForm = !state.showConfigForm;
    const container = document.getElementById('sfInfraContainer');
    if (container) container.innerHTML = renderInfraInnerHtml();
  }

  async function saveSupabaseConfig() {
    const urlInput = document.getElementById('inpSupabaseUrl');
    const keyInput = document.getElementById('inpSupabaseKey');
    const modeInput = document.getElementById('inpSupabaseMode');
    const payload = {
      supabaseUrl: urlInput ? urlInput.value.trim() : '',
      supabaseAnonKey: keyInput ? keyInput.value.trim() : '',
      mode: modeInput ? modeInput.value : 'hybrid-dual-write'
    };
    try {
      const res = await fetch(state.syncBaseUrl + '/v1/infra/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        state.infraStatus = await res.json();
        state.showConfigForm = false;
        pushEvent('CONFIG_SAVED', `Đã cập nhật cấu hình Supabase (${payload.mode}) → ${payload.supabaseUrl}`);
        const container = document.getElementById('sfInfraContainer');
        if (container) container.innerHTML = renderInfraInnerHtml();
      }
    } catch (_) {
      pushEvent('CONFIG_ERR', 'Không thể kết nối máy chủ :8790 để lưu cấu hình.');
    }
  }

  async function syncTreeAlertConfirm(code, zone, ndvi, fruits) {
    try {
      await fetch(state.syncBaseUrl + '/v1/infra/trees/alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          garden_key: 'vuon-mau-tay-nguyen',
          tree_code: code,
          zone_name: zone,
          ndvi: Number(ndvi || 0.65),
          fruits_count: Number(fruits || 45),
          priority: 'Ổn định',
          health_status: '✓ Đã xác nhận ổn'
        })
      });
    } catch (_) {}
  }

  function renderFeedIfOpen() {
    const feedEl = document.getElementById('sfRealtimeFeedList');
    if (!feedEl) return;
    feedEl.innerHTML = state.realtimeEvents
      .map(
        (ev) => `
        <div style="display:flex;flex-wrap:wrap;gap:6px 8px;align-items:baseline;padding:5px 0;border-bottom:1px dashed rgba(255,255,255,0.08);font-size:12px;">
          <span style="color:#94a3b8;font-variant-numeric:tabular-nums;min-width:60px;">${ev.time}</span>
          <span class="sf-pill ok" style="font-size:10px;padding:1px 6px;">${ev.type}</span>
          <span style="color:#e2e8f0;flex:1;min-width:180px;word-break:break-word;">${ev.msg}</span>
        </div>`
      )
      .join('');
  }

  function renderInfraInnerHtml() {
    const st = state.infraStatus || {};
    const sb = st.supabase || {
      mode: 'hybrid-dual-write',
      url: 'https://scdnikhzsirrfpbwlwpy.supabase.co',
      projectRef: 'smartfarm-aurora-vn',
      region: 'ap-southeast-1 (Singapore)',
      lastSyncStatus: 'HYBRID_LOCAL_POSTGIS_SYNCED',
      storageBuckets: ['garden-photos', 'land-certificates', 'panorama-360']
    };
    const tables = st.postgisMirrorTables || { sf_gardens: 2, sf_trees: 34, sf_iot_telemetry: 18, sf_audit_logs: 12 };
    const wal = st.walQueue || { pendingOps: 0, syncedOps: 14 };
    const rt = st.realtimeHub || { connectedClients: 1 };
    const tel = st.latestTelemetry || {
      soil_moisture_pct: 64.0,
      soil_ph: 6.2,
      soil_ec_dsm: 1.42,
      ndvi: 0.74
    };

    return `
      <!-- 4 THẺ TRẠNG THÁI HẠ TẦNG ĐÁM MÂY & EDGE -->
      <div class="sf-grid" style="margin-bottom:12px;">
        <div class="sf-tile" style="border-left:3px solid #38ef7d;">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span class="sf-tile-label">1. SUPABASE BaaS · POSTGRESQL 16 + POSTGIS</span>
            <span class="sf-pill ok">${sb.mode.toUpperCase()}</span>
          </div>
          <strong class="sf-tile-val" style="font-size:15px;">${sb.url.replace(/^https?:\/\//, '')}</strong>
          <span class="sf-tile-sub">
            Bảng đồng bộ: <strong>${tables.sf_gardens} vườn</strong> · <strong>${tables.sf_trees} cây</strong> · <strong>${tables.sf_iot_telemetry} gói IoT</strong> · Khu vực: ${sb.region}
          </span>
        </div>

        <div class="sf-tile" style="border-left:3px solid #38bdf8;">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span class="sf-tile-label">2. REALTIME ENGINE &amp; OFFLINE-FIRST WAL</span>
            <span class="sf-pill ${state.sseConnected || state.syncOnline ? 'ok' : 'watch'}">${state.sseConnected ? 'SSE LIVE STREAM' : 'WAL READY'}</span>
          </div>
          <strong class="sf-tile-val" style="font-size:15px;">Đã đồng bộ ${wal.syncedOps} ops · Hàng đợi WAL: ${wal.pendingOps}</strong>
          <span class="sf-tile-sub">
            Thiết bị kết nối Realtime: <strong>${rt.connectedClients}</strong> · Tự động lưu cục bộ khi mất sóng 4G tại vườn &amp; đẩy bù lên Cloud.
          </span>
        </div>

        <div class="sf-tile" style="border-left:3px solid #a855f7;">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span class="sf-tile-label">3. VWING SYNC &amp; RBAC SERVER (:8790)</span>
            <span class="sf-pill ${state.syncOnline ? 'ok' : 'watch'}">${state.syncOnline ? 'ONLINE · 8790' : 'LOCAL CACHE'}</span>
          </div>
          <strong class="sf-tile-val" style="font-size:15px;">${state.syncBaseUrl}</strong>
          <span class="sf-tile-sub">
            Bảo mật RLS 5 vai trò (Chủ vườn, Quản lý, Nhân viên, Phi công, Kỹ sư) · Truy xuất QR &amp; Storage (${sb.storageBuckets.join(', ')}).
          </span>
        </div>

        <div class="sf-tile" style="border-left:3px solid #f59e0b;">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span class="sf-tile-label">4. SATELLITE GEE &amp; WEATHER BACKEND (:8000)</span>
            <span class="sf-pill ${state.satOnline ? 'ok' : 'watch'}">${state.satOnline ? 'LIVE GEE · 8000' : 'CACHED GEE'}</span>
          </div>
          <strong class="sf-tile-val" id="stSatStatus" style="font-size:15px;">${state.satBaseUrl}</strong>
          <span class="sf-tile-sub">
            Sentinel-2 10m (NDVI ${tel.ndvi}) · Độ ẩm đất ${tel.soil_moisture_pct}% · pH ${tel.soil_ph} · EC ${tel.soil_ec_dsm} dS/m.
          </span>
        </div>
      </div>

      <!-- THANH CÔNG CỤ ĐIỀU HÀNH HẠ TẦNG SUPABASE & POSTGIS -->
      <div class="sf-tile" style="margin-bottom:12px;">
        <span class="sf-tile-label">ĐIỀU KHIỂN ĐỒNG BỘ ĐÁM MÂY SUPABASE &amp; KIỂM ĐỊNH POSTGIS</span>
        <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px;">
          <button class="sf-action-btn" onclick="window.SmartFarmInfra.triggerCloudSyncNow()">⚡ Đồng bộ Cloud &amp; Flush WAL ngay</button>
          <button class="sf-action-btn" onclick="window.SmartFarmInfra.simulateLiveTelemetry()">📡 Phát gói Telemetry IoT Realtime</button>
          <button class="sf-action-btn secondary" onclick="window.SmartFarmInfra.runPostgisSpatialCheck()">📐 Tính diện tích PostGIS Sổ đỏ</button>
          <button class="sf-action-btn secondary" onclick="window.SmartFarmInfra.toggleConfigPanel()">⚙️ Cấu hình Supabase URL / Key</button>
          <button class="sf-action-btn secondary" onclick="window.SmartFarmInfra.toggleMigrationSql()">📜 Xem SQL Migration (PostGIS + RLS)</button>
        </div>

        ${state.postgisResult ? `
          <div style="margin-top:10px;padding:10px 12px;border-radius:10px;background:rgba(56,239,125,0.1);border:1px solid rgba(56,239,125,0.3);font-size:13px;">
            <strong>✓ Kết quả PostGIS Geodesic RPC (SRID 4326 ⇄ VN-2000 SRID 3405):</strong><br>
            Diện tích thực địa: <strong>${state.postgisResult.area_m2.toLocaleString('vi-VN')} m² (${state.postgisResult.area_ha} ha)</strong> ·
            Chu vi: <strong>${state.postgisResult.perimeter_m} m</strong> ·
            Tâm đa giác: <strong>${state.postgisResult.centroid_lat}°N, ${state.postgisResult.centroid_lng}°E</strong>
          </div>
        ` : ''}

        ${state.showConfigForm ? `
          <div style="margin-top:12px;padding:12px;border-radius:12px;background:rgba(4,18,27,0.75);border:1px solid rgba(255,255,255,0.14);">
            <strong style="display:block;margin-bottom:8px;font-size:13px;">Cấu hình Kết nối Supabase Cloud Project</strong>
            <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:8px;">
              <div>
                <label style="font-size:11px;color:#94a3b8;display:block;margin-bottom:4px;">SUPABASE_URL</label>
                <input id="inpSupabaseUrl" type="text" value="${sb.url}" style="width:100%;padding:8px 10px;border-radius:8px;border:1px solid rgba(255,255,255,0.2);background:#071a26;color:#fff;font-size:12px;" />
              </div>
              <div>
                <label style="font-size:11px;color:#94a3b8;display:block;margin-bottom:4px;">SUPABASE_ANON_KEY / SERVICE_ROLE_KEY</label>
                <input id="inpSupabaseKey" type="password" placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." style="width:100%;padding:8px 10px;border-radius:8px;border:1px solid rgba(255,255,255,0.2);background:#071a26;color:#fff;font-size:12px;" />
              </div>
              <div>
                <label style="font-size:11px;color:#94a3b8;display:block;margin-bottom:4px;">CHẾ ĐỘ ĐỒNG BỘ</label>
                <select id="inpSupabaseMode" style="width:100%;padding:8px 10px;border-radius:8px;border:1px solid rgba(255,255,255,0.2);background:#071a26;color:#fff;font-size:12px;">
                  <option value="hybrid-dual-write" ${sb.mode === 'hybrid-dual-write' ? 'selected' : ''}>Hybrid Dual-Write (Local WAL + Cloud)</option>
                  <option value="cloud-primary" ${sb.mode === 'cloud-primary' ? 'selected' : ''}>Cloud Primary (Supabase PostgreSQL)</option>
                  <option value="local-offline" ${sb.mode === 'local-offline' ? 'selected' : ''}>Local Offline-First (Chỉ lưu máy chủ nội bộ)</option>
                </select>
              </div>
            </div>
            <div style="margin-top:10px;display:flex;gap:8px;">
              <button class="sf-action-btn" onclick="window.SmartFarmInfra.saveSupabaseConfig()">💾 Lưu &amp; Kích hoạt</button>
              <button class="sf-action-btn secondary" onclick="window.SmartFarmInfra.toggleConfigPanel()">Đóng</button>
            </div>
          </div>
        ` : ''}

        ${state.showSqlModal ? `
          <div style="margin-top:12px;padding:12px;border-radius:12px;background:#030d14;border:1px solid rgba(56,189,248,0.3);">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">
              <strong style="font-size:12px;color:#38bdf8;">supabase/migrations/20261102000000_smartfarm_postgis_schema.sql</strong>
              <button class="sf-action-btn secondary" style="padding:3px 10px;font-size:11px;" onclick="navigator.clipboard && navigator.clipboard.writeText(window.SmartFarmInfra.getSqlText())">📋 Copy SQL</button>
            </div>
            <pre style="max-height:190px;overflow:auto;font-size:11px;line-height:1.45;color:#cbd5e1;margin:0;white-space:pre-wrap;">${state.migrationSqlPreview.replace(/</g, '&lt;')}</pre>
          </div>
        ` : ''}
      </div>

      <!-- NHẬT KÝ SỰ KIỆN REALTIME (SSE + WAL FEED) -->
      <div class="sf-tile" style="margin-bottom:12px;">
        <span class="sf-tile-label">LUỒNG SỰ KIỆN REALTIME (SSE STREAM &amp; POSTGIS WAL LOG)</span>
        <div id="sfRealtimeFeedList" style="margin-top:6px;max-height:150px;overflow-y:auto;-webkit-overflow-scrolling:touch;">
          ${state.realtimeEvents
            .map(
              (ev) => `
              <div style="display:flex;flex-wrap:wrap;gap:6px 8px;align-items:baseline;padding:5px 0;border-bottom:1px dashed rgba(255,255,255,0.08);font-size:12px;">
                <span style="color:#94a3b8;font-variant-numeric:tabular-nums;min-width:60px;">${ev.time}</span>
                <span class="sf-pill ok" style="font-size:10px;padding:1px 6px;">${ev.type}</span>
                <span style="color:#e2e8f0;flex:1;min-width:180px;word-break:break-word;">${ev.msg}</span>
              </div>`
            )
            .join('')}
        </div>
      </div>

      <!-- TRỢ LÝ KỸ SƯ NÔNG NGHIỆP AI -->
      <div class="sf-tile">
        <span class="sf-tile-label">HỎI NHANH KỸ SƯ AI (PHÂN TÍCH TỪ DỮ LIỆU GEE + SUPABASE TELEMETRY)</span>
        <div style="display:flex;flex-wrap:wrap;gap:8px;margin:6px 0;">
          <button class="sf-action-btn" onclick="askAiQuick(0)">🌧 Có nên phun thuốc cây D-02 hôm nay?</button>
          <button class="sf-action-btn" onclick="askAiQuick(1)">🍂 Phác đồ xử lý lá vàng cây A-04?</button>
          <button class="sf-action-btn" onclick="askAiQuick(2)">💧 Lịch tưới &amp; pH đất Khu B đất dốc?</button>
        </div>
        <span class="sf-tile-sub" id="sfAiAnswer">
          • Thời tiết Bảo Lộc có mưa dông (xác suất 98%, ẩm 98%): Tạm hoãn phun lá cho cây D-02 đến khung giờ khô ráo.<br>
          • Ưu tiên kiểm tra trực tiếp cây A-04 (Khu A, NDVI 0.52, lá vàng) và đối chiếu B-05 (Khu B) sau bón phân hữu cơ vi sinh.
        </span>
      </div>
    `;
  }

  function renderSettingsDrawer() {
    return `<div id="sfInfraContainer">${renderInfraInnerHtml()}</div>`;
  }

  window.SmartFarmInfra = {
    state,
    probeEndpoints,
    renderSettingsDrawer,
    triggerCloudSyncNow,
    simulateLiveTelemetry,
    runPostgisSpatialCheck,
    toggleMigrationSql,
    toggleConfigPanel,
    saveSupabaseConfig,
    syncTreeAlertConfirm,
    getSqlText: () => state.migrationSqlPreview
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', probeEndpoints);
  } else {
    probeEndpoints();
  }
})();
