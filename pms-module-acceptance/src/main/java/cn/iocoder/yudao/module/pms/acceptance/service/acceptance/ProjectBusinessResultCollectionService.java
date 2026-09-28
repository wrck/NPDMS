package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;

/** 提交台账的成果归集入口：仅形成的成果进入交付件证据槽；守卫与幂等在 collectBusinessResult。 */
@Service @RequiredArgsConstructor
public class ProjectBusinessResultCollectionService {
    private final ProjectDeliverableRuleApi rules;
    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableSubmissionService submissions;

    @EventListener @Transactional(rollbackFor = Exception.class)
    public void onBusinessResultChange(BusinessResultChange change) {
        if (!Objects.equals(change.channel().tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_EVENT_TENANT_MISMATCH");
        if (!change.formation()) return;
        var type = change.channel().type();
        var projectId = change.channel().projectId();
        var sourceCode = type.ownerContext() + "." + type.entityType() + "." + type.resultType();
        var codes = new HashSet<>(rules.documentTargets(projectId, sourceCode));
        for (var view : platform.listByProject(projectId))
            if (codes.contains(view.deliverableCode()))
                submissions.collectBusinessResult(view, sourceCode, change);
    }
}
