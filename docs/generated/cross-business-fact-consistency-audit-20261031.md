# 跨业务类型的任务结果一致性排查

时间：2026-10-31。范围：当前测试库178:13306/npdms、租户1。用户要求排查其他业务类型，非继续列举安装项目。

## 结论

施工计划确认存在与安装相近的“新有效业务结果已产生，DONE任务仍关联旧结果”情况：项目79557。另有83855、83214的当前工期基线与旧批准批次不一致，但两者尚处PENDING_RECALCULATION，没有相应的新批准完成事实，不应当作“实际已完成却误报”直接放行。

## 核查范围

只读事务核查9类、314条有效任务关联，按对象身份、租户、项目、业务状态、当前结果/版本指针及冻结任务完成条件比对。未调用批量校验、未生成新快照、未改数据、未重启服务。

| 业务 | 有效关联数 | 已完成任务关联数 | 本次结论 |
|---|---:|---:|---|
| 施工计划 | 46 | 46 | 1个新批准批次未接续旧任务关联；2个工期变更未完成重算/批准 |
| 实施方案 | 51 | 43 | 已完成任务关联的批准状态、批准人/时间、基线与版本一致；未发现本次同形问题 |
| 需求分析 | 53 | 49 | 已完成任务关联均冻结且仍有效，冻结人/时间存在 |
| 现场工勘 | 63 | 59 | 已完成任务关联均为确认或归档状态 |
| 到货签收 | 20 | 18 | 已完成任务关联均为已确认状态 |
| 现场培训 | 15 | 12 | 已完成任务关联均为客户已确认状态 |
| 满意度调查 | 14 | 12 | 当前结果指针存在，结果有效、通过、已归档，无失效时间 |
| 验收 | 17 | 16 | 当前报告指针存在、归属一致、报告有效且未过期 |
| 割接 | 35 | 19 | 对照CUTOVER_APPROVED/ANY后，无已完成任务因全部关联审批失效而失去依据 |

“未发现”仅指本轮关联身份/版本/状态检查，不代表完整审批附件、签章、文件服务及全部服务端事实重验通过；未逐项目运行浏览器或生成业务快照。

## 确认的同类情况：施工计划79557

- 项目编码：16323225112706N-0，生命周期ACTIVE。
- 已完成施工计划任务：2114155466992295941。
- 当前任务关联旧批准批次13，工期修订2114259677693779970；旧批次2026-10-25 15:55:30生效。
- 新批准批次14于同日16:15:21生效，工期修订2114267214182785026；施工计划根当前指针已指向该修订，重算状态RECALCULATED。
- 冻结任务要求CONSTRUCTION_PLAN_APPROVED/ALL及施工计划交付件。
- `StagePlanApprovalCompletionProvider.load`读取旧批次时因工期修订不同得到未完成；锁定时按current选中14又因请求身份13不一致而拒绝。`ProjectTaskBusinessAssociationService.synchronize`保留DONE任务旧关联。
- 因而这不是单纯字段版本号变化，而是新有效业务结果与旧任务完成依据的衔接问题。尚未执行该项目浏览器闭环校验，不能宣称已复现S6报错。
- 裁决建议：需修复正常业务结果演进后的核验衔接，明确校验当前批准批次及其交付件，同时保留旧批准历史、原任务完成证据；不能直接改关联ID、把旧批次改成当前、取消工期一致性检查或把新草稿当有效。

## 两项应继续办理业务变更，不应强行放行

| 项目 | 编码 | 当前情况 |
|---|---|---|
| 83855 | 16200826062501N-0 | 任务关联批准批次36；当前工期修订已换为2114923338930233346，新批次37仍为草稿；计划PENDING_RECALCULATION |
| 83214 | 16201426020402N-0 | 任务关联批准批次49；当前工期修订换为2116110803015737346，尚无对应新批准批次；计划PENDING_RECALCULATION |

此处数据差异真实存在，但尚不能认定代码误判；应通过正式计划重算/审批流程处理，禁止为了闭环降低校验。

## 排除的割接误报

初筛发现9条DONE任务关联的割接未成功归档。进一步读取实际冻结契约，全部要求的是CUTOVER_APPROVED，量词ANY，并非CUTOVER_COMPLETED。

19条关联中18条当前方案具有未被替代的批准实例；剩余1条属于84001，该同一任务还关联另外2条有效批准割接，符合ANY。故不能将这些数据作为“任务虚假完成”或相同缺陷报告。当前检查未包含审批实例所有来源摘要、附件及锁定校验。

## 证据与复现

- `.run/audit-other-business-facts.py`：读取9类关联、业务主记录及当前结果。
- `.run/other-business-facts-raw.json`：本轮只读数据。
- `.run/analyze-other-business-facts.py`、`.run/other-business-facts-analysis.json`：初筛，割接的归档标记仅是候选，不是最终异常结论。
- `.run/audit-other-contracts.py`、`.run/other-business-contracts.json`：实际冻结条件、割接方案/批准实例复核；本报告以该复核裁决为准。
- 代码来源：当前工作树`StagePlanApprovalCompletionProvider`、`StagePlanCompletionMapper.xml`、`SolutionApprovalCompletionProvider`、`RequirementAnalysisEntityBusinessObjectProvider`、`SiteSurveyTaskBusinessObjectProvider`、`ArrivalTaskBusinessObjectProvider`、`TrainingTaskBusinessObjectProvider`、`SatisfactionTaskBusinessObjectProvider`、`AcceptanceTaskBusinessObjectProvider`、`CutoverApprovalTaskBusinessObjectProvider`。

本轮只检测和登记，未修改上述业务代码、数据库或历史记录。
