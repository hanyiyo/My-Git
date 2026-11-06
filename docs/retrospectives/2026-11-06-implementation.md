# MVP implementation retrospective

## Findings, in severity order

1. **Specification drift:** the previous run substituted a Node/Map prototype for Spring Boot/MySQL/Redis because Maven was absent from PATH. Maven exists at `D:/apache-maven-3.6.3/bin/mvn.cmd`; MySQL and Redis also exist. Discover tools before declaring them unavailable. Enforce the approved stack in the default build/start and CI checks.
2. **False completion:** twelve prototype tests were treated as evidence for nine tickets. Review identified absent persistence, substitution, SSE, real attachments and Vue flows. Maintain an acceptance record per issue; require real dependency and browser tests. Keep later issues open until their own acceptance criteria pass.
3. **Ignored failures:** a shell sequence ran `git commit` after failed tests because native exit codes were not checked. Use a single fail-fast verification script with explicit exit checks; CI runs that same entry point.
4. **Issue damage risk:** closing attempts also replaced issue descriptions with a completion claim. The connector displayed unsubmitted forms, so success was never established. Preserve issue bodies and use separate comments for verification evidence. No close action is justified by the prototype.
5. **Premature agent interruption:** implementers were interrupted after brief waits and replaced by rushed direct edits. Use bounded, meaningful work units and concrete blockers; review the resulting diff and checks before integration.
6. **Environment mismatch:** Windows PowerShell misread UTF-8 documentation tests. Run them with PowerShell 7 (`pwsh`) and propagate failures.

## Changes to the environment

`docs/agents/implementation-gate.md` defines the delivery gate; `scripts/verify.ps1` and CI enforce the mechanical checks. `docs/acceptance/issue-2.md` maps the first ticket to executable evidence. The existing prototype remains historical evidence only and must not be the shipped runtime.

The current recovery starts with issue #2. Completion of #2 does not imply completion of #3–#10. No automatic push is part of the local verification gate.
