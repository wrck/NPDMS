# DDL 漂移审查报告

> 状态：`DEFER`（仅完成事实核对，未批准重建迁移制品）
>
> 目的：作为 `AI-MIG-000` 的第一份可复核产物，防止把当前 DDL 或旧矩阵未经裁决直接当作权威基线。

## 1. 核对范围

| 输入 | 路径 | 当前事实 |
|---|---|---|
| 当前目标DDL | `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql` | SHA-256 `3CDDE2E206EE4AE401ECC398EA01A6F44FFAD37AC2644478DCD42202330D58BC` |
| 迁移目标字段目录 | `evidence/migration/target-field-catalog.jsonl` | 52张表；表名集合与当前DDL一致 |
| 迁移摘要 | `evidence/migration/target-field-catalog-summary.json` | 记录DDL SHA-256 `2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33` |
| 完整迁移摘要 | `evidence/migration/complete-migration-summary.json` | 同样记录旧DDL SHA-256 `2B2069…BF33`，不能作为当前DDL验证结果 |
| 迁移校验 | `evidence/migration/migration-validation.json` | `passed=true`，但输入哈希已过期，不具备当前发布放行效力 |

当前DDL与目录的表名集合目前均为52张且无集合差异；这只证明名称集合一致，不能证明列、类型、注释、索引、外键、CHECK或业务语义一致。

## 2. 漂移分类与裁决

当前尚未取得数据架构和业务负责人对逐项差异的批准，因此所有漂移统一为`DEFER`：

| 差异对象 | 事实 | 当前裁决 | 责任角色 | 解除条件 |
|---|---|---|---|---|
| DDL文件哈希 | 当前文件为`3CDDE2…D58BC`，矩阵摘要为`2B2069…BF33` | `DEFER` | 数据架构、业务负责人 | 生成逐表/逐列/约束差异清单，选择`ACCEPT_CURRENT`、`RESTORE_APPROVED_BASELINE`或`AMEND_CURRENT` |
| 目标字段目录 | 表名集合与当前DDL均为52张；字段级尚未由当前DDL重新生成 | `DEFER` | 数据架构 | 逐列比较通过，目录生成版本与批准DDL哈希一致 |
| 核心字段矩阵 | 旧摘要的326字段“未映射0”只属于旧DDL证据 | `DEFER` | 数据架构、迁移负责人 | 按批准DDL重建并验证字段处置、目标绑定和来源载荷 |
| 迁移校验报告 | `passed=true`引用旧输入 | `DEFER` | 迁移负责人 | 使用新`releaseId`、批准DDL和结构化规则重新运行 |

## 3. 本次禁止的动作

- 不得只修改`ddlSha256`、`passed`或摘要计数来消除漂移。
- 不得在未有`approvedDdlSha256`和审批证据时生成新的正式迁移发布清单。
- 不得依据表名集合一致推断列和约束一致。
- 不得连接旧`localhost:3306/dppms`执行写操作、跨库SQL或锁表。
- 不得开始`AI-MIG-001`至`AI-MIG-017`的生产数据导入。

## 4. 下一步可执行命令

以下命令只读且只生成审查输入，不修改旧库或迁移摘要：

```powershell
$ddl = 'specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql'
(Get-FileHash -Algorithm SHA256 -LiteralPath $ddl).Hash
rg '^CREATE TABLE |^    [A-Za-z_][A-Za-z0-9_]* ' $ddl
Get-Content 'specs/001-project-delivery-platform/evidence/migration/target-field-catalog-summary.json' -Raw | ConvertFrom-Json
```

实现`AI-MIG-000`时，应将上述事实扩展为机器输出：

```text
ddl-drift-review.json
  -> table-diff[]
  -> column-diff[]
  -> index-diff[]
  -> foreign-key-diff[]
  -> check-diff[]
  -> comment-diff[]
  -> decisionRegisterRef
  -> approvedDdlSha256
```

完成逐项裁决后，才允许重建`target-field-catalog*`、`complete-migration-summary.json`、`core-field-mapping*`和`migration-validation.json`。
