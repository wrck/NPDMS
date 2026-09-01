# DU-20260901-FSOL003-DEPRECATION 固定章节实现废弃标记

> DU状态：`HANDOFF_READY`
> DU类型：`TASK`
> Feature协调：`F-SOL-003=TASK_COORDINATED`
> Task范围：`只补齐已被动态表单替代的固定章节代码废弃标记与防回流测试`
> Owner：`Codex本次master工程链调整会话`
> 分支：`codex/f-sol-003-legacy-deprecation`
> Worktree：`M:/AICoding/CodexData/worktrees/fsol003-deprecation/NPDMS`
> 认领基线：`60054c1b420009f76b85d951a5c47e89cdccc818`
> 认领提交：`SELF`
> 修改边界：`pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysis*.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/preparation/RequirementAnalysisSectionDO.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/RequirementAnalysisSectionMapper.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/requirement/RequirementAnalysisCatalog.java;pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysis*.java;scripts/generate_requirement_traceability.py;scripts/tests/test_fsol003_dynamic_form_amendment.py;scripts/tests/test_generate_requirement_traceability.py;specs/features/F-SOL-003-requirement-analysis-versioning.md;specs/features/README.md;yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/requirement-analysis/index.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts`
> 串行资源：`specs/features/README.md;scripts/generate_requirement_traceability.py`
> 旧功能范围：`LegacyRequirementAnalysisFixedSections`
> 验证：`58项F-SOL-003/追溯/DU/旧实现清单测试通过；追溯、DU、旧实现清单校验通过；mvn -pl pms-module-engineering -am -DskipTests compile BUILD SUCCESS`
> 集成记录：`候选3e27f047abb5771507985102786ce34d72ca7f0a；待master串行集成`

## 目标与边界

本DU只迁移当前PROJ工作树中已经形成的F-SOL-003废弃整改：Java使用`@Deprecated`，TypeScript使用`@deprecated`，Vue保留历史只读提示，并由测试阻止固定章节实现重新成为Feature实施基础。原`pms_eng_requirement`独立旧功能不在本次替代范围，不得误删或改写。

## 交接来源

- 来源工作树：`M:/AICoding/CodexData/worktrees/7a76/NPDMS`
- 来源状态：未提交，必须以补丁复制到本DU工作树；原工作树保持不变。
- 目标分支必须从包含本认领记录的master提交创建，不能把F-PROJ-008共享历史带入本DU。

## 交接

- 最后提交：`3e27f047abb5771507985102786ce34d72ca7f0a`
- 已完成：21个受控文件；旧固定章节Java/TypeScript/Vue标记废弃；新增防回流与Feature索引漂移检查。
- 明确排除：来源工作树中的`.run`删除和`specs/features/README.md`分支状态回写均未迁移。
- 剩余：master选择该单一提交集成并在最终内容上复验。
- 已知失败：无；前端改动仅增加`@deprecated`注释，未改变可执行TypeScript/Vue逻辑。
