# F-CUT-006 P6割接跟踪与闭环

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@4e390d4f`
> Technical Plan Gate：`PASS / GO@354471f1`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-06@V1=FULL`
> Feature Spec：`specs/features/F-CUT-006-p6-cutover-closure.md`
> 机器合同：`specs/features/F-CUT-006-api-contract.json`、`specs/features/F-CUT-006-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-006-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-006-p6-cutover-closure.md`

## 当前最小工作单元

- 形成CUT-06完整纵向Feature，不拆成INT-12专用Provider碎片。
- 跨模块仅保留`ProjectScopeApi/FileArtifactApi/INT-12`消费端口；正常正向闭环使用`src/test`受控替身，不修改其他Owner或Yudao。
- Feature Ready独立最小整改复审已在`4e390d4f`裁决`GO`，采集基数、归档/晚到回调、平台事务顺序/resultRef及PLT迁移证据生命周期四项阻断均已关闭。
- 唯一Technical Plan已通过`PASS / GO@354471f1`；正向实施顺序、两类失败采集的人工替代及INT-12外部任务同意图恢复三项计划阻断已关闭。
- Task 1闭环领域类型、PLT/INT-12消费端口及`src/test`受控替身已通过独立复审：`PASS / GO@35c8462d`；未注册生产Bean、Fake或fallback。
- Task 1最小整改已在采集恢复事实中加入不含`transientSecret`的稳定请求摘要；同一意图只能恢复同摘要外部任务，异摘要按幂等冲突失败关闭。
- Task 1聚焦证据：`CutoverClosurePortContractTest` 6/6通过；`pms-module-cutover`及依赖模块打包通过。
- Task 2三表Schema、任务归档前向约束与Mapper合同已通过独立复审：`PASS / GO@6c3dd424`。V155已创建CUT自有闭环、附件和采集证据三表，前向扩展`P6/ARCHIVED`与`P6_CLOSURE_SUBMITTED`，并补齐闭环/附件/证据、任务归档和设备释放的场景化Mapper/XML。
- Task 2聚焦证据：`Fcut006MigrationContractTest`与`CutoverClosureMapperContractTest`共5/5通过；独立MySQL 8.4空卷从V1全量迁移至V155，合法DRAFT、终态采集证据、SUCCESS/FAILED归档及P6阶段历史均写入成功；异常结果无说明和回退无原因均由CHECK拒绝且原行不变。
- Task 2物理可执行纠偏：五个最长4000字符的闭环文本字段改用`TEXT + CHAR_LENGTH<=4000`，避免utf8mb4行大小超限，不改变API长度语义。
- Task 2最小整改候选：外部任务、回调、PLT引用及结果身份均使用显式大小写敏感存储/比较，保留Owner原值；`file_hash`统一为`VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin`并以case-sensitive正则约束小写SHA-256。
- Task 2最小整改MySQL证据：空卷再次全量迁移至V155；信息架构确认全部不透明身份使用`utf8mb4_bin`、哈希为`VARCHAR(64)/ascii_bin`；`CaseTask`与`casetask`可作为两个Owner任务并存，大小写错误的SUCCESS resultRef及大写哈希均由CHECK拒绝且原值不变。
- Task 3候选已形成CUT自有创建、CAS保存、详情与附件事实正常链：首次保存冻结任务/批准方案/设备水位，后续保存拒绝刷新冻结来源；ProjectScope与PLT仅经消费端口调用，测试使用`src/test`受控替身，未注册生产Service、Fake或fallback。
- Task 3候选聚焦证据：Application/Query/Port/Mapper共10/10通过；独立MySQL 8.4空卷全量迁移至V155后，首次创建、同键重放、版本化保存、附件替换及平台幂等/审计同事务1/1通过。候选仍等待独立Application/MySQL复审，不提前回写PASS。
- Task 3首轮独立复审对`6d6f0e46`裁决`NO-GO`：DRAFT最终结果、人工采集附件保留、详情冻结来源/ProjectScope错误映射三项需最小整改；Task 3保持`REVIEW_REQUIRED`。
- Task 3整改候选已按上游到下游收敛：DRAFT保存固定`final_result_code=NULL`且提交命令显式携带最终结果；普通保存仅替换普通附件并保留`MANUAL_COLLECTION_RESULT`；详情逐项验证冻结任务/审批/方案/设备水位和ProjectScope身份，并区分Provider不可用与Owner事实损坏。
- Task 3整改证据：两份机器JSON可解析；Application/Query/Port/Mapper 10/10通过；独立MySQL 8.4空卷全量迁移至V155后，合法NULL结果DRAFT、同键重放、CAS保存、人工采集附件保留及平台幂等/审计同事务1/1通过。整改仍等待独立复审，不提前回写PASS。
- Task 3 A/B/C最小整改复审对`4d7e4235`确认A、B及冻结来源核对已关闭，仅剩详情误捕获闭环文件端口异常类型；单点候选已改为捕获ProjectScope正式`CutoverOwnerFactException`，将`PROVIDER_UNAVAILABLE`稳定映射为Owner Provider不可用、数据范围拒绝保持不可见，其余事实异常失败关闭为Owner数据损坏。
- Task 3最终单点复审已对`a99ddf5b`裁决`GO`，ProjectScope正式异常映射阻断关闭；Task 3创建、保存、详情与文件事实正常链Gate现为`PASS / GO@a99ddf5b`。
- Task 4候选已形成CUT自有采集请求、回调与人工结果替代正向链：DRAFT单设备请求追加下发事实，成功回调追加终态事实；下发/回调失败可锁定PLT文件事实追加人工结果，原失败证据保持不可变。INT-12仅经消费端口调用，受控实现位于`src/test`，未注册生产Bean、Fake或fallback。
- Task 4恢复链已按同一`CollectionIntentIdentity`先查询外部任务：CUT本地事务失败后，重试复用同一`collectionTaskId`补齐本地投影，不创建第二外部任务；请求摘要不包含`transientSecret`。
- Task 4首轮独立复审对`55dc8e49`裁决`NO-GO`：人工结果命令缺少`deviceId/collectionStage`联合身份，callback/manual业务摘要误含`correlationId/Idempotency-Key`；Task 4保持`REVIEW_REQUIRED`。
- Task 4最小整改候选已补齐人工结果的`collectionTaskId + deviceId + collectionStage + failure type`精确匹配；callback/manual改用封闭业务摘要，明确排除关联标识与平台幂等键，人工摘要包含PLT文件冻结事实。
- Task 4 A/B最小整改已在`c6fe303e`通过独立复审；人工结果联合身份和callback/manual封闭业务摘要两项阻断均已关闭。Task 4采集请求、同意图恢复、回调与人工结果替代正向闭环Gate现为`PASS / GO@c6fe303e`。
- Task 4整改证据：Application与Mapper合同4/4通过；隔离MySQL 8.4空库全量迁移至V155后，dispatch/callback/manual、同关联标识变化重放及外部任务恢复链3/3通过，临时数据库已删除。
- Task 5候选已形成SUCCESS/FAILED提交正常链：写前锁定P6任务、DRAFT闭环、批准方案/审批、ProjectScope、两类必需PLT附件、全部采集终态及活动设备；同一平台NEW事务提交闭环、归档任务、释放设备、追加P6历史和幂等审计。仅SUCCESS形成`CUTOVER_CLOSURE:{closureId}:{submittedClosureVersion}`并发布一个`CutoverCompleted`，FAILED不发布完成事件。
- Task 5跨模块事实仍只经CUT消费端口调用；聚焦测试显式组装受控ProjectScope/PLT替身，未注册生产Fake、fallback或其他Owner实现。
- Task 5候选证据：Application与Mapper合同4/4通过；隔离MySQL 8.4空库执行151个迁移至V155后，既有采集链及SUCCESS/FAILED提交共5/5通过，验证真实`PlatformCommandExecutionApiImpl`下归档、设备释放、P6历史、幂等审计和Outbox原子提交，临时数据库已删除。候选保持`REVIEW_REQUIRED`，不提前回写PASS。
- Task 5首轮独立复审对`3a790c41`裁决`NO-GO`：`CutoverCompleted`缺少物理机器合同规定的`tenantId/closureId/finalResult/correlationId`。单点整改候选已补齐九个精确载荷字段，并在既有SUCCESS正向MySQL流程断言字段集合和值；Task 5仍保持`REVIEW_REQUIRED`。
- Task 5单点整改已在`39083446`通过独立复审；`CutoverCompleted`九字段精确载荷阻断关闭。Task 5 SUCCESS/FAILED提交、归档、设备释放、P6历史、平台幂等审计与完成事件Gate现为`PASS / GO@39083446`。
- Task 6首轮独立复审对`df1b7830`裁决`NO-GO`：应用异常将Owner、来源陈旧、状态及业务不完整原因有损折叠，且候选含负向测试。整改已由实际守卫携带封闭reasonCode、ownerContext和当前版本，Controller仅结构化投影；负向测试及Gate证据已移除，五路由、Codec与既有闭环正向聚焦验证5/5通过。
- Task 6最小整改已在`97ee0904`通过独立复审；错误身份无损投影与正向验证边界阻断均已关闭。生产Controller/Service仍未注册，Task 6 REST Contract/Code Review Gate现为`PASS / GO@97ee0904`。
- Task 7已形成CUT受控legacy前向分类：生产代码只领取PLT `STAGED_READY`冻结批次，结构合法旧步骤归为`RETAINED`，冻结来源载荷损坏归为`FCUT006_SOURCE_RECORD_INVALID`，不读取旧表且不创建CUT闭环；V156仅将`legacyCutoverClosureReconciliationJob`登记为`status=2/PAUSED`。
- Task 7聚焦证据：Classifier与迁移合同5/5通过；隔离MySQL 8.4空库执行152个迁移至V156后，正式暂存来源正常分类与完成链1/1通过，批次`mapped=0/issue=0/retained=1`且CUT闭环零写；临时容器、网络和卷均已清理。独立Migration/MySQL Gate现为`PASS / GO@739aad09`。
- 最近Gate：Task 8 P6工作台与组件交互的前端组件交互Gate。

## 状态边界

- `Q-FCUT004-001`的P6职责变化回P4分支保持`BLOCKED_BY_SPEC`，不进入正常P6闭环。
- 生产INT-12与下游项目/资产消费者缺失阻断真实浏览器和Implementation Done，不阻断Ready后CUT自有内核及受控替身实现。
- 旧`pms_cut_execution/pms_cut_observation`和旧页面保持不变。
