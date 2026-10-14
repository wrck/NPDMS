package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ProjectCustomerContactMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.ProjectContactRowQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectContactReferenceApiImpl implements ProjectContactReferenceApi {
    private final ProjectCustomerContactMapper contacts;
    private final cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ProjectContactChoiceMapper choices;
    private final ProjectScopeApi scopes;

    @Override public List<Reference> list(Query query) {
        scope(query, false);
        return choices.enabled(new cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ProjectContactChoiceMapper.ProjectContactChoiceQuery(query.tenantId(), query.projectId())).stream().map(ProjectContactReferenceApiImpl::reference).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Reference lockAndInspect(Revalidation query) {
        scope(new Query(query.tenantId(), query.actorId(), query.projectId()), true);
        var row = contacts.selectForUpdate(new ProjectContactRowQuery(query.tenantId(), query.projectId(), query.contactId()));
        if (row == null || !Integer.valueOf(0).equals(row.getStatus()) || !Objects.equals(row.getVersion(), query.expectedVersion())) {
            throw new IllegalArgumentException("项目联系记录已变更、停用或删除，请重新选择");
        }
        return reference(row);
    }

    private void scope(Query query, boolean lock) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()) || query.actorId() == null || query.projectId() == null) {
            throw new SecurityException("项目联系人查询身份不匹配");
        }
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw new SecurityException("项目联系人不可见");
        if (lock) {
            scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW)
                .expectedScopeVersion(scope.treeVersion()).build());
            if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw new SecurityException("项目联系人权限已变化");
        }
    }

    private static Reference reference(ProjectCustomerContactDO row) {
        return new Reference(row.getId(), row.getVersion(), row.getProjectId(), row.getCustomerContactId(), row.getName(), row.getMobile(),
                row.getPhone(), row.getDepartment(), row.getTitle());
    }
}
