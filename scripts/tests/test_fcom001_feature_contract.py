import json
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = ROOT / "specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md"
PHYSICAL_CONTRACT = ROOT / "specs/features/F-COM-001-physical-contract.json"
MIGRATION_CONTRACT = ROOT / "docs/traceability/domain-entity-migration-contract.json"
DATABASE_DESIGN = ROOT / "docs/design/09-database-design.md"
MODULE_MAPPING = ROOT / "specs/001-project-delivery-platform/appendices/module-boundary-and-naming.md"
REUSE_AUDIT = ROOT / "specs/features/F-COM-001-legacy-reuse-audit.md"
FEATURE_INDEX = ROOT / "specs/features/README.md"
V161_MIGRATION = ROOT / "sql/migrations/V161__fcom001_permissions_menu_and_acceptance_seed.sql"


REQUIRED_PROVIDER_REUSE_AUDIT = {
    "ProjectOrganizationFactApi": "COPY_THEN_ENHANCE",
    "ProjectMasterDO/Mapper": "DIRECT_REUSE",
    "ProjectStageSnapshotDO/Mapper/Repository": "DIRECT_REUSE",
    "ProjectGovernanceApplicationService": "COPY_THEN_ENHANCE",
    "AcceptanceController": "DO_NOT_REUSE",
    "ProjectClosureServiceImpl": "DO_NOT_REUSE",
    "ProjectParticipantFactApi.inspect": "DIRECT_REUSE",
    "AssetDeviceScopeApi.validateAssignableSerials": "DIRECT_REUSE",
    "NotificationRequested": "DIRECT_REUSE",
    "OrganizationScopeApi.getActiveScopes": "DIRECT_REUSE",
}


def provider_reuse_audit_errors(audit: str) -> list[str]:
    rows = [line for line in audit.splitlines() if line.startswith("| REUSE-")]
    errors = []
    for asset, decision in REQUIRED_PROVIDER_REUSE_AUDIT.items():
        matching = [row for row in rows if asset in row]
        if not matching:
            errors.append(f"missing carrier audit: {asset}")
        elif not any(f"`{decision}`" in row for row in matching):
            errors.append(f"wrong carrier decision: {asset} must be {decision}")
    required_boundaries = (
        "不得承接`AcceptanceScopeBinding`身份、触发、表或Provider",
        "独立新建ACC事实、`acc_acceptance_scope_binding`表",
        "不得复用`pms_acc_acceptance`主键、表、状态、Controller、Service或Mapper",
    )
    for boundary in required_boundaries:
        if boundary not in audit:
            errors.append(f"missing report/binding separation: {boundary}")
    return errors


def conflict_notification_and_serial_guard_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    asset = contract.get("moduleApis", {}).get("AssetDeviceScopeApi", {})
    if asset.get("provider") != "EXISTING_REAL_PROVIDER":
        errors.append("AST serial validation must use the existing real Provider")
    if asset.get("failureMode") != "FAIL_CLOSED_WITH_ZERO_COM_WRITES":
        errors.append("AST Provider failures must fail closed with zero COM writes")
    version_behavior = asset.get("versionBehavior", "")
    for rule in ("NO_VERSION_TOKEN", "PREVIEW_RESULT_NOT_REUSABLE", "FRESH_REVALIDATION_REQUIRED_BEFORE_EVERY_WRITE"):
        if rule not in version_behavior:
            errors.append(f"missing AST version behavior: {rule}")

    recipient = contract.get("moduleApis", {}).get("ProjectParticipantFactApi", {})
    if recipient.get("provider") != "EXISTING_REAL_PROVIDER" or recipient.get("method") != "inspect":
        errors.append("project-manager recipient must use ProjectParticipantFactApi.inspect")
    if not any("PROJECT_MANAGER" in value for value in recipient.get("input", [])):
        errors.append("project-manager recipient role is not frozen")

    notification = contract.get("events", {}).get("NotificationRequested", {})
    required_notification = {
        "notificationType": "DELIVERY_SCOPE_CONFLICT_FROZEN",
        "recipientFact": "ProjectParticipantFactApi.inspect(PROJECT_MANAGER)",
        "persistence": "SAME_COM_TRANSACTION_AS_CONFLICT_FREEZE_TO_COM_OUTBOX",
        "deliveryFailure": "RETRY_WITHOUT_ROLLBACK_UNLOCK_OR_CONFLICT_STATE_CHANGE",
    }
    for field, expected in required_notification.items():
        if notification.get(field) != expected:
            errors.append(f"invalid conflict notification {field}")
    if set(notification.get("idempotency", [])) != {
        "notificationType", "deliveryScopeId", "allocationVersion", "erpSourceVersion"
    }:
        errors.append("conflict notification idempotency is incomplete")
    for required_text in (
        "Provider异常/超时/不可用均失败关闭并保持COM零写入",
        "DELIVERY_SCOPE_CONFLICT_FROZEN",
        "不得回滚冲突冻结",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing Feature rule: {required_text}")
    return errors


def contract_administrator_scope_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    question = contract.get("openQuestions", {}).get("Q-FCOM-001", {})
    if question.get("status") != "RESOLVED" or question.get("blockingScope") != "NONE":
        errors.append("Q-FCOM-001 must be resolved without a remaining blocking scope")

    scope = contract.get("contractAdministratorScope", {})
    expected_scope = {
        "owner": "SYSTEM",
        "authorityFact": "CURRENT_ACTIVE_USER_COMPANY_DEPARTMENT_SCOPE",
        "companyMatch": "EXACT_NON_EMPTY_COMPANY_CODE_DISTINCT_UNION",
        "emptyOrUnavailable": "LIST_EMPTY_DETAIL_AND_WRITE_DENIED",
        "positiveAuthorizationCache": "NONE",
        "sensitiveFieldPermission": "pms:commerce:contract:sensitive-read",
    }
    for field, expected in expected_scope.items():
        if scope.get(field) != expected:
            errors.append(f"invalid contract-administrator scope {field}")
    if scope.get("departmentSemantics") != "AUTHORIZATION_CONTEXT_ONLY_NO_COMPANY_SCOPE_INFERENCE":
        errors.append("department facts must not infer company scope")
    if scope.get("writeRevalidation") != "REFETCH_OWNER_FACT_BEFORE_RELATION_WRITE":
        errors.append("relation writes must revalidate the current Owner fact")

    api = contract.get("moduleApis", {}).get("OrganizationScopeApi", {})
    if api.get("provider") != "EXISTING_UNMODIFIED_REAL_PROVIDER" or api.get("method") != "getActiveScopes":
        errors.append("contract scope must reuse OrganizationScopeApi.getActiveScopes")
    if "pms:commerce:contract:sensitive-read" not in contract.get("permissions", {}).get("functional", []):
        errors.append("sensitive contract fields need an independent permission key")

    for required_text in (
        "OrganizationScopeApi.getActiveScopes",
        "部门、主范围标记、scopeRole和项目关系均不得扩大或缩小该公司集合",
        "写入前重新读取当前scope",
        "pms:commerce:contract:sensitive-read",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing Feature rule: {required_text}")
    return errors


def managed_v72_seed_disposition_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    seed = contract.get("managedV72SeedDisposition", {})
    identity = seed.get("exactIdentity", {})
    expected_identity = {
        "sourceMigration": "V72__fproj002_v18_seed_and_menu.sql",
        "tenantId": 0,
        "creator": "seed",
        "updater": "seed",
        "sourceSystem": "SEED",
        "sourceKeyPrefix": "FPROJ002-V18-",
        "sourceEvidencePrefix": "FPROJ002-V18-",
        "projectId": 992002000000,
        "orderId": 992002399001,
    }
    for field, expected in expected_identity.items():
        if identity.get(field) != expected:
            errors.append(f"invalid managed V72 seed identity {field}")
    expected_ids = {
        "orderLineIds": [992002300001, 992002300002, 992002300003, 992002300004],
        "deliveryScopeIds": [992002310001, 992002310004],
        "detailIds": [992002320001, 992002320002, 992002320003, 992002320004],
    }
    for field, expected in expected_ids.items():
        if identity.get(field) != expected:
            errors.append(f"invalid managed V72 seed relation closure {field}")
    expected_relations = {
        "orderLineToOrder": {
            "992002300001": 992002399001,
            "992002300002": 992002399001,
            "992002300003": 992002399001,
            "992002300004": 992002399001,
        },
        "deliveryScopeToOrderLineAndProject": {
            "992002310001": [992002300001, 992002000000],
            "992002310004": [992002300004, 992002000000],
        },
        "detailToDeliveryScope": {
            "992002320001": 992002310001,
            "992002320002": 992002310001,
            "992002320003": 992002310001,
            "992002320004": 992002310004,
        },
    }
    if identity.get("relations") != expected_relations:
        errors.append("managed seed relation references are incomplete")
    if identity.get("matchPolicy") != "ALL_PREDICATES_AND_COMPLETE_RELATION_CLOSURE_OR_FAIL":
        errors.append("managed seed matching must fail on every partial identity")
    if seed.get("businessConversionParticipation") != "EXCLUDED_ONLY_AFTER_EXACT_IDENTITY_MATCH":
        errors.append("ordinary V70 rows must remain in strict business conversion")
    if seed.get("disposition") != "REBUILD_EXPLICIT_TARGET_FIXTURE_IN_SAME_FORWARD_MIGRATION":
        errors.append("managed seed fixture disposition is not explicit")
    if seed.get("ordinaryV70Policy") != "UNCHANGED_STRICT_OWNER_MAPPING_FAIL_BATCH":
        errors.append("ordinary V70 conversion was weakened")
    forbidden = set(seed.get("forbidden", []))
    for rule in (
        "ITEM_CODE_AS_PRODUCT_CODE",
        "PARTIAL_IDENTITY_SEED_BRANCH",
        "UNBOUNDED_SKIP_OR_DELETE",
        "SEED_CONSTANTS_IN_ORDINARY_V70_CONVERSION",
    ):
        if rule not in forbidden:
            errors.append(f"missing managed seed negative rule: {rule}")
    fixture = seed.get("seedOnlyTargetFixture", {})
    expected_fixture = {
        "preserveSourceIds": True,
        "orderId": 992002399001,
        "sourceSystem": "SEED",
        "sourceRecordKey": "FPROJ002-V18-ORDER",
        "sourceVersion": "1",
        "companyCode": "DPTECH-DEMO",
        "orderType": "SEED_FIXTURE",
        "orderNo": "FPROJ002-V18-ORDER",
        "status": "ENABLED",
        "creator": "seed",
        "updater": "seed",
        "projectId": 992002000000,
        "officeDepartmentCode": "OFFICE-HZ-DEMO",
        "officeDepartmentOwnerResolution": "EXACT_ENABLED_SYSTEM_DEPARTMENT_BY_CODE_AND_PROJECT_VERSION",
    }
    for field, expected in expected_fixture.items():
        if fixture.get(field) != expected:
            errors.append(f"invalid seed-only target fixture {field}")
    if set(fixture.get("detailSubjects", {})) != {
        "992002320001", "992002320002", "992002320003", "992002320004"
    }:
        errors.append("seed-only detail subjects are incomplete")
    if fixture.get("constantSemantics") != (
            "MANAGED_ACCEPTANCE_FIXTURE_ONLY_NOT_ERP_PRODUCT_DEVICE_OR_OFFICE_BUSINESS_FACT"):
        errors.append("seed constants are not isolated from business facts")

    ordinary = contract.get("v70Conversion", {})
    if "seedOnlyTargetFixture" in ordinary or "DPTECH-DEMO" in json.dumps(ordinary):
        errors.append("seed constants leaked into ordinary V70 conversion")
    for required_text in (
        "全部身份谓词及关系闭包同时命中",
        "部分命中或关系不完整时整批失败",
        "非种子V70行继续执行既有逐字段Owner解析",
        "不得用`item_code`推断产品编码",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing managed seed Feature rule: {required_text}")
    return errors


def product_code_compatibility_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    line = contract.get("physicalDelta", {}).get("tables", {}).get("com_sales_order_line", {})
    if line.get("fields", {}).get("product_code") != "varchar(64) NULL":
        errors.append("SalesOrderLine ERP product_code physical field is missing")
    authority = contract.get("moduleApis", {}).get("CommerceAuthorityWriteApi", {})
    if "productCode" not in authority.get("salesOrderLineInputFacts", []):
        errors.append("Authority input must carry ERP productCode")
    if "SAME_VERSION_DIFFERENT_PRODUCT_CODE_REJECTED" not in authority.get("successFacts", []):
        errors.append("productCode must participate in same-version payload conflict detection")
    forbidden = set(authority.get("forbiddenProductCodeSources", []))
    if forbidden != {"itemCode", "itemDescription", "productId", "clientField", "deliveryScopeHistory"}:
        errors.append("forbidden runtime productCode substitutes are incomplete")
    delivery = contract.get("moduleApis", {}).get("DeliveryScopeApi", {})
    if delivery.get("applyCommandAdditiveInputs") != [
            "expectedParentProjectVersion", "projectVersionsByClientItemKey"]:
        errors.append("Apply Command parent and child project version inputs are incomplete")
    if delivery.get("projectOfficeLockOrder") != "ALL_PARENT_AND_CHILD_PROJECT_IDS_ASC_BEFORE_ANY_COM_WRITE":
        errors.append("parent and child ProjectOffice facts must lock in stable order before COM writes")
    if delivery.get("remainderOfficeFact") != "EXPECTED_PARENT_PROJECT_VERSION":
        errors.append("REMAINDER must reuse the locked parent project version fact")
    if delivery.get("noSerialSubjectRule") != (
            "LOCKED_CONFIRMED_SALES_ORDER_LINE_NONBLANK_PRODUCT_CODE_CREATES_ONE_DETAIL_WITH_SCOPE_QUANTITY"):
        errors.append("no-SN compatibility detail subject is not deterministic")
    if delivery.get("noSerialFailureMode") != (
            "ZERO_SCOPE_HISTORY_OUTBOX_WRITES_ON_MISSING_BLANK_PENDING_AUTHORITY_OR_VERSION_CONFLICT"):
        errors.append("no-SN Owner failures must leave zero writes")
    fixture = contract.get("managedV72SeedDisposition", {}).get("seedOnlyTargetFixture", {})
    if fixture.get("salesOrderLineCompatibilityProductCodes") != {
        "992002300001": "FPROJ002-V18-COMPAT-001",
        "992002300002": "FPROJ002-V18-COMPAT-002",
        "992002300003": "FPROJ002-V18-COMPAT-003",
        "992002300004": "FPROJ002-V18-COMPAT-004",
    }:
        errors.append("managed compatibility productCode constants are not exact")
    legacy = contract.get("legacySourceReference", {})
    if legacy.get("runtimeUse") != "FORBIDDEN_IN_F_COM_001" or legacy.get("futureMigrationGate") != "AI-MIG-000":
        errors.append("legacy order tables must remain migration references only")
    for required_text in (
        "原子应用方法及`Allocation`语义不变",
        "不得使用`itemCode`、`productId`、客户端值、历史明细或普通业务种子常量替代",
        "`pm_order_data_from_erp`订单头",
        "`pm_project_product_line`项目订单仅作为历史来源及原始子单参照",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing productCode compatibility Feature rule: {required_text}")
    return errors


def conflict_notification_and_serial_guard_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    asset = contract.get("moduleApis", {}).get("AssetDeviceScopeApi", {})
    if asset.get("provider") != "EXISTING_REAL_PROVIDER":
        errors.append("AST serial validation must use the existing real Provider")
    if asset.get("failureMode") != "FAIL_CLOSED_WITH_ZERO_COM_WRITES":
        errors.append("AST Provider failures must fail closed with zero COM writes")
    version_behavior = asset.get("versionBehavior", "")
    for rule in ("NO_VERSION_TOKEN", "PREVIEW_RESULT_NOT_REUSABLE", "FRESH_REVALIDATION_REQUIRED_BEFORE_EVERY_WRITE"):
        if rule not in version_behavior:
            errors.append(f"missing AST version behavior: {rule}")

    recipient = contract.get("moduleApis", {}).get("ProjectParticipantFactApi", {})
    if recipient.get("provider") != "EXISTING_REAL_PROVIDER" or recipient.get("method") != "inspect":
        errors.append("project-manager recipient must use ProjectParticipantFactApi.inspect")
    if not any("PROJECT_MANAGER" in value for value in recipient.get("input", [])):
        errors.append("project-manager recipient role is not frozen")

    notification = contract.get("events", {}).get("NotificationRequested", {})
    required_notification = {
        "notificationType": "DELIVERY_SCOPE_CONFLICT_FROZEN",
        "recipientFact": "ProjectParticipantFactApi.inspect(PROJECT_MANAGER)",
        "persistence": "SAME_COM_TRANSACTION_AS_CONFLICT_FREEZE_TO_COM_OUTBOX",
        "deliveryFailure": "RETRY_WITHOUT_ROLLBACK_UNLOCK_OR_CONFLICT_STATE_CHANGE",
    }
    for field, expected in required_notification.items():
        if notification.get(field) != expected:
            errors.append(f"invalid conflict notification {field}")
    if set(notification.get("idempotency", [])) != {
        "notificationType", "deliveryScopeId", "allocationVersion", "erpSourceVersion"
    }:
        errors.append("conflict notification idempotency is incomplete")
    for required_text in (
        "Provider异常/超时/不可用均失败关闭并保持COM零写入",
        "DELIVERY_SCOPE_CONFLICT_FROZEN",
        "不得回滚冲突冻结",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing Feature rule: {required_text}")
    return errors


def contract_administrator_scope_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    question = contract.get("openQuestions", {}).get("Q-FCOM-001", {})
    if question.get("status") != "RESOLVED" or question.get("blockingScope") != "NONE":
        errors.append("Q-FCOM-001 must be resolved without a remaining blocking scope")

    scope = contract.get("contractAdministratorScope", {})
    expected_scope = {
        "owner": "SYSTEM",
        "authorityFact": "CURRENT_ACTIVE_USER_COMPANY_DEPARTMENT_SCOPE",
        "companyMatch": "EXACT_NON_EMPTY_COMPANY_CODE_DISTINCT_UNION",
        "emptyOrUnavailable": "LIST_EMPTY_DETAIL_AND_WRITE_DENIED",
        "positiveAuthorizationCache": "NONE",
        "sensitiveFieldPermission": "pms:commerce:contract:sensitive-read",
    }
    for field, expected in expected_scope.items():
        if scope.get(field) != expected:
            errors.append(f"invalid contract-administrator scope {field}")
    if scope.get("departmentSemantics") != "AUTHORIZATION_CONTEXT_ONLY_NO_COMPANY_SCOPE_INFERENCE":
        errors.append("department facts must not infer company scope")
    if scope.get("writeRevalidation") != "REFETCH_OWNER_FACT_BEFORE_RELATION_WRITE":
        errors.append("relation writes must revalidate the current Owner fact")

    api = contract.get("moduleApis", {}).get("OrganizationScopeApi", {})
    if api.get("provider") != "EXISTING_UNMODIFIED_REAL_PROVIDER" or api.get("method") != "getActiveScopes":
        errors.append("contract scope must reuse OrganizationScopeApi.getActiveScopes")
    if "pms:commerce:contract:sensitive-read" not in contract.get("permissions", {}).get("functional", []):
        errors.append("sensitive contract fields need an independent permission key")

    for required_text in (
        "OrganizationScopeApi.getActiveScopes",
        "部门、主范围标记、scopeRole和项目关系均不得扩大或缩小该公司集合",
        "写入前重新读取当前scope",
        "pms:commerce:contract:sensitive-read",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing Feature rule: {required_text}")
    return errors


def managed_v72_seed_disposition_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    seed = contract.get("managedV72SeedDisposition", {})
    identity = seed.get("exactIdentity", {})
    expected_identity = {
        "sourceMigration": "V72__fproj002_v18_seed_and_menu.sql",
        "tenantId": 0,
        "creator": "seed",
        "updater": "seed",
        "sourceSystem": "SEED",
        "sourceKeyPrefix": "FPROJ002-V18-",
        "sourceEvidencePrefix": "FPROJ002-V18-",
        "projectId": 992002000000,
        "orderId": 992002399001,
    }
    for field, expected in expected_identity.items():
        if identity.get(field) != expected:
            errors.append(f"invalid managed V72 seed identity {field}")
    expected_ids = {
        "orderLineIds": [992002300001, 992002300002, 992002300003, 992002300004],
        "deliveryScopeIds": [992002310001, 992002310004],
        "detailIds": [992002320001, 992002320002, 992002320003, 992002320004],
    }
    for field, expected in expected_ids.items():
        if identity.get(field) != expected:
            errors.append(f"invalid managed V72 seed relation closure {field}")
    expected_relations = {
        "orderLineToOrder": {
            "992002300001": 992002399001,
            "992002300002": 992002399001,
            "992002300003": 992002399001,
            "992002300004": 992002399001,
        },
        "deliveryScopeToOrderLineAndProject": {
            "992002310001": [992002300001, 992002000000],
            "992002310004": [992002300004, 992002000000],
        },
        "detailToDeliveryScope": {
            "992002320001": 992002310001,
            "992002320002": 992002310001,
            "992002320003": 992002310001,
            "992002320004": 992002310004,
        },
    }
    if identity.get("relations") != expected_relations:
        errors.append("managed seed relation references are incomplete")
    if identity.get("matchPolicy") != "ALL_PREDICATES_AND_COMPLETE_RELATION_CLOSURE_OR_FAIL":
        errors.append("managed seed matching must fail on every partial identity")
    if seed.get("businessConversionParticipation") != "EXCLUDED_ONLY_AFTER_EXACT_IDENTITY_MATCH":
        errors.append("ordinary V70 rows must remain in strict business conversion")
    if seed.get("disposition") != "REBUILD_EXPLICIT_TARGET_FIXTURE_IN_SAME_FORWARD_MIGRATION":
        errors.append("managed seed fixture disposition is not explicit")
    if seed.get("ordinaryV70Policy") != "UNCHANGED_STRICT_OWNER_MAPPING_FAIL_BATCH":
        errors.append("ordinary V70 conversion was weakened")
    forbidden = set(seed.get("forbidden", []))
    for rule in (
        "ITEM_CODE_AS_PRODUCT_CODE",
        "PARTIAL_IDENTITY_SEED_BRANCH",
        "UNBOUNDED_SKIP_OR_DELETE",
        "SEED_CONSTANTS_IN_ORDINARY_V70_CONVERSION",
    ):
        if rule not in forbidden:
            errors.append(f"missing managed seed negative rule: {rule}")
    fixture = seed.get("seedOnlyTargetFixture", {})
    expected_fixture = {
        "preserveSourceIds": True,
        "orderId": 992002399001,
        "sourceSystem": "SEED",
        "sourceRecordKey": "FPROJ002-V18-ORDER",
        "sourceVersion": "1",
        "companyCode": "DPTECH-DEMO",
        "orderType": "SEED_FIXTURE",
        "orderNo": "FPROJ002-V18-ORDER",
        "status": "ENABLED",
        "creator": "seed",
        "updater": "seed",
        "projectId": 992002000000,
        "officeDepartmentCode": "OFFICE-HZ-DEMO",
        "officeDepartmentOwnerResolution": "EXACT_ENABLED_SYSTEM_DEPARTMENT_BY_CODE_AND_PROJECT_VERSION",
    }
    for field, expected in expected_fixture.items():
        if fixture.get(field) != expected:
            errors.append(f"invalid seed-only target fixture {field}")
    if set(fixture.get("detailSubjects", {})) != {
        "992002320001", "992002320002", "992002320003", "992002320004"
    }:
        errors.append("seed-only detail subjects are incomplete")
    if fixture.get("constantSemantics") != (
            "MANAGED_ACCEPTANCE_FIXTURE_ONLY_NOT_ERP_PRODUCT_DEVICE_OR_OFFICE_BUSINESS_FACT"):
        errors.append("seed constants are not isolated from business facts")

    ordinary = contract.get("v70Conversion", {})
    if "seedOnlyTargetFixture" in ordinary or "DPTECH-DEMO" in json.dumps(ordinary):
        errors.append("seed constants leaked into ordinary V70 conversion")
    for required_text in (
        "全部身份谓词及关系闭包同时命中",
        "部分命中或关系不完整时整批失败",
        "非种子V70行继续执行既有逐字段Owner解析",
        "不得用`item_code`推断产品编码",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing managed seed Feature rule: {required_text}")
    return errors


def product_code_compatibility_errors(contract: dict, feature_spec: str) -> list[str]:
    errors = []
    line = contract.get("physicalDelta", {}).get("tables", {}).get("com_sales_order_line", {})
    if line.get("fields", {}).get("product_code") != "varchar(64) NULL":
        errors.append("SalesOrderLine ERP product_code physical field is missing")
    authority = contract.get("moduleApis", {}).get("CommerceAuthorityWriteApi", {})
    if "productCode" not in authority.get("salesOrderLineInputFacts", []):
        errors.append("Authority input must carry ERP productCode")
    if "SAME_VERSION_DIFFERENT_PRODUCT_CODE_REJECTED" not in authority.get("successFacts", []):
        errors.append("productCode must participate in same-version payload conflict detection")
    forbidden = set(authority.get("forbiddenProductCodeSources", []))
    if forbidden != {"itemCode", "itemDescription", "productId", "clientField", "deliveryScopeHistory"}:
        errors.append("forbidden runtime productCode substitutes are incomplete")
    delivery = contract.get("moduleApis", {}).get("DeliveryScopeApi", {})
    if delivery.get("noSerialSubjectRule") != (
            "LOCKED_CONFIRMED_SALES_ORDER_LINE_NONBLANK_PRODUCT_CODE_CREATES_ONE_DETAIL_WITH_SCOPE_QUANTITY"):
        errors.append("no-SN compatibility detail subject is not deterministic")
    if delivery.get("noSerialFailureMode") != (
            "ZERO_SCOPE_HISTORY_OUTBOX_WRITES_ON_MISSING_BLANK_PENDING_AUTHORITY_OR_VERSION_CONFLICT"):
        errors.append("no-SN Owner failures must leave zero writes")
    fixture = contract.get("managedV72SeedDisposition", {}).get("seedOnlyTargetFixture", {})
    if fixture.get("salesOrderLineCompatibilityProductCodes") != {
        "992002300001": "FPROJ002-V18-COMPAT-001",
        "992002300002": "FPROJ002-V18-COMPAT-002",
        "992002300003": "FPROJ002-V18-COMPAT-003",
        "992002300004": "FPROJ002-V18-COMPAT-004",
    }:
        errors.append("managed compatibility productCode constants are not exact")
    legacy = contract.get("legacySourceReference", {})
    if legacy.get("runtimeUse") != "FORBIDDEN_IN_F_COM_001" or legacy.get("futureMigrationGate") != "AI-MIG-000":
        errors.append("legacy order tables must remain migration references only")
    for required_text in (
        "既有DTO签名不变",
        "不得使用`itemCode`、`productId`、客户端值、历史明细或普通业务种子常量替代",
        "`pm_order_data_from_erp`订单头",
        "`pm_project_product_line`项目订单仅作为历史来源及原始子单参照",
    ):
        if required_text not in feature_spec:
            errors.append(f"missing productCode compatibility Feature rule: {required_text}")
    return errors


class Fcom001FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))
        cls.migration_contract = json.loads(MIGRATION_CONTRACT.read_text(encoding="utf-8"))
        cls.database_design = DATABASE_DESIGN.read_text(encoding="utf-8")
        cls.module_mapping = MODULE_MAPPING.read_text(encoding="utf-8")
        cls.reuse_audit = REUSE_AUDIT.read_text(encoding="utf-8")
        cls.feature_index = FEATURE_INDEX.read_text(encoding="utf-8")
        cls.v161_migration = V161_MIGRATION.read_text(encoding="utf-8")

    def test_feature_ready_go_is_recorded_after_contract_scope_resolution(self) -> None:
        self.assertEqual("BASELINE_READY", self.contract["status"])
        self.assertEqual("GO_REQUIREMENT_CONVERGENCE_APPROVED_2026_09_02",
                         self.contract["featureReadyDecision"])
        self.assertEqual([], contract_administrator_scope_errors(self.contract, self.feature_spec))
        self.assertIn("不得由Technical Plan发明", self.feature_spec)
        self.assertIn("BASELINE", self.feature_index)
        self.assertIn("REQUIREMENT_CONVERGENCE_APPROVED", self.feature_index)

    def test_contract_scope_gate_rejects_missing_owner_provider_or_sensitive_permission(self) -> None:
        missing_provider = deepcopy(self.contract)
        del missing_provider["moduleApis"]["OrganizationScopeApi"]
        self.assertTrue(contract_administrator_scope_errors(missing_provider, self.feature_spec))

        missing_sensitive_permission = deepcopy(self.contract)
        missing_sensitive_permission["permissions"]["functional"].remove(
            "pms:commerce:contract:sensitive-read")
        self.assertTrue(contract_administrator_scope_errors(
            missing_sensitive_permission, self.feature_spec))

    def test_managed_v72_seed_isolated_from_ordinary_v70_conversion(self) -> None:
        self.assertEqual([], managed_v72_seed_disposition_errors(self.contract, self.feature_spec))

    def test_managed_v72_seed_gate_rejects_partial_identity_or_constant_leakage(self) -> None:
        mutations = []

        partial_identity = deepcopy(self.contract)
        if "managedV72SeedDisposition" in partial_identity:
            partial_identity["managedV72SeedDisposition"]["exactIdentity"].pop("detailIds")
        mutations.append(("partial relation closure", partial_identity))

        ordinary_by_creator = deepcopy(self.contract)
        if "managedV72SeedDisposition" in ordinary_by_creator:
            ordinary_by_creator["managedV72SeedDisposition"]["exactIdentity"]["matchPolicy"] = "CREATOR_ONLY"
        mutations.append(("ordinary row by creator", ordinary_by_creator))

        inferred_item = deepcopy(self.contract)
        if "managedV72SeedDisposition" in inferred_item:
            inferred_item["managedV72SeedDisposition"]["forbidden"].remove("ITEM_CODE_AS_PRODUCT_CODE")
        mutations.append(("item code inference", inferred_item))

        unbounded_skip = deepcopy(self.contract)
        if "managedV72SeedDisposition" in unbounded_skip:
            unbounded_skip["managedV72SeedDisposition"]["disposition"] = "SKIP_OR_DELETE_SOURCE_ROWS"
        mutations.append(("unbounded skip", unbounded_skip))

        leaked_constant = deepcopy(self.contract)
        leaked_constant["v70Conversion"]["seedOnlyTargetFixture"] = {"companyCode": "DPTECH-DEMO"}
        mutations.append(("seed constant leakage", leaked_constant))

        for label, candidate in mutations:
            with self.subTest(label=label):
                self.assertTrue(managed_v72_seed_disposition_errors(candidate, self.feature_spec))

    def test_no_serial_compatibility_uses_only_locked_erp_product_code(self) -> None:
        self.assertEqual([], product_code_compatibility_errors(self.contract, self.feature_spec))
        self.assertIn("ADD COLUMN `product_code` varchar(64) NULL", self.v161_migration)
        for order_line_id in (992002300001, 992002300002, 992002300003, 992002300004):
            self.assertIn(str(order_line_id), self.v161_migration)
        self.assertIn("fcom001_verify_v161_managed_product_codes", self.v161_migration)
        self.assertIn("pm_order_line_from_erp", self.reuse_audit)
        self.assertIn("pm_project_product_line", self.reuse_audit)

    def test_product_code_gate_rejects_inference_or_unbounded_seed_fallback(self) -> None:
        missing_owner_input = deepcopy(self.contract)
        missing_owner_input["moduleApis"]["CommerceAuthorityWriteApi"]["salesOrderLineInputFacts"].remove(
            "productCode")
        self.assertTrue(product_code_compatibility_errors(missing_owner_input, self.feature_spec))

        inferred_item = deepcopy(self.contract)
        inferred_item["moduleApis"]["CommerceAuthorityWriteApi"]["forbiddenProductCodeSources"].remove(
            "itemCode")
        self.assertTrue(product_code_compatibility_errors(inferred_item, self.feature_spec))

        unbounded_seed = deepcopy(self.contract)
        unbounded_seed["managedV72SeedDisposition"]["seedOnlyTargetFixture"][
            "salesOrderLineCompatibilityProductCodes"] = {"*": "SEED-PRODUCT"}
        self.assertTrue(product_code_compatibility_errors(unbounded_seed, self.feature_spec))

        runtime_legacy = deepcopy(self.contract)
        runtime_legacy["legacySourceReference"]["runtimeUse"] = "DIRECT_TABLE_READ"
        self.assertTrue(product_code_compatibility_errors(runtime_legacy, self.feature_spec))

        missing_parent_version = deepcopy(self.contract)
        missing_parent_version["moduleApis"]["DeliveryScopeApi"]["applyCommandAdditiveInputs"].remove(
            "expectedParentProjectVersion")
        self.assertTrue(product_code_compatibility_errors(missing_parent_version, self.feature_spec))

    def test_v161_registers_exact_permissions_and_controlled_acceptance_identity(self) -> None:
        permissions = set(self.contract["permissions"]["functional"])
        self.assertEqual(9, len(permissions))
        for permission in permissions:
            with self.subTest(permission=permission):
                self.assertIn(f"'{permission}'", self.v161_migration)
        self.assertIn("fcom001_acceptance_full", self.v161_migration)
        self.assertIn("仅用于F-COM-001本地真实浏览器正向闭环", self.v161_migration)
        self.assertNotIn("pm_order_data_from_erp", self.v161_migration)
        self.assertNotIn("pm_order_line_from_erp", self.v161_migration)
        self.assertNotIn("pm_project_product_line", self.v161_migration)

    def test_office_snapshot_replaces_ast_location_without_inference(self) -> None:
        self.assertNotIn("AssetLocationApi", self.contract["moduleApis"])
        physical_fields = {
            field
            for table in self.contract["physicalDelta"]["tables"].values()
            for field in table.get("fields", {})
        }
        self.assertTrue({"site_id", "site_location_id", "location_text"}.isdisjoint(physical_fields))
        self.assertIn("在COM范围或明细保存`siteId/siteLocationId/locationText`第二套地点真值",
                      self.feature_spec)
        office = self.contract["officeSnapshot"]
        self.assertEqual("PROJ", office["owner"])
        self.assertEqual(
            ["projectId", "officeDepartmentId", "officeDepartmentCode", "officeDepartmentName", "officeDepartmentVersion"],
            office["frozenFields"],
        )
        self.assertIn("NO_AST_ADDRESS_NAME_OR_ORDER_INFERENCE", office["inferencePolicy"])
        self.assertIn("办事处发生时快照", self.reuse_audit)

    def test_feature_forward_delta_matches_the_approved_sds_gate(self) -> None:
        self.assertEqual(
            "P3_E09_FEATURE_FORWARD_DELTAS_008_009_REVIEWED_GO",
            self.contract["phaseGateImpact"],
        )
        tables = self.contract["physicalDelta"]["tables"]
        self.assertEqual("decimal(18,6) NOT NULL", tables["com_delivery_scope"]["fields"]["allocated_qty"])
        self.assertEqual("tinyint unsigned NOT NULL", tables["com_sales_order_line"]["fields"]["unit_scale"])
        self.assertIn("implementation_location", tables["com_delivery_scope_detail"]["removedFields"])
        self.assertNotIn("acceptance_id", tables["acc_acceptance_scope_binding"]["fields"])
        self.assertEqual("bigint NOT NULL", tables["acc_acceptance_scope_binding"]["fields"]["project_stage_snapshot_id"])

    def test_owner_apis_and_atomic_binding_paths_are_explicit(self) -> None:
        carriers = self.contract["implementationCarriers"]
        self.assertEqual(["pms-module-commerce-api", "pms-module-commerce"], carriers["COM"])
        self.assertEqual(["pms-module-project-api", "pms-module-project"], carriers["PROJ"])
        self.assertEqual(["pms-module-project-api", "pms-module-project"], carriers["ACC"])
        self.assertIn("语义Owner仍分别为PROJ/ACC", self.feature_spec)
        self.assertIn("`pms-module-project` | 项目承接、项目团队、项目组合、项目层级、任务WBS、里程碑、风险、问题、验收与闭环", self.module_mapping)
        apis = self.contract["moduleApis"]
        for api in (
            "ProjectOfficeFactApi",
            "ProjectAcceptanceStageFactApi",
            "AcceptanceScopeGuardApi",
            "DeliveryScopeAcceptanceLockApi",
            "AcceptanceScopeBindingApi",
        ):
            with self.subTest(api=api):
                self.assertIn(api, apis)
                self.assertEqual("REAL_PROVIDER_REQUIRED", apis[api]["provider"])
        self.assertEqual(
            ["PROJ_PROJECT", "COM_ORDER_LINE_IF_APPLICABLE", "COM_DELIVERY_SCOPE_BY_STABLE_ID",
             "COM_DELIVERY_SCOPE_PROJECT_VERSION_BY_PROJECT_ID", "ACC_SCOPE_BINDING"],
            self.contract["transactionBoundary"]["lockOrder"],
        )
        self.assertIn("PROJECT_STAGE_ENTRY", self.contract["acceptanceBinding"]["triggers"])
        self.assertIn("SCOPE_VERSION_EFFECTIVE", self.contract["acceptanceBinding"]["triggers"])
        self.assertFalse(self.contract["acceptanceBinding"]["reportTriggersBinding"])

    def test_real_provider_carriers_are_audited_and_reports_cannot_become_bindings(self) -> None:
        self.assertEqual([], provider_reuse_audit_errors(self.reuse_audit))
        for evidence in (
            "ProjectOrganizationFactApiImplTest",
            "ProjectStageSnapshotRulesTest",
            "ProjectGovernanceApplicationServiceTest",
            "ProjectClosureStateAdapterTest",
            "V17__pms_acceptance_tables.sql",
            "OrganizationScopeApiImplTest",
        ):
            with self.subTest(evidence=evidence):
                self.assertIn(evidence, self.reuse_audit)

    def test_reuse_audit_guard_rejects_missing_carriers_or_report_binding_reuse(self) -> None:
        missing_provider = self.reuse_audit.replace("ProjectMasterDO/Mapper", "ProjectMasterCarrier")
        self.assertTrue(provider_reuse_audit_errors(missing_provider))

        report_reused = self.reuse_audit.replace(
            "`AcceptanceController`、`AcceptanceService/Impl`、`AcceptanceDO/Mapper`与`pms_acc_acceptance`（`V17__pms_acceptance_tables.sql`） | `DO_NOT_REUSE`",
            "`AcceptanceController`、`AcceptanceService/Impl`、`AcceptanceDO/Mapper`与`pms_acc_acceptance`（`V17__pms_acceptance_tables.sql`） | `DIRECT_REUSE`",
            1,
        )
        self.assertTrue(provider_reuse_audit_errors(report_reused))

    def test_conflict_notification_and_direct_serial_owner_guard_are_complete(self) -> None:
        self.assertEqual([], conflict_notification_and_serial_guard_errors(self.contract, self.feature_spec))
        self.assertEqual(["pms-module-asset-api", "pms-module-asset"],
                         self.contract["implementationCarriers"]["AST"])
        self.assertEqual([], provider_reuse_audit_errors(self.reuse_audit))

    def test_feature_gate_rejects_missing_notification_or_reusable_preview_validation(self) -> None:
        missing_notification = deepcopy(self.contract)
        del missing_notification["events"]["NotificationRequested"]
        self.assertTrue(conflict_notification_and_serial_guard_errors(
            missing_notification, self.feature_spec))

        reusable_preview = deepcopy(self.contract)
        reusable_preview["moduleApis"]["AssetDeviceScopeApi"]["versionBehavior"] = "CACHE_PREVIEW_RESULT"
        self.assertTrue(conflict_notification_and_serial_guard_errors(
            reusable_preview, self.feature_spec))

    def test_v70_required_targets_and_deterministic_detail_sequence_are_frozen(self) -> None:
        mappings = self.contract["v70Conversion"]["requiredTargetMappings"]
        expected = {
            "com_sales_order_line.status",
            "com_delivery_scope.project_code",
            "com_delivery_scope.order_source_system",
            "com_delivery_scope.order_company_code",
            "com_delivery_scope.order_type",
            "com_delivery_scope.order_no",
            "com_delivery_scope.line_no",
            "com_delivery_scope.allocation_source",
            "com_delivery_scope.status",
            "com_delivery_scope_detail.detail_sequence",
        }
        self.assertEqual(expected, set(mappings))
        self.assertIn("ROW_NUMBER", mappings["com_delivery_scope_detail.detail_sequence"])
        self.assertEqual("FAIL_BATCH", self.contract["v70Conversion"]["missingOrConflict"])
        managed = {
            record["object"]: next(
                source
                for source in record["sources"]
                if source.get("gate") == "F-COM-001" and "requiredTargetMappings" in source
            )["requiredTargetMappings"]
            for record in self.migration_contract["records"]
            if record["object"] in {"OrderLine", "DeliveryScope", "DeliveryScopeDetail"}
        }
        flattened = {
            target: rule
            for object_mappings in managed.values()
            for target, rule in object_mappings.items()
        }
        self.assertEqual(flattened, mappings)

    def test_q_fcom002_blocks_only_close_or_unlock(self) -> None:
        question = self.contract["openQuestions"]["Q-FCOM-002"]
        self.assertEqual("BLOCKED_BY_SPEC", question["status"])
        self.assertEqual("EXIT_ROLLBACK_BINDING_CLOSE_OR_UNLOCK_ONLY", question["blockingScope"])
        self.assertTrue(question["confirmedEntryAndNewVersionPathsRemainImplementable"])

    def test_every_feature_forward_field_is_backed_by_the_approved_p3_e09_delta(self) -> None:
        grouped = {
            ("com_sales_order_line", "order_qty"): "order_qty/open_qty/delivered_qty",
            ("com_sales_order_line", "open_qty"): "order_qty/open_qty/delivered_qty",
            ("com_sales_order_line", "delivered_qty"): "order_qty/open_qty/delivered_qty",
            ("acc_acceptance_scope_binding", "creator"): "creator/updater",
            ("acc_acceptance_scope_binding", "updater"): "creator/updater",
            ("acc_acceptance_scope_binding", "create_time"): "create_time/update_time",
            ("acc_acceptance_scope_binding", "update_time"): "create_time/update_time",
        }
        for table, table_contract in self.contract["physicalDelta"]["tables"].items():
            self.assertIn(f"`{table}`", self.database_design)
            for field, definition in table_contract["fields"].items():
                with self.subTest(table=table, field=field):
                    evidence_field = grouped.get((table, field), field)
                    self.assertIn(evidence_field, self.database_design)
                    self.assertIn(definition, self.database_design)
        binding_row = next(
            line
            for line in self.database_design.splitlines()
            if line.startswith("| `acc_acceptance_scope_binding`")
        )
        self.assertNotIn("acceptance_id", self.contract["physicalDelta"]["tables"]["acc_acceptance_scope_binding"]["fields"])
        self.assertIn("不含`acceptance_id`", binding_row)
        self.assertIn("project_stage_snapshot_id bigint NOT NULL", binding_row)


if __name__ == "__main__":
    unittest.main()
