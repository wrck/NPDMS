# F-CUT-003 P3动态采集清单、直接填写与人工降级闭环 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY`（GO@`ea986d61`）
> Requirement：`CUT-03（V1/P0）`
> Requirement切片覆盖：`CUT-03@V1=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-001`、`F-CUT-002`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> Feature边界裁决：`GO`（事实基线`9b3644d9`）
> 机器合同：`specs/features/F-CUT-003-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-003-legacy-reuse-audit.md`
> Technical Plan：`CANDIDATE / IN_REVIEW`（`NPDMS-FCUT003-TECHPLAN-20260831-01`）

## 1. 业务目标

割接-一线工程师在A/B/C级任务进入P3后，基于F-CUT-001已发布配置和任务冻结维度生成版本化采集清单，在同一工作台完成直接填写、任务级自定义项、暂存、人工证据降级、必填校验和提交，并由CUT原子推进到P4。D级继续跳过P3，不生成清单。

## 2. Scope

### 2.1 包含

- `CutoverChecklist / CutoverChecklistItem / CutoverChecklistItemResult`三类CUT Owner事实；
- 割接类型、组网模式、设备类型、最终等级及已启用扩展维度的动态匹配；
- 冻结配置修订、输入快照、定义/规则版本、匹配轨迹和配置缺口；
- 业务调研、风险考察、双机部署检查三类分块及高可靠组网单类检查表；
- 文本、选择、表格、文件、业务引用及设备采集工作方式的服务端Schema投影；
- 任务级自定义项、直接填写、人工证据降级、暂存、重匹配和提交；
- 设备采集请求、外部数据加载及结果引用的CUT消费端口和失败事实；
- 提交后清单不可覆盖、结果追加、当前选择唯一及P3到P4状态迁移；
- 项目/任务范围、最小权限键、幂等、并发、审计和响应式工作台。

### 2.2 不包含

- V2清单导出、可配置流程跳转或提前时间合规判断；
- P4方案、P5审批、P6执行与归档；
- INT-12/DAC连接器、设备连接、命令执行、原始采集引擎或回调Owner；
- 外部技术公告或其他外部数据源Provider；
- 通用工单、独立采集页面、第四张清单业务真值表；
- 修改旧`pms_cut_risk`、旧页面、旧接口、旧表或Yudao基础平台；
- 以人工证据伪造自动采集成功，或以受控替身声明跨Feature真实浏览器链完成。

## 3. 业务规则

### BR-FCUT003-001 生成资格与版本

- 仅当前`CutoverTask`为`SURVEYING`、最终等级为A/B/C、当前提交评估仍有效时可生成清单；D级和`LEGACY_FORWARD`不得生成。
- 同一任务仅一个未失效当前清单。首次生成使用`checklistVersion=1`；已提交版本不得原位编辑，受控变更只能关闭旧当前版本并追加新版本。
- 生成时锁定任务、当前评估，并按任务创建时冻结的`configurationRevisionId/configurationCode/configurationRevisionNo`精确读取同一`CutoverConfigurationRevision`，即使该修订后来已`DISABLED`仍按历史身份消费，并保存输入、定义、规则和字典快照。P3不得按当前时间、种子代码或任意当前发布版本重新选择；配置后续发布不改变既有任务或清单。
- 任务、评估、配置或项目范围身份不完整时整笔失败，不留下部分清单或项目。

### BR-FCUT003-002 动态匹配与重匹配

- 匹配输入固定为割接类型、组网模式、任务设备类型集合、最终等级和配置修订中启用的扩展维度；前端不得按名称硬编码。
- 规则仅从同一已发布配置修订读取。一个稳定项命中多条兼容规则时按`stableItemKey`去重，任一命中为必填则结果为必填，并保存全部命中规则版本。
- 同一稳定项命中互斥定义时状态为`CONFLICT`并列出规则；工程师选择一个有效定义后才可继续。无命中时记录`GAP`，允许新增任务级自定义项，不把配置缺口伪装为正常命中。
- 条件变化在当前DRAFT内重匹配。稳定项键和定义版本均未变化的有效答案可保留；移出适用范围的项和旧结果只保留历史，不进入提交门禁。
- 高可靠组网只显示当前组网模式对应的一类双机检查表；非版本升级任务不得出现升级后版本输入。

### BR-FCUT003-003 填写、结果与人工降级

- 直接填写保存为追加式`DIRECT`结果；每项同一时点最多一个未结束当前选择，切换结果时先关闭旧区间再追加新结果。
- 设备采集项通过正式INT-12消费端口创建绑定任务、清单版本、稳定项、设备和命令模板的CollectionTask引用。技术回调成功只产生候选结果，不等于业务通过。
- 外部数据和设备采集不可用时保留请求摘要、失败码和时间；授权工程师可改用`MANUAL`结果，并冻结PLT公共文件事实作为人工证据。原自动失败事实不可覆盖或改写为成功。
- 人工证据文件目标固定为`CUT/CUTOVER_CHECKLIST_ITEM/{checklistItemId}/MANUAL_EVIDENCE`。CUT文件策略Provider锁定任务、清单和项目范围，PLT继续拥有上传、扫描、存储、Artifact/Version/Reference和Access Ticket。
- 文件结果只保存PLT公共`artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/sha256`，不得保存或读取PLT内部主键。完整公共Fact冻结在既有`answer_snapshot`中，`manual_evidence_file_reference`只保存同一Fact的稳定`referenceKey`，两者不形成第二文件真值。

### BR-FCUT003-004 自定义项与提交

- 自定义项仅能由当前任务的一线工程师在DRAFT中新增；稳定键由服务端生成并标记`CUSTOM`。本人可在提交前删除尚未被引用的自定义项。
- 系统预置必填项不得删除、改名或改为选填；配置冲突未选择、适用必填项无当前有效结果、失败降级无人工证据或未通过项缺事实说明时不得提交。
- 暂存只保存当前DRAFT，不推进阶段。提交锁定任务、评估、清单、项目范围及全部当前结果，在一个CUT事务内把清单置`SUBMITTED`、任务从`SURVEYING`推进到`PLAN_DRAFTING`并追加阶段历史。
- 提交使用`Idempotency-Key + If-Match taskVersion + checklistVersion`。同键同载荷返回原结果，异载荷或陈旧版本冲突，不产生部分状态。

### BR-FCUT003-005 权限与工作台

- `pms:cutover-task:query`控制查看；`save-checklist`控制生成、重匹配、暂存及自定义项；`request-collection`控制设备采集请求；`submit-checklist`控制提交。
- 功能权限不能扩大项目/任务范围。所有写命令重验当前租户、项目`ACTION_EDIT`、任务负责人、任务/评估版本和状态。
- 工作台只渲染服务端返回的Schema、条件、工作方式和`allowedActions`；外部接口不可用时提供人工路径，不新增中转页。
- 审计保存配置/规则/定义版本、输入快照、增删项、答案选择区间、自动失败、人工证据、提交人、状态前后值和幂等操作ID，不记录密码或文件正文。

## 4. API与模块契约

所有用户接口继承`/api/v1/pms`，精确字段见机器合同：

| 接口 | 操作 | 业务结果 |
|---|---|---|
| `/cutover-tasks/{taskId}/checklist` | `GET` | 当前清单、分块项目、结果、匹配轨迹和`allowedActions` |
| `/cutover-tasks/{taskId}/checklist/actions/generate` | `POST` | 生成当前DRAFT清单 |
| `/cutover-tasks/{taskId}/checklist/actions/rematch` | `POST` | 在DRAFT中按当前条件重匹配并返回差异 |
| `/cutover-tasks/{taskId}/checklist` | `PUT` | 暂存直接填写结果和背景数据 |
| `/cutover-tasks/{taskId}/checklist/custom-items` | `POST` | 新增任务级自定义项 |
| `/cutover-tasks/{taskId}/checklist/custom-items/{stableItemKey}` | `DELETE` | 移除本人创建且尚未被引用的自定义项 |
| `/cutover-tasks/{taskId}/checklist/items/{stableItemKey}/collection-requests` | `POST` | 通过INT-12消费端口请求设备采集 |
| `/cutover-tasks/{taskId}/checklist/items/{stableItemKey}/manual-results` | `POST` | 选择人工结果并冻结公共文件事实 |
| `/cutover-tasks/{taskId}/checklist/actions/submit` | `POST` | 校验清单并原子进入P4 |

设备采集和外部加载只定义CUT消费端口及稳定失败，不实现Provider。`CutoverChecklistItemResultLinked`只表示CUT选择了一个结果版本，不表示DAC任务成功、采集项通过或P3完成。

## 5. 数据与迁移边界

- 只新建SDS已锁定的`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`三张表；不新建同义根、映射真值表或结果中转表。
- 清单以`tenant_id + cutover_task_id + checklist_version`唯一，生成列`current_marker`保证任务最多一个未失效版本。
- 项目以`tenant_id + checklist_id + stable_item_key`唯一；结果以`tenant_id + checklist_item_id + result_version`唯一，生成列保证每项至多一个当前选择。
- 系统匹配项必须保存`item_definition_id/item_definition_version`；任务级自定义项两字段必须为空，并以`source_code=CUSTOM`、服务端稳定键和`custom_creator_user_id`识别，不伪造配置定义身份。
- 三表为`NEW_ONLY / FEATURE_FORWARD_MIGRATION`。旧`pms_cut_risk`字段和值域未经确认，禁止迁移、反推或双写。
- Flyway在实施时取下一连续空闲版本；Feature Ready不预约版本号、不提交DDL。

## 6. UI

- 在F-CUT-002任务详情P3步骤内新增清单面板，不新建独立采集任务页面。
- 顶部固定显示项目、最终等级和割接信息；主体按业务调研、风险考察、双机检查分块。
- 控件由服务端Schema驱动；配置缺口、规则冲突、自动失败和人工来源必须清晰区分，不能只用颜色表达。
- 保存与提交分离；提交后只读。320/768/1024/1440宽度无页面级横向溢出。

## 7. 验收标准

- AC-FCUT003-001：A/B/C任务只按任务冻结的配置revision及四维/扩展维度生成唯一DRAFT清单，保存配置/规则/定义版本；缺少或不一致的配置身份失败关闭，D级不生成。
- AC-FCUT003-002：同一稳定项多规则命中去重并正确合并必填；高可靠组网只展示对应检查表；版本升级条件正确控制升级后版本。
- AC-FCUT003-003：直接填写、自定义项、暂存和重匹配均在同一P3工作台完成，稳定项保留有效答案，移出项不参与提交。
- AC-FCUT003-004：设备/外部请求失败事实保留，工程师可用带PLT公共文件事实的MANUAL结果继续，原失败事实不被改写。
- AC-FCUT003-005：全部适用必填项完成后提交，清单置SUBMITTED且任务原子进入P4；重放不重复，失败不留部分结果。
- AC-FCUT003-006：旧`pms_cut_risk`页面、接口、表和状态机保持不变；V2导出和流程跳转配置不可达。
- AC-FCUT003-007：实现完成后以CUT聚焦服务验证和一次真实工作台完成“A/B/C生成→直接填写/人工证据→暂存→提交→P4”；跨Feature生产依赖未关闭时不得以替身宣称Implementation Done。

## 8. Feature Ready Gate

当前结论：`BASELINE / READY`（GO@`ea986d61`）。允许进入唯一Technical Plan形成阶段；Technical Plan独立GO前不得创建Task、产品代码或Flyway。
