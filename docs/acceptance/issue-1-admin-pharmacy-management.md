# 管理员药品与库存工作台验收记录

## 范围

本记录覆盖管理员运营总览模块 `/prototype/admin-home?module=药品与库存`，并验证正式深链接 `/admin/pharmacy` 兼容访问。页面接入真实药品 HTTP 接口，维护药品资料、启停状态和库存；处方、药费订单支付和医疗内容仍由医生端、患者端及后端药品服务负责。

## 验收证据

| 验收标准 | 测试 | 结果 | 限制 |
| --- | --- | --- | --- |
| 管理员可从运营总览进入药品与库存模块，看到摘要、筛选和库存表 | `frontend/tests/admin-home-prototype.spec.ts`；`frontend/tests/admin-pharmacy.spec.ts` | 通过 | 真实后端验收通过；空列表也有明确空状态 |
| 列表明确拆分可用库存、库存锁定、总库存和预警阈值，低库存标红 | 同文件：库存口径和预警展示 | 通过 | 金额仍使用分为单位的后端字段 |
| 可新增、编辑和启停药品；停用药品不出现在医生可开具列表 | 同文件；`PharmacyHttpTest` | 通过 | 不包含真实药品出库 |
| 有处方历史的药品不能删除，无历史药品可以删除 | 同文件：删除限制；`PharmacyServiceTest`/`PharmacyHttpTest` | 通过 | 历史订单只保存快照 |
| 库存独立调整总库存，不能低于最新锁定库存，并处理并发冲突 | `frontend/tests/admin-pharmacy.spec.ts`；`PharmacyServiceTest`/`PharmacyHttpTest` | 通过 | 不填写库存调整原因 |
| 详情抽屉展示消耗记录的订单状态、数量和时间，不展示患者医疗内容 | 同文件：药品详情和消耗记录 | 通过 | 记录来自药费订单历史 |
| 页面在窄屏无横向溢出，旧 `/admin/pharmacy` 深链接继续可访问 | 同文件：管理员药品页移动布局与深链接 | 通过 | 管理员登录流程沿用现有会话 |
| 原型入口使用稳定演示数据，宽屏内容不再受全局窄页样式限制，接口空响应不显示 JSON 解析错误 | `frontend/tests/admin-home-prototype.spec.ts`：演示药品、无 `/api/` 请求和管理员模块入口 | 通过 | 原型数据仅保存在当前页面，不写入后端 |

## 业务边界

- `Medicine（药品）` 是可开具药物品项，不等同于 `Prescription（开药记录）`。
- `Available medicine inventory（可用库存）` 不包含 `Inventory hold（库存锁定）`；总库存是两者之和。
- 有处方历史的药品不能硬删除，停用只影响后续开具，不撤销既有医疗记录或药费订单。

## 全量门禁

已运行 `pwsh -NoProfile -File scripts/verify.ps1`：文档检查、Vue 类型检查与构建、MySQL/Redis 集成测试、浏览器验收和后端测试全部通过（96 个后端测试，浏览器验收包含管理员药品工作台）。
