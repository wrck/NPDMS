# 平台身份、公司、部门与项目授权迁移规则

> 文档状态：已确认规则基线
> 基线日期：2026-08-04
> 适用范围：旧`dppms`中的EHR、`t_*`、`fnd_*`身份权限数据迁移，以及新平台内部人员、外部人员和项目转派授权
> 数据边界：旧库只允许`SELECT/SHOW`只读访问；新平台使用独立MySQL 8.x数据库；禁止跨库SQL

## 1. 文档目的

本文记录已经确认的身份、公司、部门、账号、菜单、数据范围和外部人员项目授权规则，作为后续物理表设计、迁移脚本、权限接口、前端工作台和验收测试的共同依据。

身份权限对象与数据元、旧库表和当前实现DO的三方比对见[`legacy-data-element-business-object-mapping.md`](legacy-data-element-business-object-mapping.md)。

本文区分三类内容：

- **已确认规则**：开发和迁移不得自行改变。
- **旧库证据**：来自当前旧库只读统计及旧代码查询路径。
- **待形成明细**：模型方向已经确认，但仍需补齐逐项映射表，不得凭字段名猜测。

## 2. 已确认的公司、部门与身份边界

### 2.1 租户、公司和部门

1. V1按单租户运行，同时保留平台已有`tenant_id`技术字段和多租户扩展能力。
2. 公司是统一业务主体，目标表、字段、DTO和接口使用`company_*`，不再创建等义的`org_*`或`organization*`主档。
3. 所属公司和租户是不同边界：租户是技术隔离边界，公司是业务归属和授权边界。
4. 部门基础数据全平台共享，部门编码使用同一套编码体系。
5. 不建立可从部门反推所属公司的全局“公司—部门”主数据从属关系；但项目归属、用户任职和权限范围中的公司—部门组合必须在同一业务关系行明确保存，不能拆开后猜配对。
6. 内部关联使用目标表ID；工号、公司编码、部门编码和岗位编码作为正式业务编码，用于集成、检索、导入导出和问题对账。
7. 旧来源中的`org_*`、`compId`、`compCode`、`corporationCode`等原字段名保持原样进入血缘和原始载荷，目标字段统一映射到`company_*`或明确的`department_*`。

### 2.2 系统账号与员工目录

1. 系统账号是平台登录、权限、项目成员、任务、审批和审计的主体，所有正式业务关系使用`system_users.id`。
2. EHR员工是企业人员全集，只作为只读集成人员目录，服务于查找公司或部门内人员和创建内部系统账号时按工号带入信息。
3. 不在权限和项目业务中引入独立`system_employee`主档。
4. `system_users.employee_no`是正式字段：内部账号必须保存工号；外部人员和服务账号允许为空。
5. 用户主部门使用`system_users.dept_id`关联`system_dept.id`；`system_dept.code`必须保存共享部门编码。

### 2.3 账号类型

`system_users`必须能够区分以下账号类型：

| 账号类型 | 含义 | 工号 | 人员来源 |
| --- | --- | --- | --- |
| `INTERNAL` | 企业内部系统使用人员 | 必填 | EHR及旧内部账号 |
| `EXTERNAL` | 外协、驻场、服务商等外部实施人员 | 为空 | `fnd_user_info`等外部账号源 |
| `SERVICE` | 系统集成或自动化账号 | 为空 | 受控创建 |

没有匹配到EHR或`t_user`的`fnd_user_info`账号已经确认是外部人员账号，不得作为身份脏数据丢弃，也不得伪造员工工号。

## 3. 旧权限数据证据

### 3.1 `t_*`权限体系

当前旧库统计：

- `t_company`3行，不能与EHR公司ID或公司编码直接等值复用。
- `t_user`341行，其中209个有效、127个无效、5个锁定。
- `t_user_info`345行；`workNo`作为旧账号工号候选，但只有193行能够匹配当前EHR，不能把该表当成员工权威主档。
- `t_user_role`829行，存在少量空公司、异常公司和跨公司角色授权。
- `t_menu`39行、`t_permission`119行、`t_role_menu`126行、`t_role_permission`637行。
- `t_user_info.custom3`是办事处/部门编码语义，不能简单解释为用户当前主部门。
- `t_user_info.custom5`可以规范为用户—部门数据范围。
- `t_user_info.custom4`包含项目类型范围语义。

### 3.2 `fnd_*`权限体系

当前旧库统计：

| 表 | 当前行数 | 已确认语义 |
| --- | ---: | --- |
| `fnd_company` | 3 | 旧公司字典；不能据此推导用户公司 |
| `fnd_department` | 202 | 旧部门字典和编码别名来源 |
| `fnd_menus` | 23 | 旧菜单定义 |
| `fnd_roles` | 18 | 旧业务角色定义 |
| `fnd_role_menus` | 21 | 当前未启用，迁移忽略 |
| `fnd_user_info` | 880 | 外部账号及少量与`t_user`重合的账号 |
| `fnd_user_menus` | 5,401 | 用户实际菜单准入记录 |
| `fnd_user_power` | 880 | 用户部门/区域范围字符串 |

补充证据：

- `fnd_user_info`有878个不同用户名和2组重复用户名；重复组当前均为停用记录，迁移仍需生成冲突清单。
- 416个`fnd_user_info`账号当前有效，其中402个用户名不在`t_user`中；这些未匹配账号已确认属于外部人员。
- `fnd_user_info.dpNo`是部门编码，不是工号；不得写入`system_users.employee_no`。
- `fnd_user_info`没有公司外键，不能通过`fnd_company`或共享部门编码推导所属公司。
- `fnd_user_info.password`是旧MD5形态凭据，不迁入新平台；外部账号必须执行密码重置或统一认证激活。
- 5,401条`fnd_user_menus`可以归纳为44种不同菜单组合。
- `fnd_role_menus`已经确认未启用，不迁移其`menuPower`，也不据此推导按钮权限。
- `fnd_roles`和`fnd_user_info.roleIds`仍需迁移，因为旧业务代码直接使用角色判断项目经理、服务经理等业务身份。
- `customInfo`中的项目查询限制必须转换为明确的能力限制，不能丢失或转成正向授权。

## 4. 权限源迁移规则

### 4.1 公司、部门与编码映射

1. EHR公司和部门作为当前主数据优先来源。
2. `t_company`、`fnd_company`、`fnd_department`只提供旧编码、旧名称和迁移别名证据。
3. 任何来源ID都必须通过`来源系统 + 对象类型 + 外部主键`映射到目标ID，不得假设旧表之间ID相同。
4. 相同部门编码名称冲突时，以当前EHR部门名称为正式名称，旧名称进入迁移映射或问题记录。
5. 旧编码无法直接命中当前部门但存在已确认编码关系时，映射到正式`system_dept.id`，不额外创建重复部门。

目标基础平台至少补充以下公司与用户业务上下文能力：

```text
system_company
- id
- tenant_id
- company_code
- company_name
- status
- version

system_user_company_department_scope
- id
- tenant_id
- user_id
- company_id
- company_code
- company_name
- department_id          可空；公司级范围不限定部门
- department_code        可空；与company_*共同表达同一业务上下文
- department_name        可空
- scope_role
- is_primary
- effective_from
- effective_to
- status
- version
```

`system_user_company_department_scope`保存用户在特定公司—部门组合下的任职或权限上下文。公司与部门主数据仍各自独立；关系行同时保存二者，是为了保留实际业务配对，不代表建立全局部门从属公司关系。目标物理字段和接口不得再使用`org_id/org_code/org_name`。

### 4.2 账号合并

1. 与`t_user`明确为同一账号的`fnd_user_info`记录合并到同一`system_users.id`，权限来源分别保留迁移血缘。
2. `fnd_user_info`独有账号按`EXTERNAL`创建，不要求EHR匹配。
3. 内部账号的姓名、部门、公司、邮箱等人员基础信息以当前EHR为优先来源；旧账号状态、登录名和权限以相应权限源为依据。
4. 外部账号保留用户名、姓名、邮箱和启停状态；`employee_no`为空。
5. 账号状态冲突、重复用户名和一对多身份匹配必须生成迁移问题，禁止自动合并到错误账号。

### 4.3 菜单和角色

1. `t_role/t_role_menu/t_role_permission`按语义映射到新平台角色和权限编码，不能复用旧数字ID。
2. `fnd_roles/roleIds`迁移业务身份，不使用`fnd_role_menus`生成权限。
3. `fnd_user_menus`是`fnd`账号的菜单准入依据。迁移时将旧`menuCode`映射为新平台稳定的入口或查看能力编码；它本身不能证明新增、修改、删除、审批或导出权限。
4. 旧菜单组合可以作为初始迁移权限模板的输入；不得永久依赖按用户保存的逗号或JSON菜单集合。
5. 旧数据不能提供可靠按钮和字段权限时，未明确能力默认拒绝，不根据角色名称猜测扩大权限。

## 5. `fnd_user_power`处理规则

### 5.1 旧系统实际生效范围

旧系统的有效部门编码集合为：

```text
规范化(fnd_user_power.areapower)
并集 {fnd_user_info.dpNo}
并集 市场部门与用服部门对应编码
```

具体规则：

1. `areapower`按逗号拆分、去空值并去重。
2. 用户自己的非空`dpNo`必须加入范围。
3. 空`areapower`不代表全量权限，只表示至少保留自己的`dpNo`。
4. `areapower`和`dpNo`都为空时，不产生部门范围。
5. 旧查询层的`-1`只是空范围临时标记，不迁入新库。
6. 旧逻辑补充市场与用服对应办事处，但不自动展开整个下级部门树。

当前数据中，以下统计均基于迁移前的全部880个`fnd_user_info`源账号，尚未执行目标账号合并；关系数按`源用户ID + 原始部门编码`去重，且发生在市场/用服别名补充之前：

- `areapower`规范化后有1,501个用户—部门编码关系。
- 115个用户的`areapower`为空。
- 56个用户的自身`dpNo`尚未包含在`areapower`中。
- 合并自身部门后形成1,557个直接范围关系；后续别名映射和目标账号合并可能再次去重，不能直接作为最终导入行数。
- 9个未直接出现在当前部门主档的`311202`至`311210`编码，均能按当前市场/用服关系映射到有效的`162102`至`162110`，不能直接作为脏数据删除。

### 5.2 内部人员与外部人员分流

| 用户类型 | `fnd_user_power`目标语义 | 是否直接形成项目查看权 |
| --- | --- | --- |
| `INTERNAL` | 部门数据范围，可迁入项目访问范围的`DEPARTMENT`类型 | 是，仍需同时满足功能权限 |
| `EXTERNAL` | 可服务、可被转派的部门范围 | 否，必须存在有效项目转派 |

外部人员的范围进入正式业务关系`pms_user_service_scope`，用于转派候选人筛选和覆盖区域统计，不参与项目查看权限的直接判定。

建议字段：

```text
pms_user_service_scope
- id
- user_id
- department_id
- source_type       FND_DP_NO / FND_AREA_POWER / DERIVED_DEPARTMENT_MAPPING / MANUAL
- status
- effective_from
- effective_to
```

## 6. 外部人员项目转派授权

### 6.1 授权原则

1. 外部人员默认没有公司级、部门级或区域级项目查看权限。
2. 外部人员只有在存在有效项目转派时才能查看对应项目。
3. 转派默认绑定一个正式项目节点；正式子项目具有独立负责人、计划、状态和验收时，授权只落到该子项目。
4. 默认不继承父项目、兄弟项目或整个项目组合权限。
5. 只有显式选择`PROJECT_SUBTREE`时，才允许访问被转派项目及其后代。
6. 转派撤销、到期、人员停用或账号停用后必须立即失效。

### 6.2 转派模型

`pms_project_assignment`保存转派事实和历史：

```text
pms_project_assignment
- id
- project_id
- assignee_user_id
- assignee_type          INTERNAL / EXTERNAL
- assignment_role        PROJECT_MANAGER / IMPLEMENTER / SUPPORT
- assignment_scope       PROJECT / PROJECT_SUBTREE
- profile_version_id
- source_type            MANUAL / MIGRATION / RULE
- assigned_by
- assigned_at
- effective_from
- effective_to
- status                 PENDING / ACTIVE / REVOKED / EXPIRED
- previous_assignment_id
- transfer_reason
- version
```

现有`pms_project_member`保存当前有效项目成员关系；`pms_project_assignment`保存可追溯的转派链。转派生效、撤销和到期时，两者必须在同一事务中同步。外部人员的成员关系必须由当前有效转派背书，不能仅凭单独存在的`pms_project_member`获得访问权。

### 6.3 迁移初始化

1. 旧项目中能够明确定位到具体负责人的项目责任关系，生成`source_type=MIGRATION`的项目转派记录。
2. 只有`fnd_user_power`但无法定位具体项目的外部账号，不得自动获得该部门全部项目。
3. 此类账号只迁移服务范围，并进入待转派清单，由业务人员选择具体项目。

## 7. 菜单、操作和字段级权限

### 7.1 六项授权条件

外部人员的最终授权必须满足：

```text
平台账号功能权限
∩ 有效项目转派
∩ 转派权限模板
∩ 数据对被转派项目的归属关系
∩ 字段访问规则
∩ 必要的敏感数据临时审批
```

任一层不满足即拒绝。前端隐藏菜单或字段不构成安全控制，所有读取、导出和写入必须由服务端再次校验。

### 7.2 版本化权限模板

权限模板必须版本化，项目转派引用确定的模板版本：

```text
pms_access_profile
- id
- code
- name
- subject_type
- status
- current_version_id

pms_access_profile_version
- id
- profile_id
- version_no
- status
- effective_from
- effective_to

pms_access_profile_capability
- id
- profile_version_id
- capability_code
- effect                 ALLOW / DENY

pms_access_profile_field_rule
- id
- profile_version_id
- resource_type
- field_code
- read_level             HIDDEN / MASKED / FULL
- write_level            NONE / EDIT
- mask_policy
```

能力编码使用稳定业务语义，例如项目查看、实施订单行查看、发货查看、SN查看、任务更新和交付物上传；不得把数据库自增菜单ID当作跨环境稳定契约。

### 7.3 菜单显示与项目内权限

1. 外部账号的全局角色只授予外部实施工作台入口。
2. 用户只要存在一个有效转派即可显示相关工作台菜单；菜单显示可以取有效转派能力的并集。
3. 进入具体项目后必须按`projectId + assignment + profile_version`重新计算能力，不能把菜单并集当成全部项目权限。
4. 同一外部人员可以在项目A拥有实施权限，在项目B只有只读权限。

### 7.4 字段权限

1. 字段规则使用稳定业务字段编码，不直接绑定数据库列名。
2. 读取支持`HIDDEN/MASKED/FULL`；写入支持`NONE/EDIT`。
3. 服务端DTO组装、导出和详情接口必须执行相同字段策略。
4. 写接口必须校验允许修改的字段白名单，禁止通过构造请求更新无权字段。
5. 单次转派可以收窄模板权限；扩大权限必须选择更高权限模板并记录审批或审计，不允许随意逐字段放大。

具体菜单—能力、资源—字段矩阵尚需依据新平台页面、接口和敏感数据分类逐项确认；在矩阵批准前，未列出的菜单、操作和字段默认拒绝。

## 8. 项目、订单行、发货和SN的数据边界

外部实施人员的数据链必须按以下路径过滤：

```text
外部人员
  -> 有效项目转派
  -> 正式项目或子项目
  -> 项目实施订单行范围
  -> ERP订单行
  -> 发货数量和设备SN事件
```

已确认规则：

1. 同一订单拆给多个子项目时，外部人员只能查看归属于其项目的订单行。
2. 订单头只返回实施所需摘要，不能借订单号返回其他项目订单行。
3. 发货数量和SN通过项目订单行范围继续过滤。
4. 合同金额、回款、毛利和其他项目客户敏感信息默认不向外部实施人员开放。
5. 客户联系人等必要敏感字段按字段规则脱敏；需要完整值时复用受控临时审批能力。

## 9. 查询与安全约束

1. 项目列表在数据库层使用`EXISTS`/索引条件过滤，不加载全量项目后在内存裁剪。内部人员可以按有效成员关系判断；外部人员必须同时存在有效转派及其派生的有效成员关系。
2. 权限模板按版本缓存；转派、模板、用户状态变化后必须精确失效缓存。
3. 项目转派至少建立`tenant_id + assignee_user_id + status + project_id`查询索引。
4. 项目成员至少建立`tenant_id + user_id + status + project_id`查询索引。
5. 项目订单行范围、发货和SN查询必须沿项目范围索引下钻，不按合同号或订单号猜测项目归属。
6. 权限拒绝、字段脱敏、导出、转派、撤销和模板变更必须记录安全审计。
7. 内部人员按共享部门获得项目范围时，还必须命中同一条用户—公司—部门上下文：项目所属公司与用户显式公司关系一致，项目部门与用户范围一致；部门范围不能跨公司单独放行。

## 10. 仍待形成明细的事项

以下内容尚未确认到可直接实施的明细，不得写入迁移脚本的默认推断：

1. 外部账号接入哪个公司，以及是否允许一个外部账号服务多个公司。
2. 旧`fnd_menus.menuCode`到新平台能力编码的逐项映射。
3. 外部实施、外部只读、外部支持等标准模板的菜单、操作和字段矩阵。
4. 历史项目责任字段到`pms_project_assignment`的逐字段来源优先级和冲突规则。
5. 单次转派扩大权限时采用的审批流程和审批角色。
6. 无有效EHR工号的`t_user`、与`t_user`重合但不匹配EHR的`fnd_user_info`以及旧服务账号的分类规则。
7. 同一项目存在多个有效转派或模板时的`ALLOW/DENY`、字段可见性和编辑权限合并规则。
8. `PROJECT_SUBTREE`是否动态包含转派生效后新增的后代项目。
9. 单次转派收窄模板权限的持久化结构，以及敏感字段临时审批的范围、期限、撤销和优先级。

## 11. 不可违反的约束

- 不把`fnd_user_info.dpNo`当成工号。
- 不把外部人员账号当成无法匹配的员工脏数据。
- 不迁移旧MD5密码。
- 不迁移或推断`fnd_role_menus.menuPower`。
- 不从共享部门编码推导所属公司。
- 不把外部人员的`fnd_user_power`直接转换为部门级项目查看权限。
- 不因能够查看订单头而返回其他项目的订单行、发货或SN。
- 不依赖前端隐藏实现菜单、按钮或字段安全。
- 不对未明确的按钮、字段和敏感数据默认放行。
