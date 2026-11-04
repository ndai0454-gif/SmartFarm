// Phân quyền theo vườn (RBAC) và xoá lan truyền — chạy ở MÁY CHỦ nên không thể qua mặt bằng cách sửa app.
// Hàm thuần (không đụng đĩa/mạng) để kiểm thử trực tiếp. Ma trận quyền theo docs/VWING_TECHNOLOGY_PLAN_AND_ROLES.md mục C3.
const crypto = require('crypto');

const ROLES = ['owner', 'manager', 'worker', 'pilot', 'agronomist'];
const ROLE_LABEL = { owner: 'Chủ vườn', manager: 'Quản lý', worker: 'Nhân viên', pilot: 'Phi công', agronomist: 'Chuyên gia' };
const COLLECTIONS = ['trees', 'events', 'issues', 'tasks', 'sales', 'features', 'imagery', 'measures', 'indices', 'droneFlights'];
const ALL = new Set([...COLLECTIONS, 'garden', 'zones']);

const WRITE = {
  owner: ALL,
  manager: new Set([...ALL].filter(c => c !== 'sales')),
  worker: new Set(['trees', 'events', 'issues', 'measures', 'zones', 'tasks']),     // tasks: chỉ việc được giao (xem checkTaskUpdate)
  pilot: new Set(['events', 'imagery', 'droneFlights', 'tasks']),
  agronomist: new Set(['events', 'issues', 'measures', 'indices']),
};
const DELETE = { owner: ALL, manager: new Set([...ALL].filter(c => c !== 'sales')), worker: new Set(), pilot: new Set(), agronomist: new Set() };
const HIDDEN_READ = { owner: [], manager: [], worker: ['sales'], pilot: ['sales'], agronomist: [] };
const HIDE_PRICE = new Set(['worker', 'pilot']);
const PRICE_FIELDS = ['pricePerKg', 'priceSource', 'priceRegion', 'priceGrade', 'priceDate'];
// Vai trò được mời / sửa vai trò: chủ mời được mọi vai trò (trừ chủ); quản lý chỉ mời cấp thấp hơn.
const MANAGER_GRANTS = new Set(['worker', 'pilot', 'agronomist']);

const SYNC_ONLY = new Set(['sync']);
function clean(e) { const c = { ...e }; for (const k of SYNC_ONLY) delete c[k]; return JSON.stringify(sortKeys(c)); }
function sortKeys(v) {
  if (Array.isArray(v)) return v.map(sortKeys);
  if (v && typeof v === 'object') return Object.fromEntries(Object.keys(v).sort().map(k => [k, sortKeys(v[k])]));
  return v;
}
const newOp = () => crypto.randomBytes(8).toString('hex');
function bump(e) { return { ...e, baseVersion: e.version || 1, version: (e.version || 1) + 1, opId: newOp(), updatedAt: Date.now() }; }

function emptySnapshot() {
  const s = { garden: null, zones: [], tombstones: [] };
  for (const c of COLLECTIONS) s[c] = [];
  return s;
}
function normalize(s) {
  const o = { ...emptySnapshot(), ...(s || {}) };
  for (const c of [...COLLECTIONS, 'zones', 'tombstones']) if (!Array.isArray(o[c])) o[c] = [];
  return o;
}

/** scope = { zones: [tên khu] }; rỗng/không có = toàn vườn. */
function zonesOf(scope) { return scope && Array.isArray(scope.zones) && scope.zones.length ? new Set(scope.zones) : null; }

function allowedTreeIds(snap, zs) {
  return new Set(snap.trees.filter(t => zs.has(t.zone)).map(t => t.id));
}
function visibleIn(coll, e, zs, treeOk) {
  if (!zs) return true;
  switch (coll) {
    case 'trees': return zs.has(e.zone);
    case 'events': case 'issues': case 'tasks': case 'measures':
      if (e.scopeZone && zs.has(e.scopeZone)) return true;
      return Array.isArray(e.treeIds) && e.treeIds.some(id => treeOk.has(id));
    case 'zones': return zs.has(e.name);
    default: return true;      // bản đồ, ảnh, chỉ số, nhật ký bay: không gắn khu
  }
}

/** Bản chụp mà thành viên này được phép xem (lọc theo vai trò và phạm vi). */
function redact(snap, role, scope) {
  const s = normalize(snap);
  const out = { ...s };
  for (const c of HIDDEN_READ[role] || []) out[c] = [];
  if (HIDE_PRICE.has(role) && s.garden) { out.garden = { ...s.garden }; for (const f of PRICE_FIELDS) delete out.garden[f]; }
  const zs = zonesOf(scope);
  if (zs) {
    const ok = allowedTreeIds(s, zs);
    for (const c of [...COLLECTIONS, 'zones']) out[c] = out[c].filter(e => visibleIn(c, e, zs, ok));
  }
  return out;
}

function sameAssignee(task, userName) {
  return !!userName && String(task.assignee || '').trim().toLowerCase() === String(userName).trim().toLowerCase();
}
const TASK_DONE_FIELDS = ['done', 'doneAt', 'doneBy', 'version', 'baseVersion', 'updatedAt', 'opId', 'sync'];
const CLAIM_FIELDS = ['assignee', 'open', 'version', 'baseVersion', 'updatedAt', 'opId', 'sync'];
function onlyChanged(a, b, allowed) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (!allowed.includes(k) && JSON.stringify(a[k]) !== JSON.stringify(b[k])) return false;
  return true;
}
function onlyDoneChanged(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (!TASK_DONE_FIELDS.includes(k) && JSON.stringify(a[k]) !== JSON.stringify(b[k])) return false;
  return true;
}

function refs(e) { return Array.isArray(e.treeIds) ? e.treeIds : []; }

/**
 * Xoá một thực thể và mọi thứ phụ thuộc. Trả về danh sách bản ghi bị xoá; bản ghi chỉ bị bỏ liên kết thì được tăng phiên bản
 * (để thiết bị khác nhận bản mới như một lần cập nhật bình thường, không thành xung đột).
 */
function cascade(snap, coll, id, by, removed, now) {
  const rm = (c, pred) => {
    const gone = snap[c].filter(pred);
    if (!gone.length) return [];
    snap[c] = snap[c].filter(e => !pred(e));
    for (const g of gone) removed.push({ type: c, id: g.id || g.name, at: now, by });
    return gone;
  };
  if (coll === 'trees') {
    if (!rm('trees', t => t.id === id).length) return;
    for (const c of ['events', 'issues', 'tasks', 'measures']) {
      for (const e of [...snap[c]]) {
        if (!refs(e).includes(id)) continue;
        const left = refs(e).filter(x => x !== id);
        const orphan = !left.length && !e.scopeZone && !(c === 'measures' && e.point);
        if (orphan) cascade(snap, c, e.id, by, removed, now);
        else snap[c] = snap[c].map(x => x.id === e.id ? bump({ ...x, treeIds: left }) : x);
      }
    }
    snap.trees = snap.trees.map(t => (t.replacesId === id || t.replacedById === id)
      ? bump({ ...t, replacesId: t.replacesId === id ? null : t.replacesId, replacedById: t.replacedById === id ? null : t.replacedById }) : t);
    snap.imagery = snap.imagery.map(s => Array.isArray(s.detections) && s.detections.some(d => d.treeId === id)
      ? bump({ ...s, detections: s.detections.map(d => d.treeId === id ? { ...d, treeId: null, status: 'PENDING' } : d) }) : s);
  } else if (coll === 'events') {
    if (!rm('events', e => e.id === id).length) return;
    snap.issues = snap.issues.map(i => i.originEventId === id ? bump({ ...i, originEventId: null }) : i);
  } else if (coll === 'issues') {
    if (!rm('issues', i => i.id === id).length) return;
    for (const t of snap.tasks.filter(t => t.issueId === id)) cascade(snap, 'tasks', t.id, by, removed, now);
    snap.events = snap.events.map(e => e.issueId === id ? bump({ ...e, issueId: null }) : e);
  } else if (coll === 'zones') {
    if (!rm('zones', z => z.name === id).length) return;
    for (const t of snap.trees.filter(t => t.zone === id)) cascade(snap, 'trees', t.id, by, removed, now);
    for (const c of ['events', 'issues', 'tasks', 'measures']) {
      for (const e of snap[c].filter(e => e.scopeZone === id && !refs(e).length)) cascade(snap, c, e.id, by, removed, now);
    }
  } else if (COLLECTIONS.includes(coll)) {
    rm(coll, e => e.id === id);
  }
}

/** Tên tệp (ảnh, lưới, log bay) mà bản chụp đang tham chiếu. */
function referencedFiles(s) {
  const base = p => String(p || '').split(/[\\/]/).pop();
  const names = [];
  for (const e of s.events || []) for (const p of e.photos || []) names.push(base(p.path));
  for (const i of s.imagery || []) names.push(i.file);
  for (const v of s.indices || []) if (v.gridFile) names.push(v.gridFile);
  for (const f of s.droneFlights || []) names.push(f.file);
  return new Set(names.filter(Boolean));
}

/**
 * Áp bản chụp do thiết bị gửi lên vào bản của máy chủ, theo quyền. Không bao giờ coi "vắng mặt" là xoá: chỉ có tombstone mới xoá.
 * Trả {snapshot, accepted, rejected, stale, removed}. Mục bị từ chối không làm hỏng cả lượt đồng bộ.
 */
function applyPut(oldSnap, incoming, member) {
  const role = member.role, zs = zonesOf(member.scope), now = Date.now();
  const snap = normalize(JSON.parse(JSON.stringify(oldSnap || emptySnapshot())));
  const inc = normalize(incoming);
  const rejected = [], removed = [];
  let accepted = 0, stale = 0;
  const can = (set, c) => (set[role] || new Set()).has(c);
  const reject = (c, id, reason) => rejected.push({ collection: c, id, reason });

  const tombKey = t => t.type + '\u0000' + t.id;
  const treeOk = () => (zs ? allowedTreeIds(snap, zs) : null);

  // Chỉ những yêu cầu xoá được phép mới có hiệu lực (kể cả để chặn bản ghi cùng lượt); yêu cầu bị cấm chỉ bị ghi vào danh sách từ chối.
  const incTomb = [];
  for (const t of inc.tombstones) {
    if (!t || typeof t.type !== 'string' || typeof t.id !== 'string') continue;
    const c = t.type;
    if (!ALL.has(c) || c === 'garden') { reject(String(c), t.id, 'Loại bản ghi không hợp lệ.'); continue; }
    if (!can(DELETE, c)) { reject(c, t.id, 'Vai trò ' + ROLE_LABEL[role] + ' không được xoá mục này.'); continue; }
    const target = c === 'zones' ? snap.zones.find(z => z.name === t.id) : snap[c].find(e => e.id === t.id);
    if (target && zs && !visibleIn(c, target, zs, treeOk())) { reject(c, t.id, 'Ngoài phạm vi khu được cấp.'); continue; }
    if (c === 'zones' && zs) { reject(c, t.id, 'Người có phạm vi riêng không được xoá khu.'); continue; }
    incTomb.push(t);
  }
  const dead = new Set([...snap.tombstones, ...incTomb].map(tombKey));

  // 1) thêm/sửa
  for (const c of COLLECTIONS) {
    for (const e of inc[c]) {
      if (!e || typeof e.id !== 'string') continue;
      if (dead.has(c + '\u0000' + e.id)) continue;                 // đã bị xoá ở nơi khác (hoặc đang bị xoá lượt này)
      const old = snap[c].find(x => x.id === e.id);
      if (old && clean(old) === clean(e)) continue;
      if (old && (e.version || 1) <= (old.version || 1)) { stale++; continue; }
      if (!can(WRITE, c)) { reject(c, e.id, 'Vai trò ' + ROLE_LABEL[role] + ' không được sửa mục này.'); continue; }
      const ok = treeOk();
      if (zs && !visibleIn(c, e, zs, ok)) { reject(c, e.id, 'Ngoài phạm vi khu được cấp.'); continue; }
      if (old && zs && !visibleIn(c, old, zs, ok)) { reject(c, e.id, 'Ngoài phạm vi khu được cấp.'); continue; }
      if (c === 'tasks') {
        if (!['owner', 'manager'].includes(role)) {
          if (!old) { reject(c, e.id, 'Không được tạo công việc.'); continue; }
          // Ba việc người nhận việc được làm: nhận một đơn việc mở, trả lại việc mình đã nhận, hoàn tất việc được giao cho mình.
          const claim = old.open === true && !old.assignee && sameAssignee(e, member.name) && onlyChanged(old, e, CLAIM_FIELDS);
          const release = sameAssignee(old, member.name) && !old.done && e.open === true && !e.assignee && onlyChanged(old, e, CLAIM_FIELDS);
          const done = sameAssignee(old, member.name) && onlyDoneChanged(old, e);
          if (!(claim || release || done)) {
            const taken = old.open !== true && old.assignee && !sameAssignee(old, member.name) && sameAssignee(e, member.name);
            reject(c, e.id, taken ? 'Việc này đã có người khác nhận.' : 'Chỉ được nhận đơn việc mở, trả lại hoặc hoàn tất việc của mình.'); continue;
          }
        }
      }
      if (c === 'issues') {
        const closes = e.status === 'CONFIRMED' && (!old || old.status !== 'CONFIRMED');
        const reopens = old && old.status === 'CONFIRMED' && e.status !== 'CONFIRMED';
        if ((closes || reopens || (old && old.status === 'CONFIRMED')) && !['owner', 'manager'].includes(role)) {
          reject(c, e.id, 'Chỉ chủ vườn hoặc quản lý được xác nhận/mở lại kết quả đóng vấn đề.'); continue;
        }
      }
      if (c === 'events' || c === 'issues' || c === 'tasks' || c === 'measures') {
        const gone = refs(e).filter(id => dead.has('trees\u0000' + id));
        if (gone.length) {
          const left = refs(e).filter(id => !gone.includes(id));
          if (!left.length && !e.scopeZone) { reject(c, e.id, 'Cây liên quan đã bị xoá.'); continue; }
          e.treeIds = left;
        }
      }
      snap[c] = old ? snap[c].map(x => x.id === e.id ? e : x) : [...snap[c], e];
      accepted++;
    }
  }

  // 2) thông tin vườn: bản mới hơn theo updatedAt thắng
  if (inc.garden && typeof inc.garden === 'object') {
    const g = inc.garden;
    const oldG = snap.garden;
    const same = oldG && JSON.stringify(sortKeys({ ...oldG })) === JSON.stringify(sortKeys({ ...g }));
    if (!same) {
      if (oldG && (g.updatedAt || 0) <= (oldG.updatedAt || 0)) stale++;
      else if (!can(WRITE, 'garden')) reject('garden', 'garden', 'Chỉ chủ vườn hoặc quản lý được sửa thông tin vườn.');
      else {
        const next = { ...g };
        if (HIDE_PRICE.has(role) && oldG) for (const f of PRICE_FIELDS) next[f] = oldG[f];
        snap.garden = next; accepted++;
      }
    }
  }

  // 3) khu: chỉ thêm khu mới; mô tả khu do chủ/quản lý sửa
  for (const z of inc.zones) {
    if (!z || typeof z.name !== 'string' || !z.name.trim() || dead.has('zones\u0000' + z.name)) continue;
    const old = snap.zones.find(x => x.name === z.name);
    if (!old) {
      if (!can(WRITE, 'zones')) { reject('zones', z.name, 'Không được tạo khu.'); continue; }
      if (zs && !zs.has(z.name)) { reject('zones', z.name, 'Ngoài phạm vi khu được cấp.'); continue; }
      snap.zones.push({ name: z.name, desc: String(z.desc || '') }); accepted++;
    } else if ((old.desc || '') !== (z.desc || '') && z.desc && can(WRITE, 'garden') && !(zs)) {
      old.desc = String(z.desc); accepted++;
    }
  }

  // 4) xoá (tombstone) + lan truyền
  const have = new Set(snap.tombstones.map(tombKey));
  for (const t of incTomb) {
    if (have.has(tombKey(t))) continue;
    const c = t.type;
    const gone = [];
    cascade(snap, c, t.id, member.name || role, gone, now);
    if (!gone.length) gone.push({ type: c, id: t.id, at: now, by: member.name || role });   // vẫn ghi dấu để thiết bị khác xoá theo
    for (const g of gone) { const k = tombKey(g); if (!have.has(k)) { have.add(k); snap.tombstones.push(g); removed.push(g); } }
  }
  if (snap.tombstones.length > 5000) snap.tombstones = snap.tombstones.slice(-5000);

  return { snapshot: snap, accepted, rejected, stale, removed };
}

/** Thành viên còn hiệu lực (chưa thu hồi, chưa hết hạn). */
function activeMember(m, now = Date.now()) {
  return !!m && !m.revokedAt && !(m.expiresAt && m.expiresAt <= now);
}

module.exports = {
  ROLES, ROLE_LABEL, COLLECTIONS, MANAGER_GRANTS, WRITE, DELETE,
  normalize, emptySnapshot, redact, applyPut, cascade, referencedFiles, activeMember, zonesOf, visibleIn, allowedTreeIds,
};
