// Mã OTP qua SMS: gửi mã 6 số, kiểm tra một lần, hết hạn 5 phút, tối đa 5 lần thử, giới hạn tần suất gửi.
// Nhà cung cấp SMS đổi được bằng biến môi trường (xem README): OTP_PROVIDER=dev | twilio | webhook.
//   dev     : KHÔNG gửi SMS thật; ghi mã vào nhật ký máy chủ (console) và hộp thư trong bộ nhớ (dùng cho kiểm thử/thử nội bộ).
//   twilio  : TWILIO_SID, TWILIO_TOKEN và TWILIO_FROM (số gửi) hoặc TWILIO_MESSAGING_SERVICE.
//   webhook : POST JSON {phone, text} tới OTP_WEBHOOK_URL (kèm "Authorization: Bearer OTP_WEBHOOK_TOKEN" nếu có) — nối được với nhà mạng/nhà cung cấp SMS trong nước.
// Không đặt OTP_PROVIDER thì OTP tắt (đăng ký chỉ cần SĐT + mật khẩu như trước).
const crypto = require('crypto');

const TTL_MS = 5 * 60 * 1000;
const RESEND_MS = (Number(process.env.OTP_RESEND_SECONDS) || 60) * 1000;      // chỉ hạ xuống khi kiểm thử
const MAX_TRIES = 5;
const PER_PHONE_HOUR = 5;
const PER_IP_HOUR = 20;

const provider = (process.env.OTP_PROVIDER || '').trim().toLowerCase();
const enabled = ['dev', 'twilio', 'webhook'].includes(provider);
const outbox = [];                       // chỉ có ở chế độ dev, chỉ để kiểm thử/xem nội bộ; không bao giờ trả qua HTTP
const store = new Map();                 // phone → {hash, salt, purpose, exp, tries, sentAt}
const sends = new Map();                 // khoá "p:<sđt>" / "i:<ip>" → [thời điểm gửi]

const sha = (code, salt) => crypto.createHash('sha256').update(salt + ':' + code).digest();

function allow(key, max) {
  const now = Date.now(), h = (sends.get(key) || []).filter(t => now - t < 3600000);
  if (h.length >= max) { sends.set(key, h); return false; }
  h.push(now); sends.set(key, h);
  if (sends.size > 5000) sends.clear();
  return true;
}

async function deliver(phone, text) {
  if (provider === 'dev') { outbox.push({ phone, text, at: Date.now() }); console.log(`[OTP dev] ${phone}: ${text}`); return; }
  if (provider === 'twilio') {
    const sid = process.env.TWILIO_SID, token = process.env.TWILIO_TOKEN;
    if (!sid || !token) throw new Error('Thiếu TWILIO_SID/TWILIO_TOKEN');
    const body = new URLSearchParams({ To: '+84' + phone.slice(1), Body: text });
    if (process.env.TWILIO_MESSAGING_SERVICE) body.set('MessagingServiceSid', process.env.TWILIO_MESSAGING_SERVICE);
    else body.set('From', process.env.TWILIO_FROM || '');
    const r = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, {
      method: 'POST', body, headers: { Authorization: 'Basic ' + Buffer.from(sid + ':' + token).toString('base64') }, signal: AbortSignal.timeout(10000),
    });
    if (!r.ok) throw new Error('Twilio HTTP ' + r.status);
    return;
  }
  if (provider === 'webhook') {
    const url = process.env.OTP_WEBHOOK_URL;
    if (!url) throw new Error('Thiếu OTP_WEBHOOK_URL');
    const headers = { 'Content-Type': 'application/json' };
    if (process.env.OTP_WEBHOOK_TOKEN) headers.Authorization = 'Bearer ' + process.env.OTP_WEBHOOK_TOKEN;
    const r = await fetch(url, { method: 'POST', headers, body: JSON.stringify({ phone, text }), signal: AbortSignal.timeout(10000) });
    if (!r.ok) throw new Error('Webhook HTTP ' + r.status);
    return;
  }
  throw new Error('OTP chưa bật');
}

/** Gửi mã. Trả {ok, wait} ; lỗi người dùng ném {status, msg}. */
async function request(phone, purpose, ip) {
  const err = (status, msg) => Object.assign(new Error(msg), { status, msg });
  const cur = store.get(phone);
  if (cur && Date.now() - cur.sentAt < RESEND_MS) throw err(429, `Đợi ${Math.ceil((RESEND_MS - (Date.now() - cur.sentAt)) / 1000)} giây rồi gửi lại mã.`);
  if (!allow('p:' + phone, PER_PHONE_HOUR) || !allow('i:' + ip, PER_IP_HOUR)) throw err(429, 'Đã gửi quá nhiều mã. Thử lại sau 1 giờ.');
  const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  const salt = crypto.randomBytes(8).toString('hex');
  try { await deliver(phone, `VWing: ma xac thuc ${code}. Co hieu luc 5 phut. Khong chia se ma nay.`); }
  catch (e) { console.error('Gửi SMS lỗi:', e.message); throw err(502, 'Không gửi được tin nhắn SMS. Thử lại sau.'); }
  store.set(phone, { hash: sha(code, salt), salt, purpose, exp: Date.now() + TTL_MS, tries: 0, sentAt: Date.now() });
  return { ok: true, resendAfter: RESEND_MS / 1000 };
}

/** Kiểm tra và tiêu thụ mã (dùng một lần). Sai quá 5 lần thì mã bị huỷ. */
function consume(phone, purpose, code) {
  const e = store.get(phone);
  if (!e || e.purpose !== purpose || e.exp <= Date.now()) return false;
  e.tries++;
  if (e.tries > MAX_TRIES) { store.delete(phone); return false; }
  const ok = /^\d{6}$/.test(String(code || '')) && crypto.timingSafeEqual(sha(String(code), e.salt), e.hash);
  if (ok) store.delete(phone);
  return ok;
}

module.exports = { enabled, provider, request, consume, outbox, TTL_MS, RESEND_MS, MAX_TRIES };
