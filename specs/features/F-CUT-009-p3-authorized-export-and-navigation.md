# F-CUT-009 P3授权清单导出与受控流程跳转 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / REVIEW_REQUIRED`
> Requirement：`CUT-03（V2/P0）`
> Requirement切片覆盖：`CUT-03@V2=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-001（配置版本）`、`F-CUT-003（P3清单）`
> 机器合同：`specs/features/F-CUT-009-api-contract.json`、`specs/features/F-CUT-009-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-009-legacy-reuse-audit.md`
> 已决问题：`Q-FCUT009-001（RESOLVED / OPTION_A）`

## 1. 业务目标

在不改变CUT-03 V1清单生成、填写、暂存、提交、D级跳过和P3到P4状态事实的前提下，让获权用户下载当前可见清单版本，并让割接管理员通过不可变配置修订维护提交成功后的受控导航规则。

## 2. Scope

### 2.1 包含

- 导出当前任务的指定清单版本，导出内容与调用人当次获权的P3详情投影同源；
- 按项目、任务、设备、清单项和字段可见性裁剪导出，不以导出扩大查询范围；
- 导出业务调研项和风险考察项，保留来源、必填性、当前选择结果、事实说明及允许导出的证据状态摘要；双机检查项不属于本导出范围；
- 导出请求审计；导出不修改清单、任务、结果选择、配置或流程状态；
- 按既有SDS预留在`CutoverConfigurationRevision.navigationRuleSnapshot`中维护、复制、校验并发布受控导航规则；当前迁移/DO尚未落字段，由本Feature前向接通；
- 清单提交继续先执行F-CUT-003全部守卫并原子推进`SURVEYING -> PLAN_DRAFTING`，提交成功后仅按冻结配置规则返回服务端导航决定；
- 跨模块范围/文件能力只通过既有或最窄消费端口调用，`src/test`可用受控替身完成正常正向闭环。

### 2.2 不包含

- 新建第二套清单、流程、状态机、工作台导航表或导出业务真值表；
- 修改V1清单版本、必填校验、结果选择、D级跳过及P3到P4状态迁移；
- 允许配置绕过P4/P5/P6、跳过审批、回退已批准任务或直接写任务状态；
- 实现PROJ、AST、PLT或其他跨模块Provider，注册生产Fake/fallback；
- CUT-08备件系统集成、CUT-05提前时间、V3逐步骤执行或稳定观察；
- 修改旧`/pms/cut-risk`、旧页面、旧接口、旧表或Yudao基础平台。

## 3. 业务规则

### BR-FCUT009-001 授权导出

- 导出只接受`NEW_PLATFORM`任务上已存在且未删除的清单版本；D级与`LEGACY_FORWARD`无清单可导出。调用人必须同时具备`pms:cutover-task:query`和任务项目`ACTION_VIEW`范围。
- 只允许导出任务当前未失效的DRAFT或SUBMITTED清单版本。请求`checklistVersion`必须精确等于当前版本；INVALIDATED历史版本不属于“当前授权清单”，不得通过本接口导出。调用人不可见的资源返回不可见/不存在，不泄露其身份；版本不一致时失败并要求刷新，不回退到其他版本。
- 导出投影复用同一清单详情查询和服务端字段裁剪策略：只包含调用人可见的适用项、设备和字段。清单详情未返回的敏感字段、文件内部标识、设备凭证、外部原始响应和不可见历史结果不得进入导出。
- 输出格式固定为XLSX，文件名固定为`cutover-checklist-{taskId}-v{checklistVersion}.xlsx`。工作簿始终按顺序包含`业务调研`、`风险考察`两个Sheet；无数据的Sheet只保留表头，不生成伪数据行。
- 两个Sheet使用同一十列合同：`序号/稳定项键/项目名称/项目说明/工作模式/是否必填/来源/当前答案/事实说明/证据状态`。`序号`为从1开始的整数，其余均为文本；可空值输出空单元格，布尔值固定为`是/否`，证据状态只允许空、`人工附件已关联`、`采集结果已关联`。不得输出文件引用键、采集任务/结果ID或文件正文。
- `当前答案`不得通用透传`answerSnapshot`。`DIRECT`只把获权的直接答案原文作为XLSX文本单元格写出且禁止公式执行；`MANUAL/COLLECTION/EXTERNAL`分别固定显示`人工结果已提交/采集结果已形成/外部结果已形成`，原始快照、文件Fact、采集身份和外部原始响应均不得进入工作簿。未知来源使整次导出失败关闭。
- 行先按`sortOrder`升序、再按`stableItemKey`升序；`sortOrder`为空按0处理。只导出`applicable=true`的`BUSINESS_SURVEY`或`RISK`项，其他类型不得进入工作簿。
- 导出是只读业务动作，只追加操作审计；不得更新清单、任务、结果、配置revision、`updated_at`或任何业务version。重复导出可产生独立审计，但输出必须由同一授权事实和同一清单版本确定。

### BR-FCUT009-002 受控导航配置

- 导航规则属于`CutoverConfigurationRevision`根内不可变`navigationRuleSnapshot`，V2只允许一个无条件提交后目标，精确结构为`{"target":"CURRENT_STAGE_WORKBENCH|TASK_OVERVIEW"}`或JSON null；不提供条件、优先级、多规则或状态目标。
- 导航字段只在DRAFT整体保存，随既有配置整体发布；复制修订原样复制，已发布/停用修订不得覆盖。现有create/update完整请求增加必带的`navigationRule`键（值可为null）；create/copy继续只返回revisionId，复制后的继承值通过读取新revision详情证明；detail/publish/disable响应增加该字段，既有响应外壳和其余字段不变。
- 发布校验必须拒绝额外字段、未知目标及任何试图表达条件或改变CUT任务状态的结构；失败保持草稿和当前已发布修订不变。
- F-CUT-002任务已冻结配置revision，因此F-CUT-003提交后只读取该任务冻结revision中的导航规则；后续配置发布不改变既有任务。
- 导航判断只在F-CUT-003全部提交守卫和业务写成功后执行，并且只有事务提交成功才随响应返回。提交命令仍只产生`SUBMITTED + PLAN_DRAFTING`业务结果；导航规则只形成响应中的导航决定，不参与提交资格、状态CAS或平台幂等业务摘要。
- `CURRENT_STAGE_WORKBENCH`表示导航到提交后权威P4工作台；`TASK_OVERVIEW`表示导航到任务总览。冻结修订的字段为null或历史字段缺失时确定性使用`CURRENT_STAGE_WORKBENCH`，不得读取当前新发布修订，也不得返回任意目标。

### BR-FCUT009-003 跨模块与受控替身

- 项目范围继续复用`ProjectScopeApi`；设备/字段裁剪复用P3详情已有服务端投影，不新增跨模块直表读取。
- 导出由CUT根据授权投影即时生成并流式返回，不创建PLT文件事实、不持久化导出制品。跨模块只消费现有范围事实；测试装配可用`src/test`替身提供确定性范围结果，生产不得注册Fake/fallback。
- 受控替身证据只能证明CUT导出编排和导航决策正向闭环，不代表跨模块Provider、真实浏览器生产链或Feature Implementation Done。

## 4. API与权限

- `POST /api/v1/pms/cutover-tasks/{taskId}/checklist/actions/export`：按`F-CUT-009-api-contract.json`导出明确版本；复用`pms:cutover-task:query`与`ACTION_VIEW`，不新增导出角色。
- F-CUT-003的`POST .../checklist/actions/submit`仅做加法扩展：成功响应可增加`navigationDecision`；请求、Header、幂等、守卫和业务结果不变。
- 配置管理复用F-CUT-001既有`query/manage/publish/disable`权限和修订CRUD；导航配置权限不授予任务、清单或设备数据访问权。

## 5. 数据与迁移

- 按SDS在`cut_cutover_configuration_revision`增加/接通可空JSON列`navigation_rule_snapshot`，不新增导航规则表；当前代码与既有迁移尚未落该列，实施时必须使用实际下一个可用Flyway前向增加并同步DO/Mapper。
- 不为导出新增业务表；导出审计复用平台操作审计，业务表保持只读。
- 不得修改已执行迁移或补造历史规则。既有修订新增列后保持null，表示确定性使用`CURRENT_STAGE_WORKBENCH`，不表示任意跳转。
- `cut_cutover_checklist`三表、`cut_task`及其状态/版本列不做结构改变。

## 6. 验收标准

- AC-FCUT009-001：获权用户可导出明确的当前DRAFT或SUBMITTED清单版本；业务调研、风险考察两个Sheet的列、顺序、值和证据状态与同次授权详情投影一致，双机检查不进入导出，INVALIDATED历史版本被拒绝。
- AC-FCUT009-002：跨项目、跨租户、不可见设备/字段和不存在版本不进入导出；拒绝路径不修改任何CUT业务事实。
- AC-FCUT009-003：导出前后清单、结果、任务阶段/状态/version完全不变，只留下可对账审计。
- AC-FCUT009-004：导航规则在草稿中编辑并随配置整体发布；非法规则整版拒绝，已发布版本不可覆盖，任务只消费冻结revision。
- AC-FCUT009-005：正常正向链为“A/B/C生成并完成清单→提交进入P4→返回冻结规则决定→下载当前授权版本”；V1提交守卫与P4事实不被导航结果改变。
- AC-FCUT009-006：CUT服务、真实MySQL与组件测试可用受控跨模块替身验证正向闭环；替身不进入生产装配，也不作为真实跨模块或浏览器完成证据。

## 7. Feature Ready Gate

当前：`DRAFT / NOT_READY / REVIEW_REQUIRED`。`Q-FCUT009-001`已采用方案A；最近Gate为导出范围、导航机器语义与XLSX线协议最小整改复审，通过前不得生成Technical Plan或实施。
