package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationProjectSitesQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** 安装工作台使用的项目当前安装站点，不授予全局站点访问权限。 */
@Service
@RequiredArgsConstructor
public class InstallationProjectSitesService {
    private final InstallationMapper installations;
    private final AssetLocationApi locations;
    private final CustomerQueryApi customers;
    private final ProjectScopeApi scopes;

    public record Result(List<SiteRespDTO> sites, List<AddressRespDTO> addresses,
                         List<SiteLocationRespDTO> siteLocations) { }

    private void authorize(Long tenant, Long actor, Long projectId) {
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(tenant).subjectUserId(actor)
                .anchorProjectId(projectId).actionCode(ProjectScopeApi.ACTION_EDIT).build());
        if (projectId == null || actor == null || scope == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
    }

    public Result read(Long projectId, Long siteId) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        authorize(tenant, actor, projectId);
        List<SiteRespDTO> sites = currentSites(tenant, projectId);
        if (siteId != null && sites.stream().noneMatch(site -> Objects.equals(site.id(), siteId))) throw exception(FORBIDDEN);
        Map<Long, AddressRespDTO> addresses = new LinkedHashMap<>();
        for (var site : sites) if (site.addressId() != null)
            addresses.computeIfAbsent(site.addressId(), id -> locations.getAddress(id, null));
        var tree = siteId == null ? List.<SiteLocationRespDTO>of() : locations.getLocationTree(siteId);
        if (tree.stream().anyMatch(node -> !Objects.equals(node.siteId(), siteId))) throw exception(FORBIDDEN);
        return new Result(sites, List.copyOf(addresses.values()), tree);
    }

    public void validateExistingSite(Long projectId, LocationMaintenanceCommand command) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        Long siteId = command.site().id();
        authorize(tenant, actor, projectId);
        if (currentSites(tenant, projectId).stream().anyMatch(site -> Objects.equals(site.id(), siteId))) {
            var site = command.site();
            var location = command.siteLocation();
            if (command.address() != null || site.code() != null || site.name() != null || site.customerId() != null
                    || site.siteType() != null || location != null && location.id() != null
                    && (location.parentId() != null || location.code() != null || location.name() != null
                    || location.locationType() != null || location.treeSort() != null)) throw exception(FORBIDDEN);
            return;
        }
        var context = customers.getProjectCustomer(new CustomerQueryApi.ProjectQuery(tenant, actor, projectId));
        if (context != null && context.customer() != null && locations.getCustomerSites(
                new CustomerSiteQuery(tenant, context.customer().id())).stream().anyMatch(site ->
                Objects.equals(site.id(), siteId) && Objects.equals(site.status(), CommonStatusEnum.ENABLE.getStatus())
                        && Objects.equals(site.customerId(), context.customer().id()))) return;
        throw exception(FORBIDDEN);
    }

    private List<SiteRespDTO> currentSites(Long tenant, Long projectId) {
        var ids = installations.selectCurrentProjectSiteIds(new InstallationProjectSitesQuery(tenant, projectId));
        if (ids.isEmpty()) return List.of();
        return locations.getEnabledSites(new EnabledSitesQuery(tenant, ids));
    }
}
