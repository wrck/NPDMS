package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionIdentityQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Deprecated // 使用RequirementAnalysisDynamicFormPolicyProvider；仅保留历史候选文件事实解释。
public class RequirementAnalysisFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    static final String OWNER_CONTEXT = "SOL";
    static final String OBJECT_TYPE = "REQUIREMENT_ANALYSIS_SECTION";
    static final String PURPOSE_CODE = "SECTION_ATTACHMENT";
    static final String CATEGORY_CODE = "REQUIREMENT_ANALYSIS_ATTACHMENT";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> CATEGORIES = Set.of(CATEGORY_CODE);
    private static final Set<String> MEDIA_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> WRITE_ACTIONS = Set.of(FileActionCodes.UPLOAD, FileActionCodes.REFERENCE,
            FileActionCodes.REPLACE, FileActionCodes.DETACH);
    private static final Set<String> READ_ACTIONS = Set.of(
            FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final Set<String> MANAGER_ROLE = Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);

    private final RequirementAnalysisRootMapper rootMapper;
    private final RequirementAnalysisSectionMapper sectionMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;

    @Override
    public String ownerContext() {
        return OWNER_CONTEXT;
    }

    @Override
    public String objectType() {
        return OBJECT_TYPE;
    }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        Context context = locate(query.tenantId(), query.objectId(), false);
        if (context == null || !validKey(query.purposeCode(), query.referenceKey())) return denied();
        try {
            Authorization authorization = authorize(query.actorUserId(), query.requiredAction(), context);
            return policy(authorization.allowed(), authorization.scopeVersion(), context.root());
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        Context located = locate(query.tenantId(), query.objectId(), false);
        if (located == null || !validKey(query.purposeCode(), query.referenceKey())) return denied();
        try {
            ActionPolicy action = actionPolicy(query.requiredAction(), located.root());
            if (action == null) return policy(false, query.expectedScopeVersion(), located.root());
            ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    query.tenantId(), query.actorUserId(), located.root().getProjectId(), action.scopeAction(),
                    query.expectedScopeVersion()));
            if (!inScope(scope, located.root().getProjectId())) return denied();
            if (action.managerRequired()) lockManager(located.root().getProjectId(), query.actorUserId());
            Context locked = locate(query.tenantId(), query.objectId(), true);
            if (locked == null || !Objects.equals(locked.root().getProjectId(), located.root().getProjectId())) {
                return denied();
            }
            return policy(hasPermission(query.actorUserId(), action.permission()), scope.treeVersion(), locked.root());
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        if (!validNamespace(query.key())) return denied();
        Context context = locate(query.tenantId(), query.key().objectId(), false);
        if (context == null) return denied();
        try {
            Authorization authorization = authorize(query.actorUserId(), query.requiredAction(), context);
            return policy(authorization.allowed(), authorization.scopeVersion(), context.root());
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        if (!validNamespace(query.key())) return denied();
        Context located = locate(query.tenantId(), query.key().objectId(), false);
        if (located == null) return denied();
        try {
            ActionPolicy action = actionPolicy(query.requiredAction(), located.root());
            if (action == null) return policy(false, query.expectedScopeVersion(), located.root());
            ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    query.tenantId(), query.actorUserId(), located.root().getProjectId(), action.scopeAction(),
                    query.expectedScopeVersion()));
            if (!inScope(scope, located.root().getProjectId())) return denied();
            if (action.managerRequired()) lockManager(located.root().getProjectId(), query.actorUserId());
            Context locked = locate(query.tenantId(), query.key().objectId(), true);
            if (locked == null || !Objects.equals(locked.root().getProjectId(), located.root().getProjectId())) {
                return denied();
            }
            return policy(hasPermission(query.actorUserId(), action.permission()), scope.treeVersion(), locked.root());
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    private Authorization authorize(Long actorId, String action, Context context) {
        ActionPolicy actionPolicy = actionPolicy(action, context.root());
        if (actionPolicy == null) return new Authorization(false, null);
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                context.root().getTenantId(), actorId, context.root().getProjectId(), actionPolicy.scopeAction()));
        if (!inScope(scope, context.root().getProjectId())) return new Authorization(false, null);
        if (actionPolicy.managerRequired() && !isCurrentManager(context.root().getProjectId(), actorId)) {
            return new Authorization(false, scope.treeVersion());
        }
        return new Authorization(hasPermission(actorId, actionPolicy.permission()), scope.treeVersion());
    }

    private ActionPolicy actionPolicy(String action, PreparationDO root) {
        if ("DRAFT".equals(root.getStatusCode()) && (WRITE_ACTIONS.contains(action) || READ_ACTIONS.contains(action))) {
            return new ActionPolicy(RequirementAnalysisQueryService.PERMISSION_MANAGE,
                    ProjectScopeApi.ACTION_MANAGE, true);
        }
        if ("COMPLETED".equals(root.getStatusCode()) && READ_ACTIONS.contains(action)) {
            return new ActionPolicy(RequirementAnalysisQueryService.PERMISSION_QUERY,
                    ProjectScopeApi.ACTION_VIEW, false);
        }
        return null;
    }

    private boolean hasPermission(Long actorId, String permission) {
        return permissionApi.hasAnyPermissions(actorId, permission);
    }

    private boolean isCurrentManager(Long projectId, Long actorId) {
        ProjectParticipantFact fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                projectId, actorId, MANAGER_ROLE, LocalDateTime.now()));
        return validManager(fact, projectId, actorId);
    }

    private void lockManager(Long projectId, Long actorId) {
        ProjectParticipantFact inspected = participantFactApi.inspect(new ProjectParticipantFactQuery(
                projectId, actorId, MANAGER_ROLE, LocalDateTime.now()));
        if (!validManager(inspected, projectId, actorId)) throw new IllegalStateException("manager unavailable");
        ProjectParticipantFact locked = participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                projectId, actorId, inspected.projectVersion(), "ACTIVE", null, MANAGER_ROLE));
        if (!validManager(locked, projectId, actorId)) throw new IllegalStateException("manager changed");
    }

    private boolean validManager(ProjectParticipantFact fact, Long projectId, Long actorId) {
        return fact != null && Objects.equals(fact.projectId(), projectId) && Objects.equals(fact.userId(), actorId)
                && "ACTIVE".equals(fact.lifecycleStatus()) && fact.projectVersion() != null
                && fact.projectVersion() >= 0
                && fact.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
    }

    private Context locate(Long tenantId, String objectId, boolean lock) {
        Long sectionId;
        try {
            sectionId = Long.valueOf(objectId);
        } catch (RuntimeException invalid) {
            return null;
        }
        if (sectionId <= 0) return null;
        RequirementAnalysisSectionDO section = sectionMapper.selectByIdentity(
                new RequirementAnalysisSectionIdentityQuery(tenantId, sectionId));
        if (section == null) return null;
        PreparationDO root = lock
                ? rootMapper.selectForUpdate(new RequirementAnalysisRowQuery(tenantId, section.getPreparationId()))
                : rootMapper.selectById(new RequirementAnalysisRowQuery(tenantId, section.getPreparationId()));
        if (root == null) return null;
        if (lock) {
            section = sectionMapper.selectForUpdate(
                    new RequirementAnalysisSectionRowQuery(tenantId, root.getId(), sectionId));
        }
        return section == null ? null : new Context(root, section);
    }

    private boolean validKey(String purposeCode, String referenceKey) {
        if (!PURPOSE_CODE.equals(purposeCode)) return false;
        try {
            return UUID.fromString(referenceKey).toString().equalsIgnoreCase(referenceKey);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean validNamespace(cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey key) {
        return key != null && OWNER_CONTEXT.equals(key.ownerContext()) && OBJECT_TYPE.equals(key.objectType())
                && PURPOSE_CODE.equals(key.purposeCode());
    }

    private boolean inScope(ProjectScopeResult scope, Long projectId) {
        return scope != null && scope.treeVersion() != null && scope.treeVersion() >= 0
                && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId);
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long scopeVersion, PreparationDO root) {
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion,
                "DRAFT".equals(root.getStatusCode()) ? "MUTABLE" : "IMMUTABLE", "MULTIPLE",
                CATEGORIES, MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }

    private record Context(PreparationDO root, RequirementAnalysisSectionDO section) {
    }

    private record ActionPolicy(String permission, String scopeAction, boolean managerRequired) {
    }

    private record Authorization(boolean allowed, Long scopeVersion) {
    }
}
