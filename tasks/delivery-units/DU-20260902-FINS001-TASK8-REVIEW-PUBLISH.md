# DU-20260902-FINS001-TASK8-REVIEW-PUBLISH F-INS-001审核与发布闭环

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`TASK`
> Feature协调：`F-INS-001=TASK_COORDINATED`
> Task范围：`Task 8现有PermissionApi审核守卫、最后审核事实查询、生产审核入口与完整发布；保留已集成停用和发布CAS基础`
> Owner：`Codex本次F-INS-001 Task 8实施会话`
> 分支：`codex/f-ins-001-task8-review-publish`
> Worktree：`M:/AICoding/CodexData/worktrees/7a76/NPDMS/.run/fins001-task8`
> 认领基线：`00f386806540fa87b77c4ce655331c92be3a8f09`
> 认领提交：`SELF`
> 修改边界：`pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/**;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/**;pms-module-service/src/main/resources/mapper/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/controller/admin/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/**;scripts/tests/test_fins001_owner_and_query_boundary.py;docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-FINS001-TASK8-REVIEW-PUBLISH.md;tasks/delivery-units/README.md;docs/traceability/requirement-matrix.md;docs/traceability/requirement-version-coverage.json`
> 串行资源：`pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/README.md;docs/traceability/requirement-*`
> 旧功能范围：`NONE；旧pms_srv_rule、SrvRule Controller/Service/Mapper、旧页面、旧接口、旧菜单与旧迁移全部PRESERVE_EXISTING且不作为本DU实施基础`
> 验证：`F-INS Python门禁；InspectionRuleSecurityReviewPermissionGuardTest；InspectionRulePublicationServiceImplTest；InspectionRulePublicationMySqlIntegrationTest；pms-module-service适用测试与package；Requirement追溯；git diff --check；五轴代码审查`
> 集成记录：`master以--ff-only接收99213cef完整审核/发布实现、1c04f43f最后审核事实当前读并发修复及1057f083收口边界；本DU仅核销Task 8，不提升Feature Done`

## 目标与边界

实现`INS-03@V2`与`INS-09@V2`的Task 8审核和发布闭环：在租户访问完成目标租户上下文切换后，直接复用System现有`PermissionApi.hasAnyPermissions`判定当前审核人；普通角色菜单授权和System超级管理员均沿用布尔`true`，失败或异常关闭且不追加事实。审核事实只追加，记录精确权限码、`RBAC_PERMISSION`和空授权来源；发布在共享聚合锁内按`reviewed_at DESC, id DESC`重新选择同租户、同revision、同摘要最后事实，并复用既有AST重验、CAS、平台幂等和审计基础。

本DU不新增或修改Yudao System接口、DTO、Mapper、表或权限语义，不修改Flyway、前端、选择投影、旧规则功能及INS-01/02/04～08。只关闭Task 8本DU范围，不提前声明F-INS-001 Implementation Done。

## 交接

- 最后提交：`99213cef（审核/发布实现）；1c04f43f（最后审核事实并发修复）；1057f083（收口边界）；SELF（工程链回执）`
- 已完成：直接复用System现有`PermissionApi.hasAnyPermissions`完成目标租户审核权限判定；删除已被修订013替代且未装配的`InspectionRuleExplicitAuthorizationApi`，将前序内部`publishVerified`入口标记为待删除；实现DRAFT审核事实只追加、同聚合锁、最后审核事实精确查询、发布时完整领域/字典/AST重验、权威名称快照、旧发布版停用与新修订发布原子提交，并接入平台幂等、成功审计和失败审计。
- 剩余：Task 9工程师可选规则投影、Task 10管理端API与页面、Task 11～13全量验证/真实浏览器/最终追溯收口及Feature最终DoD不在本DU内，后续须从最新master新建DU。
- 测试：F-INS Python门禁26项PASS；service精确全量19个测试类共104项`Failures: 0 / Errors: 0 / Skipped: 20`；真实`npdms_test` MySQL公开审核/发布/停用5项及事务最后事实/原子发布/审核发布并发6项均`Skipped: 0`、全部PASS；23模块package与聚焦Java测试PASS；`git diff --check` PASS；五轴自审PASS。
- 已知失败：直接执行23模块无筛选全量测试会在进入service前被既有`FileArtifactMigrationContractTest`阻断；根因为Windows工作树将V92 SQL检出为CRLF，而该测试写死LF字符串。当前DU未修改`pms-module-platform`或`sql/migrations`，故不在本DU扩大修复；service适用全量已用精确类集合独立通过。

## 集成回执

`INTEGRATED_COMPLETE`：master已按线性快进接收Task 8全部生产代码、测试和最后审核事实并发修复。集成结论只说明Task 8交付单元完成；F-INS-001仍为`IMPLEMENTATION_IN_PROGRESS`、Implementation Done仍为`NOT_STARTED`，当前最近Gate转为Task 9。
