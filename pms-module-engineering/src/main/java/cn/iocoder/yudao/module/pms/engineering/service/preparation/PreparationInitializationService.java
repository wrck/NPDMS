package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationBusinessVersionQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalog;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormRules;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationTemplateRules;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_FIXED_FORM_CATALOG_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_VERSION_NOT_MATCH;

@Service
@RequiredArgsConstructor
public class PreparationInitializationService {

    public static final String PREPARATION_TYPE = "PRE_02_SITE_SURVEY";
    public static final String PERMISSION_MANAGE = "pms:preparation-survey:manage";
    private static final String IDEMPOTENCY_SCOPE = "PREPARATION_INITIALIZE";
    private static final String ACTIVE = "ACTIVE";

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final DynamicFormInstanceMapper formMapper;
    private final FixedSurveyFormCatalogProvider catalogProvider;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectScopeApi projectScopeApi;
    private final PermissionApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public PreparationInitializationResult initialize(PreparationInitializationCommand command) {
        validate(command);
        Long tenantId = trustedTenantId();
        try {
            return transactionTemplate.execute(status -> initializeInTransaction(tenantId, command));
        } catch (RuntimeException failure) {
            auditRejected(tenantId, command, failure);
            throw failure;
        }
    }

    private PreparationInitializationResult initializeInTransaction(
            Long tenantId, PreparationInitializationCommand command) {
        if (PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY.equals(command.triggerType())) {
            authorizeRecovery(tenantId, command);
        }
        ProjectWorkBindingFact fact = workBindingFactApi.lockAndRevalidate(
                new ProjectWorkBindingFactRevalidationQuery(command.projectId(), command.projectTaskId(),
                        command.executionContractId(), command.expectedProjectTaskVersion(),
                        command.expectedContractVersion(), command.expectedProjectVersion()));
        requireExactFact(command, fact);
        if (PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY.equals(command.triggerType())) {
            participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                    command.projectId(), command.actorUserId(), command.expectedProjectVersion(),
                    ACTIVE, null, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        }

        PreparationDO existing = preparationMapper.selectBusinessVersionForUpdate(
                new PreparationBusinessVersionQuery(tenantId, command.projectId(), PREPARATION_TYPE, 1));
        if (existing != null) {
            requireSameInitialization(existing, fact);
            if (!PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY.equals(command.triggerType())) {
                throw exception(PREPARATION_STATUS_INVALID);
            }
            auditRecoveryNoChange(tenantId, command, existing);
            return result(existing);
        }

        String requestDigest = requestDigest(command);
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, IDEMPOTENCY_SCOPE, command.actorUserId(), command.idempotencyKey()),
                requestDigest, PreparationInitializationResult.class,
                () -> createOnce(tenantId, command, fact),
                response -> successFacts(command, fact, response));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        return execution.response();
    }

    private PreparationInitializationResult createOnce(
            Long tenantId, PreparationInitializationCommand command, ProjectWorkBindingFact fact) {
        FixedSurveyFormCatalog catalog = catalogProvider.load();
        if (!Objects.equals(catalog.catalogVersion(), fact.fixedFormCatalogVersion())) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        List<PreparationTemplateRules.ItemDefinition> definitions =
                PreparationTemplateRules.parse(fact.itemConfigurationSnapshot());
        LocalDateTime now = LocalDateTime.now();
        String actor = String.valueOf(command.actorUserId());

        PreparationDO preparation = new PreparationDO();
        preparation.setTenantId(tenantId);
        preparation.setProjectId(command.projectId());
        preparation.setPreparationTypeCode(PREPARATION_TYPE);
        preparation.setBusinessVersion(1);
        preparation.setCurrentMarker(1);
        preparation.setTemplateId(fact.templateTaskDefinitionId());
        preparation.setTemplateRevisionId(fact.sourceDefinitionVersion().longValue());
        preparation.setTemplateSnapshot(templateSnapshot(fact));
        preparation.setFixedFormCatalogVersion(fact.fixedFormCatalogVersion());
        preparation.setStatusCode("DRAFT");
        preparation.setReadinessStatusCode("NOT_READY");
        preparation.setInputVersion(0);
        preparation.setReadinessVersion(0);
        preparation.setSnapshotCurrent(false);
        preparation.setVersion(0);
        preparation.setCreator(actor);
        preparation.setUpdater(actor);
        preparation.setCreateTime(now);
        preparation.setUpdateTime(now);
        if (preparationMapper.insert(preparation) != 1 || preparation.getId() == null) {
            throw new IllegalStateException("PREPARATION_INITIALIZE_FAILED");
        }

        for (PreparationTemplateRules.ItemDefinition definition : definitions) {
            if (!Boolean.TRUE.equals(definition.enabled())) continue;
            String schema = freezeSchema(catalog, definition);
            PreparationItemDO item = new PreparationItemDO();
            item.setTenantId(tenantId);
            item.setPreparationId(preparation.getId());
            item.setItemCode(definition.itemCode());
            item.setItemName(definition.itemName());
            item.setSortOrder(definition.sortOrder());
            item.setApplicabilityCode("REQUIRED");
            item.setConfirmationStatusCode("PENDING");
            item.setFormCode(definition.formCode());
            item.setFormVersion(definition.formVersion());
            item.setFormSchemaSnapshot(schema);
            item.setEvidencePolicySnapshot(JsonUtils.toJsonString(
                    Map.of("required", definition.evidenceRequired())));
            item.setSourcePolicySnapshot(JsonUtils.toJsonString(
                    Map.of("requirementCode", definition.sourceRequirementCode())));
            item.setWaiverPolicySnapshot(JsonUtils.toJsonString(Map.of(
                    "allowed", definition.waiverAllowed(),
                    "approvalRoleCode", definition.approvalRoleCode())));
            item.setOutsourced(false);
            item.setVersion(0);
            item.setCreator(actor);
            item.setUpdater(actor);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            if (itemMapper.insert(item) != 1 || item.getId() == null) {
                throw new IllegalStateException("PREPARATION_ITEM_INITIALIZE_FAILED");
            }

            DynamicFormInstanceDO form = new DynamicFormInstanceDO();
            form.setTenantId(tenantId);
            form.setPreparationId(preparation.getId());
            form.setItemId(item.getId());
            form.setFormCode(definition.formCode());
            form.setFormVersion(definition.formVersion());
            form.setSchemaSnapshot(schema);
            form.setValueSnapshot("{}");
            form.setStatusCode("DRAFT");
            form.setVersion(0);
            form.setCreator(actor);
            form.setUpdater(actor);
            form.setCreateTime(now);
            form.setUpdateTime(now);
            if (formMapper.insert(form) != 1 || form.getId() == null) {
                throw new IllegalStateException("PREPARATION_FORM_INITIALIZE_FAILED");
            }
        }
        return result(preparation);
    }

    private String freezeSchema(FixedSurveyFormCatalog catalog,
                                PreparationTemplateRules.ItemDefinition definition) {
        FixedSurveyFormCatalog.FormDefinition form = catalog.forms().stream()
                .filter(candidate -> candidate.formCode().equals(definition.formCode())
                        && candidate.formVersion().equals(definition.formVersion()))
                .findFirst().orElseThrow(() -> exception(PREPARATION_FIXED_FORM_CATALOG_INVALID));
        return FixedSurveyFormRules.freeze(catalog.schemaVersion(), form, catalog.commonFields());
    }

    private void authorizeRecovery(Long tenantId, PreparationInitializationCommand command) {
        if (!permissionApi.hasAnyPermissions(command.actorUserId(), PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, command.actorUserId(), command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(command.projectId())) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private void requireExactFact(PreparationInitializationCommand command, ProjectWorkBindingFact fact) {
        if (fact == null || !Objects.equals(fact.projectId(), command.projectId())
                || !Objects.equals(fact.projectTaskId(), command.projectTaskId())
                || !Objects.equals(fact.executionContractId(), command.executionContractId())
                || !Objects.equals(fact.projectVersion(), command.expectedProjectVersion())
                || !Objects.equals(fact.projectTaskVersion(), command.expectedProjectTaskVersion())
                || !Objects.equals(fact.contractVersion(), command.expectedContractVersion())
                || fact.templateTaskDefinitionId() == null || fact.sourceDefinitionVersion() == null
                || fact.fixedFormCatalogVersion() == null || fact.itemConfigurationSnapshot() == null) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private void requireSameInitialization(PreparationDO existing, ProjectWorkBindingFact fact) {
        if (!Integer.valueOf(1).equals(existing.getBusinessVersion())
                || !Integer.valueOf(1).equals(existing.getCurrentMarker())
                || !Objects.equals(existing.getTemplateId(), fact.templateTaskDefinitionId())
                || !Objects.equals(existing.getTemplateRevisionId(), fact.sourceDefinitionVersion().longValue())
                || !Objects.equals(existing.getFixedFormCatalogVersion(), fact.fixedFormCatalogVersion())
                || !Objects.equals(existing.getTemplateSnapshot(), templateSnapshot(fact))) {
            throw exception(PREPARATION_STATUS_INVALID);
        }
    }

    private String templateSnapshot(ProjectWorkBindingFact fact) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectTaskId", fact.projectTaskId());
        snapshot.put("projectTaskVersion", fact.projectTaskVersion());
        snapshot.put("executionContractId", fact.executionContractId());
        snapshot.put("contractVersion", fact.contractVersion());
        snapshot.put("templateTaskDefinitionId", fact.templateTaskDefinitionId());
        snapshot.put("sourceDefinitionVersion", fact.sourceDefinitionVersion());
        snapshot.put("preparationTemplateCode", fact.preparationTemplateCode());
        snapshot.put("preparationTemplateRevision", fact.preparationTemplateRevision());
        snapshot.put("fixedFormCatalogVersion", fact.fixedFormCatalogVersion());
        snapshot.put("itemConfiguration", JsonUtils.parseTree(fact.itemConfigurationSnapshot()));
        return JsonUtils.toJsonString(snapshot);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            PreparationInitializationCommand command, ProjectWorkBindingFact fact,
            PreparationInitializationResult response) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", command.projectId());
        detail.put("preparationId", response.preparationId());
        detail.put("businessVersion", response.businessVersion());
        detail.put("projectTaskId", fact.projectTaskId());
        detail.put("executionContractId", fact.executionContractId());
        detail.put("projectVersion", fact.projectVersion());
        detail.put("projectTaskVersion", fact.projectTaskVersion());
        detail.put("contractVersion", fact.contractVersion());
        detail.put("statusBefore", "NONE");
        detail.put("statusAfter", "DRAFT");
        detail.put("preparationVersionAfter", response.preparationVersion());
        detail.put("triggerType", command.triggerType());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "PREPARATION_INITIALIZE", "Preparation", String.valueOf(response.preparationId()),
                command.operationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRecoveryNoChange(Long tenantId, PreparationInitializationCommand command,
                                       PreparationDO preparation) {
        operationAuditApi.record(tenantId, command.actorUserId(), command.operationId(),
                "PREPARATION_INITIALIZATION_RECOVERY", "Preparation", String.valueOf(preparation.getId()),
                "NO_CHANGE", Map.of("projectId", command.projectId(),
                        "preparationId", preparation.getId(), "businessVersion", preparation.getBusinessVersion(),
                        "preparationVersion", preparation.getVersion()));
    }

    private void auditRejected(Long tenantId, PreparationInitializationCommand command, RuntimeException failure) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", command.projectId());
        detail.put("projectTaskId", command.projectTaskId());
        detail.put("executionContractId", command.executionContractId());
        detail.put("triggerType", command.triggerType());
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(tenantId, command.actorUserId(), command.operationId(),
                "PREPARATION_INITIALIZE", "Preparation", String.valueOf(command.projectId()),
                "REJECTED", Map.copyOf(detail));
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceException) {
            return String.valueOf(serviceException.getCode());
        }
        return "PREPARATION_INITIALIZE_FAILED";
    }

    private PreparationInitializationResult result(PreparationDO preparation) {
        return new PreparationInitializationResult(preparation.getId(), preparation.getProjectId(),
                preparation.getBusinessVersion(), preparation.getVersion());
    }

    private String requestDigest(PreparationInitializationCommand command) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("projectId", command.projectId());
        facts.put("projectTaskId", command.projectTaskId());
        facts.put("executionContractId", command.executionContractId());
        facts.put("expectedProjectVersion", command.expectedProjectVersion());
        facts.put("expectedProjectTaskVersion", command.expectedProjectTaskVersion());
        facts.put("expectedContractVersion", command.expectedContractVersion());
        facts.put("triggerType", command.triggerType());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(facts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) throw exception(PREPARATION_COMMAND_INVALID);
        return tenantId;
    }

    private void validate(PreparationInitializationCommand command) {
        if (command == null || invalidId(command.projectId()) || invalidId(command.projectTaskId())
                || invalidId(command.executionContractId()) || invalidVersion(command.expectedProjectVersion())
                || invalidVersion(command.expectedProjectTaskVersion())
                || invalidVersion(command.expectedContractVersion()) || invalidId(command.actorUserId())
                || !Set.of(PreparationInitializationApi.TRIGGER_PROJECT_CREATION,
                        PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY).contains(command.triggerType())
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.operationId() == null || command.operationId().isBlank()) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private boolean invalidId(Long value) {
        return value == null || value <= 0;
    }

    private boolean invalidVersion(Integer value) {
        return value == null || value < 0;
    }
}
