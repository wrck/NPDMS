# 迁入成员用户名绑定及授权确认执行记录

执行日期：2026-10-24（北京时间）。用户明确批准：137,273条精确匹配同租户启用账号、内部角色的成员记录，其余保留。环境为172.17.90.178:13306/npdms，server_uuid=1c105560-cd38-11f1-babc-923e05e6f067。

## 实际结果

实际提交137,049条；224条因既有成员区间约束冲突保留原状。本记录不把137,273条写成全部执行完成。

| 角色 | 批准候选 | 实际更新 | 冲突保留 |
|---|---:|---:|---:|
| SERVICE_MANAGER | 100975 | 100945 | 30 |
| PROJECT_MANAGER | 32478 | 32294 | 184 |
| SALES_REPRESENTATIVE | 3820 | 3810 | 10 |
| 合计 | 137273 | 137049 | 224 |

更新仅限：`user_id=精确匹配账号ID`、`authorization_confirmed=true`。匹配要求同租户、账号启用且未删除、用户名与member_code大小写及字节精确一致；不按姓名、手机号或相近编码猜配。其余账号、停用记录、未匹配记录及角色60均未确认。

原成员字段包括有效期、角色、主责类型、历史ID、审计时间、版本和内容均逐行SHA256核对未变；`update_time`显式保持原值，不覆盖历史审计元数据。本次授权变更的执行时间和前像记录于独立执行日志，不伪装为原始来源业务动作。

## 224条保留原因

涉及32个账号、107个项目，存在同一用户/项目/角色的区间重叠；其中15组、30条还会违反`uk_proj_member_role(tenant_id,project_id,user_id,member_role,effective_from)`。224已包含这30条，不重复相加。

既有Owner规则禁止相同用户、项目和角色的重叠区间。因此没有删除重复历史、改写有效期、任选一条授权、改角色或删除唯一约束。y06496候选没有被排除；z01010有10条冲突保留。

逐条冲突及原因见 `.run/permission-data-audit-20261024/member-confirmation/blocked-candidates.json`。需按原来源和业务区间另行裁决后才能处理，不能通过本次批量确认绕过。

## 执行与验证证据

执行脚本 `.run/permission-data-audit-20261024/confirm-members.py`：

1. 只读预览重新确认137273条候选，保存用户名、目标ID、区间及候选ID。
2. 排除冲突后锁定对应账号和成员，事务内再次核对候选集合与预览一致。
3. 保存全成员前像（ID、原user_id、原授权标记、其他全部字段SHA256），仅对137049个冻结ID执行更新。
4. 提交前逐行比较全部成员：所选记录仅两个字段改变；其余记录逐字段摘要及两个字段均不变。
5. 核对角色、角色菜单、用户角色、公司部门范围、项目主档、树版本、树路径及Flyway记录的行数和校验值未变，再提交。新数据库连接回读确认实际提交结果。

主要证据均在 `.run/permission-data-audit-20261024/member-confirmation/`：`execution-plan.json`、`before-members.json`、`after-members.json`、`before-protected-tables.json`、`verified-before-commit.json`、`committed.json`、`fresh-database-verification.json`。前后成员证据只包含原指针/授权标志及摘要，不导出成员联系方式或密码。

未迁入V515，未执行同步、改派项目经理、修改主责、变更菜单或系统角色、重启或部署服务。

## 两经理的实际成员状态

| 账号 | 角色 | 确认记录 | 涉及项目数 | 当前有效记录 |
|---|---|---:|---:|---:|
| y06496 | PROJECT_MANAGER | 123 | 123 | 123 |
| z01010 | SERVICE_MANAGER | 2678 | 2678 | 2678 |
| z01010 | PROJECT_MANAGER | 1146 | 889 | 690 |
| z01010 | SALES_REPRESENTATIVE | 1 | 1 | 1 |

各角色项目有交集，不能相加当作可见项目总数；数据库确认成员数也不等于浏览器验收通过。

## 真实浏览器验收与未解决事项

- 使用用户实际登录的服务经理z01010会话，直接打开项目51922（16200723010901N-0），项目名称及基本档案实际可读。页面仍提示“待指派项目经理”，因为本次未更改主责引用，成员确认不替代负责人指派。证据`authorized-project-visible.txt`。
- 范围外项目84571仍拒绝：详情及实例返回1014003000且无data；树范围返回1014024033且无data。证据`foreign-project-refused.json`。
- 服务经理项目列表刷新返回code=500“系统异常”，未记为通过。运行日志`.run/direct-test/latest-application.log`在2026-10-24 01:23:16记录`Handler dispatch failed: java.lang.StackOverflowError`，栈顶为`ObjectOutputStream`序列化。当前MyBatis配置使用JDK序列化SQL解析缓存，大范围查询与该异常有关；具体SQL/缓存调用点尚未完成代码定位，不凭此认定最终根因。未扩大堆栈、关闭权限/缓存校验或重启来掩盖异常。
- 项目经理y06496真实会话待用户切换后验证，未冒充该账号验收；未执行模拟登录或提取浏览器Token。

## 同步保护保持生效

137049条已更新成员均有BASE_P31（任务2112881209570852866）/MEMBER_HISTORY绑定。原绑定指纹没有修改，来源记录及原始快照没有修改。

现有P31MemberContactSyncTask的指纹包含userId和authorizationConfirmed；当前授权修正后，原任务重跑会按既有`EXISTING_MEMBER_PROTECTED`检测到目标变化，不能宣称幂等重跑已通过。本次没有执行重跑或降低保护。后续若要继续该历史同步，需单独设计来源历史与当前授权变更的承接规则，不能简单重写指纹或恢复未确认值。
