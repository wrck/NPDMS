package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class InstallationAddressRequirementPolicy {
    public static final String CONFIG_KEY = "pms.installation.address.product.filters";
    private final ConfigApi configs;
    private final DeviceQueryApi devices;

    public record Decision(Set<Long> exemptDeviceIds, String version) {
        public Decision { exemptDeviceIds = Set.copyOf(exemptDeviceIds); }
    }

    public Decision inspect(ProjectDeviceInstallationQuery context) {
        if (context == null || context.tenantId() == null || !context.tenantId().equals(TenantContextHolder.getTenantId())
                || context.projectId() == null || context.projectId() <= 0) throw new IllegalArgumentException("INSTALLATION_FILTER_SCOPE_REQUIRED");
        var filters = InstallationAddressFilterConfig.parse(configs.getConfigValueByKey(CONFIG_KEY));
        if (filters.productFilters().isEmpty()) return new Decision(Set.of(), "");
        var ids = devices.findProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(
                context.tenantId(), context.projectId(), filters.productFilters(),
                cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterMatchMode.NOT_MATCHING));
        return new Decision(Set.copyOf(ids), cn.hutool.crypto.digest.DigestUtil.sha256Hex(filters.version() + ":" + ids));
    }
}
