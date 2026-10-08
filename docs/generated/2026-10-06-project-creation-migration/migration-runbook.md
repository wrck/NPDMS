# Struts 项目只读档案迁移说明

用户 2026-10-06 已明确“先迁只读历史档案”。此工具是本机离线档案路径，不向 NPDMS 运行项目表写入、不调用创建接口、不生成阶段、审批、门禁、成员权限或模板历史。没有生产数据库连接代码；本次仅运行仓库内合成数据。

## 输入与字段映射

输入 UTF-8 JSONL，每行一个完整项目 bundle。必须声明 tenantId、sourceSystem=PMS_STRUTS、sourcePk，并包含 header、members、groups、contracts、products，空关系用 []。sourcePk 是旧项目主键，不能用合同号/项目名称去重。每条子关系必须携带旧 id；这仅用于档案关系去重，不是创建界面要求用户选择的订单行 ID。

| bundle | 旧来源及关联证据 | 档案规则 |
|---|---|---|
| header | pm_project_header.projectId；Project.java 与 sql-map-project-config.xml:1075 | 全部白名单字段按原值保存。projectState、isback、column001~014 不改成新值域；projectStartTime 是指派开始时间，不取订单日期替代 |
| members | pm_project_member.projectId -> header.projectId；SQL:1272 | memberRole/memberCode/memberName、有效期原值保留；不授予新系统权限、不猜人员 ID。电话邮箱不纳入本次迁移 |
| groups | pm_project_group_relationship.projectCode -> header.projectCode；SQL:1266 | 保留 projectGroupCode/smsProjectCode；不映射父子树或项目组合 |
| contracts | pm_project_contract.projectGroupCode -> groups.projectGroupCode；SQL:1261 | 可保留同项目多合同、同组多项目的原始关系；不按合同合并项目 |
| products | pm_project_product_line.projectId -> header.projectId，contractNo -> contracts.contractNo；旧插入语句 | 产品编码、数量和订单/行号原样保留；不重算分配量、不转成新交付范围 |

字段白名单与 TABLES 定位由 scripts/legacy_project_archive.py 维护；合成输入是 scripts/tests/fixtures/legacy_project_archive.synthetic.jsonl。导出方应从只读快照产生每行完整关系集合；必须在获授权的离线环境完成真实导出，不能让本工具直接连生产。未支持字段会整条隔离，不能静默丢字段；customInfo 等任意 JSON 不收录，需单独审核字段映射。未知旧状态允许原值归档。

## 本机使用

默认 dry-run：读取已有档案副本到内存，或创建内存新库，绝不修改指定文件。

```powershell
python scripts/legacy_project_archive.py import --archive "$env:TEMP\npdms-history.sqlite" --tenant 1 --input scripts/tests/fixtures/legacy_project_archive.synthetic.jsonl
```

显式 `--apply` 才落本机 SQLite 档案；仅对已审核的离线导出使用。合成样本包含预期异常，退出码 2 表示有隔离记录，不代表事务崩溃。0 表示无隔离记录；结构/文件级异常非零失败。

```powershell
python scripts/legacy_project_archive.py import --archive "$env:TEMP\npdms-history.sqlite" --tenant 1 --input scripts/tests/fixtures/legacy_project_archive.synthetic.jsonl --apply
python scripts/legacy_project_archive.py list --archive "$env:TEMP\npdms-history.sqlite" --tenant 1
python scripts/legacy_project_archive.py withdraw --archive "$env:TEMP\npdms-history.sqlite" --tenant 1 --batch <报告中的batchId> --reason "本批校验撤回" --apply
```

## 幂等、校验、隔离与撤回

- 来源身份为 tenantId + sourceSystem + pm_project_header + sourcePk；归档内容规范化后 SHA-256。相同身份/内容重放，内容不同隔离，绝不覆盖旧内容。子关系排序不改变摘要。
- 批次身份包含租户、来源系统及输入文件摘要。同一批次重复调用返回原报告。整批事务保证数据库/文件级故障不会留下半批；业务异常只隔离该项目 bundle。
- 校验租户/来源、头主键、子关系主键重复、人员/产品所属项目、组所属项目、合同所属组和产品所属合同。报告逐行给出 issue code 和数量核对；异常内容只存原行摘要与错误码，不保存未知敏感字段。
- archive_project/batch/item/withdrawal 有 UPDATE/DELETE 拒绝触发器。撤回仅追加 archive_withdrawal 记录，使该批次不再出现在 active_archive_project 只读视图中，原证据保持不变。同一档案仍被其他未撤回批次引用时继续可见。
- 撤回后原批次重放不会自动重新激活。无新运行项目产生，因此没有需要删除的新项目/状态机记录。SQLite 文件的操作系统访问控制由执行环境负责；这不是 NPDMS 在线历史档案页面或线上授权替代物。

## 本轮验证

`python -m unittest scripts.tests.test_legacy_project_archive -v`：9 项通过。合成 dry-run：5 条输入，2 条保留、1 条重放、2 条隔离，reconciled=true；未创建任何运行项目。具体报告见同目录 archive-dry-run.json 与 archive-tests.log。真实导出、生产迁移及历史流程激活均未执行。


## 身份与版本失败保护

sourcePk 必须是非空字符串，header.projectId 必须存在且与之对应；空白和 None/null/undefined/NaN 等占位身份拒绝，关系身份不得为空或占位值。无效项目仅记录行号、摘要和问题码后隔离，同批有效项目继续处理。既有档案若不是单一 v1 schema，必须在任何初始化写入前拒绝；不得为了升级或重试写入新版本标记。2026-10-06 回归已验证 apply/dry-run 对未知版本文件均保持字节不变，v1 正常初始化/重放/撤回不受影响。
