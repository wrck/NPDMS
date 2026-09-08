package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectManagerMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectManagerMemberUpdate;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserPageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** PM-01：项目经理成员写入，不改变旧服务经理命令和项目阶段。 */
@Service
@RequiredArgsConstructor
public class ProjectManagerMemberApplicationService {
    private static final String ROLE = "PROJECT_MANAGER";
    private static final String SCOPE = "POST:/api/v1/pms/projects/{id}/actions/update-project-managers";
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final OrganizationScopeApi organizationScopeApi;
    private final ProjectCreationAuthorizationService functionAuthorization;
    private final ProjectAuthorizationGuard projectAuthorization;
    private final PlatformCommandExecutionApi commandApi;

    @Transactional(rollbackFor = Exception.class)
    public ProjectManagerMemberResult update(ProjectManagerMemberCommand command, Actor actor) {
        validate(command, actor);
        functionAuthorization.assertCanAssign(actor.userId());
        projectAuthorization.assertCanAssign(new ProjectAuthorizationGuard.Actor(actor.tenantId(), actor.userId()),
                command.projectId());
        Map<String, Object> audit = new LinkedHashMap<>();
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(new Object[]{command.projectId(),
                command.expectedVersion(), new TreeSet<>(command.addUserIds()), new TreeSet<>(command.removeUserIds()),
                command.primaryUserId(), command.reason().trim()}));
        var result = commandApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE, actor.userId(), command.idempotencyKey()), digest,
                ProjectManagerMemberResult.class, () -> updateOnce(command, actor, audit),
                changed -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_MANAGERS_UPDATE", "Project",
                        String.valueOf(command.projectId()), actor.correlationId(), JsonUtils.toJsonString(audit),
                        changed.changed() ? "ProjectManagersChanged" : null,
                        changed.changed() ? JsonUtils.toJsonString(changed) : null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }

    private ProjectManagerMemberResult updateOnce(ProjectManagerMemberCommand command, Actor actor,
                                                   Map<String, Object> audit) {
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(command.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        if (!Objects.equals(project.getVersion(), command.expectedVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        var query = new ProjectManagerMemberQuery(actor.tenantId(), project.getId(), now);
        Map<Long, ProjectMemberAssignmentDO> members = new TreeMap<>();
        for (var member : memberMapper.selectProjectManagersForUpdate(query)) {
            if (members.put(member.getUserId(), member) != null) throw invalid("同一经理存在重叠责任区间");
        }
        audit.put("beforeUserIds", new ArrayList<>(members.keySet()));
        audit.put("beforePrimaryUserId", project.getManagerId());
        audit.put("reason", command.reason().trim());
        audit.put("actorUserId", actor.userId());
        audit.put("effectiveAt", now);
        audit.put("beforeProjectVersion", project.getVersion());
        Set<Long> next = new TreeSet<>(members.keySet());
        next.removeAll(command.removeUserIds());
        next.addAll(command.addUserIds());
        Long primary = command.primaryUserId() != null ? command.primaryUserId() : project.getManagerId();
        if (next.isEmpty() && command.primaryUserId() == null) primary = null;
        if (!next.isEmpty() && (primary == null || !next.contains(primary))
                || next.isEmpty() && primary != null) throw invalid("请从保留的经理中明确选择主责");
        Set<Long> additions = new TreeSet<>(next);
        additions.removeAll(members.keySet());
        Set<Long> removals = new TreeSet<>(members.keySet());
        removals.removeAll(next);
        boolean changed = !additions.isEmpty() || !removals.isEmpty() || !Objects.equals(primary, project.getManagerId());
        if (!changed) return result(project, members, false);
        Set<Long> toValidate = new TreeSet<>(additions);
        if (primary != null && !Objects.equals(primary, project.getManagerId())) toValidate.add(primary);
        Map<Long, CompanyRoleUserRespDTO> qualified = qualify(project.getCompanyId(), toValidate);
        audit.put("qualifiedUserIds", new ArrayList<>(qualified.keySet()));
        for (Long id : removals) {
            var old = members.remove(id);
            var close = new ProjectMemberAssignmentDO();
            close.setId(old.getId());
            close.setEffectiveTo(now);
            close.setVersion(old.getVersion() == null ? 1 : old.getVersion() + 1);
            if (memberMapper.updateById(close) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        }
        for (Long id : additions) {
            var member = new ProjectMemberAssignmentDO();
            member.setTenantId(actor.tenantId());
            member.setProjectId(project.getId());
            member.setUserId(id);
            member.setMemberName(qualified.get(id).getNickname());
            member.setCompanyId(project.getCompanyId());
            member.setCompanyCode(project.getCompanyCode());
            member.setCompanyName(project.getCompanyName());
            member.setMemberRole(ROLE);
            member.setAssignmentType(Objects.equals(primary, id) ? "PRIMARY" : "COLLABORATOR");
            member.setChangeReason(command.reason().trim());
            member.setEffectiveFrom(now);
            member.setStatus("ACTIVE");
            member.setVersion(0);
            if (memberMapper.insert(member) != 1) throw exception(PROJECT_VERSION_CONFLICT);
            members.put(id, member);
        }
        var selected = primary == null ? null : members.get(primary);
        boolean serviceManager = memberMapper.selectActiveForAssignmentState(
                new ProjectAssignmentStateQuery(project.getId(), now)).stream().anyMatch(row ->
                Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2").contains(row.getMemberRole())
                        && (row.getAssignmentType() == null || "PRIMARY".equals(row.getAssignmentType())));
        String status = selected != null && serviceManager ? "ASSIGNED" : "UNASSIGNED";
        if (projectMapper.updateManagerMembersIfMatch(new ProjectManagerMemberUpdate(actor.tenantId(), project.getId(),
                project.getVersion(), primary, selected == null ? null : selected.getMemberName(),
                selected == null ? null : selected.getEmployeeNo(), status, String.valueOf(actor.userId()))) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        project.setManagerId(primary);
        project.setAssignmentStatus(status);
        project.setVersion(project.getVersion() + 1);
        audit.put("afterUserIds", new ArrayList<>(members.keySet()));
        audit.put("afterPrimaryUserId", primary);
        audit.put("projectVersion", project.getVersion());
        return result(project, members, true);
    }

    private Map<Long, CompanyRoleUserRespDTO> qualify(Long companyId, Set<Long> userIds) {
        Map<Long, CompanyRoleUserRespDTO> found = new TreeMap<>();
        if (userIds.isEmpty()) return found;
        var query = new CompanyRoleUserPageReqDTO().setCompanyId(companyId).setRoleCode(ROLE).setUserIds(userIds);
        query.setPageSize(100);
        for (int page = 1; ; page++) {
            query.setPageNo(page);
            var result = organizationScopeApi.pageCompanyRoleUsers(query);
            for (var user : result.getList()) {
                if (!Objects.equals(companyId, user.getCompanyId()) || !ROLE.equals(user.getRoleCode()))
                    throw invalid("上游人员资格身份不一致");
                found.put(user.getUserId(), user);
            }
            if ((long) page * 100 >= result.getTotal()) break;
        }
        if (!found.keySet().equals(userIds)) throw invalid("人员不具备当前公司项目经理资格");
        return found;
    }

    private static ProjectManagerMemberResult result(ProjectMasterDO project,
                                                      Map<Long, ProjectMemberAssignmentDO> members, boolean changed) {
        return new ProjectManagerMemberResult(project.getId(), project.getVersion(), project.getManagerId(),
                project.getAssignmentStatus(), changed, members.values().stream().map(row ->
                new ProjectManagerMemberResult.Member(row.getId(), row.getUserId(), row.getMemberName(),
                        row.getEffectiveFrom())).toList());
    }

    private static void validate(ProjectManagerMemberCommand command, Actor actor) {
        if (command == null || actor == null || actor.tenantId() == null || actor.tenantId() < 0 || actor.userId() == null
                || actor.userId() <= 0 || actor.correlationId() == null || actor.correlationId().isBlank()
                || command.projectId() == null || command.projectId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.reason() == null || command.reason().isBlank() || command.reason().trim().length() > 500
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128
                || command.primaryUserId() != null && command.primaryUserId() <= 0
                || command.addUserIds().stream().anyMatch(id -> id <= 0)
                || command.removeUserIds().stream().anyMatch(id -> id <= 0)
                || command.addUserIds().stream().anyMatch(command.removeUserIds()::contains)) throw invalid("成员操作参数无效");
    }

    private static RuntimeException invalid(String reason) { return exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, reason); }
    public record Actor(Long tenantId, Long userId, String correlationId) { }
}
