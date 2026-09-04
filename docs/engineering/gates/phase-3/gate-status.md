# SDS Phase 3 Review

> 审查状态：`REVALIDATION_REQUIRED`<br>
> 当前依据：PRD V1.8修订001—012、014—015正式基线；Phase 1/2均待差量复核<br>
> 上次批准：PRD V1.8修订007，`APPROVED / READY_FOR_SDS_BASELINE_V1.8`（历史证据）<br>
> 当前结论：`BLOCKED_BY_PRD_DELTA`<br>
> 机器门禁：`NOT_RUN_FOR_REVISION_015`<br>
> 适用修订：`PRD_V1.8_REVISION_015`

## 1. 状态说明

修订007的安全、审计、部署、性能和测试设计仍可作为历史输入，但不能证明修订015新增和改变的业务分支已经具备完整验证。Phase 3当前重开的是差量测试与运行保障范围，不将尚未变化的NFR设计无条件推翻。

## 2. 必须重验证的Phase 3场景

| 场景 | 当前状态 | 最小验证要求 |
|---|---|---|
| 完整工程主链 | REVALIDATION_REQUIRED | S0→S1→S2→S3→S4→S5→S6参考链及S4内嵌CUT P1～P6均有正常、驳回和失效场景 |
| 模板裁剪与阶段推进 | REVALIDATION_REQUIRED | 只实例化模板阶段；当前准出、唯一后置和目标准入原子执行；无`APPLICABLE/NOT_APPLICABLE`假实例 |
| S5验收 | REVALIDATION_REQUIRED | 直签初验后终验、非直签直接终验；模板无S5时不生成验收门禁 |
| 三类项目退出 | REVALIDATION_REQUIRED | 有/无S6的NORMAL、代理商自服NO_TRACKING、PM-10 EXCEPTION；唯一Writer、`closed_from_stage`和不得补造事实 |
| PM-06范围追加 | REVALIDATION_REQUIRED | S0～S5不同阶段追加、重复/超量拒绝、事务回滚、旧事实不覆盖新增范围、阶段和终验失效重算、终态项目拒绝 |
| RPT-02全集统计 | REVALIDATION_REQUIRED | 进行中全状态、真实阶段、超期、三类终态、正常/业务闭环率、父子粒度、权限下钻、Excel导出和异常快照 |
| S4与割接 | REVALIDATION_REQUIRED | 五类97项配置基准、V1/V2人工判级、D级跳过P3、CUT成功范围聚合及部分覆盖不放行 |
| BPM身份与历史 | REVALIDATION_REQUIRED | definition key解析、实际定义和任务key留痕、历史定义选择、撤回/驳回/重提、通知失败不改变审批事实 |
| 版本隔离 | REVALIDATION_REQUIRED | V2/V3能力不成为V1前置；旧E2E不能用后续能力伪造当前版本通过 |

## 3. 关闭动作

1. Phase 1/2差量设计先形成可执行基线；
2. 更新正式测试设计及Requirement—场景—证据映射；
3. 为第13.1章8个关键子流程和RPT-02五类能力建立完整断言，不能只保留闭环摘要；
4. 验证状态机、权限、幂等、并发、异常补偿、历史兼容和审计；
5. 明确P3-E09及部署/迁移是否受影响，并在对应最晚安全门禁关闭；
6. 运行适用Phase 3校验并把证据写入本文件后，才可恢复`READY_FOR_SDS_BASELINE_V1.8`。

## 4. 当前放行边界

当前不批准受影响范围以修订007测试结果进入SDS Baseline、Feature Done、SIT、UAT或Release。P3-E01～P3-E09、`AI-MIG-000`和生产发布仍按实际变更条件独立适用，不因本次重开自动通过或自动失败。
