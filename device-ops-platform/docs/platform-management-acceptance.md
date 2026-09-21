# 平台管理闭环验收记录

日期：2026-09-08

状态：实现与自动化质量门完成；真实浏览器视觉及完整发布旅程因工具限制未完成，详见下文。

## 隔离边界

- 工作目录为独立 device-ops-platform，不操作 NPDP 主数据库或主档。
- 未读取或更改运行目录 data/ 的密钥、数据库及日志。
- 不自动 Git 提交、推送、上线迁移或切换活动解析发布。
- 浏览器验收服务使用 loopback、全新 H2 内存数据库和合成历史记录；其 local 身份不等于真实 OAuth2 验收。
- 回调、调度派发及外部主数据关闭，不连接真实设备。

## 已完成的定向验证

工作台修复定向 Vitest：4 个文件、26 项通过。新增回归覆盖 SSH 模式保留、采集终态后解析继续查看、历史导出未知值、脚本一次性交接不执行、项目/设备乱序响应及卸载失效。实际先运行失败测试再修复。

管理 MySQL 查询/迁移的隔离 Testcontainers 测试已实际运行一次，1 项通过、0 失败、0 错误、0 跳过。首次运行发现 V19 MySQL 默认值语法不兼容，修正后该次通过；完整最终测试另行记录。

后端最终定向组合验证 19 项通过、0 失败、0 跳过，覆盖 namespace 授权、H2/MySQL 查询和 V18→V19 迁移、管理 HTTP、活动绑定以及既有采集边界。两个目标的全部状态组合与原详情聚合一致。旧行 createdAt 保持 null，新行使用默认时间。

只读复核发现并修复的安全问题：client_namespace 或 subject fallback 的字面量星号不应被视为显式 namespace 通配授权。已通过 authorizer、SQL 和 HTTP 回归，只有显式 namespace 数组授予通配。管理 HTTP 使用测试专用 JwtDecoder，验证 HTTP/Spring Security 授权链，不将其报告为真实 IdP 或签名算法验收。

## 全量质量门

- `mvn clean test`：399 项通过、0 失败、0 错误、0 跳过，包含真实隔离 Docker MySQL 测试。
- 前端 `pnpm test`：15 文件、57 项通过。
- `pnpm ts:check`、`pnpm lint`、`pnpm build`：退出码均为 0。Lint 有 545 条格式警告；构建保留大于 500 kB 的 chunk 提示，不将其写为零警告。

- `mvn verify`：全部模块成功，完整测试再次通过。
- Playwright `pnpm test:e2e`：4 项通过（原采集旅程 1 项、新管理旅程 3 项）。首轮 2 项因 RequestState 嵌套重复 alert 失败，删除外层重复 role 后原用例复验通过；未通过放宽断言掩盖问题。
- Playwright 使用真实本机 Chrome，但 API/认证/设备均为 route mock；不能代替隔离真实后端验收。

## 用户补充的统一设计要求

六个管理页面统一复用 Element Plus 和共享管理样式变量；状态统一使用 ManagementStatus 的中文标签及原始 code，文件导入入口为 Element Plus 按钮。历史详情启用只读任务面板，不显示无效的采集执行按钮；默认工作台提交行为保持不变。

最终前端复验：16 文件、63 项测试通过；ts:check、lint、build 和 4 条 Playwright 旅程均通过。Lint 547 条格式警告、0 错误；Rollup 仍报告依赖注释和大 chunk 提示。

## 真实隔离 JAR 页面验证

以 tools/start-management-acceptance.ps1 启动 loopback 48182、独立 H2 内存数据库，V999 仅种入三条合成终态记录和一个脚本版本；没有连接设备、创建执行尝试或回调。

- 首次 `/overview` 直接访问返回 404，定位后补充管理 SPA 白名单 fallback。新增 2 项 HTTP 回归覆盖管理路径转发，未知 API/资源仍为 404；重包后真实页面已打开。
- 总览显示 3 条记录，以及成功、失败、部分成功各 1，近期记录倒序与真实隔离数据库一致。
- 历史详情深链自动补 collectionId 并恢复 FAILED 和持久化输出，缺失历史脚本明确不可用。
- 脚本目录只显示授权关联的 acceptance-show 1.0.0，页面元素触发安全读取后显示 show version；无自动设备执行。
- 设置页显示 local-debug-user、显式授权范围、8388608 字节限制，以及禁用的 callback/schedule/masterdata/Telnet。
- 解析控制台显示空注册表及真实 worker/能力/等待计数，日志类型表单可展开和输入。

**浏览器工具限制：**IAB 截图返回 `browser screenshot activity capture failed for guest`，常规 locator 点击多次超时，且页面出现非预期导航。因此上述依据为成功导航后的 DOM 及部分页面元素事件读取，不声称完整鼠标旅程或视觉布局验收通过，也未验证真实后端完整草稿→验证→发布→激活旅程。自动化 mock Chrome 旅程与 HTTP/组件回归另列，不能互相替代。

## 最终收口

- 隔离服务已停止，48182 无本次服务监听；内存数据不保留。
- 最终 `mvn clean verify` 成功：401 项通过、0 失败、0 错误、0 跳过，包含新增 SPA 回归与隔离 MySQL 测试。一次 clean 因验收进程占用 target 目录失败，停止本次验收进程后完整重跑成功，不是测试代码失败。
- JAR 内 `BOOT-INF/classes/static/index.html` 与当前 `device-ops-web/dist/index.html` 内容一致，引用的静态 assets 均存在。
- `git diff --check` 通过。
- 未提交或推送。原有采集 Playwright 用例刷新了 `docs/browser-acceptance.png`；这是 mock 采集旅程截图，不作为管理页视觉通过证据。
- 用户原有 data/、工作区外脑暴目录和工具生成的 .zcode/ 计划文件不纳入功能交付内容，不删除或覆盖。

真实 IdP 登录续期、真实设备协议、生产网关/iframe、生产数据库升级、线上回调及活动 release 切换不属于本次自动验收。
