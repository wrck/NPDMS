# system_role 整表字段裁决材料

用户裁决：保留既有结构，由用户指定缺失字段处理。三列新增提案已撤回，默认首页和有效期来源字段已由用户明确裁决不迁入；原因记录为用户明确选择不迁入。当前没有结构、角色或权限写入。本表已核对t_role/fnd_roles共22原列、14与18条角色；32项映射、排序、CUSTOM/SELF及超级管理员例外均已明确批准，详见逐角色映射清单。

## 既有15列全部复用

| 既有字段 | 实库定义 | 本轮处理建议 |
|---|---|---|
| `id` | `bigint` | 复用正式角色ID；来源主键通过既有迁移/同步绑定，不直接套用旧ID。 |
| `name` | `varchar(30)` | 依32项已批准清单复用同义角色；独立新角色以t定义为主、fnd补缺，关联现存角色不改其名称或权限。 |
| `code` | `varchar(100)` | t_role使用原字符串值；fnd按用户裁决将常量语义转换为lowerCamel业务编码，不直接使用ID或常量名。明确别名按32项角色映射关联既有角色。 |
| `sort` | `int` | 已批准t_role用100-priority，独立fnd角色用100+原ID保持显示顺序；ID只参与排序，不用于角色编码。 |
| `data_scope` | `tinyint` | 已批准新角色SELF=5，不自动授予菜单按钮；内外用户仅按具体项目授权，公司/部门范围只供资格候选。现存角色全部权限保留，平台超级管理员按用户明确例外关联。 |
| `data_scope_dept_ids` | `varchar(500)` | 复用现有字段；不将不同用户的公司/部门范围合并成全角色部门集合。新增角色SELF不配置泛化部门集合。 |
| `status` | `tinyint` | 只迁入有效来源角色（原1→正式启用0）；停用fnd15由用户明确裁决不迁入，不激活当前停用角色。 |
| `type` | `tinyint` | 新增普通角色CUSTOM=2；t_role1按用户明确裁决关联平台super_admin并授予相应权限，其余普通角色不推定超级管理员。 |
| `remark` | `varchar(500)` | 复用remark；同一角色的多来源冲突列明，不默认覆盖。 |
| `creator` | `varchar(64)` | 复用正式用户关联；身份迁移后回填，不伪造来源操作者。 |
| `create_time` | `datetime` | 原历史创建时间。 |
| `updater` | `varchar(64)` | 复用正式用户关联；身份迁移后回填，不伪造来源操作者。 |
| `update_time` | `datetime` | 原更新时间；NULL按用户裁决用原创建时间初始化，原NULL列入来源证据，不用运行时间。 |
| `deleted` | `bit(1)` | 复用既有逻辑删除字段，不从原停用推导删除。 |
| `tenant_id` | `bigint` | 复用租户边界。 |

## 已撤回结构提案；对应角色首页和有效期已裁决不迁入

| 撤回字段 | 原提案定义（不执行） | 对应来源业务内容 |
|---|---|---|
| `default_page` | `VARCHAR(255) NULL` | 角色默认入口；承接t_role.home_page和未合并fnd角色defaultPage，保存旧值，不将旧URL自动激活为新平台路由。 |
| `effective_from` | `DATETIME(3) NULL` | 来源角色有效期开始；t_role无对应值，不伪造开始日期。 |
| `effective_to` | `DATETIME(3) NULL` | 来源角色有效期结束；NULL保留开放结束期。 |

## 原22列完整审查

| 原表.字段 | 正式承接点或候选 | 处理与未匹配原因 |
|---|---|---|
| `t_role.role_id` | system_role.id 的来源绑定 | 14角色独立来源ID，不能和fnd_roles同数字ID归并。 |
| `t_role.role_name` | system_role.code | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 |
| `t_role.role_name_zn` | system_role.name | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 |
| `t_role.home_page` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 |
| `t_role.priority` | system_role.sort（已批准100-priority） | 旧role查询按priority降序，且决定最高角色首页；现sort用于排序，须明确转换与既有角色冲突，不能直接当作同义字段。 |
| `t_role.status` | system_users.status / system_role.status | 先区分账号、角色；原1有效→0启用，原0无效→1停用。t_user原2为锁定，不能丢失锁定含义。 |
| `t_role.create_by` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 |
| `t_role.create_time` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 |
| `t_role.update_by` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 |
| `t_role.update_time` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 |
| `t_role.remark` | system_role.remark | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 |
| `fnd_roles.id` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 |
| `fnd_roles.roleName` | system_role.name / 明确的业务角色语义映射 | 与t_role中文完全同名仅普通用户、项目管理员、项目经理3项；同名不证明授权范围一致，其他15项不静默丢弃。 |
| `fnd_roles.defaultPage` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 |
| `fnd_roles.status` | system_users.status / system_role.status | 先区分账号、角色；原1有效→0启用，原0无效→1停用。t_user原2为锁定，不能丢失锁定含义。 |
| `fnd_roles.effectiveFrom` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 |
| `fnd_roles.effectiveTo` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 |
| `fnd_roles.createBy` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 |
| `fnd_roles.createTime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 |
| `fnd_roles.updateBy` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 |
| `fnd_roles.updateTime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 |
| `fnd_roles.roleRemark` | system_role.remark | 角色同义合并时差异列为冲突，不能覆盖t_role主定义。 |

## 另待角色关系裁决

- fnd的18角色与t_role及现有平台角色逐项语义关联。
- 同一角色合并后默认入口、有效期、备注与审计差异必须另行裁决，不把同名合并视为丢弃差异的许可。
- 原fnd角色15名称编码异常；原值不擅自解码覆盖，正式显示名待确认。

既有RoleServiceImpl.createRole默认data_scope=ALL；迁移不能未经明确批准套用该默认值。本表字段批准不等于批准任意角色合并、菜单按钮授权、管理员提升或流程激活。全部写入经SYSTEM Owner和现有迁移/同步链路。

## 已批准的历史空时间转换及实施进展

用户批准：原更新时间为NULL的角色仅用原创建时间初始化正式更新时间，原NULL保留来源证据；原空创建/更新人仍为空。SYSTEM业务接口和DPPMS_ROLES同步适配器已实现，未新增角色表或列，不调用默认ALL的普通创建接口。

直接执行Java模板生成的只读原表查询，31条启用来源角色和22原字段全部取回，无缺列；尚未执行目标角色或权限写入。角色绑定及原操作者必须依赖正式账号迁移，不按同名猜配。真实运行和页面验收尚待数据库基线及后端切换。
