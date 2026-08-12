# Phase 1 Field 业务命名清单

> 扫描范围：活动 SDS、追溯矩阵、生成脚本、工程链和评审证据<br>
> 规则：只整改 `Field = 现场/实施业务语义`，保留 `field = 字段技术语义`。历史评审原文不修改。

| Location | Symbol/Text | Category | Meaning | Action | Target Name |
|---|---|---|---|---|---|
| `docs/design/02-domain-model.md` | Field Execution | BUSINESS_CONTEXT | 现场实施 bounded context | RENAME | Implementation Execution |
| `docs/design/02-domain-model.md` | Field Execution 内部聚合拆分 | BUSINESS_CONTEXT | 实施执行域内部聚合说明 | RENAME | Implementation Execution 内部聚合拆分 |
| `docs/design/02-domain-model.md` | FieldQualityCheck | BUSINESS_AGGREGATE | 实施质量/安全检查聚合旧名 | RENAME | ImplementationQualityCheck / ImplementationSafetyCheck |
| `docs/design/04-module-design.md` | 现场实施模块 | BUSINESS_MODULE | 实施业务模块中文名已正确 | KEEP | 现场实施 |
| `docs/traceability/requirement-matrix.md` | FieldQualityCheck | BUSINESS_AGGREGATE | IMP需求聚合映射旧名 | RENAME | ImplementationQualityCheck / ImplementationSafetyCheck |
| `scripts/generate_requirement_traceability.py` | FieldQualityCheck | BUSINESS_AGGREGATE | IMP需求默认聚合映射旧名 | RENAME | ImplementationQualityCheck / ImplementationSafetyCheck |
| `docs/design/02-domain-model-full-review.md` | Field Execution、FieldQualityCheck | HISTORICAL_EVIDENCE | 已有完整评审建议/历史证据 | KEEP | 保留原文；由ADR记录规范名称 |
| `docs/design/phase-1-independent-review.md` | Field Execution、FieldQualityCheck | HISTORICAL_EVIDENCE | 已签署独立评审证据 | KEEP | 保留原文；由ADR记录规范名称 |
| `docs/design/phase-1-review.md` | Field Execution、FieldQualityCheck | HISTORICAL_EVIDENCE | Phase 1评审记录 | KEEP | 保留原文；后续新评审使用规范名称 |
| `docs/engineering/archive/*`、`prompts/*` | Field*示例 | HISTORICAL_EVIDENCE | 归档材料或本命名任务说明 | KEEP | 不作为活动业务命名资产 |
| 活动设计文档中的 `field permission`、`字段权限` 等 | field | TECHNICAL_FIELD | 字段技术语义 | KEEP | 保留 |

## 清单结论

- 活动业务 `Field` 标识：待本轮整改后为 0。
- 技术 `field` 语义误改：0。
- 历史评审证据改写：0。
- 无法确认的活动 `Field*`：0；历史文件中的命中项均按证据保留。
