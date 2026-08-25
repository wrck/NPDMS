# F-PROJ-001 V1.8 真实浏览器验收证据

## 环境与边界

- 执行日期：2026-08-22。
- 当前工作树隔离基础设施：Compose 项目 `npdms-50eb`，MySQL `13306`，Redis `16379`。
- 固定端口 `58080`、`18081` 已被非本任务宿主机进程占用；未停止、未复用这些进程。本轮临时使用后端 `58081`、前端 `18082`，未修改仓库约定端口。
- 已优先请求 Codex 内置浏览器打开 `http://127.0.0.1:18082`；当前任务返回 `queued`，且会话未暴露可执行 DOM 操作的内置浏览器接口。自动验收因此降级为 Codex bundled Node.js 提供的 Playwright/Chromium，仍连接当前工作树实际前后端和真实 MySQL，不使用 Mock 页面或 Mock API。
- `specs/001-project-delivery-platform/` 仅作历史参考，本轮未运行该目录的规格基线校验，也未把其哈希作为验收门禁。

## 唯一默认模板创建

浏览器从“项目交付 → 项目管理 → 项目列表”进入创建向导，填写合法三维：`直签 / 工程类 / 原厂直服`。

- 候选结果：唯一命中 `TPL-DIRECT-ENG-STD`，模板 `#910001`，revision `v2`，加载方式 `AUTO_DEFAULT`。
- revision 预览：S0～S6 共 `7` 个阶段、`24` 个任务、`8` 个里程碑、`17` 个交付件、`14` 个门禁。
- 创建接口：HTTP `200`、业务码 `0`，返回项目 `920052 / PJT2026000008`。
- 创建结果：`ACTIVE / S0 / UNASSIGNED`，列表出现新项目且模板显示 `#910001 v2 自动`。
- 详情页和页面刷新后均能重新读取项目、模板绑定、客户、合同、实施地点及创建原因；详情接口、实例接口、成员接口、项目树接口和进度接口均返回 HTTP `200`，无失败请求。
- MySQL 持久化：Project `1`、Stage `7`、Task `24`、Milestone `8`、当前任务执行契约 `24`、ACC 交付件 `17`、Gate `14`、Gate Reference `23`。
- 平台事实：完成幂等记录 `1`、`PROJECT_CREATE/SUCCESS` 审计 `1`、`ProjectCreated/PENDING` Outbox `1`，聚合键均为项目 `920052`，租户为单租户回退值 `0`。

截图：

- [候选列表](./04-template-candidates.png)
- [revision 预览](./05-template-preview.png)
- [创建前确认](./06-create-confirm.png)
- [创建成功列表](./07-create-success.png)
- [项目详情](./08-project-detail.png)
- [刷新后项目详情](./09-project-detail-refreshed.png)

## 人工选模、失败闭环与内存边界

使用迁移已提供的示例组合验证，无臆造权威值域：

- `非直签 / 工程类 / 原厂直服`：页面显示 `PROJECT_TEMPLATE_NO_MATCH`，确认按钮不可用；返回上一步后项目名称仍保留，刷新页面后向导消失。MySQL 中所有 `IT-BROWSER-NO-MATCH-%` 项目数为 `0`。
- `非直签 / 普通类 / 原厂直服`：同优先级返回两个候选，人工选择 `#910004 v1` 后创建成功；返回 `MANUAL_SELECTED`，项目 `920055 / PJT2026000011`。MySQL 中 Stage `7`、Task `18`、Milestone `6`、当前任务执行契约 `18`、ACC 交付件 `14`、Gate `14`、Gate Reference `22`。
- 陈旧候选水位：在真实浏览器提交前仅修改本次网络请求中的 `candidateWatermark`，服务端返回业务码 `1014024013`“模板候选已变化”；页面保留全部输入并显示创建失败，刷新后向导消失。MySQL 中所有 `IT-BROWSER-STALE-%` 项目数为 `0`。
- 页面源码检查未出现 `localStorage`、`sessionStorage` 或 `indexedDB` 草稿读写；结合失败后刷新即清空向导的行为，证明创建表单仅保存在页面内存。未枚举或输出认证存储内容。

截图：

- [无模板阻断](./10-no-template-match.png)
- [多候选人工选择](./11-multi-template-selected.png)
- [人工选模创建成功](./12-manual-template-created.png)
- [陈旧候选失败且输入保留](./13-stale-candidate-retained.png)

## 服务经理指派与版本冲突

对项目 `920055` 从列表打开“指派一级服务经理”，使用平台用户选择器选择“管理员”：

- 浏览器请求确认同时携带 `Idempotency-Key` 与 `If-Match`。
- 首次仅在浏览器路由层把 `If-Match` 改为陈旧版本，服务端返回业务码 `1014024014`；弹窗保留并提示“Project版本冲突，请重新加载后重试”。
- 页面自动重新读取Project版本，再次确认后返回业务码 `0`；Assignment `1` 创建，Project版本由 `0` 升为 `1`。
- MySQL 只有一个当前有效的 `SERVICE_MANAGER_L1` 区间；责任范围快照为 `L1`，本次未臆造办事处ID或实施地点ID。
- 指派后 `assignment_status` 仍为 `UNASSIGNED`，符合“只确认服务经理不得冒充项目主责已指派”的契约。
- 同事务产生完成幂等记录、`PROJECT_SERVICE_MANAGER_ASSIGN/SUCCESS` 审计和 `ProjectServiceManagerAssigned/PENDING` Outbox。

截图：

- [If-Match版本冲突](./15-assignment-version-conflict.png)
- [重新确认后指派成功](./16-service-manager-assigned.png)

## 验证中发现并修复的问题

真实浏览器首次创建时，MySQL 报告 `proj_project_task_execution_contract.tenant_id` 无默认值。根因是当前单租户运行配置关闭租户拦截器，而创建入口只把租户传给平台事实，没有把Controller解析出的租户继续传播到Project草稿和任务执行契约。

修复后，应用服务在进入领域创建前写入命令租户，任务执行契约从Project草稿显式取得租户。补充的事后单元测试分别捕获应用服务传入的草稿和Mapper插入的执行契约，断言租户均被传播。修复后重新打包、重启，再执行上述真实浏览器闭环成功；真实MySQL原子失败和并发测试仍为 `11/11 PASS`，全量项目模块回归为 `154/154 PASS`。

## 控制台与结论边界

- 主流程、负向模板流程和指派流程没有失败请求或HTTP `4xx/5xx`。
- 控制台仅观察到全局既存的 Vue Router `next()` 弃用警告和 Element Plus link underline 弃用警告；未发现本Feature新增的控制台错误。
- AC-FPROJ-001～006、008～010已有跨页面、API、MySQL和测试证据。
- AC-FPROJ-007仍受实施地点权威主数据接口及可比较版本缺失阻断；本轮没有通过手填稳定ID或臆造值域绕过，因此不能放行该AC，也不等同于UAT、发布或治理门禁GO。

## 2026-08-24 V1.8 组织与地点改造后的状态

上述 AC-FPROJ-007 结论保留为本轮浏览器执行时的历史状态。后续 V1.8 组织与地点基础改造已补齐公司、部门编码、同一行公司—部门授权范围、AST 地址/站点/位置、项目多站点、区划—办事处建议和人工指派契约，并通过服务端负向测试、真实 MySQL 与真实浏览器复验；AC-FPROJ-007 的实现阻断已经关闭。后续证据见 `output/location-v18/browser-acceptance.md`、`output/location-v18/mysql-acceptance.md`和`output/location-v18/regression-summary.md`。

该补充只更新 Feature 实现证据状态，不构成 Deployment、SIT、UAT、Release 或治理 GO；受管 Feature 索引与 Requirement 追溯仍须在规格仓库回写后重新同步。
