# system_user_company_department_scope 整表裁决及当前承接定义

现有20列全部复用，无新增表、列。字段、部门精确映射、主范围冲突及原资料排除均已裁决并完成本次正式迁移。内部、外部项目查看按具体项目授予，本表不会自动授予办事处全部项目可见范围。

正式运行2108571476367917057成功：9,492条来源关系承接到9,477条正式历史关系，15条同事实来源共享关系；1,329项按已批准规则排除。全部20列、历史审计和NULL操作者逐条核对，原41条范围、既有角色、菜单和用户角色关系保持原值。重复预览2108571647373885441为UNCHANGED 9,492／SKIPPED 1,329，全部无变化记录的前后字段完全一致。迁移Owner使用不可变审计参数避免框架填充历史NULL；普通业务写入的自动审计保持。5项SYSTEM事务与7项INT适配器、1项来源模板测试通过。当前证据覆盖本次已批准来源和范围，不表示项目承接或全部历史数据迁移完成。

| 既有字段 | 类型 | 承接及排除原因 |
|---|---|---|
| `id` | `bigint` | 由正式Owner生成新ID，原来源ID复用现有同步绑定；不把不同表同数值ID当同一身份。 |
| `tenant_id` | `bigint` | 当前授权迁移任务租户。 |
| `user_id` | `bigint` | 精确来源账号绑定，排除审计专用admin及不符合账号范围者。 |
| `company_id` | `bigint` | 来源公司明确转换后关联正式公司；fnd独有账号按用户指定001、002。 |
| `company_code` | `varchar(64)` | 复用01→001、02→002、03→003、04→004规则，未知码阻断。 |
| `company_name` | `varchar(128)` | 取已关联正式公司名称；不根据部门反推公司。 |
| `department_id` | `bigint` | 精确正式部门关联；28项补入system_dept、9项311202—311210精确转162102—162110已批准，不截断、不猜父级。没有来源部门时三项部门字段均NULL，仅保留明确公司角色资格。 |
| `department_code` | `varchar(64)` | 取关联部门正式编码，原码保留来源字段清单及绑定证据。 |
| `department_name` | `varchar(128)` | 取关联正式部门名称，旧名称差异完整列明。 |
| `scope_role` | `varchar(32)` | 复用已批准的目标角色业务编码；不生成新角色，不授予办事处全部项目访问权。 |
| `is_primary` | `bit(1)` | 自身custom3/dpNo为主、areapower/custom5非主；7项多来源冲突按fnd为主、t不同范围非主补缺。 |
| `effective_from` | `datetime(3)` | 关系原effectiveFrom；没有关系日期时用原账号create_time，用户已批准，不使用本次运行时间。 |
| `effective_to` | `datetime(3)` | 有来源结束时间直接承接；来源没有结束时间保持NULL，不推测。 |
| `status` | `tinyint` | 原1有效→0启用、0无效→1停用；原账户停用不使候选选择生效。原角色关系无独立状态，不伪造来源状态。 |
| `version` | `int unsigned` | 新增正式关系初始化0，后续沿既有乐观锁更新。 |
| `creator` | `varchar(64)` | 原创建人精确账号关联，来源无该字段或NULL保持NULL。 |
| `create_time` | `datetime(3)` | 原关系createTime；没有关系日期时用原账号创建时间，用户已批准。 |
| `updater` | `varchar(64)` | 原更新人精确账号关联，来源无该字段或NULL保持NULL。 |
| `update_time` | `datetime(3)` | 原关系updateTime；NULL用本条原createTime，用户已批准；来源原NULL留核对证据，不使用当前时间。 |
| `deleted` | `bit(1)` | 新增承接关系FALSE；不删除既有或来源缺失的授权事实。 |

## 范围关系完整原表字段

| 原字段 | 类型 | 承接及未映射原因 |
|---|---|---|
| `t_user_role.id` | `int(11)` | 既有来源身份及system_user_role迁移绑定；作为范围公司/角色关联依据，不另加来源列。 |
| `t_user_role.user_id` | `int(11)` | 原t_user外键→精确DPPMS_USERS绑定→正式user_id；不根据冗余姓名合并。 |
| `t_user_role.role_id` | `int(11)` | 原t_role外键→已裁决字符串角色编码→scope_role及system_user_role；先迁入角色关系，再承接范围。 |
| `t_user_role.comp_id` | `int(11)` | 原t_company外键，读取其compCode后按01→001等规则关联正式company_id/code/name；不得把数值ID当公司码。5条NULL公司仅角色关系，不授予公司范围。 |
| `fnd_user_power.id` | `int(11)` | 既有来源身份绑定；一条关系按明确公司、部门和已关联角色展开，派生键确保重放，不新增来源ID列。 |
| `fnd_user_power.fndUserId` | `int(11)` | 账号关联以原外键为准，精确用户绑定→正式user_id；第470条不按冗余tbpublic关联。 |
| `fnd_user_power.username` | `varchar(25)` | 冗余核对属性，不作为关联主键；来源差异列明，账号username由fnd_user_info承接。 |
| `fnd_user_power.areapower` | `varchar(4096)` | 完整非空集合逐项→正式department_id/code/name，is_primary=false；109条选中账号空集合经用户明确不迁入，无附加授权事实。 |
| `fnd_user_power.createTime` | `datetime` | 非空范围→create_time；空范围109条用户明确不迁入，并逐字段列出排除原因。 |
| `fnd_user_power.createBy` | `varchar(25)` | 非空范围经用户来源绑定→creator；空范围109条用户明确不迁入。 |
| `fnd_user_power.updateTime` | `datetime` | 非空范围→update_time；原NULL按各自createTime初始化；空范围109条用户明确不迁入（其中42条有值、67条NULL）。 |
| `fnd_user_power.updateBy` | `varchar(25)` | 非空范围经用户来源绑定→updater；原NULL保持；空范围109条用户明确不迁入。 |
| `fnd_user_power.effectiveFrom` | `datetime` | 非空范围→effective_from；空范围109条用户明确不迁入。 |
| `fnd_user_power.effectiveTo` | `datetime` | 非空范围→effective_to，原NULL保持；空范围109条用户明确不迁入，本批结束日期均NULL。 |

## 关联账号原字段完整核对

实际同步模板读取5个原对象的72个非凭据原列，并核对20个派生引用字段。下表列出关联账号的每个原字段；业务承接与排除以[system_users整表裁决](2026-10-09-dppms-system-users-table-decision.md)为准，不以范围快照替代账号业务承接。旧密码两列不读取、不迁入。

| 原表 | 全部非凭据原字段 |
|---|---|
| `t_user` | `user_id`, `user_name`, `create_by`, `create_time`, `update_by`, `update_time`, `status`, `needChangePwd`, `loginErrorCount`, `isSysUser`, `userCustom1`, `userCustom2`, `userCustom3`, `userCustom4`, `userCustom5` |
| `t_user_info` | `id`, `workNo`, `realName`, `eName`, `compID`, `depID`, `jobID`, `reportTo`, `wfreportTo`, `empStatus`, `jobStatus`, `empType`, `sex`, `birthday`, `email`, `mobile`, `telphone`, `avatar`, `remark`, `state`, `user_id`, `custom1`, `custom2`, `custom3`, `custom4`, `custom5` |
| `fnd_user_info` | `id`, `username`, `email`, `dpNo`, `realName`, `roleIds`, `isemail`, `status`, `defaultPage`, `pwdoverdue`, `createBy`, `createTime`, `updateBy`, `updateTime`, `effectiveFrom`, `effectiveTo`, `customInfo` |

## 已批准范围和排除

- 公司码统一01→001、02→002、03→003、04→004，未知阻断；fnd独有账号限定001、002，跨来源账号依据明确t_company关系，不从部门反推公司。
- 自身范围为主、附加范围非主；7项冲突按fnd为主、t补缺。同一办事处在自身和附加来源出现时保留各自业务属性及审计，完全同值的历史事实可复用同一正式关系。
- konglinghui_supp全部范围不迁入：原角色缺失且用户明确排除，账号资料仍迁入。
- 109条空areapower声明的全部原字段不迁入：用户裁决“空范围没有附加授权事实”；不写custom_info、不以空集合生成公司/区域授权。
- 孤立t_user_info 1000186/1000188/1000189不迁入：无账号外键且不在已批准账号范围；其他未来孤立资料仍待关联。
- z04214、w04218、y04223、w04261、z04264的原NULL公司仅迁入system_user_role，不新增范围。
- 不符合首字母+工号、*_ss、*_supp原登录名的资料按已定范围排除；旧admin仅审计关联当前admin，不迁入资料、角色或范围。

## 部门关联

28项补入既有system_dept、9项311202—311210精确转162102—162110，父项、启停、历史时间及原字段排除均已裁决，见[system_dept整表清单](2026-10-09-dppms-system-dept-table-decision.md)。没有启用截断、父级替换或全区域授权；999999只代表通用部门。

## 验证边界

范围Owner5项隔离事务检查、适配器7项及模板1项检查通过。实际模板只读提取3275条记录，整批展开9492条承接候选，其中完全同值历史9477组、公司角色资格136条、主范围2886条；原字段无遗漏、来源键无重复，展开后的派生键唯一。

这次展开模拟已迁入用户绑定和28项待补部门ID，Owner未执行；正式授权表零写入，不代表真实迁移、并发运行或浏览器验收通过。
