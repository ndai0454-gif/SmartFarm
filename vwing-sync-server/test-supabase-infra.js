// Kiểm thử Hạ tầng Supabase Cloud Bridge, PostGIS Geodesic Engine & WAL Queue
process.env.VWING_TOKEN = 'test-token-123456';
process.env.DATA_DIR = require('path').join(require('os').tmpdir(), 'vwing-infra-' + Date.now());

const test = require('node:test');
const assert = require('node:assert');
const { SupabaseInfraBridge } = require('./supabase-bridge');

test('SupabaseInfraBridge: khởi tạo MirrorDB, ghi Telemetry IoT, cập nhật cây cảnh báo và flush WAL', async () => {
  const bridge = new SupabaseInfraBridge(process.env.DATA_DIR);

  const initial = bridge.getStatus(2);
  assert.equal(initial.ok, true);
  assert.equal(initial.supabase.postgisEnabled, true);
  assert.ok(initial.postgisMirrorTables.sf_gardens >= 2);

  // 1. Ghi gói Telemetry IoT & Viễn thám GEE
  const tel = await bridge.recordTelemetry('vuon-mau-tay-nguyen', {
    zone_name: 'Khu A',
    soil_moisture_pct: 66.5,
    soil_ph: 6.3,
    soil_ec_dsm: 1.38,
    ndvi: 0.76
  });
  assert.equal(tel.garden_key, 'vuon-mau-tay-nguyen');
  assert.equal(tel.soil_moisture_pct, 66.5);
  assert.equal(bridge.getStatus(2).walQueue.pendingOps, 1);

  // 2. Cập nhật trạng thái cây cảnh báo A-04
  const tree = await bridge.updateTreeAlert('vuon-mau-tay-nguyen', 'A-04', {
    health_status: '✓ Đã xác nhận ổn',
    priority: 'Ổn định'
  });
  assert.equal(tree.tree_code, 'A-04');
  assert.equal(tree.health_status, '✓ Đã xác nhận ổn');
  assert.equal(bridge.getStatus(2).walQueue.pendingOps, 2);

  // 3. Flush hàng đợi WAL
  const flushed = await bridge.flushWalNow();
  assert.equal(flushed.ok, true);
  assert.equal(flushed.flushedOps, 2);
  assert.equal(bridge.getStatus(2).walQueue.pendingOps, 0);

  // 4. Tính toán diện tích & chu vi không gian PostGIS Geodesic
  const metrics = bridge.computePostgisMetrics([
    [11.54612, 107.79145],
    [11.54628, 107.79268],
    [11.54518, 107.79291],
    [11.54496, 107.79168]
  ]);
  assert.ok(metrics.area_m2 > 15000 && metrics.area_m2 < 20000);
  assert.ok(metrics.perimeter_m > 450 && metrics.perimeter_m < 650);
  assert.equal(metrics.srid, 4326);
  assert.equal(metrics.vn2000_srid, 3405);
});
