# DU-20260908-PM01-MANAGER-ROLE-FACT 多项目经理同角色权限

> DU状态：`CLAIMED`
> DU类型：`TASK`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED`
> Task范围：`Task 10有效项目经理角色事实与阶段授权直接消费者；保留无指定用户的主责查询；不实现成员写入/UI或宣称Feature Done`
> Owner：`Codex当前项目交付主线会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`50c23217e89aa2cdd5e12fb976f647268437e0ac`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/ProjectParticipantFactApiImpl.java;pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectStageAdvanceApplicationService.java;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/participant/ProjectParticipantFactApiImplTest.java;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectParticipantFactMapperTest.java;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectStageAdvanceApplicationServiceTest.java;docs/design/10-api-design.md;tasks/features/F-PROJ-001.md;tasks/delivery-units/DU-20260908-PM01-MANAGER-ROLE-FACT.md;tasks/delivery-units/README.md`
> 串行资源：`上述PROJ文件；仅使用固定测试库23316/npdms_test；不改TRAE文件、DDL、阶段图或全局配置`
> 旧功能范围：`NONE`
> 验证：`角色事实/阶段授权定向测试及固定MySQL Mapper用例；无UI/DDL不执行浏览器/迁移`
> 集成记录：`NONE`

依据修订019同角色同权限；当前实际缺陷是指定用户的inspect过滤掉PROJECT_MANAGER成员查询而只认manager_id，阶段写守卫又拒绝非PRIMARY类型经理。复用既有成员区间与角色API，不新增模型；指定用户按当前有效角色判定，无用户条件的通知/主责读取继续使用主责引用。保留租户、版本、生命周期、角色范围、门禁及历史保护。开始时间2026-09-08 20:24:05；这里只交付权限消费增量，完整成员管理仍在途。
