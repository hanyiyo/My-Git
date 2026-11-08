# 校医院健康服务系统

当前运行栈为 Spring Boot 3、Java 21+、MyBatis、MySQL/Flyway、Redis 和 Vue 3/TypeScript/Vite/Router/Pinia。已实现身份与资质、医院目录与排班、预约与模拟支付、签到候诊与现场挂号、接诊病历、授权文本 AI、停诊替诊、医疗审核和可靠通知。各项实际验收及限制见 `docs/acceptance/issue-2.md` 至 `issue-10.md`。历史 Node 原型保存在 `prototypes/node/`，不作为业务运行时。

## 本机运行

1. 配置 MySQL 数据库 `medical_mvp` 和专用账户，以及本机 Redis。也可使用 `compose.yml`：先在进程环境配置 `MYSQL_ROOT_PASSWORD` 和 `MEDICAL_DB_PASSWORD`，再执行 `docker compose up -d`。已有 3306/6379 服务时使用自己的隔离端口，避免冲突。
2. 配置 `MEDICAL_DB_URL`、`MEDICAL_DB_USER`、`MEDICAL_DB_PASSWORD`；Redis 端口可用 `MEDICAL_REDIS_PORT`，密码可用 `MEDICAL_REDIS_PASSWORD`。分别生成至少32字节的随机 `MEDICAL_JWT_SECRET` 和 `MEDICAL_CAPTCHA_SECRET`，持久保存用于跨重启验证；不要提交密钥。
3. 首次管理员需显式设置 `MEDICAL_BOOTSTRAP_IDENTIFIER` 和至少12位的 `MEDICAL_BOOTSTRAP_PASSWORD`。仅在该账号不存在时创建。首次成功后移除引导环境变量。公开注册只允许患者或待认证医生；管理员由已有管理员创建。
4. 本机 HTTP 开发设置 `MEDICAL_COOKIE_SECURE=false`；HTTPS 部署保留默认 `true`。执行 `npm start` 启动真实后端。若 Maven 不在 PATH，设置 `MEDICAL_MAVEN` 为绝对路径；Java 需21+。本机脚本可发现 `D:/apache-maven-3.6.3/bin/mvn.cmd` 和 `D:/jdk/jdk-25`。
5. 执行 `npm --prefix frontend ci`，然后 `npm run dev:frontend`。浏览器打开 Vite 输出的地址，API 由开发代理转发到8080，可通过 `MEDICAL_API_URL` 调整。登录验证码为实际生成的图片，答案不会返回浏览器。

## 医院目录与排班

管理员登录后进入“医院目录与排班管理”：维护医院名称、地址和说明，新增或编辑科室，再为已注册的医生维护姓名、职称、简介、单科室归属与认证状态。只有有效科室中已认证且未禁用的医生可发布排班。同一医生、日期、上午/下午只能发布一次，线上与线下也不能冲突；存在未结束排班时不能直接变更医生科室。

排班使用医院时区 Asia/Shanghai，固定上午08:00–12:00、下午14:00–18:00。容量为1–500，费用以整数分记录，每份号源有独立数据库记录；发布时保存时间、容量与费用快照。患者在“查询科室、医生与排班”查询有效资源，默认开诊时间在未来7天内，也保留当天尚开放的线下现场资源；明确指定日期可查询其他有效安排。医生在“我的认证与排班”查看自身资料和全部出诊安排。提前预约在开诊时停止，线下现场资源显示至结束前15分钟；患者可选择有效排班登记，完成模拟支付后在订单查看凭证、签到和进入诊室。

生产构建：`npm --prefix frontend run build`；后端 `mvn -f backend/pom.xml package`。用同源 HTTPS 反向代理托管 `frontend/dist/` 并代理 `/api/` 到 Spring Boot；Vue 路由需静态页回退。MySQL 数据目录须持久化，Redis 只保存可重建登录状态。测试固定验证码仅存在于测试类路径，不在生产包内。

## 完整验证

```powershell
npm --prefix frontend ci
node frontend/node_modules/@playwright/test/cli.js install chromium
pwsh -NoProfile -File scripts/verify.ps1
```

Windows 测试脚本在随机本机端口、独立临时数据目录启动 MySQL 和 Redis，结束时只停止它启动的进程，保留失败日志。`MEDICAL_MYSQL_BIN`、`MEDICAL_REDIS_BIN` 可指定安装目录。MySQL 的独立测试数据放在 ASCII 临时路径，避免 Windows 服务对含中文目录的启动限制。

本机安装 Chrome 时脚本使用该浏览器；其他环境使用 Playwright Chromium。可用 `MEDICAL_BROWSER_CHANNEL` 显式选择浏览器。浏览器测试的已知验证码由测试类路径的随机数适配器提供，不通过 HTTP 返回答案。

Linux/CI 预先配置专用测试服务：`MEDICAL_TEST_DB_URL`、`MEDICAL_TEST_DB_USER`、`MEDICAL_TEST_DB_PASSWORD`、`MEDICAL_TEST_REDIS_PORT`。测试会创建虚构账户，并在独立 Redis 数据库3模拟状态丢失；严禁指向业务数据。CI 使用 MySQL 8.4/Redis 7.4；本机实际版本记录在验收文档中。

统一检查覆盖文档、运行栈约束、前端类型/构建、真实 HTTP、MySQL/Redis、重启恢复和 Playwright 浏览器路径。任何失败均以非零退出码终止。详见 `docs/acceptance/issue-2.md` 和 `docs/retrospectives/2026-11-06-implementation.md`。

单个 Java 测试可执行 `pwsh -NoProfile -File scripts/test-backend.ps1 -Test CatalogHttpTest`；交付仍必须执行完整验证。目录与排班证据见 `docs/acceptance/issue-3.md`。

## 医疗内容审核与审计

普通管理员只看业务元数据。在“医疗内容审核”中，由另一位管理员授予已有管理员审核资格，或新建审核管理员并授权；禁止自授。审核人员登录自己的账号后，选择预约、正文/附件范围和理由，取得30分钟访问授权。资格或单次授权撤回后，后端立即拒绝后续读取。限制展示作用于患者、医生、附件下载和材料派生的AI摘要；审核人员按授权取证可见原文，每次读取和下载留审计，解除限制恢复显示。

所有新增敏感审计采用Ed25519非对称签名、单调序号和前一条签名链接，业务记录与审计签名在MySQL同一事务中追加。提交后在数据库之外原子保存最新签名校验点，能检测正文篡改、缺行、额外未签名事件以及数据库连同链尾回滚。普通审计查询只返回脱敏元数据，医疗更正理由保留在受控医疗记录内。完整性接口提供签名公钥，可交独立验证方留存。

`MEDICAL_AUDIT_DIRECTORY` 默认为后端工作目录下 `data/audit`，首次启动生成Ed25519私钥、公钥及链尾校验点。此目录必须持久保存、仅后端运行账户可写；Linux私钥创建为0600，Windows部署须配置对应账户ACL。数据库和附件备份也必须配套备份审计目录；恢复时先停止写入，再恢复同一备份时点，不能丢失私钥后重新生成来冒充历史签名。更换数据库测试环境时使用隔离的测试审计目录。

记录签名证明来自持有应用私钥的服务，结合认证主体、理由和版本提供应用层归责；不等同于患者或医生各自私钥的个人电子签名。持有数据库和审计目录全部写权限的运维仍处于信任边界内；真实机构上线需进一步使用独立密钥托管、可信时间戳和外部只写归档。

## 通知与持久任务

三端全局“站内通知”展示未读数和关键提醒，支持逐条标为已读。业务事务先在 MySQL 写入通知；独立扫描器提交后分配递增游标，再推送认证 SSE。浏览器以游标查询分页补齐并每2秒轮询核对；流不可用时10秒后重连。代理需允许 `/api/v1/notifications/stream` 长连接、关闭缓冲，使用同源 HTTPS；访问令牌仅放请求头。通知只携带状态提示，不包含病历正文。

默认每2秒扫描投递，每5秒恢复 AI、占号超时、替诊确认截止和签到窗口提醒；退款使用自身扫描器。任务、租约、次数和错误码保存于 MySQL，重启后继续。AI 和投递租约30秒，最多尝试3次；投递和业务任务失败间隔60秒，AI 普通请求可立即完成有限重试。AI 与退款的外部调用不持有业务事务，旧 worker 的返回须匹配当前租约；占号和替诊任务将业务效果与完成状态同事务提交。

管理员可通过 `/api/v1/admin/notifications/tasks` 查看最近100条业务任务元数据。达到3次的失败任务停止自动重试，须先排查数据库或业务故障，再由维护人员制定恢复方案。站内通知在 SSE 发送失败时仍可查询，发送状态不改变预约、支付和候诊事实。签到提醒只在确认预约未签到且处于合法窗口时生成。此方案面向单后端部署；扩容前须重新设计跨节点事件分发和附件共享（ADR-0013）。
