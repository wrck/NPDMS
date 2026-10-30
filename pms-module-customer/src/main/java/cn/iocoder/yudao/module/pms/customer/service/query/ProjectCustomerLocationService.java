package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.customer.service.location.CustomerLocationReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Project-scoped read composition; global customer/location management grants remain unchanged. */
@Service
@RequiredArgsConstructor
public class ProjectCustomerLocationService {
    private final ProjectCustomerOverviewService overview;
    private final CustomerLocationReferenceService references;
    private final AssetLocationApi locations;

    public record Query(Long tenantId, Long actorUserId, Long projectId, Long siteId) { }
    public record Result(Long customerId, String customerName, List<SiteRespDTO> sites,
                         List<AddressRespDTO> addresses, List<AddressRespDTO> customerAddresses,
                         List<SiteLocationRespDTO> siteLocations) { }

    public Result get(Query query) {
        var customer = overview.get(new ProjectCustomerOverviewService.Query(
                query.tenantId(), query.actorUserId(), query.projectId()));
        if (customer == null) {
            if (query.siteId() != null) throw exception(FORBIDDEN);
            return new Result(null, null, List.of(), List.of(), List.of(), List.of());
        }
        var sites = locations.getCustomerSites(new CustomerSiteQuery(query.tenantId(), customer.getId()));
        if (sites.stream().anyMatch(site -> !Objects.equals(site.customerId(), customer.getId())
                || !Objects.equals(site.status(), 0))) throw exception(FORBIDDEN);
        if (query.siteId() != null && sites.stream().noneMatch(site -> Objects.equals(site.id(), query.siteId()))) {
            throw exception(FORBIDDEN);
        }
        Map<Long, AddressRespDTO> addresses = new LinkedHashMap<>();
        for (var site : sites) {
            if (site.addressId() != null) addresses.computeIfAbsent(site.addressId(), id -> locations.getAddress(id, null));
        }
        List<AddressRespDTO> customerAddresses = new ArrayList<>();
        for (var reference : references.listCurrent(query.tenantId(), customer.getId())) {
            if (!Objects.equals(reference.getTenantId(), query.tenantId())
                    || !Objects.equals(reference.getCustomerId(), customer.getId())) throw exception(FORBIDDEN);
            if (!"ADDRESS".equals(reference.getLocationType())) continue;
            var address = addresses.computeIfAbsent(reference.getLocationId(), id -> locations.getAddress(id, null));
            if (Objects.equals(address.status(), 0)) customerAddresses.add(address);
        }
        var tree = query.siteId() == null ? List.<SiteLocationRespDTO>of() : locations.getLocationTree(query.siteId());
        if (tree.stream().anyMatch(node -> !Objects.equals(node.siteId(), query.siteId()))) throw exception(FORBIDDEN);
        return new Result(customer.getId(), customer.getName(), sites, List.copyOf(addresses.values()),
                List.copyOf(customerAddresses), tree);
    }
}
