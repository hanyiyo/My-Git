# #16 患者首页科室医生与排班入口

## 范围

首页展示真实可见的科室与已认证医生，并将入口带入现有患者资源工作台。目录工作台继续由后端校验科室、医生、已发布排班和号源有效性；失效筛选由 URL 回填逻辑安全清除。

## 验收案例与公共测试 seam

| 验收标准 | 公共测试 seam | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 首页展示的科室和医生来自真实目录接口 | Playwright 患者首页流程注入目录 HTTP 响应并验证真实接口形状 | 已通过 | 浏览器边界使用虚构目录数据，生产页面请求真实接口 |
| 点击科室、医生或立即挂号进入真实目录工作台 | Playwright 首页入口点击和 URL 断言 | 已通过 | 目录页继续复用现有正式路由 |
| URL 回填筛选，非法或失效条件安全清除 | `PatientCatalogView` 的 URL 回填逻辑与浏览器查询参数断言 | 已通过 | 业务有效性最终以后端响应为准 |
| 目录结果只展示有效医生和已发布排班 | `CatalogHttpTest` 的患者可见目录与排班接口测试 | 已有 HTTP 覆盖，随完整门禁回归 | 本切片只接入口，不改变目录后端规则 |
| 无可用号源显示空状态并保留日期/服务方式操作 | Playwright 目录页空响应流程 | 已通过 | 空状态不做像素级视觉回归 |

## 验收记录

- `MEDICAL_PLAYWRIGHT_GREP='患者完成注册' pwsh -NoProfile -File scripts/test-backend.ps1 -Test BrowserAcceptanceTest`：通过。
- `npm --prefix frontend run build`：通过，包含 `vue-tsc --noEmit` 与 Vite 生产构建。
- `git diff --check`：通过。
- 完整 `pwsh -NoProfile -File scripts/verify.ps1`：通过，96 个后端测试、真实 MySQL/Redis、全量浏览器验收、前端构建与文档检查均通过。

限制：浏览器边界使用注入的目录数据验证接口形状和导航；目录后端真实可见性、医生资质和排班有效性由既有 `CatalogHttpTest` 覆盖。
