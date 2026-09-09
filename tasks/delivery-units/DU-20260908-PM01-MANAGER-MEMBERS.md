# DU-20260908-PM01-MANAGER-MEMBERS 项目经理成员写入

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`TASK`
> Feature协调：`F-PROJ-001=TASK_COORDINATED`
> Task范围：`Task 10项目经理成员增补、移除和主责切换后端API；复用公司角色资格、权限及幂等审计；不包含UI及联合服务经理事务`
> Owner：`Codex当前项目交付主线会话`
> 分支：`codex/pm01-manager-members-2916`
> Worktree：`M:/AICoding/CodexData/worktrees/2916/NPDMS`
> 认领基线：`18c5a7957a3e01cc958032b700d4a13a7e01a0f4`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmember/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectmember/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMasterMapper.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectManagerMemberQuery.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectManagerMemberUpdate.java;pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml;pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmember/**;docs/design/10-api-design.md;tasks/features/F-PROJ-001.md;tasks/delivery-units/DU-20260908-PM01-MANAGER-MEMBERS.md;tasks/delivery-units/README.md`
> 串行资源：`上述PROJ文件；复用固定测试MySQL23316/npdms_test，不改TRAE文件/环境/DDL`
> 旧功能范围：`NONE`
> 验证：`成员事务、资格及授权拒绝、版本/幂等、主责一致性、历史和回滚；固定测试库；无UI不宣称浏览器完成`
> 集成记录：`2026-09-08按用户指令由master快进合入753e99e9，仅包含本DU后端增量，无冲突或代码改写，复用下述有效测试与审阅证据；释放本DU写边界。Task 10及Feature仍IN_PROGRESS，不包含UI、联合服务经理事务或通知送达`

按用户要求快速认领，不先做范围收敛轮次。直接承接修订019已确认规则和Task 10；公司角色查询及同角色权限已完成，成员写入前只补齐必要请求/响应和事务细则。保留旧服务经理接口、项目阶段/生命周期和历史；开始时间2026-09-08 20:42:40。

交付：PM-01，Owner为PROJ。新增`POST /api/v1/pms/projects/{id}/actions/update-project-managers`，复用SYSTEM公司角色资格API、既有指派权限/数据范围守卫及PLT幂等事务。只更新`proj_project`主责投影/指派状态/版本和`proj_project_member_assignment`责任区间，审计及Outbox同事务；角色事实API是直接消费者。增删多经理、切换或清空主责，不改变生命周期、阶段、任务或旧服务经理接口；无DDL及依赖变更。

实际验证：原工作树使用固定`127.0.0.1:23316/npdms_test`，命令为`mvn.cmd -o -q -pl pms-module-project -am '-Dtest=ProjectManagerMemberMySqlTest,ProjectParticipantFactApiImplTest' '-DskipITs=false' '-Dsurefire.failIfNoSpecifiedTests=false' test`。角色API 10/10；首轮MySQL的Outbox故障注入未命中代理目标，改为setter后单独重跑`ProjectManagerMemberMySqlTest`，6/6通过（9.370秒）；补充旧工号清空断言后，仅重跑`ProjectManagerMemberMySqlTest#addsMultipleManagersAndChangesPrimaryWithoutRewritingMembershipHistory`，1/1通过（12.29秒）。覆盖新增/主责切换/移除历史、资格和权限拒绝、版本/租户/生命周期拒绝、幂等重放/冲突/无变化、并发及Outbox失败原子回滚。测试只清理自身成功创建的数据；功能权限接口和数据范围守卫为可控替身，不宣称整套真实鉴权验收。

审阅：复用前轮独立只读审阅和本次自审；此前工号意见按现有上游无权威工号、字段可空的事实处理，同步置空避免沿用旧人数据，已补合同及断言；未取得此末次差异的独立复验回执。未执行UI/HTTP运行验收、通知投递及完整Feature验收，不以本API增量宣称Task 10完成。

交接：原认领已由master提交`ec1aed7b`；原工作树20:45:40被切换分支，21:13停止提交等待确认。用户将同一未提交增量迁移至2916工作树并明确要求提交；交付分支从包含认领的同一HEAD建立，Owner/实现边界未变，复用未变代码的测试证据。后续按用户master指令在2916工作树集成，索引与回执本次集中更新。实施至21:13约30分钟，效率目标未达成；本次不追加流程改造。
