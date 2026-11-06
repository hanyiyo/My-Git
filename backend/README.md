# Spring Boot identity foundation

The shipped backend is `src/main/java/cn/medical/`. Identity owns accounts, roles, pending doctor credentials and revocable refresh sessions in MySQL. Flyway applies migrations from `src/main/resources/db/migration/`. Redis Lua scripts atomically issue/consume browser-bound captcha challenges and increment expiry-bound rate limits.

Run and verification commands, environment variables, test-service isolation and HTTPS cookie settings are documented in the repository README. The previous Node service is retained under `prototypes/node/` only.

HTTP entry points are `/api/v1/auth/{csrf,captcha,register,login,refresh,logout}`, `/api/v1/profile`, role-specific `/api/v1/{patient,doctor,admin}/home`, and protected administrator account provisioning/status operations. Writes require the CSRF header/cookie pair. Login returns a short JWT; refresh credentials stay in HttpOnly/SameSite cookies and only their hashes are persisted. Every protected request checks the current account and revocable session in MySQL.

Doctor registration produces `PENDING` credentials. Certification, catalog, schedules and later business flows belong to subsequent issues and have not been implemented in Spring Boot here.
