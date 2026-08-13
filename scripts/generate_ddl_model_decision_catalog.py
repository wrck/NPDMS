#!/usr/bin/env python3
"""Generate the concrete P3-E09 data-model decision catalog."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


REGISTER = Path("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json")
INVENTORY = Path("specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json")
OUTPUT = Path("specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md")


CURRENT_SINGLETON_KEYS = {
    "uk_device_current_assignment",
    "uk_scope_current",
    "uk_customer_primary_contact",
    "uk_project_primary_company_department",
    "uk_device_component_current_slot",
}
VERSION_SEQUENCE_KEYS = {
    "uk_product_release",
    "uk_document_version",
    "uk_project_code_sequence",
    "uk_configuration_parse_attempt",
    "uk_satisfaction_task_revision",
    "uk_satisfaction_questionnaire_revision",
    "uk_satisfaction_response_sequence",
    "uk_satisfaction_result_sequence",
    "uk_cutover_support_history_sequence",
    "uk_cutover_responsibility_interval_sequence",
}
RELATION_GRAIN_KEYS = {
    "uk_delivery_scope_detail_sequence",
    "uk_device_configuration_feature",
    "uk_device_configuration_service",
    "uk_topology_device",
    "uk_order_change",
    "uk_order_contract",
    "uk_order_execution",
    "uk_order_line_execution",
    "uk_project_contract",
    "uk_project_company_department_role",
    "uk_project_member_role",
    "uk_portfolio_project",
    "uk_project_relation",
    "uk_incident_device",
    "uk_configuration_component_candidate",
    "uk_satisfaction_response_request",
    "uk_satisfaction_result_response",
    "uk_cutover_support_scope_window",
}
SOURCE_IDEMPOTENCY_KEYS = {
    "uk_device_assignment_source",
    "uk_device_relation_source",
    "uk_shipment_event_source",
    "uk_contract_master_source",
    "uk_contract_receivable_source",
    "uk_crm_execution_config",
    "uk_execution_merge_batch",
    "uk_execution_merge_member_source",
    "uk_shipment_contract_ref_source",
    "uk_shipment_package_source",
    "uk_market_relation_source",
    "uk_external_key_source_target",
    "uk_migration_issue_source",
    "uk_migration_source_record",
    "uk_project_party_source",
    "uk_historical_work_order_source",
    "uk_historical_time_record_source",
    "uk_directory_sync_snapshot_source",
}
TEMPORAL_CHECKS = {
    "chk_device_configuration_dates",
    "chk_device_assignment_dates",
    "chk_device_version_dates",
    "chk_network_topology_dates",
    "chk_contract_dates",
    "chk_contract_receivable_dates",
    "chk_scope_dates",
    "chk_project_contract_dates",
    "chk_shipment_package_warranty_dates",
    "chk_sync_batch_time",
    "chk_project_company_department_dates",
    "chk_project_member_dates",
    "chk_project_party_dates",
    "chk_service_incident_times",
    "chk_configuration_parse_attempt_time",
    "chk_cutover_support_window",
    "chk_cutover_responsibility_dates",
    "chk_historical_work_order_dates",
    "chk_historical_time_dates",
    "chk_device_component_dates",
    "chk_directory_sync_times",
}
NO_SELF_CHECKS = {
    "chk_device_relation_self",
    "chk_device_secondary_self",
    "chk_order_change_self",
    "chk_project_relation_self",
}
STATUS_COUPLED_CHECKS = {
    "chk_crm_execution_af",
    "chk_migration_issue_resolution",
    "chk_cutover_closure_submit",
}
NONNEGATIVE_CHECKS = {
    "chk_external_key_target_sequence",
    "chk_migration_source_target_count",
    "chk_sync_batch_count",
    "chk_project_depth",
    "chk_configuration_collection_result_version",
    "chk_configuration_parse_attempt_no",
    "chk_configuration_component_candidate_no",
    "chk_satisfaction_task_revision",
    "chk_satisfaction_questionnaire_revision",
    "chk_satisfaction_response_sequence",
    "chk_satisfaction_result_sequence",
    "chk_cutover_support_history_sequence",
    "chk_cutover_responsibility_interval_sequence",
    "chk_cutover_support_arrangement_no",
}
CROSS_FIELD_CHECKS = {
    "chk_delivery_scope_detail_subject",
    "chk_device_secondary_cache",
    "chk_project_code_namespace",
    "chk_project_company_department_pair",
}
EXACT_MATCH_FIELD_NAMES = {
    "source_record_key",
    "master_source_record_key",
    "source_config_key",
    "source_merge_key",
    "source_object_id",
    "source_business_key",
    "source_pk",
    "source_checksum",
    "source_extract_checksum",
    "file_checksum",
}


def unique_key_category(name: str) -> str:
    if name.endswith("tenant_row") or name == "uk_document_version_owner":
        return "TENANT_REFERENCE"
    if name in CURRENT_SINGLETON_KEYS:
        return "CURRENT_SINGLETON"
    if name in VERSION_SEQUENCE_KEYS:
        return "VERSION_SEQUENCE"
    if name in RELATION_GRAIN_KEYS:
        return "RELATION_GRAIN"
    if name in SOURCE_IDEMPOTENCY_KEYS:
        return "SOURCE_IDEMPOTENCY"
    return "BUSINESS_IDENTITY"


def check_category(name: str, value: str) -> str:
    if name.endswith("_deleted"):
        return "SOFT_DELETE"
    if name in TEMPORAL_CHECKS:
        return "TEMPORAL_ORDER"
    if name in NO_SELF_CHECKS:
        return "NO_SELF"
    if name in STATUS_COUPLED_CHECKS:
        return "STATUS_COUPLED"
    if name in NONNEGATIVE_CHECKS:
        return "NONNEGATIVE_COUNT"
    if name in CROSS_FIELD_CHECKS:
        return "CROSS_FIELD"
    if " IN (0, 1)" in value:
        return "BOOLEAN_FLAG"
    return "UNCLASSIFIED"


def constraint_kind(value: str) -> str:
    normalized = value.upper().strip()
    if normalized.startswith("PRIMARY KEY"):
        return "PRIMARY_KEY"
    if " FOREIGN KEY " in f" {normalized} ":
        return "FOREIGN_KEY"
    if normalized.startswith("UNIQUE KEY"):
        return "UNIQUE_KEY"
    if " CHECK " in f" {normalized} ":
        return "CHECK"
    if normalized.startswith("KEY "):
        return "INDEX"
    return "OTHER"


def constraint_name(value: str) -> str:
    match = re.search(r"^(?:UNIQUE KEY|KEY|CONSTRAINT)\s+([a-zA-Z0-9_]+)", value.strip())
    return match.group(1) if match else "PRIMARY"


def escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def render_decision_analysis(
    changes: dict[str, list[dict[str, object]]],
    domain_tables: dict[str, list[str]],
    constraints: dict[str, list[tuple[str, str]]],
    unique_groups: dict[str, list[tuple[str, str, str]]],
    check_groups: dict[str, list[tuple[str, str, str]]],
    generated_columns: list[tuple[str, str, str]],
    exact_match_fields: list[tuple[str, str, str, bool]],
) -> list[str]:
    lines = [
        "",
        f"### 1.1 {sum(len(items) for items in changes.values())}项证据的真实含义",
        "",
        "|比较结果|数量|实际含义|能否据此直接批准|",
        "|---|---:|---|---|",
        f"|`MATCH`|{len(changes['MATCH']):,}|历史目标DDL与当前DDL中的表/字段定义一致；不是旧库数据质量证明|无需重复讨论未改变的字段语义，但不能据此宣称迁移通过|",
        f"|`ADDED`|{len(changes['ADDED']):,}|当前模型相对历史目标DDL新增|必须有需求或ADR依据|",
        f"|`MODIFIED`|{len(changes['MODIFIED']):,}|字段定义或说明发生变化|必须说明是否改变业务含义|",
        f"|`REMOVED`|{len(changes['REMOVED']):,}|历史目标DDL中存在、当前模型已移除|必须确认是范围排除而非数据遗漏|",
        f"|`UNVERIFIED_BASELINE_MISSING`|{len(changes['UNVERIFIED_BASELINE_MISSING']):,}|历史目录未保存约束和表选项|必须按约束语义分类评审，不能自动接受|",
        "",
        f"因此，{len(changes['MATCH'])}项MATCH只保留逐项追溯；真正需要裁决的是新增/修改/移除的模型变化，以及{len(changes['UNVERIFIED_BASELINE_MISSING'])}项缺少历史结构证据的约束、表选项或生成表达式。",
        "",
        "### 1.2 当前核心迁移子集按领域分布",
        "",
        "|领域|表数|表清单|",
        "|---|---:|---|",
    ]
    for domain, tables in sorted(domain_tables.items()):
        lines.append(f"|{domain}|{len(tables)}|{'、'.join(f'`{table}`' for table in tables)}|")

    lines.extend([
        "",
        "### 1.3 已有ADR明确的模型变化",
        "",
        "|变化组|具体内容|依据|当前判断|",
        "|---|---|---|---|",
        "|客户与项目市场行业四维|新增`cus_market_relation`及20个字段；客户新增7个字段并修订`industry_code`语义；项目新增7个字段并修订`industry_code`语义|ADR-0021；CRM表`pm_project_market_relations_from_sms`|业务含义已确认；仍需保证来源键精确匹配|",
        "|项目编码命名空间|`proj_project`新增`code_root_id`、`code_rule_version`、`project_sequence`|ADR-0020|同一CRM项目不因多合同/订单改号，子项目使用永久流水号|",
        "|一源多目标映射|`plt_external_key_mapping`新增`target_role`、`target_sequence`|ADR-0022|目标角色和顺序已确认且重跑不可重排|",
        "|V3技术公告治理排除|移除4张KNO表及67个字段|ADR-0022|不进入核心迁移DDL；INT-04只读引用逻辑对象仍保留|",
        "|Q03当前关系与交付范围粒度|4项当前唯一生成列去除扩展状态依赖；新增`com_delivery_scope_detail`；删除订单级唯一主执行单约束|ADR-0023|同一项目节点—订单行一条当前范围主记录并允许多条明细；订单可关联多个默认主执行单|",
        "",
        "### 1.4 不能用“整组接受”带过的实质风险",
        "",
        "|风险|当前证据|业务影响|批准前应作出的选择|",
        "|---|---|---|---|",
        f"|精确键与默认排序规则冲突|{sum(len(tables) for tables in domain_tables.values())}张表默认`utf8mb4_0900_ai_ci`；{len(exact_match_fields)}个来源键/哈希字段要求原值精确匹配|大小写或重音不同的来源键可能被视为相同|来源键改用二进制排序规则，名称继续使用中文友好排序规则|",
        "|可空列参与唯一键|8个唯一键包含可空列；5个是有意的当前记录标记，1个是可选来源键，2个关系粒度键存在空洞|可能允许重复历史关系或重复成员任职|逐项区分有意NULL语义与意外空洞|",
        "|状态码写入数据库表达式|3个原固定状态CHECK已移除；5个当前唯一生成列使用稳定事实表达式|状态扩展不再需要修改DDL；已确认当前唯一事实不会被状态扩展绕过|保持业务守卫由受控状态动作执行并留痕|",
        f"|普通索引没有查询证据|{len(constraints['INDEX'])}个候选索引未绑定查询计划、基数和写入成本|过量索引增加同步写入成本，缺失索引影响树查询和对账|Q08已接受为候选基线；Feature/P3-E06用真实查询和压测定稿|",
        "",
        "### 1.5 Q07已按数据架构不变量批量确认的内容",
        "",
        "|内容|数量|批量确认的前提|仍未包含的业务判断|",
        "|---|---:|---|---|",
        f"|主键结构|{len(constraints['PRIMARY_KEY'])}|{len(constraints['PRIMARY_KEY']) - 1}张实体/关系表使用单列`id`；分析投影使用`(tenant_id, project_id)`复合主键|不决定业务编码是否可重复|",
        f"|租户复合引用键|{len(unique_groups['TENANT_REFERENCE'])}|仅支撑同租户复合外键/行引用|不替代业务唯一键|",
        f"|同领域物理外键|{len(constraints['FOREIGN_KEY'])}|{len(constraints['FOREIGN_KEY'])}个外键的父子表均在同一领域；违规旧数据进入迁移问题池|不授权跨Context直接访问Repository|",
        f"|软删除检查|{len(check_groups['SOFT_DELETE'])}|`deleted`稳定为0/1技术字段|删除不得释放永久业务键|",
        f"|时间顺序检查|{len(check_groups['TEMPORAL_ORDER'])}|只拒绝结束早于开始，不补造旧数据时间|不决定业务有效期|",
        f"|稳定布尔标志|{len(check_groups['BOOLEAN_FLAG'])}|字段确为稳定0/1标志|业务状态不能压缩成布尔值|",
        f"|禁止直接自关联|{len(check_groups['NO_SELF'])}|拒绝对象直接关联自身|项目/任务完整防环仍由应用校验|",
        f"|非负数与计数一致性|{len(check_groups['NONNEGATIVE_COUNT'])}|仅约束物理不变量|不替代数量可分配性检查|",
        "",
        f"### 1.6 {sum(len(rows) for rows in unique_groups.values())}个唯一键按业务语义分组",
    ])
    unique_titles = {
        "BUSINESS_IDENTITY": ("业务身份键", "决定业务编码、SN或单号能否重复；建议永久不复用"),
        "SOURCE_IDEMPOTENCY": ("来源幂等键", "决定外部记录重放时更新同一事实还是产生重复记录"),
        "CURRENT_SINGLETON": ("当前唯一记录", "利用生成列仅限制当前有效记录"),
        "VERSION_SEQUENCE": ("版本与永久序号", "保证版本号或编码流水号不可重复、不可复用"),
        "RELATION_GRAIN": ("关系事实粒度", "决定哪些字段组合代表同一条关系事实"),
        "TENANT_REFERENCE": ("租户行引用键", "支撑同租户复合引用，属于技术完整性"),
    }
    for category in ("BUSINESS_IDENTITY", "SOURCE_IDEMPOTENCY", "CURRENT_SINGLETON", "VERSION_SEQUENCE", "RELATION_GRAIN", "TENANT_REFERENCE"):
        title, meaning = unique_titles[category]
        rows = sorted(unique_groups[category])
        lines.extend([
            "",
            f"#### {title}（{len(rows)}项）",
            "",
            meaning + "。",
            "",
            "|表|唯一键|当前字段组合|判断重点|",
            "|---|---|---|---|",
        ])
        for table, name, value in rows:
            focus = {
                "BUSINESS_IDENTITY": "确认租户内业务身份永久唯一",
                "SOURCE_IDEMPOTENCY": "确认来源键精确匹配且重放幂等",
                "CURRENT_SINGLETON": "确认同一时点只能存在一条当前记录",
                "VERSION_SEQUENCE": "确认版本/序号只增不复用",
                "RELATION_GRAIN": "确认字段完整表达关系粒度，重点检查NULL",
                "TENANT_REFERENCE": "技术引用键，可按架构规则确认",
            }[category]
            lines.append(f"|`{table}`|`{name}`|`{escape(value)}`|{focus}|")

    lines.extend([
        "",
        "### 1.7 8个可空唯一键逐项判断",
        "",
        "|唯一键|可空列|NULL是否有意|判断|",
        "|---|---|---|---|",
        "|`uk_device_current_assignment`|`current_device_id`|是：仅当前归属生成设备ID|约束同一设备同一时点仅归属一个最具体项目；建议保留|",
        "|`uk_scope_current`|`current_order_line_id`|是：仅当前范围生成订单行ID|约束同一项目—订单行只有一条当前范围；建议保留|",
        "|`uk_customer_primary_contact`|`primary_customer_id`|是：仅主联系人生成客户ID|约束客户只有一个主联系人；建议保留|",
        "|`uk_project_primary_company_department`|`primary_project_id`|是：仅主关系生成项目ID|按关系角色约束一个主公司部门关系；建议保留|",
        "|`uk_device_component_current_slot`|`current_slot_code`|是：仅当前组件关系生成槽位编码|约束同一机框槽位同一时点最多一个当前板卡；建议保留|",
        "|`uk_contract_master_source`|`master_source_record_key`|是：未取得主来源键时允许NULL|非NULL来源键必须唯一；建议保留并改为精确比较|",
        "|`uk_project_company_department_role`|`department_code`、`effective_from`|尚无证据表明有意|NULL会允许相同项目/公司/角色重复，存在约束空洞|",
        "|`uk_project_member_role`|`effective_from`|尚无证据表明有意|NULL会允许相同成员/角色重复，存在约束空洞|",
        "",
        f"### 1.8 {sum(len(rows) for rows in check_groups.values())}个CHECK按业务语义分组",
        "",
        "|分组|数量|代表规则|建议|",
        "|---|---:|---|---|",
        f"|软删除|{len(check_groups['SOFT_DELETE'])}|`deleted IN (0,1)`|技术规则批量确认|",
        f"|时间顺序|{len(check_groups['TEMPORAL_ORDER'])}|结束不得早于开始|接受；旧数据缺失保持NULL|",
        f"|稳定布尔标志|{len(check_groups['BOOLEAN_FLAG'])}|主标记、必需标记等|仅稳定二值字段可接受|",
        f"|禁止直接自关联|{len(check_groups['NO_SELF'])}|设备/订单/项目不能直接关联自身|接受；多节点防环由应用校验|",
        f"|非负数与计数|{len(check_groups['NONNEGATIVE_COUNT'])}|序号、目标数、树深、成功失败计数|技术规则批量确认|",
        f"|跨字段不变量|{len(check_groups['CROSS_FIELD'])}|附加SN、项目编码命名空间、部门配对、交付范围明细主体|按ADR和业务规则逐项确认|",
        f"|状态耦合|{len(check_groups['STATUS_COUPLED'])}|当前DDL不再用固定状态码触发允许值或必填规则|已按ADR-0023调整|",
        "",
        "状态耦合CHECK当前为0项；AF证据、DeliveryScope生效数量和MigrationIssue关闭完整性改由受控业务动作校验并留痕。",
        "",
        f"{len([name for _, name, _ in generated_columns if name != 'rma_marked'])}个当前唯一生成列逐项如下。Q03已确认其业务事实，表达式只依赖稳定有效期、删除标记或主标记，不依赖可扩展业务状态码：",
        "",
        "|表/生成列|当前表达式|被保护的不变量|推荐调整|",
        "|---|---|---|---|",
    ])
    for table, name, expression in sorted(generated_columns):
        if name == "rma_marked":
            continue
        protected = {
            "current_device_id": "同一设备同一时点只有一个直接项目归属",
            "current_order_line_id": "同一项目—订单行只有一个当前交付范围",
            "primary_customer_id": "一个客户只有一个当前主联系人",
            "primary_project_id": "项目同一角色只有一个主公司部门关系",
            "current_slot_code": "同一机框槽位同一时点最多一个当前板卡",
        }.get(name, "当前唯一业务事实")
        lines.append(f"|`{table}.{name}`|`{escape(expression)}`|{protected}|已改为只依赖稳定有效期、删除标记或主标记|")
    lines.extend([
        "",
        "另有1个非状态生成列也需明确边界：`ast_device_shipment_event.rma_marked`当前按RMA编号是否为空生成，并把字符串`null`视为空。该列只能作为迁移兼容和查询索引投影，不能替代已确认的`business_action_code`、方向和正负数量业务事实；字符串哨兵清洗必须在迁移规则中留痕。",
        "",
        f"### 1.9 {len(exact_match_fields)}个精确匹配字段与排序规则",
        "",
        "这些字段当前继承表级`utf8mb4_0900_ai_ci`。推荐来源键使用`utf8mb4_0900_bin`；契约明确为ASCII摘要的字段使用`ascii_bin`；不得改变原值大小写。",
        "",
        "|表|字段|类型|可空|推荐比较语义|",
        "|---|---|---|---:|---|",
    ])
    for table, name, data_type, nullable in sorted(exact_match_fields):
        collation = "`ascii_bin`（限定ASCII摘要时），否则`utf8mb4_0900_bin`" if name.endswith("checksum") else "`utf8mb4_0900_bin`"
        lines.append(f"|`{table}`|`{name}`|`{data_type}`|{'是' if nullable else '否'}|{collation}|")

    lines.extend([
        "",
        "### 1.10 建议审批层次",
        "",
        "|层次|内容|批准主体|批准结果|",
        "|---|---|---|---|",
        "|L1 已确认业务变化|ADR-0019～0022对应111项|需求Owner复核引用|回写逐项登记，不重复讨论|",
        "|L2 数据架构不变量|主键、租户引用、同域外键、稳定技术CHECK|需求方已接受推荐，Reviewer待签署|Q07登记为当前SDS技术约束|",
        "|L3 业务唯一性与状态守卫|业务身份、来源幂等、当前唯一、关系粒度、状态耦合CHECK|需求Owner+数据架构Owner|逐组批准；有空洞的先修模|",
        f"|L4 性能候选|{len(constraints['INDEX'])}个普通索引|需求方接受候选；Feature Owner+性能Owner验证|Q08进入候选基线，绑定查询后由P3-E06压测定稿|",
        "|L5 迁移运行证据|源库哈希、水位、脏数据量、对账、回退、切换|迁移Owner+独立复核人|AI-MIG-000实施/切换门禁关闭|",
    ])
    return lines


def render(root: Path) -> str:
    register_path = root / REGISTER
    inventory_path = root / INVENTORY
    register = json.loads(register_path.read_text(encoding="utf-8"))
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))

    table_columns: dict[str, list[dict[str, object]]] = defaultdict(list)
    table_items: dict[str, dict[str, object]] = {}
    changes: dict[str, list[dict[str, object]]] = defaultdict(list)
    for item in register["items"]:
        changes[item["comparisonStatus"]].append(item)
        if item.get("comparisonStatus") == "REMOVED":
            continue
        if item["itemType"] == "TABLE":
            table_items[item["table"]] = item
        elif item["itemType"] == "COLUMN":
            table_columns[item["table"]].append(item)

    constraints: dict[str, list[tuple[str, str]]] = defaultdict(list)
    table_options: dict[str, str] = {}
    for record in inventory["records"]:
        table = record["table"]
        table_options[table] = record["tableOptions"]
        for value in record["constraints"]:
            constraints[constraint_kind(value)].append((table, value))

    domain_tables: dict[str, list[str]] = defaultdict(list)
    for table in sorted(table_items):
        domain_tables[table.split("_", 1)[0].upper()].append(table)

    unique_groups: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    for table, value in constraints["UNIQUE_KEY"]:
        name = constraint_name(value)
        unique_groups[unique_key_category(name)].append((table, name, value))

    check_groups: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    for table, value in constraints["CHECK"]:
        name = constraint_name(value)
        check_groups[check_category(name, value)].append((table, name, value))
    if check_groups.get("UNCLASSIFIED"):
        names = ", ".join(name for _, name, _ in check_groups["UNCLASSIFIED"])
        raise ValueError(f"unclassified CHECK constraints: {names}")

    generated_columns: list[tuple[str, str, str]] = []
    exact_match_fields: list[tuple[str, str, str, bool]] = []
    for table, items in table_columns.items():
        for item in items:
            current = item.get("currentValue")
            if not isinstance(current, dict):
                continue
            name = str(item.get("name", ""))
            expression = current.get("generatedExpression")
            if current.get("generated") and isinstance(expression, str):
                generated_columns.append((table, name, expression))
            if name in EXACT_MATCH_FIELD_NAMES or name.endswith("_checksum"):
                exact_match_fields.append((table, name, str(current["dataType"]), bool(current["nullable"])))

    register_sha = hashlib.sha256(register_path.read_bytes()).hexdigest().upper()
    inventory_sha = hashlib.sha256(inventory_path.read_bytes()).hexdigest().upper()
    lines = [
        "# P3-E09 数据模型逐项裁决清单",
        "",
        "> 状态：`REVIEW_REQUIRED`",
        f"> 决策登记SHA-256：`{register_sha}`",
        f"> 约束清单SHA-256：`{inventory_sha}`",
        "> 本清单只展开现有机器证据，不自动批准数据模型。",
        "",
        "## 1. 核对结论与裁决分组",
        "",
        "|分组|数量|当前事实|建议裁决方式|",
        "|---|---:|---|---|",
        f"|表|{len(table_items)}|当前核心迁移子集；新增、修改和移除事实见逐项登记|按ADR及Reviewer证据逐项裁决|",
        f"|字段|{sum(len(items) for items in table_columns.values()):,}|当前DDL字段；不包含已移除V3治理表字段|按业务语义、类型和约束分类裁决|",
        f"|表选项|{len(table_options)}|旧基线未保存|需确认字符比较与存储规则|",
        f"|主键|{len(constraints['PRIMARY_KEY'])}|旧基线未保存|Q07已按技术不变量确认，Reviewer待签署|",
        f"|外键|{len(constraints['FOREIGN_KEY'])}|旧基线未保存|Q07已确认同域外键；违规历史数据隔离|",
        f"|普通索引|{len(constraints['INDEX'])}|旧基线未保存|Q08已接受为候选基线，Feature/P3-E06验证|",
        f"|唯一键|{len(constraints['UNIQUE_KEY'])}|旧基线未保存|影响重复业务数据，必须业务审查|",
        f"|CHECK|{len(constraints['CHECK'])}|旧基线未保存|影响异常历史数据，必须业务审查|",
    ]
    lines.extend(render_decision_analysis(
        changes,
        domain_tables,
        constraints,
        unique_groups,
        check_groups,
        generated_columns,
        exact_match_fields,
    ))
    lines.extend([
        "",
        "## 2. 表与字段完整清单",
        "",
        "以下仅列当前核心迁移DDL中的表与字段；相对旧目录的`MATCH/ADDED/MODIFIED`状态以逐项决策登记为准。",
        "",
        "|编号|表|字段数|字段清单|",
        "|---|---|---:|---|",
    ])
    for index, table in enumerate(sorted(table_items), 1):
        columns = sorted(table_columns[table], key=lambda item: str(item.get("name", "")))
        column_names = "、".join(f"`{item['name']}`" for item in columns)
        lines.append(f"|T-{index:03d}|`{table}`|{len(columns)}|{column_names}|")

    lines.extend(["", "## 3. 表选项完整清单", "", "|编号|表|当前表选项|建议|", "|---|---|---|---|"])
    for index, table in enumerate(sorted(table_options), 1):
        lines.append(f"|O-{index:03d}|`{table}`|`{escape(table_options[table])}`|待确认字符比较规则后分类接受|")

    sections = [
        ("PRIMARY_KEY", "4. 主键完整清单", "PK", "Q07已确认；Reviewer待签署"),
        ("FOREIGN_KEY", "5. 外键完整清单", "FK", "Q07已确认同域约束；违规历史数据隔离"),
        ("INDEX", "6. 普通索引完整清单", "IX", "Q08候选基线；Feature查询计划与P3-E06压测验证"),
        ("UNIQUE_KEY", "7. 唯一键完整清单", "UK", "影响重复数据；需逐组业务确认"),
        ("CHECK", "8. CHECK规则完整清单", "CK", "影响异常历史数据；需逐组业务确认"),
    ]
    for kind, title, prefix, recommendation in sections:
        lines.extend(["", f"## {title}", "", "|编号|表|当前定义|业务影响/建议|", "|---|---|---|---|"])
        for index, (table, value) in enumerate(sorted(constraints[kind]), 1):
            lines.append(f"|{prefix}-{index:03d}|`{table}`|`{escape(value)}`|{recommendation}|")

    lines.extend([
        "",
        "## 9. 裁决边界",
        "",
        "- `ACCEPT_CURRENT`表示接受当前DDL作为目标数据模型，不代表历史数据天然满足约束。",
        "- 历史数据违反已批准约束时进入迁移问题池并保留来源证据，不得静默删除、改写或临时放宽模型掩盖问题。",
        "- Q07技术约束和Q08候选索引已回写逐项决策登记；Q08仍需Feature查询计划和P3-E06压测，不等于性能已验收。",
        "- 本清单不授权连接或修改旧库，不授权执行生产迁移。",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    output = root / args.output
    content = render(root)
    if args.check:
        if not output.exists() or output.read_text(encoding="utf-8") != content:
            print(f"[FAIL] P3-E09 decision catalog drift: {output}")
            return 1
        print("[PASS] P3-E09 concrete data-model decision catalog")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8")
    print(f"[WRITE] {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
