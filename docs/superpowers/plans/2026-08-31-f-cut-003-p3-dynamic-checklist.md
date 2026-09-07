# F-CUT-003 P3动态采集清单、直接填写与人工降级闭环 Implementation Plan

> 计划 ID：`NPDMS-FCUT003-TECHPLAN-20260831-01`
> Technical Plan Gate：`PASS / GO@ac740458`
> Feature Ready：`PASS / GO@ea986d61`
> Feature Spec：`specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`
> Physical Contract：`specs/features/F-CUT-003-physical-contract.json`

**Goal：** 一次交付“A/B/C任务在P3按已发布配置生成版本化清单 → 同工作台直接填写或以人工证据降级 → 暂存 → 必填校验 → 提交并原子进入P4”的最小完整业务闭环；D级不生成清单。

**Architecture：** CUT在既有`CutoverTask/CutoverAssessment`之下新增SDS锁定的三表聚合；任务创建时冻结F-CUT-001配置revision三元组，P3只按该身份读取配置，不复制或重选配置真值。写命令沿用F-CUT-002共享Owner锁序和`PlatformCommandExecutionApi`幂等/审计/Outbox；PLT继续拥有文件事实，INT-12和外部数据只定义CUT消费端口，不实现第三方Provider。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、pnpm。

**Spec：** `specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`

## Global Constraints

- 权威语义固定为`CUT-03@V1=FULL`；V2导出、流程跳转配置和P4/P5/P6业务不进入本计划。
- 先实现每个Task的完整正向能力，再补聚焦验证；不测试先行，不建设权限、冲突、乱序、跨租户或Provider失败矩阵。
- 只新建`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`三张业务真值表；既有`cut_task`仅加任务配置身份三列，不新增映射表或第二配置真值。
- 不修改旧`pms_cut_risk`、旧页面、旧接口、旧表或Yudao基础平台。
- 不实现INT-12/DAC、外部技术公告或其他第三方Provider；只保留接口、稳定失败和人工降级路径。
- 所有新增Mapper查询遵守`docs/coding/database-query-interface.md`：场景Query单参数、锁查询进入XML、禁止SQL注解和Service拼SQL。
- 写事务首锁必须是`ProjectScopeApi.lockAndRevalidate`，随后遵守F-CUT-002既有Owner顺序，再锁`CutoverTask → submitted assessment → checklist → stableItemKey顺序items`。
- F-CUT-002生产Owner未接通时，不注册Fake到生产，不以测试替身、手工SQL或内部API声明浏览器闭环及Implementation Done。
- F-AST-002产品类型公开API尚未集成时，CUT只保留与其结果同形的消费端口；`src/test`受控替身可驱动设备类型正向匹配，不阻断Task 1、REST/UI候选或聚焦验证。

---

## 1. 实施边界

- 覆盖清单生成、动态匹配、直接填写、任务级自定义项、草稿重匹配、人工证据降级、暂存和提交P4。
- 设备采集只实现`CutoverCollectionPort.request/inspect`的CUT消费契约；外部数据只实现`CutoverExternalDataPort.inspect`契约，生产Provider均不在本Feature。
- 自动/外部不可用时，业务正向链使用PLT公共文件事实形成`MANUAL`结果；失败事实保留，不改写为自动成功。
- 配置缺口允许创建`CUSTOM`项继续；配置定义冲突必须由工程师明确选择有效定义后继续。
- 提交只读取适用项的当前选择结果，全部必填完成后把清单置`SUBMITTED`，并把任务从`SURVEYING`推进到`PLAN_DRAFTING`。
- 提交后的变更、下游方案失效入口和清单新版本生成能力保留在领域服务中；本Feature不新增外部失效REST。

## 2. 模块与文件责任

| 责任 | 文件或目录 | 处理 |
|---|---|---|
| 任务配置身份与三表物理模型 | `sql/migrations/V178__fcut003_p3_dynamic_checklist.sql` | 给`cut_task`增加配置revision三列并按批准规则补齐既有NEW_PLATFORM任务，再新建三表和三个权限按钮；不授权固定角色，不改V146 |
| 清单DO/Mapper | `pms-module-cutover/src/main/java/.../dal/dataobject/checklist/`、`.../dal/mysql/checklist/`、`src/main/resources/mapper/checklist/` | 三表DO、场景Query、锁查询、CAS和稳定排序；不访问其他Context表 |
| 配置读取与匹配 | `.../service/checklist/CutoverChecklistConfigurationQueryService.java`、`CutoverChecklistMatcher.java` | 按任务冻结的revision ID/code/no精确读取F-CUT-001修订、项定义和规则；允许该历史修订后来DISABLED，不按P3当前时间重选；按稳定键匹配、去重、合并必填、暴露GAP/CONFLICT |
| 清单命令内核 | `.../service/checklist/CutoverChecklistApplicationService.java`、`command/`、`result/` | 生成、重匹配、暂存、自定义项、人工结果、设备采集请求和提交；统一幂等与锁序 |
| 清单查询 | `.../service/checklist/CutoverChecklistQueryService.java`、`view/CutoverChecklistViews.java` | 当前清单、分块项目、匹配轨迹、当前结果及allowedActions；D级返回资源不存在语义 |
| 外部消费端口 | `.../service/checklist/port/CutoverCollectionPort.java`、`CutoverExternalDataPort.java` | 只定义稳定请求/结果/失败联合；`src/test`可提供受控正向替身，生产不实现Provider |
| PLT文件策略 | `.../service/checklist/CutoverChecklistFilePolicyProvider.java` | 实现`CUT/CUTOVER_CHECKLIST_ITEM/{itemId}/MANUAL_EVIDENCE`策略，复用现有`FileArtifactApi`和ProjectScope重验，不修改PLT |
| 用户REST | `.../controller/admin/taskv2/CutoverChecklistController.java`、`vo/checklist/` | 精确实现Feature机器合同九条REST；Controller只转换Header/VO，不拼状态或SQL |
| P3工作台 | `yudao-ui/.../src/api/pms/cutover/cutover-task/index.ts`、`src/views/pms/cutover/cutover-task/components/CutoverChecklistPanel.vue`、`CutoverChecklistField.vue` | 在现有详情P3步骤内渲染Schema、配置缺口、结果来源、人工证据、保存和提交，不新建页面 |
| 聚焦验证 | CUT后端与工作台同目录测试、`scripts/tests/run_fcut003_browser_acceptance.cjs` | 所有实现完成后验证单条正向链；不扩建异常矩阵 |

## 3. 核心实现决策

### 3.1 配置读取、匹配与快照

`CutoverChecklistConfigurationQueryService.resolveFrozen(MatchInput)`只按任务冻结三元组读取同一配置修订及其全部启用定义/规则；该revision后来`DISABLED`仍可读取，DRAFT或身份不一致失败。`MatchInput`冻结：

- `cutoverType`、`networkMode`、从`cut_task_device_scope.device_type_code_snapshot`去重稳定排序得到的设备类型集合、`manualGrade`；该快照由F-CUT-002创建时从F-AST-002公开产品类型事实冻结，P3不重新查询或推断；
- 版本升级场景的当前/升级后版本；
- 配置修订中启用的扩展维度；
- `configurationRevisionId/configurationCode/configurationRevisionNo`；
- `taskId/taskVersion/assessmentId/assessmentVersion/projectScopeVersion`。

配置查询新增`CutoverFrozenConfigurationQuery`，用`tenantId + configurationRevisionId + configurationCode + configurationRevisionNo`精确读取任务冻结revision；三项任一不匹配、目标为DRAFT或缺失时返回稳定配置错误，不查看P3当前发布指针、不任选。定义和规则继续复用现有Mapper，按`sortOrder/id`与`priority/id`稳定排序。

`CutoverChecklistMatcher.match`产生`MatchedItem/GAP/CONFLICT`：

- 同一`stableItemKey`命中兼容规则时去重，`required=true`采用任一命中为真的合并结果，并保存全部规则身份；
- 同一稳定键命中不同定义版本时输出`CONFLICT`，由明确的`selectedDefinitionId/version`解决；
- 无适用规则时在`config_gap_snapshot`记录GAP，允许后续创建CUSTOM项；
- 高可靠组网仅保留对应一类双机检查；非版本升级不产生升级后版本字段；
- `input_snapshot/config_revision_snapshot/match_trace`使用规范JSON，`input_snapshot_hash`只用于判断草稿重匹配输入是否改变。

### 3.2 三表状态与结果选择

- `CutoverChecklist`状态只用`DRAFT/SUBMITTED/INVALIDATED`；同任务仅一个未失效当前版本。
- SYSTEM项必须保存定义ID/版本；CUSTOM项的定义ID/版本必须为空，保存`source_code=CUSTOM`、服务端稳定键和`custom_creator_user_id`。
- 直接填写、采集引用、外部引用和人工降级都追加`CutoverChecklistItemResult`。切换当前选择时，在同一事务关闭旧结果区间后插入下一`result_version`。
- `MANUAL`结果先调用`FileArtifactApi.lockAndRevalidate`核对`CUT/CUTOVER_CHECKLIST_ITEM/{itemId}/MANUAL_EVIDENCE`、文件版本、referenceKey、fileFactVersion和scopeVersion；完整公共Fact进入`answer_snapshot`，`manual_evidence_file_reference`只保存同一Fact的referenceKey。
- 客户端文件Fact、CollectionTask成功状态和外部数据不得成为CUT通过真值。提交只判断当前结果是否满足冻结的项目Schema和必填规则。

### 3.3 命令、锁序与幂等

命令记录固定为：

- `GenerateChecklistCommand(tenantId, actorId, taskId, expectedTaskVersion, expectedAssessmentVersion, expectedProjectScopeVersion, selectedConflictDefinitions, idempotencyKey, correlationId)`；
- `RematchChecklistCommand(..., checklistId, expectedChecklistVersion, expectedInputSnapshotHash, selectedConflictDefinitions, idempotencyKey)`；
- `SaveChecklistCommand(..., checklistId, expectedChecklistVersion, directAnswers, backgroundData)`；
- `AddCustomItemCommand/RemoveCustomItemCommand`保存服务端稳定键、创建人和引用状态；移出只把本人创建且从未产生结果版本的CUSTOM项置为不适用，不物理删除历史行；
- `RequestCollectionCommand(tenantId, actorId, taskId, expectedTaskVersion, checklistId, expectedChecklistVersion, expectedProjectScopeVersion, stableItemKey, idempotencyKey, correlationId)`只调用`CutoverCollectionPort.request/inspect`并保存请求、结果或失败引用；端口请求冻结project/task/checklist/item/device/commandTemplate及各自版本，返回`ACCEPTED/RUNNING/COMPLETED/FAILED`，只有结果引用完整的`COMPLETED`当前结果参与必填完成判断；
- `SelectManualResultCommand`携带PLT公共文件句柄，Owner重验后写规范Fact；
- `SubmitChecklistCommand(..., expectedTaskVersion, expectedAssessmentVersion, checklistId, expectedChecklistVersion, expectedProjectScopeVersion, idempotencyKey, correlationId)`。

写命令先用公开inspect和CUT只读投影取得稳定身份，不写业务；进入事务后顺序固定为：

1. `CutoverProjectScopePort.lockAndRevalidate(actorId, projectId, ACTION_EDIT, expectedProjectScopeVersion)`；
2. 按F-CUT-002既有顺序重验当前命令实际需要的项目/设备/客户/IMP事实；
3. 锁`CutoverTask`，确认NEW_PLATFORM、owner、`SURVEYING`、A/B/C和期望版本；
4. 锁当前`SUBMITTED`评估并核对ID、业务版本和人工等级；
5. 锁当前Checklist及其期望版本，再按`stableItemKey`锁项目；
6. 完成结果区间切换或提交状态写入。

生成、重匹配、采集请求和提交使用`PlatformCommandExecutionApi`。同作用域同摘要重放原结果；异摘要冲突。`CutoverChecklistItemResultLinked`只在一个结果版本被CUT选为当前结果时随同一事务写Outbox，载荷冻结task/checklist/item/result版本和技术引用，不表达P3完成。

### 3.4 REST、权限与页面

- GET沿用`pms:cutover-task:query`；生成、重匹配、暂存和自定义项使用`save-checklist`；设备采集请求使用`request-collection`；提交使用`submit-checklist`。
- Service必须同时重验项目`ACTION_EDIT`、任务负责人、任务/评估/清单版本和状态；不按角色名放行。
- `CutoverTaskDetail.allowedActions`加性增加`GENERATE_CHECKLIST/SAVE_CHECKLIST/REQUEST_COLLECTION/SUBMIT_CHECKLIST`，只有服务端满足条件时返回。
- `CutoverChecklistPanel`只消费服务端Schema和allowedActions。控件覆盖文本、单/多选、表格、文件、业务引用和采集项；GAP、CONFLICT、自动失败和MANUAL来源使用文字与图标双重表达。
- 人工证据通过既有PLT上传入口和CUT文件策略形成Reference；页面不拼Artifact事实、不接受粘贴JSON。

### 3.5 V147前向迁移

`V178__fcut003_p3_dynamic_checklist.sql`先给`cut_task`增加可空`configuration_revision_id/configuration_code/configuration_revision_no`，再在任何更新前完成全表预检：

- 既有`NEW_PLATFORM`按原`create_time`，从具有正式发布事实、状态为`PUBLISHED/DISABLED`且生效区间覆盖该时点的历史revision中解析；DRAFT排除；
- 全租户全部配置代码恰好一个候选时补齐三元组；零个或多个候选整批失败，不使用`CUTOVER_DEFAULT`、当前时间、类型或名称推断；
- `LEGACY_FORWARD`三列保持空；补齐后增加来源联合CHECK，保证NEW_PLATFORM三列非空且revision ID/code/no精确一致，LEGACY_FORWARD三列全空。

随后严格从SDS物理附录创建三表列、唯一键、生成列、索引、检查与item/result外键，并增加：

- `status_code`限定`DRAFT/SUBMITTED/INVALIDATED`；
- `result_source_code`限定`DIRECT/COLLECTION/EXTERNAL/MANUAL`；
- SYSTEM/CUSTOM字段组合检查，不为自定义项伪造定义ID；
- 既有工作台菜单`992602050001`下新增`992602050006/007/008`，分别承载`save-checklist/request-collection/submit-checklist`；查询继续复用V146的`pms:cutover-task:query`。

迁移不修改V128～V146，不为通用角色预授权，不插入清单实例。若实施落文件前已有并行迁移占用V147，只把本迁移整体顺延到下一连续空闲号并同步计划检查点，不修改SQL语义。

## 4. Task 1：CUT三表、匹配、填写与提交内核

**Produces：** 可编译、可由受控正向装配执行的完整CUT清单内核；不依赖第三方Provider即可通过DIRECT/MANUAL完成P3→P4。

- [ ] 建立任务配置身份字段、三表DO、场景Query、Mapper/XML和V147，完成历史唯一补齐并落位当前版本、当前结果、稳定项及权限菜单。
- [ ] 实现`CutoverChecklistConfigurationQueryService`和`CutoverChecklistMatcher`，只消费任务冻结的F-CUT-001 revision，输出稳定匹配、GAP和CONFLICT，不复制配置表或矩阵。
- [ ] 实现Generate/Rematch/Save/Custom/Manual/Submit命令及Query View；所有写命令使用3.3统一锁序和幂等组件。
- [ ] 实现`CutoverCollectionPort/CutoverExternalDataPort`接口和CUT文件策略；生产不实现第三方Provider，MANUAL使用PLT规范公共Fact。
- [ ] 实现完成后补聚焦后端验证：真实MySQL证明既有NEW_PLATFORM任务唯一历史revision可补齐、零/多候选整批失败；A/B/C按冻结revision生成DRAFT；DIRECT与带文件Fact的MANUAL结果暂存；CUSTOM补足GAP；提交后Checklist=SUBMITTED且Task=P4；同键重放不重复。
- [ ] 只运行CUT聚焦测试与受影响Maven reactor `package -DskipTests`；不运行Phase 1/2/3或全仓负向回归。

Task 1结束不单独申请Implementation Done；若CUT内核与V147验证通过，直接进入Task 2接工作台。

## 5. Task 2：P3工作台与一次真实正向验收

**Produces：** 生产Owner到位后，一线工程师可在正式工作台完成生成、填写/人工降级、暂存、提交和刷新核对。

- [ ] 在F-CUT-002生产Controller/Owner Adapter已接通后注册`CutoverChecklistController`和`CutoverChecklistFilePolicyProvider`；依赖未到位时保持`BLOCKED_BY_DEPENDENCY`，不注册Fake或跨表fallback。
- [ ] 扩展现有cutover-task API类型和详情allowedActions，新增`CutoverChecklistPanel/CutoverChecklistField`，只在P3展示；D级不创建或显示清单。
- [ ] 完成工作台生成、冲突选择、DIRECT填写、CUSTOM新增、PLT人工证据上传、暂存和提交；提交成功刷新为P4并只读展示已提交清单。
- [ ] 前端能力完成后补三项聚焦交互验证：Schema驱动控件、保存刷新、MANUAL提交P4；随后运行定向Vitest、`ts:check`和`build:local`。
- [ ] 生产Owner到位后，以正式权限身份和真实MySQL/Chromium完成一条“A级P3 → 生成 → DIRECT填写 + 一项MANUAL证据 → 暂存刷新 → 提交 → P4”正向链，核对三表与任务阶段一致。
- [ ] 更新唯一Feature Task和追溯检查点，形成一个Implementation Done候选并申请一次独立审核；不增加权限、Provider失败、跨租户、乱序或重试浏览器矩阵。

## 6. 验证与完成口径

候选级验证只回答五个正向交付问题：

1. 三张CUT表及权限菜单能否从当前基线前向迁移并通过Flyway validate；
2. A/B/C能否按任务冻结的F-CUT-001 revision生成唯一DRAFT，后续配置发布/停用是否不改变其解释，D级是否不生成；
3. DIRECT、CUSTOM和带PLT公共Fact的MANUAL能否在同一P3草稿保存并刷新保持；
4. 全部必填完成后能否原子形成SUBMITTED清单并把任务推进P4；
5. 生产Owner到位后，正式工作台能否完成同一正向链并与数据库一致。

服务端仍实现权限、版本、状态、范围和唯一性守卫，但本计划不把低收益负向组合建设成新的实施阻断。现有平台与模块测试继续覆盖其既有边界。

## 7. 风险、依赖与回退

- **F-CUT-002及F-AST-002生产依赖尚未集成：** 不阻断Task 1、REST/UI候选和受控正向验证，但继续阻断Controller生产装配、真实浏览器和Implementation Done；CUT不得复制PROJ/AST/CUS/IMP Owner。
- **INT-12/外部Provider未实现：** 不阻断V1正向闭环；页面展示稳定不可用事实并允许MANUAL，不能把人工结果写成自动成功。
- **共享Flyway竞争：** 落文件前重新读取最高版本；只前向改本迁移版本号，不修改已执行迁移。
- **任务配置身份缺失或冲突：** 新任务创建前已冻结三元组；V147存量补齐零/多候选整批失败；P3不重选。缺规则允许GAP+CUSTOM，定义冲突必须显式选择，生产配置错误不通过硬编码默认值掩盖。
- **回退：** 未发布迁移可删除候选文件；迁移一旦执行只允许新增前向纠正。业务回退不删除已提交清单、结果、审计或Outbox。

## 8. Technical Plan Gate

当前结论：`PASS / GO@ac740458`。允许创建唯一`tasks/features/F-CUT-003.md`并按本计划进入Implementation；本结论不批准产品代码结果、V147实际迁移执行、Implementation Done或后续Gate。
