# DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE COM需求能力整体合并

> DU状态：`CLAIMED`
> DU类型：`FEATURE`
> Feature协调：`F-COM-001=FEATURE_EXCLUSIVE`
> Task范围：`统一COM-01闭环并完成PLT迁移证据与IMP/AST实施地点的边界拆分；未形成独立Requirement前不实施拆出能力，不接收ACC-001/002业务实现`
> Owner：`Codex本次master需求收敛与选择性集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`a11f95e28913f324c36cc2d1db8cef5e75ab0313`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/**;docs/decisions/open-questions.md;docs/design/**;docs/reports/2026-09-02-PRD-V1.8修订010基线变更报告.md;docs/superpowers/plans/2026-09-02-f-com-001-requirement-convergence.md;docs/traceability/**;specs/001-project-delivery-platform/domains/COM-合同订单履约需求规格.md;specs/features/F-COM-001*;specs/features/F-PLT-003*;specs/features/F-IMP-003*;specs/features/README.md;tasks/features/F-COM-001.md;tasks/features/F-PLT-003.md;tasks/features/F-IMP-003.md;tasks/features/README.md;tasks/delivery-units/DU-20260901-COM-ACC-CANDIDATE.md;tasks/delivery-units/DU-20260902-FCOM001-COMB-INTEGRATION.md;tasks/delivery-units/DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE.md;tasks/delivery-units/README.md;pms-module-commerce-api/**;pms-module-commerce/**;pms-module-project/pms-module-project-api/**;pms-module-project/pms-module-project/**;pms-module-acceptance/pms-module-acceptance-api/**;pms-module-acceptance/pms-module-acceptance/**;pms-framework/pms-common/**;sql/migrations/V16*.sql;yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/**;yudao-ui/yudao-ui-admin-vue3/src/router/modules/remaining.ts`
> 串行资源：`COM-01 Feature状态;COM公共契约;PROJ/ACC窄Owner契约;V160起Flyway;PRD修订010;master追溯投影`
> 旧功能范围：`COM-A与COM-B并行Feature规格和重复DeliveryScope模型；统一能力可用后标记废弃，不在旧实现上继续扩展`
> 验证：`PRD/SDS/Feature/DU校验;COM与PROJ聚焦测试;后端模块构建;前端测试与构建;Flyway静态及可用环境升级校验;五轴代码审查`
> 集成记录：`PENDING；COM-A闭环作为代码基础，COM-B仅按Requirement吸收非重复能力；全部历史Done需在master重新验证`

## 目标与裁决

- COM唯一事实链：ERP权威副本/人工待核对依据 → 合同订单项目关系 → DeliveryScope当前与历史 → 项目范围版本 → ACC精确范围绑定。
- COM地点唯一采用目标项目办事处发生时快照；COM-B的AST站点、位置和文本降级迁到IMP/AST实施地点，不形成第二套DeliveryScope地点真值。
- COM-B平台迁移证据拆为PLT能力；没有独立Requirement和消费方前不得宣称完成。
- `ProjectDeliveryScopeQualificationFactApi`只有在统一COM存在真实调用方和PROJ真实Provider时保留；否则标记废弃，不继续扩展空契约。
- 不整支合并`codex/f-acc-001-sds`或CUT/PROJ共享分支，不接收ACC-001/002、CUT或其他Feature实现。

## 交接

- 当前：已完成需求方地点Owner裁决和合并方案批准，开始上游规格收敛。
- 下一步：完成PRD修订010与统一Feature Spec，随后选择性迁入COM-A代码并按COM-B能力增补。
- 完成口径：master可构建增量可以先提交，但F-COM-001仅在统一规格全部AC、MySQL和真实浏览器证据重新通过后恢复Implementation Done。
