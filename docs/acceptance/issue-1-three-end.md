# 总需求 #1 三端修订验收

规格：[三端交互与业务修订](../design/three-end-requirements.md)。固定基线：06bca76。任务T0–T5实施中，总需求不关闭。

| 验收项 | 公开测试边界 | 当前结果 | 限制 |
| --- | --- | --- | --- |
| T0规则读写、权限、非法时段拒绝 | `RulesHttpTest` 真实 HTTP | 已通过 | 存量排班/订单快照由业务写入时固定 |
| T1登记、候诊、替诊、超时、快照 | `BookingHttpTest`、`WorkflowHttpTest`、`BrowserAcceptanceTest` | 已通过 | 已进入候诊队列的替诊需人工处置；普通替诊自动迁移并通知 |
| T2开药、并发库存、支付、超时 | `PharmacyServiceTest`、后端完整回归 | 已通过核心服务 | 支付仍为模拟支付；药品端页面和完整并发浏览器覆盖仍需扩展 |
| T3页面、新闻、统计、账号 | Vue 构建、现有 Playwright 流程 | 部分完成 | 公共三端壳、目录/治理/药品入口已接入；新闻、统计、完整个人中心仍未实现 |
| T4图文、授权、多模态、配置 | `AiServiceTest`、`LocalAiTextProviderTest`、浏览器导诊流程 | 部分完成 | 患者端已改为导诊；医生摘要接口和入口已接入，外部多模态配置及全量页面验收仍待补齐 |
| T5完整回归和双轴审查 | `scripts/verify.ps1` | 已通过（退出码 0；后端 90 tests、浏览器验收、Vue 构建、文档检查） | 仍有 T3/T4 页面范围限制，见上表 |
