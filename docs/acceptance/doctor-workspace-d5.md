# D5 医生症状摘要授权与版本验收

对应 GitHub 任务：[#28 医生症状摘要的授权与版本接入](https://github.com/hanyiyo/My-Git/issues/28)。

## 验收案例与公开边界

| 案例 | 公开边界 | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 患者明确选择提供方、用途和具体消息/材料范围后，医生才可生成摘要 | `POST /patient/appointments/{id}/ai/summary-consents`、`POST /doctor/clinical/{id}/ai/summarize` | `WorkflowHttpTest.doctorSummaryRequiresExactPatientScopeKeepsVersionsAndHonorsRevocation` 验证未授权时医生结果为空，精确授权后生成 `DOCTOR_SYMPTOM_SUMMARY` | 当前正式提供方为明确标注的 `LOCAL_MOCK`；外部提供方未接入真实服务 |
| 新增未授权消息不扩大旧摘要，扩大授权生成新版本 | 医生 AI 结果接口、患者/医生正式诊室页面 | HTTP 测试验证新增消息不进入旧摘要，新增授权后版本号递增为 2；浏览器用例“患者精确授权医生症状摘要、版本隔离并可撤回”验证勾选消息和材料、医生重新生成 | 版本只在同一预约内递增，未提供跨预约摘要合并 |
| 患者撤回后医生不能继续读取或重新生成 | `POST /patient/appointments/{id}/ai/summary-consents/{consentId}/revoke`、医生 AI 结果接口 | HTTP 测试验证撤回全部授权后医生读取为空；重新生成仅能在新授权后进行 | 患者历史授权记录保留用于审计，正文按当前有效授权隐藏 |
| 摘要只对负责医生可见，不自动填诊断或用药建议 | `GET /doctor/clinical/{id}/ai`、`GET /patient/appointments/{id}/ai/results`、正式医生诊室 | 摘要挂在医生端 AI 结果中，患者端不返回；生成结果使用独立 `DOCTOR_SYMPTOM_SUMMARY` 用途，不写入结构化病历字段 | 医生仍需手工填写并提交结构化病历 |
| 外部 AI 失败不阻断诊疗且不静默回退 mock | `POST /doctor/clinical/{id}/ai/summarize` with `EXTERNAL_TEXT` | HTTP 测试验证返回 503、保留失败版本、`analysis` 为空；不会调用本地 provider 回退 | 外部服务接入和重试策略属于后续工作 |

## 验证记录

- 后端定向：`AiServiceTest` 通过；`WorkflowHttpTest#doctorSummaryRequiresExactPatientScopeKeepsVersionsAndHonorsRevocation` 通过，使用真实 MySQL/Redis HTTP 边界和 Flyway V26。
- 浏览器定向：`MEDICAL_PLAYWRIGHT_GREP='患者精确授权医生症状摘要' ... -Test BrowserAcceptanceTest` 通过，覆盖患者勾选具体消息/材料、授权请求和医生重新生成。
- 提交前完整门禁：`pwsh -NoProfile -File scripts/verify.ps1` 通过（退出码 0）；后端 104 项测试全部通过，真实浏览器验收 206 项全部通过，Vue 类型检查、生产构建、架构/规格/文档检查全部通过。

## 当前限制

`LOCAL_MOCK` 仅用于开发和验收，界面显式标注提供方；外部 AI 尚未接入。撤回后的摘要记录保留版本和失败元数据，但正文只在当前有效授权范围内向负责医生展示。GitHub Issue #28 保持开放，等待统一验收关闭。
