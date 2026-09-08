# DU-20260908-PM01-COMPANY-ROLE-QUERY 上游公司角色用户查询

> DU状态：`PLANNED`
> DU类型：`TASK`
> Feature协调：`F-PROJ-001=TASK_COORDINATED`
> Task范围：`Task 10内上游通用公司/角色用户资格查询增量；候选与指定用户重验共享规则；不声明成员写入/UI或Feature Done`
> Owner：`Codex本次项目级成员管理会话`
> 分支：`codex/pm01-company-role-query`
> Worktree：`E:/AICoding/Worktrees/pm01q`
> 认领基线：`ef8bc4f92462905fa1e95ca4b7b55236d0716f8a`
> 认领提交：`SELF`
> 修改边界：`yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApi.java;yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApiImpl.java;yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/dto/CompanyRoleUserPageReqDTO.java;yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/dto/CompanyRoleUserRespDTO.java;yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/permission/query/CompanyRoleUserPageQuery.java;yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/permission/UserCompanyDepartmentScopeMapper.java;yudao-module-system/src/main/resources/mapper/permission/UserCompanyDepartmentScopeMapper.xml;yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/api/permission/CompanyRoleUserQueryMySqlIntegrationTest.java;tasks/delivery-units/DU-20260908-PM01-COMPANY-ROLE-QUERY.md`
> 串行资源：`上述PMS自建SYSTEM组织扩展文件；仅复用固定测试MySQL 23316/npdms_test做事务回滚验证，不创建第二套环境，不修改上游API及TRAE认领文件`
> 旧功能范围：`NONE`
> 验证：`新查询行为与原OrganizationScopeApi回归；同公司/角色同一有效行、跨部门去重、空集合、分页、过期停用及租户；SYSTEM与PROJ依赖编译；无DDL/UI不跑迁移/浏览器`
> 集成记录：`NONE`

## 正式合同与复用

依据用户确认、PRD PM-01规则11、Q-FPROJ-010与SDS10“通用公司/角色用户资格查询”。SYSTEM拥有UserCompanyDepartmentScope公司/角色有效授权，PROJ只消费，不能用现有项目成员推导候选，也不能拼接跨公司角色。接口不固定PROJECT_MANAGER，调用者指定公司和角色。

`OrganizationScopeApi`由本仓f923493a/5f6312a3引入，是PMS公司/组织扩展；本DU仅加法扩展它，保留原pageActiveUsers并复制必要查询片段到独立场景Query/XML，不修改上游AdminUserApi、DeptApi、PermissionApi及基础框架。多个部门记录以EXISTS表达资格，用户列表与COUNT共享条件，不新增表、索引或权限框架。

## 实施与验证边界

先实现查询，再补聚焦测试。用户明确只使用既有测试数据库，不另建H2、数据库、容器或应用环境。固定目标为127.0.0.1:23316/npdms_test，凭据仅由已核对的测试容器配置注入进程；测试在写入前断言实际库名和端口，不读取默认开发.env。使用专用标识的公司/用户/授权记录，每个用例在同一事务插入并回滚，再验证无残留；不执行初始化脚本、全表清理、迁移或失败触发器。数据库无结构变化，UI未涉及；不把持久层测试称为生产server或完整成员验收。

SQL实现参考[MyBatis动态SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)的参数绑定/集合条件及[MySQL 8.4 EXISTS](https://dev.mysql.com/doc/refman/8.4/en/exists-and-not-exists-subqueries.html)的存在性判定；只使用已有依赖，不新增hash校验或缓存。

## 交接

- 当前：PLANNED，未写代码；master提交计划、创建短工作树、激活并同步认领后实施。
- 交付只关闭本查询增量，Task 10项目级成员写入、角色集合消费者、UI及真实运行闭环仍未完成。
