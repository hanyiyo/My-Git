# 总需求 #1：患者站内通知原型验收计划

状态：原型已实现，完整门禁通过。设计见 [患者站内通知原型](../design/patient-notifications-prototype.md)。公开交互测试位于 `frontend/tests/notifications-prototype.spec.ts`。

| 验收项 | 公开测试边界 | 当前结果 |
| --- | --- | --- |
| 导航和详情定位 | Playwright：顶部、铃铛、首页动态进入；tab / notice 深链接与临时标签 | 通过；侧栏沿用相同导航处理，通知项有选中态 |
| 双标签与阅读 | Playwright：全部/未读、打开详情已读、单条已读、当前标签全部已读，新闻独立计数 | 通过 |
| 全局红点 | Playwright：系统未读同步铃铛与侧栏；新闻不影响；删除和清空后联动 | 通过 |
| 重要提醒与时间分组 | Playwright：有效高优先级在顶部，已读仍重要，提醒到期或完成签到回时间组 | 通过；列表每条只属于一个分组 |
| 订单入口 | Playwright：详情只有查看订单，定位并高亮关联挂号单；新闻无业务按钮 | 通过；无订单项使用同一条件隐藏业务按钮 |
| 停诊与替诊语义 | Playwright：停诊取消与退款状态分开；替诊已迁移且无需接受；阅读不改变业务 | 通过 |
| 单条删除 | Playwright：二次确认、默认取消焦点、取消无变化、高优先级提示、详情删除后关闭 | 通过 |
| 清空范围 | Playwright：未读筛选下也清空当前标签全部条目，确认数量含已读；另一标签不变 | 通过 |
| 阅读与删除边界 | Playwright：删除个人收件不修改订单或首页新闻原文；已删深链接不恢复内容 | 通过 |
| 状态与恢复 | Playwright：初始失败、保留旧内容的刷新失败、重试、空列表、暂无未读；页面加载与更新反馈 | 通过 |
| 手机与键盘 | Playwright：375px 无横向溢出、375×812 全屏详情、Escape 逐层关闭、确认框焦点恢复 | 通过；已修复公共弹窗 88dvh 限制导致的全屏高度不足 |
| 原型范围 | Playwright：无业务 API 请求，刷新重置演示记录；构建产物检索原型路由及特有文案 | 通过；生产资源未包含通知原型路由和数据 |

实现验证：2026-11-11（Asia/Shanghai）运行 `pwsh -NoProfile -File scripts/verify.ps1` 退出 0。文档检查、Vue 类型检查/生产构建、真实 MySQL/Redis 与浏览器回归全部通过；Java 测试 93 条，0 失败、0 错误、0 跳过；浏览器用例 41 条，0 失败、0 跳过、0 flaky，其中新增通知用例 6 条。日志见本机 `.runtime/notifications-implementation-verify.log`。通知与原患者端专项合计 17 条通过；首次专项发现手机详情受公共弹窗高度限制，修复后重跑通过。

视觉检查：桌面系统通知、医院新闻、375px 手机列表及全屏详情截图已检查，保存于本机 `.runtime/notifications-system-desktop.png`、`.runtime/notifications-desktop.png`、`.runtime/notifications-mobile.png`、`.runtime/notifications-mobile-detail.png`。

限制：固定虚构通知、新闻及订单，原型内存状态在切页时保留、刷新重置；未接真实通知/新闻接口、SSE 或持久化。删除及清空仅隐藏患者收件展示，真实业务记录、审计与新闻发布功能未改动。正式端的批量已读、个人删除/清空及新闻个人隐藏接口仍是后续接入工作。

设计阶段历史验证：`.runtime/notification-design-verify.log` 记录了设计文档提交时的门禁；本轮页面验收以上述实现验证为准。
