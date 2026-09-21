package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class ProjectDocumentCollectionService {
    private final FileEvidenceApi files;
    private final ProjectDocumentSourceRegistry owners;
    private final AccProjectDeliverableMapper deliverables;
    private final ProjectDeliverableSubmissionService submissions;
    private final cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi rules;

    @EventListener @Transactional(rollbackFor = Exception.class)
    public void onReferenceChanged(FileReferenceChanged event) {
        if (!Objects.equals(event.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DOCUMENT_EVENT_TENANT_MISMATCH");
        var file = files.inspectDocument(event.tenantId(), event.referenceId());
        if (file == null) return;
        var scope = owners.resolve(event.tenantId(), file);
        if (scope == null || scope.projectId() == null) return;
        var codes = rules.documentTargets(scope.projectId(), scope.sourceCode());
        for (var row : deliverables.selectDocuments(new AccProjectDeliverableMapper.DocumentScope(event.tenantId(), scope.projectId(), codes)))
            submissions.collectDocument(row, scope, file, event.eventId());
    }
}
