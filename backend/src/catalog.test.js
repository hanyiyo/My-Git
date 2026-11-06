import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "./app.js";

async function runningApp() {
  const app = createApp({ exposeCaptchaCode: true });
  await new Promise((resolve) => app.listen(0, resolve));
  return { app, base: `http://127.0.0.1:${app.address().port}`, close: () => new Promise((resolve) => app.close(resolve)) };
}
async function json(base, route, options = {}) {
  const response = await fetch(base + route, { headers: { "content-type": "application/json", ...(options.headers ?? {}) }, ...options, body: options.body && JSON.stringify(options.body) });
  return { response, body: response.status === 204 ? null : await response.json() };
}
async function login(base, identifier, password, browserId) {
  const registered = await json(base, "/api/v1/auth/register", { method: "POST", body: { identifier, password, role: identifier.startsWith("admin") ? "admin" : "doctor" } });
  assert.equal(registered.response.status, 201);
  const challenge = await json(base, "/api/v1/auth/captcha", { method: "POST", body: { browserId } });
  const result = await json(base, "/api/v1/auth/login", { method: "POST", body: { identifier, password, browserId, challengeId: challenge.body.challengeId, captcha: challenge.body.code } });
  assert.equal(result.response.status, 200); return result.body.accessToken;
}

test("admin publishes certified doctor schedule and patient query filters valid resources", async (t) => {
  const runtime = await runningApp(); t.after(runtime.close);
  const admin = await login(runtime.base, "admin-catalog@example.test", "correct horse", "admin-catalog");
  const doctorAccount = await json(runtime.base, "/api/v1/auth/register", { method: "POST", body: { identifier: "doctor-catalog@example.test", password: "correct horse", role: "doctor" } });
  assert.equal(doctorAccount.response.status, 201);
  const department = await json(runtime.base, "/api/v1/admin/departments", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { name: "全科", description: "常见病" } });
  assert.equal(department.response.status, 201);
  const doctor = await json(runtime.base, "/api/v1/admin/doctors", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { identifier: "doctor-catalog@example.test", departmentId: department.body.department.id, displayName: "李医生" } });
  const certified = await json(runtime.base, `/api/v1/admin/doctors/${doctor.body.doctor.id}/certify`, { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { certified: true } });
  assert.equal(certified.body.doctor.status, "CERTIFIED");
  const schedule = await json(runtime.base, "/api/v1/admin/schedules", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { doctorId: doctor.body.doctor.id, departmentId: department.body.department.id, date: "2030-01-02", halfDay: "AM", mode: "ONLINE", capacity: 3, fee: 1200 } });
  assert.equal(schedule.response.status, 201); assert.equal(schedule.body.schedule.remaining, 3);
  const duplicate = await json(runtime.base, "/api/v1/admin/schedules", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { doctorId: doctor.body.doctor.id, departmentId: department.body.department.id, date: "2030-01-02", halfDay: "AM", mode: "ONLINE", capacity: 3 } });
  assert.equal(duplicate.response.status, 409);
  const catalog = await json(runtime.base, "/api/v1/catalog?date=2030-01-02");
  assert.equal(catalog.response.status, 200); assert.equal(catalog.body.schedules.length, 1); assert.equal(catalog.body.schedules[0].doctor.displayName, "李医生");
});

test("schedule rejects uncertified doctors and invalid capacity", async (t) => {
  const runtime = await runningApp(); t.after(runtime.close);
  const admin = await login(runtime.base, "admin-invalid@example.test", "correct horse", "admin-invalid");
  const doctorAccount = await json(runtime.base, "/api/v1/auth/register", { method: "POST", body: { identifier: "doctor-invalid@example.test", password: "correct horse", role: "doctor" } });
  assert.equal(doctorAccount.response.status, 201);
  const department = await json(runtime.base, "/api/v1/admin/departments", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { name: "儿科" } });
  const doctor = await json(runtime.base, "/api/v1/admin/doctors", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { identifier: "doctor-invalid@example.test", departmentId: department.body.department.id } });
  const result = await json(runtime.base, "/api/v1/admin/schedules", { method: "POST", headers: { authorization: `Bearer ${admin}` }, body: { doctorId: doctor.body.doctor.id, departmentId: department.body.department.id, date: "2030-01-03", halfDay: "NOON", mode: "ONLINE", capacity: 0 } });
  assert.equal(result.response.status, 400);
});
