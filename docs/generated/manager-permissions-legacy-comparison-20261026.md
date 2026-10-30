# 旧系统服务经理、项目经理权限与当前实现差异

核查日期：2026-10-26。范围：旧系统本地源码、当前NPDMS源码、测试库角色及组织/成员数据。只读审查，未修改角色、范围、成员、项目状态或历史，未部署、重启或提交。

## 结论

当前数据权限与旧系统确有模型差异，不能仅通过增加菜单权限或补齐项目成员解决。

1. 当前项目列表、统计、详情、项目树以有效且已确认的项目成员、创建人查阅和显式项目授权为主要范围来源，没有将SYSTEM已有公司/部门服务范围接入统一PROJ范围计算。服务经理的区域管理职责没有在这里形成独立授权来源。
2. 项目经理在授权办事处的概况查阅、闭环/不予跟踪项目查阅，与当前“仅完整项目范围列表”不同。当前SDS已写明本办事处非敏感概况可查，但项目列表实现没有走对应概况范围。
3. 旧系统存在Struts和SpringMVC两条权限链，角色编码和入口规则不同。不能把旧系统简化成“服务经理所有项目全权、项目经理仅本人项目”，也不能把区域查阅权直接升级为编辑或审批权。
4. 用户已明确“服务经理是区域负责人，管理本区域内所有项目经理，权限层级高于项目经理”。这是本次对齐目标；旧源码逐项说明哪些入口已有区域能力，不能借旧入口更窄的默认列表反向否定该业务要求。

## 核查对象与证据边界

- 旧系统源码：`D:/IDEAWorkspace/PMS`，包含PMS-struts、PMS-springmvc、core；Git HEAD `bb206c4`（2022-03-11）。所引核心文件无未提交差异。未取得旧线上当前构建指纹或实时角色权限表，因此以下是这份源码的行为，不冒充旧线上所有入口已重新验收。
- 当前工作树：`M:/AICoding/CodexData/worktrees/853c/NPDMS`，HEAD `c9b14415a`，含其他已有未提交改动；本报告引用当前代码，不将其全部视为部署版本。
- 同时查看 `E:/AICoding/Projects/NPDMS` 的codex/domain-migration，HEAD `3ea13217a`；其ProjectTreeScopeService也没有组织范围授权分支。未对两个分支做全库等价声明。
- 测试库：既有远程npdms、tenant=1，2026-10-26 19:03:57只读一致性快照；脚本和原始回执在 `.run/manager-permission-audit-20261026/read.py`、`target.json`。没有读取密码、Token或人员联系方式。
- 未模拟账号登录、未重新执行两角色真实浏览器验收；数据库候选计数不是接口实际可见数量。

## 旧系统角色与权限来源

| 来源 | 服务经理/区域负责人 | 项目经理 | 范围来源 |
|---|---|---|---|
| Struts旧角色 | fnd角色11，serviceManager | fnd角色12，programManager | 用户areapower、有效项目成员、具体业务入口的人员范围和流程受理人 |
| SpringMVC角色 | projectAreaManager（区域负责人） | projectManager | t_user_info.custom4项目类型、custom5办事处、当前公司角色、有效项目成员 |
| 项目成员 | memberRole=20 | memberRole=30（另有40参与编辑判定） | pm_project_member有效起止时间、memberCode |
| 当前迁移映射 | fnd serviceManager→SERVICE_MANAGER；旧projectAreaManager仍保留同名角色 | fnd programManager→PROJECT_MANAGER | 角色承接不等于旧数据授权算法已接入 |

旧系统角色、成员角色和区域范围是不同事实。当前迁移保留旧角色名称，不能据此宣称区域管理语义已迁完。

## 旧系统逐项能力

| 场景 | 服务经理 | 项目经理 | 源码证据 |
|---|---|---|---|
| Struts默认项目列表 | 本人为有效服务经理或项目经理的项目；另含授权办事处状态20/100项目 | 本人为有效项目经理的项目；另含授权办事处状态20/100项目 | ProjectDaoImpl:201；实际加载sql-map-project-config.xml:390 |
| 闭环、不予跟踪项目 | 可按区域查询；20为不予跟踪，100为闭环 | 同样存在区域查阅分支 | MessageUtil:47、119；项目SQL:397、413 |
| 按项目名称查找 | 去掉本人指派限制，保留办事处范围 | 同左 | ProjectDaoImpl:236附近 |
| 按设备条码查找 | 旧DAO显式移除办事处和本人负责人限制 | 同左 | ProjectDaoImpl:230附近；仅表示这个旧查询分支，不能推导所有编辑/下载权限 |
| SpringMVC项目列表 | 项目类型+办事处范围并结合成员分支 | 同样有组织范围查询，编辑另判 | ProjectController:147；ProjectHeaderMapper权限SQL |
| SpringMVC详情及编辑 | 范围命中后projectAreaManager进入角色增强，可形成all操作级别；仍先校验功能权限和业务状态 | 有效项目成员形成edit；仅区域命中、无成员通常为view；成员20/30/40且范围命中可形成all | ProjectHeaderService:971；ProjectHeaderMapper:1447、1543；PermissionUtils:85 |
| 人员维护 | 区域负责人属于增强角色集合 | 项目经理允许其项目编辑；其他角色的edit降为view | ProjectMemberController:128–136 |
| 维护记录 | 区域范围、本人创建、项目成员、服务经理管理的人员范围均可参与查询 | 区域范围、本人或项目成员参与查询 | MaintenanceAction:175–191；sql-map-maintenance-config.xml:232–247 |
| 审批与待办 | 服务经理仍按任务assignee及流程节点处理，区域可见不等于可代批 | 项目经理按本人任务和节点权限执行 | WorkSpaceDaoImpl:115；闭环/回退相关业务服务 |
| 项目编辑状态 | 特定项目状态允许编辑，不是区域内无条件写入 | 同样受具体项目状态限制 | ProjectAction.obtainModifyflag:1009 |

Struts实际配置入口是 `PMS-struts/config-ibaits/sql-map-config.xml:17` 引用的 `sql-map-project-config.xml`。同目录config2副本不作为运行依据。

维护SQL中的 `serviceManagerPower_<用户>` 明确表达服务经理→项目经理人员范围，但本轮未找到其生成定义；不能臆测该关系由行政部门、直接领导或全部角色成员生成。

## 当前实现与差异清单

| 编号 | 当前事实 | 与旧系统/既定职责差异 | 影响及处理建议 |
|---|---|---|---|
| D01 区域管理范围未接入 | ProjectTreeScopeService只从有效已确认成员、创建人VIEW和显式授权发现项目；无OrganizationScopeApi依赖 | 服务经理维护了公司部门范围，仍不能据此管理范围内未逐项目指派的项目 | 优先补统一区域管理范围解析，复用SYSTEM现有范围；不要批量伪造成项目成员 |
| D02 办事处概况缺失 | getProjectPage和statistics只取resolveAllFullProjectIds；summaryProjectIds当前返回空 | 旧系统有区域查阅路径；SDS07:67要求项目经理可查询本办事处非敏感概况 | 独立区分概况查阅和完整详情/编辑，列表和统计同口径；不要把全办事处项目都授予PROJECT_EDIT |
| D03 查找行为变化 | 当前先收敛授权项目集合，再按项目名/条码筛选 | 旧DAO按名称和条码有扩展查询范围的特殊处理 | 记录为兼容差异；旧跨区域条码行为涉及边界扩大，不能原样绕过租户/权限。需明确搜索应返回概况还是可办详情 |
| D04 服务经理与项目经理范围层级未体现 | MANAGEMENT_CODES把两类角色均作为项目内MANAGE资格；仍依赖项目成员 | 当前有项目内动作区别，却缺少区域管理层次的范围区别 | “角色较高”应体现在区域管理覆盖；具体审批仍使用真实项目Owner和流程候选 |
| D05 组织数据存在但消费割裂 | OrganizationScopeApi用于候选人、创建/指派资格、设备组织范围等；PROJ统一范围没有接入 | 同一人能成为区域服务经理候选或查组织设备，却可能无法看对应项目 | 修复共同授权入口及消费者契约，不在每个页面增加例外 |
| D06 旧区域角色未形成等价入口 | 测试库projectAreaManager存在，非空菜单权限0；当前PROJ管理角色常量不含该编码 | 旧SpringMVC区域负责人身份不能仅靠同名角色在新系统发挥旧效果 | 核实这类账号与SERVICE_MANAGER的实际映射，按同一人员/公司边界处理，不直接全量提权 |
| D07 成员授权确认条件新增 | 当前有效成员还要求authorization_confirmed=1；旧按有效期及人员编码 | 已迁成员存在但未确认时，不进入当前范围 | 逐条核实合法来源与确认回执，不取消确认校验，不覆盖历史 |
| D08 功能权限不是严格超集 | 服务经理164项、项目经理138项非空权限；项目经理另有两个删除权限 | 服务经理“层级较高”不代表当前功能权限集合天然包含所有项目经理动作 | 核实删除权限业务必要性；不能仅按角色高低批量复制删除权限 |

当前具体代码：

- `ProjectTreeScopeService.java:93`成员→完整范围，`:105`创建人VIEW，`:114`显式授权；`:180`全部完整项目范围；`:360`角色动作判定。
- `ProjectViewReadScopeSql.xml:42`数据库读取范围同样只用成员、创建人和显式授权，没有组织范围分支。仅修Java查询会让SQL消费者继续不一致。
- `ProjectManualCreationServiceImpl.java:426`项目分页；`ProjectStatisticsService.java:22`统计。
- `OrganizationScopeApiImpl.java:43`读取有效公司部门范围、`:56`按同一公司+部门判断资格；该能力已有，不应另建一套区域权限表。
- `ProjectAuthorizationGuard.java:114`管理范围校验，服务经理授权维护仍依赖PROJECT_MANAGE；`:163`服务经理身份取有效项目成员。
- `ProjectMemberRoles.java:16`服务经理旧L1/L2识别集合、`:18`两经理管理角色集合、`:28`服务经理归一。
- `P04AccountSyncTask.java:195`两套来源角色映射；`DataScopeEnum.java:26`SELF=5。

## 测试库现状

| 项目 | 服务经理 | 项目经理 | 旧区域负责人角色 |
|---|---|---|---|
| 当前角色编码 | SERVICE_MANAGER | PROJECT_MANAGER | projectAreaManager |
| 状态 | 启用 | 启用 | 启用 |
| system_role.data_scope | 5（SELF） | 5（SELF） | 5（SELF） |
| 非空菜单权限去重数 | 164 | 138 | 0 |

上述菜单统计未额外按菜单status过滤，因此是已配置权限集合，不声称全部菜单当前可操作。两经理的data_scope同为SELF不等于项目只能看本人创建记录：PROJ另有显式范围算法；直接把data_scope改为ALL不能修复模型，反而可能扩大其他模块权限。

服务经理特有权限包括项目指派、授权管理/撤销、部分审核、巡检办理等。项目经理独有而服务经理没有的两项是 `pms:acc-completion-certificate:delete` 和 `pms:imp-training:delete`，不擅自判定应补给服务经理。

| 账号样本 | 系统角色 | 有效组织范围命中项目数 | 其中本人有效已确认成员项目数 | 说明 |
|---|---|---:|---:|---|
| z01010 | SERVICE_MANAGER + PROJECT_MANAGER | 2889 | 2705 | 184个组织范围项目无本人确认成员；其他显式授权/创建人尚可能覆盖，不能直接称184个均无权 |
| y06496 | PROJECT_MANAGER | 2889 | 125 | 不能把剩余2764个都授予编辑；应核查应有区域概况查询 |

z01010组织范围有公司001/002及部门162007、167001、310407；y06496有公司001/002及部门162007、310407。存在不同来源区间形成的重复公司部门组合，本轮COUNT DISTINCT避免重复计数，未清理这些记录。统计使用项目主档公司/部门精确匹配，不含组织树递归或项目公司部门多关系扩展；不是最终应授权集合。

## 建议对齐规则与验证顺序

1. 服务经理：按有效公司+区域/部门服务范围覆盖区域项目管理；明确项目类型限制如何承接旧custom4。已有成员/显式跨区域授权继续参与，不能因区域模型上线删掉它们。
2. 项目经理：本人有效负责/授权项目可按动作办理；授权办事处其他项目仅按正式契约返回非敏感概况。概况不附带正文下载、敏感商务、审批或修改权限。
3. 实际办理仍需功能权限、项目范围、Owner角色/状态和流程候选共同通过。区域负责人不自动成为每个审批节点的受理人。
4. 统一校验项目分页、统计、详情、树、设备/合同关联、成员候选、授权管理及业务办理。必须覆盖区域内无成员、跨区域有显式授权、同部门不同公司、失效范围、离职账号、闭环只读和越权拒绝。
5. 使用真实服务经理/项目经理分别验证，至少保留一个区域内未逐项目指派样本；管理员正常或项目成员数量相近不能证明区域权限正确。

本轮完成源码与测试配置差异整理，不修改PRD/SDS业务语义，不执行权限修复。已知区域管理目标无需再次询问；具体旧项目类型限制、跨区域条码搜索和维护人员范围来源应在实施前沿现有正式来源确认。

## 可定位来源

- 旧源码根：`D:/IDEAWorkspace/PMS`。
- Struts项目权限：`PMS-struts/src/com/dp/plat/dao/ProjectDaoImpl.java:201`；`PMS-struts/config-ibaits/sql-map-project-config.xml:390`；`PMS-struts/src/com/dp/plat/util/MessageUtil.java:217`。
- SpringMVC区域及成员权限：`PMS-springmvc/src/main/java/com/dp/plat/pms/springmvc/controller/ProjectController.java:147`；`service/impl/ProjectHeaderService.java:971`；`mapping/ProjectHeaderMapper.xml:1447`；`util/PermissionUtils.java:85`，后三项相对于同一springmvc根。
- 旧维护记录：`PMS-struts/src/com/dp/plat/maintenance/action/MaintenanceAction.java:175`；`PMS-struts/config-ibaits/sql-map-maintenance-config.xml:232`。
- 当前权威约束：`AGENTS.md`服务经理区域负责人规则；`docs/design/07-authorization-design.md:66`两经理范围和概况；`docs/design/07-authorization-design.md:145`同项目多经理权限。
- 当前代码路径均位于本工作树pms-module-project及yudao-module-system；上述精确类名可定位。测试库原始证据包含各角色完整权限清单，未作为新权限定义。
