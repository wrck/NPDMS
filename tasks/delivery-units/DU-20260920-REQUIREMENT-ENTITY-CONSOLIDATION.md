# DU-20260920-REQUIREMENT-ENTITY-CONSOLIDATION

> 状态：CLAIMED
> Requirement / Feature：PRE-04 / F-SOL-003
> Owner / 协调者：当前需求分析实体合并任务
> 工作树 / 分支：E:/AICoding/Projects/NPDMS / codex/domain-migration
> 授权：2026-09-20需求方确认统一新旧实现、保留当前与历史修订存储；当前页面11项正文和8项结构化业务字段落实体字段，只有模板自定义项使用扩展字段。

## 写入边界

- engineering：requirement 包内 RequirementAnalysisDO、RequirementAnalysisRevisionDO、新增业务明细值对象及其类型处理器；RequirementAnalysisMapper.xml；RequirementAnalysisEntityProvider、RequirementAnalysisEntityQueryService、RequirementAnalysisEntityCommands、RequirementAnalysisImportService、RequirementAnalysisFormPolicyProvider、RequirementAnalysisRevisionFilePolicy；对应字段映射、查询、命令、附件批处理和持久化专项测试。
- frontend：requirement-analysis/entity/entityForm.ts及其测试；必要的EntityForm.vue数据绑定适配，保留其他任务的交底书面板改动。
- sql：新增前向 requirement_analysis_business_fields 迁移，编号实施时确认；不改既有迁移，不执行开发库数据迁移。
- 文档：本DU记录本轮结果；F-SOL-003规格中的本次字段落位差量。原DU-20260909-DELIVERY-DEMO-BUSINESS-UI的旧表/动态实例实施及历史验收保留，本轮仅承接新实体字段与该规格差量，不接管其他模板、宿主或共享PLT实施。

## 数据与协作边界

SOL继续唯一拥有正文与生命周期；当前实体与修订继承同一字段定义。PLT继续拥有表单配置、真正自定义扩展和受控文件。旧实体/冻结表单/完成历史不删除、不覆盖；旧扩展中既有业务字段仅作历史来源，通过显式映射承接，不双写。

现场培训、满意度、物料、设备等当前在途改动不纳入本任务。复用既有公共API，不增加第二套表单、权限或版本框架。固定测试设施使用前核实占用；不停止其他任务服务，不清库，不推送。

## 完成条件

固定字段保存与回读、旧绑定和历史兼容、修订复制、扩展隔离、租户/权限/并发失败路径及附件批处理定向验证。数据库与浏览器未实际执行的部分如实保留，不能用编译代替业务验收。

## 追加授权与边界（2026-09-20）

需求方确认旧 sol-requirement 的 BUSINESS 写入口收敛为历史只读，INTERFACE 保持功能。追加 RequirementServiceImpl、ErrorCodeConstants 中专用错误码、旧 engineering/requirement/index.vue 及对应测试；不删除旧记录、不转换旧状态。实体收敛同时覆盖 RequirementAnalysisFields、EntityController 比较投影及 ImportMapper 类型映射。

## 本轮实施与验证结果（2026-09-20）

- 本地代码增量：统一当前/修订19项业务字段；V321新增8列并精确承接旧扩展来源；旧表单别名统一投影，固定字段禁止扩展双写，复制与对比去除旧固定字段扩展副本。历史表、来源扩展、冻结配置和状态保留。
- 性能回归：旧批处理能力未被删除，但独立修订文件Policy没有承接批量入口，导致重复Owner/表单解析；补齐同一次请求、同租户/用户/修订/动作/执行上下文的检查复用，各字段仍独立核对，权限不跨请求缓存。此前扩展校验范围收窄的审查发现不属于已修复结论，未更改公共PLT校验。
- 旧BUSINESS的create/update/delete/submit/markEffective/archive服务端全部拒绝，保留read/page；INTERFACE原状态操作和更新保留，禁止切换成BUSINESS。旧页面新增默认为INTERFACE，BUSINESS仅查看，富文本Editor显式readonly。
- 后端定向65项有通过证据：原61项中H2方言适配修复后单独复跑Mapper通过；追加旧入口3项、修订复制1项通过，最后Mapper及Owner上下文9项通过。命令为`mvn -pl pms-module-engineering -am test -Dtest=<定向类> -Dsurefire.failIfNoSpecifiedTests=false`；日志`.run/requirement-entity-tests.log`、`requirement-fields-mapper-tests.log`、`requirement-legacy-tests.log`、`requirement-entity-final-tests.log`。最初H2失败未当作通过，后续只调整测试内SQL字面量方言，生产SQL未降级。
- 前端4文件11项通过：entityForm.spec.ts、EntityForm.runtime.spec.ts、EntityPanel.runtime.spec.ts及旧页index.runtime.spec.ts；使用`vitest.pms-file.config.ts`，日志`.run/requirement-entity-frontend.log`和`requirement-legacy-ui.log`。属于Vue运行测试，不是真实浏览器。
- MySQL会话临时表验证V321成功：复用V248结构创建带专用前缀的TEMPORARY表，执行V321同构SQL；断言当前/历史值独立、JSON列表/明细/null正确、租户/Owner隔离、源扩展及版本/状态不变；连接结束临时表自动释放。日志`.run/requirement-mysql-temporary-test.log`，复跑脚本同名前缀.py。未触碰持久业务表、未执行Flyway、未修改flyway_schema_history。
- 迁移编号：发现并行物料任务新增V320，已将本任务迁移改为V321并更新Mapper测试入口，未修改物料迁移。
- 未完成：实际目标库迁移、应用重启部署、真实页面加载耗时前后对比及保存/重开/完成/历史浏览器验收。浏览器runtime依赖browser-service.mjs缺失；实际Compose实例13306/npdms与旧固定测试说明23316/npdms_test不一致，未将开发库默认为可迁移测试库。当前不宣称运行环境已修复或Feature Done，不改变历史Gate。
- 自审通过本次差量与`git diff --check`；未执行独立审查。仅认领及边界记录已在master提交（ebc3d981a、3f5022143），实现及本轮结果未提交、未推送，保留其他任务改动。

## 加载异常运行修复（2026-09-20 23:35）

用户报告需求分析加载异常后，实际59191后端日志确认`Unknown column transmission_current_options`，新JAR已启动而当前分支测试库未迁移。按实际启动脚本及Compose交叉核对，目标为npdms-domain-test / 24306 / npdms_domain_test，不是13306开发库；Flyway info确认仅V321 Pending，V320已成功。

先对需求当前、修订和扩展来源三表执行single-transaction备份（.run/requirement-before-v321.sql），再使用现有Compose及两份环境配置执行`migrate -target=321 migrate`。266项迁移校验通过，成功执行唯一待迁移V321；两张需求表新增8列均存在，历史登记success=1。无reset/repair、无历史状态转换、无应用重启或其他服务停止。

实际登录59191后端读取项目992203060010和992203060001，两个需求分析工作区均code=0，分别约329ms和108ms；前者正常返回有效完成版。应用内浏览器插件仍缺browser-service.mjs，本轮读取webapp-testing技能后使用独立Playwright Chromium访问19191真实应用；首次登录导航等待超时，重试同一正常登录流程后成功。需求分析页显示有效V2、既有正文及结构化字段，工作区HTTP200/code=0，未捕获pageerror，页面没有加载失败；截图.run/requirement-load-restored.png，日志.run/requirement-browser-verify.log。此处补齐的是实际迁移和加载验收，不将其扩大为保存/完成/历史写入完整验收。

## HTTP浏览器白屏与切页残留修复（2026-09-20 23:41）

- 需求方补充：白屏、持续转圈、切换页面仍保留需求分析内容。追溯23:35:10日志，首次异常是RevisionFiles.vue初始化调用`crypto.randomUUID is not a function`；后续emitsOptions和日志格式化异常是伴随错误，未据此修改Vue或Vite配置。
- 局域网真实HTTP复现：10.210.0.11:19191，isSecureContext=false，crypto.randomUUID=undefined。修复前需求分析初始化捕获4次同类pageerror；先前localhost加载验收未覆盖此访问方式。
- 最小修复：RevisionFiles初始化/上传后换槽、dynamicFormRuntime稳定命令标识、PmsFileUploader上传尝试及PmsFileReferenceList移除引用使用已有`@/utils.generateUUID`，不再直接调用只在安全上下文提供的randomUUID。保留同一意图重试key、文件权限和API协议。master边界记录a5bf9e599。
- 修复后同一HTTP浏览器加载工作区HTTP200/code=0、无pageerror；需求分析→工勘分工→需求分析→物料换货→需求分析→基本信息真实切换，需求分析可见性依次false/true/false/true/false。日志.run/requirement-insecure-before.log、requirement-insecure-after.log；截图.run/requirement-switch-before.png为脚本最终成功切到基本信息的截图（文件名沿用初次脚本，不作为修复前证据）。
- 新增RevisionFiles.runtime.spec.ts验证randomUUID缺失时挂载及命令重试幂等；连同EntityForm、文件引用和执行上下文4文件14项通过，日志.run/requirement-uuid-tests.log。自审改动仅UUID来源替换，未升级依赖、未修改数据库、未重启后端。
- 已崩溃的旧浏览器实例需要完整刷新以重新挂载组件；本轮不强制刷新其他用户页面，避免丢失其尚未保存输入。代码未提交、未推送。

## 全局兼容修复授权（2026-09-21）

需求方明确要求全局修复crypto.randomUUID。追加以下前端文件，只替换UUID生成来源并补充import及针对性测试，保留业务、幂等、权限与并行修改；复用utils/index.ts的generateUUID，不改服务端Java UUID或Node测试工具。

- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/commerceInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/customerInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/DecisionTableConditionEditor.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/decisionTableModel.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/editorModel.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/ruleTreeModel.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/StageGraphDesigner.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/templateCanvasModel.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/TemplateContentEditor.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/versionRuleModel.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/submissionIdempotency.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/preparationInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectGovernancePanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectNormalClosurePanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectPlanEditor.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectProgressPanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectSplitWizard.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectStageGatePanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectStageStatusPanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTreePanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/business-view/index.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/dynamic-form/components/PmsFileArtifactField.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/training/TrainingPrintTemplates.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/contacts/index.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssignCustomerDialog.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssignProjectDialog.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance-report/detail.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/acceptance-report/ReportDraftEditor.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/satisfaction/TaskPanel.vue`
- `yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/operationHost.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/integration/index.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-templates/designerAssets.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-templates/directBinding.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/acceptance/satisfaction/index.ts`

验证可覆盖上述同目录现有测试，新增公共UUID兼容/禁止浏览器直接调用回归测试。

## 全局UUID兼容修复结果（2026-09-21）

- 按需求方全局授权新增替换41个前端文件71处原生调用，全部使用现有generateUUID；加上上轮4文件修复，浏览器src中除utils/index.ts能力检查和受保护分支外无randomUUID调用。不修改Java UUID、Node侧工具或历史文档；未引入全局polyfill，不改变业务协议、键格式前缀及重试复用时机。各并行任务原有改动保留。
- 新增utils/uuid.spec.ts：原生分支、HTTP仅getRandomValues分支、缺crypto分支、1000个生成结果格式/无重复、实际客户默认工厂重试，以及扫描禁止浏览器业务代码重新直接使用randomUUID。
- Vitest 31文件168项通过。最初目录式运行误纳2个node:test文件，已改用原生Node执行，operationContract 31项通过、executionConfiguration 2项通过；后者因模型新增公共工具导入而需要解析@/utils，已在测试入口用Node registerHooks按项目别名解析，未改业务模型为测试副本。
- 另外4个模板运行测试（gateReferencesEditor、operationContractReferences、taskApprovalBinding、taskManualHandling）在加载真实auth/WebStorageCache链时因window未定义而无法初始化，不计通过；未降低断言。日志.run/uuid-global-tests.log、uuid-global-node-tests.log、uuid-global-node-retest.log。
- 全量vue-tsc已执行，报告配置/安装/联调页面8项类型错误（DeviceArchiveApi缺失、string|number到number不匹配），均位于本轮未修改文件；未把全量类型检查标为通过，未扩大本次修复。日志.run/uuid-global-typecheck.log。
- 真实HTTP验证仍为19191/59191、npdms-domain-test：10.210.0.11的isSecureContext=false且原生randomUUID undefined，登录后需求分析、项目模板列表/新增弹窗正常。浏览器加载真实源码模块在局部临时对象上调用模板规则、决策表、客户意图及项目提交工厂，生成4个不同键、重复请求键稳定，无pageerror；无创建/发布业务数据。日志.run/uuid-global-browser.log，截图.run/uuid-global-template.png。
- 自审确认更改为import和生成来源替换，保留后续字符串处理和意图存储；git diff --check通过。全局边界认领提交16a48b962，实现未提交未推送。旧浏览器崩溃实例仍需完整刷新。
