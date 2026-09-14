package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageBusinessQueryService;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.StageBusinessContext;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageApprovalCommandService;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/stages/{stageCode}/business")
public class ProjectStageBusinessController {
    private final ProjectStageBusinessQueryService service;
    private final ProjectStageApprovalCommandService approvals;

    public record StartRequest(@NotNull ProjectStageExecutionContext execution, ProjectNodeApprovalApi.Submission approval) { }

    @PostMapping("/approvals")
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectNodeApprovalApi.Fact> startApproval(@PathVariable @Positive Long projectId,
            @PathVariable @Size(min = 1, max = 32) String stageCode,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody StartRequest request) {
        return success(approvals.start(new ProjectStageApprovalCommandService.Command(projectId,stageCode,request.execution(),request.approval()),
                SecurityFrameworkUtils.getLoginUserId(),key));
    }

    @GetMapping("/context")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<StageBusinessContext> context(@PathVariable @Positive Long projectId,
            @PathVariable @Size(min = 1, max = 32) String stageCode) {
        return success(service.getContext(projectId, stageCode, new ProjectAccessActor(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())));
    }
}
