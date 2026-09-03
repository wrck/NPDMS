# F-IMP-001 割接上线实施就绪快照 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / NO-GO`
> Requirement：`EXE-06（V1/P0）`
> Requirement切片覆盖：`EXE-06@V1=PARTIAL`
> Owner Context：`IMP（现场实施）`
> 消费Feature：`F-CUT-002`
> 前置Feature：`F-IMP-002`、`F-IMP-003`、`F-IMP-004`、`F-IMP-005`、`F-PROJ-003`
> AST支撑Task：`T-FIMP001-AST-01`（物理Owner AST；不形成独立Feature Done）
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 独立裁决：`NO-GO`（锁定提交`72ccb83f8052758e70fc585b1226403b6a825311`）

## 1. 业务目标

由IMP基于EXE-01到货签收、EXE-02硬件安装、EXE-03配置调试和EXE-04业务联调的权威Owner事实，实时计算并追加保存不可变的`ImplementationReadinessSnapshot`，向CUT提供稳定的查询与锁定重验契约。只有四项均完成且来源版本仍有效时返回`READY`；本Feature不创建或推进CUT任务。

## 2. Scope

### 2.1 包含

- 项目经理对本人负责项目执行实施就绪检查，保存四项来源事实、来源版本、设备范围、批准方案引用及判定结果快照；
- `READY/NOT_READY`判定和未满足项代码，不建立额外业务生命周期；
- 快照只追加、历史只读、同一幂等意图重放和并发快照序号控制；
- IMP公开的查询、评估、`inspect/lockAndRevalidate`契约以及`ImplementationReadinessSnapshotPublished`事件；
- CUT创建和每次继续前可按明确快照ID、版本和来源水位重验；
- 项目范围、项目经理主体、租户隔离、审计和失败关闭。

### 2.2 不包含

- EXE-01～04各自的业务表单、状态机、文件、采集回调或整改流程；
- 把旧`pms_eng_arrival/installation/configuration/joint_test`整数状态直接解释为正式Owner事实；
- CUT任务、问卷、等级、清单、方案、审批、执行、闭环或项目S4/S5状态推进；
- 第三方系统连接器、跨系统跳转、V2/V3能力或Yudao基础平台修改；
- 用测试替身、手工SQL或种子数据声明生产Provider、真实浏览器闭环或Implementation Done。

## 3. Owner与依赖

- IMP唯一拥有`ImplementationReadinessSnapshot`和`imp_implementation_readiness_snapshot`；PROJ只拥有`proj_project_stage_snapshot`，不得承载EXE-06快照。
- F-IMP-002～005分别通过`ArrivalAcceptanceFactApi`、`InstallationCompletionFactApi`、`ConfigurationCompletionFactApi`、`JointDebuggingCompletionFactApi`返回明确业务结果及版本水位。F-IMP-001不得依赖其`-biz`、Service、Mapper、DO或业务表。
- PROJ使用`ProjectScopeApi.ACTION_EDIT`合并本人参与、负责或明确授权项目；AST通过`T-FIMP001-AST-01`交付的`DeviceScopeFactApi`提供明确设备ID、序列号、当前项目归属及归属版本事实。该API按受信租户和项目批量解析SN，以`deviceId`升序返回设备及`deviceId/projectAssignmentVersion`结构化水位；锁定重验只把完整有效集合的归属版本变化判为`STALE`，缺失、状态不可用、错项目或Provider故障失败关闭。该支撑Task复用F-AST-001现有Device聚合，不新增表，也不形成独立Feature Done。
- F-CUT-002只消费IMP公开事实，不直接查询IMP表，也不能以Provider调用成功替代`READY`判定。
- 硬依赖形成顺序：EXE-01～04权威事实 → F-IMP-001快照Provider → F-CUT-002真实创建/继续验收。

## 4. 业务规则

### BR-FIMP001-001 实时评估与不可变快照

- 每次评估重新读取明确项目、设备范围、批准方案以及EXE-01～04最新Owner事实；不得复用旧项目阶段缓存。
- 四项结果分别要求：到货`ACCEPTED`、安装`COMPLETED`、配置调试`COMPLETED`、业务联调`COMPLETED`。任一事实缺失、部分完成、失败、重新打开或Provider不可用均判定`NOT_READY`。
- 评估完成后追加保存一个快照，包含来源对象ID、业务版本、水位、判定和未满足项；历史快照不可覆盖或删除。
- 快照判定不直接修改项目阶段或CUT状态。

### BR-FIMP001-002 设备范围与方案变化

- 快照冻结项目ID、项目版本、规范化设备ID集合、各设备归属版本、批准方案ID/版本和四项来源事实版本。
- CUT重验时，项目、设备范围、任一归属版本、批准方案或任一来源事实变化均返回`STALE`或`NOT_READY`，不得继续。
- 同一项目内设备范围必须全部有权且归属明确；空范围、重复设备、跨租户或不属于项目的设备拒绝评估。

### BR-FIMP001-003 幂等、并发与失败语义

- 评估命令使用平台既有幂等能力；作用域固定为`IMP_READINESS_EVALUATE:{tenantId}:{projectId}:{actorId}:{Idempotency-Key}`。
- 同键同规范化请求重放首次结果；同键异请求冲突。复用既有命令幂等摘要，不新增第二套哈希或指纹机制。
- 同项目快照序号通过数据库唯一键和事务内重试分配；失败不得留下半快照或发布事件。
- Provider超时、未知或版本变化失败关闭；可以记录失败审计，但不得产生`READY`快照。

### BR-FIMP001-004 权限与审计

- 项目经理只能评估本人负责且处于允许实施阶段的项目；历史查询使用`ACTION_VIEW`，评估命令使用`ACTION_EDIT`并叠加项目经理业务守卫。
- CUT内部消费使用受信租户和明确业务动作，仍需校验项目/设备范围及快照版本，不能继承项目经理发起权限。
- 审计记录项目、设备ID、来源对象与版本、前后判定、未满足项、快照ID、操作者和时间；不复制文件正文、设备凭证或配置Log正文。

## 5. API与事件契约

所有用户路径继承`/api/v1/pms`并返回统一`CommonResult`：

| 接口 | 操作 | 契约 |
|---|---|---|
| `/implementation-readiness/{projectId}` | `GET` | 返回最新可见快照和未满足项；无副作用 |
| `/implementation-readiness/{projectId}/history` | `GET` | 稳定游标分页返回历史快照摘要 |
| `/implementation-readiness/{projectId}/actions/evaluate` | `POST` | 项目经理评估并追加快照；必填`Idempotency-Key`，请求含设备ID集合与批准方案引用 |

跨模块契约`ImplementationReadinessApi`：

- `inspect(ImplementationReadinessQuery)`：读取明确快照事实，不持锁；
- `lockAndRevalidate(ImplementationReadinessRevalidationQuery)`：按期望快照、项目、设备、方案和来源版本锁定重验；
- 返回`READY/NOT_READY/STALE`、`snapshotId/snapshotNo/version`、项目/设备/方案版本及`unmetCodes`；不返回Owner表实体或正文。

事件`ImplementationReadinessSnapshotPublished`只在快照事务成功后发布，payload包含`snapshotId/version/projectId/decision/unmetCodes`。事件发布成功不改变CUT或项目阶段状态。

## 6. 数据与物理边界

机器契约：`specs/features/F-IMP-001-physical-contract.json`。

- 新表`imp_implementation_readiness_snapshot`使用`uk(tenant_id, project_id, readiness_type, snapshot_no)`；`readiness_type`当前固定为`CUTOVER`。
- 保存项目/设备/方案引用与版本、四项来源事实向量、判定、未满足项、评估人、评估时间和乐观锁版本。
- 来源事实向量与设备范围以结构化JSON快照保存，字段只允许稳定ID、业务版本、水位和判定，不保存业务正文。
- 迁移策略为`REBUILD_AFTER_OWNERS`：只从已通过Feature Gate的Owner公开事实生成，不从旧表或附件批量导入READY快照。
- Flyway只使用实施时下一未占用版本，不修改既有迁移。

## 7. 验收标准

- AC-FIMP001-001：四项权威事实均完成时，项目经理评估生成唯一不可变`READY`快照。
- AC-FIMP001-002：任一事实未完成、重开、失败或不可用时生成`NOT_READY`或失败关闭，返回明确未满足项且不产生READY。
- AC-FIMP001-003：项目、设备范围、归属版本、批准方案或来源版本变化后，旧快照重验返回`STALE/NOT_READY`。
- AC-FIMP001-004：同键同请求重放同一快照；同键异请求、并发序号冲突和越权均无半快照或成功事件。
- AC-FIMP001-005：跨租户、非项目经理评估和无项目范围查询被服务端拒绝且不泄露Owner正文。
- AC-FIMP001-006：CUT通过公开契约读取及重验，IMP不可用或事实未知时CUT侧失败关闭。
- AC-FIMP001-007：真实MySQL验证追加唯一键、历史不可变和事务回滚；真实浏览器只在EXE-01～04真实Owner事实形成后验收。

## 8. Feature Ready Gate

当前结论：`NOT_READY / NO-GO`。

已完成：SDS物理Owner冲突已纠正为`imp_implementation_readiness_snapshot`，生成投影和Phase 3验证通过。

F-IMP-002已通过Feature Ready并冻结ArrivalAcceptanceFactApi；F-IMP-003～005仍须分别通过Feature Ready，旧`pms_eng_*`映射须按各Feature锁定。F-AST-001现有`ast_device`已核验具备稳定设备ID、当前项目和归属版本，`DeviceScopeFactApi`改由`T-FIMP001-AST-01`承接，机器契约仍须通过评审并由AST Owner实现。其余设计输入通过独立评审后，才可重审F-IMP-001 Feature Ready；相关Feature Ready通过后可使用受控替身实施不依赖生产事实的部分，EXE-01～04与AST生产事实未形成前仍不得声明Implementation Done或真实浏览器闭环。
