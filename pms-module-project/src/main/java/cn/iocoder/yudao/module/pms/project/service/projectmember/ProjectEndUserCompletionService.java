package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 由已授权的项目任务/闭环命令读取，项目锁与成员维护使用同一串行化边界。 */
@Service
@RequiredArgsConstructor
public class ProjectEndUserCompletionService {
    private final ProjectAssignmentCompletionMapper members;

    public record Query(Long tenantId, Long projectId) { }
    public record MemberFact(Long assignmentId, Integer version) { }
    public record Fact(Long projectId, List<MemberFact> members) {
        public Fact { members = List.copyOf(members); }
        public boolean completed() { return !members.isEmpty(); }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Fact lockAndInspect(Query request) {
        if (request == null || request.tenantId() == null || request.projectId() == null || request.projectId() <= 0
                || TenantContextHolder.isIgnore() || !Objects.equals(request.tenantId(), TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("PROJECT_END_USER_CONTEXT_INVALID");
        var query = new ProjectAssignmentCompletionQuery(request.tenantId(), request.projectId(), true);
        var project = members.project(query);
        if (project == null || !Objects.equals(project.getId(), request.projectId())
                || !Objects.equals(project.getTenantId(), request.tenantId()))
            throw new IllegalArgumentException("PROJECT_END_USER_PROJECT_UNAVAILABLE");
        var now = LocalDateTime.now();
        var current = members.members(query).stream()
                .filter(member -> Objects.equals(member.getTenantId(), request.tenantId())
                        && Objects.equals(member.getProjectId(), request.projectId())
                        && !Boolean.TRUE.equals(member.getDeleted()) && "ACTIVE".equals(member.getStatus())
                        && ProjectMemberRoles.END_USER.equals(member.getMemberRole()) && member.getUserId() == null
                        && member.getMemberName() != null && !member.getMemberName().isBlank()
                        && (member.getEffectiveFrom() == null || !member.getEffectiveFrom().isAfter(now))
                        && (member.getEffectiveTo() == null || member.getEffectiveTo().isAfter(now)))
                .map(member -> new MemberFact(member.getId(), member.getVersion())).toList();
        return new Fact(request.projectId(), current);
    }
}
