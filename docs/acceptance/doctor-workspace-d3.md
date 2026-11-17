# D3 医生诊室与持久化接诊草稿验收

对应 GitHub 任务：[#26 医生诊室与持久化接诊草稿](https://github.com/hanyiyo/My-Git/issues/26)。本记录只覆盖 D3；D4 的结构化结果提交、库存事务和药费订单仍由后续任务交付。

## 验收案例与公开边界

| 案例 | 公开边界 | 结果 |
| --- | --- | --- |
| 线上诊室显示本次授权图文和附件，线下隐藏聊天与 AI | `GET /doctor/clinical/{id}`、`GET/POST /doctor/clinical/{id}/ai` | 已通过 `WorkflowHttpTest.clinicalMessagesResultsAndCorrectionsRespectOwnershipAndReadOnlyBoundary` |
| 草稿按预约和负责医生隔离 | `GET/PUT /doctor/clinical/{id}/draft` | 已通过 `WorkflowHttpTest.clinicalDraftsPersistByAppointmentAndRejectStaleVersions` |
| 五项病历和开药输入可保存不完整草稿 | `PUT /doctor/clinical/{id}/draft` | 已通过；版本 0→1，字段可为空字符串 |
| 切换患者、返回队列和刷新后恢复草稿 | 医生正式诊室页面 | 已实现；页面加载诊室时恢复服务端草稿 |
| 版本冲突可见且迟到保存不能覆盖新版本 | `PUT /doctor/clinical/{id}/draft` | 已通过；返回 `DRAFT_CONFLICT` 并保留当前输入 |
| 保存失败保留当前输入并显示未保存 | 医生正式诊室页面 | 已实现；保存失败显示“草稿未保存”和重试入口 |
| 跨医生读取和保存被拒绝 | 同一草稿接口跨账户请求 | 已通过；返回 404 |
| 患者共享撤回后医生材料不可见，草稿仍与患者结果分离 | 既有材料共享接口 + 草稿接口 | 已通过既有共享撤回回归；草稿使用独立表保存 |
| 375px 和键盘可完成草稿编辑、重试和预览 | 正式医生浏览器验收 | 前端生产构建通过；正式浏览器门禁随完整 `verify.ps1` 验证 |

## 计划验证

- `WorkflowHttpTest`：补充草稿保存、恢复、冲突、跨医生和线上/线下可见性边界；继续使用真实 MySQL/Redis 与可调时钟。新增用例均通过。
- `frontend/tests/doctor-home.spec.ts`：补充正式诊室五项病历编辑、自动保存提示、冲突保留输入和移动端布局。
- `BrowserAcceptanceTest`：验证从真实队列进入诊室、刷新恢复草稿、离开不结束接诊和线下隐藏聊天/AI。
- `pwsh -NoProfile -File scripts/verify.ps1`：提交前运行完整门禁。

## 当前限制

D3 不交付正式病历提交、药品库存扣减、药费订单、AI 摘要授权、排班申请、通知和个人资料编辑；这些能力分别由 D4–D7 实现。
