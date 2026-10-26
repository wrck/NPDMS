package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectShipmentFact;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShipmentPageContextQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectShipmentQueryService {
    private final DeviceMapper mapper;
    private final ProjectScopeApi projects;

    /** Only enrich the authorized page, never expose context from other projects of the same SN. */
    public Map<Long, List<ProjectShipmentFact>> forPage(ProjectShipmentPageContextQuery query) {
        if (query.projectId() == null || query.deviceIds() == null || query.deviceIds().isEmpty()
                || !Objects.equals(TenantContextHolder.getTenantId(), query.tenantId())) {
            return Map.of();
        }
        try {
            var scope = projects.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(),
                    SecurityFrameworkUtils.getLoginUserId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId())) return Map.of();
        } catch (RuntimeException denied) {
            return Map.of();
        }
        return mapper.selectProjectShipmentContext(query).stream()
                .collect(Collectors.groupingBy(ProjectShipmentFact::deviceId));
    }
}
