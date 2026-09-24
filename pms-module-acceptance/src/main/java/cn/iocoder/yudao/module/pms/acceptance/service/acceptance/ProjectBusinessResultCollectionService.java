package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Mirrors the document collector: the durable outbox delivery owns retries; this handler never grants a business write. */
@Service @RequiredArgsConstructor
public class ProjectBusinessResultCollectionService {
    private final ProjectDeliverableRuleApi rules;
    private final AccProjectDeliverableMapper deliverables;
    private final ProjectDeliverableSubmissionService submissions;

    @EventListener @Transactional(rollbackFor = Exception.class)
    public void onBusinessResultChange(BusinessResultChange change) {
        if (!Objects.equals(change.channel().tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_EVENT_TENANT_MISMATCH");
        if (!change.formation()) return;
        var type = change.channel().type();
        var projectId = change.channel().projectId();
        var sourceCode = type.ownerContext() + "." + type.entityType() + "." + type.resultType();
        var codes = rules.documentTargets(projectId, sourceCode);
        for (var row : deliverables.selectDocuments(new AccProjectDeliverableMapper.DocumentScope(
                change.channel().tenantId(), projectId, codes)))
            submissions.collectBusinessResult(row, sourceCode, change);
    }
}
