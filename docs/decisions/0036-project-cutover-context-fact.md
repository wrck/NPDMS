# ADR-0036：割接接入所需项目发生时上下文事实

## 状态

`ACCEPTED`

## 日期

2026-08-31

## 需求依据

- PRD V1.8：`CUT-01@V1`。
- F-CUT-002需要按设备归属解析有权项目，并在任务创建与P2提交时冻结、重验项目、办事处和客户身份展示事实。
- PROJ是项目主档及项目发生时组织/客户快照Owner；CUS继续拥有客户主档和客户服务等级时间线。

## 问题

现有`ProjectScopeApi`只返回项目范围和树版本；`ProjectOrganizationFactApi`只返回公司/部门标识；`CustomerServiceLevelFactApi`只返回客户ID及服务等级时间线。它们不能唯一提供F-CUT-002已锁定的`projectCode/projectName/officeCode/officeName/customerId/customerCode/customerName`，CUT又不得跨Context读表或拼接零散Summary。

## 决策

1. 在现有`pms-module-project-api`加性新增`ProjectCutoverContextFactApi`，真实Provider位于`pms-module-project`，不新建第二PROJ API模块。
2. 接口包固定为`cn.iocoder.yudao.module.pms.project.api.cutovercontext`，方法固定为`ProjectCutoverContextFactResult inspect(ProjectCutoverContextFactQuery)`与`ProjectCutoverContextFactResult lockAndRevalidate(ProjectCutoverContextFactRevalidationQuery)`。前者输入`tenantId + projectId`并只读返回当前项目发生时上下文；后者携带从前次`FOUND`原样复制的完整`ExpectedProjectCutoverContextFact`，以`MANDATORY`加入CUT写事务并锁定同一`proj_project`行。显式tenant必须与受信`TenantContextHolder`一致，Provider不得切换租户。
3. `FOUND`事实精确包含：`tenantId/projectId/projectVersion/projectCode/projectName/customerId/customerCode/customerName/departmentId/departmentCode/departmentName`。所有值直接来自同一`ProjectMasterDO`行，不由CUT、客户端、名称规则或SYSTEM/CUS当前值推导；CUT展示层可把department字段标注为办事处，但公共DTO不得引入`officeCode`等第二命名。编码字段最大64字符，`projectName/customerName/departmentName`最大255字符，与PROJ物理Owner一致，消费方不得截断。
4. `customerCode/customerName`是项目主档中的发生时客户展示快照；它不成为CUS当前客户主档或服务等级第二真值。CUT仍以`customerId`调用`CustomerServiceLevelFactApi`取得和重验当前等级时间线。
5. `ProjectCutoverContextFactResult`为严格判别联合：`FOUND`必须携带完整Fact，`VERSION_CONFLICT`携带当前完整Fact，`NOT_FOUND/INACTIVE`不得携带Fact。`inspect`只允许`FOUND/NOT_FOUND/INACTIVE`；`lockAndRevalidate`允许`FOUND/NOT_FOUND/INACTIVE/VERSION_CONFLICT`。只有同租户、生命周期`ACTIVE`、字段完整，且锁定行的上述全部Fact字段逐项精确等于Expected时可返回可写`FOUND`；任一字段不同均返回`VERSION_CONFLICT`。Expected只用于并发守卫，CUT最终冻结且写入的事实只能取锁后Owner返回的`currentFact`。
6. `VERSION_CONFLICT`供调用方刷新并重新明确选择；Owner数据缺损或Provider异常使用`ProjectCutoverContextFactException`稳定失败，不伪装成`NOT_FOUND`或版本冲突。
7. 该Fact不做项目数据范围授权。CUT必须先后继续使用`ProjectScopeApi`的`PROJECT_VIEW/PROJECT_EDIT`和`treeVersion`；项目范围版本与项目主档版本是两个独立水位，不得互相替代。

## 稳定失败

- `INVALID_REQUEST`
- `TENANT_CONTEXT_MISMATCH`
- `OWNER_DATA_CORRUPTED`
- `PROVIDER_UNAVAILABLE`

## 边界

- 本ADR只批准公共合同、Owner方向和F-CUT-002消费映射，不批准Provider产品代码、CUT实现、Flyway或Technical Plan GO。
- 不扩展`ProjectScopeApi`，不修改现有DTO构造器，不让CUT访问`proj_project`、SYSTEM或CUS表。
- 不把客户服务等级、项目范围或设备归属合并进该Fact。
