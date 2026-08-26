package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.*;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormRules;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationStateRules;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewResult;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class PreparationReviewService {

    private static final String SCOPE = "PREPARATION_REVIEW";
    private static final Set<String> ACTIONS = Set.of(PreparationReviewCommand.SUBMIT,
            PreparationReviewCommand.CONFIRM, PreparationReviewCommand.CONFIRM_NOT_APPLICABLE,
            PreparationReviewCommand.RETURN);

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final DynamicFormInstanceMapper formMapper;
    private final PreparationSourceReferenceMapper sourceMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final FileArtifactApi fileArtifactApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public PreparationReviewResult execute(PreparationReviewCommand command,
                                           PreparationItemApplicationService.Actor actor) {
        validate(command, actor);
        try {
            return transactionTemplate.execute(status -> executeInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(command, actor, failure);
            throw failure;
        }
    }

    private PreparationReviewResult executeInTransaction(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor) {
        PreparationDO located = preparationMapper.selectById(
                new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        authorizeManager(located.getProjectId(), command.expectedProjectVersion(), actor);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE + "_" + command.action(), actor.actorId(), command.idempotencyKey()),
                digest(command), PreparationReviewResult.class,
                () -> perform(command, actor, located.getProjectId()),
                response -> successFacts(command, actor, response));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        return execution.response();
    }

    private PreparationReviewResult perform(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor, Long projectId) {
        PreparationDO preparation = preparationMapper.selectForUpdate(
                new PreparationRowQuery(actor.tenantId(), command.preparationId()));
        if (preparation == null || !Objects.equals(preparation.getProjectId(), projectId)
                || !Objects.equals(preparation.getVersion(), command.expectedPreparationVersion())
                || !Integer.valueOf(1).equals(preparation.getCurrentMarker())) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        List<PreparationItemDO> items = itemMapper.selectListForUpdate(
                new PreparationChildrenQuery(actor.tenantId(), preparation.getId()));
        List<DynamicFormInstanceDO> forms = formMapper.selectListForUpdate(
                new PreparationChildrenQuery(actor.tenantId(), preparation.getId()));
        if (items.isEmpty() || forms.size() != items.size()) throw exception(PREPARATION_STATUS_INVALID);
        Map<Long, DynamicFormInstanceDO> formsByItem = new LinkedHashMap<>();
        for (DynamicFormInstanceDO form : forms) {
            if (formsByItem.put(form.getItemId(), form) != null) throw exception(PREPARATION_STATUS_INVALID);
        }
        return switch (command.action()) {
            case PreparationReviewCommand.SUBMIT -> submit(command, actor, preparation, items, formsByItem);
            case PreparationReviewCommand.CONFIRM, PreparationReviewCommand.CONFIRM_NOT_APPLICABLE ->
                    confirm(command, actor, preparation, items);
            case PreparationReviewCommand.RETURN ->
                    returnToDraft(command, actor, preparation, items, forms);
            default -> throw exception(PREPARATION_COMMAND_INVALID);
        };
    }

    private PreparationReviewResult submit(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO preparation,
            List<PreparationItemDO> items, Map<Long, DynamicFormInstanceDO> forms) {
        PreparationStateRules.requirePreparationTransition(preparation.getStatusCode(), "PENDING_CONFIRMATION");
        LocalDateTime now = LocalDateTime.now();
        for (PreparationItemDO item : items) {
            DynamicFormInstanceDO form = forms.get(item.getId());
            if (form == null || !"DRAFT".equals(form.getStatusCode()) || form.getFrozenAt() != null) {
                throw exception(PREPARATION_STATUS_INVALID);
            }
            if ("REQUIRED".equals(item.getApplicabilityCode())) {
                FixedSurveyFormRules.validateAndNormalizeValue(form.getSchemaSnapshot(), form.getValueSnapshot());
                if (item.getAssigneeUserId() == null || item.getSiteResultCode() == null
                        || item.getSiteResultCode().isBlank()) throw exception(PREPARATION_STATUS_INVALID);
                validateEvidence(item, actor);
            } else if ("NOT_APPLICABLE_PENDING".equals(item.getApplicabilityCode())) {
                if (item.getNotApplicableReason() == null || item.getNotApplicableReason().isBlank()) {
                    throw exception(PREPARATION_STATUS_INVALID);
                }
            } else {
                throw exception(PREPARATION_STATUS_INVALID);
            }
            if (formMapper.freezeIfMatch(new DynamicFormFreezeUpdate(actor.tenantId(), preparation.getId(),
                    item.getId(), form.getId(), form.getVersion(), now, actor.actorId(),
                    String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        if (preparationMapper.updateLifecycleIfMatch(new PreparationLifecycleUpdate(actor.tenantId(),
                preparation.getId(), preparation.getVersion(), "DRAFT", "PENDING_CONFIRMATION",
                now, null, null, null, String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        return result(preparation, "PENDING_CONFIRMATION", preparation.getVersion() + 1, preparation.getId());
    }

    private PreparationReviewResult confirm(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO preparation,
            List<PreparationItemDO> items) {
        if (!"PENDING_CONFIRMATION".equals(preparation.getStatusCode())) {
            throw exception(PREPARATION_STATUS_INVALID);
        }
        PreparationItemDO selected = requireItem(items, command);
        String applicability = selected.getApplicabilityCode();
        if (PreparationReviewCommand.CONFIRM.equals(command.action())) {
            if (!"REQUIRED".equals(applicability)) throw exception(PREPARATION_STATUS_INVALID);
        } else {
            PreparationStateRules.requireApplicabilityTransition(preparation.getStatusCode(), applicability,
                    "NOT_APPLICABLE_CONFIRMED");
            if (command.reason() == null || command.reason().isBlank()) throw exception(PREPARATION_COMMAND_INVALID);
            applicability = "NOT_APPLICABLE_CONFIRMED";
        }
        PreparationStateRules.requireItemConfirmationTransition(preparation.getStatusCode(),
                selected.getApplicabilityCode(), selected.getConfirmationStatusCode(), "CONFIRMED");
        LocalDateTime now = LocalDateTime.now();
        if (itemMapper.updateReviewIfMatch(new PreparationItemReviewUpdate(actor.tenantId(), preparation.getId(),
                selected.getId(), selected.getVersion(), selected.getConfirmationStatusCode(), applicability,
                "CONFIRMED", command.reason(), actor.actorId(), now, null,
                String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
        if (preparationMapper.invalidateReadinessIfMatch(new PreparationInputInvalidationUpdate(actor.tenantId(),
                preparation.getId(), preparation.getVersion(), preparation.getInputVersion(),
                preparation.getReadinessVersion(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        String confirmedApplicability = applicability;
        List<PreparationStateRules.ItemState> states = items.stream().map(item -> new PreparationStateRules.ItemState(
                item.getId().equals(selected.getId()) ? confirmedApplicability : item.getApplicabilityCode(),
                item.getId().equals(selected.getId()) ? "CONFIRMED" : item.getConfirmationStatusCode())).toList();
        boolean complete = PreparationStateRules.allItemsConfirmed(states);
        int version = preparation.getVersion() + 1;
        String status = preparation.getStatusCode();
        if (complete) {
            if (preparationMapper.updateLifecycleIfMatch(new PreparationLifecycleUpdate(actor.tenantId(),
                    preparation.getId(), version, "PENDING_CONFIRMATION", "CONFIRMED",
                    preparation.getSubmittedAt(), now, null, null,
                    String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
            version++;
            status = "CONFIRMED";
        }
        return result(preparation, status, version, preparation.getId());
    }

    private PreparationReviewResult returnToDraft(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor, PreparationDO oldPreparation,
            List<PreparationItemDO> oldItems, List<DynamicFormInstanceDO> oldForms) {
        if (command.reason() == null || command.reason().isBlank()) throw exception(PREPARATION_COMMAND_INVALID);
        PreparationStateRules.requirePreparationTransition(oldPreparation.getStatusCode(), "RETURNED");
        PreparationItemDO selected = requireItem(oldItems, command);
        PreparationStateRules.requireItemConfirmationTransition(oldPreparation.getStatusCode(),
                selected.getApplicabilityCode(), selected.getConfirmationStatusCode(), "RETURNED");
        List<PreparationSourceReferenceDO> sources = sourceMapper.selectListForUpdate(
                new PreparationChildrenQuery(actor.tenantId(), oldPreparation.getId()));
        LocalDateTime now = LocalDateTime.now();
        String actorText = String.valueOf(actor.actorId());
        if (itemMapper.updateReviewIfMatch(new PreparationItemReviewUpdate(actor.tenantId(), oldPreparation.getId(),
                selected.getId(), selected.getVersion(), selected.getConfirmationStatusCode(),
                selected.getApplicabilityCode(), "RETURNED", selected.getNotApplicableReason(), actor.actorId(), now,
                command.reason(), actorText)) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
        if (preparationMapper.updateLifecycleIfMatch(new PreparationLifecycleUpdate(actor.tenantId(),
                oldPreparation.getId(), oldPreparation.getVersion(), oldPreparation.getStatusCode(), "RETURNED",
                oldPreparation.getSubmittedAt(), oldPreparation.getConfirmedAt(), now, command.reason(), actorText)) != 1
                || preparationMapper.clearCurrentMarkerIfMatch(new PreparationCurrentClearUpdate(actor.tenantId(),
                oldPreparation.getId(), oldPreparation.getVersion() + 1, actorText)) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        PreparationDO next = copyPreparation(oldPreparation, actor, now);
        if (preparationMapper.insert(next) != 1 || next.getId() == null) throw new IllegalStateException("PREPARATION_COPY_FAILED");
        Map<Long, Long> itemIds = new LinkedHashMap<>();
        for (PreparationItemDO oldItem : oldItems) {
            PreparationItemDO copied = copyItem(oldItem, next.getId(), oldItem.getId().equals(selected.getId()), actor, now);
            if (itemMapper.insert(copied) != 1 || copied.getId() == null) throw new IllegalStateException("PREPARATION_ITEM_COPY_FAILED");
            itemIds.put(oldItem.getId(), copied.getId());
        }
        for (DynamicFormInstanceDO oldForm : oldForms) {
            Long newItemId = itemIds.get(oldForm.getItemId());
            PreparationItemDO oldItem = oldItems.stream().filter(row -> row.getId().equals(oldForm.getItemId())).findFirst().orElseThrow();
            DynamicFormInstanceDO copied = copyForm(oldForm, next.getId(), newItemId,
                    oldItem.getId().equals(selected.getId()) || !"CONFIRMED".equals(oldItem.getConfirmationStatusCode()), actor, now);
            if (formMapper.insert(copied) != 1) throw new IllegalStateException("PREPARATION_FORM_COPY_FAILED");
        }
        for (PreparationSourceReferenceDO source : sources) {
            PreparationSourceReferenceDO copied = copySource(source, next.getId(), itemIds.get(source.getItemId()), actor, now);
            if (sourceMapper.insert(copied) != 1) throw new IllegalStateException("PREPARATION_SOURCE_COPY_FAILED");
        }
        return result(next, "DRAFT", 0, next.getId());
    }

    private PreparationItemDO requireItem(List<PreparationItemDO> items, PreparationReviewCommand command) {
        return items.stream().filter(item -> Objects.equals(item.getId(), command.itemId()))
                .filter(item -> Objects.equals(item.getVersion(), command.expectedItemVersion()))
                .findFirst().orElseThrow(() -> exception(PREPARATION_VERSION_NOT_MATCH));
    }

    private void validateEvidence(PreparationItemDO item, PreparationItemApplicationService.Actor actor) {
        Map<String, Object> policy = JsonUtils.parseMap(item.getEvidencePolicySnapshot());
        boolean required = policy != null && Boolean.TRUE.equals(policy.get("required"));
        List<EvidenceFact> facts = JsonUtils.parseArray(item.getEvidenceReferenceSnapshot(), EvidenceFact.class);
        if (required && facts.isEmpty()) throw exception(PREPARATION_FILE_FACT_INVALID);
        for (EvidenceFact frozen : facts) {
            FileArtifactVersionFact current = fileArtifactApi.lockAndRevalidate(
                    new FileArtifactVersionRevalidationQuery(frozen.artifactId(), frozen.versionNo(),
                            PreparationFilePolicyProvider.OWNER_CONTEXT, PreparationFilePolicyProvider.OBJECT_TYPE,
                            String.valueOf(item.getId()), PreparationFilePolicyProvider.PURPOSE_CODE,
                            frozen.referenceKey(), FileActionCodes.REFERENCE, frozen.fileFactVersion(), frozen.scopeVersion()));
            if (current == null || !"AVAILABLE".equals(current.availabilityStatus())
                    || !"ACTIVE".equals(current.referenceStatus())) throw exception(PREPARATION_FILE_FACT_INVALID);
        }
    }

    private void authorizeManager(Long projectId, Integer expectedProjectVersion,
            PreparationItemApplicationService.Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        ProjectScopeResult current = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),
                actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        if (current == null || current.treeVersion() == null || current.fullProjectIds() == null
                || !current.fullProjectIds().contains(projectId)) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        ProjectScopeResult locked = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(),
                actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE, current.treeVersion()));
        if (locked == null || locked.fullProjectIds() == null || !locked.fullProjectIds().contains(projectId)) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(projectId,
                actor.actorId(), expectedProjectVersion, "ACTIVE", null,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
    }

    private PreparationDO copyPreparation(PreparationDO old, PreparationItemApplicationService.Actor actor, LocalDateTime now) {
        PreparationDO row = new PreparationDO();
        row.setTenantId(actor.tenantId()); row.setProjectId(old.getProjectId());
        row.setPreparationTypeCode(old.getPreparationTypeCode()); row.setBusinessVersion(old.getBusinessVersion() + 1);
        row.setCurrentMarker(1); row.setTemplateId(old.getTemplateId()); row.setTemplateRevisionId(old.getTemplateRevisionId());
        row.setTemplateSnapshot(old.getTemplateSnapshot()); row.setFixedFormCatalogVersion(old.getFixedFormCatalogVersion());
        row.setStatusCode("DRAFT"); row.setReadinessStatusCode("NOT_READY"); row.setInputVersion(old.getInputVersion() + 1);
        row.setReadinessVersion(old.getReadinessVersion()); row.setSnapshotCurrent(false); row.setVersion(0);
        row.setCreator(String.valueOf(actor.actorId())); row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now); row.setUpdateTime(now); return row;
    }

    private PreparationItemDO copyItem(PreparationItemDO old, Long preparationId, boolean returned,
            PreparationItemApplicationService.Actor actor, LocalDateTime now) {
        PreparationItemDO row = new PreparationItemDO();
        row.setTenantId(actor.tenantId()); row.setPreparationId(preparationId); row.setSourceItemId(old.getId());
        row.setItemCode(old.getItemCode()); row.setItemName(old.getItemName()); row.setSortOrder(old.getSortOrder());
        row.setApplicabilityCode(returned && "NOT_APPLICABLE_CONFIRMED".equals(old.getApplicabilityCode())
                ? "NOT_APPLICABLE_PENDING" : old.getApplicabilityCode());
        row.setConfirmationStatusCode(returned ? "PENDING" : old.getConfirmationStatusCode());
        row.setFormCode(old.getFormCode()); row.setFormVersion(old.getFormVersion()); row.setFormSchemaSnapshot(old.getFormSchemaSnapshot());
        row.setEvidencePolicySnapshot(old.getEvidencePolicySnapshot()); row.setSourcePolicySnapshot(old.getSourcePolicySnapshot());
        row.setWaiverPolicySnapshot(old.getWaiverPolicySnapshot()); row.setOutsourced(old.getOutsourced());
        row.setAssigneeUserId(old.getAssigneeUserId()); row.setAssigneeEffectiveFrom(old.getAssigneeEffectiveFrom());
        row.setSiteResultCode(old.getSiteResultCode()); row.setSiteResultDetail(old.getSiteResultDetail());
        row.setEvidenceReferenceSnapshot(old.getEvidenceReferenceSnapshot()); row.setNotApplicableReason(old.getNotApplicableReason());
        if (!returned && "CONFIRMED".equals(old.getConfirmationStatusCode())) {
            row.setNotApplicableConfirmedBy(old.getNotApplicableConfirmedBy()); row.setNotApplicableConfirmedAt(old.getNotApplicableConfirmedAt());
            row.setConfirmedBy(old.getConfirmedBy()); row.setConfirmedAt(old.getConfirmedAt());
        }
        row.setVersion(0); row.setCreator(String.valueOf(actor.actorId())); row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now); row.setUpdateTime(now); return row;
    }

    private DynamicFormInstanceDO copyForm(DynamicFormInstanceDO old, Long preparationId, Long itemId,
            boolean editable, PreparationItemApplicationService.Actor actor, LocalDateTime now) {
        DynamicFormInstanceDO row = new DynamicFormInstanceDO(); row.setTenantId(actor.tenantId());
        row.setPreparationId(preparationId); row.setItemId(itemId); row.setFormCode(old.getFormCode());
        row.setFormVersion(old.getFormVersion()); row.setSchemaSnapshot(old.getSchemaSnapshot()); row.setValueSnapshot(old.getValueSnapshot());
        row.setStatusCode(editable ? "DRAFT" : "FROZEN");
        if (!editable) { row.setFrozenAt(old.getFrozenAt()); row.setFrozenBy(old.getFrozenBy()); }
        row.setVersion(0); row.setCreator(String.valueOf(actor.actorId())); row.setUpdater(String.valueOf(actor.actorId()));
        row.setCreateTime(now); row.setUpdateTime(now); return row;
    }

    private PreparationSourceReferenceDO copySource(PreparationSourceReferenceDO old, Long preparationId, Long itemId,
            PreparationItemApplicationService.Actor actor, LocalDateTime now) {
        PreparationSourceReferenceDO row = new PreparationSourceReferenceDO(); row.setTenantId(actor.tenantId());
        row.setPreparationId(preparationId); row.setItemId(itemId); row.setSourceTypeCode(old.getSourceTypeCode());
        row.setSourceObjectType(old.getSourceObjectType()); row.setSourceObjectId(old.getSourceObjectId());
        row.setSourceReferenceKey(old.getSourceReferenceKey()); row.setRequiredResultPolicySnapshot(old.getRequiredResultPolicySnapshot());
        row.setSyncStatusCode("UNKNOWN"); row.setLastSuccessResultCode(old.getLastSuccessResultCode());
        row.setLastSuccessFactVersion(old.getLastSuccessFactVersion()); row.setLastSuccessWatermark(old.getLastSuccessWatermark());
        row.setLastSuccessAt(old.getLastSuccessAt()); row.setVersion(0); row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId())); row.setCreateTime(now); row.setUpdateTime(now); return row;
    }

    private PreparationReviewResult result(PreparationDO source, String status, int version, Long currentId) {
        return new PreparationReviewResult(source.getId(), source.getBusinessVersion(), status, version, currentId);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(PreparationReviewCommand command,
            PreparationItemApplicationService.Actor actor, PreparationReviewResult response) {
        Map<String, Object> detail = new LinkedHashMap<>(); detail.put("action", command.action());
        detail.put("preparationId", command.preparationId()); detail.put("itemId", command.itemId() == null ? "NONE" : command.itemId());
        detail.put("statusAfter", response.statusCode()); detail.put("businessVersionAfter", response.businessVersion());
        detail.put("currentPreparationId", response.currentPreparationId()); detail.put("reason", command.reason() == null ? "NONE" : command.reason());
        return new PlatformCommandExecutionApi.SuccessFacts("PREPARATION_" + command.action(), "Preparation",
                String.valueOf(command.preparationId()), actor.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(PreparationReviewCommand command, PreparationItemApplicationService.Actor actor,
            RuntimeException failure) {
        if (command == null || actor == null) return;
        String action = command.action() == null ? "UNKNOWN" : command.action();
        Object preparationId = command.preparationId() == null ? "NONE" : command.preparationId();
        Map<String, Object> detail = new LinkedHashMap<>(); detail.put("action", action);
        detail.put("preparationId", preparationId); detail.put("itemId", command.itemId() == null ? "NONE" : command.itemId());
        detail.put("failureCode", failure instanceof ServiceException se ? String.valueOf(se.getCode()) : "PREPARATION_REVIEW_FAILED");
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PREPARATION_" + action,
                "Preparation", String.valueOf(preparationId), "REJECTED", Map.copyOf(detail));
    }

    private String digest(PreparationReviewCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(Map.of(
                    "action", command.action(), "preparationId", command.preparationId(),
                    "itemId", command.itemId() == null ? "NONE" : command.itemId(),
                    "expectedPreparationVersion", command.expectedPreparationVersion(),
                    "expectedItemVersion", command.expectedItemVersion() == null ? -1 : command.expectedItemVersion(),
                    "expectedProjectVersion", command.expectedProjectVersion(),
                    "reason", command.reason() == null ? "NONE" : command.reason())).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private void validate(PreparationReviewCommand command, PreparationItemApplicationService.Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0 || !ACTIONS.contains(command.action())
                || command.preparationId() == null || command.preparationId() <= 0
                || command.expectedPreparationVersion() == null || command.expectedPreparationVersion() < 0
                || command.expectedProjectVersion() == null || command.expectedProjectVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || (!PreparationReviewCommand.SUBMIT.equals(command.action())
                    && (command.itemId() == null || command.itemId() <= 0 || command.expectedItemVersion() == null
                        || command.expectedItemVersion() < 0))) throw exception(PREPARATION_COMMAND_INVALID);
    }

    private record EvidenceFact(Long artifactId, Integer versionNo, String referenceKey,
                                FileFactVersion fileFactVersion, Long scopeVersion) {}
}
