package cn.iocoder.yudao.module.pms.commerce.service.associated;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.associated.ProjectShipmentLogisticsMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectShipmentLogisticsService implements ProjectShipmentLogisticsApi {
    private final ProjectShipmentLogisticsMapper mapper;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;

    @Override public List<Fact> read(Query query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || query.userId() == null || query.userId() <= 0 || query.projectId() == null || query.projectId() <= 0
                || query.references() == null || query.references().size() > 100
                || query.references().stream().anyMatch(r -> r == null || r.sourceSystem() == null || r.sourceSystem().isBlank()
                    || r.packageNo() == null || r.packageNo().isBlank() || r.contractNo() == null || r.contractNo().isBlank()))
            throw new IllegalArgumentException("项目物流查询参数无效");
        if (!permissions.hasAnyPermissions(query.userId(), "pms:device:query"))
            throw new AccessDeniedException("缺少设备查询权限");
        var scope = projects.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.userId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(query.projectId()))
            throw new AccessDeniedException("无权查看项目物流信息");
        if (query.references().isEmpty()) return List.of();
        var result = mapper.selectFacts(query);
        var requested = new HashSet<>(query.references());
        var seen = new HashSet<Reference>();
        for (var fact : result)
            if (!requested.contains(fact.reference()) || !seen.add(fact.reference()))
                throw new IllegalStateException("项目物流引用不唯一或超出查询范围");
        return result;
    }
}
