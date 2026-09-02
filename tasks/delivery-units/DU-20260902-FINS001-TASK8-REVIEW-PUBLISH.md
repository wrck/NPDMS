# DU-20260902-FINS001-TASK8-REVIEW-PUBLISH F-INS-001审核与发布闭环

> DU状态：`CLAIMED`
> DU类型：`TASK`
> Feature协调：`F-INS-001=TASK_COORDINATED`
> Task范围：`Task 8现有PermissionApi审核守卫、最后审核事实查询、生产审核入口与完整发布；保留已集成停用和发布CAS基础`
> Owner：`Codex本次F-INS-001 Task 8实施会话`
> 分支：`codex/f-ins-001-task8-review-publish`
> Worktree：`M:/AICoding/CodexData/worktrees/7a76/NPDMS/.run/fins001-task8`
> 认领基线：`00f386806540fa87b77c4ce655331c92be3a8f09`
> 认领提交：`SELF`
> 修改边界：`pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/**;pms-module-service/src/main/resources/mapper/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/**;scripts/tests/test_fins001_owner_and_query_boundary.py;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-FINS001-TASK8-REVIEW-PUBLISH.md;tasks/delivery-units/README.md;docs/traceability/requirement-matrix.md;docs/traceability/requirement-version-coverage.json`
> 串行资源：`pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/README.md;docs/traceability/requirement-*`
> 旧功能范围：`NONE；旧pms_srv_rule、SrvRule Controller/Service/Mapper、旧页面、旧接口、旧菜单与旧迁移全部PRESERVE_EXISTING且不作为本DU实施基础`
> 验证：`F-INS Python门禁；InspectionRuleSecurityReviewPermissionGuardTest；InspectionRulePublicationServiceImplTest；InspectionRulePublicationMySqlIntegrationTest；pms-module-service适用测试与package；Requirement追溯；git diff --check；五轴代码审查`
> 集成记录：`NONE`

## 目标与边界

实现`INS-03@V2`与`INS-09@V2`的Task 8审核和发布闭环：在租户访问完成目标租户上下文切换后，直接复用System现有`PermissionApi.hasAnyPermissions`判定当前审核人；普通角色菜单授权和System超级管理员均沿用布尔`true`，失败或异常关闭且不追加事实。审核事实只追加，记录精确权限码、`RBAC_PERMISSION`和空授权来源；发布在共享聚合锁内按`reviewed_at DESC, id DESC`重新选择同租户、同revision、同摘要最后事实，并复用既有AST重验、CAS、平台幂等和审计基础。

本DU不新增或修改Yudao System接口、DTO、Mapper、表或权限语义，不修改Flyway、前端、选择投影、旧规则功能及INS-01/02/04～08。只关闭Task 8本DU范围，不提前声明F-INS-001 Implementation Done。

## 交接

- 最后提交：`NONE`
- 已完成：无
- 剩余：全部
- 测试：未开始
- 已知失败：无

## 集成回执

由master协调者记录选中的提交范围、验证结果和`INTEGRATED_PARTIAL|INTEGRATED_COMPLETE`结论。分支自报不能填写master集成结论。
