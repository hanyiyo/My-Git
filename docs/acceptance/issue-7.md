# #7 MVP-06 AI 业务助手与文本预问诊

依据 GitHub #7、`mvp-spec`、`GLOSSARY.md`、ADR-0006、ADR-0008 和 ADR-0010。实现只分析文本；附件仍由临床模块保存供医生查看。

| 验收规则 | 实现与 seam | 当前结果 | 限制 |
| --- | --- | --- | --- |
| 普通业务助手只回答医院地址、流程和科室职责 | `GET /api/v1/assistant/business`；来源只取 `hospital` 与启用科室 | 已实现本地事实回答；未知主题返回 `BUSINESS_TOPIC_UNSUPPORTED` | 集成 HTTP 验证延后 |
| 症状分析前必须确认授权，拒绝后仍可预约 | `POST /patient/appointments/{id}/ai/consents` + `.../ai/preconsultations`；患者页显示授权范围并勾选 | 已实现；未接受授权时不创建材料或任务 | 集成 HTTP 验证延后 |
| 输出持续时间、科室建议、追问和风险提示，不作诊断/处方 | `LocalAiTextProviderTest` | 2 条本地 Provider 单测通过；输出带安全边界文案 | 规则型本地 Provider，不分析图片/PDF |
| 推荐只引用认证医生和有效排班 | `AiService.recommendations` 调用目录服务过滤医生资质、科室和可用排班 | 已实现，返回真实 doctor/schedule ID | 集成数据验证延后 |
| 医生可看摘要，材料变化后旧摘要标过时 | `/doctor/clinical/{id}/ai`；以结果关联的 material 版本与最新材料比较 | 已实现；医生页展示 Provider、版本和过时状态 | 集成 HTTP 验证延后 |
| Provider 失败显示不可用且调用可追踪，不静默切模拟 | `ai_task`、`ai_call_log` 持久化；`EXTERNAL_TEXT` 失败不回退 | 已实现失败状态/错误码和成功、失败调用日志 | 外部 Provider 本身未接入，按 ADR 默认关闭 |

## 本轮门禁

- 后端 `mvn -f backend/pom.xml -DskipTests test-compile`：通过。
- 本批单元测试：`ReceptionServiceTest`、`ClinicalAttachmentValidatorTest`、`LocalAiTextProviderTest`、`AiServiceTest` 全部通过。
- 前端 `npm --prefix frontend run build`（含 `vue-tsc --noEmit`）：通过。
- `git diff --check`：通过。

按当前批次策略未执行 MySQL/Redis HTTP 集成、浏览器 E2E、全量 `npm test` 或 `scripts/verify.ps1`；待一批 issue 合并前统一执行。完整双轴审查和延后门禁通过前不关闭 issue。
