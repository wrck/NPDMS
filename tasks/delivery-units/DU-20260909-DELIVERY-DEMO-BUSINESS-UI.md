# DU-20260909-DELIVERY-DEMO-BUSINESS-UI Demo领域页面与表单内容

> DU状态：`CLAIMED`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-SOL-001=TASK_COORDINATED;F-SOL-003=TASK_COORDINATED;F-ACC-002=TASK_COORDINATED`
> Task范围：`当前按S1顺序推进PRE-01工期组件直接复用；保留已认领PRE-04内容与ACC-02适配；仅领域组件上下文/只读/dirty协议及定向测试，不含宿主、注册或模板接入`
> Owner：`实现动态表单和业务操作界面（01a0822c-ea82-7801-b318-39bfd843f866）`
> 分支：`DETACHED`
> Worktree：`M:/AICoding/CodexData/worktrees/bfbf/NPDMS`
> 认领基线：`3d6d6686b703638041a1007bdc7372206c9501c3`
> 认领提交：`SELF`
> 修改边界：`yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/demo-template.json;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/demoTemplate.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/README.md;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/browser/**;tasks/delivery-units/DU-20260909-DELIVERY-DEMO-BUSINESS-UI.md;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/index.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/TaskPanel.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/ResultPanel.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/projectContext.runtime.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/projectContext.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/satisfaction/README.md;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/satisfaction/browser/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationContext.runtime.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/duration/README.md;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/duration/browser/**`
> 串行资源：`无数据库/生产访问，无SQL或迁移号；浏览器目录仅各自内容/组件测试入口，不增加生产路由；保留bfbf在途PM01文件及p903边界`
> 旧功能范围：`NONE`
> 验证：`PRE-04共享codec及FormCreate渲染/取值；ACC-02复用已有依赖和mocked Owner API验证独立入口不回退、指定项目锁定/非法值拒绝、切换竞态及只读禁写；工具不可用如实报告，不冒充生产或宿主联调`
> 集成记录：`NONE`

## 协调入口与依据

- 协调者：开始项目交付主线任务 (2)，任务`01a08130-1cc3-7713-bb2b-20ea95140b1a`；协调Worktree为`M:/AICoding/CodexData/worktrees/2916/NPDMS`，只在master登记认领，不接收实施工作树在途文件、不推送。
- 来源Task：bfbf任务中的“Demo V2需求分析表单内容增量”，本单元进展集中记本DU；关联现有`tasks/features/F-SOL-003.md`，不修改其既有Feature状态或历史Done。
- Requirement：`PRE-04`；正式契约为`specs/features/F-SOL-003-requirement-analysis-versioning.md`及其物理合同；F-PLT-002仅作为既有共享表单能力依赖，不认领其实现职责。
- 授权依据：用户已确认ZCode继续负责宿主/模板/通用注册接入，bfbf负责Demo对应领域页面与表单内容；来源任务于2026-09-09集中补交本单元精确清单并要求一次登记。来源工作树当时为detached `8912d6f8`；此认领不授权切换其工作树/分支、提交或接收PM01在途内容，也不要求来源分支包含认领提交。
- Demo：`E:/AICoding/Projects/NPDMS/需求/项目交付/项目交付页面数据DemoV2.html`，只作界面与示例数据参考，不覆盖正式业务、权限、状态和历史契约。

## 本次交付与排除项

唯一生产内容产物为新增JSON对象`{formConfJson, formRulesJson}`，保持与现有`DynamicFormRevisionVO`、`decodeDynamicForm`兼容；保留11个核心Editor、3个核心必填和11个受控附件槽。Demo传输多选、流量、IP及运维多选仅按既有契约允许的扩展添加，不嵌入业务状态命令、API地址或密码。

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
- 验证：只复用既有依赖、mocked Owner API与真实组件测试，覆盖原project用法、只读、项目切换/旧响应隔离及dirty协议；不宣称生产鉴权、真实BPM或证据文件流程已联调。master活动认领及p903现存边界未发现本次F-SOL-001组件路径冲突。
- S1联系人差距：来源任务已核对旧CustomerContact按customerId及客户主联系人维护，而CUS-04要求projectId独立记录及项目主联系人历史，F-CUS-001不承接CUS-04。本DU仅记录该缺口，不把客户联系人冒充项目联系人，不实现未落位的CUS-04新API/业务表，也不以此阻断不依赖它的工期组件适配。
- 来源工作树、Owner及不接收PM01/不推送约束不变。后续工勘、物料、需求分析仍须依据相应已明确契约与生效精确边界实施，不因本次认领自动扩权。

## 既有依赖与消费交接

- F-PLT-002 → 既有表单引擎、共享codec及受控文件组件 → `specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`、对应Feature Task和源码。仅消费，不复制引擎或Provider；来源分支缺代码时先明确可用增量及同步授权，不从p903在途文件复制。
- F-SOL-003 → PRE-04兼容规则及既有`PATCH /preparations/{id}/form` → 现有Feature Spec/Task。原应用链为PLT模板草稿PATCH、由原Owner配置发布/选择、SOL表单PATCH；本单元只交付兼容内容与测试，不执行该生产写链。
- `DU-20260908-TEMPLATE-BUSINESS-VIEW` → 原宿主、受控注册、模板发布/选择/项目冻结和任务接入 → Owner/位置/进展直接读取master该DU及p903来源Task。本次不新增宿主props、事件或注册键，不改变或转交其现有职责与文件，因此不以尚未取得ZCode接入回执阻断本独立内容单元；实际配置、挂载和联调仍由原Owner承接，涉及契约或边界改变时须取得其真实确认。

## 交接

- 最后提交：`NONE`
- 已完成：仅职责、依赖及文件边界认领；实现和测试尚未执行。
- 剩余：新增JSON、定向兼容/渲染测试与结果说明；真实宿主接入不在本单元。
- 完成层级：本内容单元完成不等于整个Demo、F-SOL-003新增量或完整用户办理闭环完成；未挂载不得宣称用户已能从任务入口办理。
