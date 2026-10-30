package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Read-only source identity; runtime still executes the plan's frozen snapshot. */
@Component
@RequiredArgsConstructor
public class ProjectPlanTemplateBindingReader {
    private final ProjectTemplateRevisionMapper revisions;
    private final ProjectTemplateMapper templates;

    public record Binding(Long templateId, Long revisionId, Integer revisionNo, String name) { }

    public Binding read(Long revisionId) {
        if (revisionId == null) return null;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var revision = revisions.selectById(revisionId);
        if (revision == null || !Objects.equals(tenantId, revision.getTenantId())) return null;
        var template = templates.selectById(revision.getTemplateId());
        if (template == null || !Objects.equals(tenantId, template.getTenantId())) return null;
        return new Binding(template.getId(), revision.getId(), revision.getRevisionNo(), template.getName());
    }
}
