package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCandidatePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemPatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.DynamicFormDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.DynamicFormItemQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationInputInvalidationUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationRowQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormRules;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationStateRules;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_ASSIGNEE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_FILE_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_VERSION_NOT_MATCH;

@Service
@RequiredArgsConstructor
public class PreparationItemApplicationService {

    public static final String PERMISSION_FILL = "pms:preparation-survey:fill";
    private static final String OPERATION = "PREPARATION_ITEM_PATCH";
    private static final Set<String> MANAGER_FIELDS = Set.of(
            "applicabilityCode", "outsourced", "assignee", "notApplicableReason");
    private static final Set<String> ASSIGNEE_FIELDS = Set.of(
            "siteResultCode", "siteResultDetail", "formValueSnapshot", "evidenceReferences");
    private static final Set<String> ALL_FIELDS;
    static {
        Set<String> fields = new LinkedHashSet<>(MANAGER_FIELDS);
        fields.addAll(ASSIGNEE_FIELDS);
        ALL_FIELDS = Set.copyOf(fields);
    }

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final DynamicFormInstanceMapper formMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectOrganizationFactApi organizationFactApi;
    private final AdminUserApi adminUserApi;
    private final OrganizationScopeApi organizationScopeApi;
    private final FileArtifactApi fileArtifactApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public PageResult<OrganizationUserCandidateRespDTO> getCandidates(
            Long preparationId, PreparationCandidatePageReqVO request, Actor actor) {
        requireActor(actor);
        PreparationDO preparation = preparationMapper.selectById(new PreparationRowQuery(
                actor.tenantId(), positive(preparationId)));
        if (preparation == null) throw exception(PREPARATION_NOT_EXISTS);
        requireManagerRead(preparation.getProjectId(), actor);
        ProjectOrganizationFact organization = organizationFactApi.inspect(
                new ProjectOrganizationFactQuery(preparation.getProjectId()));
        OrganizationUserCandidatePageReqDTO query = new OrganizationUserCandidatePageReqDTO();
        query.setCompanyId(organization.companyId());
        query.setDepartmentId(organization.departmentId());
        query.setDepartmentCode(organization.departmentCode());
        query.setKeyword(request == null ? null : request.getKeyword());
        query.setPageNo(request == null ? 1 : request.getPageNo());
        query.setPageSize(request == null ? 20 : request.getPageSize());
        return organizationScopeApi.pageActiveUsers(query);
    }

    public PreparationItemPatchRespVO patch(PatchPreparationItemCommand command, Actor actor) {
        try {
            return transactionTemplate.execute(status -> patchInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(command, actor, failure);
            throw failure;
        }
    }

    private PreparationItemPatchRespVO patchInTransaction(PatchPreparationItemCommand command, Actor actor) {
        validate(command, actor);
        PreparationDO located = preparationMapper.selectById(new PreparationRowQuery(
                actor.tenantId(), command.preparationId()));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        boolean managerWrite = command.submittedFields().stream().anyMatch(MANAGER_FIELDS::contains);
        boolean assigneeWrite = command.submittedFields().stream().anyMatch(ASSIGNEE_FIELDS::contains);

        ProjectScopeResult currentScope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), located.getProjectId(),
                managerWrite ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW));
        requireScope(currentScope, located.getProjectId());
        ProjectScopeResult lockedScope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.actorId(), located.getProjectId(),
                managerWrite ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW,
                currentScope.treeVersion()));
        requireScope(lockedScope, located.getProjectId());

        if (managerWrite) requireManagerLocked(located.getProjectId(), command.expectedProjectVersion(), actor);
        if (command.submittedFields().contains("assignee") && command.assigneeUserId() != null) {
            ProjectOrganizationFact organization = organizationFactApi.lockAndRevalidate(
                    new ProjectOrganizationFactRevalidationQuery(located.getProjectId(),
                            command.expectedProjectVersion()));
            adminUserApi.validateUser(command.assigneeUserId());
            if (!organizationScopeApi.hasScope(command.assigneeUserId(),
                    organization.companyId(), organization.departmentId())) {
                throw exception(PREPARATION_PROJECT_FACT_INVALID);
            }
        }
        PreparationDO preparation = preparationMapper.selectForUpdate(new PreparationRowQuery(
                actor.tenantId(), command.preparationId()));
        PreparationItemDO item = itemMapper.selectForUpdate(new PreparationItemRowQuery(
                actor.tenantId(), command.preparationId(), command.itemId()));
        DynamicFormInstanceDO form = formMapper.selectByItemForUpdate(new DynamicFormItemQuery(
                actor.tenantId(), command.preparationId(), command.itemId()));
        requireEditable(command, preparation, item, form);
        if (assigneeWrite) requireAssignee(item, actor);

        String normalizedForm = form.getValueSnapshot();
        if (command.submittedFields().contains("formValueSnapshot")) {
            normalizedForm = FixedSurveyFormRules.validateAndNormalizeValue(
                    form.getSchemaSnapshot(), command.formValueSnapshot());
        }
        String evidenceSnapshot = item.getEvidenceReferenceSnapshot();
        if (command.submittedFields().contains("evidenceReferences")) {
            evidenceSnapshot = revalidateEvidence(command, actor, lockedScope.treeVersion());
        }
        if (command.submittedFields().contains("applicabilityCode")
                && !Objects.equals(item.getApplicabilityCode(), command.applicabilityCode())) {
            PreparationStateRules.requireApplicabilityTransition(
                    preparation.getStatusCode(), item.getApplicabilityCode(), command.applicabilityCode());
        }

        Map<String, Object> before = auditSnapshot(preparation, item, form);
        Set<String> itemFields = new LinkedHashSet<>(command.submittedFields());
        itemFields.remove("formValueSnapshot");
        itemFields.remove("evidenceReferences");
        if (command.submittedFields().contains("evidenceReferences")) {
            itemFields.add("evidenceReferenceSnapshot");
        }
        if (!itemFields.isEmpty() && itemMapper.updateDraftIfMatch(new PreparationItemDraftUpdate(
                actor.tenantId(), preparation.getId(), item.getId(), item.getVersion(), Set.copyOf(itemFields),
                command.applicabilityCode(), command.outsourced(), command.assigneeUserId(),
                command.submittedFields().contains("assignee") ? LocalDateTime.now() : item.getAssigneeEffectiveFrom(),
                command.siteResultCode(), command.siteResultDetail(), evidenceSnapshot,
                command.notApplicableReason(),
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        if (command.submittedFields().contains("formValueSnapshot")
                && formMapper.updateDraftIfMatch(new DynamicFormDraftUpdate(actor.tenantId(), preparation.getId(),
                item.getId(), form.getId(), form.getVersion(), normalizedForm,
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
        if (preparationMapper.invalidateReadinessIfMatch(new PreparationInputInvalidationUpdate(
                actor.tenantId(), preparation.getId(), preparation.getVersion(), preparation.getInputVersion(),
                preparation.getReadinessVersion(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }

        int itemVersionAfter = item.getVersion() + (itemFields.isEmpty() ? 0 : 1);
        int formVersionAfter = form.getVersion()
                + (command.submittedFields().contains("formValueSnapshot") ? 1 : 0);
        PreparationItemPatchRespVO response = new PreparationItemPatchRespVO(preparation.getId(), item.getId(),
                preparation.getVersion() + 1, preparation.getInputVersion() + 1,
                itemVersionAfter, formVersionAfter);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("preparationVersion", response.getPreparationVersion());
        after.put("inputVersion", response.getInputVersion());
        after.put("itemVersion", response.getItemVersion());
        after.put("formVersion", response.getFormVersion());
        after.put("submittedFields", command.submittedFields());
        if (command.submittedFields().contains("applicabilityCode")) {
            after.put("applicability", command.applicabilityCode());
        }
        if (command.submittedFields().contains("outsourced")) after.put("outsourced", command.outsourced());
        if (command.submittedFields().contains("assignee")) {
            after.put("assigneeUserId", command.assigneeUserId() == null ? "NONE" : command.assigneeUserId());
        }
        if (command.submittedFields().contains("notApplicableReason")) {
            after.put("notApplicableReason", auditValue(command.notApplicableReason()));
        }
        if (command.submittedFields().contains("siteResultCode")) {
            after.put("siteResultCode", auditValue(command.siteResultCode()));
        }
        if (command.submittedFields().contains("siteResultDetail")) {
            after.put("siteResultDetail", auditValue(command.siteResultDetail()));
        }
        if (command.submittedFields().contains("formValueSnapshot")) {
            after.put("formValueSnapshot", normalizedForm);
        }
        if (command.submittedFields().contains("evidenceReferences")) {
            after.put("evidenceReferenceSnapshot", evidenceSnapshot);
        }
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), OPERATION,
                "PreparationItem", String.valueOf(item.getId()), "SUCCESS",
                Map.of("before", before, "after", Map.copyOf(after)));
        return response;
    }

    private String revalidateEvidence(PatchPreparationItemCommand command, Actor actor, Long scopeVersion) {
        List<PatchPreparationItemCommand.EvidenceReference> references = command.evidenceReferences() == null
                ? List.of() : command.evidenceReferences();
        Set<String> keys = new LinkedHashSet<>();
        List<Map<String, Object>> frozen = new ArrayList<>();
        for (PatchPreparationItemCommand.EvidenceReference reference : references) {
            if (reference == null || reference.referenceKey() == null || reference.referenceKey().isBlank()
                    || !keys.add(reference.referenceKey().trim())) throw exception(PREPARATION_FILE_FACT_INVALID);
            FileArtifactVersionFact fact;
            try {
                fact = fileArtifactApi.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(
                        reference.artifactId(), reference.versionNo(), PreparationFilePolicyProvider.OWNER_CONTEXT,
                        PreparationFilePolicyProvider.OBJECT_TYPE, String.valueOf(command.itemId()),
                        PreparationFilePolicyProvider.PURPOSE_CODE, reference.referenceKey(),
                        FileActionCodes.REFERENCE, reference.fileFactVersion(), reference.scopeVersion()));
            } catch (RuntimeException failure) {
                throw exception(PREPARATION_FILE_FACT_INVALID);
            }
            if (fact == null || !Objects.equals(fact.scopeVersion(), scopeVersion)
                    || !"AVAILABLE".equals(fact.availabilityStatus())
                    || !"ACTIVE".equals(fact.referenceStatus())) {
                throw exception(PREPARATION_FILE_FACT_INVALID);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("artifactId", fact.artifactId());
            row.put("versionNo", fact.versionNo());
            row.put("referenceKey", fact.referenceKey());
            row.put("fileFactVersion", fact.fileFactVersion());
            row.put("scopeVersion", fact.scopeVersion());
            frozen.add(Map.copyOf(row));
        }
        return JsonUtils.toJsonString(frozen);
    }

    private void requireManagerRead(Long projectId, Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        requireScope(scope, projectId);
        ProjectParticipantFact manager = participantFactApi.inspect(new ProjectParticipantFactQuery(
                projectId, actor.actorId(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                LocalDateTime.now()));
        requireManager(manager, projectId, actor.actorId());
    }

    private void requireManagerLocked(Long projectId, Integer expectedProjectVersion, Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        ProjectParticipantFact manager = participantFactApi.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(projectId, actor.actorId(), expectedProjectVersion,
                        "ACTIVE", null, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        requireManager(manager, projectId, actor.actorId());
    }

    private void requireManager(ProjectParticipantFact fact, Long projectId, Long actorId) {
        if (fact == null || !Objects.equals(fact.projectId(), projectId)
                || !Objects.equals(fact.userId(), actorId)
                || !fact.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private void requireAssignee(PreparationItemDO item, Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_FILL)
                || !Objects.equals(item.getAssigneeUserId(), actor.actorId())
                || !"REQUIRED".equals(item.getApplicabilityCode())) {
            throw exception(PREPARATION_ASSIGNEE_FORBIDDEN);
        }
    }

    private void requireEditable(PatchPreparationItemCommand command, PreparationDO preparation,
                                 PreparationItemDO item, DynamicFormInstanceDO form) {
        if (preparation == null || item == null || form == null) throw exception(PREPARATION_NOT_EXISTS);
        if (!Integer.valueOf(1).equals(preparation.getCurrentMarker())
                || !"DRAFT".equals(preparation.getStatusCode())
                || !Objects.equals(preparation.getVersion(), command.expectedPreparationVersion())
                || !Objects.equals(preparation.getInputVersion(), command.expectedInputVersion())
                || !Objects.equals(preparation.getReadinessVersion(), command.expectedReadinessVersion())
                || !Objects.equals(item.getVersion(), command.expectedItemVersion())
                || !Objects.equals(form.getVersion(), command.expectedFormVersion())
                || !"DRAFT".equals(form.getStatusCode()) || form.getFrozenAt() != null) {
            throw exception(PREPARATION_VERSION_NOT_MATCH);
        }
    }

    private void validate(PatchPreparationItemCommand command, Actor actor) {
        requireActor(actor);
        if (command == null || command.preparationId() == null || command.preparationId() <= 0
                || command.itemId() == null || command.itemId() <= 0
                || invalidVersion(command.expectedItemVersion())
                || invalidVersion(command.expectedPreparationVersion())
                || invalidVersion(command.expectedInputVersion())
                || invalidVersion(command.expectedReadinessVersion())
                || invalidVersion(command.expectedFormVersion())
                || invalidVersion(command.expectedProjectVersion())
                || command.submittedFields() == null || command.submittedFields().isEmpty()
                || !ALL_FIELDS.containsAll(command.submittedFields())
                || command.submittedFields().contains("outsourced") && command.outsourced() == null
                || command.submittedFields().contains("assignee")
                    && command.assigneeUserId() != null && command.assigneeUserId() <= 0
                || command.submittedFields().contains("applicabilityCode")
                    && (command.applicabilityCode() == null || command.applicabilityCode().isBlank())
                || command.submittedFields().contains("formValueSnapshot")
                    && command.formValueSnapshot() == null) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private Map<String, Object> auditSnapshot(PreparationDO preparation, PreparationItemDO item,
                                               DynamicFormInstanceDO form) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", preparation.getProjectId());
        snapshot.put("preparationId", preparation.getId());
        snapshot.put("itemId", item.getId());
        snapshot.put("formInstanceId", form.getId());
        snapshot.put("preparationVersion", preparation.getVersion());
        snapshot.put("inputVersion", preparation.getInputVersion());
        snapshot.put("itemVersion", item.getVersion());
        snapshot.put("formVersion", form.getVersion());
        snapshot.put("applicability", item.getApplicabilityCode());
        snapshot.put("outsourced", item.getOutsourced());
        snapshot.put("assigneeUserId", item.getAssigneeUserId() == null ? "NONE" : item.getAssigneeUserId());
        snapshot.put("siteResultCode", item.getSiteResultCode() == null ? "NONE" : item.getSiteResultCode());
        snapshot.put("siteResultDetail", item.getSiteResultDetail() == null ? "NONE" : item.getSiteResultDetail());
        snapshot.put("evidenceReferenceSnapshot", auditValue(item.getEvidenceReferenceSnapshot()));
        snapshot.put("formValueSnapshot", auditValue(form.getValueSnapshot()));
        return Map.copyOf(snapshot);
    }

    private Object auditValue(Object value) { return value == null ? "NONE" : value; }

    private void auditRejected(PatchPreparationItemCommand command, Actor actor, RuntimeException failure) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (command != null && command.preparationId() != null) detail.put("preparationId", command.preparationId());
        if (command != null && command.itemId() != null) detail.put("itemId", command.itemId());
        detail.put("failureCode", failure instanceof ServiceException service
                ? String.valueOf(service.getCode()) : "PREPARATION_ITEM_PATCH_FAILED");
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), OPERATION,
                "PreparationItem", command == null || command.itemId() == null
                        ? "UNKNOWN" : String.valueOf(command.itemId()), "REJECTED", Map.copyOf(detail));
    }

    private void requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.treeVersion() < 0
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    private boolean invalidVersion(Integer value) { return value == null || value < 0; }
    private long positive(Long value) {
        if (value == null || value <= 0) throw exception(PREPARATION_COMMAND_INVALID);
        return value;
    }
    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {}
}
