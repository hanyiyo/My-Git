import http from "node:http";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendRoot = path.resolve(__dirname, "../../frontend");
const CAPTCHA_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

export class RedisUnavailable extends Error {}

export class MemoryRedis {
  constructor({ clock = () => Date.now(), captchaTtlMs = 120_000 } = {}) {
    this.clock = clock; this.captchaTtlMs = captchaTtlMs; this.available = true;
    this.values = new Map(); this.currentByBrowser = new Map(); this.buckets = new Map();
  }
  assertAvailable() { if (!this.available) throw new RedisUnavailable("redis unavailable"); }
  purge(key) { const item = this.values.get(key); if (item && item.expiresAt <= this.clock()) this.values.delete(key); return this.values.get(key); }
  set(key, value, ttlMs = this.captchaTtlMs) { this.assertAvailable(); this.values.set(key, { value, expiresAt: this.clock() + ttlMs }); }
  get(key) { this.assertAvailable(); return this.purge(key)?.value; }
  delete(key) { this.assertAvailable(); this.values.delete(key); }
  rateLimit(key, limit, windowMs) {
    this.assertAvailable();
    const now = this.clock(); const bucket = this.buckets.get(key) ?? { startedAt: now, count: 0 };
    if (now - bucket.startedAt >= windowMs) { bucket.startedAt = now; bucket.count = 0; }
    bucket.count += 1; this.buckets.set(key, bucket);
    return { allowed: bucket.count <= limit, remaining: Math.max(0, limit - bucket.count) };
  }
  saveChallenge(challenge) {
    this.assertAvailable();
    const prior = this.currentByBrowser.get(challenge.browserId); if (prior) this.values.delete(prior);
    this.currentByBrowser.set(challenge.browserId, challenge.challengeId);
    this.set(`captcha:${challenge.challengeId}`, challenge, this.captchaTtlMs);
  }
  consumeChallenge(challengeId, browserId, answer) {
    this.assertAvailable();
    const item = this.purge(`captcha:${challengeId}`); if (!item) return false;
    const challenge = item.value;
    if (challenge.browserId !== browserId || this.currentByBrowser.get(browserId) !== challengeId || challenge.consumed) return false;
    challenge.consumed = true; this.values.delete(`captcha:${challengeId}`);
    const supplied = hash(answer ?? "");
    return crypto.timingSafeEqual(Buffer.from(supplied, "hex"), Buffer.from(challenge.answerHash, "hex"));
  }
}

function hash(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function passwordHash(password, salt = crypto.randomBytes(16).toString("hex")) {
  return `${salt}:${crypto.scryptSync(password, salt, 32).toString("hex")}`;
}
function verifyPassword(password, stored) {
  const [salt, expected] = stored.split(":");
  const actual = crypto.scryptSync(password, salt, 32).toString("hex");
  return crypto.timingSafeEqual(Buffer.from(actual, "hex"), Buffer.from(expected, "hex"));
}
function token() { return crypto.randomBytes(32).toString("base64url"); }
function randomCode() { return Array.from({ length: 4 }, () => CAPTCHA_ALPHABET[crypto.randomInt(CAPTCHA_ALPHABET.length)]).join(""); }
function captchaImage(code) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="140" height="48"><rect width="100%" height="100%" fill="#f3f4f6"/><text x="12" y="32" font-family="monospace" font-size="24" letter-spacing="4" fill="#1f2937">${code}</text></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}
function accountView(account) { return { id: account.id, identifier: account.identifier, role: account.role, status: account.status }; }
function errorBody(code, message) { return { error: { code, message } }; }
function json(res, status, body, headers = {}) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", ...headers });
  if (status !== 204) res.end(JSON.stringify(body)); else res.end();
}
function parseBearer(req) { const value = req.headers.authorization ?? ""; return value.startsWith("Bearer ") ? value.slice(7) : null; }
async function body(req) {
  let text = ""; for await (const chunk of req) { text += chunk; if (text.length > 1_000_000) throw new Error("request too large"); }
  if (!text) return {}; try { return JSON.parse(text); } catch { throw Object.assign(new Error("invalid json"), { status: 400, code: "INVALID_JSON" }); }
}

export function createApp(options = {}) {
  const clock = options.clock ?? (() => Date.now());
  const store = options.store ?? new MemoryRedis({ clock });
  const accounts = new Map(); const accessSessions = new Map(); const refreshSessions = new Map();
  const catalog = {
    hospital: { id: "hospital-1", name: "示范医院", address: "", process: "" },
    departments: new Map(), doctors: new Map(), schedules: new Map()
  };
  const booking = { appointments: new Map(), payments: new Map(), refunds: new Map(), guards: new Map(), idempotency: new Map() };
  const reception = { entries: new Map(), sequence: 0 };
  const clinical = { materials: new Map(), messages: new Map(), results: new Map(), corrections: new Map(), attachments: new Map(), grants: new Map() };
  const ai = { facts: new Map(), consents: new Map(), tasks: new Map(), results: new Map(), calls: [] };
  const governance = { changes: new Map(), audits: [] };
  const delivery = { events: [], notifications: [], cursor: 0, tasks: new Map() };
  const catalogId = (prefix) => `${prefix}-${crypto.randomUUID()}`;
  const scheduleWindow = (halfDay) => halfDay === "AM" ? { start: "08:00", end: "12:00" } : { start: "14:00", end: "18:00" };
  const isAdmin = (role) => role === "admin";
  const isDoctor = (role) => role === "doctor";
  const cutoffFor = (schedule) => {
    const endHour = schedule.halfDay === "AM" ? 12 : 18;
    return new Date(`${schedule.date}T${String(endHour - 1).padStart(2, "0")}:45:00+08:00`).getTime();
  };
  const releaseBooking = (appointment) => {
    if (!appointment.released) { const schedule = catalog.schedules.get(appointment.scheduleId); if (schedule) schedule.remaining += 1; booking.guards.delete(appointment.guardKey); appointment.released = true; }
  };
  const sessionWindow = (schedule) => ({ start: new Date(`${schedule.date}T${schedule.start}:00+08:00`).getTime(), end: new Date(`${schedule.date}T${schedule.end}:00+08:00`).getTime() });
  const checkinWindow = (schedule) => { const window = sessionWindow(schedule); return { start: window.start - 30 * 60_000, end: window.end - 15 * 60_000 }; };
  const doctorForAccount = (accountId) => [...catalog.doctors.values()].find((item) => item.accountId === accountId);
  const app = http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/api/v1/health") return json(res, 200, { status: "ok", redis: store.available ? "up" : "down" });
      if (req.method === "GET" && req.url === "/") return serveFrontend(res, "index.html");
      if (req.method === "GET" && req.url?.startsWith("/assets/")) return serveFrontend(res, req.url.slice(1));
      const url = new URL(req.url, "http://localhost"); const route = url.pathname;
      if (!route.startsWith("/api/v1/")) return json(res, 404, errorBody("NOT_FOUND", "资源不存在"));
      const data = await body(req);
      if (req.method === "POST" && route === "/api/v1/auth/register") {
        const identifier = String(data.identifier ?? "").trim().toLowerCase(); const password = String(data.password ?? ""); const role = String(data.role ?? "patient");
        if (!identifier || password.length < 8 || !["patient", "doctor", "admin"].includes(role)) return json(res, 400, errorBody("INVALID_INPUT", "账号、密码或角色无效"));
        if (accounts.has(identifier)) return json(res, 409, errorBody("ACCOUNT_EXISTS", "账号已存在"));
        const account = { id: crypto.randomUUID(), identifier, role, status: "active", password: passwordHash(password), createdAt: clock() }; accounts.set(identifier, account);
        return json(res, 201, { account: accountView(account) });
      }
      if (req.method === "POST" && route === "/api/v1/auth/captcha") {
        const browserId = String(data.browserId ?? req.headers["x-browser-id"] ?? "").trim(); if (!browserId) return json(res, 400, errorBody("BROWSER_REQUIRED", "需要浏览器标识"));
        const limited = store.rateLimit(`captcha:${browserId}`, options.captchaLimit ?? 5, options.captchaWindowMs ?? 60_000); if (!limited.allowed) return json(res, 429, errorBody("RATE_LIMITED", "请求过于频繁，请稍后重试"));
        const code = randomCode(); const challenge = { challengeId: crypto.randomUUID(), browserId, answerHash: hash(code), createdAt: clock(), consumed: false }; store.saveChallenge(challenge);
        const response = { challengeId: challenge.challengeId, image: captchaImage(code), expiresIn: store.captchaTtlMs };
        if (options.exposeCaptchaCode || process.env.NODE_ENV === "test") response.code = code;
        return json(res, 201, response, { "cache-control": "no-store" });
      }
      if (req.method === "POST" && route === "/api/v1/auth/login") {
        const identifier = String(data.identifier ?? "").trim().toLowerCase(); const browserId = String(data.browserId ?? req.headers["x-browser-id"] ?? "");
        const limited = store.rateLimit(`login:${browserId || req.socket.remoteAddress}`, options.loginLimit ?? 10, options.loginWindowMs ?? 60_000); if (!limited.allowed) return json(res, 429, errorBody("RATE_LIMITED", "登录尝试过于频繁，请稍后重试"));
        let captchaOk = false; try { captchaOk = store.consumeChallenge(String(data.challengeId ?? ""), browserId, String(data.captcha ?? "")); } catch (error) { if (error instanceof RedisUnavailable) return json(res, 503, errorBody("REDIS_UNAVAILABLE", "验证码服务暂不可用，请稍后重试")); throw error; }
        if (!captchaOk) return json(res, 401, errorBody("INVALID_CREDENTIALS", "账号或密码错误"));
        const account = accounts.get(identifier); if (!account || account.status !== "active" || !verifyPassword(String(data.password ?? ""), account.password)) return json(res, 401, errorBody("INVALID_CREDENTIALS", "账号或密码错误"));
        const accessToken = token(); const refreshToken = token(); accessSessions.set(accessToken, { accountId: account.id, expiresAt: clock() + (options.accessTtlMs ?? 900_000) }); refreshSessions.set(refreshToken, { accountId: account.id, expiresAt: clock() + (options.refreshTtlMs ?? 7 * 86400000) });
        return json(res, 200, { accessToken, expiresIn: options.accessTtlMs ?? 900_000, refreshToken, account: accountView(account) });
      }
      if (req.method === "POST" && route === "/api/v1/auth/refresh") {
        const current = refreshSessions.get(String(data.refreshToken ?? "")); if (!current || current.expiresAt <= clock()) return json(res, 401, errorBody("INVALID_REFRESH_TOKEN", "刷新会话无效或已过期"));
        refreshSessions.delete(String(data.refreshToken)); const account = [...accounts.values()].find((item) => item.id === current.accountId); if (!account) return json(res, 401, errorBody("INVALID_REFRESH_TOKEN", "刷新会话无效或已过期"));
        const accessToken = token(); const refreshToken = token(); accessSessions.set(accessToken, { accountId: account.id, expiresAt: clock() + (options.accessTtlMs ?? 900_000) }); refreshSessions.set(refreshToken, { accountId: account.id, expiresAt: clock() + (options.refreshTtlMs ?? 7 * 86400000) });
        return json(res, 200, { accessToken, expiresIn: options.accessTtlMs ?? 900_000, refreshToken, account: accountView(account) });
      }
      if (req.method === "POST" && route === "/api/v1/auth/logout") {
        const access = parseBearer(req); if (access) accessSessions.delete(access); if (data.refreshToken) refreshSessions.delete(String(data.refreshToken)); return json(res, 204, null);
      }
      if (req.method === "GET" && route === "/api/v1/catalog") {
        const date = url.searchParams.get("date"); const departmentId = url.searchParams.get("departmentId"); const mode = url.searchParams.get("mode");
        const schedules = [...catalog.schedules.values()].filter((item) => item.active && (!date || item.date === date) && (!departmentId || item.departmentId === departmentId) && (!mode || item.mode === mode) && item.remaining > 0).map((item) => ({ ...item, doctor: catalog.doctors.get(item.doctorId), department: catalog.departments.get(item.departmentId) })).filter((item) => item.doctor?.certified && item.department?.active).map(({ doctor, ...item }) => ({ ...item, doctor: { id: doctor.id, displayName: doctor.displayName } }));
        return json(res, 200, { hospital: catalog.hospital, departments: [...catalog.departments.values()].filter((item) => item.active), schedules });
      }
      const access = parseBearer(req); const session = access ? accessSessions.get(access) : null;
      if (!session || session.expiresAt <= clock()) return json(res, 401, errorBody("UNAUTHENTICATED", "请先登录"));
      const account = [...accounts.values()].find((item) => item.id === session.accountId); if (!account) return json(res, 401, errorBody("UNAUTHENTICATED", "请先登录"));
      const holdRoute = route.match(/^\/api\/v1\/bookings\/holds$/);
      if (req.method === "POST" && holdRoute) {
        if (account.role !== "patient") return json(res, 403, errorBody("FORBIDDEN", "仅患者可登记"));
        const idem = String(req.headers["idempotency-key"] ?? data.idempotencyKey ?? ""); if (idem && booking.idempotency.has(`${account.id}:${idem}`)) return json(res, 200, booking.idempotency.get(`${account.id}:${idem}`));
        const schedule = catalog.schedules.get(String(data.scheduleId ?? "")); const now = clock();
        if (!schedule?.active || schedule.remaining < 1) return json(res, 409, errorBody("SLOT_UNAVAILABLE", "号源不可用"));
        if (now >= new Date(`${schedule.date}T${schedule.start}:00+08:00`).getTime()) return json(res, 409, errorBody("ADVANCE_CLOSED", "提前预约已截止"));
        const guardKey = `${account.id}:${schedule.date}:${schedule.halfDay}`; const existing = booking.guards.get(guardKey); if (existing && existing.holdExpiresAt > now && !["CANCELLED", "EXPIRED"].includes(existing.status)) return json(res, 409, errorBody("HALF_DAY_CONFLICT", "同一日期半天已有有效登记"));
        const holdExpiresAt = Math.min(now + 10 * 60_000, cutoffFor(schedule)); if (holdExpiresAt <= now) return json(res, 409, errorBody("CHECKIN_CLOSED", "签到窗口已结束"));
        const appointment = { id: catalogId("appointment"), patientId: account.id, scheduleId: schedule.id, date: schedule.date, halfDay: schedule.halfDay, mode: schedule.mode, amount: schedule.fee, status: "PENDING_PAYMENT", holdExpiresAt, guardKey, released: false, source: "ADVANCE", createdAt: now };
        schedule.remaining -= 1; booking.appointments.set(appointment.id, appointment); booking.guards.set(guardKey, appointment); const result = { appointment, paymentDeadline: new Date(holdExpiresAt).toISOString() }; if (idem) booking.idempotency.set(`${account.id}:${idem}`, result); return json(res, 201, result);
      }
      const payRoute = route.match(/^\/api\/v1\/bookings\/([^/]+)\/pay$/);
      if (req.method === "POST" && payRoute) {
        if (account.role !== "patient") return json(res, 403, errorBody("FORBIDDEN", "仅患者可支付")); const appointment = booking.appointments.get(payRoute[1]); if (!appointment || appointment.patientId !== account.id) return json(res, 404, errorBody("NOT_FOUND", "登记不存在"));
        const idem = String(req.headers["idempotency-key"] ?? data.idempotencyKey ?? ""); if (idem && booking.idempotency.has(`${account.id}:${idem}`)) return json(res, 200, booking.idempotency.get(`${account.id}:${idem}`));
        const existingPayment = booking.payments.get(appointment.id); if (existingPayment) return json(res, 200, { appointment, payment: existingPayment, refund: booking.refunds.get(appointment.id) ?? null });
        const payment = { id: catalogId("payment"), appointmentId: appointment.id, amount: appointment.amount, status: "SUCCEEDED", createdAt: clock() }; booking.payments.set(appointment.id, payment);
        let refund = null; if (clock() > appointment.holdExpiresAt || appointment.status !== "PENDING_PAYMENT") { appointment.status = "EXPIRED"; releaseBooking(appointment); refund = { id: catalogId("refund"), appointmentId: appointment.id, amount: payment.amount, status: "REFUND_SUCCEEDED", reason: "LATE_PAYMENT" }; booking.refunds.set(appointment.id, refund); } else appointment.status = "CONFIRMED";
        const result = { appointment, payment, refund }; if (idem) booking.idempotency.set(`${account.id}:${idem}`, result); return json(res, 200, result);
      }
      const cancelRoute = route.match(/^\/api\/v1\/bookings\/([^/]+)\/cancel$/);
      if (req.method === "POST" && cancelRoute) {
        const appointment = booking.appointments.get(cancelRoute[1]); if (!appointment || appointment.patientId !== account.id) return json(res, 404, errorBody("NOT_FOUND", "登记不存在"));
        if (["CANCELLED", "EXPIRED"].includes(appointment.status)) return json(res, 200, { appointment, refund: booking.refunds.get(appointment.id) ?? null });
        appointment.status = "CANCELLED"; releaseBooking(appointment); let refund = null; if (booking.payments.has(appointment.id)) { refund = { id: catalogId("refund"), appointmentId: appointment.id, amount: appointment.amount, status: "REFUND_SUCCEEDED", reason: "PATIENT_CANCELLED" }; booking.refunds.set(appointment.id, refund); } return json(res, 200, { appointment, refund });
      }
      if (req.method === "GET" && route === "/api/v1/bookings") return json(res, 200, { appointments: [...booking.appointments.values()].filter((item) => item.patientId === account.id).map((item) => ({ ...item, payment: booking.payments.get(item.id) ?? null, refund: booking.refunds.get(item.id) ?? null })) });
      const checkinRoute = route.match(/^\/api\/v1\/reception\/check-in\/([^/]+)$/);
      if (req.method === "POST" && checkinRoute) {
        const appointment = booking.appointments.get(checkinRoute[1]); if (!appointment || appointment.patientId !== account.id) return json(res, 404, errorBody("NOT_FOUND", "预约不存在")); const schedule = catalog.schedules.get(appointment.scheduleId); const now = clock(); const window = checkinWindow(schedule);
        if (appointment.status !== "CONFIRMED") return json(res, 409, errorBody("INVALID_APPOINTMENT", "预约当前不可签到")); if (now < window.start || now >= window.end) return json(res, 409, errorBody("CHECKIN_CLOSED", "当前不在签到窗口"));
        const existing = [...reception.entries.values()].find((item) => item.appointmentId === appointment.id); if (existing) return json(res, 200, { entry: existing });
        const entry = { id: catalogId("queue"), appointmentId: appointment.id, scheduleId: schedule.id, patientId: account.id, group: appointment.source === "WALK_IN" ? 2 : 1, enqueueSeq: ++reception.sequence, status: "WAITING", restoreCount: 0, checkedInAt: now }; reception.entries.set(entry.id, entry); appointment.queueStatus = "WAITING"; return json(res, 201, { entry });
      }
      if (req.method === "POST" && route === "/api/v1/reception/walk-in") {
        if (account.role !== "patient") return json(res, 403, errorBody("FORBIDDEN", "仅患者可现场挂号")); const schedule = catalog.schedules.get(String(data.scheduleId ?? "")); const now = clock(); const window = schedule && sessionWindow(schedule); const cutoff = schedule && checkinWindow(schedule).end;
        const localDate = new Date(now + 8 * 60 * 60_000).toISOString().slice(0, 10); if (!schedule?.active || schedule.mode !== "OFFLINE" || schedule.date !== localDate || now < window.start || now >= cutoff) return json(res, 409, errorBody("WALK_IN_CLOSED", "现场挂号当前不可用"));
        const guardKey = `${account.id}:${schedule.date}:${schedule.halfDay}`; if (booking.guards.has(guardKey)) return json(res, 409, errorBody("HALF_DAY_CONFLICT", "同一日期半天已有有效登记")); if (schedule.remaining < 1) return json(res, 409, errorBody("SLOT_UNAVAILABLE", "号源不可用"));
        const appointment = { id: catalogId("appointment"), patientId: account.id, scheduleId: schedule.id, date: schedule.date, halfDay: schedule.halfDay, mode: schedule.mode, amount: schedule.fee, status: "CONFIRMED", source: "WALK_IN", createdAt: now, released: false, guardKey }; schedule.remaining -= 1; booking.appointments.set(appointment.id, appointment); booking.guards.set(guardKey, appointment); const payment = { id: catalogId("payment"), appointmentId: appointment.id, amount: appointment.amount, status: "SUCCEEDED", createdAt: now }; booking.payments.set(appointment.id, payment); return json(res, 201, { appointment, payment });
      }
      const clinicalRoute = route.match(/^\/api\/v1\/clinical\/appointments\/([^/]+)(?:\/(materials|messages|start|result|corrections))?$/);
      if (clinicalRoute) {
        const appointment = booking.appointments.get(clinicalRoute[1]); if (!appointment || (appointment.patientId !== account.id && account.role !== "doctor")) return json(res, 404, errorBody("NOT_FOUND", "问诊不存在")); const schedule = catalog.schedules.get(appointment.scheduleId); const assignedDoctor = doctorForAccount(account.id); const entry = [...reception.entries.values()].find((item) => item.appointmentId === appointment.id);
        const isAssignedDoctor = account.role === "doctor" && assignedDoctor?.id === schedule?.doctorId;
        if (clinicalRoute[2] === "materials" && req.method === "POST") {
          if (account.id !== appointment.patientId || !["CONFIRMED", "PENDING_PAYMENT"].includes(appointment.status)) return json(res, 403, errorBody("FORBIDDEN", "仅患者可在接诊前补充材料")); const text = String(data.text ?? ""); const files = Array.isArray(data.attachments) ? data.attachments : []; if (files.length > 5) return json(res, 400, errorBody("ATTACHMENT_LIMIT", "每次最多上传5份附件"));
          const allowed = new Set(["image/jpeg", "image/png", "application/pdf"]); for (const file of files) { if (!allowed.has(String(file.contentType)) || Number(file.size) > 10 * 1024 * 1024 || Number(file.size) < 0) return json(res, 400, errorBody("ATTACHMENT_INVALID", "附件格式或大小无效")); }
          const revision = { id: catalogId("material"), appointmentId: appointment.id, version: (clinical.materials.get(appointment.id)?.version ?? 0) + 1, text, createdAt: clock(), attachments: files.map((file) => { const item = { id: catalogId("attachment"), appointmentId: appointment.id, filename: String(file.filename ?? "attachment"), contentType: file.contentType, size: Number(file.size), storageKey: crypto.randomUUID() }; clinical.attachments.set(item.id, item); return item; }) }; clinical.materials.set(appointment.id, revision); return json(res, 201, { revision });
        }
        if (clinicalRoute[2] === "start" && req.method === "POST") { if (!isAssignedDoctor || entry?.status !== "CALLED") return json(res, 403, errorBody("FORBIDDEN", "医生尚未叫号或无权接诊")); entry.status = "IN_SERVICE"; appointment.encounterStatus = "IN_SERVICE"; appointment.encounterStartedAt = clock(); return json(res, 200, { encounter: { appointmentId: appointment.id, status: appointment.encounterStatus } }); }
        if (clinicalRoute[2] === "messages" && req.method === "POST") { if (entry?.status !== "IN_SERVICE" || !(account.id === appointment.patientId || isAssignedDoctor)) return json(res, 409, errorBody("ROOM_CLOSED", "诊室当前不可发送消息")); const message = { id: catalogId("message"), appointmentId: appointment.id, authorId: account.id, text: String(data.text ?? ""), createdAt: clock() }; const messages = clinical.messages.get(appointment.id) ?? []; messages.push(message); clinical.messages.set(appointment.id, messages); return json(res, 201, { message }); }
        if (clinicalRoute[2] === "result" && req.method === "POST") { if (clinical.results.has(appointment.id)) return json(res, 409, errorBody("RESULT_LOCKED", "问诊结果已结束，只能追加更正")); if (!isAssignedDoctor || entry?.status !== "IN_SERVICE") return json(res, 403, errorBody("FORBIDDEN", "仅当前接诊医生可提交结果")); const result = { id: catalogId("result"), appointmentId: appointment.id, authorId: account.id, version: 1, text: String(data.text ?? ""), createdAt: clock(), readonly: true }; clinical.results.set(appointment.id, result); entry.status = "FINISHED"; appointment.encounterStatus = "FINISHED"; appointment.status = "COMPLETED"; return json(res, 201, { result }); }
        if (clinicalRoute[2] === "corrections" && req.method === "POST") { if (!isAssignedDoctor || !clinical.results.has(appointment.id)) return json(res, 403, errorBody("FORBIDDEN", "仅结果作者可追加更正")); const correction = { id: catalogId("correction"), appointmentId: appointment.id, authorId: account.id, text: String(data.text ?? ""), createdAt: clock() }; const corrections = clinical.corrections.get(appointment.id) ?? []; corrections.push(correction); clinical.corrections.set(appointment.id, corrections); return json(res, 201, { correction }); }
        if (req.method === "GET" && !clinicalRoute[2]) { if (!(account.id === appointment.patientId || isAssignedDoctor)) return json(res, 403, errorBody("FORBIDDEN", "无权访问问诊")); return json(res, 200, { appointment, materials: clinical.materials.get(appointment.id) ?? null, messages: clinical.messages.get(appointment.id) ?? [], result: clinical.results.get(appointment.id) ?? null, corrections: clinical.corrections.get(appointment.id) ?? [] }); }
      }
      if (req.method === "GET" && route.startsWith("/api/v1/clinical/attachments/")) { const parts = route.split("/"); const content = parts[parts.length - 1] === "content"; const attachment = clinical.attachments.get(content ? parts[parts.length - 2] : parts[parts.length - 1]); if (!attachment) return json(res, 404, errorBody("NOT_FOUND", "附件不存在")); const appointment = booking.appointments.get(attachment.appointmentId); if (account.id !== appointment.patientId && !(account.role === "doctor" && doctorForAccount(account.id)?.id === catalog.schedules.get(appointment.scheduleId)?.doctorId)) return json(res, 403, errorBody("FORBIDDEN", "无权下载附件")); if (content) return json(res, 200, { attachmentId: attachment.id, storageKey: attachment.storageKey, contentType: attachment.contentType }); return json(res, 200, { attachment: { ...attachment, downloadUrl: `/api/v1/clinical/attachments/${attachment.id}/content` } }); }
      if (req.method === "POST" && route === "/api/v1/ai/facts") { if (account.role !== "admin") return json(res, 403, errorBody("FORBIDDEN", "仅管理员可维护咨询事实")); const fact = { id: catalogId("fact"), topic: String(data.topic ?? ""), content: String(data.content ?? ""), active: true }; ai.facts.set(fact.id, fact); return json(res, 201, { fact }); }
      if (req.method === "POST" && route === "/api/v1/ai/consult") { const text = String(data.text ?? ""); const personal = /症状|疼|痛|报告|化验|发烧|咳嗽/.test(text); if (personal && data.consent !== true) return json(res, 403, errorBody("CONSENT_REQUIRED", "预问诊外发前需要授权")); if (options.aiProvider === "external-failing") return json(res, 503, errorBody("AI_UNAVAILABLE", "AI 服务暂不可用")); const facts = [...ai.facts.values()].filter((item) => item.active).map((item) => item.content); const result = { id: catalogId("ai-result"), mode: personal ? "PRECONSULTATION" : "BUSINESS", status: "SUCCEEDED", provider: options.aiProvider ?? "local-mock", text: personal ? "AI 辅助摘要：请由医生进一步判断。" : (facts.join(" ") || "暂无维护的医院事实"), createdAt: clock() }; ai.results.set(result.id, result); ai.calls.push({ id: catalogId("ai-call"), resultId: result.id, authorized: !personal || data.consent === true, provider: result.provider, createdAt: clock() }); return json(res, 200, { result, disclaimer: "AI 仅供辅助参考，不能诊断或开处方" }); }
      if (req.method === "POST" && route === "/api/v1/governance/reviews") { if (!(["admin", "doctor"].includes(account.role))) return json(res, 403, errorBody("FORBIDDEN", "无审核权限")); const review = { id: catalogId("review"), subjectId: String(data.subjectId ?? ""), reason: String(data.reason ?? "").trim(), status: "APPROVED", reviewerId: account.id, createdAt: clock() }; if (!review.reason) return json(res, 400, errorBody("REASON_REQUIRED", "审核理由不能为空")); governance.changes.set(review.id, review); governance.audits.push({ action: "REVIEW", actorId: account.id, objectId: review.subjectId, reason: review.reason, at: clock() }); return json(res, 201, { review }); }
      if (req.method === "POST" && route === "/api/v1/governance/restrict") { if (account.role !== "admin") return json(res, 403, errorBody("FORBIDDEN", "仅管理员可限制展示")); const item = { id: catalogId("restriction"), subjectId: String(data.subjectId ?? ""), restricted: true, reason: String(data.reason ?? ""), createdAt: clock() }; governance.changes.set(item.id, item); governance.audits.push({ action: "RESTRICT", actorId: account.id, objectId: item.subjectId, reason: item.reason, at: clock() }); return json(res, 200, { restriction: item }); }
      if (req.method === "GET" && route === "/api/v1/events") { const after = Number(url.searchParams.get("after") ?? 0); return json(res, 200, { cursor: delivery.cursor, events: delivery.events.filter((event) => event.cursor > after) }); }
      if (req.method === "POST" && route === "/api/v1/events") { const event = { cursor: ++delivery.cursor, id: catalogId("event"), type: String(data.type ?? "NOTICE"), payload: data.payload ?? {}, createdAt: clock() }; delivery.events.push(event); delivery.notifications.push({ ...event, delivered: false }); return json(res, 201, { event }); }
      if (req.method === "GET" && route === "/api/v1/notifications") return json(res, 200, { notifications: delivery.notifications.filter((item) => item.payload?.accountId === account.id || !item.payload?.accountId) });
      if (req.method === "GET" && route === "/api/v1/reception/queue") {
        const scheduleId = url.searchParams.get("scheduleId"); const entries = [...reception.entries.values()].filter((item) => (!scheduleId || item.scheduleId === scheduleId) && ["WAITING", "CALLED", "IN_SERVICE"].includes(item.status)).sort((a, b) => a.group - b.group || a.enqueueSeq - b.enqueueSeq); return json(res, 200, { entries });
      }
      if (req.method === "POST" && route === "/api/v1/reception/call-next") {
        if (!isDoctor(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅医生可叫号")); const doctor = doctorForAccount(account.id); const schedule = catalog.schedules.get(String(data.scheduleId ?? "")); if (!doctor || !schedule || schedule.doctorId !== doctor.id) return json(res, 403, errorBody("FORBIDDEN", "无权操作该排班")); if ([...reception.entries.values()].some((item) => item.scheduleId === schedule.id && item.status === "IN_SERVICE")) return json(res, 409, errorBody("ENCOUNTER_ACTIVE", "已有患者接诊中")); const entry = [...reception.entries.values()].filter((item) => item.scheduleId === schedule.id && item.status === "WAITING").sort((a, b) => a.group - b.group || a.enqueueSeq - b.enqueueSeq)[0]; if (!entry) return json(res, 404, errorBody("QUEUE_EMPTY", "候诊队列为空")); entry.status = "CALLED"; entry.calledAt = clock(); return json(res, 200, { entry });
      }
      const queueAction = route.match(/^\/api\/v1\/reception\/([^/]+)\/(start|miss|restore)$/);
      if (req.method === "POST" && queueAction) {
        const entry = reception.entries.get(queueAction[1]); if (!entry) return json(res, 404, errorBody("NOT_FOUND", "候诊记录不存在")); const appointment = booking.appointments.get(entry.appointmentId); const schedule = catalog.schedules.get(entry.scheduleId); const doctor = doctorForAccount(account.id); const isAssignedDoctor = Boolean(doctor && schedule?.doctorId === doctor.id); if (!isAssignedDoctor && queueAction[2] !== "restore") return json(res, 403, errorBody("FORBIDDEN", "无权操作该候诊记录"));
        if (queueAction[2] === "start" && entry.status === "CALLED") { entry.status = "IN_SERVICE"; entry.startedAt = clock(); return json(res, 200, { entry }); }
        if (queueAction[2] === "miss" && entry.status === "CALLED") { entry.status = "MISSED"; entry.missedAt = clock(); return json(res, 200, { entry }); }
        if (queueAction[2] === "restore") { if (account.id !== appointment.patientId && !isAssignedDoctor) return json(res, 403, errorBody("FORBIDDEN", "仅本人或当前医生可恢复排队")); const now = clock(); if (entry.status !== "MISSED" || entry.restoreCount > 0) return json(res, 409, errorBody("RESTORE_UNAVAILABLE", "该预约不能再次恢复排队")); if (now >= sessionWindow(schedule).end) return json(res, 409, errorBody("SESSION_ENDED", "本次门诊时段已经结束，无法恢复排队")); entry.status = "WAITING"; entry.group = 3; entry.restoreCount = 1; entry.enqueueSeq = ++reception.sequence; entry.restoredAt = now; return json(res, 200, { entry }); }
        return json(res, 409, errorBody("INVALID_QUEUE_STATE", "候诊状态不允许该操作"));
      }
      if (req.method === "POST" && route === "/api/v1/admin/hospital") {
        if (!isAdmin(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅管理员可维护医院信息"));
        catalog.hospital = { ...catalog.hospital, name: String(data.name ?? catalog.hospital.name).trim(), address: String(data.address ?? "").trim(), process: String(data.process ?? "").trim() };
        return json(res, 200, { hospital: catalog.hospital });
      }
      if (req.method === "POST" && route === "/api/v1/admin/departments") {
        if (!isAdmin(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅管理员可维护科室"));
        const name = String(data.name ?? "").trim(); if (!name) return json(res, 400, errorBody("INVALID_INPUT", "科室名称不能为空"));
        if ([...catalog.departments.values()].some((item) => item.name === name)) return json(res, 409, errorBody("DUPLICATE_DEPARTMENT", "科室已存在"));
        const department = { id: catalogId("dept"), name, description: String(data.description ?? "").trim(), active: true }; catalog.departments.set(department.id, department);
        return json(res, 201, { department });
      }
      if (req.method === "POST" && route === "/api/v1/admin/doctors") {
        if (!isAdmin(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅管理员可维护医生"));
        const identifier = String(data.identifier ?? "").trim().toLowerCase(); const doctorAccount = accounts.get(identifier); const department = catalog.departments.get(String(data.departmentId ?? ""));
        if (!doctorAccount || doctorAccount.role !== "doctor") return json(res, 400, errorBody("DOCTOR_ACCOUNT_REQUIRED", "需要已注册的医生账号"));
        if (!department) return json(res, 400, errorBody("DEPARTMENT_REQUIRED", "科室不存在"));
        if ([...catalog.doctors.values()].some((item) => item.accountId === doctorAccount.id)) return json(res, 409, errorBody("DOCTOR_EXISTS", "医生资料已存在"));
        const doctor = { id: catalogId("doctor"), accountId: doctorAccount.id, identifier, displayName: String(data.displayName ?? identifier), departmentId: department.id, certified: false, status: "PENDING" };
        catalog.doctors.set(doctor.id, doctor); return json(res, 201, { doctor });
      }
      const certify = route.match(/^\/api\/v1\/admin\/doctors\/([^/]+)\/certify$/);
      if (req.method === "POST" && certify) {
        if (!isAdmin(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅管理员可审核资质"));
        const doctor = catalog.doctors.get(certify[1]); if (!doctor) return json(res, 404, errorBody("NOT_FOUND", "医生不存在"));
        doctor.certified = Boolean(data.certified ?? true); doctor.status = doctor.certified ? "CERTIFIED" : "REJECTED"; return json(res, 200, { doctor });
      }
      if (req.method === "POST" && route === "/api/v1/admin/schedules") {
        if (!isAdmin(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅管理员可发布排班"));
        const doctor = catalog.doctors.get(String(data.doctorId ?? "")); const department = catalog.departments.get(String(data.departmentId ?? doctor?.departmentId ?? ""));
        const halfDay = String(data.halfDay ?? "").toUpperCase(); const mode = String(data.mode ?? "").toUpperCase(); const date = String(data.date ?? ""); const capacity = Number(data.capacity);
        if (!doctor || !department || doctor.departmentId !== department.id || !doctor.certified) return json(res, 400, errorBody("DOCTOR_NOT_CERTIFIED", "医生必须属于该科室且已认证"));
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !["AM", "PM"].includes(halfDay) || !["ONLINE", "OFFLINE"].includes(mode) || !Number.isInteger(capacity) || capacity < 1) return json(res, 400, errorBody("INVALID_SCHEDULE", "排班日期、时段、方式或号源无效"));
        const duplicate = [...catalog.schedules.values()].some((item) => item.doctorId === doctor.id && item.date === date && item.halfDay === halfDay && item.mode === mode && item.active);
        if (duplicate) return json(res, 409, errorBody("DUPLICATE_SCHEDULE", "重复发布排班"));
        const schedule = { id: catalogId("schedule"), doctorId: doctor.id, departmentId: department.id, date, halfDay, mode, capacity, remaining: capacity, fee: Number(data.fee ?? 0), ...scheduleWindow(halfDay), active: true };
        catalog.schedules.set(schedule.id, schedule); return json(res, 201, { schedule });
      }
      if (req.method === "GET" && route === "/api/v1/catalog") {
        const date = url.searchParams.get("date"); const departmentId = url.searchParams.get("departmentId"); const mode = url.searchParams.get("mode");
        const schedules = [...catalog.schedules.values()].filter((item) => item.active && (!date || item.date === date) && (!departmentId || item.departmentId === departmentId) && (!mode || item.mode === mode) && item.remaining > 0).map((item) => ({ ...item, doctor: catalog.doctors.get(item.doctorId), department: catalog.departments.get(item.departmentId) })).filter((item) => item.doctor?.certified && item.department?.active).map(({ doctor, ...item }) => ({ ...item, doctor: { id: doctor.id, displayName: doctor.displayName } }));
        return json(res, 200, { hospital: catalog.hospital, departments: [...catalog.departments.values()].filter((item) => item.active), schedules });
      }
      if (req.method === "GET" && route === "/api/v1/doctor/schedules") {
        if (!isDoctor(account.role)) return json(res, 403, errorBody("FORBIDDEN", "仅医生可查看个人排班"));
        const doctor = [...catalog.doctors.values()].find((item) => item.accountId === account.id); return json(res, 200, { schedules: doctor ? [...catalog.schedules.values()].filter((item) => item.doctorId === doctor.id) : [] });
      }
      if (req.method === "GET" && route === "/api/v1/profile") return json(res, 200, { ...accountView(account), permissions: [account.role] });
      if (req.method === "GET" && route === "/api/v1/admin") return account.role === "admin" ? json(res, 200, { ok: true, role: account.role }) : json(res, 403, errorBody("FORBIDDEN", "无权访问该资源"));
      if (req.method === "GET" && route === "/api/v1/doctor") return account.role === "doctor" ? json(res, 200, { ok: true, role: account.role }) : json(res, 403, errorBody("FORBIDDEN", "无权访问该资源"));
      return json(res, 404, errorBody("NOT_FOUND", "资源不存在"));
    } catch (error) {
      if (error instanceof RedisUnavailable) return json(res, 503, errorBody("REDIS_UNAVAILABLE", "验证码服务暂不可用，请稍后重试"));
      if (error.status) return json(res, error.status, errorBody(error.code ?? "BAD_REQUEST", error.message));
      console.error(error); return json(res, 500, errorBody("INTERNAL_ERROR", "服务暂时不可用"));
    }
  });
  app.store = store; app.accounts = accounts; app.catalog = catalog; app.booking = booking; app.reception = reception; app.clinical = clinical; app.ai = ai; app.governance = governance; app.delivery = delivery; return app;
}

function serveFrontend(res, relative) {
  const target = path.resolve(frontendRoot, relative); if (!target.startsWith(frontendRoot) || !fs.existsSync(target)) return json(res, 404, errorBody("NOT_FOUND", "资源不存在"));
  const type = target.endsWith(".html") ? "text/html; charset=utf-8" : "text/plain; charset=utf-8"; res.writeHead(200, { "content-type": type }); res.end(fs.readFileSync(target));
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const port = Number(process.env.PORT ?? 8080); createApp().listen(port, () => console.log(`auth foundation listening on http://localhost:${port}`));
}


