// Trang truy xuất công khai cho người mua (R6): quét QR trên sản phẩm → xem lịch sử của cây/lô.
// Chủ vườn CHỌN từng trường được hiện. Danh sách trường là danh sách trắng: tên người ghi, người phụ trách, vấn đề, công việc,
// doanh thu và nhật ký sửa đổi không bao giờ có trong trang công khai, dù chủ chọn gì.
const fs = require('fs');
const path = require('path');

const FIELDS = [
  { id: 'garden.name', label: 'Tên vườn' },
  { id: 'garden.location', label: 'Địa điểm' },
  { id: 'garden.crop', label: 'Cây trồng' },
  { id: 'garden.areaHa', label: 'Diện tích vườn' },
  { id: 'tree.code', label: 'Mã cây' },
  { id: 'tree.zone', label: 'Khu vực' },
  { id: 'tree.variety', label: 'Giống' },
  { id: 'tree.plantedYear', label: 'Năm trồng' },
  { id: 'tree.condition', label: 'Tình trạng hiện tại' },
  { id: 'tree.position', label: 'Vị trí (làm tròn ~10 m)' },
  { id: 'timeline.activities', label: 'Lịch sử chăm sóc (loại, hoạt động, ngày)' },
  { id: 'timeline.products', label: 'Vật tư và liều lượng đã dùng' },
  { id: 'timeline.harvest', label: 'Thu hoạch (ngày, khối lượng)' },
  { id: 'timeline.notes', label: 'Ghi chú trong nhật ký' },
  { id: 'timeline.photos', label: 'Ảnh trong nhật ký' },
  { id: 'lastCheck', label: 'Lần kiểm tra gần nhất' },
];

const CONDITION = { UNASSESSED: 'Chưa đánh giá', STABLE: 'Ổn định', WATCH: 'Theo dõi', NEEDS_CHECK: 'Cần kiểm tra' };
const EVENT_TYPE = { OBSERVATION: 'Quan sát', CARE: 'Chăm sóc', HARVEST: 'Thu hoạch', RECHECK: 'Tái kiểm tra' };
const base = p => String(p || '').split(/[\\/]/).pop();
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const day = ms => { const d = new Date(ms); return `${String(d.getUTCDate()).padStart(2, '0')}/${String(d.getUTCMonth() + 1).padStart(2, '0')}/${d.getUTCFullYear()}`; };

/** Dữ liệu công khai của một cây, đã lọc theo trường chủ chọn. Trả null nếu không công khai hoặc không có cây. */
function treeView(meta, snap, treeId) {
  const cfg = meta && meta.public;
  if (!cfg || !cfg.enabled || !snap) return null;
  const tree = (snap.trees || []).find(t => t.id === treeId && t.active !== false);
  if (!tree) return null;
  const on = new Set(cfg.fields || []);
  const out = { story: cfg.story || '' };
  const g = snap.garden || {};
  const garden = {};
  if (on.has('garden.name')) garden.name = g.name || meta.name;
  if (on.has('garden.location')) garden.location = g.location;
  if (on.has('garden.crop')) garden.crop = g.crop;
  if (on.has('garden.areaHa') && g.areaHa != null) garden.areaHa = g.areaHa;
  if (Object.keys(garden).length) out.garden = garden;
  const t = {};
  if (on.has('tree.code')) t.code = tree.code;
  if (on.has('tree.zone')) t.zone = tree.zone;
  if (on.has('tree.variety') && tree.variety) t.variety = tree.variety;
  if (on.has('tree.plantedYear') && tree.plantedYear) t.plantedYear = tree.plantedYear;
  if (on.has('tree.condition')) t.condition = CONDITION[tree.condition] || undefined;
  if (on.has('tree.position') && tree.pos) t.position = { lat: Math.round(tree.pos.lat * 1e4) / 1e4, lng: Math.round(tree.pos.lng * 1e4) / 1e4 };
  if (Object.keys(t).length) out.tree = t;
  const evs = (snap.events || []).filter(e => (e.treeIds || []).includes(tree.id)).sort((a, b) => a.occurredAt - b.occurredAt);
  const wantsTimeline = ['timeline.activities', 'timeline.products', 'timeline.harvest', 'timeline.notes', 'timeline.photos'].some(f => on.has(f));
  if (wantsTimeline) {
    out.timeline = evs.map(e => {
      const row = { at: e.occurredAt };
      const isHarvest = e.type === 'HARVEST';
      if (on.has('timeline.activities') || (isHarvest && on.has('timeline.harvest'))) { row.type = EVENT_TYPE[e.type] || e.type; row.activity = e.activity; }
      if (on.has('timeline.products') && e.product) { row.product = e.product; if (e.dosage) row.dosage = e.dosage; }
      if (isHarvest && on.has('timeline.harvest') && e.amountKg != null) row.amountKg = e.amountKg;
      if (on.has('timeline.notes') && e.note) row.note = e.note;
      if (on.has('timeline.photos') && (e.photos || []).length) row.photos = e.photos.map(p => base(p.path)).filter(n => /^[A-Za-z0-9._-]{1,100}$/.test(n));
      return row;
    }).filter(r => Object.keys(r).length > 1);
  }
  if (on.has('lastCheck') && evs.length) out.lastCheck = evs[evs.length - 1].occurredAt;
  return out;
}

const PAGE_CSP = "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'";

function renderHtml(v, key) {
  const rows = (v.timeline || []).map(r => {
    const bits = [];
    if (r.type || r.activity) bits.push(`<b>${esc(r.type || '')}</b> ${esc(r.activity || '')}`);
    if (r.product) bits.push(`Vật tư: ${esc(r.product)}${r.dosage ? ' · ' + esc(r.dosage) : ''}`);
    if (r.amountKg != null) bits.push(`Khối lượng: ${esc(r.amountKg)} kg`);
    if (r.note) bits.push(`<i>${esc(r.note)}</i>`);
    const imgs = (r.photos || []).map(n => `<img loading="lazy" src="/p/${esc(key)}/photo/${esc(n)}" alt="Ảnh nhật ký">`).join('');
    return `<li><time>${day(r.at)}</time><div>${bits.join('<br>')}${imgs ? `<div class="ph">${imgs}</div>` : ''}</div></li>`;
  }).join('');
  const kv = (o, labels) => Object.entries(labels).filter(([k]) => o && o[k] != null && o[k] !== '').map(([k, l]) => `<div><span>${l}</span><b>${esc(o[k])}</b></div>`).join('');
  const treeBlock = kv(v.tree && { ...v.tree, position: v.tree.position ? `${v.tree.position.lat}, ${v.tree.position.lng}` : null },
    { code: 'Mã cây', zone: 'Khu vực', variety: 'Giống', plantedYear: 'Năm trồng', condition: 'Tình trạng', position: 'Vị trí' });
  const gardenBlock = kv(v.garden && { ...v.garden, areaHa: v.garden.areaHa != null ? v.garden.areaHa + ' ha' : null }, { name: 'Vườn', location: 'Địa điểm', crop: 'Cây trồng', areaHa: 'Diện tích' });
  return `<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>Truy xuất nguồn gốc · VWing SmartFarm</title><style>
::selection{background:rgba(20,99,58,0.18);color:#0D2117}
body{font:15px/1.55 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;margin:0;background:#F2F6F4;color:#0D2117;-webkit-font-smoothing:antialiased}
main{max-width:640px;margin:0 auto;padding:24px 16px 56px}
.hdr{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px}
.badge{display:inline-flex;align-items:center;gap:6px;padding:5px 12px;border-radius:999px;background:#DCFCE7;color:#14633A;font-size:12.5px;font-weight:700;border:1px solid #BBF7D0}
h1{font-size:24px;font-weight:700;letter-spacing:-0.5px;margin:4px 0 2px;color:#0D2117}
.card{background:#FFFFFF;border:1px solid #CBD8D1;border-radius:22px;padding:18px 20px;margin:14px 0;box-shadow:0 6px 20px rgba(13,33,23,0.04)}
.kv div{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid #E1EAE5;font-variant-numeric:tabular-nums}
.kv div:last-child{border:0;padding-bottom:0}.kv div:first-child{padding-top:0}
.kv span{color:#52665B;font-weight:500}.kv b{color:#0D2117;font-weight:600;text-align:right}
ol{list-style:none;padding:0;margin:8px 0 0}
li{display:flex;gap:14px;padding:12px 0;border-bottom:1px solid #E1EAE5;font-variant-numeric:tabular-nums}
li:last-child{border:0;padding-bottom:0}
li time{min-width:92px;color:#14633A;font-size:13px;font-weight:700;background:#E6EFEA;padding:4px 8px;border-radius:8px;height:fit-content;text-align:center}
.ph img{width:96px;height:96px;object-fit:cover;border-radius:12px;border:1px solid #CBD8D1;margin:8px 8px 0 0}
small{color:#52665B;font-size:13.5px}
</style></head><body><main>
<div class="hdr"><div><h1>Truy xuất nguồn gốc</h1><small>Thông tin minh bạch do chủ vườn chọn công khai.</small></div><span class="badge">✓ VWing Verified</span></div>
${v.story ? `<div class="card">${esc(v.story)}</div>` : ''}
${gardenBlock ? `<div class="card kv">${gardenBlock}</div>` : ''}${treeBlock ? `<div class="card kv">${treeBlock}</div>` : ''}
${v.lastCheck ? `<div class="card kv"><div><span>Kiểm tra gần nhất</span><b>${day(v.lastCheck)}</b></div></div>` : ''}
${rows ? `<div class="card"><b>Lịch sử chăm sóc &amp; thu hoạch</b><ol>${rows}</ol></div>` : ''}</main></body></html>`;
}

const NOT_FOUND = '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><body style="font:16px system-ui;padding:24px">Không có thông tin công khai cho mã này.</body>';

function handle(parts, res, d) {
  // /p/<key>/<treeId>   hoặc   /p/<key>/photo/<name>
  const key = parts[1];
  const html = (code, body) => d.send(res, code, body, 'text/html; charset=utf-8', { 'Content-Security-Policy': PAGE_CSP, 'Referrer-Policy': 'no-referrer' });
  if (!key || !d.KEY_RE.test(key)) return html(404, NOT_FOUND);
  const meta = d.readMeta(key), snap = d.readSnapshot(key);
  if (parts[2] === 'photo' && parts.length === 4) {
    const name = parts[3];
    if (!d.PHOTO_RE.test(name) || !meta || !meta.public || !meta.public.enabled || !(meta.public.fields || []).includes('timeline.photos') || !snap) return html(404, NOT_FOUND);
    const active = new Set((snap.trees || []).filter(t => t.active !== false).map(t => t.id));
    const referenced = (snap.events || []).some(e => (e.treeIds || []).some(id => active.has(id)) && (e.photos || []).some(p => base(p.path) === name));
    const file = path.join(d.gardenDir(key), 'photos', name);
    if (!referenced || !fs.existsSync(file)) return html(404, NOT_FOUND);
    return d.send(res, 200, fs.readFileSync(file), 'image/jpeg', { 'Cache-Control': 'public, max-age=3600' });
  }
  if (parts.length !== 3 || !d.ID_RE.test(parts[2])) return html(404, NOT_FOUND);
  const v = treeView(meta, snap, parts[2]);
  if (!v) return html(404, NOT_FOUND);
  return html(200, renderHtml(v, key));
}

function handleJson(parts, res, d) {
  // /v1/public/<key>/trees/<treeId>
  if (parts[3] !== 'trees' || parts.length !== 5 || !d.KEY_RE.test(parts[2]) || !d.ID_RE.test(parts[4])) return d.send(res, 404, { error: 'Không tìm thấy.' });
  const v = treeView(d.readMeta(parts[2]), d.readSnapshot(parts[2]), parts[4]);
  return v ? d.send(res, 200, v) : d.send(res, 404, { error: 'Không có thông tin công khai.' });
}

module.exports = { FIELDS, treeView, handle, handleJson, renderHtml };
