# SDS Phase 1：版本范围矩阵

> 文档状态：`IN_REVIEW`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8机器差量校验已完成，待fresh-context独立复审
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 能力 | V1 | V2 | V3/排除 |
|---|---|---|---|
| Stage—ProjectTask工作台 | Stage→ProjectTask导航投影、含默认TASK_NATIVE的六类WorkBinding、通用任务详情基础功能、权限模式、按绑定类型执行与完成规则、项目概览六页签 | 甘特图和高级编排 | 不建设重复导航树；通用详情不得替代非TASK_NATIVE绑定的业务执行 |
| 到货、安装、配置Log、业务联调、实施证据上传 | 建设 | — | — |
| 实施质量检查 | — | IMP-01 | — |
| 现场安全检查 | — | — | IMP-02退出当前正式范围，后续如恢复需重新立项 |
| 满意度收集、有效答卷/签字、整改重收和闭环引用 | ACC-02、CLO-01～02 | — | 异常放行复核仅P2演进方向 |
| CUT-01核心任务P1～P6闭环 | CUT-01～07、CUT-09～10；P1入口+P2～P6五步工作台；P3内直接填写/采集回填 | CUT-08外部备件集成 | 自动建议等级仅V3演进；不建立独立采集阶段、逐步骤执行或稳定观察 |
| 通用工单、打卡、工时和割接保障工单 | — | — | WO-01～06、WO-08～10、RPT-01/04为P3候选；不恢复通用WorkOrder或迁入CUT |
| Device Access & Collection 凭证、任务编排与回调 | INT-12实施/割接入口 | INT-12巡检入口 | — |
| 项目状态分层 | `current_stage` S0～S6、`lifecycle_status` ACTIVE；PM-10异常关闭和受控重开、CLO-02正常闭环 | — | 不新增维护阶段；`display_status`只读派生 |
| 实施方案配置脚本 | SCH-01上传/引用脚本文件 | SCH-03脚本解析与版本治理增强 | SCH-03不构成EXE-03 V1前置 |
| 现有采集平台子应用集成 | 复用现有设备连接、命令执行和原始采集能力 | 可演进为内部模块并扩展巡检调用和结果解释 | 不重复建设采集引擎 |
| 技术公告治理 | — | INT-04基础同步 | KNO-V3-01～08治理增强 |
| 续保经营、维保空间、独立维保档案 | — | — | OUT_OF_SCOPE |

本矩阵只表达 PRD V1.8 已确认的版本边界，不新增版本承诺；ACC-05仅作为V3演进方向保留，COM-02和IMP-02不得回流当前V1/V2。
