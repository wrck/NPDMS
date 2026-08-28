package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectParticipantFactApiImpl implements ProjectParticipantFactApi {

    private static final String ACTIVE = "ACTIVE";
    private static final String INITIAL_STAGE = "S1";
    private static final String PRIMARY = "PRIMARY";
    private static final Set<String> SUPPORTED_ROLES = Set.of(
            ROLE_PROJECT_MANAGER, ROLE_SERVICE_MANAGER_L1, ROLE_SERVICE_MANAGER_L2);

    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;

    @Override
    public ProjectParticipantFact inspect(ProjectParticipantFactQuery query) {
        Long tenantId = trustedTenantId();
        validateInspect(query);
        ProjectMasterDO project = projectMapper.selectById(query.projectId());
        requireProject(project, tenantId);
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectParticipantFacts(
                new ProjectParticipantFactLookupQuery(tenantId, query.projectId(), query.subjectUserId(),
                        serviceRoles(query.requiredRoleCodes()), query.checkedAt()));
        return resolve(project, tenantId, query.subjectUserId(), query.requiredRoleCodes(), assignments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectParticipantFact lockAndRevalidate(ProjectParticipantFactRevalidationQuery query) {
        Long tenantId = trustedTenantId();
        validateRevalidation(query);
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(query.projectId());
        requireProject(project, tenantId);
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        if (!Objects.equals(project.getLifecycleStatus(), query.requiredLifecycleStatus())
                || query.requiredCurrentStage() != null
                && !Objects.equals(project.getCurrentStage(), query.requiredCurrentStage())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        List<ProjectMemberAssignmentDO> assignments = memberMapper.selectParticipantFactsForUpdate(
                new ProjectParticipantFactLockQuery(tenantId, query.projectId(), query.userId(),
                        serviceRoles(query.requiredRoleCodes())));
        return resolve(project, tenantId, query.userId(), query.requiredRoleCodes(), assignments);
    }

    private ProjectParticipantFact resolve(ProjectMasterDO project, Long tenantId, Long subjectUserId,
                                           Set<String> requiredRoles,
                                           List<ProjectMemberAssignmentDO> assignments) {
        Map<Long, Set<String>> rolesByUser = new HashMap<>();
        if (requiredRoles.contains(ROLE_PROJECT_MANAGER) && project.getManagerId() != null
                && (subjectUserId == null || Objects.equals(subjectUserId, project.getManagerId()))) {
            rolesByUser.computeIfAbsent(project.getManagerId(), ignored -> new HashSet<>())
                    .add(ROLE_PROJECT_MANAGER);
        }
        for (ProjectMemberAssignmentDO assignment : assignments) {
            requireAssignment(assignment, tenantId, project.getId(), subjectUserId, requiredRoles);
            rolesByUser.computeIfAbsent(assignment.getUserId(), ignored -> new HashSet<>())
                    .add(assignment.getMemberRole());
        }
        if (subjectUserId != null) {
            rolesByUser.keySet().removeIf(userId -> !Objects.equals(userId, subjectUserId));
        }
        if (rolesByUser.size() != 1) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        Map.Entry<Long, Set<String>> selected = rolesByUser.entrySet().iterator().next();
        long factVersion = project.getVersion() == null ? 0L : project.getVersion().longValue();
        return new ProjectParticipantFact(project.getId(), selected.getKey(), selected.getValue(), PRIMARY,
                project.getLifecycleStatus(), project.getCurrentStage(), project.getVersion(), factVersion);
    }

    private void validateInspect(ProjectParticipantFactQuery query) {
        if (query == null || query.projectId() == null || query.projectId() <= 0
                || query.subjectUserId() != null && query.subjectUserId() <= 0
                || query.checkedAt() == null || !validRoles(query.requiredRoleCodes())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void validateRevalidation(ProjectParticipantFactRevalidationQuery query) {
        if (query == null || query.projectId() == null || query.projectId() <= 0
                || query.userId() == null || query.userId() <= 0
                || query.expectedProjectVersion() == null || query.expectedProjectVersion() < 0
                || !ACTIVE.equals(query.requiredLifecycleStatus())
                || query.requiredCurrentStage() != null && !INITIAL_STAGE.equals(query.requiredCurrentStage())
                || !validRoles(query.requiredRoleCodes())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private boolean validRoles(Set<String> roles) {
        return roles != null && !roles.isEmpty() && SUPPORTED_ROLES.containsAll(roles);
    }

    private Set<String> serviceRoles(Set<String> requiredRoles) {
        Set<String> roles = new HashSet<>(requiredRoles);
        roles.remove(ROLE_PROJECT_MANAGER);
        return Set.copyOf(roles);
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return tenantId;
    }

    private void requireProject(ProjectMasterDO project, Long tenantId) {
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void requireAssignment(ProjectMemberAssignmentDO assignment, Long tenantId, Long projectId,
                                   Long subjectUserId, Set<String> requiredRoles) {
        if (assignment == null || !Objects.equals(assignment.getTenantId(), tenantId)
                || !Objects.equals(assignment.getProjectId(), projectId)
                || assignment.getUserId() == null
                || subjectUserId != null && !Objects.equals(assignment.getUserId(), subjectUserId)
                || !requiredRoles.contains(assignment.getMemberRole())
                || assignment.getAssignmentType() != null && !PRIMARY.equals(assignment.getAssignmentType())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

}
