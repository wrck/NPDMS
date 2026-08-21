package cn.iocoder.yudao.module.pms.project.controller.admin.v1.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplateCandidateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplateCandidateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplatePreviewRespVO;
import cn.iocoder.yudao.module.pms.project.service.template.ProjectTemplateCandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PM-03 项目模板查询")
@RestController
@RequestMapping("/api/v1/pms/project-templates")
@Validated
public class ProjectTemplateQueryController {

    private final ProjectTemplateCandidateService candidateService;

    public ProjectTemplateQueryController(ProjectTemplateCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    @Operation(summary = "查询当前创建条件适用的已发布模板")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectTemplateCandidateRespVO> getCandidates(@Valid ProjectTemplateCandidateReqVO request) {
        return success(ProjectTemplateCandidateRespVO.from(candidateService.findCandidates(
                TenantContextHolder.getRequiredTenantId(), requiredActorId(), request.toCriteria())));
    }

    @GetMapping("/{revisionId}")
    @Operation(summary = "预览当前创建条件适用的已发布模板")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectTemplatePreviewRespVO> getPreview(@PathVariable("revisionId") Long revisionId,
                                                                 @Valid ProjectTemplateCandidateReqVO request) {
        return success(ProjectTemplatePreviewRespVO.from(candidateService.getPreview(
                TenantContextHolder.getRequiredTenantId(), requiredActorId(), revisionId, request.toCriteria())));
    }

    private long requiredActorId() {
        return Objects.requireNonNull(SecurityFrameworkUtils.getLoginUserId(), "login user required");
    }
}
