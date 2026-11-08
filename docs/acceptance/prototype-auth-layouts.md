# 登录注册左右布局原型

问题：现有 `/prototype/auth` 页面纵向堆叠较多。比较三种左右结构，帮助确定登录注册切换、角色区分和验证码区域的布局；尚未选定正式方案。

分支：`prototype/auth-layouts`，基于包含原型的本地集成提交 `06bca76`。仅在该原型分支比较，不发布到集成分支，也不替换真实 `/login`。

运行：在该工作树执行 `npm run dev:auth-prototype`，打开 `http://127.0.0.1:5181/prototype/auth?variant=A`。

| 方案 | 布局与交互 |
| --- | --- |
| A 温暖分栏 | 左侧品牌介绍与纵向角色选择，右侧分段登录/注册切换；验证码紧邻输入框 |
| B 角色引导 | 左侧表单与两张登录/注册入口，右侧编号角色说明；验证码图示在输入框左侧 |
| C 服务工作台 | 左侧精简角色导航，右侧账户工作区；验证码独立分区，注册字段在宽屏分两列 |

底部开发态切换条、左右方向键及 `?variant=A/B/C` 共用路由；切换保留当前角色、表单、勾选和演示登录状态。输入框及可编辑区域内方向键仍用于编辑。刷新保留方案，清空内存中的演示资料；移动端改为上下排列。

原有 `chooseRole`、`switchMode`、`fillDemo`、`submit`、`signOut` 及其验证与提示保持不变，包括三端模拟注册。现有原型没有验证码控件，本轮按用户要求补充纯展示、可换图的模拟验证码，不增加提交校验，不接真实登录或注册请求。真实登录页继续使用后端生成和校验的验证码。

| 验收点 | 公开测试入口 | 结果 |
| --- | --- | --- |
| 三套方案三端登录、原有注册校验、密码显隐、记住演示、找回提示、退出、模拟验证码换图 | `prototype A/B/C preserves all three roles and registration behavior` | 定向浏览器验收通过 |
| 390px 无横向溢出、三端入口可操作、登录及注册可达 | `prototype A/B/C remains usable on a narrow screen` | 定向浏览器验收通过 |
| 切换保留状态、URL 及刷新、方向键不干扰输入、未知方案回退 | `prototype variants preserve form/session state, URL and keyboard editing` | 定向浏览器验收通过 |
| 真实页面回归、文档、Vue 类型和构建、MySQL/Redis HTTP 与浏览器验收 | `pwsh -NoProfile -File scripts/verify.ps1` | 同源码第二轮完整门禁退出 0；86 项后端、23 项浏览器，零失败/错误/跳过 |

补充验证：逐段比较原有状态与五个业务函数，内容一致；已查看三套桌面、注册与手机截图。生产构建预览中 B 方案正常显示，原型切换条隐藏。最终视觉方案尚未选定，待用户比较后另行实现正式页面。

首轮完整门禁中，原有 `workflows.spec.ts` 的诊室流程未在默认 5 秒等待内显示签到成功状态，导致该轮失败；失败截图停留在签到前。同一套源码第二轮完整门禁全部通过，23 项浏览器测试无重试或跳过。未修改该业务页面或放宽其断言，也未定位首轮偶发失败的根因。首轮不能作为通过证据；最后一轮完整门禁才是本次交付依据。

原型截图保存在忽略的 `frontend/test-results/`；第一轮浏览器结果与最终门禁日志保存在忽略的 `.runtime/auth-layouts-first-browser-results.json` 和 `.runtime/auth-layouts-verify.log`。
