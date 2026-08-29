# SDS Phase 2 Review

> 审查状态：`APPROVED`<br>
> 依据：PRD V1.8修订007正式基线、SDS Phase 1 V1.8正式基线、ADR-0029/ADR-0030、需求方推进批准<br>
> 结论：`READY_FOR_PHASE_3_V1.8`<br>
> 机器门禁：`PASS`<br>
> 需求方批准：`GO`<br>
> 适用修订：`PRD_V1.8_REVISION_007`<br>
> 当前范围：主版本V1 53项、V2 47项、正式Requirement 100项；111个目标版本切片（V1 53个、V2 58个）；已编号V3 31项、跨需求演进方向5项；`OUT_OF_SCOPE` 9项

## 1. 当前结论

修订007新增11个补充V2切片并明确配置基础能力边界。08～16分册及显式契约图已按111个目标版本切片完成差量复核，当前只放行进入Phase 3设计评审。

## 2. 必须重验证的契约

| 范围 | 修订007前状态 | 修订007关闭条件 |
|---|---|---|
| 修订007差量 | PASS | 100项Requirement共享实施契约与111个切片业务结果精确同源；数据、API、事件、集成、权限、异常和迁移边界已复核 |
| 数据与数据库 | PASS | 已清理ACC-05、COM-02、IMP-02活动对象/迁移目标；已校准项目状态、闭环字段及新增六表物理设计 |
| API与命令 | PASS | 100项正式需求已逐项落位；已移除退出需求API并补齐执行契约、事实版本和幂等边界 |
| 事件与集成 | PASS | 已校准ERP/CRM权威事实、质量事件、非阻断依赖及CUT结果引用语义 |
| 文件、缓存、并发、异常 | PASS | 已同步V1.8文件、缓存、并发和异常边界，不机械继承V1.7结论 |
| 迁移设计 | PASS | 当前契约为87对象/98来源绑定/1排除源；新增的`CustomerServiceLevelRevision`和`CutoverConfigurationRevision`均为NONE_NEW/FEATURE_FORWARD_MIGRATION，不进入当前核心DDL，不从联系人、关系快照、旧方案或风险项反推历史业务事实 |
| F-PROJ-004聚焦迁移增量 | READY / GO | 当前契约为88对象/99来源绑定/1排除源；`ProjectTemplateMatchHistory`为NONE_NEW/FEATURE_FORWARD_MIGRATION(PM-07)，已进入受管生成链；Feature Ready独立裁决`NPDMS-FPROJ004-FEATURE-READY-20260825-06`为GO |
| F-PLT-002聚焦动态表单候选 | IN_REVIEW | 候选形成时为90对象/101来源绑定/1排除源，修订007当前总体为93对象/104来源绑定/1排除源；PLT模板、修订、通用实例与Preparation专用实例分离，旧`pms_eng_form_*`仅作复用审计证据且不迁移、不双写；不代表Feature Ready通过 |
| F-CUS-001规格回写 | READY | 当前契约为93对象/104来源绑定/1排除源；客户主档、地点引用、五维权限切片及V106～V108实现证据已进入受管生成链，Feature前向表不冒充当前核心DDL |
| F-COM-001修订008聚焦差量 | READY / GO | 当前受管生成契约为94对象/107来源绑定/1排除源；办事处发生时快照、单位精度、来源版本、范围历史、ACC Owner守卫及V70的10项必填目标映射（含明细序号唯一键规则）/逐项缺失负向门禁已由整改提交`20f03ba316ca431a55f96aa9c3c97be54d08b4e0`通过独立复审；只放行Feature Spec/机器物理契约整改，Q-FCOM-001仍BLOCKED_BY_SPEC，Feature继续CANDIDATE / NOT_READY |
| F-COM-001修订009状态SDS差量 | READY / GO | ADR-0037、项目阶段快照驱动的ACC范围绑定、报告解耦、进入/新范围两条原子路径、PROJ→COM→ACC统一锁序及聚焦物理差量已由提交`b17ae89f92b01488378aeb8c36a77a5b2d46ad29`通过独立复审；只放行同一Feature的Feature Spec与机器物理契约整改，Q-FCOM-002仍只阻断退出/回退关闭或解锁，Feature继续CANDIDATE / NOT_READY |
| F-COM-001修订009合同授权SDS差量 | READY / GO | ADR-0038已由独立裁决批准为`ACCEPTED`：直接复用SYSTEM现有`OrganizationScopeApi.getActiveScopes`，按当前有效`UserCompanyDepartmentScope.companyCode`形成合同管理员公司范围；空/不可用失败关闭、写前重验并审计scope ID/version、敏感字段独立权限、不缓存正向授权；P3-E09为`NO_PHYSICAL_DELTA`。裁决对象`2cf427d6ccb6e0cef0cef3b1460eeaa95ddced53`；本GO不放行Feature Ready |
| 追溯 | PASS | `phase2-contract-map.md`保留100个稳定Requirement锚点并显式登记111个切片键；迁移对象和目标表映射精确同步 |
| 工作绑定、P3采集结果与CUS-02/CUT-07承载 | PASS | ADR-0030六表已由P3-E09纳入当前冻结模型；ADR-0031仅批准两个逻辑对象及Feature前向表名，实际物理表须由对应Feature以前向迁移审批创建 |
| F-PROJ-003 PM-04角色与项目子树授权精化 | PASS | ADR-0034已接受，角色与范围分离、PLT授权事实、PROJ范围计算、API、物理字段及幂等边界已落位；属于Feature级差量精化，不重新打开Phase 2总体门禁 |

## 3. 不变的后置边界

- P3-E09模型基线与Q08候选索引不因本次PRD发布自动批准或自动否定；`AI-MIG-000`按具体Release范围判断，未包含历史迁移或数据切换时为`NOT_APPLICABLE`。
- 历史工单/工时仍无V1/V2用户入口；V3和`OUT_OF_SCOPE`不得回流。
- 环境参数、生产拓扑、KMS、SIT/UAT和真实迁移/切换证据继续在各自最晚安全门禁关闭。
- 本次新增逻辑事实影响当前物理模型时，P3-E09必须复核差量DDL；在此之前旧DDL只作历史模型证据，不能放行相关Feature实现。
- 修订009合同授权差量复用现有SYSTEM、COM和AuditRecord字段，已判定`NO_PHYSICAL_DELTA`；该结论不批准产品代码、权限种子或Flyway。

当前Phase 2结论为`APPROVED / READY_FOR_PHASE_3_V1.8`，批准修订007进入Phase 3设计评审。

本结论不批准DDL执行、Feature实现或完成、历史迁移、数据切换、SIT/UAT或Release。
