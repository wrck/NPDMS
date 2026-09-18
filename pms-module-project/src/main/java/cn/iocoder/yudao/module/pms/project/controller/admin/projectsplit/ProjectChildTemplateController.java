package cn.iocoder.yudao.module.pms.project.controller.admin.projectsplit;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectChildDraftFactory;
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
    private final ProjectChildDraftFactory childDrafts;

    @GetMapping
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<PageResult<ProjectTemplateSelectionService.Option>> options(
            @RequestParam Long parentProjectId, @Valid ProjectTemplatePageReqVO page,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String businessLevelCode,
            @RequestParam(required = false) String departmentCode) {
        try {
            var actor = new ProjectSplitDraftService.Actor(TenantContextHolder.getRequiredTenantId(),
                    SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString());
            var parent = drafts.requireTemplateSelectionParent(parentProjectId, actor);
            var item = new ProjectSplitItemDO();
            item.setProjectName(projectName); item.setBusinessLevelCode(businessLevelCode); item.setDepartmentCode(departmentCode);
            return CommonResult.success(templates.options(childDrafts.create(parent, item), page, actor.actorId()));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException
                 | org.springframework.security.access.AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            // Global unexpected-error logging persists request bodies; matching facts must not enter it.
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_MATCH_PREVIEW_FAILED);
        }
    }
}
