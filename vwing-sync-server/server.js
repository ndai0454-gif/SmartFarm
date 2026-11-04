// Máy chủ VWing (không cần thư viện ngoài): tài khoản, phân quyền theo vườn (RBAC), mời thành viên, nhật ký kiểm toán,
// đồng bộ bản chụp có kiểm soát phiên bản, xoá lan truyền và trang truy xuất công khai cho người mua (R6).
// Mọi kiểm tra quyền nằm ở ĐÂY; giao diện app chỉ là tiện lợi. Việc hợp nhất xung đột vẫn do app làm (xem Sync.kt).
//
// Chạy:  VWING_TOKEN=chuoi-bi-mat node server.js        (PORT mặc định 8790, DATA_DIR mặc định ./data)
//   VWING_TOKEN là mã quản trị hệ thống (R7): dùng để cấp chủ cho vườn cũ và hỗ trợ; không dùng cho thiết bị thường ngày.
//   PUBLIC_BASE_URL (tuỳ chọn) đặt địa chỉ công khai ghi vào mã QR, ví dụ https://vwing.example.com
// Chỉ dùng HTTP trong mạng nội bộ tin cậy, hoặc đặt sau HTTPS (reverse proxy/tunnel) khi ra Internet.

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const rbac = require('./rbac');
const pub = require('./public');
const otp = require('./otp');
const { SupabaseInfraBridge } = require('./supabase-bridge');

const PORT = Number(process.env.PORT || 8790);
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));
const ADMIN_TOKEN = process.env.VWING_TOKEN || '';
const MAX_SNAPSHOT = 20 * 1024 * 1024;
const MAX_PHOTO = 30 * 1024 * 1024;
const SESSION_MS = 30 * 24 * 3600 * 1000;
const INVITE_MS = 7 * 24 * 3600 * 1000;

if (!ADMIN_TOKEN || ADMIN_TOKEN.length < 12) {
  console.error('Cần đặt VWING_TOKEN (tối thiểu 12 ký tự). Ví dụ: VWING_TOKEN=' + crypto.randomBytes(12).toString('hex') + ' node server.js');
  process.exit(1);
}
fs.mkdirSync(DATA_DIR, { recursive: true });
const infra = new SupabaseInfraBridge(DATA_DIR);

const KEY_RE = /^[A-Za-z0-9_-]{3,64}$/;
const PHOTO_RE = /^[A-Za-z0-9._-]{1,100}$/;
const ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
const CODE_RE = /^[A-Z2-7]{10}$/;

// ---------- lưu trữ ----------
function writeAtomic(file, data) {
  const tmp = file + '.' + process.pid + '.tmp';
  fs.writeFileSync(tmp, data);
  fs.renameSync(tmp, file);
}
function readJson(file, fallback) { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; } }
const writeJson = (file, v) => writeAtomic(file, JSON.stringify(v));

const ACCOUNTS = path.join(DATA_DIR, 'accounts.json');
let accounts = readJson(ACCOUNTS, { users: [], sessions: [] });
const saveAccounts = () => writeJson(ACCOUNTS, accounts);

const gardenDir = key => path.join(DATA_DIR, key);
const readMeta = key => readJson(path.join(gardenDir(key), 'meta.json'), null);
const writeMeta = (key, m) => writeJson(path.join(gardenDir(key), 'meta.json'), m);
const readMembers = key => readJson(path.join(gardenDir(key), 'members.json'), []);
const writeMembers = (key, v) => writeJson(path.join(gardenDir(key), 'members.json'), v);
const readInvites = key => readJson(path.join(gardenDir(key), 'invites.json'), []);
const writeInvites = (key, v) => writeJson(path.join(gardenDir(key), 'invites.json'), v);
const readSnapshot = key => readJson(path.join(gardenDir(key), 'snapshot.json'), null);

function audit(key, actor, action, detail) {
  const line = JSON.stringify({ at: Date.now(), by: actor.name || actor.kind, userId: actor.id || null, role: actor.role || actor.kind, action, detail: detail || {} });
  fs.mkdirSync(gardenDir(key), { recursive: true });
  fs.appendFileSync(path.join(gardenDir(key), 'audit.jsonl'), line + '\n');
}
function readAudit(key, limit) {
  try {
    const lines = fs.readFileSync(path.join(gardenDir(key), 'audit.jsonl'), 'utf8').split('\n').filter(Boolean);
    return lines.slice(-limit).reverse().map(l => JSON.parse(l));
  } catch { return []; }
}

// ---------- tài khoản ----------
const sha256 = s => crypto.createHash('sha256').update(s).digest('hex');
function hashPassword(pw, salt = crypto.randomBytes(16).toString('hex')) {
  return { salt, hash: crypto.scryptSync(pw, salt, 64).toString('hex') };
}
function checkPassword(user, pw) {
  const h = crypto.scryptSync(pw, user.salt, 64);
  const want = Buffer.from(user.hash, 'hex');
  return h.length === want.length && crypto.timingSafeEqual(h, want);
}
function normPhone(p) {
  let d = String(p || '').replace(/\D/g, '');
  if (d.startsWith('84') && (d.length === 11 || d.length === 12)) d = '0' + d.slice(2);
  return /^0\d{9,10}$/.test(d) ? d : null;
}
const publicUser = u => ({ id: u.id, phone: u.phone, name: u.name });

const failures = new Map();       // chống dò mật khẩu: khoá theo SĐT và IP
function throttled(k) { const f = failures.get(k); return f && f.n >= 5 && Date.now() - f.at < 15 * 60 * 1000; }
function noteFail(k) { const f = failures.get(k); failures.set(k, { n: (f && Date.now() - f.at < 15 * 60 * 1000 ? f.n : 0) + 1, at: Date.now() }); }

function newSession(user) {
  const token = crypto.randomBytes(32).toString('hex');
  const now = Date.now();
  accounts.sessions = accounts.sessions.filter(s => s.exp > now);
  accounts.sessions.push({ h: sha256(token), userId: user.id, exp: now + SESSION_MS });
  saveAccounts();
  return token;
}

/** Danh tính của yêu cầu: quản trị (mã hệ thống) hoặc người dùng đăng nhập. */
function identify(req) {
  const h = req.headers['authorization'] || '';
  const tok = h.startsWith('Bearer ') ? h.slice(7) : '';
  if (!tok) return null;
  const a = Buffer.from(tok), b = Buffer.from(ADMIN_TOKEN);
  if (a.length === b.length && crypto.timingSafeEqual(a, b)) return { kind: 'admin', name: 'Quản trị hệ thống' };
  const hs = sha256(tok);
  const s = accounts.sessions.find(x => x.h === hs && x.exp > Date.now());
  if (!s) return null;
  const u = accounts.users.find(x => x.id === s.userId);
  return u ? { kind: 'user', id: u.id, name: u.name, phone: u.phone, user: u, tokenHash: hs } : null;
}

/** Quyền của danh tính trên một vườn → {role, scope, name, ...} hoặc null. Quản trị có quyền như chủ vườn. */
function memberOf(key, who) {
  if (who.kind === 'admin') return { role: 'owner', scope: null, name: who.name, admin: true };
  const m = readMembers(key).find(x => x.userId === who.id);
  if (!rbac.activeMember(m)) return null;
  return { role: m.role, scope: m.scope || null, name: who.name, id: who.id, expiresAt: m.expiresAt || null };
}

// ---------- HTTP ----------
function send(res, code, body, type, extra) {
  const isBuf = Buffer.isBuffer(body), isStr = typeof body === 'string';
  res.writeHead(code, { 'Content-Type': type || (isBuf ? 'application/octet-stream' : 'application/json; charset=utf-8'), 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type, Authorization, If-Match, X-Confirm', ...(extra || {}) });
  res.end(isBuf || isStr ? body : JSON.stringify(body));
}
function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0;
    req.on('data', c => { size += c.length; if (size > limit) { reject(Object.assign(new Error('too large'), { status: 413 })); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}
async function readJsonBody(req, limit = 256 * 1024) {
  const b = await readBody(req, limit);
  try { const v = JSON.parse(b.toString('utf8') || '{}'); return v && typeof v === 'object' ? v : {}; }
  catch { throw Object.assign(new Error('bad json'), { status: 400, msg: 'Dữ liệu không phải JSON hợp lệ.' }); }
}
const fail = (status, msg) => Object.assign(new Error(msg), { status, msg });

const live = new Map();           // vườn → (userId → {name, role, until, pos}); mất khi tắt máy chủ, chủ ý không lưu vị trí xuống đĩa

const pubHits = new Map();
function pubLimited(ip) {
  const now = Date.now(), h = (pubHits.get(ip) || []).filter(t => now - t < 60000);
  h.push(now); pubHits.set(ip, h);
  if (pubHits.size > 5000) pubHits.clear();
  return h.length > 60;
}

function baseUrl(req) {
  return (process.env.PUBLIC_BASE_URL || ('http://' + (req.headers.host || 'localhost'))).replace(/\/+$/, '');
}

const randomCode = () => {
  const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; const b = crypto.randomBytes(10);
  return Array.from(b, x => A[x % 32]).join('');
};

function gardenSummary(key, m) {
  const meta = readMeta(key) || {};
  return { key, name: meta.name || key, role: m.role, scope: m.scope || null, expiresAt: m.expiresAt || null };
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    const parts = url.pathname.split('/').filter(Boolean);
    const ip = req.socket.remoteAddress || '';

    // ---- công khai (không cần đăng nhập) ----
    if (req.method === 'GET' && parts[0] === 'p') {
      if (pubLimited(ip)) return send(res, 429, 'Quá nhiều yêu cầu.', 'text/plain; charset=utf-8');
      return pub.handle(parts, res, { readMeta, readSnapshot, gardenDir, KEY_RE, PHOTO_RE, ID_RE, send });
    }
    if (req.method === 'GET' && parts[0] === 'v1' && parts[1] === 'public') {
      if (pubLimited(ip)) return send(res, 429, { error: 'Quá nhiều yêu cầu.' });
      return pub.handleJson(parts, res, { readMeta, readSnapshot, KEY_RE, ID_RE, send });
    }
    if (req.method === 'OPTIONS') return send(res, 204, '');
    if (req.method === 'GET' && url.pathname === '/v1/health') return send(res, 200, { ok: true, otp: otp.enabled, supabaseMode: infra.config.mode, postgis: true, realtimeClients: infra.sseClients.size });
    if (req.method === 'GET' && url.pathname === '/v1/realtime/stream') return infra.handleSseStream(req, res);
    if (parts[0] === 'v1' && parts[1] === 'infra') {
      const localGardensCount = fs.readdirSync(DATA_DIR).filter(k => KEY_RE.test(k) && readMeta(k)).length;
      if (req.method === 'GET' && parts[2] === 'status') return send(res, 200, infra.getStatus(localGardensCount));
      if (req.method === 'POST' && parts[2] === 'config') {
        const b = await readJsonBody(req);
        return send(res, 200, infra.updateConfig(b));
      }
      if (req.method === 'POST' && parts[2] === 'sync-now') {
        return send(res, 200, await infra.flushWalNow());
      }
      if (req.method === 'POST' && parts[2] === 'telemetry') {
        const b = await readJsonBody(req);
        return send(res, 200, { ok: true, telemetry: await infra.recordTelemetry(b.garden_key || 'vuon-mau-tay-nguyen', b) });
      }
      if (req.method === 'POST' && parts[2] === 'trees' && parts[3] === 'alert') {
        const b = await readJsonBody(req);
        return send(res, 200, { ok: true, tree: await infra.updateTreeAlert(b.garden_key || 'vuon-mau-tay-nguyen', String(b.tree_code || 'A-04'), b) });
      }
      if (req.method === 'POST' && parts[2] === 'postgis' && parts[3] === 'metrics') {
        const b = await readJsonBody(req);
        return send(res, 200, { ok: true, metrics: infra.computePostgisMetrics(b.coords) });
      }
      if (req.method === 'GET' && parts[2] === 'migration.sql') {
        const sqlFile = path.resolve(__dirname, '../supabase/migrations/20261102000000_smartfarm_postgis_schema.sql');
        const sql = fs.existsSync(sqlFile) ? fs.readFileSync(sqlFile, 'utf8') : '-- Migration not found';
        return send(res, 200, sql, 'text/plain; charset=utf-8');
      }
    }

    // ---- đăng ký / đăng nhập ----
    if (parts[0] === 'v1' && parts[1] === 'auth' && req.method === 'POST') {
      const b = await readJsonBody(req);
      if (parts[2] === 'register') {
        const phone = normPhone(b.phone), name = String(b.name || '').trim().slice(0, 60), pw = String(b.password || '');
        if (!phone) throw fail(400, 'Số điện thoại không hợp lệ (ví dụ 0912345678).');
        if (!name) throw fail(400, 'Nhập họ tên.');
        if (pw.length < 8) throw fail(400, 'Mật khẩu tối thiểu 8 ký tự.');
        if (accounts.users.some(u => u.phone === phone)) throw fail(409, 'Số điện thoại đã đăng ký.');
        if (otp.enabled && !otp.consume(phone, 'register', b.otp)) throw fail(403, 'Mã xác thực SMS sai hoặc hết hạn. Bấm gửi lại mã.');
        const user = { id: crypto.randomBytes(8).toString('hex'), phone, name, ...hashPassword(pw), createdAt: Date.now(), phoneVerified: otp.enabled };
        accounts.users.push(user); saveAccounts();
        return send(res, 200, { token: newSession(user), user: publicUser(user) });
      }
      if (parts[2] === 'otp' && parts[3] === 'request') {
        if (!otp.enabled) throw fail(404, 'Máy chủ chưa bật xác thực SMS.');
        const phone = normPhone(b.phone), purpose = String(b.purpose || '');
        if (!phone) throw fail(400, 'Số điện thoại không hợp lệ.');
        if (!['register', 'login', 'reset'].includes(purpose)) throw fail(400, 'Mục đích không hợp lệ.');
        const exists = accounts.users.some(u => u.phone === phone);
        if (purpose === 'register' && exists) throw fail(409, 'Số điện thoại đã đăng ký. Hãy đăng nhập.');
        // đăng nhập/đặt lại mật khẩu cho số chưa đăng ký: vẫn trả "đã gửi" nhưng không gửi gì, để không lộ số nào có tài khoản
        if (purpose !== 'register' && !exists) return send(res, 200, { ok: true, resendAfter: otp.RESEND_MS / 1000 });
        return send(res, 200, await otp.request(phone, purpose, ip));
      }
      if (parts[2] === 'otp' && parts[3] === 'login') {
        if (!otp.enabled) throw fail(404, 'Máy chủ chưa bật xác thực SMS.');
        const phone = normPhone(b.phone), k1 = 'o:' + phone, k2 = 'i:' + ip;
        if (throttled(k1) || throttled(k2)) throw fail(429, 'Sai quá nhiều lần. Thử lại sau 15 phút.');
        const user = phone && accounts.users.find(u => u.phone === phone);
        if (!user || !otp.consume(phone, 'login', b.code)) { noteFail(k1); noteFail(k2); throw fail(401, 'Mã xác thực sai hoặc hết hạn.'); }
        failures.delete(k1);
        user.phoneVerified = true; saveAccounts();
        return send(res, 200, { token: newSession(user), user: publicUser(user) });
      }
      if (parts[2] === 'password' && parts[3] === 'reset') {
        if (!otp.enabled) throw fail(404, 'Máy chủ chưa bật xác thực SMS.');
        const phone = normPhone(b.phone), pw = String(b.password || ''), k1 = 'o:' + phone, k2 = 'i:' + ip;
        if (throttled(k1) || throttled(k2)) throw fail(429, 'Sai quá nhiều lần. Thử lại sau 15 phút.');
        if (pw.length < 8) throw fail(400, 'Mật khẩu tối thiểu 8 ký tự.');
        const user = phone && accounts.users.find(u => u.phone === phone);
        if (!user || !otp.consume(phone, 'reset', b.code)) { noteFail(k1); noteFail(k2); throw fail(401, 'Mã xác thực sai hoặc hết hạn.'); }
        Object.assign(user, hashPassword(pw), { phoneVerified: true });
        accounts.sessions = accounts.sessions.filter(x => x.userId !== user.id);        // đổi mật khẩu thì mọi phiên cũ bị đăng xuất
        saveAccounts();
        return send(res, 200, { token: newSession(user), user: publicUser(user) });
      }
      if (parts[2] === 'login') {
        const phone = normPhone(b.phone), k1 = 'p:' + phone, k2 = 'i:' + ip;
        if (throttled(k1) || throttled(k2)) throw fail(429, 'Sai quá nhiều lần. Thử lại sau 15 phút.');
        const user = phone && accounts.users.find(u => u.phone === phone);
        if (!user || !checkPassword(user, String(b.password || ''))) { noteFail(k1); noteFail(k2); throw fail(401, 'Sai số điện thoại hoặc mật khẩu.'); }
        failures.delete(k1);
        return send(res, 200, { token: newSession(user), user: publicUser(user) });
      }
    }

    const who = identify(req);
    if (!who) return send(res, 401, { error: 'Chưa đăng nhập hoặc phiên đã hết hạn.' });

    if (parts[0] !== 'v1') return send(res, 404, { error: 'Không tìm thấy.' });

    if (parts[1] === 'auth' && parts[2] === 'logout' && req.method === 'POST') {
      accounts.sessions = accounts.sessions.filter(s => s.h !== who.tokenHash); saveAccounts();
      return send(res, 200, { ok: true });
    }
    if (parts[1] === 'me' && req.method === 'GET') {
      if (who.kind === 'admin') return send(res, 200, { admin: true, gardens: fs.readdirSync(DATA_DIR).filter(k => KEY_RE.test(k) && readMeta(k)).map(k => gardenSummary(k, { role: 'owner' })) });
      const gardens = [];
      for (const k of fs.readdirSync(DATA_DIR)) {
        if (!KEY_RE.test(k) || !readMeta(k)) continue;
        const m = memberOf(k, who);
        if (m) gardens.push(gardenSummary(k, m));
      }
      return send(res, 200, { user: publicUser(who.user), gardens });
    }

    // ---- nhận lời mời ----
    if (parts[1] === 'invites' && parts[2] === 'accept' && req.method === 'POST') {
      if (who.kind !== 'user') throw fail(403, 'Cần đăng nhập bằng tài khoản người dùng.');
      const k = 'inv:' + who.id;
      if (throttled(k)) throw fail(429, 'Nhập sai mã mời quá nhiều lần. Thử lại sau 15 phút.');
      const b = await readJsonBody(req);
      const code = String(b.code || '').trim().toUpperCase().replace(/[\s-]/g, '');
      if (!CODE_RE.test(code)) { noteFail(k); throw fail(400, 'Mã mời không hợp lệ.'); }
      for (const key of fs.readdirSync(DATA_DIR)) {
        if (!KEY_RE.test(key) || !fs.existsSync(path.join(gardenDir(key), 'invites.json'))) continue;
        const invites = readInvites(key);
        const inv = invites.find(i => i.codeHash === sha256(code));
        if (!inv) continue;
        if (inv.usedAt || inv.revokedAt || inv.expiresAt <= Date.now()) throw fail(410, 'Lời mời đã dùng, bị thu hồi hoặc hết hạn.');
        if (inv.phone && inv.phone !== who.phone) { noteFail(k); throw fail(403, 'Lời mời này dành cho số điện thoại khác.'); }
        const members = readMembers(key);
        const cur = members.find(m => m.userId === who.id);
        if (cur && rbac.activeMember(cur) && cur.role === 'owner') throw fail(409, 'Bạn là chủ vườn này.');
        const entry = { userId: who.id, name: who.name, phone: who.phone, role: inv.role, scope: inv.scope || null,
          expiresAt: inv.memberDays ? Date.now() + inv.memberDays * 86400000 : null, joinedAt: Date.now(), invitedBy: inv.createdBy };
        writeMembers(key, [...members.filter(m => m.userId !== who.id), entry]);
        inv.usedAt = Date.now(); inv.usedBy = who.id; writeInvites(key, invites);
        audit(key, { ...who, role: inv.role }, 'member.join', { role: inv.role, scope: inv.scope, expiresAt: entry.expiresAt });
        return send(res, 200, { garden: gardenSummary(key, entry) });
      }
      noteFail(k);
      throw fail(404, 'Không tìm thấy lời mời.');
    }

    // ---- tạo vườn ----
    if (parts[1] === 'gardens' && parts.length === 2 && req.method === 'POST') {
      if (who.kind !== 'user') throw fail(403, 'Quản trị viên dùng /v1/admin để cấp chủ cho vườn.');
      const b = await readJsonBody(req);
      const key = String(b.key || '').trim();
      if (!KEY_RE.test(key)) throw fail(400, 'Mã vườn chỉ gồm chữ, số, - và _ (3–64 ký tự).');
      if (fs.existsSync(gardenDir(key))) throw fail(409, 'Mã vườn đã tồn tại. Chọn mã khác hoặc nhờ chủ vườn mời bạn.');
      fs.mkdirSync(path.join(gardenDir(key), 'photos'), { recursive: true });
      writeMeta(key, { rev: 0, updatedAt: Date.now(), name: String(b.name || key).slice(0, 80), ownerId: who.id, createdAt: Date.now(), public: { enabled: false, fields: [] } });
      writeMembers(key, [{ userId: who.id, name: who.name, phone: who.phone, role: 'owner', scope: null, expiresAt: null, joinedAt: Date.now() }]);
      audit(key, { ...who, role: 'owner' }, 'garden.create', { name: b.name || key });
      infra.syncGardenSnapshot(key, readMeta(key), null, { ...who, role: 'owner' }).catch(() => {});
      return send(res, 200, { garden: gardenSummary(key, { role: 'owner' }) });
    }

    // ---- quản trị: cấp chủ cho vườn cũ ----
    if (parts[1] === 'admin' && parts[2] === 'gardens' && parts[4] === 'owner' && req.method === 'PUT') {
      if (who.kind !== 'admin') throw fail(403, 'Chỉ quản trị hệ thống.');
      const key = parts[3];
      if (!KEY_RE.test(key) || !readMeta(key)) throw fail(404, 'Không tìm thấy vườn.');
      const b = await readJsonBody(req);
      const user = accounts.users.find(u => u.phone === normPhone(b.phone));
      if (!user) throw fail(404, 'Chưa có tài khoản với số điện thoại này.');
      const meta = readMeta(key);
      writeMeta(key, { ...meta, ownerId: user.id });
      const members = readMembers(key).map(m => m.role === 'owner' && !m.revokedAt ? { ...m, role: 'manager' } : m).filter(m => m.userId !== user.id);
      writeMembers(key, [...members, { userId: user.id, name: user.name, phone: user.phone, role: 'owner', scope: null, expiresAt: null, joinedAt: Date.now() }]);
      audit(key, who, 'owner.assign', { to: user.id });
      return send(res, 200, { ok: true });
    }

    // ---- theo vườn ----
    if (parts[1] !== 'gardens' || !parts[2] || !KEY_RE.test(parts[2])) return send(res, 404, { error: 'Không tìm thấy.' });
    const key = parts[2], dir = gardenDir(key);
    const mem = memberOf(key, who);
    // Không phân biệt "không tồn tại" và "không có quyền" để không lộ mã vườn của người khác.
    const gardenExists = !!readMeta(key);
    if (!mem || (!gardenExists && who.kind !== 'admin')) return send(res, 404, { error: 'Không tìm thấy vườn, hoặc bạn không có quyền.' });
    const isOM = mem.role === 'owner' || mem.role === 'manager';
    const sub = parts[3];
    const actor = { ...who, role: mem.role, name: mem.name };

    if (!sub && req.method === 'DELETE') {
      if (mem.role !== 'owner') throw fail(403, 'Chỉ chủ vườn được xoá vườn.');
      if (req.headers['x-confirm'] !== key) throw fail(400, 'Cần gửi X-Confirm với đúng mã vườn để xác nhận xoá.');
      audit(key, actor, 'garden.delete', {});
      const gone = path.join(DATA_DIR, '.deleted-' + key + '-' + Date.now());
      fs.renameSync(dir, gone);                       // xoá lan truyền: dữ liệu, ảnh, thành viên, lời mời
      fs.rmSync(path.join(gone, 'photos'), { recursive: true, force: true });
      for (const f of ['snapshot.json', 'members.json', 'invites.json']) fs.rmSync(path.join(gone, f), { force: true });
      return send(res, 200, { ok: true });
    }

    // ---- snapshot ----
    if (sub === 'snapshot' && parts.length === 4) {
      if (req.method === 'GET') {
        const meta = readMeta(key);
        const snap = readSnapshot(key);
        if (!meta || !snap) return send(res, 404, { error: 'Vườn chưa có dữ liệu trên máy chủ.', rev: 0, role: mem.role });
        return send(res, 200, { rev: meta.rev, updatedAt: meta.updatedAt, role: mem.role, scope: mem.scope, data: rbac.redact(snap, mem.role, mem.scope) });
      }
      if (req.method === 'PUT') {
        const body = await readBody(req, MAX_SNAPSHOT);
        let data;
        try { data = JSON.parse(body.toString('utf8')); } catch { return send(res, 400, { error: 'Dữ liệu không phải JSON hợp lệ.' }); }
        if (!data || typeof data !== 'object' || !Array.isArray(data.trees)) return send(res, 400, { error: 'Dữ liệu vườn không hợp lệ.' });
        const meta = readMeta(key);
        const current = meta ? meta.rev : 0;
        const ifMatch = Number(req.headers['if-match']);
        if (!Number.isInteger(ifMatch) || ifMatch !== current) return send(res, 409, { error: 'Máy chủ đã có bản mới hơn.', rev: current });
        const old = readSnapshot(key);
        const r = rbac.applyPut(old, data, mem);
        fs.mkdirSync(path.join(dir, 'photos'), { recursive: true });
        const rev = current + 1;
        const next = { ...r.snapshot, conflicts: [] };
        writeJson(path.join(dir, 'snapshot.json'), next);
        writeMeta(key, { ...meta, rev, updatedAt: Date.now() });
        infra.syncGardenSnapshot(key, { ...meta, rev }, next, actor).catch(() => {});
        // ảnh không còn được tham chiếu (do xoá) cũng bị xoá khỏi máy chủ
        if (old) {
          const still = rbac.referencedFiles(next);
          for (const n of rbac.referencedFiles(old)) if (!still.has(n) && PHOTO_RE.test(n)) fs.rmSync(path.join(dir, 'photos', n), { force: true });
        }
        if (r.accepted || r.removed.length || r.rejected.length) {
          const counts = {};
          for (const g of r.removed) counts[g.type] = (counts[g.type] || 0) + 1;
          audit(key, actor, 'snapshot.put', { rev, accepted: r.accepted, stale: r.stale, deleted: counts, rejected: r.rejected.slice(0, 20), rejectedCount: r.rejected.length });
        }
        return send(res, 200, { rev, accepted: r.accepted, stale: r.stale, rejected: r.rejected, deleted: r.removed.length });
      }
      return send(res, 405, { error: 'Phương thức không hỗ trợ.' });
    }

    // ---- ảnh ----
    if (sub === 'photos') {
      if (parts.length === 4 && req.method === 'GET') {
        const names = fs.existsSync(path.join(dir, 'photos')) ? fs.readdirSync(path.join(dir, 'photos')).filter(n => PHOTO_RE.test(n) && !n.endsWith('.tmp')) : [];
        return send(res, 200, { names });
      }
      if (parts.length === 5) {
        const name = parts[4];
        if (!PHOTO_RE.test(name)) return send(res, 400, { error: 'Tên ảnh không hợp lệ.' });
        const file = path.join(dir, 'photos', name);
        if (req.method === 'GET') {
          if (!fs.existsSync(file)) return send(res, 404, { error: 'Không có ảnh.' });
          return send(res, 200, fs.readFileSync(file), 'image/jpeg');
        }
        if (req.method === 'PUT') {
          if (!['events', 'imagery', 'droneFlights'].some(c => rbac.WRITE[mem.role].has(c))) throw fail(403, 'Vai trò của bạn không được tải ảnh lên.');
          const body = await readBody(req, MAX_PHOTO);
          if (body.length === 0) return send(res, 400, { error: 'Ảnh rỗng.' });
          fs.mkdirSync(path.join(dir, 'photos'), { recursive: true });
          if (!fs.existsSync(file)) writeAtomic(file, body);   // ảnh theo ID không đổi; không ghi đè
          return send(res, 200, { ok: true });
        }
      }
    }

    // ---- chuyển quyền chủ vườn: chỉ chủ hiện tại, phải nhập lại mật khẩu, người nhận phải đã là thành viên ----
    if (sub === 'transfer' && parts.length === 4 && req.method === 'POST') {
      if (who.kind !== 'user' || mem.role !== 'owner') throw fail(403, 'Chỉ chủ vườn được chuyển quyền chủ.');
      const k = 'tr:' + who.id;
      if (throttled(k)) throw fail(429, 'Nhập sai mật khẩu quá nhiều lần. Thử lại sau 15 phút.');
      const b = await readJsonBody(req);
      if (!checkPassword(who.user, String(b.password || ''))) { noteFail(k); throw fail(403, 'Mật khẩu không đúng.'); }
      const members = readMembers(key);
      const target = members.find(m => m.userId === b.userId && rbac.activeMember(m));
      if (!target || target.userId === who.id) throw fail(404, 'Người nhận phải là thành viên đang hoạt động khác bạn.');
      writeMembers(key, members.map(m => m.userId === who.id ? { ...m, role: 'manager' } : m.userId === target.userId ? { ...m, role: 'owner', scope: null, expiresAt: null } : m));
      writeMeta(key, { ...readMeta(key), ownerId: target.userId });
      audit(key, actor, 'owner.transfer', { to: target.userId, toName: target.name });
      return send(res, 200, { ok: true });
    }

    // ---- chia sẻ vị trí trực tiếp (người dùng tự bật, có hẹn giờ; chỉ giữ trong bộ nhớ, không ghi đĩa) ----
    if (sub === 'live') {
      if (who.kind !== 'user') throw fail(403, 'Chỉ tài khoản người dùng mới chia sẻ vị trí.');
      const room = live.get(key) || new Map();
      live.set(key, room);
      const now = Date.now();
      const activeIds = new Set(readMembers(key).filter(m => rbac.activeMember(m)).map(m => m.userId));
      for (const [uid, s] of room) if (s.until <= now || !activeIds.has(uid)) room.delete(uid);      // hết giờ hoặc đã bị thu hồi/hết hạn quyền → xoá vị trí
      if (parts[4] === 'me' && parts.length === 5 && req.method === 'PUT') {
        const b = await readJsonBody(req);
        const minutes = Number(b.minutes);
        if (!Number.isFinite(minutes) || minutes < 1 || minutes > 480) throw fail(400, 'Thời gian chia sẻ từ 1 đến 480 phút.');
        room.set(who.id, { name: mem.name, role: mem.role, until: now + minutes * 60000, startedAt: now, pos: null });
        audit(key, actor, 'live.start', { minutes });
        return send(res, 200, { until: now + minutes * 60000 });
      }
      if (parts[4] === 'me' && parts.length === 5 && req.method === 'DELETE') {
        if (room.delete(who.id)) audit(key, actor, 'live.stop', {});
        return send(res, 200, { ok: true });
      }
      if (parts[4] === 'me' && parts[5] === 'position' && req.method === 'POST') {
        const s = room.get(who.id);
        if (!s) throw fail(409, 'Bạn chưa bật chia sẻ vị trí hoặc đã hết thời gian.');
        const b = await readJsonBody(req);
        const lat = Number(b.lat), lng = Number(b.lng), acc = b.acc === undefined || b.acc === null ? null : Number(b.acc);
        if (!(Math.abs(lat) <= 90) || !(Math.abs(lng) <= 180) || (acc !== null && !(acc >= 0 && acc < 100000))) throw fail(400, 'Toạ độ không hợp lệ.');
        s.pos = { lat, lng, acc, at: now };
        return send(res, 200, { until: s.until });
      }
      if (parts.length === 4 && req.method === 'GET') {
        const list = [...room].filter(([uid, s]) => s.pos && (isOM || uid === who.id))
          .map(([uid, s]) => ({ userId: uid, name: s.name, role: s.role, until: s.until, ...s.pos }));
        return send(res, 200, { people: list, sharing: room.has(who.id) ? room.get(who.id).until : null });
      }
    }

    // ---- thành viên ----
    if (sub === 'members') {
      if (!isOM) throw fail(403, 'Chỉ chủ vườn hoặc quản lý xem được danh sách thành viên.');
      const members = readMembers(key);
      if (parts.length === 4 && req.method === 'GET') {
        return send(res, 200, { members: members.filter(m => !m.revokedAt).map(m => ({ ...m, expired: !rbac.activeMember(m) })) });
      }
      if (parts.length === 5) {
        const uid = parts[4];
        const target = members.find(m => m.userId === uid && !m.revokedAt);
        if (!target) throw fail(404, 'Không tìm thấy thành viên.');
        if (target.role === 'owner') throw fail(403, 'Không thể sửa hoặc thu hồi chủ vườn.');
        if (mem.role === 'manager' && !rbac.MANAGER_GRANTS.has(target.role)) throw fail(403, 'Quản lý chỉ quản lý được nhân viên, phi công và chuyên gia.');
        if (req.method === 'DELETE') {
          writeMembers(key, members.map(m => m.userId === uid ? { ...m, revokedAt: Date.now(), revokedBy: who.id || 'admin' } : m));
          // quyền được kiểm tra lại ở mỗi yêu cầu nên thu hồi có hiệu lực ngay, không cần huỷ phiên đăng nhập
          audit(key, actor, 'member.revoke', { userId: uid, name: target.name, role: target.role });
          return send(res, 200, { ok: true });
        }
        if (req.method === 'PATCH') {
          const b = await readJsonBody(req);
          const role = b.role === undefined ? target.role : b.role;
          if (!rbac.ROLES.includes(role) || role === 'owner') throw fail(400, 'Vai trò không hợp lệ.');
          if (mem.role === 'manager' && !rbac.MANAGER_GRANTS.has(role)) throw fail(403, 'Quản lý không được cấp vai trò này.');
          const scope = b.scope === undefined ? target.scope : parseScope(b.scope);
          const expiresAt = b.expiresAt === undefined ? target.expiresAt : (b.expiresAt === null ? null : Number(b.expiresAt));
          if (expiresAt !== null && !Number.isFinite(expiresAt)) throw fail(400, 'Ngày hết hạn không hợp lệ.');
          writeMembers(key, members.map(m => m.userId === uid ? { ...m, role, scope, expiresAt } : m));
          audit(key, actor, 'member.update', { userId: uid, name: target.name, from: { role: target.role, scope: target.scope, expiresAt: target.expiresAt }, to: { role, scope, expiresAt } });
          return send(res, 200, { ok: true });
        }
      }
    }

    // ---- lời mời ----
    if (sub === 'invites') {
      if (!isOM) throw fail(403, 'Chỉ chủ vườn hoặc quản lý được mời thành viên.');
      const invites = readInvites(key);
      if (parts.length === 4 && req.method === 'GET') {
        return send(res, 200, { invites: invites.filter(i => !i.usedAt && !i.revokedAt && i.expiresAt > Date.now()).map(({ codeHash, ...i }) => i) });
      }
      if (parts.length === 4 && req.method === 'POST') {
        const b = await readJsonBody(req);
        if (!rbac.ROLES.includes(b.role) || b.role === 'owner') throw fail(400, 'Vai trò không hợp lệ.');
        if (mem.role === 'manager' && !rbac.MANAGER_GRANTS.has(b.role)) throw fail(403, 'Quản lý không được mời vai trò này.');
        const phone = b.phone ? normPhone(b.phone) : null;
        if (b.phone && !phone) throw fail(400, 'Số điện thoại không hợp lệ.');
        const memberDays = b.memberDays === undefined || b.memberDays === null ? null : Number(b.memberDays);
        if (memberDays !== null && !(memberDays >= 1 && memberDays <= 3650)) throw fail(400, 'Thời hạn thành viên phải từ 1 đến 3650 ngày.');
        if (b.role === 'pilot' && memberDays === null) throw fail(400, 'Phi công thuê ngoài cần có thời hạn (số ngày) để tự hết quyền.');
        const code = randomCode();
        const inv = { id: crypto.randomBytes(6).toString('hex'), codeHash: sha256(code), role: b.role, scope: parseScope(b.scope), memberDays, phone,
          createdBy: who.id || 'admin', createdByName: mem.name, createdAt: Date.now(), expiresAt: Date.now() + INVITE_MS };
        writeInvites(key, [...invites.filter(i => i.expiresAt > Date.now() - 30 * 86400000), inv]);
        audit(key, actor, 'member.invite', { inviteId: inv.id, role: inv.role, scope: inv.scope, memberDays, phone });
        const { codeHash, ...shown } = inv;
        return send(res, 200, { ...shown, code, uri: `vwing://invite/${code}`, server: baseUrl(req), gardenKey: key });
      }
      if (parts.length === 5 && req.method === 'DELETE') {
        const inv = invites.find(i => i.id === parts[4]);
        if (!inv) throw fail(404, 'Không tìm thấy lời mời.');
        writeInvites(key, invites.map(i => i.id === inv.id ? { ...i, revokedAt: Date.now() } : i));
        audit(key, actor, 'invite.revoke', { inviteId: inv.id });
        return send(res, 200, { ok: true });
      }
    }

    // ---- nhật ký kiểm toán ----
    if (sub === 'audit' && req.method === 'GET') {
      if (mem.role !== 'owner') throw fail(403, 'Chỉ chủ vườn xem được nhật ký kiểm toán.');
      const limit = Math.min(500, Math.max(1, Number(url.searchParams.get('limit')) || 100));
      return send(res, 200, { entries: readAudit(key, limit) });
    }

    // ---- cấu hình trang công khai (R6) ----
    if (sub === 'public') {
      const meta = readMeta(key);
      if (req.method === 'GET') {
        if (!isOM) throw fail(403, 'Chỉ chủ vườn hoặc quản lý xem được cấu hình này.');
        return send(res, 200, { public: meta.public || { enabled: false, fields: [] }, allFields: pub.FIELDS, baseUrl: baseUrl(req) });
      }
      if (req.method === 'PUT') {
        if (mem.role !== 'owner') throw fail(403, 'Chỉ chủ vườn chọn dữ liệu công khai.');
        const b = await readJsonBody(req);
        const fields = Array.isArray(b.fields) ? [...new Set(b.fields)] : [];
        const bad = fields.filter(f => !pub.FIELDS.some(x => x.id === f));
        if (bad.length) throw fail(400, 'Trường không hợp lệ: ' + bad.join(', '));
        const cfg = { enabled: !!b.enabled, fields, story: String(b.story || '').slice(0, 600) };
        writeMeta(key, { ...meta, public: cfg });
        audit(key, actor, 'public.update', cfg);
        return send(res, 200, { public: cfg });
      }
    }

    send(res, 404, { error: 'Không tìm thấy.' });
  } catch (e) {
    send(res, e.status || 500, { error: e.msg || (e.status === 413 ? 'Dữ liệu quá lớn.' : e.status ? e.message : 'Lỗi máy chủ.') });
    if (!e.status) console.error(e);
  }
});

function parseScope(s) {
  if (!s || typeof s !== 'object') return null;
  const zones = Array.isArray(s.zones) ? s.zones.map(z => String(z).slice(0, 80)).filter(Boolean).slice(0, 100) : [];
  return zones.length ? { zones } : null;
}

if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => console.log(`VWing server: cổng ${PORT}, dữ liệu tại ${DATA_DIR}`));
}
module.exports = server;
module.exports.otpOutbox = otp.outbox;
