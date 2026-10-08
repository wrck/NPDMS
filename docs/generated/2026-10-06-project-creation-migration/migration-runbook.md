# 本机历史档案与试迁说明

默认历史迁移先进入只读档案。十项运行项目试迁另有用户授权，但必须满足当前身份、来源链与服务端权限；本文件不授予新权限。

## 离线归档

`scripts/legacy_project_archive.py` 不含网络或数据库客户端，默认在内存中 dry-run。输入为 UTF-8 JSONL，每行明确 tenantId、sourceSystem=PMS_STRUTS、sourcePk，以及 header/members/groups/contracts/products。字段白名单由脚本 FIELDS 定义，其他字段拒绝，不默默丢弃。member 电话、邮箱、任意 customInfo 和凭据不在白名单内。

| 部分 | 映射及保护 |
|---|---|
| header | 保留旧 projectId、projectCode、状态、column001~014、组织及日期原值；来源 pm_project 的头可映射为逻辑 header |
| members | 按 projectId 关联，保留旧人员标识、角色及有效期；不创建新用户或授权 |
| groups | 按 projectCode 关联，保留 group code；不猜为父子树或项目组合 |
| contracts | 按 projectGroupCode 关联，保留旧合同关系；不按合同号合并项目 |
| products | 按 projectId、contractNo 关联，保留旧产品及数量；不据此伪造交付完成 |

固定归档身份命名空间 `pm_project_header` 是现有归档格式的一部分，不等于所有来源环境的实际表名。数据导出须记录实际只读对象及快照时间；本机取证对象为 pm_project。不得改变现有归档身份键以重建相同来源项目。

```powershell
python scripts/legacy_project_archive.py import --archive "$env:TEMP/npdms-history.sqlite" --tenant 1 --input scripts/tests/fixtures/legacy_project_archive.synthetic.jsonl
python -m unittest scripts.tests.test_legacy_project_archive scripts.tests.test_legacy_project_source_preflight -v
```

`--apply` 仅写本机 SQLite 档案。租户＋来源系统＋来源主键去重；规范化关系顺序后计算摘要。相同输入重放返回原批次，不同内容的相同身份隔离，不覆盖历史。异常仅存行号、摘要、问题码。整个归档批次事务中隔离业务坏记录；文件/结构错误失败。未知 schema 在任何初始化写入前拒绝。

```powershell
python scripts/legacy_project_archive.py import --archive "$env:TEMP/npdms-history.sqlite" --tenant 1 --input scripts/tests/fixtures/legacy_project_archive.synthetic.jsonl --apply
python scripts/legacy_project_archive.py list --archive "$env:TEMP/npdms-history.sqlite" --tenant 1
python scripts/legacy_project_archive.py withdraw --archive "$env:TEMP/npdms-history.sqlite" --tenant 1 --batch <batchId> --reason "合成验证撤回" --apply
```

合成 fixture 含预期异常，import 退出码 2 表示有隔离记录；不是全体导入失败。UPDATE/DELETE 触发器保护历史。withdraw 追加撤回记录，使该批次不再贡献 active_archive_project 可见项，原证据保留；重放撤回批次不会重新激活。这不是运行项目硬删除工具。

## 来源完整性预检

`scripts/legacy_project_source_preflight.py` 校验合同、订单头、执行单、公司、产品、数量、发货以及 RMA 例外。D365 发货 lineNum 按订单行 customInfo 中库存事务标识定位；如有 fb_shipment_barcode_order_line 的明确关系，则必须同时核对 pack_id、barcode、合同、订单和行键。该关系允许销售产品与物理发货产品不同，不推断 BOM 或单位换算。重复、冲突、缺失归属隔离；没有明确关系时保留产品码一致性要求。可选执行单字段为空不构成冲突，非空时必须一致。

缺失计量单位的订单行只能保持 PENDING_AUTHORITY，不能伪造 EA；CONFIRMED 仍要求单位，负数量拒绝。合同 PENDING_AUTHORITY 不因试迁强制改为确认。

## 运行项目试迁边界

最终客户必须由 CRM 执行单真实主键→new_account_id→AccountId→CUS 外部身份映射证明；名称相等仅辅助比对，购货方不替代最终客户。创建走既有公开业务 API、幂等键、来源指纹、公司＋办事处联合权限和正式模板匹配，不直接写项目状态或数据库业务表。

来源补齐应使用 owner 服务，保留既有来源键、版本前置条件及原始证据。不得创建平行身份或覆盖不可变记录。源、目标快照及逐项映射仅留本机私有目录，不能随代码上传。

当前101项候选中现有操作人精确联合范围仅覆盖8项且同类型，没有其他现成启用身份补足两项。因此十项试迁尚未执行。没有维护范围的 API，不声称存在；不扩大持久角色或组织范围。运行项目导入、重复重放、撤回及真实业务浏览器验收均不能记为通过。
