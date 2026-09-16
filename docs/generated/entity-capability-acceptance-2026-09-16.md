# 公共实体能力实施与验收记录

本记录对应本次对话批准的专项实施，不执行项目工程实施链，不表示整体功能验收完成。

## 已纠正的实现边界

- 现场工勘新业务实体为 `SiteSurveyEntityDO`，继承 `TenantBaseDO`；主表为 `sol_site_survey`，明细为 `sol_site_survey_condition`、`sol_site_survey_material`。
- 新实现位于 `sitesurvey/entity`，不使用 `FieldSurvey` 业务名称，不注册版本 Provider，不建立工勘修订表。
- 新工勘服务、Mapper、表单策略独立实现。原服务、旧 Controller 和旧表单策略退出运行时注册；原业务实体及业务逻辑不扩展。
- 新页面调用 `/api/v1/pms/site-surveys`；任务事实、外包关联及项目客户引用统计改读写新实现。
- 只有专用迁移 Mapper 读取 `pms_eng_site_survey`；其返回独立迁移投影 `SiteSurveyImportSource`，不引用旧 `SiteSurveyDO`。
- 删除自定义 `EntityBusinessField` 注解和重复业务字段编码。字段映射使用实体属性名、标准约束及既有 BeanUtils；旧表单编码通过绑定映射到属性。

- 需求分析新事实 API、任务／阶段关联 Provider 和执行校验使用 `RequirementAnalysisDO`／`RequirementAnalysisRevisionDO`、新 Mapper 和公共扩展／表单能力；原事实 API 与任务 Provider 退出运行时注册。
- 原 PRE-04 四个写路由已取消映射，原动态表单实例的写入、复制、完成与文件写入一律拒绝；章节附件仅保留授权只读，包含原草稿附件。
- 旧 `RequirementAnalysisExecutionBinding` 恢复原内容，新实体使用独立 `RequirementAnalysisExecutionAccess`。旧表与旧实体仅在保留的原实现、只读历史解释和专用迁移中出现。

## 实际验证

### 隔离与建表

- Docker 测试实例：`npdms-50eb-test-mysql-1`，宿主端口 `23316`，数据库 `npdms_test`；Redis 测试端口 `26379`。
- 执行前核实实际容器端口、数据库名称及 Compose 解析后的当前工作树迁移挂载路径。
- 使用当前工作树的 Compose 一次性迁移容器，未复用指向其他工作树的旧迁移容器，未停止开发服务。
- V248、V249 已在该隔离数据库成功执行。**两份文件从此不得回改；后续结构变化使用新的前向迁移。**
- 执行日志：`.run/entity-capability-migrate.log`。

### 工勘存量承接

- 测试：`SiteSurveyImportMySqlIntegrationTest`，启用参数 `-DentityCapabilityMigration=true`。测试显式拒绝非 `npdms_test:23316` 连接。
- 实际承接并重试租户 1 的 **52 条**原工勘记录，包含 **1 条逻辑删除记录**；保留逻辑删除标记和原始来源。
- 承接 **19 个表单绑定、2 条当前扩展值记录、1 个来自原表单修订的扩展定义修订**。
- 固定业务字段进入新主表，多选条件和物料进入新明细表。保留业务身份、租户、项目、并发版本、状态、确认／归档时间、位置及外包关联。
- 每个对象使用独立事务；重复执行不新增业务记录、定义、值或绑定。逐字段和逐明细对比，不使用哈希代替内容核对。
- 故意修改目标的供电条件后，重试报告 `powerSupply` 差异且不覆盖目标；该冲突试验事务已回滚。
- 全部源记录在执行前后直接内容比较一致。迁移不调用业务工期、外包、确认、归档等操作。
- 执行日志：`.run/entity-capability-survey-mysql.log`，最后结果 `BUILD SUCCESS`。

### 需求分析存量承接

- `RequirementAnalysisImportMySqlIntegrationTest` 在上述隔离库实际承接 **11 个业务对象、16 个修订、5 个附件引用**，重复执行核对通过。
- 原 `preparationId` 保留为修订 ID，编号、来源修订、完成主体／时间、项目模板来源分别保留；正文使用明确业务列。
- 公共文件能力按原不可变文件版本及引用状态承接附件；逐项核对源与目标，重试不重复生成引用，源记录执行前后保持一致。
- 首次暴露的布尔字面量映射问题已修复，后续执行 `BUILD SUCCESS`；日志 `.run/entity-capability-requirement-mysql.log`。
- 此结果覆盖隔离库现有样本；合成来源缺失、编号冲突和全部部分失败重试矩阵仍未完整执行。

### 相关代码回归

- 需求分析业务字段继承、对象拷贝、部分更新及元数据保护单测通过。
- 扩展字段值校验、工勘来源转换和内容核对单测通过。
- 新工勘八类写操作的执行授权拒绝路径、任务事实消费者及外包关联事务测试通过。
- 初次集成测试暴露的测试配置缺失和重命名残留构建资源已修正；随后真实 MySQL 承接测试通过。
- 相关日志：`.run/entity-capability-corrections.log`、`.run/entity-capability-survey-acceptance.log`。后者的首次集成测试失败由后续专门 MySQL 测试结果替代，不能将该日志整体称为成功。

- 本轮纠偏后的公共复制／必填扩展校验及业务字段映射测试通过，日志 `.run/entity-capability-review-fixes.log`。
- 新需求分析事实 API 的 5 项测试通过：实体／修订归属、草稿禁止作为完成事实、内容与有效版变化、权限撤销、来源缺失；日志 `.run/entity-capability-facts-test.log`。
- 任务关联、原表单只读、原附件只读、新命令头及 PRE-02 Controller 共 18 项测试通过；日志 `.run/entity-capability-boundary-tests.log`。

## 前后端切换（本轮完成范围）

用户将本轮范围限定为前后端切换，明确不做浏览器验收；以下结果不替代此前整体公共能力计划的其余完成条件。

### 已切换

- 需求分析新增 `requirement-analysis/entity.ts` API 和 `entity/EntityPanel.vue`、`EntityForm.vue`、修订／比较抽屉；业务视图独立入口、项目详情、继承项目详情均引用新页面。
- 新调用统一使用 `/api/v1/pms/requirement-analyses`。实体 ID、修订 ID、业务修订号、乐观锁分别传递；保存、完成和复制采用修订 `If-Match` 及幂等键。
- 公共字段目录提供固定字段类型；页面通过表单绑定映射回属性，固定值与真正的扩展值分开提交，保留未提交的扩展值以及 `false`、`0`、空值。
- 附件组件使用 `SOL/REQUIREMENT_ANALYSIS_REVISION` 和修订 ID；保留上传、替换、解绑、查看及冻结保护。版本比较包含不可变附件版本差异。
- 保留未保存提示、保存失败后回读核对、指定历史修订打开，以及任务／阶段执行上下文。创建草稿确认期间固定原上下文，防止误用后来切换的轮次。
- 工勘所有页面调用 `/api/v1/pms/site-surveys`；沿用现有工期、位置、物料、外包和状态操作。移除声称数据保存到旧实体的过时界面说明。
- 旧 PRE-04 写方法从准备 Controller 移除，旧命令服务退出注册，消除对已退役策略 Bean 的注入；PRE-02 与原历史只读入口保留。

### 验证与自审

- 前端 **86 项**针对性测试通过：新表单／接口、独立业务视图、任务／阶段上下文、工勘项目范围及现有交互。使用 Vitest 自定义渲染器，未启动真实浏览器。日志 `.run/entity-switch-frontend-tests.log`。
- 后端 **64 项**针对性测试通过；所有后端模块编译通过。日志 `.run/entity-switch-backend-tests.log`、`.run/entity-switch-backend-build.log`。
- Vite 前端构建通过，产物 `.run/entity-switch-dist`；测试构建环境提示未设置 `VITE_APP_TITLE`，未部署此产物。日志 `.run/entity-switch-frontend-build.log`。
- 默认后端 JAR 在重命名步骤被文件占用阻断；随后使用 `.run/entity-switch-build` 下临时构建描述及独立输出，**完整可执行 JAR 构建通过**：`.run/entity-switch-build/server/target/yudao-server-entity-switch.jar`。日志 `.run/entity-switch-backend-isolated-build.log`。没有停止或重启开发服务。
- 全量 `vue-tsc` 仍有三项与本次无关的既有错误：`CustomerFormDrawer.vue:248` 和 `ProjectCustomerOverview.vue:159` 的 `PageParam.customerId`；`customer/contacts/index.vue:199` 的未使用 `queryFormRef`。本次新增／调整文件无类型错误。日志 `.run/entity-switch-types.log`。
- 自审修复：表单渲染器导入命名遮蔽、旧命令服务的失效注入、复制确认期间的执行上下文漂移、重新办理后操作权限刷新；核对新页面不再依赖旧准备接口或动态表单实例 ID。
- 本轮只运行不依赖数据库的指定测试和编译／构建，未执行迁移、未连接运行库，未提交或推送。

## 工勘页面与模板入口纠偏（2026-09-16）

上一轮只更改了旧工勘 API 的地址，项目详情和模板业务视图仍挂载原页面，不能视为完整的前端切换。本轮按用户指出的两个入口纠正，仍不做浏览器验收。

- 新增独立 `delivery-business/site-survey` 页面及其表单、物料、工期、转包交互；项目详情“工勘准备”、继承项目详情、`SOL_SITE_SURVEY` 模板业务视图统一引用新页面。
- 新页面只引用 `api/pms/engineering/site-survey/entity.ts`。外包、领料、采购、换货的工勘来源查询和返回入口同步使用新能力；保留原 URL、权限和执行上下文。
- 旧页面及其 API 保留原地址；新功能不再通过旧页面或旧 API 模块运行。项目模板组件目录移除“原业务页面”标记。
- 新接口使用 `businessValues`、`extensionValues` 分开读写。固定业务字段按公共反射目录映射到实体列及明细；扩展存储只接收真正动态值。前端读取服务端 `fieldCatalog`、`fieldBindings`，不再维护固定字段清单或按 `extra_` 前缀决定存储位置。
- 原发布表单的 `extra_*` 控件编码保持原义，由后端表单绑定解释为当前业务属性；无需修改不可变表单修订。
- 新增 `V250__site_survey_entity_page.sql`，将现有独立菜单的组件位置切换到新页面，保留菜单身份、路由和权限。**本轮未执行该迁移，也未切换运行实例。**
- 自审修复表单模型更新触发重复加载的问题；只在对象、表单修订或只读状态变化时加载。新增运行时测试验证编辑后固定值、扩展值及原绑定保持正确。

### 本轮实际验证

- 前端 **90 项／12 个文件**通过，覆盖新入口、项目／模板业务视图上下文、表单编辑、物料和外包直接消费者。日志 `.run/survey-entry-switch-tests.log`。
- 后端 **67 项不同用例**通过：第一次 66 项；字段服务增加工期冲突用例后，该服务 6 项再次通过，其余 60 项复用未变化结果。日志 `.run/survey-entry-backend-tests.log`、`.run/survey-entry-fields-tests.log`。
- 后端独立可执行 JAR 构建通过，日志 `.run/survey-entry-backend-build.log`。产物仍位于 `.run/entity-switch-build/server/target/yudao-server-entity-switch.jar`。
- 前端构建通过（31.39 秒），日志 `.run/survey-entry-frontend-build.log`。全量类型检查仍仅有前述三项既有客户模块错误，本轮新页面及直接消费者无新增类型错误；日志 `.run/survey-entry-types.log`。
- 未执行浏览器验收、数据库写入、部署、服务重启、提交或推送。

## 原完整计划的剩余范围

- 需求分析合成历史边界样本、完整版本事务闭环与全部迁移失败重试验收。
- 公共能力管理界面及其完整接入；本轮需求分析／工勘前后端入口切换已经完成。
- 浏览器验收按用户本轮要求不执行，不属于此次切换的完成条件。
- 整体方案最终代码自审、并发／事务／冻结保护验收及正式切换说明。

此前存量承接仅使用隔离测试数据库；本轮页面纠偏未操作数据库。真实运行库尚未迁移或切换；未提交或推送代码。

## 服务重启与启动问题修复（2026-09-16 09:57）

- 用户明确要求重启。沿用当前工作树实际运行端口：前端 `19081`、后端 `59280`；替换为本轮构建的可执行 JAR，未启动默认开发端口或停止数据库／Redis。
- 最初将数据库拒绝连接判断为“凭据无效”不准确。重建后的 MySQL 容器环境声明 `npdms_app`，但既有测试库连接配置使用 `npdms_test_app`。通过原 `npdms-50eb-test-migrate-1` 中已有凭据连接 `npdms_test` 验证成功；未变更任何数据库账号或密码。
- 本地启动入口改用 `.run/start-entity-backend.ps1`，读取已有测试库连接配置，保留原加密密钥和 JVM 参数。
- 修复附件策略与公共表单服务的启动循环依赖：附件策略通过 `ObjectProvider<EntityFormApi>` 在检查附件时解析表单服务。新增 Spring 容器依赖测试通过。
- 修复工勘新服务 `@Resource` 按旧名称误注入旧 Mapper：注入名称改为 `siteSurveyEntityMapper`。新增新旧 Mapper 同时注册的装配测试，并回归工勘写权限、字段、工期冲突，共 15 项通过。
- 两次针对性构建均成功；日志 `.run/service-restart-dependency-build.log`、`.run/service-restart-wiring-build.log`。
- 后端启动完成：PID `34472`，`http://localhost:59280/actuator/health` 返回 HTTP 200、`status=UP`；前端 PID `24836`，`http://localhost:19081/` 返回 HTTP 200。PID 记录已更新。
- 启动日志 `.run/service-restart-backend.out.log`、`.run/service-restart-backend.err.log`；未执行数据库迁移、浏览器验收、提交或推送。

## 权限、工勘保存与 Owner 归属修复（2026-09-16）

### 本轮边界

按用户最后澄清执行：复用原业务逻辑，不等于复用原业务类。权限、模板策略、任务／阶段适配与执行区逻辑复制到独立业务 Owner 内，仅调整业务实例、修订与完成事实来源。公共项目范围、参与人、任务执行、动态表单、文件和审计 API 继续复用；新业务运行路径不访问旧业务实体或表。旧 PRE-04 命令／Provider 仍不注册，不恢复旧写入口。

- 新需求分析使用 `RequirementAnalysisAccess`、`RequirementAnalysisExecutionAccess`、`RequirementAnalysisFormPolicyProvider` 和 `RequirementAnalysisEntityBusinessObjectProvider`。复制原有作用域、经理资格、任务／阶段与来源轮次校验；原任务契约 16 项用例以新实体夹具保留在 `RequirementAnalysisEntityBusinessObjectContractTest`。
- 新工勘在 `sitesurvey/entity` 内持有 `SiteSurveyEntityWriteAccess` 和 `SiteSurveyEntityFormPolicyProvider`；前端执行上下文和业务跳转逻辑保留在新工勘目录。固定字段清单来自所属 Owner 的字段目录，不额外复制业务字段枚举或赋值映射。
- 需求分析经理事实不可用时，沿用原读取逻辑：只关闭编辑能力，正常项目读取不被经理资格异常中断；草稿、写入、当前经理和执行轮次限制继续生效。
- 工勘多选／集合字段缺值时初始化为空数组，扩展 checkbox 同样处理，保留 `false`、`0` 与既有值。以真实 FormCreate 引擎和 checkbox 适配器、自定义 Vue 渲染器验证选择及清空，不使用浏览器。
- 首次保存带扩展字段的已发布表单，先绑定并复用／建立对应不可变定义，再保存扩展值；定义从已授权表单描述生成。只有固定字段的表单不创建扩展定义和值。既有定义冲突仍拒绝，不覆盖历史定义。
- 实际 API 写入发现新入口遗漏审计关联 ID：`OperationAuditApiImpl` 收到 null 后抛异常，导致整个保存事务回滚。按原入口 UUID 方式补齐需求分析、工勘及承接入口的操作上下文，保留原审计实现。

### 实际验证

- 前端 92 项／13 个文件通过：`.run/entity-owner-frontend-tests.log`。最终 Vite 构建通过（32.79 秒）：`.run/entity-owner-frontend-build.log`。
- 后端主体回归 183 项通过，1 项数据库迁移用例未启用：`.run/entity-owner-final-build.log`；新 Owner 执行区／权限另 17 项通过：`.run/entity-owner-execution-tests.log`。表单策略增加附件规则用例后，再次验证受影响服务，记录于 `.run/entity-owner-schema-tests.log`。
- 类型检查仅有原三项客户模块错误：`CustomerFormDrawer.vue:248`、`customer/contacts/index.vue:199`、`ProjectCustomerOverview.vue:159`，见 `.run/entity-interaction-reuse-types.log`。
- 显式核实隔离 MySQL `npdms_test:23316`，沿用原凭据；通过后端 `59280` 的 API 检查无经理项目需求分析读取，以及标准工勘／带扩展字段工勘的创建、保存多选值、重开、修改与过期版本拒绝。过期版本返回 `1011001003`。验证脚本 `.run/verify-entity-interaction.ps1`，结果 `.run/entity-interaction-api-results.json`。
- 临时工勘通过正常删除接口清理；未修改既有业务记录、账号、权限授予或数据库迁移。API 实写只验证上述场景，不代表整个版本化计划已经完成。
- 未做浏览器验收，未使用项目技能、工程实施链或项目记忆；未提交或推送。

### 最终运行结果

- 本轮后端累计 201 项不同用例通过（主体 183、Owner 执行／权限 17、新增表单附件规则 1）；最终受影响服务 11 项重跑通过，最终后端可执行 JAR 构建成功。
- 最终 JAR 已替换到当前测试后端，Java PID `41552`，端口 `59280`，健康检查 `UP`；前端继续运行 `19081`。没有停止 Docker 或默认开发端口服务。
- 替换最终 JAR 后再次通过需求分析读取与两类工勘创建／修改／重开／并发拒绝的 API 验证，临时记录均已正常删除。结果以 `.run/entity-interaction-api-results.json` 为准。
