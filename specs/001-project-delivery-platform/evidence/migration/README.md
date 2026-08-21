# 完整历史字段迁移与目标字段证据

本目录同时保存完整Excel字段处置、核心旧库字段画像、语义数据元映射和目标字段目录。默认读取这里的结构化证据，不重复解析`需求/数据元.xlsx`；只有结构化证据不足或源文件哈希变化时才回溯Excel。

## 文件

| 文件 | 用途 |
| --- | --- |
| `core-field-mapping.jsonl` | 18张核心旧表、326个字段的逐字段迁移去向、转换规则、当前填充率和已观察最大长度 |
| `core-field-mapping-summary.json` | 按表汇总字段覆盖数和结构化、关系、血缘、载荷分类 |
| `legacy-physical-field-mapping.jsonl` | 活动结构页3,931条物理字段证据的逐行处置，保留Excel坐标 |
| `legacy-physical-field-canonical.jsonl` | 归并后的3,908个唯一旧表字段及全部证据坐标 |
| `semantic-data-element-mapping.jsonl` | 197条语义数据元来源行到目标模型的映射 |
| `semantic-data-element-canonical.jsonl` | 归并后的108个唯一语义数据元 |
| `schema-business-element-mapping.jsonl` | 活动结构页82条业务数据元的处置 |
| `target-field-catalog.jsonl` | 从正式DDL单向生成的目标表字段目录、中文描述和数据元引用 |
| `complete-migration-summary.json` | DDL哈希、表列数和所有证据口径的覆盖汇总 |
| `target-field-catalog-summary.json` | 目标字段按领域和字段类别的汇总 |
| `migration-validation.json` | 中文注释、公共字段注释、目标命名、公司—部门配对及映射目标存在性的自动校验结果 |
| `ddl-item-decision-register.json` | 当前DDL与历史目录并集的逐项决策、复核证据和批准状态 |
| `p3-e09-confirmation-packet.md` / `.json` | 绑定当前DDL及寄存器哈希的需求方九组完整确认清单；ADR-0028已接受确认时全部692项`DEFER`，当前寄存器已为`DEFER=0`并通过独立复审，不要求逐项Reviewer签署 |

## 完整性定义

每个旧字段必须至少属于以下一种去向，并且每条旧记录必须原样写入`pms_migration_source_record.source_payload`：

- `STRUCTURED`：进入正式列，可直接查询、索引、统计或同步；
- `RELATION`：用于解析目标外键或关系表，失败时生成迁移问题；
- `LINEAGE`：进入来源主键、外部键或幂等血缘；
- `PAYLOAD`：只保留原值，不参与正常业务查询；该分类必须有明确理由。

当前门禁结果：18张表、326个字段、326个已映射、0个未映射。这里的“已映射”只证明设计覆盖，不能替代迁移批次的逐行对账。

完整口径还要求3,931条物理证据行、3,908个唯一表字段、197条语义来源行和82条活动业务数据元全部具有结构化目标或明确的非结构化终态处置。`SOURCE_ONLY`、`PLATFORM_REPLACED`、`LINEAGE`和`PAYLOAD`不计入结构化业务覆盖率。

## 使用方法

```powershell
rg -n '"sourceTable":"pm_project","sourceColumn":"column012"' core-field-mapping.jsonl
rg -n '"sourceTable":"fb_shipment_barcode"' core-field-mapping.jsonl
rg -n '"preservation":"PAYLOAD"' core-field-mapping.jsonl
```

## 迁移门禁

1. 迁移程序的字段配置必须由本矩阵生成或与本矩阵自动比对。
2. 新增、删除或改名旧库字段后，必须重跑结构和填充率统计，禁止手工只改一条JSONL。
3. 结构化目标不存在、来源字段未映射、来源记录未写迁移来源记录、关系解析失败但未生成问题，任一情况都阻断切换。
4. `pms_migration_source_record.source_payload`保存来源字段原名和值；正式列保存归一后的业务值。二者不能互相替代。
5. 当前填充率为0的字段仍保留在载荷中；只有业务、数据和迁移负责人共同确认后才能列入忽略清单。
6. 自动校验目标列存在，并验证当前已观察文本最大长度不超过目标`VARCHAR`；正式迁移前必须在最终一致性抽取上重跑。
7. 目标侧公司字段必须使用`company_*`、部门字段必须使用`department_*`；旧来源原字段名可保留在血缘和载荷中，不能继续生成`org_*`或`organization*`目标。
8. 公司—部门组合属于同一业务上下文时必须在同一关系行输出并共同对账，禁止把两边独立生成后再按时间、角色或编码猜配对。
9. 高频引用的ID、编码和发生时名称必须作为一组校验；主档变化默认不回写历史业务值，迁移基线补值必须明确标记来源。
10. 生成器遇到无效目标立即失败，禁止把`STRUCTURED/RELATION`静默降级为`PAYLOAD`；DDL、目标字段目录和所有映射目标必须双向一致且使用同一DDL哈希。
11. 公共字段注释必须统一为“主键ID、租户ID、状态、乐观锁版本、创建人、创建时间、更新人、更新时间、删除标志”；引用字段注释只写字段含义，取值时点和刷新规则在设计规则中统一说明。
