package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** SOL/PRE-04向共享动态表单公开的唯一Owner策略。 */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisDynamicFormPolicyProvider implements DynamicFormBusinessObjectPolicyProvider {

    public static final String OWNER_CONTEXT = "SOL";
    public static final String OBJECT_TYPE = "REQUIREMENT_ANALYSIS";
    public static final String REQUIRED_USAGE = "PRE_04_REQUIREMENT_ANALYSIS";
    private static final String PERMISSION_QUERY = "pms:requirement-analysis:query";
    private static final String PERMISSION_MANAGE = "pms:requirement-analysis:manage";
    private static final Set<String> CORE_CODES = Set.of(
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY",
            "TRANSMISSION_REQUIREMENT", "TRAFFIC_REQUIREMENT", "BUSINESS_REQUIREMENT",
            "IP_PLANNING", "REDUNDANCY_REQUIREMENT", "SECURITY_PROTECTION",
            "OPERATIONS_REQUIREMENT", "LOGGING_REQUIREMENT");
    private static final Set<String> REQUIRED_CORE_CODES = Set.of(
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY");

    private final RequirementAnalysisRootMapper rootMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final PermissionApi permissionApi;

    @Override
    public DynamicFormProviderKey providerKey() {
        return new DynamicFormProviderKey(OWNER_CONTEXT, OBJECT_TYPE);
    }

    @Override
    public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query) {
        boolean actionAllowed = query != null && Set.of(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH,
                DynamicFormBusinessAction.REVISION_FROZEN_USE).contains(query.action());
        boolean compatible = actionAllowed && REQUIRED_USAGE.equals(query.requiredUsage())
                && compatibleFields(query.fields());
        return new DynamicFormPolicyFact(query == null ? null : query.action(), compatible,
                compatible ? null : "PRE04_SCHEMA_INCOMPATIBLE",
                query == null || query.revisionFactVersion() == null ? null
                        : query.revisionFactVersion().longValue(),
                compatible ? "PRE04_SCHEMA_COMPATIBLE" : "PRE04_SCHEMA_INCOMPATIBLE");
    }

    @Override
    public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query) {
        PreparationDO root = findRoot(query);
        return policy(query == null ? null : query.actorUserId(), query == null ? null : query.action(), root);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(
            DynamicFormPolicyRevalidationQuery query) {
        DynamicFormInstancePolicyQuery lookup = new DynamicFormInstancePolicyQuery(
                query.tenantId(), query.actorUserId(), query.providerKey(), query.ownerKey(),
                query.instanceId(), query.expectedFact().action());
        PreparationDO inspected = findRoot(lookup);
        if (inspected == null) return denied(query.expectedFact().action(), "PRE04_ROOT_NOT_FOUND");
        boolean managerAction = requiresManager(query.expectedFact().action());
        String scopeAction = managerAction ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        ProjectScopeResult lockedScope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                query.tenantId(), query.actorUserId(), inspected.getProjectId(), scopeAction,
                query.expectedFact().scopeVersion()));
        boolean scopeAllowed = lockedScope != null && lockedScope.fullProjectIds() != null
                && lockedScope.fullProjectIds().contains(inspected.getProjectId());
        ProjectParticipantFact manager = managerAction ? inspectManager(query.actorUserId(), inspected.getProjectId()) : null;
        if (managerAction && manager != null) {
            participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                    inspected.getProjectId(), query.actorUserId(), manager.projectVersion(),
                    "ACTIVE", manager.currentStage(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        }
        PreparationDO locked = lockOwnerFacts(query, inspected);
        boolean authorized = scopeAllowed && (!managerAction || manager != null)
                && hasFunctionPermission(query.actorUserId(), query.expectedFact().action());
        DynamicFormPolicyFact current = policyFromLocked(
                query.expectedFact().action(), locked, authorized, query.expectedFact().scopeVersion());
        if (!Objects.equals(current, query.expectedFact())) {
            return denied(query.expectedFact().action(), "PRE04_OWNER_FACT_CHANGED");
        }
        return current;
    }

    private PreparationDO lockOwnerFacts(DynamicFormPolicyRevalidationQuery query, PreparationDO inspected) {
        RequirementAnalysisProjectQuery project = new RequirementAnalysisProjectQuery(
                query.tenantId(), inspected.getProjectId());
        return switch (query.expectedFact().action()) {
            case COMPLETE -> {
                PreparationDO draft = rootMapper.selectDraftForUpdate(project);
                rootMapper.selectEffectiveForUpdate(project);
                yield draft;
            }
            case CLONE_SOURCE -> rootMapper.selectEffectiveForUpdate(project);
            case CLONE_TARGET, CREATE, PATCH, FILE_WRITE -> rootMapper.selectDraftForUpdate(project);
            default -> rootMapper.selectForUpdate(
                    new RequirementAnalysisRowQuery(query.tenantId(), inspected.getId()));
        };
    }

    private PreparationDO findRoot(DynamicFormInstancePolicyQuery query) {
        if (query == null || query.ownerKey() == null || query.tenantId() == null
                || !OWNER_CONTEXT.equals(query.ownerKey().ownerContext())
                || !OBJECT_TYPE.equals(query.ownerKey().objectType())) return null;
        try {
            return rootMapper.selectById(new RequirementAnalysisRowQuery(
                    query.tenantId(), Long.valueOf(query.ownerKey().objectId())));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private DynamicFormPolicyFact policy(Long actorId, DynamicFormBusinessAction action, PreparationDO root) {
        if (root == null || action == null || actorId == null) return denied(action, "PRE04_ROOT_NOT_FOUND");
        boolean managerAction = requiresManager(action);
        if (!hasFunctionPermission(actorId, action)) return denied(action, "PRE04_ACTION_NOT_ALLOWED");
        ProjectScopeResult scope;
        try {
            scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(root.getTenantId(), actorId,
                    root.getProjectId(), managerAction ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW));
        } catch (RuntimeException unavailable) {
            return denied(action, "PRE04_ACTION_NOT_ALLOWED");
        }
        boolean authorized = scope != null && scope.fullProjectIds() != null
                && scope.fullProjectIds().contains(root.getProjectId())
                && (!managerAction || inspectManager(actorId, root.getProjectId()) != null);
        return policyFromLocked(action, root, authorized, scope == null ? null : scope.treeVersion());
    }

    private DynamicFormPolicyFact policyFromLocked(DynamicFormBusinessAction action, PreparationDO root,
                                                   boolean authorized, Long scopeVersion) {
        boolean draft = "DRAFT".equals(root.getStatusCode()) && Integer.valueOf(1).equals(root.getDraftMarker());
        boolean completed = "COMPLETED".equals(root.getStatusCode());
        boolean allowed = switch (action) {
            case READ, FILE_READ -> authorized;
            case CREATE, PATCH, COMPLETE, FILE_WRITE -> authorized && draft;
            case CLONE_SOURCE -> authorized && completed;
            case CLONE_TARGET -> authorized && draft;
            default -> false;
        };
        return new DynamicFormPolicyFact(action, allowed, allowed ? null : "PRE04_ACTION_NOT_ALLOWED",
                scopeVersion,
                root.getId() + ":" + root.getStatusCode() + ":" + root.getVersion());
    }

    private boolean compatibleFields(List<DynamicFormFieldDescriptor> fields) {
        if (fields == null) return false;
        Set<String> keys = new HashSet<>();
        for (DynamicFormFieldDescriptor field : fields) {
            if (field == null || field.fieldKey() == null || !keys.add(field.fieldKey())) return false;
        }
        for (String core : CORE_CODES) {
            DynamicFormFieldDescriptor text = field(fields, core);
            DynamicFormFieldDescriptor attachment = field(fields, core + "__ATTACHMENTS");
            if (text == null || text.controlledFile() || !"Editor".equals(text.componentType())
                    || text.required() != REQUIRED_CORE_CODES.contains(core)
                    || attachment == null || !attachment.controlledFile()
                    || attachment.required()
                    || !"PmsFileArtifact".equals(attachment.componentType())) return false;
        }
        return true;
    }

    private DynamicFormFieldDescriptor field(List<DynamicFormFieldDescriptor> fields, String key) {
        return fields.stream().filter(field -> key.equals(field.fieldKey())).findFirst().orElse(null);
    }

    private boolean requiresManager(DynamicFormBusinessAction action) {
        return action != DynamicFormBusinessAction.READ && action != DynamicFormBusinessAction.FILE_READ;
    }

    private boolean hasFunctionPermission(Long actorId, DynamicFormBusinessAction action) {
        if (action == DynamicFormBusinessAction.FILE_READ) {
            return permissionApi.hasAnyPermissions(actorId, PERMISSION_QUERY);
        }
        return requiresManager(action) ? permissionApi.hasAnyPermissions(actorId, PERMISSION_MANAGE)
                : permissionApi.hasAnyPermissions(actorId, PERMISSION_QUERY, PERMISSION_MANAGE);
    }

    private ProjectParticipantFact inspectManager(Long actorId, Long projectId) {
        ProjectParticipantFact fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                projectId, actorId, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now()));
        return fact != null && "ACTIVE".equals(fact.lifecycleStatus())
                && fact.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)
                ? fact : null;
    }

    private DynamicFormPolicyFact denied(DynamicFormBusinessAction action, String code) {
        return new DynamicFormPolicyFact(action, false, code, null, code);
    }
}
