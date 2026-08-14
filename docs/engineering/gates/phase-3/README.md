# Phase 3 门禁与证据

Phase 3审查安全、审计可观测、部署、性能和测试设计是否足以形成完整SDS基线。

## 当前文件

- [`runtime-fact-inventory.md`](runtime-fact-inventory.md)：实现仓库运行、构建、迁移和环境事实。
- [`gate-status.md`](gate-status.md)：Phase 3当前门禁与证据缺口。
- [`production-evidence-register.md`](production-evidence-register.md)：P3-E01～E09逐项必填事实和验收规范。
- [`frontend-ts-check-evidence.md`](frontend-ts-check-evidence.md)：P3-E08可复跑汇总；同名JSON保存逐错误机器证据。
- [`evidence-packet-templates/`](evidence-packet-templates/)：按ADR-0004生成的P3-E01～E07、P3-E09逐Owner模板；使用`scripts/generate_phase3_evidence_packets.py --check`防止模板漂移。
- [`submissions/`](submissions/)：Owner按环境或发布批次保存的版本化实际提交；生成器不得覆盖。
- [`evidence-submission-template.json`](evidence-submission-template.json)：仅作通用JSON结构示例；实际提交优先复制逐项模板，并用`scripts/validate_phase3_evidence_submission.py`校验。
- [`phase3-evidence-register.json`](phase3-evidence-register.json)：可机器校验的当前状态、Owner、事实和证据引用。
- [`self-review.md`](self-review.md)：当前自审结论和阻塞影响。
- [`P3-E09当前哈希完整确认清单`](../../../../specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.md)：ADR-0028已接受的需求方九组决策证据，覆盖确认时全部692项`DEFER`；当前只剩Reviewer签署与批准哈希门禁。

后续自审和独立复审分别登记为`self-review.md`和`independent-review.md`。正式14/17/18/19/20分册只放入`docs/design/`，评审过程不得混入正式设计目录。

## 必跑校验

```powershell
py -3 -B scripts/generate_p3e09_confirmation_packet.py --check
py -3 -B scripts/generate_phase3_evidence_packets.py --check
py -3 -B scripts/validate_ddl_item_decision_register.py
py -3 -B scripts/validate_phase3_evidence_register.py
py -3 -B scripts/validate_sds_phase3.py
```

上述校验通过只证明结构、事实绑定和当前Gate状态一致；只有生产Owner证据与独立复核通过才能将门禁改为`VERIFIED`。

## 放行原则

- PRD量化NFR必须原值落位，不得改成模糊“高性能/高安全”。
- 当前本地运行剖面与生产部署事实必须分离。
- 生产缺少的Owner、RPO/RTO、备份恢复和基础设施证据必须阻塞正式发布设计，不得填建议值伪装完成。
- 自动校验PASS只证明结构和显式约束，不替代独立语义/可执行性评审。
