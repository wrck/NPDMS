package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisCompleteUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEntityDataUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEffectiveClearUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceCloneCommand;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormEntityDataQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstanceRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_CONTENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_DRAFT_CONFLICT;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

/** PRE-04动态表单组合命令；SOL是唯一外层事务、幂等和审计Owner。 */
// Retired PRE-04 commands; only independent entity commands are registered.
@RequiredArgsConstructor
public class RequirementAnalysisDynamicFormCommandService {

    private static final DynamicFormProviderKey PROVIDER = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
    private static final String ACTIVE = "ACTIVE";

    private final RequirementAnalysisRootMapper rootMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final DynamicFormBusinessInstanceApi dynamicFormApi;
    private final RequirementAnalysisDynamicFormPolicyProvider policyProvider;
    private final PermissionApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;
    private final RequirementAnalysisExecutionBinding executionBinding;
    private final EngineeringRuleReevaluationEvents ruleEvents;

    public CommandResult createInitial(CreateCommand command, Actor actor) {
        return execute(actor, "REQUIREMENT_ANALYSIS_INITIALIZE", command.idempotencyKey(), command,
                () -> createInitialInTransaction(command, actor));
    }

    public CommandResult patch(PatchCommand command, Actor actor) {
        return transactionTemplate.execute(status -> patchInTransaction(command, actor));
    }

    public CommandResult complete(CompleteCommand command, Actor actor) {
        return execute(actor, "REQUIREMENT_ANALYSIS_COMPLETE", command.idempotencyKey(), command,
                () -> completeInTransaction(command, actor));
    }

    public CommandResult createRevision(CreateRevisionCommand command, Actor actor) {
        return execute(actor, "REQUIREMENT_ANALYSIS_CREATE_DRAFT", command.idempotencyKey(), command,
                () -> createRevisionInTransaction(command, actor));
    }

    private Outcome createInitialInTransaction(CreateCommand command, Actor actor) {
        Authorization authorization = lockManager(command.projectId(), actor, null, command.execution());
        RequirementAnalysisProjectQuery project = new RequirementAnalysisProjectQuery(actor.tenantId(), command.projectId());
        if (rootMapper.selectDraftForUpdate(project) != null || rootMapper.selectEffectiveForUpdate(project) != null) {
            throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        }
        ProjectWorkBindingFact binding = requireBinding(authorization.binding());
        DynamicFormRevisionFact revision = inspectRevision(actor, binding);
        long preparationId = IdWorker.getId();
        long instanceId = IdWorker.getId();
        PreparationDO root = newDraft(actor, command.projectId(), preparationId, instanceId,
                1, null, binding, authorization.execution());
        insert(root);
        DynamicFormInstanceFact instance = dynamicFormApi.createBusinessInstance(new DynamicFormInstanceCreateCommand(
                actor.tenantId(), actor.actorId(), PROVIDER, DynamicFormBusinessAction.CREATE,
                instanceId, owner(preparationId), revision.templateRevisionId(),
                revision.revisionFactVersion(), Map.of()));
        return new Outcome(result(root, instance), transition("INITIALIZE", command.idempotencyKey(), actor,
                root, instance, null, root.getId(), null, null, null, "DRAFT",
                null, root.getVersion(), null, root.getContentVersion(), null, instance.instanceVersion(),
                List.of()));
    }

    private CommandResult patchInTransaction(PatchCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(new RequirementAnalysisRowQuery(
                actor.tenantId(), command.preparationId()));
        if (inspected == null) throw exception(REQUIREMENT_STATUS_INVALID);
        Authorization authorization = lockManager(inspected.getProjectId(), actor, inspected, command.execution());
        PreparationDO root = lockDraft(actor.tenantId(), command.preparationId(), command.expectedSolVersion());
        Map<String, Object> values = RequirementAnalysisEntityData.values(root);
        values.putAll(command.partialValues());
        DynamicFormInstanceFact inspectedForm = dynamicFormApi.inspectEntityData(new DynamicFormEntityDataQuery(
                new DynamicFormInstanceQuery(actor.tenantId(), actor.actorId(), PROVIDER, owner(root.getId()),
                        root.getDynamicFormInstanceId(), DynamicFormBusinessAction.PATCH, executionContext(authorization)), values));
        if (!Objects.equals(inspectedForm.instanceVersion(), command.expectedInstanceVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        DynamicFormInstanceFact updated = dynamicFormApi.lockAndRevalidateInstance(
                new DynamicFormInstanceRevalidationQuery(actor.actorId(), inspectedForm));
        String json = JsonUtils.toJsonString(updated.ordinaryValues());
        if (rootMapper.updateEntityDataIfMatch(new RequirementAnalysisEntityDataUpdate(
                actor.tenantId(), root.getId(), root.getVersion(), json, String.valueOf(actor.actorId()))) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        root.setEntityValueJson(json);
        root.setVersion(root.getVersion() + 1);
        root.setContentVersion(root.getContentVersion() + 1);
        recordPatchAudit(actor, root, command, updated);
        ruleEvents.changed(root.getProjectId(), "RequirementAnalysis", root.getId(), actor.actorId(), actor.correlationId());
        return result(root, updated);
    }

    private Outcome completeInTransaction(CompleteCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(new RequirementAnalysisRowQuery(
                actor.tenantId(), command.preparationId()));
        if (inspected == null) throw exception(REQUIREMENT_STATUS_INVALID);
        Authorization authorization = lockManager(inspected.getProjectId(), actor, inspected, command.execution());
        PreparationDO root = lockDraft(actor.tenantId(), command.preparationId(), command.expectedSolVersion());
        DynamicFormRevisionFact revision = requireRootBinding(root, requireBinding(authorization.binding()), actor);
        DynamicFormInstanceFact inspectedInstance = dynamicFormApi.inspectEntityData(new DynamicFormEntityDataQuery(new DynamicFormInstanceQuery(
                actor.tenantId(), actor.actorId(), PROVIDER, owner(root.getId()), root.getDynamicFormInstanceId(),
                DynamicFormBusinessAction.COMPLETE, executionContext(authorization)), RequirementAnalysisEntityData.values(root)));
        if (!Objects.equals(inspectedInstance.instanceVersion(), command.expectedInstanceVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        DynamicFormInstanceFact lockedInstance = dynamicFormApi.lockAndRevalidateInstance(
                new DynamicFormInstanceRevalidationQuery(actor.actorId(), inspectedInstance));
        if (lockedInstance.validationFact() == null
                || !"VALID".equals(lockedInstance.validationFact().result())
                || !lockedInstance.validationFact().blockerCodes().isEmpty()) {
            throw exception(REQUIREMENT_ANALYSIS_CONTENT_INVALID);
        }
        RequirementAnalysisProjectQuery project = new RequirementAnalysisProjectQuery(actor.tenantId(), root.getProjectId());
        PreparationDO effective = rootMapper.selectEffectiveForUpdate(project);
        Long effectiveBefore = effective == null ? null : effective.getId();
        if (effective != null && rootMapper.clearEffectiveIfMatch(new RequirementAnalysisEffectiveClearUpdate(
                actor.tenantId(), effective.getId(), effective.getVersion(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        if (rootMapper.completeDraftIfMatch(new RequirementAnalysisCompleteUpdate(
                actor.tenantId(), root.getId(), root.getVersion(), root.getContentVersion(),
                actor.actorId(), LocalDateTime.now(), String.valueOf(actor.actorId()))) != 1) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        root.setStatusCode("COMPLETED");
        root.setDraftMarker(null);
        root.setEffectiveMarker(1);
        root.setVersion(root.getVersion() + 1);
        return new Outcome(result(root, lockedInstance), transition("COMPLETE", command.idempotencyKey(), actor,
                root, lockedInstance, root.getId(), null, effectiveBefore, root.getId(), "DRAFT", "COMPLETED",
                command.expectedSolVersion(), root.getVersion(), root.getContentVersion(), root.getContentVersion(),
                command.expectedInstanceVersion(), lockedInstance.instanceVersion(), List.of()));
    }

    private Outcome createRevisionInTransaction(CreateRevisionCommand command, Actor actor) {
        PreparationDO inspected = rootMapper.selectById(new RequirementAnalysisRowQuery(
                actor.tenantId(), command.sourcePreparationId()));
        if (inspected == null || !"COMPLETED".equals(inspected.getStatusCode())) {
            throw exception(REQUIREMENT_STATUS_INVALID);
        }
        Authorization authorization = lockManager(inspected.getProjectId(), actor, inspected, command.execution());
        PreparationDO source = rootMapper.selectForUpdate(new RequirementAnalysisRowQuery(
                actor.tenantId(), command.sourcePreparationId()));
        if (!Objects.equals(source.getVersion(), command.expectedSolVersion())
                || !Objects.equals(source.getDynamicFormInstanceId(), command.sourceInstanceId())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        RequirementAnalysisProjectQuery project = new RequirementAnalysisProjectQuery(actor.tenantId(), source.getProjectId());
        if (rootMapper.selectDraftForUpdate(project) != null) throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
        DynamicFormRevisionFact revision = requireRootBinding(source, requireBinding(authorization.binding()), actor);
        DynamicFormInstanceFact sourceFact = dynamicFormApi.inspectEntityData(new DynamicFormEntityDataQuery(new DynamicFormInstanceQuery(
                actor.tenantId(), actor.actorId(), PROVIDER, owner(source.getId()), source.getDynamicFormInstanceId(),
                DynamicFormBusinessAction.CLONE_SOURCE), RequirementAnalysisEntityData.values(source)));
        if (!Objects.equals(sourceFact.instanceVersion(), command.expectedInstanceVersion())) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        long preparationId = IdWorker.getId();
        long instanceId = IdWorker.getId();
        PreparationDO draft = newDraft(actor, source.getProjectId(), preparationId, instanceId,
                source.getBusinessVersion() + 1, source.getId(), authorization.binding(), authorization.execution());
        draft.setEntityValueJson(source.getEntityValueJson());
        insert(draft);
        DynamicFormPolicyFact targetPolicy = policyProvider.inspectInstanceOwnerPolicy(
                new DynamicFormInstancePolicyQuery(actor.tenantId(), actor.actorId(), PROVIDER,
                        owner(draft.getId()), instanceId, DynamicFormBusinessAction.CLONE_TARGET));
        DynamicFormInstanceFact cloned = dynamicFormApi.cloneBusinessInstance(new DynamicFormInstanceCloneCommand(
                actor.tenantId(), actor.actorId(), PROVIDER, sourceFact, owner(draft.getId()), instanceId,
                targetPolicy, command.idempotencyKey()));
        return new Outcome(result(draft, cloned), transition("CREATE_DRAFT", command.idempotencyKey(), actor,
                draft, cloned, null, draft.getId(), source.getId(), source.getId(), null, "DRAFT",
                null, draft.getVersion(), null, draft.getContentVersion(), null, cloned.instanceVersion(),
                cloned.fields().stream().map(field -> field.fieldKey()).sorted().toList()));
    }

    private Authorization lockManager(Long projectId, Actor actor, PreparationDO existing, ProjectBusinessExecutionSelection requested) {
        requireActor(actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), RequirementAnalysisQueryService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        var currentScope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE));
        if (currentScope == null || currentScope.treeVersion() == null) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        var lockedScope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_MANAGE,
                currentScope.treeVersion()));
        if (lockedScope.fullProjectIds() == null || !lockedScope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
        ProjectParticipantFact inspected = RequirementAnalysisManagerFacts.inspect(
                participantFactApi, permissionApi, projectId, actor.actorId());
        if (inspected == null) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
        ProjectParticipantFact participant = participantFactApi.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(projectId, inspected.userId(), inspected.projectVersion(),
                        ACTIVE, null,
                        Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        ProjectWorkBindingFact current;
        if (requested != null) {
            current = executionBinding.lockRequested(projectId, requested);
            if (existing != null) current = executionBinding.currentBinding(existing, requested);
        } else {
            current = existing == null ? workBindingFactApi.inspect(new ProjectWorkBindingFactQuery(
                    projectId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS)) : executionBinding.currentBinding(existing);
        }
        ProjectWorkBindingFact binding = executionBinding.lockBinding(current);
        return new Authorization(participant.projectVersion(), lockedScope.treeVersion(), binding,
                executionBinding.lockCurrent(binding));
    }

    private DynamicFormRevisionFact inspectRevision(Actor actor, ProjectWorkBindingFact binding) {
        DynamicFormRevisionFact inspected = dynamicFormApi.inspectRevisionForUsage(new DynamicFormRevisionUsageQuery(
                actor.tenantId(), actor.actorId(), PROVIDER, binding.dynamicFormTemplateRevisionId(),
                RequirementAnalysisDynamicFormPolicyProvider.REQUIRED_USAGE,
                DynamicFormBusinessAction.REVISION_FROZEN_USE, binding.dynamicFormRevisionFactVersion()));
        if (!Objects.equals(inspected.templateId(), binding.dynamicFormTemplateId())
                || !Objects.equals(inspected.revisionNo(), binding.dynamicFormRevisionNo())) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
        return inspected;
    }

    private tools.jackson.databind.JsonNode executionContext(Authorization authorization) {
        var execution = authorization.execution();
        return JsonUtils.parseTree(JsonUtils.toJsonString(new ProjectBusinessExecutionSelection(
                execution.execution(), execution.stageExecution())));
    }

    private ProjectWorkBindingFact requireBinding(ProjectWorkBindingFact binding) {
        if (binding == null || binding.projectTemplateId() == null || binding.templateRevisionId() == null
                || binding.dynamicFormTemplateId() == null
                || binding.dynamicFormTemplateRevisionId() == null || binding.dynamicFormRevisionNo() == null
                || binding.dynamicFormRevisionFactVersion() == null
                || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode().equals(binding.workBindingTypeCode())
                || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode().equals(binding.targetContextCode())
                || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType().equals(binding.targetObjectType())
                || !ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey().equals(binding.targetObjectKey())) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
        return binding;
    }

    private DynamicFormRevisionFact requireRootBinding(PreparationDO root, ProjectWorkBindingFact binding,
                                                       Actor actor) {
        if (!Objects.equals(root.getTemplateId(), binding.projectTemplateId())
                || !Objects.equals(root.getTemplateRevisionId(), binding.templateRevisionId())) {
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        }
        return inspectRevision(actor, binding);
    }

    private PreparationDO newDraft(Actor actor, Long projectId, Long preparationId, Long instanceId,
                                   int businessVersion, Long sourceId, ProjectWorkBindingFact binding,
                                   RequirementAnalysisExecutionBinding.Frozen execution) {
        PreparationDO row = new PreparationDO();
        row.setId(preparationId);
        row.setTenantId(actor.tenantId());
        row.setProjectId(projectId);
        row.setSourcePreparationId(sourceId);
        row.setDynamicFormInstanceId(instanceId);
        row.setEntityValueJson("{}");
        row.setBusinessVersion(businessVersion);
        row.setTemplateId(binding.projectTemplateId());
        row.setTemplateRevisionId(binding.templateRevisionId());
        row.setTemplateSnapshot(executionBinding.freeze(execution));
        row.setVersion(1);
        row.setContentVersion(1);
        row.setStatusCode("DRAFT");
        row.setDraftMarker(1);
        row.setCreator(String.valueOf(actor.actorId()));
        row.setUpdater(String.valueOf(actor.actorId()));
        return row;
    }

    private void insert(PreparationDO row) {
        if (rootMapper.insertDynamicRoot(row) != 1) throw exception(REQUIREMENT_ANALYSIS_DRAFT_CONFLICT);
    }

    private PreparationDO lockDraft(Long tenantId, Long id, Integer expectedVersion) {
        PreparationDO root = rootMapper.selectForUpdate(new RequirementAnalysisRowQuery(tenantId, id));
        if (root == null || !"DRAFT".equals(root.getStatusCode())
                || !Integer.valueOf(1).equals(root.getDraftMarker())
                || !Objects.equals(root.getVersion(), expectedVersion)
                || root.getDynamicFormInstanceId() == null) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        return root;
    }

    private DynamicFormOwnerKey owner(Long preparationId) {
        return new DynamicFormOwnerKey(PROVIDER.ownerContext(), PROVIDER.objectType(), String.valueOf(preparationId));
    }

    private CommandResult result(PreparationDO root, DynamicFormInstanceFact instance) {
        return new CommandResult(root.getProjectId(), root.getId(), root.getDynamicFormInstanceId(),
                root.getBusinessVersion(), root.getStatusCode(), root.getContentVersion(), root.getVersion(),
                instance.instanceVersion());
    }

    private <T> CommandResult execute(Actor actor, String scope, String key, T request, Supplier<Outcome> operation) {
        requireActor(actor);
        return transactionTemplate.execute(status -> {
            AtomicReference<AuditTransition> transition = new AtomicReference<>();
            var execution = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), scope, actor.actorId(), key),
                    digest(request), CommandResult.class, () -> {
                        Outcome outcome = operation.get();
                        transition.set(outcome.transition());
                        ruleEvents.changed(outcome.result().projectId(), "RequirementAnalysis", outcome.result().preparationId(),
                                actor.actorId(), actor.correlationId());
                        return outcome.result();
                    }, response -> successFacts(scope, actor, response, transition.get()));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
                throw exception(PLATFORM_COMMAND_IN_PROGRESS);
            }
            return execution.response();
        });
    }

    private String digest(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(String scope, Actor actor,
                                                                  CommandResult result,
                                                                  AuditTransition transition) {
        if (transition == null) throw new IllegalStateException("PRE04_AUDIT_TRANSITION_MISSING");
        return new PlatformCommandExecutionApi.SuccessFacts(scope, "RequirementAnalysis",
                String.valueOf(result.preparationId()), actor.correlationId(),
                JsonUtils.toJsonString(transition), null, null);
    }

    private void recordPatchAudit(Actor actor, PreparationDO root, PatchCommand command,
                                  DynamicFormInstanceFact fact) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", command.operationId());
        detail.put("action", "PATCH");
        detail.put("projectId", root.getProjectId());
        detail.put("preparationId", root.getId());
        detail.put("businessVersion", root.getBusinessVersion());
        detail.put("dynamicFormInstanceId", fact.instanceId());
        detail.put("dynamicFormTemplateId", fact.templateId());
        detail.put("dynamicFormTemplateRevisionId", fact.templateRevisionId());
        detail.put("statusBefore", "DRAFT");
        detail.put("statusAfter", "DRAFT");
        detail.put("changedFieldKeys", command.partialValues().keySet().stream().sorted().toList());
        detail.put("solVersionBefore", command.expectedSolVersion());
        detail.put("solVersionAfter", root.getVersion());
        detail.put("instanceVersionBefore", command.expectedInstanceVersion());
        detail.put("instanceVersionAfter", fact.instanceVersion());
        detail.put("contentVersionBefore", root.getContentVersion() - 1);
        detail.put("contentVersionAfter", root.getContentVersion());
        detail.put("controlledFileSummary", controlledFileSummary(fact));
        detail.put("actorId", actor.actorId());
        detail.put("occurredAt", LocalDateTime.now());
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "REQUIREMENT_ANALYSIS_PATCH", "RequirementAnalysis", String.valueOf(root.getId()),
                "SUCCESS", detail);
    }

    private AuditTransition transition(String action, String operationId, Actor actor,
                                       PreparationDO root, DynamicFormInstanceFact instance,
                                       Long draftBefore, Long draftAfter,
                                       Long effectiveBefore, Long effectiveAfter,
                                       String statusBefore, String statusAfter,
                                       Integer solVersionBefore, Integer solVersionAfter,
                                       Integer contentVersionBefore, Integer contentVersionAfter,
                                       Integer instanceVersionBefore, Integer instanceVersionAfter,
                                       List<String> changedFieldKeys) {
        return new AuditTransition(action, operationId, root.getProjectId(), root.getId(), root.getBusinessVersion(),
                instance.instanceId(), instance.templateId(), instance.templateRevisionId(), draftBefore, draftAfter,
                effectiveBefore, effectiveAfter, statusBefore, statusAfter, solVersionBefore, solVersionAfter,
                contentVersionBefore, contentVersionAfter, instanceVersionBefore, instanceVersionAfter,
                List.copyOf(changedFieldKeys), controlledFileSummary(instance), actor.actorId(), LocalDateTime.now());
    }

    private List<ControlledFileSummary> controlledFileSummary(DynamicFormInstanceFact fact) {
        List<ControlledFileSummary> summaries = new ArrayList<>();
        fact.controlledFileFacts().forEach(set -> set.activeFacts().forEach(file -> summaries.add(
                new ControlledFileSummary(set.key().purposeCode(), file.referenceKey(), file.artifactId(),
                        file.versionNo(), file.fileFactVersion(), file.scopeVersion()))));
        return List.copyOf(summaries);
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(FORBIDDEN);
        }
    }

    private record Authorization(Integer projectVersion, Long scopeVersion, ProjectWorkBindingFact binding,
                                 RequirementAnalysisExecutionBinding.Frozen execution) {}
    private record Outcome(CommandResult result, AuditTransition transition) {}
    private record AuditTransition(String action, String operationId, Long projectId, Long preparationId,
                                   Integer businessVersion, Long dynamicFormInstanceId, Long dynamicFormTemplateId,
                                   Long dynamicFormTemplateRevisionId, Long draftPreparationIdBefore,
                                   Long draftPreparationIdAfter, Long effectivePreparationIdBefore,
                                   Long effectivePreparationIdAfter, String statusBefore, String statusAfter,
                                   Integer solVersionBefore, Integer solVersionAfter,
                                   Integer contentVersionBefore, Integer contentVersionAfter,
                                   Integer instanceVersionBefore, Integer instanceVersionAfter,
                                   List<String> changedFieldKeys, List<ControlledFileSummary> controlledFileSummary,
                                   Long actorId, LocalDateTime occurredAt) {}
    private record ControlledFileSummary(String purposeCode, String referenceKey, Long artifactId,
                                         Integer fileVersionNo,
                                         cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion factVersion,
                                         Long scopeVersion) {}

    public record Actor(Long tenantId, Long actorId, String correlationId) {}
    public record CreateCommand(Long projectId, String idempotencyKey, ProjectBusinessExecutionSelection execution) {}
    public record PatchCommand(Long preparationId, Integer expectedSolVersion, Integer expectedInstanceVersion,
                               Map<String, Object> partialValues, String operationId, ProjectBusinessExecutionSelection execution) {
        public PatchCommand(Long preparationId, Integer expectedSolVersion, Integer expectedInstanceVersion,
                            Map<String, Object> partialValues, String operationId) {
            this(preparationId, expectedSolVersion, expectedInstanceVersion, partialValues, operationId, null);
        }
        public PatchCommand {
            partialValues = partialValues == null ? Map.of() : Map.copyOf(partialValues);
        }
    }
    public record CompleteCommand(Long preparationId, Integer expectedSolVersion,
                                  Integer expectedInstanceVersion, String idempotencyKey, ProjectBusinessExecutionSelection execution) {
        public CompleteCommand(Long preparationId, Integer expectedSolVersion, Integer expectedInstanceVersion, String idempotencyKey) {
            this(preparationId, expectedSolVersion, expectedInstanceVersion, idempotencyKey, null);
        }
    }
    public record CreateRevisionCommand(Long sourcePreparationId, Long sourceInstanceId,
                                        Integer expectedSolVersion, Integer expectedInstanceVersion,
                                        String idempotencyKey, ProjectBusinessExecutionSelection execution) {}
    public record CommandResult(Long projectId, Long preparationId, Long dynamicFormInstanceId,
                                Integer businessVersion, String status, Integer contentVersion,
                                Integer solVersion, Integer dynamicFormInstanceVersion) {}
}
