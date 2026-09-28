package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableRequirementResolver.accStatus;

/**
 * 统一交付件门禁事实（P06R I2）：交付件身份在平台 plt_delivery_requirement（projectId+type_code），
 * 事实值 = 收敛后的要求状态/版本，满足性来自统一承接的重验结果（材料失效撤回、判定证据回填台账）。
 */
@Component
@RequiredArgsConstructor
public class ProjectDeliverableStageGateFactProvider implements ProjectStageGateFactProviderApi {

    private final ProjectDeliverableRuleApi rules;
    private final PlatformDeliveryRequirementApi platform;
    private final ObjectProvider<ProjectDeliverableSubmissionService> submissions;

    @Override
    public Set<String> providerKeys() {
        return Set.of(PROVIDER_ACC_DELIVERABLE);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query) {
        validate(query);
        rules.lock(query.projectId(), query.refCode());
        var view = platform.lockByIdentity(query.projectId(), query.refCode());
        if (view.isEmpty()) {
            return new ProjectStageGateFact(PROVIDER_ACC_DELIVERABLE, query.refType(), query.refCode(),
                    "UNKNOWN", "UNKNOWN", ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE,
                    "DELIVERABLE_NOT_FOUND");
        }
        var evaluation = submissions.getObject().revalidate(view.get());
        var current = platform.findById(view.get().id()).orElse(view.get());
        return new ProjectStageGateFact(PROVIDER_ACC_DELIVERABLE, query.refType(),
                String.valueOf(view.get().id()), accStatus(current.status()), value(current.version()),
                evaluation.satisfied() ? ProjectStageGateOutcome.SATISFIED : ProjectStageGateOutcome.UNSATISFIED,
                evaluation.satisfied() ? null : evaluation.reason());
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
