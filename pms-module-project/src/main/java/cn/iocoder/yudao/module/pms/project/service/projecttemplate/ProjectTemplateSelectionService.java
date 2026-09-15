package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_NOT_SELECTABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_OVERRIDE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_SELECTION_REASON_INVALID;

/** Selection validates a frozen revision; it never substitutes the parent's version or a later revision. */
@Service
@RequiredArgsConstructor
public class ProjectTemplateSelectionService {
    public static final String OVERRIDE_PERMISSION = "pms:project-template:override";
    private final ProjectTemplateService templates;
    private final PermissionApi permissions;

    public ProjectTemplateRevisionDO requireAvailable(Long revisionId, Long tenantId) {
        if (revisionId == null || revisionId <= 0) throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        var revision = templates.getRevisionById(revisionId);
        var template = revision == null ? null : templates.getProjectTemplate(revision.getTemplateId());
        if (revision == null || template == null || !Objects.equals(tenantId, revision.getTenantId())
                || !Objects.equals(tenantId, template.getTenantId())
                || !TemplateRules.STATUS_ACTIVE.equals(template.getStatus())
                || !TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus()))
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        templates.getExecutionSnapshot(revision.getTemplateId(), revision.getRevisionNo());
        return revision;
    }

    public Selection select(ProjectMasterDO source, Long revisionId, String reason, Long actorId) {
        var revision = requireAvailable(revisionId, source.getTenantId());
        boolean override = !recommended(source).contains(revisionId);
        if (override) {
            if (actorId == null || !permissions.hasAnyPermissions(actorId, OVERRIDE_PERMISSION))
                throw exception(PROJECT_TEMPLATE_OVERRIDE_FORBIDDEN);
            if (reason == null || reason.isBlank() || reason.length() > 512)
                throw exception(PROJECT_TEMPLATE_SELECTION_REASON_INVALID);
        }
        return new Selection(revision, override);
    }

    public PageResult<Option> options(ProjectMasterDO source, ProjectTemplatePageReqVO page, Long actorId) {
        page.setStatus(TemplateRules.STATUS_ACTIVE);
        var templatesPage = templates.getProjectTemplatePage(page);
        var recommended = recommended(source);
        boolean canOverride = permissions.hasAnyPermissions(actorId, OVERRIDE_PERMISSION);
        var options = templatesPage.getList().stream().map(template -> {
            var latest = templates.getRevisionList(template.getId()).stream()
                    .filter(revision -> TemplateRules.REVISION_STATUS_PUBLISHED.equals(revision.getStatus()))
                    .max(java.util.Comparator.comparing(ProjectTemplateRevisionDO::getRevisionNo)).orElse(null);
            if (latest == null) return new Option(template.getId(), template.getName(), null, null, false, false);
            boolean available;
            try { requireAvailable(latest.getId(), source.getTenantId()); available = true; }
            catch (RuntimeException unavailable) { available = false; }
            boolean matched = recommended.contains(latest.getId());
            return new Option(template.getId(), template.getName(), latest.getId(), latest.getRevisionNo(),
                    matched, available && (matched || canOverride));
        }).toList();
        return new PageResult<>(options, templatesPage.getTotal());
    }

    private Set<Long> recommended(ProjectMasterDO source) {
        var result = templates.matchPreview(ProjectRuleFields.manualCreationFacts(source));
        return result.getCandidates().stream().map(candidate -> candidate.getTemplateRevisionId()).collect(Collectors.toSet());
    }

    public record Selection(ProjectTemplateRevisionDO revision, boolean override) { }
    public record Option(Long templateId, String name, Long revisionId, Integer revisionNo,
                         boolean recommended, boolean selectable) { }
}
