package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.ProjectSplitDraftService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pms/project-child-template-options")
@RequiredArgsConstructor
public class ProjectChildTemplateController {
    private final ProjectSplitDraftService drafts;
    private final ProjectTemplateSelectionService templates;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<PageResult<ProjectTemplateSelectionService.Option>> options(
            @RequestParam Long parentProjectId, @Valid ProjectTemplatePageReqVO page) {
        var actor = new ProjectSplitDraftService.Actor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString());
        return CommonResult.success(templates.options(
                drafts.requireTemplateSelectionParent(parentProjectId, actor), page, actor.actorId()));
    }
}
