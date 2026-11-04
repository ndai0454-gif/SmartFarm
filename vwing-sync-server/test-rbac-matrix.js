// Ma trận phân quyền theo bảng (R1–R7) và kiểm tra đồng bộ đồng thời (If-Match): node --test test-rbac-matrix.js
// Mỗi dòng của bảng là một thao tác × vai trò × kết quả mong đợi, theo docs/VWING_TECHNOLOGY_PLAN_AND_ROLES.md mục C3.
// R8 (trợ lý AI) không có đường vào máy chủ riêng: chạy trong app với quyền của người hỏi nên không có hàng ở đây.
process.env.VWING_TOKEN = 'test-token-123456';
process.env.DATA_DIR = require('path').join(require('os').tmpdir(), 'vwing-matrix-' + Date.now());
const test = require('node:test');
const assert = require('node:assert');
const server = require('./server');

let base;
const ADMIN = 'test-token-123456';
const ROLES = ['owner', 'manager', 'worker', 'pilot', 'agronomist'];
const people = {};                                   // role → {token,id,name}
const KEY = 'vuon-matrix';
let rev = 0, seq = 0;

async function api(method, url, { token, body, headers } = {}) {
  const h = { ...(headers || {}) };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(base + url, { method, headers: h, body: body === undefined ? undefined : typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body) });
  const ct = r.headers.get('content-type') || '';
  return { status: r.status, data: ct.includes('json') ? await r.json() : ct.includes('text') ? await r.text() : null };
}
async function register(name, i) {
  const r = await api('POST', '/v1/auth/register', { body: { phone: '0955' + String(100000 + i), name, password: 'matkhau-12345' } });
  assert.equal(r.status, 200); return { token: r.data.token, id: r.data.user.id, name };
}
const tree = (id, zone = 'A') => ({ id, code: id.toUpperCase(), zone, active: true, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1 });
const sale = id => ({ id, date: 1, kg: 1, pricePerKg: 1, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1 });
const event = id => ({ id, treeIds: ['seed'], type: 'CARE', activity: 'x', occurredAt: 1, recordedAt: 1, author: 'a', photos: [], version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1 });
const issue = (id, status = 'NEW') => ({ id, number: 1, treeIds: ['seed'], symptom: 's', status, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1 });
const task = id => ({ id, title: 't', treeIds: [], scopeZone: 'A', assignee: '', open: false, done: false, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1 });
const generic = id => ({ id, name: id, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1, treeIds: [], file: id + '.img', kind: 'FIELD', gridFile: null })

/** Gửi một thay đổi bằng vai trò đó; true nếu máy chủ NHẬN (accepted>0 và không bị từ chối). */
async function put(role, body) {
  const who = role === 'admin' ? ADMIN : people[role].token;
  const r = await api('PUT', `/v1/gardens/${KEY}/snapshot`, { token: who, body: JSON.stringify({ trees: [], ...body }), headers: { 'If-Match': String(rev) } });
  assert.equal(r.status, 200, JSON.stringify(r.data));
  rev = r.data.rev;
  return r.data.accepted > 0 && r.data.rejected.length === 0;
}
const u = p => p + '-' + (++seq);

test.before(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r)); base = 'http://127.0.0.1:' + server.address().port;
  let i = 0;
  for (const role of ROLES) people[role] = await register('Người ' + role, ++i);
  people.stranger = await register('Người lạ', ++i);
  const mk = await api('POST', '/v1/gardens', { token: people.owner.token, body: { key: KEY, name: 'Vườn ma trận' } });
  assert.equal(mk.status, 200);
  for (const role of ROLES.slice(1)) {
    const inv = await api('POST', `/v1/gardens/${KEY}/invites`, { token: people.owner.token, body: { role, memberDays: 30 } });
    assert.equal((await api('POST', '/v1/invites/accept', { token: people[role].token, body: { code: inv.data.code } })).status, 200);
  }
  assert.ok(await put('owner', { garden: { name: 'V', updatedAt: 5, pricePerKg: 9 }, zones: [{ name: 'A', desc: '' }], trees: [tree('seed')], sales: [sale('s0')], tasks: [] }));
});
test.after(() => server.close());

// ---------- 1. thay đổi dữ liệu qua đồng bộ: [thao tác, [vai trò ĐƯỢC phép], hàm tạo thay đổi] ----------
const SYNC = [
  ['sửa thông tin vườn',        ['owner', 'manager'],                             () => ({ garden: { name: 'V2', pricePerKg: 9, updatedAt: Date.now() + (++seq) * 1000 } })],
  ['thêm khu',                  ['owner', 'manager', 'worker'],                   () => ({ zones: [{ name: u('Khu'), desc: '' }] })],
  ['tạo cây',                   ['owner', 'manager', 'worker'],                   () => ({ trees: [tree(u('t'))] })],
  ['ghi nhật ký',               ROLES,                                            () => ({ events: [event(u('e'))] })],
  ['ghi doanh thu',             ['owner'],                                        () => ({ sales: [sale(u('s'))] })],
  ['mở vấn đề',                 ['owner', 'manager', 'worker', 'agronomist'],     () => ({ issues: [issue(u('i'))] })],
  ['tạo công việc',             ['owner', 'manager'],                             () => ({ tasks: [task(u('k'))] })],
  ['đóng vấn đề (xác nhận)',    ['owner', 'manager'],                             () => ({ issues: [issue(u('i'), 'CONFIRMED')] })],
  ['nhập ảnh drone/bộ ảnh',     ['owner', 'manager', 'pilot'],                    () => ({ imagery: [generic(u('img'))] })],
  ['nhập nhật ký bay',          ['owner', 'manager', 'pilot'],                    () => ({ droneFlights: [generic(u('fl'))] })],
  ['tạo bản đồ chỉ số (NDVI)',  ['owner', 'manager', 'agronomist'],               () => ({ indices: [generic(u('ix'))] })],
  ['ghi số đo nước/đất',        ['owner', 'manager', 'worker', 'agronomist'],     () => ({ measures: [generic(u('m'))] })],
  ['vẽ lớp bản đồ',             ['owner', 'manager'],                             () => ({ features: [generic(u('f'))] })],
];
for (const [name, allowed, make] of SYNC) {
  for (const role of ROLES) {
    const want = allowed.includes(role);
    test(`đồng bộ · ${name} · ${role} ${want ? 'ĐƯỢC' : 'BỊ CHẶN'}`, async () => {
      assert.equal(await put(role, make()), want);
    });
  }
}

// ---------- 2. xoá (tombstone) ----------
for (const [name, type, mk, allowed] of [
  ['xoá cây', 'trees', () => tree(u('del-t')), ['owner', 'manager']],
  ['xoá doanh thu', 'sales', () => sale(u('del-s')), ['owner']],
]) {
  for (const role of ROLES) {
    const want = allowed.includes(role);
    test(`xoá · ${name} · ${role} ${want ? 'ĐƯỢC' : 'BỊ CHẶN'}`, async () => {
      const e = mk();
      assert.ok(await put('owner', type === 'trees' ? { trees: [e] } : { sales: [e] }));
      await put(role, { tombstones: [{ type, id: e.id, at: Date.now(), by: role }] });
      const snap = (await api('GET', `/v1/gardens/${KEY}/snapshot`, { token: people.owner.token })).data.data;
      const still = snap[type].some(x => x.id === e.id);
      assert.equal(!still, want, `${role} ${want ? 'phải' : 'không được'} xoá được`);
    });
  }
}

// ---------- 3. đường HTTP: [mô tả, vai trò → mã mong đợi, hàm gọi] ----------
const code = (ok, no) => Object.fromEntries(ROLES.map(r => [r, ok.includes(r) ? 200 : no]));
const HTTP = [
  ['xem snapshot',               code(ROLES, 403),                        t => api('GET', `/v1/gardens/${KEY}/snapshot`, { token: t })],
  ['xem danh sách thành viên',   code(['owner', 'manager'], 403),         t => api('GET', `/v1/gardens/${KEY}/members`, { token: t })],
  ['mời Nhân viên',              code(['owner', 'manager'], 403),         t => api('POST', `/v1/gardens/${KEY}/invites`, { token: t, body: { role: 'worker' } })],
  ['mời Quản lý',                code(['owner'], 403),                    t => api('POST', `/v1/gardens/${KEY}/invites`, { token: t, body: { role: 'manager' } })],
  ['xem nhật ký kiểm toán',      code(['owner'], 403),                    t => api('GET', `/v1/gardens/${KEY}/audit`, { token: t })],
  ['xem cấu hình công khai',     code(['owner', 'manager'], 403),         t => api('GET', `/v1/gardens/${KEY}/public`, { token: t })],
  ['sửa cấu hình công khai',     code(['owner'], 403),                    t => api('PUT', `/v1/gardens/${KEY}/public`, { token: t, body: { enabled: false, fields: [] } })],
  ['chuyển quyền chủ',           code([], 403),                           t => api('POST', `/v1/gardens/${KEY}/transfer`, { token: t, body: { userId: 'x', password: 'sai' } })],
  ['xoá vườn',                   code([], 403),                           t => api('DELETE', `/v1/gardens/${KEY}`, { token: t, headers: { 'X-Confirm': 'sai-ma' } })],
  ['bật chia sẻ vị trí',         code(ROLES, 403),                        t => api('PUT', `/v1/gardens/${KEY}/live/me`, { token: t, body: { minutes: 5 } })],
  ['tải ảnh lên',                code(ROLES, 403),                        t => api('PUT', `/v1/gardens/${KEY}/photos/${u('p')}.jpg`, { token: t, body: Buffer.from([1, 2, 3]) })],
];
for (const [name, expect, call] of HTTP) {
  for (const role of ROLES) {
    test(`HTTP · ${name} · ${role} → ${expect[role]}`, async () => {
      const r = await call(people[role].token);
      // chuyển quyền/xoá vườn: người không phải chủ nhận 403; chủ bị chặn bởi thiếu xác nhận/mật khẩu nên 400/403, không bao giờ 200
      if (name === 'chuyển quyền chủ' || name === 'xoá vườn') assert.notEqual(r.status, 200);
      if (role === 'owner' && (name === 'chuyển quyền chủ' || name === 'xoá vườn')) assert.ok([400, 403, 404].includes(r.status));
      else if (name === 'chuyển quyền chủ' || name === 'xoá vườn') assert.equal(r.status, 403);
      else assert.equal(r.status, expect[role]);
    });
  }
}

// ---------- 4. người chưa đăng nhập / người lạ / quản trị ----------
test('chưa đăng nhập: mọi đường trong vườn đều 401', async () => {
  for (const [m, p] of [['GET', 'snapshot'], ['PUT', 'snapshot'], ['GET', 'members'], ['GET', 'audit'], ['GET', 'public'], ['GET', 'live'], ['GET', 'photos']]) {
    assert.equal((await api(m, `/v1/gardens/${KEY}/${p}`)).status, 401, `${m} ${p}`);
  }
  assert.equal((await api('GET', '/v1/me')).status, 401);
});
test('người lạ (không phải thành viên): 404 cho mọi đường, không lộ vườn tồn tại', async () => {
  for (const [m, p] of [['GET', 'snapshot'], ['GET', 'members'], ['GET', 'audit'], ['GET', 'public'], ['GET', 'live'], ['GET', 'photos'], ['POST', 'invites'], ['DELETE', '']]) {
    assert.equal((await api(m, `/v1/gardens/${KEY}/${p}`, { token: people.stranger.token, body: m === 'POST' ? { role: 'worker' } : undefined })).status, 404, `${m} ${p}`);
  }
});
test('R7 quản trị (mã hệ thống) có quyền như chủ: ghi doanh thu, xem kiểm toán', async () => {
  assert.ok(await put('admin', { sales: [sale(u('s-admin'))] }));
  assert.equal((await api('GET', `/v1/gardens/${KEY}/audit`, { token: ADMIN })).status, 200);
});
test('R6 người mua: trang công khai không cần đăng nhập, mặc định tắt', async () => {
  assert.equal((await api('GET', `/p/${KEY}/seed`)).status, 404);
  assert.equal((await api('GET', `/v1/public/${KEY}/trees/seed`)).status, 404);
});

// ---------- 5. dữ liệu bị ẩn theo vai trò ----------
for (const role of ROLES) {
  const sees = ['owner', 'manager', 'agronomist'].includes(role);
  test(`đọc · doanh thu ${sees ? 'THẤY' : 'ẨN'} với ${role}`, async () => {
    const s = (await api('GET', `/v1/gardens/${KEY}/snapshot`, { token: people[role].token })).data.data;
    assert.equal(s.sales.length > 0, sees);
    assert.equal(s.garden.pricePerKg !== undefined, !['worker', 'pilot'].includes(role));
  });
}

// ---------- 6. đồng bộ đồng thời (If-Match) ----------
test('hai thiết bị cùng gửi với cùng If-Match: đúng một bên thắng, bên kia nhận 409', async () => {
  const cur = rev;
  const send = (role, id) => api('PUT', `/v1/gardens/${KEY}/snapshot`, { token: people[role].token, headers: { 'If-Match': String(cur) }, body: JSON.stringify({ trees: [tree(id)] }) });
  const [a, b] = await Promise.all([send('worker', u('race')), send('manager', u('race'))]);
  assert.deepEqual([a.status, b.status].sort(), [200, 409]);
  rev = Math.max(a.data.rev || 0, b.data.rev || 0, (a.status === 409 ? a.data.rev : b.data.rev) || 0);
});

test('hai nhân viên cùng sửa một cây khi mất mạng: bản sau không ghi đè bản trước, không mất dữ liệu', async () => {
  const t0 = tree(u('shared'));
  assert.ok(await put('owner', { trees: [t0] }));
  const base0 = rev;                                          // cả hai thiết bị đang giữ rev này khi mất mạng
  const w1 = { ...t0, variety: 'Ri6', version: 2, baseVersion: 1, opId: 'w1', updatedAt: 2 };
  const w2 = { ...t0, variety: 'Monthong', version: 2, baseVersion: 1, opId: 'w2', updatedAt: 3 };
  // thiết bị 1 có mạng trước
  let r1 = await api('PUT', `/v1/gardens/${KEY}/snapshot`, { token: people.worker.token, headers: { 'If-Match': String(base0) }, body: JSON.stringify({ trees: [w1] }) });
  assert.equal(r1.status, 200); rev = r1.data.rev;
  // thiết bị 2 có mạng sau, vẫn giữ rev cũ → bị từ chối
  let r2 = await api('PUT', `/v1/gardens/${KEY}/snapshot`, { token: people.manager.token, headers: { 'If-Match': String(base0) }, body: JSON.stringify({ trees: [w2] }) });
  assert.equal(r2.status, 409);
  // thiết bị 2 kéo bản mới (thấy bản w1), cố đẩy lại bản của mình với rev mới: máy chủ coi là cũ (cùng phiên bản, khác thao tác) và KHÔNG ghi đè
  r2 = await api('PUT', `/v1/gardens/${KEY}/snapshot`, { token: people.manager.token, headers: { 'If-Match': String(rev) }, body: JSON.stringify({ trees: [w2] }) });
  assert.equal(r2.status, 200); rev = r2.data.rev; assert.ok(r2.data.stale >= 1);
  const kept = (await api('GET', `/v1/gardens/${KEY}/snapshot`, { token: people.owner.token })).data.data.trees.find(t => t.id === t0.id);
  assert.equal(kept.variety, 'Ri6');                         // bản đến trước được giữ; bản kia phải giải xung đột phía app (mục Xung đột)
});
