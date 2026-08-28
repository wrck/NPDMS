from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate_sds_phase1.py"
SPEC = importlib.util.spec_from_file_location("validate_sds_phase1", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
REPOSITORY_ROOT = MODULE_PATH.parents[1]


class ValidateSdsPhase1Test(unittest.TestCase):

    def build_fixture(self, root: Path) -> None:
        for relative in MODULE.REQUIRED_FILES:
            source = REPOSITORY_ROOT / relative
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)

    def validate_mutation(self, relative: str, old: str, new: str) -> list[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / relative
            content = target.read_text(encoding="utf-8-sig")
            self.assertIn(old, content)
            target.write_text(content.replace(old, new, 1), encoding="utf-8")
            return MODULE.validate(root)

    def test_current_phase1_review_candidate_passes_machine_gate(self) -> None:
        self.assertEqual([], MODULE.validate(REPOSITORY_ROOT))

    def test_approved_phase1_documents_cannot_return_to_in_review(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "> 文档状态：`BASELINE`",
            "> 文档状态：`IN_REVIEW`",
        )
        self.assertTrue(any("missing current Phase 1 metadata" in error for error in errors), errors)

    def test_approved_phase1_documents_cannot_retain_pending_body_claims(self) -> None:
        for pending_claim in (
            "V1.8机器差量校验已完成，待fresh-context独立复审",
            "当前结论仍须通过fresh-context独立复审",
            "V1.8独立复审尚未完成",
        ):
            with self.subTest(pending_claim=pending_claim):
                errors = self.validate_mutation(
                    "docs/design/02-domain-model.md",
                    "V1.8独立复审GO，当前分册已纳入正式基线",
                    pending_claim,
                )
                self.assertTrue(any("stale Phase 1 pending-review" in error for error in errors), errors)

    def test_sds_master_must_publish_phase1_baseline_only(self) -> None:
        errors = self.validate_mutation(
            "docs/design/00-system-detailed-design.md",
            "| SDS Phase 1 | `BASELINE` | `READY_FOR_PHASE_2_V1.8` |",
            "| SDS Phase 1 | `IN_REVIEW` | `NOT_READY_FOR_PHASE_2_V1.8` |",
        )
        self.assertTrue(any("SDS master Phase 1 summary" in error for error in errors), errors)

    def test_gate_readme_must_bind_reviewed_candidate_and_core_fix(self) -> None:
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/README.md",
            "核心修复`537ab5a`已验证",
            "核心修复尚未登记",
        )
        self.assertTrue(any("Phase 1 gate README" in error for error in errors), errors)

    def test_owner_map_must_cover_each_formal_requirement_once(self) -> None:
        errors = self.validate_mutation(
            "docs/design/phase-1-domain-ownership.md",
            "PROJ-12、INT-01",
            "PROJ-12、PM-01",
        )
        self.assertTrue(any("Owner mapping" in error for error in errors), errors)

    def test_removed_or_deferred_requirements_cannot_return_to_owner_scope(self) -> None:
        errors = self.validate_mutation(
            "docs/design/phase-1-domain-ownership.md",
            "| COM | COM-01 |",
            "| COM | COM-01、COM-02 |",
        )
        self.assertTrue(any("removed/deferred" in error for error in errors), errors)

    def test_project_state_layers_are_required(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "`assignment_status`独立维护",
            "指派状态合并到项目状态",
        )
        self.assertTrue(any("project state layers" in error for error in errors), errors)

    def test_task_native_binding_is_required(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "未指定其他业务绑定时使用TASK_NATIVE",
            "允许任务不配置WorkBinding",
        )
        self.assertTrue(any("TASK_NATIVE" in error for error in errors), errors)

    def test_non_native_completion_cannot_bypass_business_facts(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            "不以通用完成命令绕过目标业务事实",
            "允许使用通用完成命令",
        )
        self.assertTrue(any("completion guard" in error for error in errors), errors)

    def test_cut03_must_remain_inside_p3_boundary(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "界面合并不产生新的业务阶段或聚合Owner",
            "界面合并产生独立采集阶段和聚合Owner",
        )
        self.assertTrue(any("CUT-03" in error for error in errors), errors)

    def test_approved_gate_cannot_reintroduce_pending_review(self) -> None:
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/gate-status.md",
            "> 独立复审：`GO`<br>",
            "> 独立复审：`GO`<br>\n> 重新复审：`RE_REVIEW_REQUIRED`<br>",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)

    def test_version_scope_must_keep_v1_closure_and_v2_technical_notice(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02e-version-scope-matrix.md",
            "PM-10异常关闭和受控重开、CLO-02正常闭环",
            "只维护ACTIVE项目",
        )
        self.assertTrue(any("version scope" in error for error in errors), errors)

    def test_configuration_log_must_have_asset_owner(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02c-data-ownership-matrix.md",
            "ConfigurationLog原始文件、不可变解析版本和设备关联",
            "配置Log关联",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_eqp02_traceability_must_include_configuration_log(self) -> None:
        errors = self.validate_mutation(
            "docs/traceability/requirement-matrix.md",
            "ConfigurationLog / Device / DeviceArchive",
            "Device / DeviceArchive",
        )
        self.assertTrue(any("EQP-02" in error for error in errors), errors)

    def test_inspection_state_machine_must_keep_prd_states(self) -> None:
        errors = self.validate_mutation(
            "docs/design/05-state-machine.md",
            "待准备、待预检、巡检中、待报告、待标注、待办跟踪中、已闭环、已归档、已取消",
            "新建、准备、执行、报告、闭环",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_service_handover_event_must_have_one_producer(self) -> None:
        errors = self.validate_mutation(
            "docs/design/04-module-design.md",
            "ServiceStatusChanged | 当前不创建持续服务跟踪对象",
            "ServiceStatusChanged、ServiceHandoverCreated | 当前不创建持续服务跟踪对象",
        )
        self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_pm10_terminal_commands_need_operation_authorization(self) -> None:
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            "工程管理部关闭岗 | 关闭、重开",
            "服务经理 | 关闭、重开",
        )
        self.assertTrue(any("PM-10 authorization" in error for error in errors), errors)

    def test_formal_architecture_cannot_claim_runtime_gate_release(self) -> None:
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            "运行提交、证据批次、构建结果和放行结论只登记在对应工程门禁",
            "Q2实现工作包门禁已解除",
        )
        self.assertTrue(any("runtime evidence" in error for error in errors), errors)

    def test_srv01_traceability_uses_read_only_handover_reference(self) -> None:
        matrix = (REPOSITORY_ROOT / "docs/traceability/requirement-matrix.md").read_text(encoding="utf-8-sig")
        row = MODULE.markdown_row(matrix, "SRV-01")
        self.assertIn("ServiceHandoverReference", row[4])
        self.assertIn("ServiceHandoverReference", row[8])
        self.assertNotIn("ServiceHandover、", row[8])

    def test_cross_context_contracts_have_requirement_traceability(self) -> None:
        contract = (REPOSITORY_ROOT / "docs/design/02d-cross-context-contracts.md").read_text(encoding="utf-8-sig")
        self.assertEqual("Requirement ID", MODULE.markdown_row(contract, "契约")[1])
        self.assertEqual(
            {"EXE-03", "EXE-04", "EQP-02"},
            set(MODULE.requirement_ids(MODULE.markdown_row(contract, "ConfigurationLogPublished")[1])),
        )
        self.assertEqual(
            {"ACC-06", "SRV-01"},
            set(MODULE.requirement_ids(MODULE.markdown_row(contract, "ServiceHandoverCreated")[1])),
        )
        matrix = (REPOSITORY_ROOT / "docs/traceability/requirement-matrix.md").read_text(encoding="utf-8-sig")
        for identifier in ("EXE-03", "EXE-04", "EQP-02", "ACC-06", "SRV-01"):
            self.assertIn("02d契约", " | ".join(MODULE.markdown_row(matrix, identifier)))

    def test_pm10_reopen_guard_preserves_recoverable_state_and_side_effects(self) -> None:
        state = (REPOSITORY_ROOT / "docs/design/05-state-machine.md").read_text(encoding="utf-8-sig")
        for marker in (
            "记录重开原因",
            "恢复关闭前最后一个可恢复阶段",
            "创建新的责任处理事项",
            "不得自动恢复已终止的外部任务",
        ):
            self.assertIn(marker, state)

    def test_duplicate_service_handover_producer_is_rejected(self) -> None:
        row = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            row,
            row + "\n| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |",
        )
        self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_duplicate_configuration_log_owner_is_rejected(self) -> None:
        row = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            row,
            row + "\n| ConfigurationLog | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_reference_link_cannot_hide_second_service_handover_producer(self) -> None:
        row = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            row,
            row
            + "\n| [ServiceHandoverCreated][contract-ref] | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"
            + "\n\n[contract-ref]: https://example.invalid/service-handover",
        )
        self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_reference_link_cannot_hide_second_configuration_log_owner(self) -> None:
        row = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            row,
            row
            + "\n| [ConfigurationLog][owner-ref] | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |"
            + "\n\n[owner-ref]: https://example.invalid/configuration-log",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_collapsed_and_shortcut_links_cannot_hide_service_handover_producer(self) -> None:
        row = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        for linked_name in ("[ServiceHandoverCreated][]", "[ServiceHandoverCreated]"):
            with self.subTest(linked_name=linked_name):
                errors = self.validate_mutation(
                    "docs/design/02d-cross-context-contracts.md",
                    row,
                    row
                    + f"\n| {linked_name} | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"
                    + "\n\n[ServiceHandoverCreated]: https://example.invalid/service-handover",
                )
                self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_collapsed_and_shortcut_links_cannot_hide_configuration_log_owner(self) -> None:
        row = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        for linked_name in ("[ConfigurationLog][]", "[ConfigurationLog]"):
            with self.subTest(linked_name=linked_name):
                errors = self.validate_mutation(
                    "docs/design/02b-aggregate-boundary-decisions.md",
                    row,
                    row
                    + f"\n| {linked_name} | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |"
                    + "\n\n[ConfigurationLog]: https://example.invalid/configuration-log",
                )
                self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_inline_html_cannot_hide_second_service_handover_producer(self) -> None:
        row = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        for rendered_name in (
            'Service<span title="x>y"></span>HandoverCreated',
            "Service<!-- > -->HandoverCreated",
        ):
            with self.subTest(rendered_name=rendered_name):
                errors = self.validate_mutation(
                    "docs/design/02d-cross-context-contracts.md",
                    row,
                    row
                    + f"\n| {rendered_name} | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |",
                )
                self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_inline_html_cannot_hide_second_configuration_log_owner(self) -> None:
        row = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        for rendered_name in (
            'Configuration<span title="x>y"></span>Log',
            "Configuration<!-- > -->Log",
        ):
            with self.subTest(rendered_name=rendered_name):
                errors = self.validate_mutation(
                    "docs/design/02b-aggregate-boundary-decisions.md",
                    row,
                    row
                    + f"\n| {rendered_name} | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |",
                )
                self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_inspection_precheck_contradiction_is_rejected(self) -> None:
        row = "| 巡检任务主流程 | INS-01创建待准备任务 | INS-02.S1选择方式并冻结INS-03规则→在线进入待预检并经INS-04通过后执行，离线直接执行→INS-05生成报告→INS-06标注问题→INS-07闭环和归档 | 无需跟踪的问题完成标注后进入已闭环；需跟踪的问题进入待办跟踪中，全部关闭后进入已闭环并归档 | 预检未通过保持待预检；报告、标注或待办未完成不得跳过；取消保留原因和状态历史 |"
        errors = self.validate_mutation(
            "docs/design/06-workflow-design.md",
            row,
            row + "\n| 巡检预检强制执行 | INS-04预检失败 | 管理员可强制进入巡检中 | 继续执行 | 保留失败原因 |",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_conflicting_pm10_authorization_is_rejected(self) -> None:
        row = "| Project/PM-10 | 服务经理对本人主责且满足条件的项目发起回退 | 服务经理填写回退原因并结束当前责任区间，无关闭或重开权限 | 工程管理部关闭岗 | 关闭、重开仅限授权项目；关闭前校验后代、在途审批和领域任务，重开仅限EXCEPTION_CLOSED；项目经理和普通成员只读状态及原因摘要 |"
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            row,
            row + "\n| Project/PM-10补充 | 项目经理任意关闭 | 项目经理任意重开 | 项目经理 | 无状态限制 |",
        )
        self.assertTrue(any("PM-10 authorization" in error for error in errors), errors)

    def test_runtime_evidence_append_is_rejected(self) -> None:
        marker = "- Phase 1只确认逻辑架构、Context、依赖和安全边界，不批准实现工作包、数据库迁移或生产发布。Phase 1正式文档不嵌入实现或迁移代码；同仓模块与迁移目录中的实现仍须经过后续Gate。"
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            marker,
            marker + "\n- 运行提交：abcdef；测试结果：PASS；放行结论：APPROVED。",
        )
        self.assertTrue(any("runtime evidence" in error for error in errors), errors)

    def test_gate_cannot_mix_not_ready_with_approved_ready(self) -> None:
        marker = "> 核心修复：`537ab5a`（`VERIFIED`）"
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/gate-status.md",
            marker,
            marker + "\n> 审查状态：`IN_REVIEW`<br>\n> 结论：`NOT_READY_FOR_PHASE_2_V1.8`",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)

    def test_contract_with_extra_column_cannot_hide_second_producer(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 | 额外列绕过 |",
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_markdown_emphasis_cannot_hide_second_configuration_log_owner(self) -> None:
        marker = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            marker,
            marker + "\n| **ConfigurationLog** | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_precheck_unsuccessful_wording_cannot_allow_execution(self) -> None:
        marker = "| 巡检任务主流程 | INS-01创建待准备任务 | INS-02.S1选择方式并冻结INS-03规则→在线进入待预检并经INS-04通过后执行，离线直接执行→INS-05生成报告→INS-06标注问题→INS-07闭环和归档 | 无需跟踪的问题完成标注后进入已闭环；需跟踪的问题进入待办跟踪中，全部关闭后进入已闭环并归档 | 预检未通过保持待预检；报告、标注或待办未完成不得跳过；取消保留原因和状态历史 |"
        errors = self.validate_mutation(
            "docs/design/06-workflow-design.md",
            marker,
            marker + "\n预检未成功，管理员仍可进入巡检中。",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_pm10_prose_cannot_grant_project_manager_close_or_reopen(self) -> None:
        marker = "| Project/PM-10 | 服务经理对本人主责且满足条件的项目发起回退 | 服务经理填写回退原因并结束当前责任区间，无关闭或重开权限 | 工程管理部关闭岗 | 关闭、重开仅限授权项目；关闭前校验后代、在途审批和领域任务，重开仅限EXCEPTION_CLOSED；项目经理和普通成员只读状态及原因摘要 |"
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            marker,
            marker + "\n项目经理可关闭或重开任意项目。",
        )
        self.assertTrue(any("PM-10 authorization" in error for error in errors), errors)

    def test_runtime_fact_sentence_without_colons_is_rejected(self) -> None:
        marker = "- Phase 1只确认逻辑架构、Context、依赖和安全边界，不批准实现工作包、数据库迁移或生产发布。Phase 1正式文档不嵌入实现或迁移代码；同仓模块与迁移目录中的实现仍须经过后续Gate。"
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            marker,
            marker + "\n运行批次 BUILD-20260820、提交 abcdef1、测试通过、准予放行。",
        )
        self.assertTrue(any("runtime evidence" in error for error in errors), errors)

    def test_gate_status_table_cannot_override_pending_metadata(self) -> None:
        marker = "> 核心修复：`537ab5a`（`VERIFIED`）"
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/gate-status.md",
            marker,
            marker + "\n\n| 当前状态 | 结论 |\n|---|---|\n| Phase 1 | IN_REVIEW / NOT_READY_FOR_PHASE_2_V1.8 |",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)

    def test_html_entity_cannot_hide_second_service_handover_producer(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n| ServiceHandoverCre&#97;ted | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |",
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_html_entity_cannot_hide_second_configuration_log_owner(self) -> None:
        marker = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            marker,
            marker + "\n| Configuration&#76;og | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件 |",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_gfm_row_without_leading_pipe_cannot_hide_second_service_handover_producer(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\nServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者",
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_gfm_row_without_leading_pipe_cannot_hide_second_configuration_log_owner(self) -> None:
        marker = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            marker,
            marker + "\nConfigurationLog | Implementation Execution | 非法第二Owner | WriteConfigurationLog | 可覆盖原始文件",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_pipe_delimited_prose_outside_gfm_table_is_not_a_contract_row(self) -> None:
        marker = "| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |"
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            marker,
            marker + "\n\nConfigurationLog | 本段仅解释术语，不是表格行。",
        )
        self.assertEqual([], errors)

    def test_fenced_code_table_example_is_not_a_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        example = """```markdown
| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |
```"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + example,
        )
        self.assertEqual([], errors)

    def test_indented_code_table_example_is_not_a_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        example = """    | 契约 | Requirement ID | Producer | Consumer | 语义 |
    |---|---|---|---|---|
    | ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + example,
        )
        self.assertEqual([], errors)

    def test_html_comment_table_example_is_not_a_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        example = """<!--
| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |
-->"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + example,
        )
        self.assertEqual([], errors)

    def test_valid_fence_info_characters_do_not_expose_code_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        examples = (
            """```~markdown
| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |
```""",
            """~~~`markdown`
| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |
~~~""",
        )
        for example in examples:
            with self.subTest(opening=example.splitlines()[0]):
                errors = self.validate_mutation(
                    "docs/design/02d-cross-context-contracts.md",
                    marker,
                    marker + "\n\n" + example,
                )
                self.assertEqual([], errors)

    def test_invalid_backtick_fence_info_cannot_hide_real_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        second_table = """```language`invalid

| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + second_table,
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_mixed_space_tab_indented_code_table_is_not_a_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        example = """ \t| 契约 | Requirement ID | Producer | Consumer | 语义 |
 \t|---|---|---|---|---|
 \t| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 示例 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + example,
        )
        self.assertEqual([], errors)

    def test_nested_list_gfm_table_remains_a_real_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        second_table = """- 补充契约

    | 契约 | Requirement ID | Producer | Consumer | 语义 |
    |---|---|---|---|---|
    | ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + second_table,
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_literal_html_comment_marker_in_fence_cannot_hide_later_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        second_table = """```text
<!-- literal marker
```

| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + second_table,
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_literal_html_comment_marker_in_inline_code_cannot_hide_later_contract_table(self) -> None:
        marker = "| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |"
        second_table = """说明：`<!--`是字面量。

| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ServiceHandoverCreated | SRV-01 | Service Operations | Project Delivery | 非法第二生产者 |"""
        errors = self.validate_mutation(
            "docs/design/02d-cross-context-contracts.md",
            marker,
            marker + "\n\n" + second_table,
        )
        self.assertTrue(any("cross-context contracts" in error for error in errors), errors)

    def test_precheck_bypass_split_across_lines_is_rejected(self) -> None:
        marker = "| 巡检任务主流程 | INS-01创建待准备任务 | INS-02.S1选择方式并冻结INS-03规则→在线进入待预检并经INS-04通过后执行，离线直接执行→INS-05生成报告→INS-06标注问题→INS-07闭环和归档 | 无需跟踪的问题完成标注后进入已闭环；需跟踪的问题进入待办跟踪中，全部关闭后进入已闭环并归档 | 预检未通过保持待预检；报告、标注或待办未完成不得跳过；取消保留原因和状态历史 |"
        errors = self.validate_mutation(
            "docs/design/06-workflow-design.md",
            marker,
            marker + "\n预检未成功。\n管理员仍可进入巡检中。",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_unrelated_prohibition_cannot_hide_precheck_bypass(self) -> None:
        marker = "| 巡检任务主流程 | INS-01创建待准备任务 | INS-02.S1选择方式并冻结INS-03规则→在线进入待预检并经INS-04通过后执行，离线直接执行→INS-05生成报告→INS-06标注问题→INS-07闭环和归档 | 无需跟踪的问题完成标注后进入已闭环；需跟踪的问题进入待办跟踪中，全部关闭后进入已闭环并归档 | 预检未通过保持待预检；报告、标注或待办未完成不得跳过；取消保留原因和状态历史 |"
        errors = self.validate_mutation(
            "docs/design/06-workflow-design.md",
            marker,
            marker + "\n预检未成功，管理员仍可进入巡检中，但不得删除任务。",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_unrelated_prohibition_cannot_hide_pm10_grant(self) -> None:
        marker = "| Project/PM-10 | 服务经理对本人主责且满足条件的项目发起回退 | 服务经理填写回退原因并结束当前责任区间，无关闭或重开权限 | 工程管理部关闭岗 | 关闭、重开仅限授权项目；关闭前校验后代、在途审批和领域任务，重开仅限EXCEPTION_CLOSED；项目经理和普通成员只读状态及原因摘要 |"
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            marker,
            marker + "\n项目经理可关闭或重开任意项目，但不得删除项目。",
        )
        self.assertTrue(any("PM-10 authorization" in error for error in errors), errors)

    def test_runtime_release_facts_split_across_lines_are_rejected(self) -> None:
        marker = "- Phase 1只确认逻辑架构、Context、依赖和安全边界，不批准实现工作包、数据库迁移或生产发布。Phase 1正式文档不嵌入实现或迁移代码；同仓模块与迁移目录中的实现仍须经过后续Gate。"
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            marker,
            marker + "\n运行批次 BUILD-20260820\n提交 abcdef1\n测试通过\n准予放行。",
        )
        self.assertTrue(any("runtime evidence" in error for error in errors), errors)

    def test_conditional_prefix_cannot_hide_current_gate_pending_claim(self) -> None:
        marker = "> 核心修复：`537ab5a`（`VERIFIED`）"
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/gate-status.md",
            marker,
            marker + "\n如果后续通过则放行；当前状态 IN_REVIEW / NOT_READY_FOR_PHASE_2_V1.8。",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)

    def test_project_manager_may_query_closed_projects(self) -> None:
        marker = "| Project/PM-10 | 服务经理对本人主责且满足条件的项目发起回退 | 服务经理填写回退原因并结束当前责任区间，无关闭或重开权限 | 工程管理部关闭岗 | 关闭、重开仅限授权项目；关闭前校验后代、在途审批和领域任务，重开仅限EXCEPTION_CLOSED；项目经理和普通成员只读状态及原因摘要 |"
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            marker,
            marker + "\n项目经理可以查询已关闭项目。",
        )
        self.assertEqual([], errors)

    def test_test_pass_statement_may_explicitly_deny_release_meaning(self) -> None:
        marker = "- 运行提交、证据批次、构建结果和放行结论只登记在对应工程门禁，不固化到正式架构正文；证据变化不得通过修改本章伪装为阶段批准。"
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            marker,
            marker + "\n- 构建结果 PASS、测试结果 PASS 不代表准予放行。",
        )
        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
