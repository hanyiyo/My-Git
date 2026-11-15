# #19 患者首页 AI 导诊抽屉与推荐回填

## 范围

正式患者首页的 AI 导诊入口使用真实业务咨询和导诊接口。普通流程问题只读取医院事实；症状文字在提交给辅助导诊前明确授权范围，拒绝授权仍可自行查询和挂号。导诊结果只把真实目录返回的有效号源回填到目录筛选，不自动创建预约，也不生成预约前病历。

## 验收案例与公共测试 seam

| 验收标准 | 公共测试 seam | 观察结果 | 限制 |
| --- | --- | --- | --- |
| 首页可以打开和关闭 AI 导诊抽屉，并在手机视口正常使用 | `identity.spec.ts` 的患者注册与会话用例内置 AI 导诊流程，含 375px 视口、抽屉宽度与横向溢出断言 | 已通过定向浏览器验收 | 抽屉由患者工作台共享组件承载 |
| 普通医院流程咨询使用真实业务咨询接口，失败时明确显示不可用 | `/assistant/business` 真实接口；浏览器注入成功与 503 响应 | 已通过 | 业务事实内容仍以后端医院目录为准 |
| 症状内容先显示授权边界，拒绝授权不阻塞查询和挂号 | 症状响应后的授权说明、拒绝按钮和目录入口断言 | 已通过 | 本切片不把拒绝写入预约或医疗材料 |
| 推荐的科室、医生和排班来自真实有效资源并回填目录 | `/assistant/triage` 返回科室提示后查询 `/catalog/schedules`；推荐链接断言科室、医生、日期、方式和时段筛选 | 已通过 | 目录页面仍会再次校验资源有效性与号源状态 |
| 明确不能诊断或开处方，AI 失败不显示模拟成功答案 | 导诊文案断言；`/assistant/triage` 失败时错误提示和无结果断言 | 已通过 | 外部提供方是否启用由后端 AI 配置决定，失败不会由前端切换为模拟答案 |
| HTTP 与 Playwright 覆盖授权、失败、推荐回填和不自动创建预约 | `WorkflowHttpTest.patientAssistantTriageUsesRealHttpBoundaryWithoutCreatingPreconsultationTask`；首页浏览器流程断言拒绝不调用导诊、失败不显示结果、科室 ID 回填和无预约写入 | HTTP 32 项与定向浏览器验收通过 | 首页只负责导诊与导航，不执行挂号写操作 |

## 验收记录

- `pwsh -NoProfile -File scripts/test-backend.ps1 -Test WorkflowHttpTest`：通过（32 项），含真实 `/assistant/triage` HTTP 边界和无预约前 AI 任务断言。
- `$env:MEDICAL_PLAYWRIGHT_GREP='患者完成注册、验证码登录、刷新恢复会话及退出'; pwsh -NoProfile -File scripts/test-backend.ps1 -Test BrowserAcceptanceTest`：通过，覆盖首页 AI 导诊的业务咨询失败、授权拒绝/同意、导诊结果、推荐回填、导诊失败和无预约写入。
- `npm --prefix frontend run build`：通过，包含 `vue-tsc --noEmit` 与 Vite 生产构建。
- `git diff --check`：通过。
- 完整 `pwsh -NoProfile -File scripts/verify.ps1`：后端 96 项及文档、构建检查通过；浏览器全量套件触发现有十分钟上限（浏览器进程总耗时 600 秒），因此命令退出失败。定向浏览器验收已通过。

限制：症状导诊调用现有正式导诊接口，不保存预约前病历；推荐只回填目录筛选，患者仍需在目录页面自行确认并提交挂号。全量浏览器门禁仍受现有十分钟总时限限制。
