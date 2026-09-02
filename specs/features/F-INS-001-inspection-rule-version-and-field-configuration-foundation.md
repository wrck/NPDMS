# F-INS-001 巡检规则版本与字段配置基础 Feature Spec

> 规格状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FINS001-FEATURE-READY-20260901-03`
> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> Requirement切片覆盖：`INS-03@V2=PARTIAL；INS-09@V2=FULL`
> Owner Context：`Inspection（SRV物理模块）`
> 前置Feature：`F-AST-002`设备产品类型受控副本与公开查询（发布、工程师选择和Implementation Done实施Gate）；基础平台字典能力
> 后续Feature：F-INS-002巡检任务准备与规则冻结（覆盖INS-01及INS-03剩余任务内选择/命令清单义务）、INS-02在线/离线执行、INS-05报告、INS-06问题标注、INS-08误报修订
> Open Questions：`Q-PRD-VS-009`、`Q-FINS001-001`、`Q-FINS001-002`均已关闭；`Q-FINS001-003`已由`NPDMS-Q-FINS001-003-GO-20260901-01`关闭；`Q-FINS001-004`已由`NPDMS-Q-FINS001-004-GO-20260901-01`关闭
> 适用基线：master PRD V1.8修订011；该修订统一承接来源分支修订009～012的巡检超时、AST产品类型、正则子集和规则名称身份裁决；SDS Phase 1/2/3 `BASELINE`
> 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
> Technical Plan：Feature Ready规格提交锁定后生成唯一计划

## 1. 目标与业务结果

本Feature建立Inspection领域唯一的巡检规则版本真值，使巡检-管理员能够按十类检测分类维护规则稳定身份和草稿revision，通过字段与安全审核后原子发布，并供工程师按授权设备产品类型只读选择当前有效版本。发布后内容不可覆盖，停用只阻止新任务选用，历史revision继续可读。

本Feature完成后表示INS-03规则库、INS-09八字段配置、命令列表、发布版本和历史解释形成可独立验收的V2配置基础；不表示巡检任务、在线/离线执行、INT-12下发、报告、问题、误报或归档已经完成。

## 2. 范围

### 2.1 包含

- 规则稳定身份：租户内永久唯一的检测ID和规则名称，以及检测分类、检测项目、描述、严重级别、排序和适用产品类型；停用、软删除和新revision不释放名称。
- 规则revision：修订号、`DRAFT/PUBLISHED/DISABLED`状态、八字段快照、维护与发布事实、乐观锁版本。
- 命令列表：稳定命令项、命令内容、连续且不重复的执行顺序、1～30秒超时阈值、超时后继续/停止决定。
- 判定配置：预期结果正则、固定阈值数据类型`NUMBER`、比较运算符、数值和单位。
- 十类检测分类和一般/严重/致命三级严重级别的基础平台字典种子。
- 产品类型适用范围：通过AST公开的设备产品分类查询校验稳定产品类型编码，并保存发布时显示名称快照，不新建产品类型主数据。
- 草稿创建、部分保存、整体保存、复制历史revision、无副作用校验、发布、停用和历史只读查询；稳定身份创建时检测ID和规则名称必填，其余八字段及从属内容允许在草稿阶段为空或不完整。
- 发布前字段校验、正则语法与复杂度校验、命令安全审核事实记录与校验、AST产品类型有效性校验和逐项错误定位。
- 管理页面与工程师选择投影；选择投影只返回当前有效、适用于授权设备的已发布规则。
- 租户隔离、维护/发布权限分离、状态与CAS守卫、操作审计和发布失败零半发布。

### 2.2 不包含

- INS-01巡检任务创建、九状态流转、规则快照落任务或任务取消。
- INS-02在线/离线执行、脚本生成、结果解析与INT-12 CollectionTask下发。
- INS-04连通性预检、INS-05报告、INS-06问题、INS-07归档、INS-08误报处理。
- 设备凭证、临时密码、采集执行引擎、UMC或其他第三方平台内部能力。
- 超过30秒的命令阈值或其审批流程。
- 对旧接口、旧页面、旧菜单、旧字典或旧类的改造、双写和退役；旧`pms_srv_rule`仅按本Feature批准的字段级前向迁移处理。

## 3. 领域边界与复用

- Inspection拥有`InspectionRule`稳定身份、revision、命令与判定配置、发布停用和历史解释。
- 基础平台拥有字典能力；Inspection只使用正式检测分类和严重级别字典值，不修改Yudao基础平台实现。
- CRM/MES拥有产品和设备来源事实；F-AST-002由AST保存设备可解析的产品分类受控副本并提供公开查询。Inspection保存稳定编码与发布时名称快照，不维护第二套产品类型库，不实现连接器。
- Device Access & Collection拥有凭证、授权、CollectionTask和外部执行证据；本Feature不连接设备、不下发命令。
- 命令安全审核由PRD定义的审批/任务角色组在Inspection规则revision上记录，绑定命令/正则内容摘要；本Feature不新增审批角色、节点或生命周期状态。
- 旧实现结论见复用审计，固定为`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。

## 4. 状态与不变量

### 4.1 规则revision状态

```text
DRAFT --publish--> PUBLISHED --disable--> DISABLED
```

- 只有草稿可编辑；状态只能通过发布和停用命令转换，客户端不得直接写状态。
- 已发布和已停用revision全部字段只读；修改必须复制为同一规则稳定身份的新草稿revision。
- 发布在单事务内完成全量校验和状态切换；失败保持草稿，旧发布版本继续有效。
- 同一租户、同一规则稳定身份只允许一个当前已发布revision；并发发布最多一个成功。
- 停用只阻止新任务和新选择消费；历史任务及审计仍可读取原revision。
- 稳定检测ID不得在后续revision中复用为不同业务含义。

### 4.2 字段不变量

- 规则名称归属规则稳定身份并在租户内永久唯一；停用、软删除和形成新revision均不释放。同一稳定身份的后续revision沿用原名称，历史revision不可改名；检测ID在租户内永久唯一。
- 检测分类使用`pms_inspection_rule_category`，稳定值为`BASIC/OPERATING_STATUS/LOG/BUSINESS_STATUS/REDUNDANCY/ROUTING/SECURITY/FORWARDING_CHANNEL/LOAD_BALANCING/TRAFFIC_CLEANING`；严重度使用`pms_inspection_rule_severity`，稳定值为`GENERAL/SEVERE/FATAL`。发布只接受当前启用值，机器码不得改义、复用或删除。
- 命令列表至少一条；顺序从1开始连续且不得重复。
- 每条命令超时阈值必须为1～30秒正整数；31秒及以上拒绝。
- 后续命令继续/停止决定必须随已发布revision冻结，不由执行时临时猜测。
- 正则引擎固定为JDK 25 `Pattern`，最多1024个UTF-16代码单元、32个分组、8层嵌套、31个分支符、64个量词节点和1000区间上界；禁止反向引用、环视、命名捕获、原子组、局部标志、嵌套量词、分支分组量化、无上界区间及PCRE专有结构。DRAFT允许阈值整体为空或结构不完整并继续保存；发布时阈值数据类型固定为`NUMBER`，且必须同时具有运算符、数值和单位，运算符仅允许`>`、`<`、`≥`、`≤`、`=`、`≠`，不得接受其他数据类型。
- DRAFT允许命令、正则、阈值和适用产品类型尚未填写或不完整；发布时至少一个命令和一个适用产品类型，全部引用必须存在且可用，并冻结编码和显示名称快照。
- 命令、正则、阈值或适用范围任何变化都形成新revision，不覆盖历史。
- 发布前扫描全部用户输入文本中的私钥头、认证头、URL内嵌用户名密码和密码赋值；明确占位符不按明文Secret处理。命中只返回字段路径和`SECRET_DETECTED`，不得回显或记录秘密正文。

## 5. 权限与数据范围

| 动作 | 权限码 | 服务端守卫 |
|---|---|---|
| 查询规则与历史 | `pms:inspection-rule:query` | 当前租户；已发布/停用历史只读 |
| 创建/编辑草稿 | `pms:inspection-rule:manage` | 当前租户、DRAFT、If-Match一致 |
| 校验草稿 | `pms:inspection-rule:manage` | 当前租户、DRAFT；无业务副作用 |
| 记录安全审核 | `pms:inspection-rule:security-review` | 当前租户、DRAFT、PRD定义的审批/任务角色组、revision与内容摘要一致；维护或发布权限不自动授予审核权 |
| 发布规则 | `pms:inspection-rule:publish` | 当前租户、DRAFT、字段校验、安全审核事实、发布CAS |
| 停用规则 | `pms:inspection-rule:disable` | 当前租户、PUBLISHED、停用CAS |
| 工程师选择 | `pms:inspection-rule:select` | 当前租户、授权设备、当前有效revision、产品类型适用 |

前端按钮只作展示，Controller和Service必须重复执行权限、租户、状态、版本及设备范围校验。规则管理权限不授予设备访问、凭证使用、命令执行或业务审批权；平台管理员身份不自动取得发布权。

## 6. API契约

统一前缀为`/api/v1/pms/inspection-rules`，HTTP层沿用平台统一`CommonResult`和分页响应：

- `GET /revisions`：按检测ID、名称、分类、严重级别、产品类型和状态分页查询revision摘要。
- `POST /revisions`：创建空白规则草稿或新稳定身份草稿。
- `GET /revisions/{revisionId}`：读取草稿详情或历史revision只读详情。
- `PUT /revisions/{revisionId}`：按`If-Match`整体保存草稿、命令列表、判定配置和产品类型快照。
- `POST /revisions/{revisionId}/actions/copy`：复制历史revision为同一稳定身份的新草稿。
- `POST /revisions/{revisionId}/actions/validate`：执行无副作用发布预检并返回字段级错误。
- `POST /revisions/{revisionId}/actions/record-security-review`：由PRD定义的审批/任务角色组记录当前命令/正则内容摘要的审核结论。
- `POST /revisions/{revisionId}/actions/publish`：按`If-Match`发布。
- `POST /revisions/{revisionId}/actions/disable`：按`If-Match`停用。
- `GET /selectable`：按授权设备和产品类型返回当前有效已发布规则，不返回停用或不适用项。

所有列表分页并稳定排序；发布与停用不接受客户端状态字段。外部产品类型和安全审核契约不可用时，发布失败关闭且旧版本继续有效。

## 7. 数据与前向迁移边界

正式SDS目标表：

- `srv_inspection_rule`：规则稳定身份、租户内永久唯一的检测ID与规则名称和租户边界。
- `srv_inspection_rule_revision`：不可变发布revision、包含稳定名称的八字段快照、发布停用和审计事实；名称快照必须与稳定身份一致。
- `srv_inspection_rule_command_revision`：revision从属命令项，revision内执行顺序唯一。
- `srv_inspection_rule_product_type_revision`：revision从属产品类型编码及名称快照，revision内产品类型唯一。
- `srv_inspection_rule_security_review`：绑定revision、内容摘要、审核主体角色组、`PASSED/REJECTED`结论和时间的发布前置事实；只有`PASSED`可发布。

Technical Plan只确定实现步骤和最终Flyway编号，不得改变上述物理关系、唯一约束和Owner。

`srv_inspection_task_rule_snapshot`属于后续INS-01/02任务消费，不在本Feature创建或写入。所有新表使用前向Flyway；不修改V14～V20、V43和任何旧迁移。旧`pms_srv_rule`按正式`CURRENT_FORWARD+FIELD_LEVEL_REVIEW`处理：只迁可证明字段；仅在十类分类、八字段、命令顺序、产品适用范围及安全审核事实完整可证时转换为可选发布revision，否则进入迁移问题或兼容只读，禁止推断缺失业务字段。

## 8. 管理与选择界面

- 管理页面以规则稳定身份和revision为主线，展示草稿、已发布、已停用及历史版本。
- 草稿详情分为基本信息、命令列表、判定规则、适用产品、发布校验；八字段均有明确输入和字段级错误。
- 已发布/停用revision全部只读，“新建修订”是唯一修改入口。
- 工程师选择视图按十类分类展示检测ID、项目、严重级别和适用产品类型；不适用项隐藏或置灰，均不得可提交。
- 页面提供加载、空数据、失败和权限不足状态；错误不只用颜色表达；支持键盘操作和320/768/1024/1440视口。
- 旧页面仅提供名称和界面样式参考，不因缺行、缺名或数量差异阻断实现或验收。

## 9. 异常、并发与审计

- 字段、正则、阈值、产品类型或安全审核失败时拒绝发布，返回逐项位置，草稿保持可修正，旧发布版本不变。
- 同一草稿并发保存或发布使用`If-Match`/乐观锁；陈旧请求拒绝且无半发布。
- 重复发布、停用或复制请求按业务幂等键处理，不重复生成有效revision。
- 审计记录八字段前后值、命令前后值、校验结果、安全审核引用、发布停用、失败原因、操作者和时间，不记录秘密明文。
- AST产品分类查询或安全审核校验不可用、未知时失败关闭，不用缓存猜测有效性；草稿保存不要求依赖校验成功，发布必须重验。

## 10. 验收标准

- AC-FINS001-001：有维护权限的巡检-管理员可创建草稿；检测ID租户内唯一，跨租户不可见。
- AC-FINS001-002：十类检测分类和三级严重级别可维护规则并用于筛选，字典值停用或不存在时阻止发布。
- AC-FINS001-003：稳定身份创建时检测ID和规则名称必填，规则名称在租户内永久唯一且停用/软删除不释放；草稿允许其余八字段及从属内容为空或不完整并可持续保存，发布时必须全部完整并执行字段级校验。
- AC-FINS001-004：多命令顺序从1连续且不重复；缺号、重复或空命令阻止发布并定位到命令项。
- AC-FINS001-005：超时默认30秒，可配置1～30秒；30秒通过，0秒和31秒拒绝。
- AC-FINS001-006：正则语法或复杂度风险、阈值数据类型不是`NUMBER`、运算符/数值/单位缺失或冲突均阻止发布；相同不完整内容允许保留为草稿，旧发布版本保持有效。
- AC-FINS001-007：安全审核结论只允许`PASSED/REJECTED`；事实缺失、`REJECTED`、失效或不适用于当前revision及内容摘要时拒绝发布，不生成未授权命令清单。
- AC-FINS001-008：发布后全部字段不可修改；复制历史revision只能在原稳定身份下沿用原名称并形成新草稿，历史版本保持可读，任何revision改名请求拒绝。
- AC-FINS001-009：同一规则并发发布最多一个成功；失败请求不产生半发布或提前停用旧版本。
- AC-FINS001-010：停用规则不再出现在新任务可选列表，但历史revision仍可查询和解释。
- AC-FINS001-011：工程师只能选择适用于授权设备产品类型的当前已发布规则；跨租户、无设备范围、不适用和已停用规则均拒绝。
- AC-FINS001-012：无发布权限、错误If-Match、直接写状态和旧接口越权均被服务端拒绝且无业务副作用。
- AC-FINS001-012A：无`pms:inspection-rule:security-review`权限、非PRD审批/任务角色组、跨租户或内容摘要不一致时拒绝记录安全审核；维护者和发布者不因自身权限自动获得审核权。
- AC-FINS001-013：旧接口和页面保持原功能不变且不双写；旧`pms_srv_rule`只按可证明字段受控前向迁移，缺失字段不推断，不完整记录不得成为可选发布revision。
- AC-FINS001-014：同租户同名规则并发创建最多一个成功；跨租户同名允许，停用或软删除后同租户仍不得复用名称，失败请求无孤立身份或revision。
- AC-FINS001-015：后端自动化、真实MySQL前向迁移、前端检查和真实浏览器四档视口通过，控制台无错误。

## 11. 测试与证据

- 正向优先：草稿创建→八字段维护→校验→发布→工程师按产品类型选择→复制新revision→停用→历史读取。
- 必要负向仅覆盖会改变决策的风险：权限/租户、0与31秒边界、命令顺序、正则复杂度、阈值冲突、安全审核、陈旧版本和并发发布。
- 不为未实现的INS-01/02/04/05/06/07/08预写业务测试，不以第三方平台不可用阻断本Feature内部闭环。
- UI完成后必须使用真实浏览器验证管理与选择路径，编译和单测不能替代业务验收。

## 12. Feature Ready Gate

当前结论：`READY / GO NPDMS-FINS001-FEATURE-READY-20260901-03`，替代`NPDMS-FINS001-FEATURE-READY-20260901-02`。

INS-03与INS-09属于同一InspectionRule主数据；本Feature完整覆盖INS-09，并覆盖INS-03的规则维护、发布、只读选择投影和历史解释子闭环。INS-03任务内勾选提交、命令清单生成及规则快照由后续F-INS-002覆盖。master PRD修订011已统一关闭超时上限、AST产品类型来源、受限JDK正则子集和规则名称稳定身份四项冲突；Q-PRD-VS-009已关闭超时后的后续命令策略。Requirement切片、Owner、状态、权限、API、数据边界、旧实现保留边界、第三方接口边界和验收标准已冻结；`Q-FINS001-005/006`只阻断安全审核生产入口、完整发布放行和Implementation Done。F-AST-002实际契约交付仍是发布、工程师选择和Done的实施Gate。本结论不表示完整发布、浏览器验收或Implementation Done已通过。
