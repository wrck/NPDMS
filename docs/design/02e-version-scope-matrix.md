# SDS Phase 1：版本范围矩阵

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：PRD V1.7 附录 A.1 的全部 104 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；业务 Owner 已签署，见 `docs/design/phase-1-domain-ownership.md`
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 能力 | V1 | V2 | V3/排除 |
|---|---|---|---|
| 到货、安装、配置Log、业务联调、实施证据上传 | 建设 | — | — |
| 实施质量检查 | — | IMP-01 | — |
| 现场安全检查 | — | IMP-02 | — |
| 满意度收集、有效答卷/签字、整改重收和闭环引用 | ACC-02、CLO-01～02 | — | 异常放行复核仅P2演进方向 |
| 割接保障任务 | — | CUT-11 | WO-08～10领域任务增强仅P3候选 |
| 通用工单、打卡和工时 | — | — | WO-01～05、RPT-01/04为P3候选；不恢复通用WorkOrder |
| Device Access & Collection 凭证、任务编排与回调 | INT-12实施/割接入口 | INT-12巡检入口 | — |
| 现有采集平台子应用集成 | 复用现有设备连接、命令执行和原始采集能力 | 可演进为内部模块并扩展巡检调用和结果解释 | 不重复建设采集引擎 |
| 技术公告治理 | INT-04基础同步在V2 | — | KNO-V3-01～08治理增强 |
| 续保经营、维保空间、独立维保档案 | — | — | OUT_OF_SCOPE |

本矩阵只表达 PRD V1.7 已确认的版本边界，不新增版本承诺。
