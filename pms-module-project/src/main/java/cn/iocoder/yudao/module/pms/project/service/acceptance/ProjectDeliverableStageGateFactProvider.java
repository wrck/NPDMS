package cn.iocoder.yudao.module.pms.project.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.AccProjectDeliverableGateFactQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProjectDeliverableStageGateFactProvider implements ProjectStageGateFactProviderApi {

    private final AccProjectDeliverableMapper mapper;

    @Override
    public Set<String> providerKeys() {
        return Set.of(PROVIDER_ACC_DELIVERABLE);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query) {
        validate(query);
        AccProjectDeliverableDO row = mapper.selectGateFactForUpdate(new AccProjectDeliverableGateFactQuery(
                query.tenantId(), query.projectId(), query.refCode()));
        if (row == null) {
            return new ProjectStageGateFact(PROVIDER_ACC_DELIVERABLE, query.refType(), query.refCode(),
                    "UNKNOWN", "UNKNOWN", ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE,
                    "DELIVERABLE_NOT_FOUND");
        }
        boolean satisfied = "ACCEPTED".equals(row.getStatus());
        return new ProjectStageGateFact(PROVIDER_ACC_DELIVERABLE, query.refType(),
                String.valueOf(row.getId()), value(row.getStatus()), value(row.getVersion()),
                satisfied ? ProjectStageGateOutcome.SATISFIED : ProjectStageGateOutcome.UNSATISFIED,
                satisfied ? null : "DELIVERABLE_NOT_ACCEPTED");
    }

    private static void validate(ProjectStageGateFactQuery query) {
        Long trustedTenantId = TenantContextHolder.getRequiredTenantId();
        if (query == null || !Objects.equals(query.tenantId(), trustedTenantId)
                || query.projectId() == null || query.projectId() <= 0
                || !"DELIVERABLE".equals(query.refType())
                || query.refCode() == null || query.refCode().isBlank()) {
            throw new IllegalArgumentException("invalid deliverable stage gate query");
        }
    }

    private static String value(Object value) {
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }
}
