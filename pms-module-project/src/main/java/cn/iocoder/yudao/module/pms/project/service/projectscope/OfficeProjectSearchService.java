package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.OfficeProjectSearchReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.OfficeProjectSearchQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class OfficeProjectSearchService {
    private final ProjectMasterMapper projects;
    private final ProjectOrganizationScopeService organizations;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    private final ProjectContractQueryApi contracts;

    public PageResult<ProjectMasterDO> search(OfficeProjectSearchReqVO request) {
        var query = query(new Lookup(null, request.getProjectName(), request.getContractNo()), request);
        if (query.getOrganizations().isEmpty()) return PageResult.empty();
        var result = projects.selectOfficeProjectPage(query);
        result.getList().forEach(project -> project.setVisibility("OFFICE_READONLY"));
        return result;
    }

    public record Lookup(Long projectId, String projectName, String contractNo) { }

    public OfficeProjectReadContext.Scope validate(Lookup lookup) {
        var request = new OfficeProjectSearchReqVO();
        request.setPageSize(1);
        var query = query(lookup, request);
        if (lookup.projectId() == null || lookup.projectId() <= 0 || query.getOrganizations().isEmpty()
                || projects.selectOfficeProjectPage(query).getList().isEmpty()) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return new OfficeProjectReadContext.Scope(query.getTenantId(), SecurityFrameworkUtils.getLoginUserId(), lookup.projectId());
    }

    private OfficeProjectSearchQuery query(Lookup lookup, OfficeProjectSearchReqVO page) {
        var tenant = TenantContextHolder.getTenantId();
        var user = SecurityFrameworkUtils.getLoginUserId();
        var name = present(lookup.projectName());
        var contract = present(lookup.contractNo());
        if (tenant == null || user == null || name == null && contract == null
                || name != null && name.length() > 512 || contract != null && contract.length() > 512
                || page.getPageSize() <= 0 || page.getPageSize() > 100) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        if (!permissions.hasAnyPermissions(user, "pms:project:query")) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var pm = organizations.rows(new ProjectOrganizationScopeService.Query(tenant, user, "PROJECT_VIEW", true, null));
        var sm = organizations.rows(new ProjectOrganizationScopeService.Query(tenant, user, "PROJECT_VIEW", false, null));
        var rows = Stream.concat(pm.stream(), sm.stream()).distinct().toList();
        var contractProjects = contract == null || rows.isEmpty() ? java.util.Set.<Long>of()
                : contracts.findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery(contract));
        return OfficeProjectSearchQuery.builder().tenantId(tenant)
                .organizations(rows).contractProjectIds(contractProjects)
                .exactProjectName(name).exactContractNo(contract).projectId(lookup.projectId()).pageParam(page).build();
    }

    private String present(String value) { return value == null || value.isBlank() ? null : value; }
}
