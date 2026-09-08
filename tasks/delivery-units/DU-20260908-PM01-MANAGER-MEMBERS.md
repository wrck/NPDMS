# DU-20260908-PM01-MANAGER-MEMBERS 项目经理成员写入

> DU状态：`CLAIMED`
> DU类型：`TASK`
> Feature协调：`F-PROJ-001=TASK_COORDINATED`
> Task范围：`Task 10项目经理成员增补、移除和主责切换后端API；复用公司角色资格、权限及幂等审计；不包含UI及联合服务经理事务`
> Owner：`Codex当前项目交付主线会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`18c5a7957a3e01cc958032b700d4a13a7e01a0f4`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmember/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectmember/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMasterMapper.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectManagerMemberQuery.java;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectManagerMemberUpdate.java;pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml;pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmember/**;docs/design/10-api-design.md;tasks/features/F-PROJ-001.md;tasks/delivery-units/DU-20260908-PM01-MANAGER-MEMBERS.md;tasks/delivery-units/README.md`
> 串行资源：`上述PROJ文件；复用固定测试MySQL23316/npdms_test，不改TRAE文件/环境/DDL`
> 旧功能范围：`NONE`
> 验证：`成员事务、资格及授权拒绝、版本/幂等、主责一致性、历史和回滚；固定测试库；无UI不宣称浏览器完成`
> 集成记录：`NONE`

按用户要求快速认领，不先做范围收敛轮次。直接承接修订019已确认规则和Task 10；公司角色查询及同角色权限已完成，成员写入前只补齐必要请求/响应和事务细则。保留旧服务经理接口、项目阶段/生命周期和历史；开始时间2026-09-08 20:42:40。
