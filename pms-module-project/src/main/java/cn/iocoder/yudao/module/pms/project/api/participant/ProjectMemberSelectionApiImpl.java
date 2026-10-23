package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectMemberSelectionApiImpl implements ProjectMemberSelectionApi {
    private final ProjectMemberAssignmentMapper members;
    private final ProjectScopeApi scopes;
    private final AdminUserApi users;

    @Override public Page page(Query query) {
        scope(query, false);
        if (query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100) throw new IllegalArgumentException("成员分页条件无效");
        PageParam page = new PageParam(); page.setPageNo(query.pageNo()); page.setPageSize(query.pageSize());
        var rows = members.selectMemberPage(ProjectMemberPageQuery.builder().tenantId(query.tenantId())
                .projectId(query.projectId()).page(page).state("CURRENT").role(query.role()).effectiveAt(LocalDateTime.now()).build());
        var userIds = rows.getList().stream().filter(row -> !"END_USER".equals(row.getMemberRole()))
                .map(ProjectMemberAssignmentDO::getUserId).filter(Objects::nonNull).distinct().toList();
        var people = userIds.isEmpty() ? java.util.Map.<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(userIds);
        return new Page(rows.getList().stream().map(row -> fact(row, row.getUserId() == null ? null : people.get(row.getUserId()))).toList(), rows.getTotal());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Member lockAndInspect(Revalidation query) {
        scope(Query.builder().tenantId(query.tenantId()).actorId(query.actorId()).projectId(query.projectId())
                .pageNo(1).pageSize(1).role(query.role()).build(), true);
        var row = members.selectMemberSelectionForUpdate(new ProjectMemberSelectionQuery(query.tenantId(), query.projectId(), query.assignmentId(), LocalDateTime.now()));
        if (row == null || query.role() != null && !ProjectMemberRoles.storedCodes(query.role()).contains(row.getMemberRole())
                || query.expectedVersion() == null || !Objects.equals(row.getVersion().longValue(), query.expectedVersion()))
            throw new IllegalArgumentException("项目成员已变更或失效，请重新选择");
        return fact(row, row.getUserId() == null ? null : users.getUser(row.getUserId()));
    }

    private void scope(Query query, boolean lock) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()) || query.actorId() == null || query.projectId() == null)
            throw new SecurityException("成员选择身份无效");
        if (query.role() != null && !ProjectMemberRoles.ALL_CODES.contains(query.role()))
            throw new IllegalArgumentException("项目成员角色筛选无效");
        var current = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(query.tenantId())
                .subjectUserId(query.actorId()).anchorProjectId(query.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (current == null || !current.fullProjectIds().contains(query.projectId())) throw new SecurityException("项目成员不可见");
        if (lock) {
            var locked = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder().tenantId(query.tenantId())
                    .subjectUserId(query.actorId()).anchorProjectId(query.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW)
                    .expectedScopeVersion(current.treeVersion()).build());
            if (locked == null || !locked.fullProjectIds().contains(query.projectId())) throw new SecurityException("项目成员权限已变化");
        }
    }

    private Member fact(ProjectMemberAssignmentDO row, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO person) {
        String phone = "END_USER".equals(row.getMemberRole()) ? row.getExternalMobile() : person == null ? null : person.getMobile();
        return new Member(row.getId(), row.getVersion().longValue(), row.getMemberName(), phone);
    }
}
