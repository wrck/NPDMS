package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStatusUpdate;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.*;

/** 专项统一成员维护：一份角色关系，经理身份变化编排原命令，资料变化追加区间。 */
@Service
@RequiredArgsConstructor
public class OrdinaryProjectMemberService {
    public static final String WRITE_PERMISSION = "pms:project-team:create";
    public static final Set<String> ORDINARY_ROLES = ORDINARY_CODES;
    private static final Set<String> ALL_ROLES = ALL_CODES;
    private final ProjectManualCreationService projects;
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ActiveUserSelectionApi users;
    private final DeptApi departments;
    private final PermissionCommonApi permissions;
    private final ProjectAuthorizationGuard authorization;
    private final PlatformCommandExecutionApi commands;
    private final ProjectManagerMemberApplicationService projectManagers;

    public PageResult<ProjectMemberAssignmentDO> page(Long projectId, Filter filter, Actor actor) {
        validateActor(actor);
        if (filter == null || filter.pageNo() < 1 || filter.pageSize() < 1 || filter.pageSize() > 100
                || filter.state() == null || !Set.of("CURRENT", "HISTORY").contains(filter.state())
                || normalized(filter.role()) != null && !ALL_ROLES.contains(filter.role())
                || filter.keyword() != null && filter.keyword().length() > 64) throw invalid("成员查询条件无效");
        projects.getProject(projectId, accessActor(actor));
        PageParam page = new PageParam();
        page.setPageNo(filter.pageNo());
        page.setPageSize(filter.pageSize());
        return memberMapper.selectMemberPage(ProjectMemberPageQuery.builder().tenantId(actor.tenantId())
                .projectId(projectId).page(page).state(filter.state()).role(normalized(filter.role()))
                .keyword(normalized(filter.keyword())).effectiveAt(LocalDateTime.now()).build());
    }

    public PageResult<ActiveUserSelectionApi.User> candidates(Long projectId, CandidateFilter query, Actor actor) {
        validateActor(actor);
        if (query == null) throw invalid("请选择项目角色");
        requireWritePermission(actor, query.projectRole());
        var scopeActor = new ProjectAuthorizationGuard.Actor(actor.tenantId(), actor.userId());
        if (managerRole(query.projectRole())) {
            authorization.assertCanInitiallyAssign(scopeActor, projectId,
                    SERVICE_MANAGER.equals(query.projectRole()), PROJECT_MANAGER.equals(query.projectRole()));
        } else authorization.assertCanAssign(scopeActor, projectId);
        var project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        return users.page(selection(project, query.projectRole(), query.scope(), query.pageNo(), query.pageSize(),
                query.keyword(), query.userId() == null ? null : Set.of(query.userId())));
    }

    // REQUIRED：沿用平台幂等事务，区间、项目版本、审计同成同败。
    // https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
    @Transactional(rollbackFor = Exception.class)
    public Result mutate(Command command, Actor actor) {
        validateActor(actor);
        validateCommand(command);
        String role = command.member() == null ? null : command.member().memberRole();
        if (role != null) requireWritePermission(actor, role);
        else if (!permissions.hasAnyPermissions(actor.userId(), WRITE_PERMISSION)
                && !permissions.hasAnyPermissions(actor.userId(), "pms:project:assign"))
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        var scopeActor = new ProjectAuthorizationGuard.Actor(actor.tenantId(), actor.userId());
        if (command.action() == Action.ADD && managerRole(role)) {
            authorization.assertCanInitiallyAssign(scopeActor, command.projectId(), SERVICE_MANAGER.equals(role), PROJECT_MANAGER.equals(role));
        } else authorization.assertCanAssign(scopeActor, command.projectId());
        if (command.action() != Action.ADD) {
            var target = memberMapper.selectById(command.assignmentId());
            if (target == null || !Objects.equals(target.getTenantId(), actor.tenantId())
                    || !Objects.equals(target.getProjectId(), command.projectId())) throw exception(PROJECT_TEAM_MEMBER_NOT_EXISTS);
            requireWritePermission(actor, logicalRole(target.getMemberRole()));
        }
        String scope = "PROJECT_MEMBER_MAINTENANCE:" + command.action();
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(new Object[]{command.projectId(),
                command.expectedVersion(), command.assignmentId(), command.member(), command.reason().trim(), command.replacementPrimaryUserId()}));
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(),
                        scope, actor.userId(), command.idempotencyKey()), digest, Result.class,
                () -> mutateOnce(command, actor), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_MEMBER_" + command.action(), "Project", String.valueOf(command.projectId()),
                        actor.correlationId(), JsonUtils.toJsonString(new Audit(command.assignmentId(),
                        command.member(), command.reason().trim(), result)), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private Result mutateOnce(Command command, Actor actor) {
        ProjectMasterDO project = projectMapper.selectByIdForUpdate(command.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        if (!Objects.equals(project.getVersion(), command.expectedVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        ProjectMemberAssignmentDO previous = command.assignmentId() == null ? null
                : memberMapper.selectById(command.assignmentId());
        if (command.action() != Action.ADD) {
            if (previous == null || !Objects.equals(previous.getTenantId(), actor.tenantId())
                    || !Objects.equals(previous.getProjectId(), project.getId())) throw exception(PROJECT_TEAM_MEMBER_NOT_EXISTS);
            String previousRole = logicalRole(previous.getMemberRole());
            if (!ORDINARY_ROLES.contains(previousRole) && !managerRole(previousRole)) throw exception(PROJECT_MEMBER_ROLE_INVALID);
            if (command.action() == Action.UPDATE && !previousRole.equals(command.member().memberRole()))
                throw invalid("角色变更请移出原角色后重新添加");
            if (!current(previous, now)) {
                if (command.action() == Action.REMOVE && previous.getEffectiveTo() != null)
                    return result(project, previous, false);
                throw invalid("历史成员不可修改，请重新加入有效人员");
            }
        }
        String role = previous != null ? logicalRole(previous.getMemberRole()) : command.member().memberRole();
        if (SERVICE_MANAGER.equals(role)) return serviceManagerMutation(project, previous, command, actor, now);
        if (managerRole(role)) return managerMutation(project, previous, command, actor, now);
        if (command.action() == Action.REMOVE) {
            close(previous, command.reason(), now);
            incrementVersion(project);
            previous.setEffectiveTo(now);
            return result(project, previous, true);
        }
        MemberValues values = command.member();
        if (previous != null && Objects.equals(previous.getUserId(), values.userId())
                && Objects.equals(previous.getMemberRole(), values.memberRole())
                && Objects.equals(normalized(previous.getResponsibility()), normalized(values.responsibility()))
                && Objects.equals(normalized(previous.getRemark()), normalized(values.remark()))) {
            return result(project, previous, false);
        }
        var selected = users.page(selection(project, values.memberRole(), null, 1, 1, null, Set.of(values.userId())));
        if (selected.getTotal() != 1 || selected.getList().size() != 1
                || !Objects.equals(values.userId(), selected.getList().getFirst().id()))
            throw invalid("请选择具有对应系统角色的当前租户有效用户");
        var duplicate = memberMapper.selectActiveMemberIdentityForUpdate(new ProjectMemberIdentityQuery(
                actor.tenantId(), project.getId(), values.userId(), values.memberRole(), now));
        if (duplicate.stream().anyMatch(row -> previous == null || !Objects.equals(row.getId(), previous.getId())))
            throw exception(PROJECT_TEAM_MEMBER_DUPLICATE);
        if (previous != null) close(previous, command.reason(), now);
        var user = selected.getList().getFirst();
        var member = new ProjectMemberAssignmentDO();
        member.setTenantId(actor.tenantId());
        member.setProjectId(project.getId());
        member.setUserId(user.id());
        member.setMemberName(user.nickname());
        member.setDepartmentId(user.deptId());
        if (user.deptId() != null) {
            var dept = departments.getDept(user.deptId());
            if (dept != null) { member.setDepartmentCode(dept.getCode()); member.setDepartmentName(dept.getName()); }
        }
        member.setMemberRole(values.memberRole());
        member.setResponsibility(normalized(values.responsibility()));
        member.setRemark(normalized(values.remark()));
        member.setChangeReason(command.reason().trim());
        member.setEffectiveFrom(now);
        member.setStatus("ACTIVE");
        member.setVersion(0);
        if (memberMapper.insert(member) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        incrementVersion(project);
        return result(project, member, true);
    }

    private ActiveUserSelectionApi.Query selection(ProjectMasterDO project, String role, ServiceScope scope,
            int pageNo, int pageSize, String keyword, Set<Long> userIds) {
        String systemRole = switch (role) {
            case TEAM_MEMBER, PROJECT_MANAGER -> PROJECT_MANAGER;
            case SALES_REPRESENTATIVE -> SALES_REPRESENTATIVE;
            case SERVICE_MANAGER -> SERVICE_MANAGER;
            default -> throw exception(PROJECT_MEMBER_ROLE_INVALID);
        };
        var qualification = PROJECT_MANAGER.equals(role)
                ? new ActiveUserSelectionApi.Qualification(project.getCompanyId(), null, null, PROJECT_MANAGER) : null;
        return new ActiveUserSelectionApi.Query(pageNo, pageSize, keyword, userIds, systemRole, qualification);
    }

    private static boolean managerRole(String role) {
        return PROJECT_MANAGER.equals(role) || SERVICE_MANAGER.equals(role);
    }

    private static String logicalRole(String role) {
        return cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.normalize(role);
    }

    private Result serviceManagerMutation(ProjectMasterDO project, ProjectMemberAssignmentDO previous,
            Command command, Actor actor, LocalDateTime now) {
        if (command.action() == Action.REMOVE) {
            close(previous, command.reason(), now);
            incrementVersion(project);
            refreshAssignmentStatus(project, now);
            previous.setEffectiveTo(now);
            return result(project, previous, true);
        }
        MemberValues values = command.member();
        var selected = users.page(selection(project, SERVICE_MANAGER, null, 1, 1, null, Set.of(values.userId())));
        if (selected.getTotal() != 1 || selected.getList().size() != 1
                || !Objects.equals(values.userId(), selected.getList().getFirst().id()))
            throw invalid("请选择当前租户内具有服务经理角色的有效人员");
        var duplicates = memberMapper.selectActiveMemberIdentityForUpdate(new ProjectMemberIdentityQuery(
                actor.tenantId(), project.getId(), values.userId(), SERVICE_MANAGER, now));
        if (duplicates.stream().anyMatch(row -> previous == null || !Objects.equals(row.getId(), previous.getId())))
            throw exception(PROJECT_TEAM_MEMBER_DUPLICATE);
        var active = memberMapper.selectActiveForAssignmentState(new ProjectAssignmentStateQuery(project.getId(), now))
                .stream().filter(row -> SERVICE_MANAGER.equals(logicalRole(row.getMemberRole()))).toList();
        boolean primary = Boolean.TRUE.equals(values.primary()) || previous != null && servicePrimary(previous)
                || active.stream().noneMatch(OrdinaryProjectMemberService::servicePrimary);
        if (previous != null && Objects.equals(previous.getUserId(), values.userId())
                && SERVICE_MANAGER.equals(previous.getMemberRole()) && servicePrimary(previous) == primary
                && Objects.equals(normalized(previous.getResponsibility()), normalized(values.responsibility()))
                && Objects.equals(normalized(previous.getRemark()), normalized(values.remark())))
            return result(project, previous, false);
        if (primary) {
            active.stream().filter(row -> servicePrimary(row)
                    && (previous == null || !Objects.equals(previous.getId(), row.getId()))).forEach(row -> {
                close(row, command.reason(), now);
                insertServiceInterval(row, false, command.reason(), now);
            });
        }
        if (previous != null) close(previous, command.reason(), now);
        var current = new ProjectMemberAssignmentDO();
        current.setTenantId(actor.tenantId()); current.setProjectId(project.getId());
        current.setUserId(values.userId()); current.setMemberName(selected.getList().getFirst().nickname());
        current.setResponsibility(normalized(values.responsibility())); current.setRemark(normalized(values.remark()));
        var created = insertServiceInterval(current, primary, command.reason(), now);
        incrementVersion(project);
        refreshAssignmentStatus(project, now);
        return result(project, created, true);
    }

    private static boolean servicePrimary(ProjectMemberAssignmentDO member) {
        return member.getAssignmentType() == null || "PRIMARY".equals(member.getAssignmentType());
    }

    private ProjectMemberAssignmentDO insertServiceInterval(ProjectMemberAssignmentDO source, boolean primary,
            String reason, LocalDateTime now) {
        var fresh = new ProjectMemberAssignmentDO();
        fresh.setTenantId(source.getTenantId()); fresh.setProjectId(source.getProjectId());
        fresh.setUserId(source.getUserId()); fresh.setEmployeeNo(source.getEmployeeNo()); fresh.setMemberName(source.getMemberName());
        fresh.setMemberRole(SERVICE_MANAGER); fresh.setAssignmentType(primary ? "PRIMARY" : "COLLABORATOR");
        fresh.setResponsibility(source.getResponsibility()); fresh.setRemark(source.getRemark());
        fresh.setChangeReason(reason.trim()); fresh.setEffectiveFrom(now); fresh.setStatus("ACTIVE"); fresh.setVersion(0);
        if (memberMapper.insert(fresh) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        return fresh;
    }

    private Result managerMutation(ProjectMasterDO project, ProjectMemberAssignmentDO previous,
            Command command, Actor actor, LocalDateTime now) {
        String role = previous == null ? command.member().memberRole() : logicalRole(previous.getMemberRole());
        if (command.action() == Action.REMOVE) {
            var changed = projectManagers.update(new ProjectManagerMemberCommand(project.getId(), project.getVersion(),
                    Set.of(), Set.of(previous.getUserId()), command.replacementPrimaryUserId(), command.reason(),
                    command.idempotencyKey()), new ProjectManagerMemberApplicationService.Actor(
                    actor.tenantId(), actor.userId(), actor.correlationId()));
            project.setVersion(changed.version());
            recordEndReason(previous.getId(), command.reason());
            return result(project, memberMapper.selectById(previous.getId()), changed.changed());
        }
        MemberValues values = command.member();
        var qualified = users.page(selection(project, role, values.scope(), 1, 1, null, Set.of(values.userId())));
        if (qualified.getTotal() != 1 || qualified.getList().size() != 1
                || !Objects.equals(values.userId(), qualified.getList().getFirst().id())) {
            throw invalid("请选择具有对应系统角色和组织资格的当前租户有效用户");
        }
        boolean samePerson = previous != null && Objects.equals(previous.getUserId(), values.userId());
        boolean detailsChanged = previous != null && (!Objects.equals(normalized(previous.getResponsibility()), normalized(values.responsibility()))
                || !Objects.equals(normalized(previous.getRemark()), normalized(values.remark())));
        boolean primaryChange = PROJECT_MANAGER.equals(role) && Boolean.TRUE.equals(values.primary())
                && !Objects.equals(project.getManagerId(), values.userId());
        if (samePerson && !primaryChange) {
            if (!detailsChanged) return result(project, previous, false);
            var fresh = appendManagerDetails(previous, values, command.reason(), now);
            incrementVersion(project);
            return result(project, fresh, true);
        }
        ProjectMemberAssignmentDO created;
            var duplicates = memberMapper.selectActiveMemberIdentityForUpdate(new ProjectMemberIdentityQuery(
                    actor.tenantId(), project.getId(), values.userId(), role, now));
            if (!samePerson && !duplicates.isEmpty()) throw exception(PROJECT_TEAM_MEMBER_DUPLICATE);
            Long primary = Boolean.TRUE.equals(values.primary()) || project.getManagerId() == null
                    || previous != null && Objects.equals(previous.getUserId(), project.getManagerId())
                    ? values.userId() : null;
            var changed = projectManagers.update(new ProjectManagerMemberCommand(project.getId(), project.getVersion(),
                    samePerson ? Set.of() : Set.of(values.userId()),
                    previous != null && !samePerson ? Set.of(previous.getUserId()) : Set.of(), primary,
                    command.reason(), command.idempotencyKey()), new ProjectManagerMemberApplicationService.Actor(
                    actor.tenantId(), actor.userId(), actor.correlationId()));
            project.setVersion(changed.version());
            if (samePerson) {
                created = appendManagerDetails(previous, values, command.reason(), now);
                if (!changed.changed()) incrementVersion(project);
                return result(project, created, true);
            }
            if (previous != null) recordEndReason(previous.getId(), command.reason());
            Long assignmentId = changed.members().stream().filter(member -> Objects.equals(member.userId(), values.userId()))
                    .map(ProjectManagerMemberResult.Member::assignmentId).findFirst().orElseThrow(() -> invalid("经理关系未生成"));
            created = memberMapper.selectById(assignmentId);
        if (created == null) throw invalid("经理关系未生成");
        var details = new ProjectMemberAssignmentDO();
        details.setId(created.getId());
        details.setResponsibility(values.responsibility() == null ? "" : values.responsibility().trim());
        details.setRemark(values.remark() == null ? "" : values.remark().trim());
        if (memberMapper.updateById(details) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        return result(project, created, true);
    }

    private ProjectMemberAssignmentDO appendManagerDetails(ProjectMemberAssignmentDO previous,
            MemberValues values, String reason, LocalDateTime now) {
        close(previous, reason, now);
        var fresh = new ProjectMemberAssignmentDO();
        BeanUtils.copyProperties(previous, fresh);
        fresh.setId(null);
        fresh.setCreator(null); fresh.setCreateTime(null); fresh.setUpdater(null); fresh.setUpdateTime(null);
        fresh.setEffectiveFrom(now); fresh.setEffectiveTo(null); fresh.setEndReason(null); fresh.setVersion(0);
        fresh.setResponsibility(normalized(values.responsibility())); fresh.setRemark(normalized(values.remark()));
        fresh.setChangeReason(reason.trim());
        if (memberMapper.insert(fresh) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        return fresh;
    }

    private void recordEndReason(Long assignmentId, String reason) {
        var details = new ProjectMemberAssignmentDO();
        details.setId(assignmentId); details.setEndReason(reason.trim());
        if (memberMapper.updateById(details) != 1) throw exception(PROJECT_VERSION_CONFLICT);
    }

    private void refreshAssignmentStatus(ProjectMasterDO project, LocalDateTime now) {
        var active = memberMapper.selectActiveForAssignmentState(new ProjectAssignmentStateQuery(project.getId(), now));
        boolean manager = active.stream().anyMatch(member -> PROJECT_MANAGER.equals(member.getMemberRole()));
        boolean service = active.stream().anyMatch(member -> SERVICE_MANAGER.equals(logicalRole(member.getMemberRole()))
                && (member.getAssignmentType() == null || "PRIMARY".equals(member.getAssignmentType())));
        String status = manager && service ? "ASSIGNED" : "UNASSIGNED";
        if (projectMapper.updateAssignmentStatusIfVersion(new ProjectAssignmentStatusUpdate(project.getId(), project.getVersion(), status)) != 1)
            throw exception(PROJECT_VERSION_CONFLICT);
        project.setAssignmentStatus(status);
    }

    private void close(ProjectMemberAssignmentDO member, String reason, LocalDateTime now) {
        var close = new ProjectMemberAssignmentDO();
        close.setId(member.getId());
        close.setEffectiveTo(now);
        close.setEndReason(reason.trim());
        close.setVersion(member.getVersion() == null ? 1 : member.getVersion() + 1);
        if (memberMapper.updateById(close) != 1) throw exception(PROJECT_VERSION_CONFLICT);
    }

    private void incrementVersion(ProjectMasterDO project) {
        if (projectMapper.incrementVersionIfMatch(project.getId(), project.getVersion()) != 1)
            throw exception(PROJECT_VERSION_CONFLICT);
        project.setVersion(project.getVersion() + 1);
    }

    private void requireWritePermission(Actor actor, String role) {
        if (role == null || !ORDINARY_ROLES.contains(role) && !managerRole(role)) throw exception(PROJECT_MEMBER_ROLE_INVALID);
        if (!permissions.hasAnyPermissions(actor.userId(), managerRole(role) ? "pms:project:assign" : WRITE_PERMISSION))
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
    }
    private static void validateActor(Actor actor) {
        if (actor == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), actor.tenantId())
                || actor.userId() == null || actor.userId() <= 0 || actor.correlationId() == null
                || actor.correlationId().isBlank()) throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
    }
    private static void validateCommand(Command command) {
        if (command == null || command.projectId() == null || command.projectId() <= 0 || command.action() == null
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128
                || command.reason().length() > 500
                || command.action() == Action.ADD && command.assignmentId() != null
                || command.action() != Action.ADD && (command.assignmentId() == null || command.assignmentId() <= 0))
            throw invalid("成员操作参数无效");
        if (command.action() != Action.REMOVE) {
            MemberValues values = command.member();
            if (values == null || values.userId() == null || values.userId() <= 0
                    || values.memberRole() == null || !ORDINARY_ROLES.contains(values.memberRole()) && !managerRole(values.memberRole()))
                throw exception(PROJECT_MEMBER_ROLE_INVALID);
            if (values.responsibility() != null && values.responsibility().length() > 500
                    || values.remark() != null && values.remark().length() > 500) throw invalid("职责和备注不能超过500字");
        }
    }
    private static boolean current(ProjectMemberAssignmentDO member, LocalDateTime now) {
        return "ACTIVE".equals(member.getStatus()) && (member.getEffectiveFrom() == null || !member.getEffectiveFrom().isAfter(now))
                && (member.getEffectiveTo() == null || member.getEffectiveTo().isAfter(now));
    }
    private static Result result(ProjectMasterDO project, ProjectMemberAssignmentDO member, boolean changed) {
        return new Result(project.getId(), project.getVersion(), member.getId(), member.getUserId(),
                member.getMemberRole(), member.getEffectiveFrom(), member.getEffectiveTo(), changed);
    }
    private static ProjectManualCreationService.ProjectAccessActor accessActor(Actor actor) {
        return new ProjectManualCreationService.ProjectAccessActor(actor.tenantId(), actor.userId());
    }
    private static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static RuntimeException invalid(String reason) { return exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, reason); }

    public enum Action { ADD, UPDATE, REMOVE }
    public record Actor(Long tenantId, Long userId, String correlationId) { }
    public record Filter(int pageNo, int pageSize, String state, String role, String keyword) { }
    public record CandidateFilter(int pageNo, int pageSize, String keyword, Long userId, String projectRole, ServiceScope scope) { }
    public record ServiceScope(String levelCode, String assignmentType, Long siteId, Long departmentId, String departmentCode) { }
    public record MemberValues(Long userId, String memberRole, String responsibility, String remark, Boolean primary, ServiceScope scope) {
        public MemberValues(Long userId, String memberRole, String responsibility, String remark) {
            this(userId, memberRole, responsibility, remark, false, null);
        }
    }
    public record Command(Long projectId, Integer expectedVersion, Action action, Long assignmentId,
                          MemberValues member, String reason, String idempotencyKey, Long replacementPrimaryUserId) {
        public Command { reason = reason == null ? "" : reason.trim(); }
        public Command(Long projectId, Integer version, Action action, Long assignmentId, MemberValues member, String reason, String key) {
            this(projectId, version, action, assignmentId, member, reason, key, null);
        }
    }
    public record Result(Long projectId, Integer version, Long assignmentId, Long userId, String memberRole,
                         LocalDateTime effectiveFrom, LocalDateTime effectiveTo, boolean changed) { }
    private record Audit(Long previousAssignmentId, MemberValues member, String reason, Result result) { }
}
