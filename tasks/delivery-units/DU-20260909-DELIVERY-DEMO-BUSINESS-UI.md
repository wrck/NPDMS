# DU-20260909-DELIVERY-DEMO-BUSINESS-UI Demo领域页面与表单内容

> DU状态：`CLAIMED`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-PLT-002=TASK_COORDINATED;F-SOL-003=TASK_COORDINATED`
> Task范围：`实体数据读写＋动态表单设计/渲染，以PRE-04需求分析验收；SOL保存普通正文，PLT保留模板/文件运行上下文与旧历史；不退役手工填报、不扩展全平台迁移`
> Owner：`实现动态表单和业务操作界面 (2)（01a08426-28a9-7973-8c7e-1da6f8457fba）`
> 分支：`codex/delivery-demo-business-ui`
> Worktree：`M:/AICoding/CodexData/worktrees/bfbf/NPDMS`
> 认领基线：`3d6d6686b703638041a1007bdc7372206c9501c3`
> 认领提交：`SELF`
> 修改边界：`docs/design/08-data-model.md;docs/design/09-database-design.md;docs/design/10-api-design.md;specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md;specs/features/F-SOL-003-requirement-analysis-versioning.md;specs/features/F-SOL-003-physical-contract.json;tasks/features/F-PLT-002.md;tasks/features/F-SOL-003.md;tasks/delivery-units/DU-20260909-DELIVERY-DEMO-BUSINESS-UI.md;pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/dynamicform/DynamicFormBusinessInstanceApi.java;pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/dynamicform/dto/DynamicFormEntityDataQuery.java;pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/dynamicform/dto/DynamicFormInstancePatchCommand.java;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormBusinessInstanceService.java;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormBusinessInstanceApiImpl.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/preparation/PreparationDO.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/RequirementAnalysisRootMapper.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/query/RequirementAnalysisEntityDataUpdate.java;pms-module-engineering/src/main/resources/mapper/preparation/RequirementAnalysisRootMapper.xml;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisDynamicFormCommandService.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisDynamicFormQueryService.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisEntityData.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/requirement/RequirementAnalysisFactApiImpl.java;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectRequirementAnalysisPanel.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisDynamicForm.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisDynamicForm.runtime.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/browser/**;pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormBusinessInstanceServiceTest.java;pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/*EntityData*Test.java;pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisDynamicForm*Test.java;pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/requirement/RequirementAnalysisFactApiImplTest.java;pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/requirement/RequirementAnalysis*IntegrationTest.java;pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/requirement/RequirementAnalysisMigrationContractTest.java;sql/migrations/*__fsol003_entity_form_values.sql;scripts/tests/test_fsol003_dynamic_form_amendment.py;scripts/tests/run_fsol003_dynamic_form_browser_acceptance.cjs;scripts/tests/run_fsol003_entity_form_acceptance.cjs;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/formCreateKeyboardRows.ts;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormQueryService.java;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandService.java;pms-module-platform/src/main/resources/mapper/dynamicform/DynamicFormInstanceMapper.xml;pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormQueryServiceTest.java;pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormCommandServiceTest.java`
> 串行资源：`本次PLT/SOL共享契约与指定文件；固定MySQL23316/npdms_test、Redis26379及59280/19081使用前核对Owner；仅本次前向加列/精确回填，不清库/reset/repair，不改用户.gitignore或在途PM01`
> 旧功能范围：`NONE`
> 验证：`实体正文权威回读、PLT不双写、精确迁移/源值与文件及完成历史保护、权限/版本/必填/回滚、共享方法直接消费者编译与定向测试；真实需求分析设计/加载/编辑/保存/重开/修订验收`
> 集成记录：`NONE`

## 协调入口与依据

### 2026-09-09新任务接续：实体数据读写与动态表单渲染

- 用户已授权原协调者先收口master合并现场，再登记本次调整；合并已由669ecf99完成。现按明确清单将本DU实施Owner由旧任务01a0822c-ea82-7801-b318-39bfd843f866接续为新任务01a08426-28a9-7973-8c7e-1da6f8457fba，来源仍为bfbf，实际分支为codex/delivery-demo-business-ui；旧任务已核实空闲，来源只有用户.gitignore在途。本登记不接收或修改这些在途内容、不推送。
- 本次唯一生效写范围以上方精确清单及本节限制为准。下方PRE-04内容、工期、满意度、CUS-04等旧认领/排除和进度按发生时点保留为历史；不据此继续修改原工期/满意度页面、CUS-04或PRD。本次不改变其历史完成结论，也不把旧测试证据用于证明新的实体存储路径。
- Requirement/Feature：SOL-01支撑的F-PLT-002与PRE-04/F-SOL-003。来源方案为bfbf/.run/delivery-demo/entity-form-adjustment-plan.md，仅作实施协调输入；共享契约与字段先在本次已认领的SDS/Feature权威位置落位，再改代码。SDS08仅动态表单/需求分析有关段，排除CUS-04；SDS09仅本新增字段及精确回填约束；SDS10仅本次共享API/PRE-04读写，不扩大到其他业务。
- 数据责任：SOL的sol_preparation新增entity_value_json保存普通字段正文，沿用content_version/version；实体接口加载、合并合法PATCH、保存及回读权威版本，保存不自动完成。PLT保留FormCreate设计/发布修订、渲染配置、字段校验、精确文件运行上下文；业务上下文新建/克隆不再保存普通正文，共享只读能力按实体提供值形成展示/校验事实，不持久化调用方正文。不保留可绕开SOL继续写普通业务值的第二出口；不退役PLT独立手工填报或改变其REST行为。
- 历史与文件：前向加列，仅按同租户、SOL/REQUIREMENT_ANALYSIS/实体ID及原dynamic_form_instance_id精确关联回填旧普通值；不更新/删除PLT源值、文件字节或历史引用，不双写、不批量重置其他实体。已完成正文及版本、完成时间和来源键保留；修订从SOL实体复制正文，文件沿原克隆规则。只有本迁移文件后缀获认领，编号在实际集成时确定，不预约号段，不改旧迁移，不授权reset/repair。
- 界面：仅复用清单内原需求分析面板、FormCreate组件及交互测试；明确区分业务实体版本、模板修订与文件运行上下文，保存必须通过SOL并权威回读。保留只读、未保存保护、重试和版本冲突，不凭本地值宣称保存成功。browser目录只增加本次实体绑定验收相关文件，不新增平行页面或生产宿主/注册能力。
- 可访问性定向补充：仅新增同目录formCreateKeyboardRows.ts，为本需求分析表单的FormCreate group加减控件提供键盘/ARIA指令，挂入已认领RequirementAnalysisDynamicForm.vue；复用真实group逻辑并遵守只读/禁用状态，不修改第三方包，不增加生产包装页或路由。测试沿用已认领runtime.spec.ts及真实验收脚本，覆盖键盘可达、可访问名称与真实增删效果；旧浏览器记录中的已知限制保留为历史，本条不宣称修复已完成。
- 同包通配仅覆盖本次EntityData测试、RequirementAnalysisDynamicForm相关测试及需求分析Integration测试，不占用整个PLT/SOL测试包。现有脚本的实际位置为scripts/tests/，新实体验收脚本同目录登记；如改动公共方法另有编译消费者，不凭此清单扩到其包，先补该具体文件。现有多选值校验整改限所列服务与测试；需要其他生产文件同样精确补交。
- 手工入口旁路定向修复：仅补DynamicFormQueryService.java、DynamicFormCommandService.java、DynamicFormInstanceMapper.xml及对应两个ServiceTest文件。按既定PLATFORM/MANUAL_DYNAMIC_FORM精确手工Owner三元组限制manual列表/计数、读取及普通值更新，不把createdBy相同视为业务实例可经手工入口修改的依据；按Owner内部上下文读取/锁定及文件接口不改，不新增角色，不退役或削弱原手工填报。现有EntityData测试范围继续适用；复验须包含通过通用REST读取/修改业务上下文与已完成实体历史被拒绝，以及真正手工实例原有正向流程仍可用。此处仅登记必要写边界，不宣称旁路已修复或验证通过。
- 其他Owner：master当前巡检Task9边界与本清单无交叉；p903的原轮次在614f995c已有明确释放回执，当前p903工作树无在途修改，本次不是替它确认未知移交，也不接管其模板/通用注册/任务宿主后续职责。PLT与SOL的数据Owner仍各自保持，本实施任务只承接已列明的提供方/消费者修改与契约对齐。
- 验证：固定隔离库实测连接和专用夹具后，验证正文归SOL、精确旧值回填、源数据/文件/完成历史不变、多选/明细/空值、完成只读/修订、非授权主体、陈旧版本、缺必填及失败回滚。即使当前实体表为空也须验证旧格式样例，不跳过历史保护。真实登录后验证设计/加载/编辑/保存/关闭重开及SQL权威回读，不用浏览器请求替身或模拟Owner命令冒充。接口/数据库/服务尚未在本协调轮修改或验证，Feature完成状态不随认领晋级。

以下为此前认领与交付历史，若其范围描述与本节不同，以本节及顶部当前边界为准：

- 协调者：开始项目交付主线任务 (2)，任务`01a08130-1cc3-7713-bb2b-20ea95140b1a`；协调Worktree为`M:/AICoding/CodexData/worktrees/2916/NPDMS`，只在master登记认领，不接收实施工作树在途文件、不推送。
- 来源Task：bfbf同一交付界面任务，包含PRE-04表单内容、PRE-01工期、ACC-02满意度复用适配及CUS-04文档增量；进展集中记本DU，分别关联已有Feature Task，不修改其既有状态或历史Done。CUS-04尚无本DU可宣称完成的实现Feature。
- Requirement：`PRE-04`；正式契约为`specs/features/F-SOL-003-requirement-analysis-versioning.md`及其物理合同；F-PLT-002仅作为既有共享表单能力依赖，不认领其实现职责。
- 授权依据：用户已确认ZCode继续负责宿主/模板/通用注册接入，bfbf负责Demo对应领域页面与表单内容；来源任务于2026-09-09集中补交本单元精确清单并要求一次登记。来源工作树当时为detached `8912d6f8`；此认领不授权切换其工作树/分支、提交或接收PM01在途内容，也不要求来源分支包含认领提交。
- Demo：`E:/AICoding/Projects/NPDMS/需求/项目交付/项目交付页面数据DemoV2.html`，只作界面与示例数据参考，不覆盖正式业务、权限、状态和历史契约。

## 本次交付与排除项

首个PRE-04单元的生产内容产物为新增JSON对象`{formConfJson, formRulesJson}`，保持与现有`DynamicFormRevisionVO`、`decodeDynamicForm`兼容；保留11个核心Editor、3个核心必填和11个受控附件槽。Demo传输多选、流量、IP及运维多选仅按既有契约允许的扩展添加，不嵌入业务状态命令、API地址或密码。后续工期、满意度及文档增量按下文对应扩展边界交付。

PRE-04单元的测试和README只服务本JSON；browser目录可提供独立真实渲染/取值验收入口及脚本，不成为生产页面宿主或替代领域实现。该内容单元不得修改SDS、领域Service/API/表、SQL、模板注册、既有组件、已发布修订或项目冻结引用；不调用生产写API、不擅自发布模板，不把页面保存改成任务或节点完成。

除下述ACC-02及PRE-01扩展外，其他Demo页面尚未认领。用户已明确按S1→S6顺序推进：当前回到S1工期，之后依次核对工勘、物料、需求分析，再按阶段顺序推进；保留来源已报告完成的S5适配，但不因后续阶段容易而跳序。后续精确范围仍在本DU一次扩展，不提前占用整个delivery-business目录；来源未提交成果不视为master交付。

## ACC-02满意度现成页面直接复用增量（2026-09-09）

- Requirement/Feature：`ACC-02` / `F-ACC-002`；正式契约引用`specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md`及现有`tasks/features/F-ACC-002.md`，不修改其Feature状态、评分/签字/权限/归档规则。
- 授权依据：用户已确认S1～S6适配及“旧实现能复用直接复用，仅缺项目上下文/字段/操作做必要适配，保留原功能和历史”；本次按来源任务集中清单，直接增强上述三个既有满意度组件，不复制成平行页面，不新建问卷引擎。
- 输入与行为：为`index.vue`、`TaskPanel.vue`、`ResultPanel.vue`增加可选`projectId`和`readonly`输入。未传入时保持独立管理页行为；明确指定项目时锁定查询上下文，非法指定项目不得退化为全域查询，切换项目不得让旧响应回填；只读状态禁止写业务动作。`projectContext.ts`仅在需要共享纯上下文判定时新增，只供本域使用，不扩展为通用框架。
- 排除项：不改既有API、领域Service/表、SQL、评分、签字、授权或归档；不删除或替换原独立入口，不修改未列明的满意度组件。新增README及browser目录仅用于本次组件验证，不增加生产路由、宿主或注册项。
- 依赖与Owner：满意度页面继续消费既有F-ACC-002 API；p903仍持有模板发布/选择/冻结、受控注册、宿主及既有需求分析组件。组件可选输入不等于宿主契约变更，本次未转交或修改p903职责。master活动DU核对未发现F-ACC-002或上述满意度路径的其他活动认领；不据此扩大到满意度后端或整个ACC领域。
- 验证边界：复用已有测试依赖与mocked Owner API，覆盖项目切换、非法上下文、只读及不传props的原入口行为；browser夹具只验证真实组件，不访问数据库或生产、不执行真实业务写入。不将该适配视为评分/签字/归档或完整用户链路已验收。
- 来源与交付：沿用bfbf工作树及实际detached状态，不要求创建分支、同步认领提交、提交/接收PM01或当前实现。本节只扩展生效职责与文件边界；来源实现进展仍在本DU记录，后续集成须另有授权。

## 依赖与消费交接

## PRE-01工期现成组件直接复用增量（2026-09-09）

- Requirement/Feature：`PRE-01` / `F-SOL-001`；引用`specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`、`tasks/features/F-SOL-001.md`及既有工期API，保留其历史完成状态，本次适配进度只记本DU。
- 授权与实现方式：按用户已确认的直接复用原则，增强原`ProjectDurationPanel.vue`和`ProjectDurationFormDrawer.vue`，不建立平行页面。只补可选readonly、安全项目上下文切换及与既有宿主约定兼容的dirty协议；原project prop、未传readonly时的独立使用行为、日期计算、BPM和证据语义保持不变。
- 拒绝与状态保护：非法或失效项目上下文不发出越界/退化查询，切换项目隔离旧请求响应及编辑内容；只读禁止写业务动作，dirty状态与丢弃操作不得触发业务保存、审批或完成。不能用前端只读代替既有服务端授权。
- 精确边界：上述两组件、同目录`ProjectDurationContext.runtime.spec.ts`、`delivery-business/duration/README.md`及其browser专用测试文件。既有历史查看抽屉、API/Service、项目详情入口、p903宿主/注册/模板均不改；不增加生产路由、SQL或数据库访问。
- 定向回归修复补充：增加同目录`ProjectDurationPanel.spec.ts`，仅允许将`await DurationApi.getChange(plan.value.planId, draftSummary.changeId)`的源码字符串断言适配为本次安全加载使用的`current.planId`。保留其余原断言及跨项目旧草稿不回填等新增行为测试，不删除业务调用、历史校验或降低断言；修复后复验受影响测试。本条仅登记写边界，不接收实现、不修改Feature状态。
- 验证：只复用既有依赖、mocked Owner API与真实组件测试，覆盖原project用法、只读、项目切换/旧响应隔离及dirty协议；不宣称生产鉴权、真实BPM或证据文件流程已联调。master活动认领及p903现存边界未发现本次F-SOL-001组件路径冲突。
- S1联系人差距：来源任务已核对旧CustomerContact按customerId及客户主联系人维护，而CUS-04要求projectId独立记录及项目主联系人历史，F-CUS-001不承接CUS-04。本DU仅记录该缺口，不把客户联系人冒充项目联系人，不实现未落位的CUS-04新API/业务表，也不以此阻断不依赖它的工期组件适配。
- 来源工作树、Owner及不接收PM01/不推送约束不变。后续工勘、物料、需求分析仍须依据相应已明确契约与生效精确边界实施，不因本次认领自动扩权。

## 既有依赖与消费交接

## CUS-04已确认规则的权威落字（2026-09-09）

- 本次只认领文档修订：`docs/baseline/prd-v1.8.md`的CUS-04及对应修订记录；`docs/design/08-data-model.md`的9.1 CustomerContact/项目联系关系语义。只占上述具体业务段，不占用两文件其他章节；不得修改09物理DDL、10 API、其他PRD业务、Feature状态或运行代码。
- 用户已确认：客户联系人由客户维度管理；项目成员中的客户联系人默认引用客户关联联系人；在项目内新增联系人时，同步新增客户联系人记录。项目记录保留联系人引用、必要信息及本地状态，不是客户联系人的完整副本；项目内编辑、停用、删除暂不同步修改客户主档。每项目主联系人、责任历史及权限要求保持，不新增双向同步或配置开关。
- 修订须纠正“项目完全独立、无客户来源”及“只引用、不保留任何联系人信息”两种解释；采用“客户主档＋项目引用/必要信息＋本地状态”。本条记录用户已确认输入，不宣称两处正文已修订，不从“必要信息”自行扩展成完整字段复制或新的API/物理Schema。
- 角色与边界核对：master模板DU在614f995c集成回执中明确释放本轮写边界，p903上述两文件无未提交修改；其他相关历史文档认领已完成或明确释放。由现有协调者将本次精确段落交给bfbf，不代p903确认未释放范围，不接管模板及其他模型职责。
- CUS-04不归入已排除该需求的F-CUS-001，也不通过本次文档修改创建Feature Done。后续Feature/API/Schema/代码按实际影响另行明确并在本DU精确扩展；现阶段不得先实现新联系人接口、业务表或同步写逻辑。
- 验证只检查两处已确认语义一致、其他章节及每项目主联系人/历史/权限规则未被扩大或删除；不运行数据库、业务测试或全库审计来证明文档修订。本次协调提交仅扩展DU，不接收bfbf的实现或PM01在途SDS10文件、不推送。

## 已认领实现的依赖与消费交接

- F-PLT-002 → 既有表单引擎、共享codec及受控文件组件 → `specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`、对应Feature Task和源码。仅消费，不复制引擎或Provider；来源分支缺代码时先明确可用增量及同步授权，不从p903在途文件复制。
- F-SOL-003 → PRE-04兼容规则及既有`PATCH /preparations/{id}/form` → 现有Feature Spec/Task。原应用链为PLT模板草稿PATCH、由原Owner配置发布/选择、SOL表单PATCH；本单元只交付兼容内容与测试，不执行该生产写链。
- `DU-20260908-TEMPLATE-BUSINESS-VIEW` → 原宿主、受控注册、模板发布/选择/项目冻结和任务接入 → Owner/位置/进展直接读取master该DU及p903来源Task。本次不新增宿主props、事件或注册键，不改变或转交其现有职责与文件，因此不以尚未取得ZCode接入回执阻断本独立内容单元；实际配置、挂载和联调仍由原Owner承接，涉及契约或边界改变时须取得其真实确认。

## 交接

- 最后提交：界面/表单及测试`26aed316`；CUS-04需求与模型`fea87f63`，后者已由master选择接收为`7814ace5`。
- 已完成：PRE-04 Demo表单内容、PRE-01工期与ACC-02满意度原组件适配及定向验证；CUS-04修订020需求与通用模型对齐。此处仅确认已交付范围，不宣称新接口、配置发布或宿主接入完成。
- 剩余：CUS-04项目引用记录接口/Schema/实现，PRE-02字段归属及p903目录/发布格式回执，其他S1项与后续S2～S6实施；按原顺序与精确Owner边界继续，不以已完成的局部适配替代全目标。
- 完成层级：本内容单元完成不等于整个Demo、F-SOL-003新增量或完整用户办理闭环完成；未挂载不得宣称用户已能从任务入口办理。

下文各次增量的“未提交”是记录当时状态；当前已确认的提交回执以上述交接条目为准。工作树建立时带入的PM01与.zcode不属于本任务产出，继续保留，未纳入上述提交。

## 2026-09-09 bfbf内容单元交付（未提交）

- 完成范围：新增 `demo-template.json`，30个顶层字段（11核心Editor、11受控附件、8项扩展），含传输/运维多选、流量文本、IP资源及八列可增删业务明细。三个核心必填、附件可空、核心含义及原Owner命令保持不变；日志需求按PRD保留，Demo工程交底书操作不夹带。
- 修改边界：本次仅新增已认领目录内JSON、6项Vitest、README及browser测试夹具/配置/脚本，另在本DU记录结果。未修改旧组件、SDS、API、Service、SQL或注册；bfbf既有PM01改动保持，未提交业务增量或推送。
- 定向验证：`node <已有前端依赖>/node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/requirement-analysis/browser/config.mjs`，6/6通过；实际调用共享codec及runtime，覆盖核心兼容、子表单配置无损、受控附件排除、未知字段排除、空值/数组清空及配置不被运行上下文污染。不是服务端发布兼容/业务保存验收。
- 浏览器：`node src/views/pms/delivery-business/requirement-analysis/browser/verify.mjs`，独立无头Edge中的真实FormCreate/Element Plus验证必填失败与成功、多选、八列明细增删/清空、前导零、只读禁用/切回保值、普通输入键盘切换及320/768/1024/1440无页面横向溢出。脚本限制仅本地静态GET请求，无业务API、数据库或认证访问；服务器和浏览器已在finally关闭。截图在本工作树 `.run/delivery-demo/browser/`，已查看桌面明细及窄屏截图。
- 测试边界：未改动的Editor与PmsFileArtifact仅使用明确标识的测试占位；没有执行真实富文本/文件上传、鉴权、发布、项目冻结、宿主挂载或业务保存/完成。当前工作树无node_modules，只读复用已有安装；Python无Playwright，使用宿主已提供的Node Playwright，未新增项目依赖。实际版本Node24.11.1、Vue3.5.34、ElementPlus2.13.7、FormCreate3.2.38。
- 本次测试环境修复：独立Vite入口最初误读取全应用PostCSS/扫描全应用依赖，随后限定本测试入口及纯CSS；ElementPlus解析到CommonJS导致浏览器模块初始化失败，改为其既有ESM入口后通过。浏览器对ElementPlus隐藏input的点击改为点击可见标签/控件；未降低断言或修改产品实现。最后调整栅格间距后，上述6项与浏览器全流程再次通过。
- 自审：配置与现有PRE-04契约一致，数据不预填业务答案，不臆造单位/阈值/重要级字典，不创建副本Owner或领域状态。已发现FormCreate 3.2.38 group原生加减为无tabindex/可访问名称的div，鼠标可用但键盘增删未满足可访问性；README及脚本明确报告，未修改通用宿主/依赖。p903接入验收需核对该限制，不能声明整个页面WCAG通过。
- 完成层级：PRE-04表单内容与定向测试增量已完成；仍未提交、未发布、未注册、未挂载。后续由p903按原职责接入精确修订并完成真实Owner/文件/宿主验收；不代表整个Demo或Feature新增量Done。不运行无关全库/后端构建或数据库迁移。

## 2026-09-09 ACC-02既有页面直接复用适配（未提交）

- 生效依据：master认领扩展`766d3081`；用户已确认全S1～S6适配与可复用旧实现直接复用。仅直接适配既有满意度`index.vue/TaskPanel.vue/ResultPanel.vue`，新增本域`projectContext.ts`及定向测试/说明；没有复制平行生产页面。
- 接口：三个组件可选`projectId?: number / readonly?: boolean`；不传项目保持原独立管理入口与手工筛选，传入合法项目锁定查询范围，非法指定项目不发请求；指定项目/只读时不显示模板管理。只读禁止业务写动作，但保留既有受权文件查询/导出；权限仍由原API逐次校验，不改变角色、评分、签字、归档、Schema或历史。
- 上下文保护：切换后清空旧列表、选择和弹窗，旧查询/grant/导出/下载回执不回填；上传预留/文件链中上下文变化时不再发后续请求。暴露dirty-change/isDirty/discardChanges，宿主仍负责切换前确认；上传中拒绝放弃，权限切为只读保留现场协助输入。已经发出的原有效请求不假称取消。
- 测试：`node <既有安装>/node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/satisfaction/browser/config.mjs`，最终18/18通过。覆盖独立默认行为、指定范围、0/负数/null/NaN/不安全整数拒绝、旧响应隔离、加载失败、只读直接调用拒绝、原指派API/版本复用、晚到grant/下载/导出、确认期间转只读、上传预留期间上下文变化、草稿保留和明确放弃、顶层props透传。
- 浏览器：`node src/views/pms/delivery-business/satisfaction/browser/verify.mjs`最终通过。真实三个Vue组件与ElementPlus验证项目/独立入口、模板管理隐藏、只读按钮、项目切换、非法项目不查、mock指派及320/768/1024/1440显示；检查console无错误/警告、无业务或外域网络。已查看窄屏截图；证据在本工作树`.run/delivery-demo/satisfaction-browser/`。浏览器/服务器由finally关闭，不停用他人服务。
- 测试环境：沿用PRE-04只读依赖安装和Node Playwright，未安装新项目依赖；单元复用既有client测试环境，避免node环境误编译为SSR。格式化曾使测试夹具的多语句内联click失去分号导致Vue解析失败，改为命名方法后重跑浏览器通过；产品代码18项证据仍适用。最终Prettier检查与git diff --check通过。
- 自审：改动限于上述组件本身、可选props、范围/只读隔离和窄屏弹窗。API、Controller、Service、数据库、原客户问卷和模板页均未改；没有增加生产包装层、注册键或数据Owner，既有无props正向入口有浏览器回归。未执行全应用类型检查/打包、真实鉴权/文件/评分/归档回归或数据库测试：本轮为独立组件适配、使用明确mocked Owner API，当前工作树无完整依赖安装，不把组件编译/测试写成全应用或业务验收。
- 交接：既有页面新增props与dirty协议见`delivery-business/satisfaction/README.md`；p903按原职责映射已授权项目、注册和宿主接入。当前只交付页面复用适配，不代表已挂载、全Demo完成或F-ACC-002 Done；现场协助原JSON输入未在本单元重做。代码未提交、未推送，bfbf在途PM01文件与p903文件未触碰。

## 2026-09-09 按S1顺序推进：PRE-01直接复用（未提交）

- 本轮属于实质进展：核实CUS-04旧实现不能充当项目联系人，并完成S1工期原组件适配与验证。完整目标仍为按S1→S6覆盖全部Demo环节，未缩减为本工期单元或已做的PRE-04/S5。
- 认领：`f9480d8e`扩展两个原工期组件、新增组件测试与duration专用测试目录；`b4ac42f5`只补原ProjectDurationPanel.spec.ts一处源码断言适配边界。无其他Owner路径冲突，不改p903宿主/注册/模板/项目详情入口。
- 实现：原ProjectDurationPanel和ProjectDurationFormDrawer增加可选readonly、合法project ID检查、跨项目查询/草稿/保存/文件回执隔离、异步确认后重验、dirty-change/isDirty/discardChanges；保留原必填project输入、日期载荷和计算口径、CAS/幂等、BPM标准入口、证据与历史查询。只读不办理业务写动作但保留查看历史；未新建平行页面或恢复旧工期倒排/变更页写入口。
- 验证：原8项定向回归加新增16项组件测试，共24/24通过。原源码断言从plan.value.planId适配为局部current.planId，其他断言保留，新测试覆盖慢项目/慢草稿不回填。初轮测试夹具遗漏candidateRevision导致渲染错误，补齐真实形状后修复，不改产品校验。实际命令为`node <既有安装>/node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/duration/browser/config.mjs`。
- 浏览器：`node src/views/pms/delivery-business/duration/browser/verify.mjs`最终通过；真实旧面板/编辑抽屉、ElementPlus和VueUse验证原日期表单填写/提交载荷、只读入口、历史入口保留、项目切换、非法ID拒绝及320/768/1024/1440无页面溢出；已查看窄屏截图。Owner API/BPM/身份/权限指令/文件/历史弹窗为明确测试夹具，限制静态本地GET，不访问生产或数据库；不宣称真实审批、文件或业务生效验收。
- 格式与自审：新增范围Prettier检查、git diff --check通过；没有修改API、Service、SQL、日期推算规则、旧发布定义或Feature Done。未全应用类型检查/打包；复用既有安装，不新增项目依赖。业务代码未提交、未推送，既有PM01及p903修改保持原样。
- S1联系人事实：PRD CUS-04要求按项目独立记录、项目启用主联系人唯一及历史；旧CustomerContactDO只有customerId，旧Service按客户校验主联系人，F-CUS001明确排除CUS04。界面方式已确定为项目联系人列表/编辑/主责切换/历史，但必须先补CUS项目联系人契约和基础能力，不能仅前端加projectId过滤或改客户全局记录。本轮不擅自新建其API/表。
- 下一S1工勘事实：FixedSurveyFormCatalog的FormDefinition只有formCode/formVersion，PreparationInitializationService.freezeSchema统一使用catalog.commonFields；V97仅提供siteCondition。Demo不同工勘项的字段内容尚不能靠原目录独立配置。下一步从PRD PRE-02、FixedSurveyFormCatalogProvider/Rules、既有固定Schema及旧项目冻结保护设计确定最小逐项配置增量，再按Owner边界实施；不继续用简单上下文适配代替Demo字段缺口。
- 顺序检查点：master `b4ac42f5`；S1联系人模型缺口已确认，PRE-01适配24项+组件浏览器通过；PRE-02逐项Schema仍待解决，随后物料、需求分析/交底书。S1未闭环，不进入S2；既有S5适配保留但不作为前置完成依据。完整目标保持ACTIVE。

## 2026-09-09 PRE-02逐项表单契约核对与待决（未实施）

- 本轮为设计/证据进展，不新增工勘运行代码。已核对SOL FixedSurveyFormCatalogProvider/Rules、初始化和就绪服务，以及PROJ PreparationWorkBindingSchema：两端限制目录结构/版本1，逐项内容不能直接追加到现有配置；只覆盖旧目录还会使旧版本绑定失去精确初始化来源。
- 候选位于本工作树被忽略的`.run/delivery-demo/pre02-item-form-design.md`。建议保留目录1，另增配置修订2，按绑定明确选择和冻结每个form.fields，沿用五类字段并补显示label；不是把PRE-02业务推迟到V2，不新建通用版本框架，不覆盖旧项目快照。
- 当前协调者已只读认可该方向，但明确无法直接取得p903实际回执；这不是发布Owner批准，也没有扩展DU或提交新格式。需p903确认目录读取键、精确版本选择、fields/label格式与发布校验承接；缺失/不支持的版本必须拒绝，不能回退另一版。bfbf仍不改其模板/注册/宿主文件。
- 业务待决仅限Demo整页问题的逐项归属：原厂上架加电、导轨/托盘、发货物料符合性涉及不同工勘项的责任与确认粒度，不能仅凭界面分组决定存储/办理归属；外包、领料、外采、换货仍属原业务Owner。
- 另已确认就绪服务目前对表单只执行合法值校验，BOOLEAN=false也是合法值。新增资源可用性字段时，须明确接入该项既有完成/来源/豁免规则，不能把“字段合法”当成“资源就绪”，也不能统一要求所有布尔项为true。
- 未修改SOL/PROJ运行实现、正式规格、配置或数据库，未执行迁移/新增运行测试；已有已验证增量未变，不重复跑无关检查。S1完整目标仍未完成；先取得格式/归属回执，再集中落正式契约和精确认领，之后实施逐项字段。不以候选文档或协调意见宣称Feature完成。

## 2026-09-09 S1剩余操作复用核对与等待输入

- 前一goal轮为证据/设计进展；本轮再次读取master，仍为b4ac42f5，未出现PRE-02目录2的生效格式/发布承接。协调任务的最新回告已由wait_threads确认完成且当前idle，没有可等待的活跃p903回执任务；没有把自动goal续跑当业务归属确认，也没有重启协调审阅。
- 独立核对PRE-03：当前MaterialExchangeVO为单物料载荷；MaterialExchangeServiceImpl.pushToCrm仅更新模拟推送时间、SENT/RECEIVED与传入单号。PRD要求实际CRM单号受理、物料行可换数量占用、逐明细版本回传与部分完成。界面方式明确为选择表+数量/原因+申请进度，可复用旧控件，不直接使用模拟命令。
- 独立核对PRE-05：BriefingServiceImpl.generateBriefing明确生成占位内容和文件元数据，拼接.pdf URL、固定大小和auto-校验值后置GENERATED；未见对应文件生成/下载能力。PRD要求设备组合/模板优先级、来源与不可变版本、预览和下载同版。界面采用既有编辑器/内容预览+真实生成/版本操作，旧占位命令不能进入新交付工作台。
- 已将这两项具体证据更新到`.run/delivery-demo/s1-s6-ui-adaptation.md`；本轮没有修改运行代码、调用旧模拟接口、执行数据库/CRM写入或重复已有效测试。
- 当前可独立完成的已认领S1工期和PRE-04内容均已交付；PRE-02实现仍等用户确认整页问题的逐项归属及p903格式/发布承接回执，其他S1业务基础缺口不因此关闭。目标未完成，不晋级Feature或标记全流程完成。

## 2026-09-09 CUS-04修订020规则落字（未提交）

- 用户已明确：客户联系人按客户管理；项目默认引用所属客户的联系人，同时保留必要联系信息而非完整副本；项目内新增同步新增客户联系人；项目内编辑、停用、删除暂不回写客户主档。此规则替代此前“纯ID引用无需项目信息记录”的解释，不是PRE-02字段归属或发布格式回执。
- 生效认领为master `5fc31244`；只修改PRD的CUS-04/修订记录和SDS08的9.1 CustomerContact/项目联系关系语义。修订ID `CHG-PRD-2026-09-09-020`：加入默认引用、必要信息、仅新增同步、项目本地修改与相应验收；保留项目主联系人唯一、联系方式校验、软删除、权限/租户和业务不可变历史。
- 通用模型明确CustomerContact为客户主档，ProjectCustomerContactRelation保存来源身份、必要信息及项目角色/主次/状态；没有复制全量客户档案，没有定义双向同步、开关或后台回灌。默认引用不能覆盖已保存的本地修改或重新启用/加入已停用删除记录。
- 影响范围：CUS-04及项目成员客户联系人呈现/维护、CUS来源联系人查询与新增、后续业务使用的项目联系记录身份/快照。CUS-03的既有主档生命周期、CUS-02服务等级、内部员工成员及其他Feature业务不变；F-CUS-001仍不宣称覆盖CUS-04。API、物理Schema、迁移、代码和下游联调尚未落位，不能把本次需求确认当作功能完成。
- 验证：人工核对差异仅位于授权的PRD修订记录/CUS-04段及SDS08 9.1表，git diff --check通过；没有运行与文档变更无关的组件/后端/数据库测试。保留本工作树既有PM01在途SDS10及其他实现，不覆盖master此前SDS08的模板段增量。
- 当前仅完成规则与通用模型对齐，正文未提交、未推送；后续接口/Schema/实施须按已收敛语义单独明确精确边界。PRE-02尚需的业务归属和p903格式/发布回执仍保留，目标未宣告完成。
