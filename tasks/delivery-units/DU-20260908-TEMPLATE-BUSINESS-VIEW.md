# DU-20260908-TEMPLATE-BUSINESS-VIEW 单一模板与业务视图基础

> DU状态：`CLAIMED`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-PLT-003=FEATURE_EXCLUSIVE;F-PROJ-009=FEATURE_EXCLUSIVE;F-PROJ-007=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED`
> Task范围：`已批准前置基础包：单一模板升级、业务视图PAGE/DYNAMIC_FORM注册与复用、必要直接消费者；不含S0～S6领域业务或独立验收新创建/范围绑定`
> Owner：`ZCode当前模板与业务视图会话`
> 分支：`codex/pre-s0-template-foundation`
> Worktree：`E:/AICoding/Worktrees/p903`
> 认领基线：`20e542f91b0c5c277ba3c7c2147daa5a6ec55af1`
> 认领提交：`SELF`
> 修改边界：`specs/features/F-PLT-003-business-view-registration.md;specs/features/F-PROJ-009-project-delivery-template-configuration-center.md;tasks/features/F-PLT-003.md;tasks/features/F-PROJ-009.md;tasks/features/F-PROJ-007.md;tasks/features/F-PROJ-008.md;docs/superpowers/plans/2026-09-08-template-business-view-foundation.md;docs/design/08-data-model.md;docs/design/09-database-design.md;docs/traceability/sds-revision-016-physical-contract.json;specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql;pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/businessview/**;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/domain/businessview/**;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/businessview/**;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/businessview/**;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/businessview/**;pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/businessview/**;pms-module-platform/src/main/resources/mapper/businessview/**;pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/businessview/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/deliveryconfiguration/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/deliveryconfiguration/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttemplate/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttemplate/**;pms-module-project/src/main/resources/mapper/projecttemplate/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/domain/deliveryconfiguration/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/deliveryconfiguration/**;yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/business-view/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/business-view/**;yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/**;yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-templates/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/**;tasks/delivery-units/DU-20260908-TEMPLATE-BUSINESS-VIEW.md`
> 串行资源：`避开PM01-MEMBER-CLOSURE当前占用的SDS10、F-PROJ-001 Task、项目详情入口及成员代码；Flyway/公共权限/实际消费者接入前须在master修订认领；固定MySQL23316/npdms_test使用窗口须核对，禁止reset/clean/repair`
> 旧功能范围：`NONE`
> 验证：`定义/图/视图契约、租户/权限拒绝、版本/幂等/并发、失败回滚及历史保护；适用MySQL/Flyway与真实浏览器；当前未执行，不宣称通过`
> 集成记录：`NONE`

## 批准与完成边界

需求方已批准修订方案：保留视图注册/版本/Owner/权限，在其上增加现有页面和动态表单复用，支持以后注册专用页面；现有模板直接升级，不保留两套模型、入口或运行解释。Requirement为PM-03@V1，关联PM-11及现有创建/运行消费者；本包不等同PM-03全部完成。

## 并行交接与实施顺序

当前根工作树存在他人未提交PM01改动，master工作树也正在完成成员闭环。认领提交仅包含本DU及两个新Feature Task，不修改其在途内容。实施使用独立短路径工作树。

本次先认领无冲突的视图/模板独立文件。SDS10、F-PROJ-001 Task和项目详情入口仍由PM01-MEMBER-CLOSURE占用，本DU不倒签或覆盖；涉及这些权威文件的API正式落位、初始化/工作区接入前必须获得释放/明确串行交接，并修订本DU后继续。独立数据/领域模型及规范可以先推进，未明确公开契约不得用代码代替正式设计。

Flyway编号、公共权限/错误码及运行消费者文件在实际接入前核对当前master并扩展精确写边界，不预留迁移号，不修改历史迁移。旧发布模板和已建项目不自动推图或批量切换；存在影响真实可写历史的缺口时登记具体对象及最小待决事项。
