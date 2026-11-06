import http from "node:http";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
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
  const catalogId = (prefix) => `${prefix}-${crypto.randomUUID()}`;
  const scheduleWindow = (halfDay) => halfDay === "AM" ? { start: "08:00", end: "12:00" } : { start: "14:00", end: "18:00" };
  const isAdmin = (role) => role === "admin";
  const isDoctor = (role) => role === "doctor";
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
  app.store = store; app.accounts = accounts; app.catalog = catalog; return app;
}

function serveFrontend(res, relative) {
  const target = path.resolve(frontendRoot, relative); if (!target.startsWith(frontendRoot) || !fs.existsSync(target)) return json(res, 404, errorBody("NOT_FOUND", "资源不存在"));
  const type = target.endsWith(".html") ? "text/html; charset=utf-8" : "text/plain; charset=utf-8"; res.writeHead(200, { "content-type": type }); res.end(fs.readFileSync(target));
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const port = Number(process.env.PORT ?? 8080); createApp().listen(port, () => console.log(`auth foundation listening on http://localhost:${port}`));
}


