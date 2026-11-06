# Issue delivery gate

Before implementation, read the issue, `docs/design/mvp-spec.md`, the relevant ADRs and `GLOSSARY.md`. Record the acceptance cases and their public test seams in the issue's acceptance record under `docs/acceptance/`.

The shipped runtime is Spring Boot, MySQL, Redis and one Vue application. Discover installed tools by absolute path as well as PATH. Missing tools require installation or an explicit blocker; they do not authorize a different stack. Keep experimental implementations outside the default start/test commands.

Run `pwsh -File scripts/verify.ps1` and check its exit status before committing a completed change. The gate must include real MySQL/Redis HTTP integration tests, Vue type checking/build, browser acceptance and existing document checks. A dependency outage or skipped integration test is an incomplete verification.

For each acceptance criterion, record the test, observed result, and remaining limitation. A passing subset of tests is not completion. Review both standards and specification before closing an issue. Preserve the issue body; add evidence in a comment and close only that verified issue. Review findings remain open until fixed or explicitly accepted by the user.

Use a named branch based on the integration branch and preserve unrelated work. Let implementers finish or report a blocker; elapsed time alone is not evidence that an agent is stuck. Commit only after the required checks pass. Push and merge to the default branch follow the user's publishing instructions.
