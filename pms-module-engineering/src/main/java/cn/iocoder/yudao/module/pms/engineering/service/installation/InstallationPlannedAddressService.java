package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness.ImplementationReadinessSourceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness.ReadinessProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Read-only suggestions from the same final-approved solution selection used by implementation readiness. */
@Service @RequiredArgsConstructor
public class InstallationPlannedAddressService {
    private final ImplementationReadinessSourceMapper sources;
    private final SolutionTieredReviewService reviews;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    public record Address(String productCode, String location) { }
    public record Plan(Long solutionId, Long solutionVersion, String solutionName, List<Address> addresses) { }

    public Plan read(Long projectId) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (projectId == null || projectId <= 0 || actor == null
                || !permissions.hasAnyPermissions(actor, "pms:imp-installation:query")
                || !permissions.hasAnyPermissions(actor, "pms:sol-solution:query")) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(tenant).subjectUserId(actor)
                .anchorProjectId(projectId).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        var plan = sources.approvedSolutions(new ReadinessProjectQuery(tenant, projectId, false)).stream()
                .filter(row -> Integer.valueOf(0).equals(row.getReviewLevel())
                        || Integer.valueOf(1).equals(row.getReviewLevel()) && reviews.approved(row, false))
                .findFirst().orElse(null);
        if (plan == null) return null;
        if (plan.getTopology() == null || plan.getTopology().isBlank())
            return new Plan(plan.getId(), plan.getVersion(), plan.getName(), List.of());
        try {
            var topology = JsonUtils.getObjectMapper().readTree(plan.getTopology());
            Set<Address> addresses = new LinkedHashSet<>();
            for (var row : topology.path("deploy")) {
                String code = row.path("productCode").asText().trim();
                String location = row.path("location").asText().trim();
                if (!location.isEmpty()) addresses.add(new Address(code, location));
            }
            return new Plan(plan.getId(), plan.getVersion(), plan.getName(), List.copyOf(addresses));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("批准方案部署位置格式无效，请核对来源方案", invalid);
        }
    }
}
