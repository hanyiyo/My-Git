# #9 MVP-08 医疗内容访问控制与审计

依据 GitHub #9、ADR-0004、ADR-0010、`mvp-spec` 和 Q64。

| 验收规则 | 实现 seam | 当前结果 | 限制 |
| --- | --- | --- | --- |
| 审核人员查看正文/附件前必须独立授权并填写理由 | `POST /admin/medical/reviews` 生成30分钟授权；内容读取必须携带授权 ID | 已实现；无授权或过期返回可解释错误 | HTTP 集成延后 |
| 患者、医生、科主任、管理员按职责访问 | 临床医生视图只返回当前预约且仍共享的材料；管理员使用独立审核入口 | 已实现；替诊后原医生由预约归属校验拒绝 | 集成越权矩阵延后 |
| 问题内容限制展示但保留原文 | `medical_content_flag` + 审核内容响应脱敏 | 已实现 `LIMITED/CLEARED` 标记，原始记录不删除 | 审核 UI 延后 |
| 附件、授权、审核和敏感操作写审计 | `audit_event`；审核授权、查看、标记和共享撤回均留痕 | 已实现；`GET /admin/medical/audits/{objectId}` 查询 | 审计不可变存储后续部署加固 |
| 撤回共享后立即拒绝后续医生读取 | `POST /patient/appointments/{id}/clinical/sharing/revoke`；材料 `shared=false`，下载再次校验 | 已实现；历史材料保留，医生视图和下载返回 `SHARING_REVOKED` | HTTP 集成延后 |

## 本轮门禁

- 后端 `mvn -f backend/pom.xml -DskipTests test-compile`：通过。
- `ReviewServiceTest`：通过，覆盖审核理由和过期授权时间。
- 前端 `npm --prefix frontend run build`（含 `vue-tsc --noEmit`）：通过。
- `git diff --check`：通过。

按当前批次策略未执行 MySQL/Redis HTTP 集成、浏览器 E2E、全量 `npm test` 或 `scripts/verify.ps1`；待一批 issue 合并前统一执行。完整双轴审查和延后门禁通过前不关闭 issue。

## 批次完整门禁补充（2026-11-07）

批次修复完成后重新执行 `pwsh -NoProfile -File scripts/verify.ps1` 并退出 0：文档/架构检查、Vue 类型检查与生产构建、MySQL/Redis HTTP 集成、浏览器验收及后端 45 条测试全部通过（0 失败、0 错误、0 跳过）。医疗审核、临床访问控制和审计相关测试均包含在全量结果中；本记录此前的“延后”描述仅反映实现当时的阶段性门禁。
