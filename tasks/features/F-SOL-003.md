# F-SOL-003 需求分析动态表单组合与版本冻结

## 2026-10-13 模板修订收敛（待运行生效）

- 按需求方确认，将网络拓扑附件化、其他附件入口移除、运行业务表格行录入、标题与普通文本样式纳入 `template-20261013.json`；原 `demo-template.json` 保留。
- 前向迁移 `V465__requirement_analysis_template_revision.sql` 新增已发布修订并守卫推进标准模板发布指针；不更新旧模板修订、实体绑定、业务数据或项目冻结执行快照。迁移尚未执行。
- 页面移除字段过滤、类型/标题改写、模板校验清空和标签位置覆盖，仅保留业务文件上下文适配及权限/只读保护。旧来源模板可按原结构显示；冻结修订不自动切换到新默认模板。草稿仍可部分保存，完成规则仍由Owner校验。
- 验证：前端模板编解码、旧修订保留、隐藏编辑保护、表格操作共21项通过；后端模板兼容、拓扑附件完成规则及旧成果事实重验通过。部署和真实浏览器验收尚未执行；运行生效不能由源码或单测结果代替。

## 2026-09-22 当前补正：展示模板与业务能力分离

- 依据：需求方进一步明确“模板只是数据展现方式，可以随时切换；项目模板指定具体展示页面，业务操作是基础能力”。替代下方同日首次冻结模块表单的临时方案，覆盖PRE-04 / F-SOL-003；不晋级历史Feature Done。
- 业务：独立首次创建只记录租户/项目业务来源，不读取模块表单或绑定Schema；正文/修订仍由SOL持有。附件使用稳定业务字段用途码；显式节点仍检查业务目标、授权和执行规则，但展示修订不同不拒绝业务复用。成果事实校验业务版本、项目数据范围、文件与来源完整性，读取不依赖来源节点当前的展示配置。
- 展示：PLT `EntityPresentationApi`返回按实体读取权限过滤的同租户启用发布模板；SOL过滤未知/重复字段、结构类型不匹配及无映射候选。原始呈现记录保持不变，新增只读候选接口和页面选择器。基础业务表单复用既有Demo内容，不需要数据库模板；节点模板或模块参数仅为默认展示偏好，缺失不阻断。换模板按实体字段码保留未保存及隐藏内容，不写绑定、扩展定义或业务对象。展示Schema不能增加草稿保存前置，完整性由SOL完成命令校验。
- 针对性验证：89项后端检查通过（含历史来源读取、基础附件、无模板创建、权限/租户/状态/并发、节点业务约束、模板目录与不兼容候选）；前端13项运行时/映射检查通过，包含隐藏编辑保留、false/0/空列表、只读完成版保护；本地JDK25应用打包通过。扩展检查曾命中旧“展示修订不同即拒绝业务”断言，已按用户明确的契约更新并重验；真实业务与安全失败断言保留。
- 真实浏览器：原项目992203060018、19191页面可选择原始模板、基础业务表单、标准模板和部分字段模板；临时编辑项目目标后切到仅有项目背景的模板，再切回标准模板，隐藏编辑完整保留，页面无运行错误。临时编辑未保存，重新加载已清除；数据库修订2102231190901227522仍为DRAFT/version2（保留用户已填写内容和附件）。候选目录排除重复字段的历史示例。截图`.run/requirement-presentation-switch.png`；当前后端为59191，同一Compose测试库，未改写历史数据。
- 限制：本轮不代用户保存测试正文、完成或修订原项目；真实业务完成正向路径采用Owner测试证据，不能称为全流程浏览器验收。全量`ts:check`仅报既有安装页面`installation/index.vue:340,341`缺失`DeviceArchiveApi`，本次范围未报类型错误。上轮既有V101迁移文本断言与12项需显式启用的MySQL集成测试边界仍保留，本轮未重跑。其他业务模块尚未按本次边界逐一验收。


## 2026-09-22 补正：无项目模板绑定时独立开始需求分析

- 依据：落实用户此前明确的“业务操作独立，只有项目模板明确配置的规则才控制对应操作”，并经本轮再次确认。追溯沿用本Feature的PRE-04需求及规格同日补正；不晋级历史Feature状态。
- 实现：`RequirementAnalysisInitialConfiguration`从租户模块参数取得PLT已发布兼容表单，事务内复核并冻结；`RequirementAnalysisAccess`及`RequirementAnalysisExecutionAccess`支持无项目模板、无节点的首次创建和后续操作。历史快照保持原来源；显式节点关联仍匹配冻结表单。成果事实API与自动关联消费者支持新快照；初始表单绑定补传冻结事实版本，配置缺失在页面明确反馈。
- 配置：V338仅为租户1补既有标准表单参数，不覆盖已有参数。通过指定Compose `npdms-domain-test`、MySQL24306/`npdms_domain_test`执行，Flyway validate通过，首次执行1项，重复执行0项。
- 验证：后端定向72项通过（独立创建24、配置9、成果事实9、Owner操作上下文8、显式业务关联22）；前端EntityPanel运行时4项通过；JDK25全应用打包与59191运行启动通过；diff检查通过。自审覆盖租户/项目身份、权限及经理条件、单草稿、历史快照和显式节点表单一致性。
- 真实浏览器：19191原项目992203060018无需求分析绑定，成功创建V1草稿2102231190901227522，重新加载仍可编辑；数据库确认项目模板来源两列为空，冻结模块表单992203010001/修订992203020005。空白保存触发背景、目标、网络拓扑必填提示；未编造业务正文或冻结真实项目成果，因此本次不声称真实保存/完成正向闭环。保存、完成、修订及历史保护采用上述后端测试证据；没有全量验收其他业务模块。
- 限制：扩展运行RequirementAnalysis*Test时，既有`RequirementAnalysisMigrationContractTest`第130行对V101旧菜单SQL的文本断言失败，相关源码与迁移未改动；12项需显式启用的MySQL集成测试未运行。日志及浏览器截图保存在本地`.run/requirement-independent-*`，不进入版本库。


## 当前增量：实体数据驱动表单（2026-09-09）

按用户本轮要求调整并以需求分析验收：普通正文唯一保存于SOL实体，PLT保留模板/文件运行上下文，不再提供业务普通值写入出口；已有完成版和旧PLT来源值保留。先修订本Feature/SDS共享契约，再实现、前向精确回填、定向校验及真实浏览器“加载—保存—重开—完成只读”验收。当前增量实施中；下方既有Implementation Complete只代表原数据分工，不自动覆盖本次差量。认领见DU-20260909-DELIVERY-DEMO-BUSINESS-UI。

### 本次实现与验证记录（2026-09-09）

- 已实现：`PreparationDO.entityValueJson`及根Mapper实体CAS；创建、PATCH、完成、修订、查询、对比与SCH事实从SOL普通值读取，PLT使用`inspectEntityData`只读投影。V210精确匹配租户、Owner、对象键及PRE-04后复制旧来源值，不修改旧PLT正文；新业务上下文正文为空。旧手工表单继续由PLT保存。
- UI沿用需求分析现有页面；保存后必须回读实体，回读失败保留填写/操作意图；实体contentVersion变化可刷新，完成版不恢复待保存缓存。按`frontend-ui-engineering`补充状态反馈及FormCreate行操作键盘适配。
- 已通过：SOL/PLT定向后端45项（含真实MySQL临时表迁移、实体CAS/租户/完成版保护及回滚）；应用级MySQL5项（实体正文保存/克隆、完成版保护、引用事件幂等、失败回滚、并发完成，保留既有迁移断言）；前端运行时及交互23项；规格契约9项。应用级测试使用真实SOL/PLT实现和MySQL，PROJ/权限由测试替身提供；未参与的生成/辅助上传服务明确断言零调用，不能作为全栈授权或上传验收。
- 构建：`mvn -o -B -pl yudao-server -am -DskipTests package`、Vite acceptance构建、`pnpm run ts:check`通过。首次类型检查因缺少自动导入声明失败，按既有构建流程生成后复查通过。测试装配补齐现有FileArtifactApi新增依赖及FILE_READ查询权限，不修改生产授权规则。
- 迁移：固定Compose `npdms-50eb-test`、MySQL23316/`npdms_test`已由本工作树`docker compose ... run --rm --no-deps migrate -target=210 migrate`验证179项历史迁移并前向执行V210成功；无reset/repair、无旧值覆盖。临时表测试仅连接内写入；应用级测试专用对象已清理。
- 尚未执行：真实浏览器验收及320/768/1024/1440布局/键盘检查。用户允许分支独立59480/19481，但前端启动命令被执行环境policy拒绝，未绕过、未启动分支服务、未停止p903。需本机服务可用后再进行“加载—保存—重开—完成只读”和实体数据库落值核对；本次差量**未验收完成**，不晋级历史Feature状态。
- 可复跑入口：`RequirementAnalysisEntityDataMigrationIntegrationTest`、`RequirementAnalysisApplicationMySqlIntegrationTest`（必须`skipITs=false`及固定测试库环境变量）；`vitest.pms-file.config.ts`下`RequirementAnalysisDynamicForm.runtime.spec.ts`及`requirementAnalysisInteraction.spec.ts`；`python scripts/tests/test_fsol003_dynamic_form_amendment.py`。本机日志在`.run/delivery-demo/entity-*.log`；实现未提交、未推送，原有`.gitignore`修改保留。

运行续验（2026-09-09 14:03）：应用内Browser已恢复并实际访问19481，页面返回`ERR_CONNECTION_REFUSED`。本分支后端由本任务启动，PID26472、59480监听，`/actuator/health`为`UP`，连接固定测试MySQL23316/Redis26379；复用13:33已通过的本分支JAR构建。前端启动仍被执行环境以`blocked by policy`拒绝，去除非必要开关覆盖后仍拒绝；因此真实UI业务闭环仍未执行，不替代为其他服务或静态页面。未停止p903，后台服务日志为`.run/delivery-demo/entity-backend.log`。

运行续验（2026-09-09，本轮应用内Browser）：基线`7d8f89a3`。复用本分支已通过的`dist-entity-acceptance`构建，使用既有Vite preview在`127.0.0.1:19481`启动实际应用，`/admin-api`代理指向本分支`localhost:59480`；后端健康检查实测`UP`。应用内浏览器已显示真实登录页，初始化时捕获的控制台error为空。未停止或占用其他任务的59280/19081服务，未修改生产实现、测试数据或凭据。此前“前端无法启动”的环境阻塞在本轮已解除；当前等待用户在指定应用内浏览器登录。加载—保存—重开—完成只读、实体落值及四视口验收仍未执行，不记为通过，不晋级本次增量或改写下方历史Done。

### 2026-09-09 真实浏览器续验与局部修复

- 本轮结论：**标准模板下的实体正文核心闭环通过；Demo扩展实体闭环仍为BLOCKED_BY_DEPENDENCY**。以下记录接续上方等待登录检查点，不改写原Feature历史Done，不宣称整个Demo/S1～S6、SCH-01或本次增量全部验收完成。
- 环境：用户提供账号后通过应用内Browser真实登录`127.0.0.1:19481`，关闭“记住我”，未保存密码；真实后端59480连接固定`npdms-50eb-test` MySQL23316/`npdms_test`与Redis26379。仅使用专用项目`992203060001`，无请求替身、无直接改状态、无清库/迁移/生产访问，也未停止其他任务服务。
- 真实正向：创建V1，空必填保存触发三项客户端校验且保持草稿；填写三个真实Editor字段；未保存刷新显示放弃确认，取消保留输入；通过SOL保存、刷新页面重开回读；完成冻结V1；从有效版创建V2并继承正文；修改、保存、完成V2；历史抽屉及版本对比正确显示V1/V2背景变化、目标/拓扑不变。
- 真实并发与存储：两窗口同版加载V2，A先保存后B用旧版本保存；B未覆盖A的数据库值并保留本地输入，回读后重试成功。最终V1=`2097570155619299330`（历史完成、contentVersion=3），V2=`2097572343187910657`（唯一有效完成、contentVersion=3），无当前草稿；SQL直接确认各自`entity_value_json`保存不同背景、共享目标/拓扑，两个PLT上下文`value_json`均为`{}`，正文保存不递增文件上下文版本。V1正文和完成时间未被V2覆盖；有效指针切换按原契约推进根version。
- 修复一：真实保存后概览卡片仍显示旧contentVersion，而详情已刷新。`ProjectRequirementAnalysisPanel.vue`改为同时回读概览及详情，成功后一起更新；不调用会卸载保存中表单的`loadDetail`，任一回读失败保留原界面。新增父面板运行测试验证延迟/失败回读、概览同步及不卸载，不替代真实业务验收。
- 修复二：完成版隐藏保存动作但11个旧Editor仍为`contenteditable=true`，因为该组件接受`readonly`而非FormCreate的`disabled`。`RequirementAnalysisDynamicForm.vue`仅在本域运行规则中映射`readonly/editorConfig.readOnly`，保留模板原有只读值；权限变化原位更新且不替换规则/正文。运行测试覆盖权限切换、配置保留、未保存值保留；最终构建浏览器确认完成版可编辑区为0、保存按钮为0、附件写入口不显示。不修改公共Editor、PLT渲染器或后端授权。
- 真实REST补验：完成实体PATCH拒绝（`1011002003`）；通过手工实例API读取/写入业务上下文均拒绝（`1010003008`）；未登录读取拒绝（`401`）；拒绝后重新读取完成正文与根版本均不变。这些是实际后端API验证，不冒充非经理登录或跨租户浏览器验收；已有适用权限/租户后端测试证据沿用。
- 验证：本轮最终3文件30项Vitest通过；acceptance构建通过，`vue-tsc --noEmit`通过，`git diff --check`通过。实际完成版320/768/1024/1440视口的document宽度均不超视口，已目视320正文/历史及1440正文；最终截图`.run/delivery-demo/entity-browser-desktop.png`，构建与类型检查日志为`entity-browser-build.log`/`entity-browser-typecheck.log`。捕获的浏览器console error为空；CDP缓冲显示相关读取/克隆/保存HTTP200，但出现过旧事件淘汰，故不宣称全程完整网络错误统计为0，也不宣称全部键盘/WCAG已通过。
- Demo扩展依赖：通过真实PLT REST新建、保存并发布本仓30字段Demo配置（模板`2097573726276431873`、R1=`2097573726293209090`）；复制旧验收项目模板到专用草稿`993009001590`并使用数值ID精确绑定。项目模板validate明确拒绝：缺definitionRevisionId、阶段start/normalClosure及精确规则引用，旧T-START不符合现行基本操作规则，且BPM流程定义不可用；修正测试工具的版本字段及数值ID后，PRE-04表单自身不再报无效，剩余为上述项目模板依赖。测试库现有六份新F-PROJ-009场景模板也均为DRAFT，不能擅自发布或从旧sortOrder臆造图/职责来绕过。新项目模板未发布；未被绑定的Demo表单已通过正常命令停用，保留修订和草稿证据，不删除历史。API调试脚本位于被忽略的`.run/delivery-demo/entity-runtime-api.ps1`，仅从进程环境接收登录凭据。
- 剩余：由模板Owner提供符合现行契约的Demo验收项目及精确冻结绑定后，继续多选/明细/显式空值的实体保存、重开与键盘/四视口；真实附件字节流程与完整非经理/跨租户浏览器矩阵本轮未执行，不能由标准模板正向结果代替。当前改动未提交、未推送，原`.gitignore`修改保留。

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / GO（规格整改提交 4d04dbd63bbd01683416563bece31da6cd53f849）`
> Technical Plan Gate：`PASS / NPDMS-FSOL003-TECHPLAN-20260828-01-R2`
> Implementation Done Gate：`PASS / NPDMS-FSOL003-DYNAMICFORM-IMPLEMENTATION-20260828-01-R1`
> Requirement ID：`PRE-04（V1/P0）`
> Feature Spec：`specs/features/F-SOL-003-requirement-analysis-versioning.md`
> Feature物理契约：`specs/features/F-SOL-003-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-28-f-sol-003-dynamic-form-composition-and-versioning.md`
> 锁定规格提交：`44d172b31de089d96d172f82368f1467bf059259`

## 当前最小工作单元

- 按已批准新中文 Technical Plan 完成一个整体正向闭环；全部接通后统一执行整体测试和验收。

> 检查点（2026-08-28）：基线`44d172b3`、实现`70a278b5`；Implementation Done以`NPDMS-FSOL003-DYNAMICFORM-IMPLEMENTATION-20260828-01-R1`通过，固定MySQL、前后端全量、39模块打包、两轮真实浏览器及FILE_READ权限回归均通过；无阻塞；下一步保持开发阶段，识别最近一个前置已满足但未通过的开发Gate。

## 2026-10-13 需求分析模板 R5 局部修订

- 用户明确批准移除传输说明、IP资源情况说明、运行业务情况说明及运维管理文本框，保留运维多选；流量/IP独立标题与响应宽度；业务表格按页面适配；日志留存为五项多选。
- 新增 template-20261013-r5.json 与 V466；保留 V465、R4、原始模板及实体历史。日志选择复用文本存储，保存和查看保留原有自由文本；只读禁止修改。
- 定向四文件18项 Vitest 通过；V466 在已确认 npdms_domain_test 实例仅新增一项迁移成功，无后端重启。浏览器 OpenCLI 扩展未连接，实际宽度、编辑保存重开及历史页面验收未执行，不能标记 UI 验收通过。

## 2026-10-14 PJT2026000061 冻结提示与任务同步修复（后端待部署）

- 范围：用户指定项目 `PJT2026000061 / 2110018733130579971`，模板 `993009900037 V1`；S0、S1联系人/工勘共完成 `3/17`。需求分析V1正文已保存并保留，冻结原只报泛化错误。本轮同时修复窄屏任务入口、联系人保存后的状态/进度回读及旧提交动作冲突，不改变既有Feature状态。
- 冻结根因：当前实体 `2110296787568537602`、修订 `2110296787568537603` 为 `DRAFT V1 / version3`，项目背景、目标非空，但网络拓扑附件为空。[既定完成规则](../../specs/features/F-SOL-003-requirement-analysis-versioning.md)要求有效 `AVAILABLE/ACTIVE` 拓扑附件，不能放宽。`RequirementAnalysisEntityProvider`、`RequirementAnalysisRevisionFiles` 保留错误码 `1011002006`，改为指出缺少项目背景/目标，或“需求分析未完成：请在‘网络拓扑’上传至少一个有效附件”；`RevisionFiles` 显式提示必需并默认展开，历史冻结版只读，`EntityPanel` 优先显示Owner错误原因。后端新提示尚未部署，真实浏览器复核待执行。
- 联系人同步：原组件发出 `changed`，`ProjectFlowPanel` 只监听 `updated`，且规则提交后异步重评。现接通两类事件并回读工作台、父层项目统计及任务树，最多轮询30秒；同步中或刷新失败隐藏旧动作，切换任务取消旧回读。5条运行测试覆盖两类事件、延迟完成、失败和任务切换。真实浏览器保存既有E2E联系人原值成功，任务仍为 `DONE / 100%`、无旧提交按钮、未新增联系人，项目仍为 `3/17`；该已完成任务不能证明真实首次自动完成，异步完成仅有组件测试证据。
- 窄屏入口：原 `<=1024` 样式隐藏任务树且无替代入口。按用户选择，`index.vue` 复用 `ProjectTaskTree` 和现有离开保护，以原生 `details` 折叠“选择任务”，支持展开切换阶段/任务并在选择后关闭。真实浏览器在 `10.210.0.11:19191` 验证320、768、1024视口任务切换、320视口键盘收起及1440桌面，document无横向溢出；正常浏览器尺寸已恢复。后端环境固定为 `10.210.0.11:59191`。
- 已有验证：后端定向33项零失败，入口 `test.ps1` 沿用 `worktree-toolchain.ps1` 和同一工作树锁；前端3文件33项通过，前端构建成功，本轮 `git diff --check` 已通过。全量类型检查被 `cutoverTaskInteraction.spec.ts:21-24` 的6项语法错误阻断；隔离该文件后仍有43项类型错误，本轮 `EntityPanel`、`RevisionFiles`、`ProjectFlowPanel`、`index.vue`、`flowPanel.runtime` 无命中，不能记全量类型通过。日志及截图见 [本轮证据目录](../../.run/20261014-project61-repair/)，包括 `backend-tests.log`、`frontend-tests.log`、`frontend-build.log`、`frontend-types.log`、`frontend-types-isolated.log`、`contact-after-save.png` 和 `narrow-task-navigation.png`。
- 打包阻塞与后续：完整后端构建最初被 `CutoverWebConfiguration.java` 泛型错误阻断，经用户2026-10-14明确授权只将一行改为 `FilterRegistrationBean<OncePerRequestFilter> registration`。重试主代码通过，随后 `CutoverGovernanceGuardProviderTest`、`CutoverDashboardControllerMockMvcTest`、`CutoverFullFlowMySqlTest`、`CutoverTaskListServiceTest` 引用已删类导致 `testCompile` 失败（`backend-build.log`、`backend-build-retry.log`）。用户已选择由割接会话修复并通知后继续打包，不在本轮处理或跳过测试编译。尚无新冻结产物清单，本轮未部署、未重启59191；产物就绪后仍须明确批准重启，再复核后端精确错误提示。
- 验收边界：未上传虚假拓扑、未冻结或推进项目、未改变模板发布状态。局部前端已浏览器复核，后端部署及新提示的浏览器验证待完成；本轮不能记全闭环Done或Implementation Done，不改写历史状态与证据。
