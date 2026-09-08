# DU-20260908-PM01-MANAGER-ROLE-FACT 多项目经理同角色权限

> DU状态：`INTEGRATED_COMPLETE`
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
> 集成记录：`本次master提交交付有效PM角色事实和阶段直接消费增量，19项定向测试通过；完整成员写入/UI与Feature Done未完成，释放本DU边界`

依据修订019同角色同权限；当前实际缺陷是指定用户的inspect过滤掉PROJECT_MANAGER成员查询而只认manager_id，阶段写守卫又拒绝非PRIMARY类型经理。复用既有成员区间与角色API，不新增模型；指定用户按当前有效角色判定，无用户条件的通知/主责读取继续使用主责引用。保留租户、版本、生命周期、角色范围、门禁及历史保护。开始时间2026-09-08 20:24:05；这里只交付权限消费增量，完整成员管理仍在途。

交付：指定用户inspect及锁重验读取有效PROJECT_MANAGER成员，返回实际责任类型但不以类型限制PM权限；保留无subject的主责引用。角色重验接受S0～S6并继续匹配调用方要求，阶段写守卫移除主责字段快捷放行与PRIMARY限制。旧服务经理规则、租户、版本及门禁保持。

验证：一次执行`mvn.cmd -o -q -pl pms-module-project -am '-Dtest=ProjectParticipantFactApiImplTest,ProjectParticipantFactMapperTest,ProjectStageAdvanceApplicationServiceTest' '-DskipITs=false' '-Dsurefire.failIfNoSpecifiedTests=false' test`，退出0。API 10/10、固定MySQL 5/5、阶段服务4/4，均0失败/错误/跳过；MySQL使用23316/npdms_test且仅清理本次成功创建的项目数据，未建第二环境、DDL或全表清理。读取与阶段写消费点经独立只读审阅，无Required/Critical；审阅者未执行测试。

耗时：从20:24:05开始，结束时刻以本次交付提交为准，已超过5分钟预算，不能声明效率目标达成。主要多耗在最初范围选择和读取；本次没有追加流程改造。成员增补/移除、联合指派、主责切换写API及UI仍需继续；不把本权限增量当成Task 10/Feature Done。
