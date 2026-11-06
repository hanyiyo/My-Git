# #2 MVP-01 身份认证与运行底座

Source: GitHub issue #2 previously read in this chat, `docs/design/mvp-spec.md`, ADR-0001, ADR-0013, ADR-0014 and `docs/design/login-security.md`. Live re-read is unavailable: installed gh CLI has no authenticated session; public API is rate-limited. Preserve the original issue body and state until authenticated synchronization is available.

## Acceptance boundaries

Use HTTP and a real browser against Spring Boot with actual MySQL and Redis. Inject only random captcha answers in the test classpath; the shipped application cannot select a fixed answer. MySQL/Flyway owns accounts, roles and revocable refresh sessions. Redis owns TTL challenges and throttling only.

| Issue criterion | Executable evidence | Observed result | Limitations |
| --- | --- | --- | --- |
| Patient/doctor/admin registration, login, logout, refresh | `IdentityHttpTest.patientRegistersLogsInRefreshesAndRevokesSession`, `administratorCanProvisionAndDisableAccountsWithoutTrustingStaleJwtRoles`, browser acceptance | Passed; rotated/revoked refresh sessions rejected, account disabling rejects existing access | Administrator creation requires bootstrap or an authenticated administrator |
| Redis browser-bound one-use graphical captcha | Wrong answer/password, expiry/refresh/reuse, foreign browser and concurrent-consumption HTTP tests; delayed-image browser case | Passed; concurrent consumption permits one login; late image cannot restore invalidated challenge | Browser recovery obtains a new challenge after rejecting the late image |
| Configurable rate limit and unavailable Redis fail closed | HTTP rate-limit and real Redis-unreachable recovery tests; browser 503/retry case | Passed; generation/login limits reject excess requests; outage returns 503 without ending existing sessions | Browser 503 is injected at the HTTP boundary; actual dependency outage is tested through HTTP integration |
| Role routing and backend RBAC, resource ownership | HTTP privilege/ownership tests and Vue role-route browser test | Passed; public admin registration, cross-account reads and wrong-role routes rejected | Role mutation is outside #2; disablement is covered |
| Migration and runnable test environment | Fresh MySQL schema, real Redis, Flyway migration on context startup; restart HTTP recovery test | Passed; accounts and refresh sessions survive application restart; lost Redis challenge rejected | Local dependency versions differ from CI; see below |
| Vue login external behavior | `BrowserAcceptanceTest` launches six Playwright cases against Vite and the real Spring Boot app | Passed; patient, doctor, administrator, mobile, unavailable-login retry and delayed captcha response paths | Local browser is installed Chrome; CI Chromium has not run remotely |

Administrator registration is restricted to authenticated administrators; a first administrator is provisioned through explicit environment configuration, without a shared default password. Public doctor registration creates pending credentials, not certified status.

## Verification status

Local acceptance passed on 2026-11-06. `pwsh -NoProfile -File scripts/verify.ps1` exited 0: all six documentation/runtime checks passed, Vue type checking and production build passed, and Maven reported 13 tests with 0 failures, 0 errors and 0 skipped. The 13 Java tests comprise nine HTTP cases, three recovery cases and one wrapper running six real-browser cases. Playwright's last-run result was `passed` with no failed tests.

Local environment: JDK 25 targeting Java 21, MySQL 26.7.0, Redis 5.0.14.1, Node 24 and Chrome. Flyway warns that this local MySQL version is newer than its tested support; migration and restart checks passed. Surefire reports a native-output channel warning on the local JDK; the suite completed successfully. The checked-in CI matrix uses Java 21, MySQL 8.4, Redis 7.4 and Chromium, but no remote CI run is claimed.

Standards review of the complete working tree against `fe1449a` found no code-level blocker. Its delivery-record finding is resolved by this evidence. Spec review found no functional blocker; the two browser verification gaps it identified now have executable cases. Optional follow-ups are startup validation for positive configuration values and a role-change regression when role editing is implemented.

This acceptance record accompanies the #2 recovery commit on `codex/identity-foundation`. GitHub synchronization and issue closure remain pending because the CLI has no authenticated session. Preserve the original issue body. Issues #3–#10 remain unverified; historical Node prototype behavior is not evidence for their completion.
