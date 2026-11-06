# 注意事项

- 每次改动完成后，都必须创建一个对应的Git commit，以便后续追踪回滚。
- 每次改动后，都必须编写或更新相关测试，并在交付给用户前，确保所有测试和验证全部通过。

## Agent skills

Implementation and issue completion: read `docs/agents/implementation-gate.md` before changing code or closing an issue.

### Issue tracker

Issues live in GitHub Issues and are managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default canonical triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository. See `docs/agents/domain.md`.
