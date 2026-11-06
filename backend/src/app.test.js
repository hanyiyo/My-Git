import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "./app.js";

async function runningApp(options = {}) {
  const app = createApp({ exposeCaptchaCode: true, ...options });
  await new Promise((resolve) => app.listen(0, resolve));
  return { app, base: `http://127.0.0.1:${app.address().port}`, close: () => new Promise((resolve) => app.close(resolve)) };
}
async function json(base, path, options = {}) {
  const response = await fetch(base + path, { headers: { "content-type": "application/json", ...(options.headers ?? {}) }, ...options, body: options.body && JSON.stringify(options.body) });
  return { response, body: response.status === 204 ? null : await response.json() };
}
async function captcha(base, browserId) {
  const result = await json(base, "/api/v1/auth/captcha", { method: "POST", body: { browserId } });
  assert.equal(result.response.status, 201);
  assert.match(result.body.image, /^data:image\/svg\+xml,/);
  return result.body;
}

test("patient can register, login with one-time captcha, inspect profile, refresh, and logout", async (t) => {
  const runtime = await runningApp(); t.after(runtime.close); const browserId = "browser-patient";
  const registered = await json(runtime.base, "/api/v1/auth/register", { method: "POST", body: { identifier: "patient@example.test", password: "correct horse", role: "patient" } });
  assert.equal(registered.response.status, 201);
  const challenge = await captcha(runtime.base, browserId);
  const login = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "patient@example.test", password: "correct horse", challengeId: challenge.challengeId, captcha: challenge.code, browserId } });
  assert.equal(login.response.status, 200); assert.equal(login.body.account.role, "patient");
  const profile = await json(runtime.base, "/api/v1/profile", { headers: { authorization: `Bearer ${login.body.accessToken}` } });
  assert.equal(profile.response.status, 200); assert.equal(profile.body.identifier, "patient@example.test");
  const refreshed = await json(runtime.base, "/api/v1/auth/refresh", { method: "POST", body: { refreshToken: login.body.refreshToken } });
  assert.equal(refreshed.response.status, 200);
  const logout = await json(runtime.base, "/api/v1/auth/logout", { method: "POST", headers: { authorization: `Bearer ${refreshed.body.accessToken}` }, body: { refreshToken: refreshed.body.refreshToken } });
  assert.equal(logout.response.status, 204);
  const afterLogout = await json(runtime.base, "/api/v1/profile", { headers: { authorization: `Bearer ${refreshed.body.accessToken}` } });
  assert.equal(afterLogout.response.status, 401);
});

test("captcha refresh invalidates old challenge and wrong password consumes it", async (t) => {
  const runtime = await runningApp(); t.after(runtime.close);
  await json(runtime.base, "/api/v1/auth/register", { method: "POST", body: { identifier: "doctor@example.test", password: "secret123", role: "doctor" } });
  const first = await captcha(runtime.base, "browser-doctor"); const second = await captcha(runtime.base, "browser-doctor");
  const old = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "doctor@example.test", password: "secret", challengeId: first.challengeId, captcha: first.code, browserId: "browser-doctor" } });
  assert.equal(old.response.status, 401);
  const wrong = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "doctor@example.test", password: "wrong", challengeId: second.challengeId, captcha: second.code, browserId: "browser-doctor" } });
  assert.equal(wrong.response.status, 401);
  const reused = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "doctor@example.test", password: "secret123", challengeId: second.challengeId, captcha: second.code, browserId: "browser-doctor" } });
  assert.equal(reused.response.status, 401);
});

test("captcha enforces browser binding, rate limit, and Redis outage fails closed", async (t) => {
  const runtime = await runningApp({ captchaLimit: 2 }); t.after(runtime.close);
  const one = await captcha(runtime.base, "browser-a");
  const mismatch = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "missing", password: "x", challengeId: one.challengeId, captcha: one.code, browserId: "browser-b" } });
  assert.equal(mismatch.response.status, 401);
  await captcha(runtime.base, "browser-a");
  const limited = await json(runtime.base, "/api/v1/auth/captcha", { method: "POST", body: { browserId: "browser-a" } });
  assert.equal(limited.response.status, 429);
  runtime.app.store.available = false;
  const unavailable = await json(runtime.base, "/api/v1/auth/captcha", { method: "POST", body: { browserId: "browser-c" } });
  assert.equal(unavailable.response.status, 503);
});

test("RBAC blocks cross-role access", async (t) => {
  const runtime = await runningApp(); t.after(runtime.close);
  await json(runtime.base, "/api/v1/auth/register", { method: "POST", body: { identifier: "admin@example.test", password: "secret123", role: "admin" } });
  const challenge = await captcha(runtime.base, "browser-admin");
  const login = await json(runtime.base, "/api/v1/auth/login", { method: "POST", body: { identifier: "admin@example.test", password: "secret123", challengeId: challenge.challengeId, captcha: challenge.code, browserId: "browser-admin" } });
  const doctorOnly = await json(runtime.base, "/api/v1/doctor", { headers: { authorization: `Bearer ${login.body.accessToken}` } });
  assert.equal(doctorOnly.response.status, 403);
  const adminOnly = await json(runtime.base, "/api/v1/admin", { headers: { authorization: `Bearer ${login.body.accessToken}` } });
  assert.equal(adminOnly.response.status, 200);
});

