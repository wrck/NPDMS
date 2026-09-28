package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileReferenceChanged;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;

/** 提交台账的文档归集入口：按来源编码圈定候选要求，逐个走统一承接（collectDocument 自带守卫与幂等）。 */
@Service @RequiredArgsConstructor
public class ProjectDocumentCollectionService {
    private final FileEvidenceApi files;
    private final ProjectDocumentSourceRegistry owners;
    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableSubmissionService submissions;
    private final ProjectDeliverableRuleApi rules;

    @EventListener @Transactional(rollbackFor = Exception.class)
    public void onReferenceChanged(FileReferenceChanged event) {
        if (!Objects.equals(event.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DOCUMENT_EVENT_TENANT_MISMATCH");
        var file = files.inspectDocument(event.tenantId(), event.referenceId());
        if (file == null) return;
        var scope = owners.resolve(event.tenantId(), file);
        if (scope == null || scope.projectId() == null) return;
        var codes = new HashSet<>(rules.documentTargets(scope.projectId(), scope.sourceCode()));
        for (var view : platform.listByProject(scope.projectId()))
            if (codes.contains(view.deliverableCode()))
                submissions.collectDocument(view, scope, file, event.eventId());
    }
}
