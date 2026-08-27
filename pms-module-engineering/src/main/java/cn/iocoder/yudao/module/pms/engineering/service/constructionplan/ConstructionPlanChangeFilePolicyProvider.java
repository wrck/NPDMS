package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeObjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** SOL工期变更客户延期依据的业务对象权限事实。 */
@Component
@RequiredArgsConstructor
public class ConstructionPlanChangeFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    static final String OWNER_CONTEXT = "SOL";
    static final String OBJECT_TYPE = "CONSTRUCTION_PLAN_CHANGE";
    static final String PURPOSE_CODE = "CUSTOMER_DELAY_EVIDENCE";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> CATEGORIES = Set.of(PURPOSE_CODE);
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");
    private static final Set<String> PROJECT_MANAGER_ROLE =
            Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
    private static final Set<String> FILE_READER_ROLES = Set.of(
            ProjectParticipantFactApi.ROLE_PROJECT_MANAGER,
            ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1,
            ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L2);
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.REPLACE,
            FileActionCodes.DETACH, FileActionCodes.ARCHIVE, FileActionCodes.INVALIDATE);

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanChangeMapper changeMapper;
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
        return inspect(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.requiredAction());
    }

    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return inspect(query.tenantId(), query.actorUserId(), query.key().objectId(),
                query.key().purposeCode(), query.requiredAction());
    }

    private FileBusinessObjectPolicyFact inspect(Long tenantId, Long actorUserId, String objectId,
                                                 String purposeCode, String requiredAction) {
        Context context = locate(tenantId, objectId);
        if (context == null || !PURPOSE_CODE.equals(purposeCode)) {
            return denied();
        }
        return inspectCurrent(actorUserId, requiredAction, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(
            FileBusinessObjectPolicyRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.objectId(),
                query.purposeCode(), query.requiredAction(), query.expectedScopeVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.key().objectId(),
                query.key().purposeCode(), query.requiredAction(), query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact lockAndRevalidate(Long tenantId, Long actorUserId,
                                                            String objectId, String purposeCode,
                                                            String requiredAction,
                                                            Long expectedScopeVersion) {
        Context located = locate(tenantId, objectId);
        if (located == null || !PURPOSE_CODE.equals(purposeCode)) {
            return denied();
        }
        boolean mutating = MUTATING_ACTIONS.contains(requiredAction);
        Set<String> roles = mutating ? PROJECT_MANAGER_ROLE : FILE_READER_ROLES;
        String scopeAction = mutating ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        ProjectScopeResult lockedScope = projectScopeApi.lockAndRevalidate(
                new ProjectScopeRevalidationQuery(tenantId, actorUserId,
                        located.plan().getProjectId(), scopeAction, expectedScopeVersion));
        requireScope(lockedScope, located.plan().getProjectId());
        if (!Objects.equals(lockedScope.treeVersion(), expectedScopeVersion)) {
            return policy(actionAllowed(requiredAction, located.change()),
                    lockedScope.treeVersion(), located.change());
        }
        ProjectParticipantFact participant = participantFactApi.inspect(new ProjectParticipantFactQuery(
                located.plan().getProjectId(), actorUserId, roles, LocalDateTime.now()));
        requireParticipant(participant, located.plan().getProjectId(), actorUserId, roles);
        ProjectParticipantFact lockedParticipant = participantFactApi.lockAndRevalidate(
                new ProjectParticipantFactRevalidationQuery(
                located.plan().getProjectId(), actorUserId, participant.projectVersion(),
                "ACTIVE", null, roles));
        requireParticipant(lockedParticipant, located.plan().getProjectId(), actorUserId, roles);

        ConstructionPlanDO lockedPlan = planMapper.selectForUpdate(
                new ConstructionPlanLockQuery(tenantId, located.plan().getId()));
        ConstructionPlanChangeDO lockedChange = lockedPlan == null ? null : changeMapper.selectForUpdate(
                new ConstructionPlanChangeLockQuery(tenantId, lockedPlan.getId(),
                        located.change().getId()));
        if (lockedPlan == null || lockedChange == null
                || !Objects.equals(lockedPlan.getProjectId(), located.plan().getProjectId())) {
            return denied();
        }
        return policy(actionAllowed(requiredAction, lockedChange),
                lockedScope.treeVersion(), lockedChange);
    }

    private FileBusinessObjectPolicyFact inspectCurrent(Long actorUserId, String action, Context context) {
        boolean mutating = MUTATING_ACTIONS.contains(action);
        Set<String> roles = mutating ? PROJECT_MANAGER_ROLE : FILE_READER_ROLES;
        String scopeAction = mutating ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        ProjectScopeResult scope = requireScope(context.change().getTenantId(), actorUserId,
                context.plan().getProjectId(), scopeAction);
        ProjectParticipantFact participant = participantFactApi.inspect(new ProjectParticipantFactQuery(
                context.plan().getProjectId(), actorUserId, roles, LocalDateTime.now()));
        requireParticipant(participant, context.plan().getProjectId(), actorUserId, roles);
        return policy(actionAllowed(action, context.change()), scope.treeVersion(), context.change());
    }

    private void requireParticipant(ProjectParticipantFact participant, Long projectId,
                                    Long actorUserId, Set<String> requiredRoles) {
        if (participant == null || !Objects.equals(participant.projectId(), projectId)
                || !Objects.equals(participant.userId(), actorUserId)
                || participant.projectVersion() == null || participant.projectVersion() < 0
                || participant.effectiveRoleCodes().stream().noneMatch(requiredRoles::contains)) {
            throw new IllegalStateException("SOL_FILE_PARTICIPANT_FORBIDDEN");
        }
    }

    private Context locate(Long tenantId, String objectId) {
        Long changeId;
        try {
            changeId = Long.valueOf(objectId);
        } catch (RuntimeException failure) {
            return null;
        }
        if (changeId <= 0) return null;
        ConstructionPlanChangeDO change = changeMapper.selectByObjectId(
                new ConstructionPlanChangeObjectQuery(tenantId, changeId));
        if (change == null) return null;
        ConstructionPlanDO plan = planMapper.selectById(
                new ConstructionPlanLockQuery(tenantId, change.getPlanId()));
        if (plan == null) return null;
        return new Context(plan, change);
    }

    private ProjectScopeResult requireScope(Long tenantId, Long actorUserId, Long projectId,
                                            String action) {
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(
                new ProjectCurrentScopeQuery(tenantId, actorUserId, projectId, action));
        requireScope(scope, projectId);
        return scope;
    }

    private void requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.treeVersion() < 0
                || !scope.fullProjectIds().contains(projectId)) {
            throw new IllegalStateException("SOL_FILE_SCOPE_FORBIDDEN");
        }
    }

    private boolean actionAllowed(String action, ConstructionPlanChangeDO change) {
        return !MUTATING_ACTIONS.contains(action)
                || ConstructionPlanChangeDO.STATUS_DRAFT.equals(change.getStatusCode());
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long scopeVersion,
                                                ConstructionPlanChangeDO change) {
        String mutability = ConstructionPlanChangeDO.STATUS_DRAFT.equals(change.getStatusCode())
                ? "MUTABLE" : "IMMUTABLE";
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion, mutability, "SINGLE",
                CATEGORIES, MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null,
                Set.of(), Set.of(), null, null);
    }

    private record Context(ConstructionPlanDO plan, ConstructionPlanChangeDO change) {
    }
}
