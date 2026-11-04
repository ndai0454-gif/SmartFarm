// Kiểm thử đăng nhập bằng mã SMS (OTP): node --test test-otp.js  (chạy riêng vì OTP bật bằng biến môi trường)
process.env.VWING_TOKEN = 'test-token-123456';
process.env.OTP_PROVIDER = 'dev';
process.env.OTP_RESEND_SECONDS = '1';
process.env.DATA_DIR = require('path').join(require('os').tmpdir(), 'vwing-otp-test-' + Date.now());
const test = require('node:test');
const assert = require('node:assert');
const server = require('./server');

let base;
test.before(async () => { await new Promise(r => server.listen(0, '127.0.0.1', r)); base = 'http://127.0.0.1:' + server.address().port; });
test.after(() => server.close());

async function api(method, url, body, token) {
  const r = await fetch(base + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, data: await r.json().catch(() => ({})) };
}
const lastCode = phone => { const m = [...server.otpOutbox].reverse().find(x => x.phone === phone); return m && /(\d{6})/.exec(m.text)[1]; };
const request = (phone, purpose) => api('POST', '/v1/auth/otp/request', { phone, purpose });

test('máy chủ báo OTP bật; mã không bao giờ nằm trong phản hồi HTTP', async () => {
  assert.equal((await api('GET', '/v1/health')).data.otp, true);
  const r = await request('0922000001', 'register');
  assert.equal(r.status, 200); assert.deepEqual(Object.keys(r.data).sort(), ['ok', 'resendAfter']);
  assert.match(lastCode('0922000001'), /^\d{6}$/);
});

test('đăng ký phải có mã SMS đúng; mã dùng một lần; sai thì từ chối', async () => {
  const phone = '0922000002', body = { phone, name: 'An', password: 'matkhau-12345' };
  assert.equal((await api('POST', '/v1/auth/register', body)).status, 403);                         // chưa có mã
  await request(phone, 'register');
  const code = lastCode(phone);
  assert.equal((await api('POST', '/v1/auth/register', { ...body, otp: code === '000000' ? '111111' : '000000' })).status, 403);   // mã sai
  const ok = await api('POST', '/v1/auth/register', { ...body, otp: code });
  assert.equal(ok.status, 200); assert.ok(ok.data.token);
  assert.equal((await api('GET', '/v1/me', null, ok.data.token)).status, 200);
  assert.equal((await api('POST', '/v1/auth/register', { ...body, phone: '0922000003', otp: code })).status, 403);   // mã của số khác/đã dùng
  assert.equal((await request(phone, 'register')).status, 409);                                      // số đã đăng ký
});

test('giới hạn: gửi lại phải đợi 60 giây; sai quá 5 lần mã bị huỷ; mã sai mục đích không dùng được', async () => {
  const phone = '0922000004';
  await request(phone, 'register');
  assert.equal((await request(phone, 'register')).status, 429);                                      // gửi lại quá nhanh
  const code = lastCode(phone);
  for (let i = 0; i < 5; i++) await api('POST', '/v1/auth/register', { phone, name: 'B', password: 'matkhau-12345', otp: code === '123456' ? '654321' : '123456' });
  assert.equal((await api('POST', '/v1/auth/register', { phone, name: 'B', password: 'matkhau-12345', otp: code })).status, 403);   // mã đúng nhưng đã bị huỷ
});

test('đăng nhập bằng mã SMS (dùng một lần, đúng mục đích) và đặt lại mật khẩu (đăng xuất mọi phiên cũ)', async () => {
  const phone = '0922000005', sleep = ms => new Promise(r => setTimeout(r, ms));
  await request(phone, 'register');
  const reg = await api('POST', '/v1/auth/register', { phone, name: 'C', password: 'matkhau-12345', otp: lastCode(phone) });
  await sleep(1100);
  assert.equal((await request(phone, 'login')).status, 200);
  const code = lastCode(phone);
  assert.equal((await api('POST', '/v1/auth/password/reset', { phone, code, password: 'matkhau-moi-1' })).status, 401);   // mã đăng nhập không đổi được mật khẩu
  const login = await api('POST', '/v1/auth/otp/login', { phone, code });
  assert.equal(login.status, 200); assert.ok(login.data.token);
  assert.equal((await api('POST', '/v1/auth/otp/login', { phone, code })).status, 401);                                  // dùng một lần
  await sleep(1100);
  assert.equal((await request(phone, 'reset')).status, 200);
  const reset = await api('POST', '/v1/auth/password/reset', { phone, code: lastCode(phone), password: 'matkhau-moi-1' });
  assert.equal(reset.status, 200);
  assert.equal((await api('GET', '/v1/me', null, reg.data.token)).status, 401);                                          // phiên cũ bị đăng xuất
  assert.equal((await api('GET', '/v1/me', null, login.data.token)).status, 401);
  assert.equal((await api('GET', '/v1/me', null, reset.data.token)).status, 200);
  assert.equal((await api('POST', '/v1/auth/login', { phone, password: 'matkhau-12345' })).status, 401);                // mật khẩu cũ hết dùng
  assert.equal((await api('POST', '/v1/auth/login', { phone, password: 'matkhau-moi-1' })).status, 200);
  assert.equal((await api('POST', '/v1/auth/password/reset', { phone, code: '000000', password: 'ngan' })).status, 400);
});

test('không lộ số nào có tài khoản: đăng nhập/đặt lại cho số lạ vẫn trả đã gửi nhưng không gửi SMS', async () => {
  const before = server.otpOutbox.length;
  const r = await request('0922999999', 'login');
  assert.equal(r.status, 200); assert.equal(server.otpOutbox.length, before);
  assert.equal((await api('POST', '/v1/auth/otp/login', { phone: '0922999999', code: '123456' })).status, 401);
  assert.equal((await request('abc', 'login')).status, 400);
  assert.equal((await request('0922000005', 'xxx')).status, 400);
});
