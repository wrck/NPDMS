# Phase 3 门禁与证据

Phase 3审查安全、审计可观测、部署、性能和测试设计是否足以形成完整SDS基线。

当前结论为`REVALIDATION_REQUIRED / BLOCKED_BY_PRD_DELTA`。修订007的`APPROVED / READY_FOR_SDS_BASELINE_V1.8`保留为历史证据；修订008—015新增和改变的模板裁剪、条件性验收、三类项目退出、PM-06范围追加、RPT-02全集统计及完整主/子流程尚须完成Phase 3差量测试与运行保障复核。部署、联调、专项验收、历史迁移、切换、UAT和生产发布门禁继续独立有效。

## 当前文件

- [`runtime-fact-inventory.md`](runtime-fact-inventory.md)：实现仓库运行、构建、迁移和环境事实。
- [`gate-status.md`](gate-status.md)：Phase 3当前门禁与证据缺口。
- [`production-evidence-register.md`](production-evidence-register.md)：P3-E01～E09逐项必填事实和验收规范。
- [`frontend-ts-check-evidence.md`](frontend-ts-check-evidence.md)：P3-E08可复跑汇总；同名JSON保存逐错误机器证据。
- [`evidence-packet-templates/`](evidence-packet-templates/)：按ADR-0004生成P3-E01～E07运行证据模板和P3-E09模型事实模板；使用`scripts/generate_phase3_evidence_packets.py --check`防止模板漂移。
- [`submissions/`](submissions/)：环境或发布批次的版本化实际提交；P3-E09模型基线不要求Owner签署提交，生成器不得覆盖。
- [`evidence-submission-template.json`](evidence-submission-template.json)：仅作通用JSON结构示例；实际提交优先复制逐项模板，并用`scripts/validate_phase3_evidence_submission.py`校验。
- [`phase3-evidence-register.json`](phase3-evidence-register.json)：可机器校验的当前状态、Owner、事实和证据引用。
- [`self-review.md`](self-review.md)：当前自审结论和阻塞影响。
- [`P3-E09当前哈希完整确认清单`](../../../../specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.md)：ADR-0028已接受的需求方九组决策证据，覆盖确认时全部692项`DEFER`，并绑定寄存器Items哈希及文件哈希；当前DDL、`DEFER=0`和MySQL 8.4隔离执行已通过独立复审并形成可用模型基线，但不构成迁移批准。

`self-review.md`登记修订007历史差量结论；修订015当前状态只以`gate-status.md`为准。`independent-review.md`只保留历史证据，不构成当前独立裁决角色。正式14/17/18/19/20分册只放入`docs/design/`，评审过程不得混入正式设计目录。

## 按差量选择校验

```powershell
py -3 -B scripts/validate_sds_phase3.py
```

Phase 3基线修订只运行会决定阶段状态的SDS定点校验。仅当实际修改P3-E09确认包、证据模板、DDL寄存器或证据寄存器时，才运行各自生成/校验脚本；结果不改变后续行动的重复校验不执行。当前核心DDL未变化，P3-E09继续保持`MODEL_BASELINE_READY`但不授权迁移；`AI-MIG-000`按Release范围条件适用：不含历史迁移和数据切换时为`NOT_APPLICABLE`，包含任一项时须在Release前达到`VERIFIED`并只在批准窗口内执行。

## 放行原则

- PRD量化NFR必须原值落位，不得改成模糊“高性能/高安全”。
- 当前本地运行剖面与生产部署事实必须分离。
- 生产缺少的Owner、RPO/RTO、备份恢复和基础设施证据必须阻塞正式发布设计，不得填建议值伪装完成。
- 自动校验PASS只证明结构和显式约束，不替代独立语义/可执行性评审。
