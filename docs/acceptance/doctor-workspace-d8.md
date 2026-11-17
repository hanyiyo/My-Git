# D8 医生工作区完整验收与原型收尾

对应 GitHub 任务：[#31 医生工作区完整验收与原型收尾](https://github.com/hanyiyo/My-Git/issues/31)。

## 验收案例与公开边界

| 案例 | 公开边界 | 验证与观察结果 | 限制 |
| --- | --- | --- | --- |
| 患者挂号、支付、签到后进入医生队列，医生接诊并提交结构化病历，患者只读查看结果和独立药费 | `/patient/appointments`、`/patient/orders`、`/doctor/reception`、`/doctor/clinical`、`/patient/orders/{id}/clinical` | `frontend/tests/workflows.spec.ts` 的双向接诊、结构化病历和药费浏览器流程使用真实 HTTP、MySQL、Redis；患者结果在医生提交后只读，药费订单独立支付 | 仅使用模拟支付和仓库内本地 AI/时钟适配器 |
| 患者材料共享、医生摘要授权和撤回不越权 | `/patient/appointments/{id}/materials`、`/patient/appointments/{id}/ai/summary-consents`、`/doctor/clinical/{id}/ai` | 工作流浏览器用例覆盖精确消息/材料授权、版本隔离和撤回；医生只能读取当前负责预约的授权摘要 | 外部 AI、图片识别不在范围内 |
| 停诊、科主任替诊回应、管理员复核后通知患者并迁移预约 | `/doctor/catalog/suspensions`、`/doctor/catalog/head/suspensions/{id}/review`、`/doctor/catalog/substitutions/{id}/respond`、`/admin/catalog/suspensions/{id}/review` | 工作流浏览器用例覆盖替诊同意、管理员批准、患者通知和订单自动迁移；D6 排班目录用例覆盖真实月历与申请入口 | 新增调班/请假状态机不在范围内 |
| 通知断线补齐、已读幂等和医生待办/资料入口保持真实数据 | `/notifications`、`/notifications/stream`、`/notifications/{id}/read`、`/doctor/notifications`、`/doctor/profile` | D7 正式通知与资料浏览器用例通过；SSE 不可用时游标查询和轮询补齐，资料页只读显示真实身份和脱敏联系方式 | 通知删除、批量已读、资料编辑和改密不在范围内 |
| 桌面与 375px、键盘和临时标签不改变业务事实；生产构建隔离原型 | 工作区路由、浏览器可见交互、`frontend/scripts/verify-production.mjs` | 既有工作流、目录、D7 和移动端回归覆盖横向溢出、焦点/弹窗、临时标签关闭；生产构建检查排除原型路由、演示内容和原型存储键 | 原型源码继续保留在开发路由和测试中，不进入生产资产 |

## 验证记录

- D4–D7 的公开 HTTP 和浏览器证据分别记录在 `doctor-workspace-d4.md`、`doctor-workspace-d5.md`、`doctor-workspace-d6.md`、`doctor-workspace-d7.md`；本记录只汇总跨任务链路，不替代各子任务边界。
- `npm run build` 通过 Vue 类型检查、生产构建和生产资产隔离检查。
- `MEDICAL_PLAYWRIGHT_GREP='管理员维护目录并发布排班'` 的真实 `BrowserAcceptanceTest` 定向运行通过；D7 正式通知/资料用例也通过。
- 交付前运行 `scripts/verify.ps1`，未筛选完整浏览器回归 **208 项全部通过**；后端 Maven 门禁 **105 项全部通过**，脚本退出码为 **0**，最终输出为 `All required verification passed.`。
- 完整门禁仍会输出既有 SSE 断线时的 `HttpMessageNotWritableException` 日志，但未造成测试失败；SSE 不可用时的游标补齐与轮询用例已通过。

## 当前限制

本任务不接入真实支付、外部 AI、OCR、资料编辑、密码修改或新的排班状态机。开发原型仅作对照，正式构建继续禁止携带原型路由、演示身份和业务存储键。GitHub Issue #31 保持开放，等待完整未筛选门禁和双轴审查完成后再统一验收。
