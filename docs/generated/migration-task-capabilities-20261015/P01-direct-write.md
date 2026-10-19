# P01 公司和部门直写实现记录

日期：2026-10-16。工作树分支：`fix/dppms-migration-consolidation-20261012`。

## 范围与入口

本轮依据用户批准的新同步任务设计，实现独立适配器 `DPPMS_ORGANIZATION_DIRECT`。配置入口为 `GET /api/v1/pms/integration/templates/dppms-organization-direct?connectionId=...`，使用既有配置权限；返回配置不保存、不调度、不执行。任务仍通过现有任务保存、预览和执行接口操作，保留既有权限、配置版本、租户、运行锁、来源证据和绑定事务。

`AbstractSynchronizeTask` 提供 before/query/insert/success/fail/after 模板方法；`DppmsDirectOrganizationTask` 直接调用专用 `DirectOrganizationMapper` 的 MyBatis XML 写 `system_company/system_dept`。不调用原组织或部门 Owner。读取、事务及重试由现有 SyncRunService 负责，不增加第二套任务状态或重试计数器。

原任务模板、原适配器及原组织 SQL 本轮均未修改。已有 P01/P04 未提交适配保留；新模板复用原 P01 默认配置、裁决参数和公司引用映射。原绑定与运行历史不迁改，新入口需单独保存新任务。

## 裁决复用

- 原批准 SQL 的四个来源分支复制到 `direct-organization-*.sql`，保留全部原字段及筛选规则；各自使用原 `compID/depID/id` 作为读取主键（统一输出别名 sourceId），t_company 使用原 id。取消来源业务码聚合排序，整个快照在内存按精确编码归并。共四个组织来源查询和一个公司别名查询。
- 主定义完整选择沿用 EHR 基础、批准 EHR 补齐、FND 163130 的优先级；同优先级保留完整 canonicalFields 选择规则。复用 DppmsOrganizationProjection 核验原身份、完整主来源、二进制编码和别名；不拼凑不同来源的字段。
- EHR 公司只 TRIM，不做 01→001；t_company 别名单独复用 DppmsCompanyCodeConverter。原 ID 只作来源身份，正式 ID 由目标生成。不同原别名共享正式目标，不改绑。
- 八项补齐、三项 FND 父级160000与历史时间、其余 EHR 原父级与运行审计、全空排除字段、原状态和容量校验保持。正式记录已存在时引用其当前定义，不改结构、状态、联系方式或审计。
- P04 唯一核实账号回执用于八项部门主管；无唯一回执保持待关联。仅本任务创建且未被维护的部门可续补空主管，不能接管或覆盖现有负责人。
- 新旧组织任务的公司/部门别名可通过原绑定查询消费；仍限定租户、连接、来源对象和原键，歧义交由原消费者拒绝。

P01 整树必须完整核对后一次事务提交，使用 ONCE/SNAPSHOT，保持原 10000 行和 64 MiB 容量限制；不支持将组织树拆成独立提交块。正式表写入、绑定及运行证据失败一起回滚。没有新增业务表、字典、菜单或持久化配置。

## 离线验证

在工作树构建锁和原 JDK25/Maven 隔离构建目录下执行：

`python D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/verify-java.py DirectOrganizationPlanTest,DirectOrganizationTransactionTest,DppmsConsolidatedTemplateTest,DppmsOrganizationProjectionTest,SyncAccountFoundationBindingTest pms-module-integration`

最终日志：`D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/targeted-010224.log`。

27 模块构建成功，24 项测试通过（新计划8项、真实 Mapper XML/H2事务5项、旧模板3项、原投影5项、账号绑定查询3项）。覆盖预览不分配正式ID、父级精确关联/缺失/环路、整批事务回滚、原历史时间、重复执行、手工维护冲突、原绑定防改绑、完整来源优先级、排除字段变化、主管续补、租户/删除/空集合隔离以及下游别名可见性。

前序测试发现的空主管映射和预览父级空ID拆箱问题已修复。一次 JVM 原生内存不足导致测试进程退出；最终重跑仅为本次验证设置 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m -Xms32m`，未调整运行服务或机器配置。`git diff --check` 通过。

未执行真实来源读取、真实预览、同步、迁移、任务保存、部署、重启或提交。H2 事务验证不替代真实 MySQL 来源执行、性能及业务验收。

## 双线入口与验证补充

按用户要求保留原始入口，前端“任务类型”现在分别加载原 EHR、原 P01 归并及新 P01 直写模板。原任务名称、配置、绑定与已验证模板没有被自动替换；新直写类型使用 `DPPMS_ORGANIZATION_DIRECT` 独立任务身份。新增 `/templates/dppms-organization` 只为原 P01 模板增加明确入口，既有入口不变。两种 P01 类型都应通过“新建任务”分别配置，生成模板和切换选项不会保存或启动任务。

本轮离线验证：

- 后端 `targeted-011329.log`：27 模块构建成功，20 项通过。包含原 P01 归并执行路径和模板、新直写规划及真实 Mapper XML/H2 回滚，以及两个入口的身份独立、配置互不污染和非法连接拒绝。没有声称两种实现已在真实来源上逐行比对一致。
- 前端 `TaskEditor.runtime.spec.ts`：11 项通过，原保存行为保持；原 P01→直写→原 EHR→原 P01 分别加载正确模板，无保存调用。
- 隔离 Chromium 使用真实 TaskEditor 和 Element Plus 操作下拉框，通过同一切换序列并取消关闭；接口使用离线替身，来源子编辑器为替身，无真实后端请求、无任务保存、无浏览器错误。结果与截图：`D:/CodexTaskArtifacts/project-detail-followup/p01-dual-track-20261016/browser-result.json`、`task-types.png`。这是浏览器组件验证，不是带真实登录与数据库的业务验收。
- 构建仍使用原隔离工具链和同一 `.run/build/worktree.lock`；外部辅助脚本仅将已存在锁文件的打开方式由追加读写改为普通读写，保留排他字节锁，以解决 Windows 追加模式锁失败。没有绕过锁或并行写入 target。

未执行真实预览、同步、迁移、任务保存、部署、重启、提交或推送。真实数据的双线对照留待后续明确执行。


### 2026-10-16 审核范围复核纠正

上述真实运行的183条及177个部门计数是该次配置的事实，不能表示已覆盖全部审核范围。P04范围预览暴露出P01直写模板只带入了163130，遗漏生产P02文档明示的另外28个部门编码；该文档明确要求不得只复制原单码参数。已在新直写模板补回29码，修正16/31前缀、280700、999999父级和160700002/310500001的EHR停用例外，并新增完整29码及篡改停用来源的失败回归。

真实源只读核对29条全部存在且原生主键唯一，证据为 `D:/CodexTaskArtifacts/project-detail-followup/p04-direct-20261016/p01-full-approved-departments-readonly.json`。此次尚未迁入所漏28条；遵守最新“仅预览、不要迁移”，既有成功运行、绑定和历史保持。P04不得用截码、忽略范围或猜配上级绕过缺失正式部门。

## 完整29码补齐范围的真实预览

原单码实现遗漏既有审核的28个FND部门，已按P02既定完整29码修正；163130及此前180条正式写入事实保持。新包经第二次明确授权重启59284加载。独立P01任务2110783899885760514更新v1后，真实预览2110810661583712257为PREVIEW_READY，读取211条，拟新增28、保持183。预览前后业务、字典、原任务及绑定计数一致，没有执行新增28部门。凭据与原历史证据不变。
