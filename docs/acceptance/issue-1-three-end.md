# 总需求 #1 三端修订验收

规格：[三端交互与业务修订](../design/three-end-requirements.md)。固定基线：06bca76。任务T0–T5实施中，总需求不关闭。

| 验收项 | 公开测试边界 | 当前结果 | 限制 |
| --- | --- | --- | --- |
| T0规则读写、权限、非法时段拒绝 | RulesHttpTest真实HTTP | 待运行 | 不代表存量快照已实现 |
| T1登记、候诊、替诊、超时、快照 | HTTP+fake clock+浏览器 | 待实现 | 待完整门禁 |
| T2开药、并发库存、支付、超时 | HTTP+真实MySQL+浏览器 | 待实现 | 仅模拟支付 |
| T3页面、新闻、统计、账号 | HTTP+Playwright | 待实现 | 待完整门禁 |
| T4图文、授权、多模态、配置 | HTTP+受控provider+Playwright | 待实现 | 不使用真实患者数据 |
| T5完整回归和双轴审查 | scripts/verify.ps1 | 待运行 | 未验证项不能标已完成 |
