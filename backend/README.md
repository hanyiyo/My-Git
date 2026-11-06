# MVP-01 身份认证与运行底座

This directory contains the executable auth seam for the MVP. The host does not have Maven/Gradle, so the local test/runtime adapter is dependency-free Node.js; the public HTTP contract is kept compatible with the planned Spring Boot module and the Vue login flow is in `frontend/src/App.vue`.

## Run

```text
npm test
npm start
```

The service listens on `http://localhost:8080`. `POST /api/v1/auth/captcha` issues a browser-bound, single-use challenge. In production the answer is represented only by the SVG image; tests opt into `exposeCaptchaCode` to exercise the public behavior without OCR. Redis failures fail closed through the `MemoryRedis` adapter and can be replaced by a Redis implementation at this seam. `backend/db/migration/V1__identity.sql` is the Flyway-compatible identity schema for the Spring Boot deployment.

Endpoints: registration, captcha, login, refresh token rotation, logout, profile, and role-protected `/api/v1/admin` and `/api/v1/doctor` routes. Errors use `{ "error": { "code", "message" } }`.
