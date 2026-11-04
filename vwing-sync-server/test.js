// Kiểm thử máy chủ: node --test test.js
process.env.VWING_TOKEN = 'test-token-123456';
process.env.DATA_DIR = require('path').join(require('os').tmpdir(), 'vwing-sync-test-' + Date.now());
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const server = require('./server');

let base;
test.before(async () => { await new Promise(r => server.listen(0, '127.0.0.1', r)); base = 'http://127.0.0.1:' + server.address().port; });
test.after(() => server.close());

const ADMIN = 'test-token-123456';
async function api(method, url, { token, body, headers } = {}) {
  const h = { ...(headers || {}) };
  if (token) h.Authorization = 'Bearer ' + token;
  let payload;
  if (body !== undefined) { payload = typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body); }
  const r = await fetch(base + url, { method, headers: h, body: payload });
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json() : ct.includes('text') ? await r.text() : Buffer.from(await r.arrayBuffer());
  return { status: r.status, data, headers: r.headers };
}
let phoneN = 0;
async function register(name) {
  const phone = '09' + String(10000000 + (++phoneN) * 7).slice(0, 8);
  const r = await api('POST', '/v1/auth/register', { body: { phone, name, password: 'matkhau-12345' } });
  assert.equal(r.status, 200, JSON.stringify(r.data));
  return { token: r.data.token, id: r.data.user.id, phone, name };
}
const tree = (id, zone, extra = {}) => ({ id, code: id.toUpperCase(), zone, active: true, version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1, sync: 'LOCAL', ...extra });
const ev = (id, treeIds, extra = {}) => ({ id, treeIds, type: 'CARE', activity: 'Tưới', occurredAt: Date.UTC(2026, 0, 5), recordedAt: 1, author: 'Bí mật', note: 'ghi chú', photos: [], version: 1, baseVersion: 0, opId: 'op-' + id, updatedAt: 1, ...extra });
const snap = (o = {}) => JSON.stringify({ garden: { name: 'Vườn A', updatedAt: 10 }, zones: [{ name: 'A', desc: '' }, { name: 'B', desc: '' }], trees: [], events: [], issues: [], tasks: [], sales: [], ...o });
const put = (t, key, rev, body) => api('PUT', `/v1/gardens/${key}/snapshot`, { token: t, body, headers: { 'If-Match': String(rev) } });
async function newGarden(owner, key) {
  const r = await api('POST', '/v1/gardens', { token: owner.token, body: { key, name: 'Vườn ' + key } });
  assert.equal(r.status, 200, JSON.stringify(r.data));
}
async function join(owner, key, user, inv) {
  const i = await api('POST', `/v1/gardens/${key}/invites`, { token: owner.token, body: inv });
  assert.equal(i.status, 200, JSON.stringify(i.data));
  const a = await api('POST', '/v1/invites/accept', { token: user.token, body: { code: i.data.code } });
  assert.equal(a.status, 200, JSON.stringify(a.data));
  return i.data;
}

test('đăng ký, đăng nhập, sai mật khẩu bị khoá, đăng xuất', async () => {
  const u = await register('Lan');
  assert.equal((await api('POST', '/v1/auth/register', { body: { phone: u.phone, name: 'x', password: 'matkhau-12345' } })).status, 409);
  assert.equal((await api('POST', '/v1/auth/register', { body: { phone: '123', name: 'x', password: 'matkhau-12345' } })).status, 400);
  assert.equal((await api('POST', '/v1/auth/register', { body: { phone: '0987654321', name: 'x', password: 'ngan' } })).status, 400);
  const ok = await api('POST', '/v1/auth/login', { body: { phone: u.phone, password: 'matkhau-12345' } });
  assert.equal(ok.status, 200);
  for (let i = 0; i < 5; i++) assert.equal((await api('POST', '/v1/auth/login', { body: { phone: u.phone, password: 'sai' + i } })).status, 401);
  assert.equal((await api('POST', '/v1/auth/login', { body: { phone: u.phone, password: 'matkhau-12345' } })).status, 429);
  assert.equal((await api('GET', '/v1/me', { token: u.token })).status, 200);
  await api('POST', '/v1/auth/logout', { token: u.token });
  assert.equal((await api('GET', '/v1/me', { token: u.token })).status, 401);
  assert.equal((await api('GET', '/v1/me')).status, 401);
});

test('người lạ không thấy cả sự tồn tại của vườn; chủ tạo vườn và đồng bộ', async () => {
  const owner = await register('Chủ'), stranger = await register('Lạ');
  await newGarden(owner, 'vuon-a');
  assert.equal((await api('POST', '/v1/gardens', { token: stranger.token, body: { key: 'vuon-a' } })).status, 409);
  assert.equal((await api('GET', '/v1/gardens/vuon-a/snapshot', { token: stranger.token })).status, 404);
  assert.equal((await api('GET', '/v1/gardens/khong-co/snapshot', { token: stranger.token })).status, 404);
  assert.equal((await api('GET', '/v1/gardens/vuon-a/snapshot', { token: owner.token })).status, 404);   // chưa có dữ liệu
  let r = await put(owner.token, 'vuon-a', 0, snap({ trees: [tree('t1', 'A')] }));
  assert.equal(r.status, 200); assert.equal(r.data.rev, 1); assert.equal(r.data.accepted >= 2, true);
  r = await put(owner.token, 'vuon-a', 0, snap());
  assert.equal(r.status, 409); assert.equal(r.data.rev, 1);
  const me = await api('GET', '/v1/me', { token: owner.token });
  assert.deepEqual(me.data.gardens.map(g => [g.key, g.role]), [['vuon-a', 'owner']]);
});

test('RBAC phía máy chủ: nhân viên không sửa được thông tin vườn, doanh thu, không xoá, không đóng vấn đề', async () => {
  const owner = await register('Chủ'), worker = await register('Công');
  await newGarden(owner, 'vuon-rbac');
  const sale = { id: 's1', date: 1, kg: 10, pricePerKg: 5000, version: 1, baseVersion: 0, opId: 's1', updatedAt: 1 };
  const issue = { id: 'i1', number: 1, treeIds: ['t1'], symptom: 'Vàng lá', status: 'IN_PROGRESS', version: 1, baseVersion: 0, opId: 'i1', updatedAt: 1 };
  await put(owner.token, 'vuon-rbac', 0, snap({ garden: { name: 'Vườn A', updatedAt: 10, pricePerKg: 90000 }, trees: [tree('t1', 'A')], sales: [sale], issues: [issue] }));
  await join(owner, 'vuon-rbac', worker, { role: 'worker' });
  const g = await api('GET', '/v1/gardens/vuon-rbac/snapshot', { token: worker.token });
  assert.equal(g.status, 200); assert.equal(g.data.role, 'worker');
  assert.deepEqual(g.data.data.sales, []);                      // doanh thu ẩn với nhân viên
  assert.equal(g.data.data.garden.pricePerKg, undefined);       // giá bán ẩn
  assert.equal(g.data.data.trees.length, 1);
  // nhân viên thử: sửa vườn, thêm doanh thu, đóng vấn đề, xoá cây, thêm nhật ký
  const body = JSON.stringify({
    garden: { name: 'Hacked', updatedAt: 999 }, zones: [{ name: 'A', desc: '' }],
    trees: [g.data.data.trees[0]], sales: [{ ...sale, id: 's2', opId: 's2' }],
    issues: [{ ...issue, status: 'CONFIRMED', version: 2, baseVersion: 1, opId: 'i1b' }],
    events: [ev('e1', ['t1'])],
    tombstones: [{ type: 'trees', id: 't1', at: 5, by: 'Công' }],
  });
  const r = await put(worker.token, 'vuon-rbac', 1, body);
  assert.equal(r.status, 200);
  const why = Object.fromEntries(r.data.rejected.map(x => [x.collection, x.reason]));
  assert.ok(why.garden && why.sales && why.issues && why.trees, JSON.stringify(r.data.rejected));
  assert.equal(r.data.accepted, 1);                                       // chỉ nhật ký được nhận
  const after = (await api('GET', '/v1/gardens/vuon-rbac/snapshot', { token: owner.token })).data.data;
  assert.equal(after.garden.name, 'Vườn A'); assert.equal(after.sales.length, 1);
  assert.equal(after.issues[0].status, 'IN_PROGRESS'); assert.equal(after.trees.length, 1); assert.equal(after.events.length, 1);
  // thông tin bị từ chối được ghi vào nhật ký kiểm toán
  const log = (await api('GET', '/v1/gardens/vuon-rbac/audit', { token: owner.token })).data.entries;
  assert.ok(log.some(l => l.action === 'snapshot.put' && l.detail.rejectedCount >= 4 && l.by === 'Công'));
  assert.equal((await api('GET', '/v1/gardens/vuon-rbac/audit', { token: worker.token })).status, 403);
  assert.equal((await api('GET', '/v1/gardens/vuon-rbac/members', { token: worker.token })).status, 403);
  assert.equal((await api('POST', '/v1/gardens/vuon-rbac/invites', { token: worker.token, body: { role: 'worker' } })).status, 403);
  assert.equal((await api('DELETE', '/v1/gardens/vuon-rbac', { token: worker.token, headers: { 'X-Confirm': 'vuon-rbac' } })).status, 403);
});

test('phạm vi theo khu: phi công chỉ thấy và chỉ sửa trong khu được cấp; hết hạn là mất quyền', async () => {
  const owner = await register('Chủ'), pilot = await register('Phi');
  await newGarden(owner, 'vuon-scope');
  await put(owner.token, 'vuon-scope', 0, snap({ trees: [tree('ta', 'A'), tree('tb', 'B')], events: [ev('ea', ['ta']), ev('eb', ['tb'])] }));
  assert.equal((await api('POST', '/v1/gardens/vuon-scope/invites', { token: owner.token, body: { role: 'pilot', scope: { zones: ['A'] } } })).status, 400);   // phi công phải có thời hạn
  await join(owner, 'vuon-scope', pilot, { role: 'pilot', scope: { zones: ['A'] }, memberDays: 3 });
  const g = (await api('GET', '/v1/gardens/vuon-scope/snapshot', { token: pilot.token })).data.data;
  assert.deepEqual(g.trees.map(t => t.id), ['ta']); assert.deepEqual(g.events.map(e => e.id), ['ea']); assert.deepEqual(g.zones.map(z => z.name), ['A']);
  const r = await put(pilot.token, 'vuon-scope', 1, JSON.stringify({ trees: [], events: [ev('ea2', ['ta']), ev('eb2', ['tb'])] }));
  assert.equal(r.data.accepted, 1); assert.equal(r.data.rejected.length, 1); assert.equal(r.data.rejected[0].id, 'eb2');
  const all = (await api('GET', '/v1/gardens/vuon-scope/snapshot', { token: owner.token })).data.data;
  assert.equal(all.events.length, 3);                       // sự kiện khu B của chủ không bị mất dù phi công không thấy
  assert.equal(all.trees.length, 2);
  // hết hạn: sửa ngày hết hạn về quá khứ
  const members = JSON.parse(fs.readFileSync(path.join(process.env.DATA_DIR, 'vuon-scope', 'members.json')));
  members.find(m => m.userId === pilot.id).expiresAt = Date.now() - 1000;
  fs.writeFileSync(path.join(process.env.DATA_DIR, 'vuon-scope', 'members.json'), JSON.stringify(members));
  assert.equal((await api('GET', '/v1/gardens/vuon-scope/snapshot', { token: pilot.token })).status, 404);
});

test('lời mời: dùng một lần, ràng buộc SĐT, thu hồi lời mời và thu hồi thành viên có hiệu lực ngay', async () => {
  const owner = await register('Chủ'), a = await register('A'), b = await register('B'), c = await register('C');
  await newGarden(owner, 'vuon-inv');
  await put(owner.token, 'vuon-inv', 0, snap());
  const inv = await api('POST', '/v1/gardens/vuon-inv/invites', { token: owner.token, body: { role: 'worker', phone: a.phone } });
  assert.match(inv.data.uri, /^vwing:\/\/invite\/[A-Z2-7]{10}$/);
  assert.equal((await api('POST', '/v1/invites/accept', { token: b.token, body: { code: inv.data.code } })).status, 403);   // sai SĐT
  assert.equal((await api('POST', '/v1/invites/accept', { token: a.token, body: { code: inv.data.code } })).status, 200);
  assert.equal((await api('POST', '/v1/invites/accept', { token: a.token, body: { code: inv.data.code } })).status, 410);   // đã dùng
  const inv2 = await api('POST', '/v1/gardens/vuon-inv/invites', { token: owner.token, body: { role: 'agronomist' } });
  await api('DELETE', `/v1/gardens/vuon-inv/invites/${inv2.data.id}`, { token: owner.token });
  assert.equal((await api('POST', '/v1/invites/accept', { token: c.token, body: { code: inv2.data.code } })).status, 410);
  assert.equal((await api('POST', '/v1/invites/accept', { token: c.token, body: { code: 'AAAAAAAAAA' } })).status, 404);
  assert.equal((await api('GET', '/v1/gardens/vuon-inv/snapshot', { token: a.token })).status, 200);
  const rv = await api('DELETE', `/v1/gardens/vuon-inv/members/${a.id}`, { token: owner.token });
  assert.equal(rv.status, 200);
  assert.equal((await api('GET', '/v1/gardens/vuon-inv/snapshot', { token: a.token })).status, 404);
  const actions = (await api('GET', '/v1/gardens/vuon-inv/audit', { token: owner.token })).data.entries.map(e => e.action);
  for (const x of ['garden.create', 'member.invite', 'member.join', 'member.revoke', 'invite.revoke']) assert.ok(actions.includes(x), x);
  // quản lý không được nâng quyền hoặc đụng vào chủ
  const mgr = await register('Quản lý');
  await join(owner, 'vuon-inv', mgr, { role: 'manager' });
  assert.equal((await api('POST', '/v1/gardens/vuon-inv/invites', { token: mgr.token, body: { role: 'manager' } })).status, 403);
  assert.equal((await api('POST', '/v1/gardens/vuon-inv/invites', { token: mgr.token, body: { role: 'worker' } })).status, 200);
  assert.equal((await api('DELETE', `/v1/gardens/vuon-inv/members/${owner.id}`, { token: mgr.token })).status, 403);
  assert.equal((await api('PATCH', `/v1/gardens/vuon-inv/members/${mgr.id}`, { token: mgr.token, body: { role: 'worker' } })).status, 403);
});

test('xoá lan truyền: xoá cây → nhật ký/vấn đề/công việc liên quan; tombstone chặn thiết bị cũ đưa lại', async () => {
  const owner = await register('Chủ');
  await newGarden(owner, 'vuon-del');
  const issue = { id: 'i1', number: 1, treeIds: ['t1'], symptom: 'x', status: 'NEW', originEventId: 'e1', version: 1, baseVersion: 0, opId: 'i1', updatedAt: 1 };
  const task = { id: 'k1', title: 'Xử lý', treeIds: [], issueId: 'i1', version: 1, baseVersion: 0, opId: 'k1', updatedAt: 1 };
  await put(owner.token, 'vuon-del', 0, snap({
    trees: [tree('t1', 'A'), tree('t2', 'A')],
    events: [ev('e1', ['t1']), ev('e2', ['t1', 't2']), ev('e3', ['t2'])],
    issues: [issue], tasks: [task],
  }));
  const r = await put(owner.token, 'vuon-del', 1, JSON.stringify({ trees: [], tombstones: [{ type: 'trees', id: 't1', at: Date.now(), by: 'Chủ' }] }));
  assert.equal(r.status, 200);
  const s = (await api('GET', '/v1/gardens/vuon-del/snapshot', { token: owner.token })).data.data;
  assert.deepEqual(s.trees.map(t => t.id), ['t2']);
  assert.deepEqual(s.events.map(e => e.id).sort(), ['e2', 'e3']);          // e1 chỉ gắn t1 → xoá; e2 còn t2
  assert.deepEqual(s.events.find(e => e.id === 'e2').treeIds, ['t2']);
  assert.equal(s.events.find(e => e.id === 'e2').version, 2);              // bản bỏ liên kết được tăng phiên bản để thiết bị khác nhận
  assert.equal(s.issues.length, 0); assert.equal(s.tasks.length, 0);       // vấn đề → công việc của vấn đề
  const types = s.tombstones.map(t => t.type + ':' + t.id).sort();
  assert.deepEqual(types, ['events:e1', 'issues:i1', 'tasks:k1', 'trees:t1']);
  // thiết bị cũ chưa biết việc xoá cố đưa cây và nhật ký trở lại
  const r2 = await put(owner.token, 'vuon-del', 2, JSON.stringify({ trees: [tree('t1', 'A')], events: [ev('e1', ['t1']), ev('e4', ['t1'])] }));
  assert.equal(r2.data.accepted, 0); assert.equal(r2.data.rejected.length, 1);   // e4: cây đã bị xoá
  assert.equal((await api('GET', '/v1/gardens/vuon-del/snapshot', { token: owner.token })).data.data.trees.length, 1);
});

test('xoá lan truyền: xoá khu, và xoá vườn dọn sạch ảnh/thành viên', async () => {
  const owner = await register('Chủ'), w = await register('W');
  await newGarden(owner, 'vuon-zone');
  await put(owner.token, 'vuon-zone', 0, snap({ trees: [tree('ta', 'A'), tree('tb', 'B')], events: [ev('ea', ['ta'])] }));
  await api('PUT', '/v1/gardens/vuon-zone/photos/p1.jpg', { token: owner.token, body: Buffer.from([1, 2, 3]) });
  await put(owner.token, 'vuon-zone', 1, JSON.stringify({ trees: [], tombstones: [{ type: 'zones', id: 'A', at: 1, by: 'x' }] }));
  const s = (await api('GET', '/v1/gardens/vuon-zone/snapshot', { token: owner.token })).data.data;
  assert.deepEqual(s.trees.map(t => t.id), ['tb']); assert.equal(s.events.length, 0); assert.deepEqual(s.zones.map(z => z.name), ['B']);
  await join(owner, 'vuon-zone', w, { role: 'worker' });
  assert.equal((await api('DELETE', '/v1/gardens/vuon-zone', { token: owner.token })).status, 400);   // thiếu xác nhận
  assert.equal((await api('DELETE', '/v1/gardens/vuon-zone', { token: owner.token, headers: { 'X-Confirm': 'vuon-zone' } })).status, 200);
  assert.equal((await api('GET', '/v1/gardens/vuon-zone/snapshot', { token: owner.token })).status, 404);
  assert.equal((await api('GET', '/v1/gardens/vuon-zone/snapshot', { token: w.token })).status, 404);
  assert.deepEqual((await api('GET', '/v1/me', { token: owner.token })).data.gardens, []);
  assert.equal(fs.existsSync(path.join(process.env.DATA_DIR, 'vuon-zone')), false);
});

test('ảnh mồ côi bị dọn khi nhật ký chứa ảnh bị xoá', async () => {
  const owner = await register('Chủ');
  await newGarden(owner, 'vuon-ph');
  const e = ev('e1', ['t1'], { photos: [{ path: '/data/x/abc.jpg', uploadedAt: 1 }] });
  await put(owner.token, 'vuon-ph', 0, snap({ trees: [tree('t1', 'A')], events: [e] }));
  await api('PUT', '/v1/gardens/vuon-ph/photos/abc.jpg', { token: owner.token, body: Buffer.from([1, 2, 3, 4]) });
  assert.deepEqual((await api('GET', '/v1/gardens/vuon-ph/photos', { token: owner.token })).data.names, ['abc.jpg']);
  await put(owner.token, 'vuon-ph', 1, JSON.stringify({ trees: [], tombstones: [{ type: 'events', id: 'e1', at: 1, by: 'x' }] }));
  assert.deepEqual((await api('GET', '/v1/gardens/vuon-ph/photos', { token: owner.token })).data.names, []);
});

test('đồng bộ thông tin vườn: bản mới hơn thắng, bản cũ bị bỏ qua', async () => {
  const owner = await register('Chủ');
  await newGarden(owner, 'vuon-info');
  await put(owner.token, 'vuon-info', 0, snap({ garden: { name: 'V1', updatedAt: 10 } }));
  let r = await put(owner.token, 'vuon-info', 1, snap({ garden: { name: 'Cũ hơn', updatedAt: 5 } }));
  assert.equal(r.data.stale, 1);
  r = await put(owner.token, 'vuon-info', 2, snap({ garden: { name: 'V2', updatedAt: 20 } }));
  assert.equal(r.data.accepted, 1);
  assert.equal((await api('GET', '/v1/gardens/vuon-info/snapshot', { token: owner.token })).data.data.garden.name, 'V2');
});

test('trang công khai R6: tắt mặc định, chỉ hiện trường chủ chọn, không lộ dữ liệu riêng', async () => {
  const owner = await register('Chủ'), w = await register('W');
  await newGarden(owner, 'vuon-pub');
  const e = ev('e1', ['t1'], { type: 'HARVEST', activity: 'Thu hoạch <b>đợt 1</b>', amountKg: 12.5, product: 'Phân X', dosage: '2kg', photos: [{ path: '/d/p.jpg' }] });
  await put(owner.token, 'vuon-pub', 0, snap({
    garden: { name: 'Vườn Sầu', location: 'Đắk Lắk', updatedAt: 1, pricePerKg: 90000 },
    trees: [tree('t1', 'A', { variety: 'Ri6', pos: { lat: 12.123456, lng: 108.123456 } }), tree('t2', 'A', { active: false })],
    events: [e], sales: [{ id: 's', kg: 1, pricePerKg: 1 }], issues: [{ id: 'i', symptom: 'BÍ MẬT' }],
  }));
  await api('PUT', '/v1/gardens/vuon-pub/photos/p.jpg', { token: owner.token, body: Buffer.from([255, 216, 255]) });
  assert.equal((await api('GET', '/p/vuon-pub/t1')).status, 404);                          // chưa bật
  assert.equal((await api('PUT', '/v1/gardens/vuon-pub/public', { token: w.token, body: { enabled: true, fields: [] } })).status, 404);
  assert.equal((await api('PUT', '/v1/gardens/vuon-pub/public', { token: owner.token, body: { enabled: true, fields: ['author'] } })).status, 400);
  const cfg = await api('PUT', '/v1/gardens/vuon-pub/public', { token: owner.token, body: { enabled: true, fields: ['garden.name', 'tree.code', 'tree.position', 'timeline.activities', 'timeline.harvest'], story: 'Sầu riêng sạch' } });
  assert.equal(cfg.status, 200);
  const page = await api('GET', '/p/vuon-pub/t1');
  assert.equal(page.status, 200); assert.match(page.headers.get('content-security-policy'), /default-src 'none'/);
  assert.ok(page.data.includes('Vườn Sầu') && page.data.includes('T1') && page.data.includes('12.5 kg') && page.data.includes('Sầu riêng sạch'));
  assert.ok(page.data.includes('Thu hoạch &lt;b&gt;đợt 1&lt;/b&gt;'));                      // thoát HTML
  for (const secret of ['Đắk Lắk', 'Ri6', 'Phân X', 'ghi chú', 'Bí mật', 'BÍ MẬT', '90000', '/photo/']) assert.ok(!page.data.includes(secret), secret);
  const json = (await api('GET', '/v1/public/vuon-pub/trees/t1')).data;
  assert.deepEqual(json.tree.position, { lat: 12.1235, lng: 108.1235 });                    // làm tròn vị trí
  assert.equal(json.garden.location, undefined);
  assert.equal((await api('GET', '/p/vuon-pub/t2')).status, 404);                           // cây đã ngừng
  assert.equal((await api('GET', '/p/vuon-pub/photo/p.jpg')).status, 404);                  // chưa chọn ảnh
  await api('PUT', '/v1/gardens/vuon-pub/public', { token: owner.token, body: { enabled: true, fields: ['timeline.photos', 'timeline.products', 'timeline.notes'] } });
  const p2 = await api('GET', '/p/vuon-pub/t1');
  assert.ok(p2.data.includes('/p/vuon-pub/photo/p.jpg') && p2.data.includes('Phân X') && p2.data.includes('ghi chú'));
  assert.equal((await api('GET', '/p/vuon-pub/photo/p.jpg')).status, 200);
  assert.equal((await api('GET', '/p/vuon-pub/photo/..%2Fmeta.json')).status, 404);
  await api('PUT', '/v1/gardens/vuon-pub/public', { token: owner.token, body: { enabled: false, fields: [] } });
  assert.equal((await api('GET', '/p/vuon-pub/t1')).status, 404);                           // tắt là biến mất ngay
});

test('mã quản trị: cấp chủ cho vườn cũ; ảnh chặn tên nguy hiểm', async () => {
  const owner = await register('Chủ');
  assert.equal((await put(ADMIN, 'vuon-cu', 0, snap({ trees: [tree('t1', 'A')] }))).status, 200);   // vườn tạo trước khi có tài khoản
  assert.equal((await api('GET', '/v1/gardens/vuon-cu/snapshot', { token: owner.token })).status, 404);
  assert.equal((await api('PUT', '/v1/admin/gardens/vuon-cu/owner', { token: owner.token, body: { phone: owner.phone } })).status, 403);
  assert.equal((await api('PUT', '/v1/admin/gardens/vuon-cu/owner', { token: ADMIN, body: { phone: owner.phone } })).status, 200);
  assert.equal((await api('GET', '/v1/gardens/vuon-cu/snapshot', { token: owner.token })).status, 200);
  assert.equal((await api('PUT', '/v1/gardens/vuon-cu/photos/..%2F..%2Fmeta.json', { token: owner.token, body: Buffer.from([1]) })).status, 400);
  assert.equal((await api('GET', '/v1/gardens/..%2Fx/snapshot', { token: owner.token })).status, 404);
});

test('chia sẻ vị trí trực tiếp: phải tự bật, có hẹn giờ, chỉ chủ/quản lý xem người khác, tắt là xoá', async () => {
  const owner = await register('Chủ'), farmer = await register('Nông dân'), other = await register('Công');
  await newGarden(owner, 'vuon-live');
  await join(owner, 'vuon-live', farmer, { role: 'worker' });
  await join(owner, 'vuon-live', other, { role: 'worker' });
  const pos = { lat: 12.6801, lng: 108.0382, acc: 4.5 };
  const L = (t, m, p, b) => api(m, `/v1/gardens/vuon-live/live${p}`, { token: t.token, body: b });
  assert.equal((await L(farmer, 'POST', '/me/position', pos)).status, 409);          // chưa bật thì không nhận vị trí
  assert.equal((await L(farmer, 'PUT', '/me', { minutes: 0 })).status, 400);
  assert.equal((await L(farmer, 'PUT', '/me', { minutes: 15 })).status, 200);
  assert.equal((await L(farmer, 'POST', '/me/position', { lat: 91, lng: 0 })).status, 400);
  assert.equal((await L(farmer, 'POST', '/me/position', pos)).status, 200);
  const seen = (await L(owner, 'GET', '')).data.people;
  assert.equal(seen.length, 1); assert.equal(seen[0].name, 'Nông dân'); assert.equal(seen[0].acc, 4.5);
  assert.equal((await L(other, 'GET', '')).data.people.length, 0);                    // nhân viên khác không thấy
  assert.equal((await L(farmer, 'GET', '')).data.people.length, 1);                   // tự thấy mình
  assert.equal((await api('PUT', '/v1/gardens/vuon-live/live/me', { token: ADMIN, body: { minutes: 5 } })).status, 403);
  await L(farmer, 'DELETE', '/me');
  assert.equal((await L(owner, 'GET', '')).data.people.length, 0);
  assert.equal((await L(farmer, 'POST', '/me/position', pos)).status, 409);
  const log = JSON.stringify((await api('GET', '/v1/gardens/vuon-live/audit', { token: owner.token })).data.entries);
  assert.ok(log.includes('live.start') && log.includes('live.stop') && !log.includes('12.68'));   // nhật ký không chứa toạ độ
  // bị thu hồi thì không còn gửi được
  await L(farmer, 'PUT', '/me', { minutes: 15 });
  await L(farmer, 'POST', '/me/position', pos);
  assert.equal((await L(owner, 'GET', '')).data.people.length, 1);
  await api('DELETE', `/v1/gardens/vuon-live/members/${farmer.id}`, { token: owner.token });
  assert.equal((await L(farmer, 'POST', '/me/position', pos)).status, 404);
  assert.equal((await L(owner, 'GET', '')).data.people.length, 0);          // bị thu hồi thì vị trí cuối cũng biến mất   // vị trí đã gửi vẫn hết hạn theo giờ, nhưng người đã bị thu hồi không gửi thêm được
});

test('chuyển quyền chủ: phải đúng mật khẩu, người nhận là thành viên; chủ cũ thành quản lý', async () => {
  const owner = await register('Chủ'), mgr = await register('Quản'), stranger = await register('Lạ');
  await newGarden(owner, 'vuon-tr');
  await put(owner.token, 'vuon-tr', 0, snap());
  await join(owner, 'vuon-tr', mgr, { role: 'manager' });
  const T = (t, b) => api('POST', '/v1/gardens/vuon-tr/transfer', { token: t.token, body: b });
  assert.equal((await T(mgr, { userId: owner.id, password: 'matkhau-12345' })).status, 403);              // chỉ chủ
  assert.equal((await T(owner, { userId: mgr.id, password: 'sai-mat-khau' })).status, 403);               // sai mật khẩu
  assert.equal((await T(owner, { userId: stranger.id, password: 'matkhau-12345' })).status, 404);         // không phải thành viên
  assert.equal((await T(owner, { userId: owner.id, password: 'matkhau-12345' })).status, 404);
  assert.equal((await T(owner, { userId: mgr.id, password: 'matkhau-12345' })).status, 200);
  const me = async u => (await api('GET', '/v1/me', { token: u.token })).data.gardens.find(g => g.key === 'vuon-tr').role;
  assert.equal(await me(mgr), 'owner'); assert.equal(await me(owner), 'manager');
  assert.equal((await api('GET', '/v1/gardens/vuon-tr/audit', { token: mgr.token })).status, 200);       // chủ mới xem được kiểm toán
  assert.equal((await api('GET', '/v1/gardens/vuon-tr/audit', { token: owner.token })).status, 403);     // chủ cũ không còn
  assert.equal((await api('PUT', '/v1/gardens/vuon-tr/public', { token: owner.token, body: { enabled: true, fields: [] } })).status, 403);
  assert.ok(JSON.stringify((await api('GET', '/v1/gardens/vuon-tr/audit', { token: mgr.token })).data.entries).includes('owner.transfer'));
});

test('đơn việc mở: người nhận việc tự nhận, người thứ hai bị từ chối, trả lại được, không sửa nội dung', async () => {
  const owner = await register('Chủ'), p1 = await register('Phi 1'), p2 = await register('Phi 2');
  await newGarden(owner, 'vuon-job');
  const job = { id: 'k1', title: 'Phun thuốc khu A', treeIds: [], scopeZone: 'A', assignee: '', open: true, done: false, version: 1, baseVersion: 0, opId: 'k1', updatedAt: 1 };
  await put(owner.token, 'vuon-job', 0, snap({ tasks: [job], trees: [tree('t1', 'A')] }));
  await join(owner, 'vuon-job', p1, { role: 'pilot', memberDays: 5 });
  await join(owner, 'vuon-job', p2, { role: 'pilot', memberDays: 5 });
  const claim = (u, over = {}) => ({ tasks: [{ ...job, assignee: u.name, open: false, version: 2, baseVersion: 1, opId: 'c-' + u.id, ...over }] });
  let rev = 1;
  const send = async (u, body) => { const r = await put(u.token, 'vuon-job', rev, JSON.stringify({ trees: [], ...body })); if (r.status === 200) rev = r.data.rev; return r; };
  // sửa tên việc khi "nhận" bị từ chối
  let r = await send(p1, claim(p1, { title: 'Việc khác' }));
  assert.equal(r.data.accepted, 0); assert.equal(r.data.rejected.length, 1);
  r = await send(p1, claim(p1));
  assert.equal(r.data.accepted, 1);
  let t = (await api('GET', '/v1/gardens/vuon-job/snapshot', { token: owner.token })).data.data.tasks[0];
  assert.equal(t.assignee, 'Phi 1'); assert.equal(t.open, false);
  // người thứ hai nhận cùng việc → từ chối
  r = await send(p2, claim(p2, { version: 3, baseVersion: 2 }));
  assert.equal(r.data.accepted, 0); assert.match(r.data.rejected[0].reason, /đã có người khác nhận/);
  // người đầu trả lại việc, người hai nhận được
  r = await send(p1, { tasks: [{ ...job, assignee: '', open: true, version: 3, baseVersion: 2, opId: 'rel' }] });
  assert.equal(r.data.accepted, 1);
  r = await send(p2, claim(p2, { version: 4, baseVersion: 3, opId: 'c2' }));
  assert.equal(r.data.accepted, 1);
  t = (await api('GET', '/v1/gardens/vuon-job/snapshot', { token: owner.token })).data.data.tasks[0];
  assert.equal(t.assignee, 'Phi 2');
  // người đã nhận hoàn tất được
  r = await send(p2, { tasks: [{ ...job, assignee: 'Phi 2', open: false, done: true, doneAt: 5, doneBy: 'Phi 2', version: 5, baseVersion: 4, opId: 'd' }] });
  assert.equal(r.data.accepted, 1);
});
