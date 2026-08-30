# F-ACC-002 满意度问卷、达标判定与归档同步 Feature Spec

> 文档状态：`CANDIDATE`
> Feature Ready：`NOT_READY`
> 实施状态：`NOT_STARTED`
> Requirement切片覆盖：`ACC-02@V1=FULL；ACC-04@V1=PARTIAL`
> Coverage Profile：`ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`
> Owner Context：`ACC（验收与闭环）`
> 目标实现载体：`pms-module-project-api/pms-module-project`；PLT公共文件契约仅作PMS加性扩展
> 适用基线：PRD V1.8；ADR-0041 `ACCEPTED`；F-ACC-002 SDS Phase 2/P3-E09 `READY / GO`（整改提交`b98d0caa`）

## 1. 业务目标

本Feature形成“冻结业务时点→领域任务与冻结问卷→V1二维码/受控链接/现场协助→客户答卷、签字和附件→不可变评分判定→未达标整改重收→有效达标来源归档与历史下载”的完整闭环。

## 2. Scope

### 2.1 包含

- ACC问卷模板草稿/发布修订、满意度领域任务、冻结问卷、受控访问授权、答卷、签字/附件、不可变Result和RemediationFact；
- PROJ在项目创建时冻结模板Fact，在配置业务时点以`MANDATORY`初始化首个满意度任务；
- V1手工受控链接、二维码和正式登录身份现场协助；
- 评分、达标判定、失效、整改重收及同链revision历史；
- 精确`T-SAT-SURVEY→D-SAT-REPORT`满意度来源索引、归档补偿和历史下载；
- 查询、指派、收集、下载和授权字段导出；最小权限、项目/责任人/字段/文件/租户控制；
- `SatisfactionTaskCreated`、`SatisfactionResultVersionChanged`及专用Outbox投递；
- 前向Schema、权限/菜单、受管验收数据及真实浏览器纵向闭环。

### 2.2 覆盖边界

- `ACC-02@V1=FULL`：任务、问卷、客户提交、签字附件、判定、整改重收、导出和结果Fact全部纳入验收。
- `ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`：只实现满意度Result来源版本、文件集合、归档/失效/补偿和历史单文件下载。

### 2.3 Out of Scope

- ACC-04其他五类来源、统一批量下载、人工归档审核及完整归档管理Feature；
- CLO-01、CLO-02、SUB-03消费者实现或付款/闭环门禁结论；
- INT-10短信/邮件和INT-05钉钉连接器、认证、调度、回调或送达实现；V2只保留接口边界；
- 从旧问卷、回访、转包回调或缓存分数迁出客户答案、签字、评分、通过结果；
- 修改Yudao基础平台、删除鉴权/租户隔离或固定角色—权限映射。

## 3. 业务规则

### BR-FACC002-001 模板冻结与首次触发

- ACC以`SatisfactionQuestionnaireTemplateApi.resolvePublished`按项目类别、签约方式、实施方式、业务用途、适用时点唯一返回发布修订Fact；零匹配或最高优先级并列失败。
- 模板由具`manage`权限的ACC管理入口创建草稿修订并发布；已发布修订不可更新/删除，新规则只能追加修订。不得以Flyway受管种子替代正式模板配置正向路径。
- PROJ在项目创建事务内把模板/修订/规则/阈值Fact冻结到`satisfaction_timing` ProjectTask，不按任务名、任务码或默认模板推断。
- V1正向业务时点至少由已交付ACC初验活动完成Fact触发：ACC完成初验活动的同一事务按冻结`AFTER_INITIAL_ACCEPTANCE`任务调用initializer；其他模板时点只在其Owner交付后调用同一接口，不由ACC/PROJ猜测。ACC经`ProjectWorkBindingFactApi`重验同租户项目、ProjectTask、冻结Fact、时点和当前责任人，并以`MANDATORY`加入调用方事务；未显式指派时责任人为当前项目经理。
- 首次任务由ACC分配稳定`collectionKey`和`taskRevisionNo=1`；source和trigger都冻结原始业务时点Fact。同Fact同载荷返回原任务，异载荷冲突。

### BR-FACC002-002 受控访问与客户提交

- ACTIVE问卷可生成手工受控链接，二维码只是同一链接的表示；令牌仅创建时返回一次，库内只保存摘要、版本、有效期和状态。
- 客户grant只能读取唯一问卷、上传该预分配Response的签字/附件并以`questionnaireId+requestId`提交一次；过期、撤销、已消费、错问卷或错租户统一拒绝且不泄露对象。
- 外部上传仅通过PLT加性`FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload`；ACC先验证grant，PLT仍执行大小、类型、扫描、版本和审计。现场协助使用正式登录用户和`collect`权限。
- Response、答案、签字和附件只追加；同requestId同载荷返回首次结果，同键异载荷冲突。Todo完成或通道送达不能替代客户提交。

### BR-FACC002-003 判定、当前结果与整改重收

- 仅必答完整、客户答案和签字有效且评分达到冻结阈值时产生`EFFECTIVE + passed=true`；失败判定同样不可变并保存阻断原因。
- 同`collectionKey`最多一个未关闭有效达标Result；失效关闭区间但不删除、不恢复旧Result，禁止人工改分。
- `recollect`只接受当前链前一失败/失效Result、`remediationRequestId`和整改证据。ACC先追加不可变`SatisfactionRemediationFact`，再原子创建相同collectionKey、revision+1的新Task和Questionnaire。
- 新revision复制首任务source；trigger固定为`ACC/SatisfactionRemediationFact`。同整改Fact同载荷返回原revision，异载荷冲突；客户端不得覆盖collectionKey、revision、source或trigger。

### BR-FACC002-004 文件、结果文档与归档

- Response文件策略键固定为`ACC/SATISFACTION_RESPONSE/{responseId}/SATISFACTION_SIGNATURE|SATISFACTION_ATTACHMENT`；Result使用持续ACTIVE的`SATISFACTION_RESULT_DOCUMENT`和独立ARCHIVED的`SATISFACTION_ARCHIVE`。
- ACC只保存PLT公共`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/fileHash`，不得保存PLT内部FileVersion/FileReference主键。
- 有效达标Result发布`SatisfactionResultVersionChanged`。投影先由PROJ重验同租户同项目ProjectTask的稳定码为`T-SAT-SURVEY`，再锁定唯一`acc_project_deliverable(tenant,project,D-SAT-REPORT)`且要求根`task_code=T-SAT-SURVEY`。
- 根缺失、重复或身份不一致保持`PENDING_COMPENSATION`；禁止按中文名称、其他交付件或任选根推断。归档失败不回滚Result、不破坏ACTIVE历史下载；成功才记`ARCHIVED`。
- 归档actor冻结为Result形成时当前责任人；PLT以该用户重验`pms:file:archive`、FileBusinessScope和租户，不借用Job用户或伪造Web身份。

### BR-FACC002-005 结果Fact、导出与消费者边界

- `SatisfactionResultFactApi.inspect/lockAndRevalidate`返回稳定task/questionnaire/response/result、模板/规则/阈值、原始source、各版本、passed/resultStatus/archiveStatus；CLO/SUB只能读取，不能修改或推断。
- 仅有效达标Result可成为当前满意度交付件；失败/失效保留历史但不得形成CLO/SUB通过事实。
- 导出按项目树、责任人、字段、文件和租户范围裁剪，保存条件、范围摘要、文件哈希、下载人和时间；没有字段或文件权限时不得带出对应内容。

### BR-FACC002-006 旧载体与前向边界

- `pm_cl_quesnaire_*`和`pm_cl_callback*`、`pm_subcontract_project_callback`及其他回访/维保缓存事实均`PRESERVE_RAW`，等待`AI-MIG-000`字段和值域确认。
- F-ACC-002只从新平台明确命令创建当前事实；不得从旧审批/回访状态、问卷名称、缓存分数、URL或转包状态推断业务时点、答案、签字、整改或通过。
- 直接复用PROJ任务/WorkBinding、V55稳定定义、F-ACC-001应交来源与归档载体、PLT文件和平台Outbox；不建立第二任务、应交根、文件或归档真值。

## 4. API、权限与事务

所有REST使用`/api/v1/pms`前缀；租户和操作者只取服务端认证上下文。

| 接口 | 权限/身份 | 契约 |
|---|---|---|
| `GET /satisfaction-questionnaire-templates` | `pms:acceptance:satisfaction:query` | 按租户查看模板根、草稿和已发布修订 |
| `POST /satisfaction-questionnaire-templates`、`POST .../{id}/revisions` | `pms:acceptance:satisfaction:manage` | 创建模板/草稿修订；题目、必答、分值、阈值和五维适用条件完整 |
| `POST /satisfaction-questionnaire-templates/{id}/revisions/{revisionId}/actions/publish` | `pms:acceptance:satisfaction:manage` | 发布不可变修订；歧义适用范围拒绝 |
| `GET /satisfaction-tasks`、`GET /satisfaction-tasks/{id}` | `pms:acceptance:satisfaction:query` | 项目树、责任人和字段范围裁剪；空范围为空 |
| `POST /satisfaction-tasks/{id}/actions/assign` | `pms:acceptance:satisfaction:manage` | 只指派当前项目获授权成员 |
| `POST /satisfaction-tasks/{id}/actions/recollect` | `pms:acceptance:satisfaction:manage` | 前一失败/失效Result+整改Fact；新revision且旧链不变 |
| `POST /satisfaction-tasks/{id}/access-grants` | `pms:acceptance:satisfaction:manage` | 创建一次性受控链接；完整令牌只返回一次 |
| `GET /satisfaction-questionnaires/{token}`、`POST .../{token}/files`、`POST .../{token}/responses` | 有效客户grant | 仅唯一问卷、预分配Response和一次提交 |
| `POST /satisfaction-tasks/{id}/assisted-responses` | `pms:acceptance:satisfaction:collect` | 正式项目成员现场协助，记录协助人与客户联系人 |
| `GET /satisfaction-results`、`GET .../{id}` | `pms:acceptance:satisfaction:query` | 当前与历史Result，敏感答案按字段权限裁剪 |
| `GET .../{resultId}/files/{sequence}/download` | `pms:acceptance:satisfaction:download` | 每次重验项目、FileBusinessScope和租户 |
| `POST /satisfaction-results/exports` | `pms:acceptance:satisfaction:export` | 授权记录/字段/文件的异步导出及审计 |

角色映射保持正式配置；验收身份可配置全部相关键，不能删除服务端鉴权或租户隔离。

固定锁序：首次触发`PROJ ProjectTask/WorkBinding→ACC Task→Questionnaire`；提交`AccessGrant→Task→Questionnaire→PLT引用→Response→Result→Outbox`；整改`Task链→前一Result→RemediationFact→新Task→Questionnaire→Outbox`；归档`应交根→来源版本→PLT ACTIVE/ARCHIVE集合→归档投影`。

## 5. 状态、事件与异常

| 对象 | 状态 |
|---|---|
| SatisfactionTask | `PENDING_ASSIGNMENT/PENDING_COLLECTION/PENDING_DECISION/FAILED/PASSED/PENDING_ARCHIVE/ARCHIVED` |
| Questionnaire | `ACTIVE/SUBMITTED/INVALIDATED/EXPIRED` |
| Result | `EFFECTIVE/INVALIDATED`，passed独立保存 |
| AccessGrant | `ACTIVE/CONSUMED/REVOKED/EXPIRED` |
| Archive | `PENDING_COMPENSATION/ARCHIVED/INVALID` |

- `SatisfactionTaskCreated`携带collection/revision/prior、原始source、当前trigger、问卷与规则版本；不表示提交或通过。
- `SatisfactionResultVersionChanged`携带`RECORDED/INVALIDATED`、项目/任务码、完整版本链、判定及有序文件公共事实；与Result事务同写Outbox。
- `SatisfactionResultOutboxDeliveryJob`只领取该事件；投影提交后才markDelivered，失败按同retryCount重试；CLO/SUB事件不由本Job误标成功。
- 业务门禁、版本冲突、依赖不可用、幂等冲突均使用稳定分类；所有拒绝路径保持对应任务/问卷/答卷/Result/来源/Outbox零新增。

## 6. 数据与迁移

精确物理字段、唯一键、Owner及前向边界以`F-ACC-002-physical-contract.json`和已批准P3-E09差量为准。Technical Plan只能新增前向Flyway；不得改写旧迁移或跨Context建立物理外键。旧源只保留原始证据，不进入本Feature转换批次。

## 7. 验收标准

- AC-01：正式模板入口可创建并发布不可变修订；项目创建冻结唯一发布修订，零/多匹配整批失败，既有项目事实不变。
- AC-02：已交付初验活动完成Fact按冻结时点首次触发revision1，未指派时使用当前项目经理；同Fact重放不重复，错项目/版本/责任人零写入。
- AC-03：受控链接/二维码只能访问唯一ACTIVE问卷；令牌过期、撤销、消费或跨租户拒绝且不泄露。
- AC-04：必答、签字和阈值满足时形成不可变达标Result；任一不满足形成失败判定且不能用于闭环/付款。
- AC-05：首次失败后以整改Fact创建同collectionKey的revision2和新Questionnaire/Result；旧事实不变，同整改Fact重放幂等。
- AC-06：有效达标Result只进入同项目`D-SAT-REPORT/T-SAT-SURVEY`根；根缺失/错配待补偿，归档重试不回滚Result。
- AC-07：Result失效清空当前来源且不恢复旧版；来源历史、归档结果和ACTIVE文件下载保持可追溯。
- AC-08：查询、导出、下载按项目/责任人/字段/文件/租户范围执行；跨范围拒绝且不泄露。
- AC-09：旧问卷/回访/转包载体保持不变，不能产生当前Result；INT通道不可用时V1路径仍可完成。
- AC-10：真实MySQL验证唯一键、追加历史、原子回滚；真实Chromium完成指派→受控问卷→失败→整改重收→达标→归档/下载闭环。

## 8. Feature Ready检查

| 检查项 | 当前结论 |
|---|---|
| Requirement覆盖与纵向闭环 | CANDIDATE |
| Owner/API/权限/事务与锁序 | CANDIDATE |
| 状态、物理差量和迁移边界 | CANDIDATE |
| 旧实现复用审计 | COMPLETE（见独立审计文件） |
| Open Question | 无当前正向闭环阻断；AI-MIG-000仅阻断旧源迁移 |
| 独立Feature Ready裁决 | PENDING |

检查点：基线=`38901259`；当前Gate=Result失效SDS补充；已通过=Feature Ready其余边界已确认；阻塞=失效命令上游契约待独立复审；下一步=上游GO后返回同一Feature Ready整改，不生成Technical Plan或代码。
