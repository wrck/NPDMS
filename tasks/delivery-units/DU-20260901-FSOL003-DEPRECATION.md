# DU-20260901-FSOL003-DEPRECATION 固定章节实现废弃标记

> DU状态：`PLANNED`
> DU类型：`TASK`
> Feature协调：`F-SOL-003=TASK_COORDINATED`
> Task范围：`只补齐已被动态表单替代的固定章节代码废弃标记与防回流测试`
> Owner：`Codex本次master工程链调整会话`
> 分支：`codex/f-sol-003-legacy-deprecation`
> Worktree：`M:/AICoding/CodexData/worktrees/fsol003-deprecation/NPDMS`
> 认领基线：`e4b7c863b202320eed9c012c16a4a56e0e3ffe49`
> 认领提交：`SELF`
> 修改边界：`pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysis*.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/preparation/RequirementAnalysisSectionDO.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/RequirementAnalysisSectionMapper.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/requirement/RequirementAnalysisCatalog.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysis*.java;scripts/generate_requirement_traceability.py;scripts/tests/test_fsol003_dynamic_form_amendment.py;scripts/tests/test_generate_requirement_traceability.py;specs/features/F-SOL-003-requirement-analysis-versioning.md;specs/features/README.md;yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/requirement-analysis/index.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts`
> 串行资源：`specs/features/README.md;scripts/generate_requirement_traceability.py`
> 旧功能范围：`LegacyRequirementAnalysisFixedSections`
> 验证：`Python F-SOL-003契约与追溯生成器测试；受影响前端类型检查`
> 集成记录：`NONE`

## 目标与边界

本DU只迁移当前PROJ工作树中已经形成的F-SOL-003废弃整改：Java使用`@Deprecated`，TypeScript使用`@deprecated`，Vue保留历史只读提示，并由测试阻止固定章节实现重新成为Feature实施基础。原`pms_eng_requirement`独立旧功能不在本次替代范围，不得误删或改写。

## 交接来源

- 来源工作树：`M:/AICoding/CodexData/worktrees/7a76/NPDMS`
- 来源状态：未提交，必须以补丁复制到本DU工作树；原工作树保持不变。
- 目标分支必须从包含本认领记录的master提交创建，不能把F-PROJ-008共享历史带入本DU。
