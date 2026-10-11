package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanDraftService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanReworkActivationService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectReworkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/pms/projects/{projectId}/plan/draft/{draftId}/rework-apply")
@PreAuthorize("@ss.hasPermission('pms:project-plan:manage') && @ss.hasPermission('pms:project-plan:rework')")
public class ProjectPlanReworkController {
    private final ProjectPlanReworkActivationService service;
    public record Apply(@NotNull ProjectPlanDraftService.Preview expectedPreview,
                        @NotNull @Valid ProjectReworkController.Apply rework) { }

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanReworkActivationService.Applied> apply(
            @PathVariable("projectId") @Positive Long projectId, @PathVariable("draftId") @Positive Long draftId,
            @Valid @RequestBody Apply body, @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        var rework = body.rework();
        return success(service.apply(new ProjectPlanReworkActivationService.Apply(projectId, draftId, body.expectedPreview(),
                new ProjectReworkService.Apply(projectId, rework.planVersionId(), rework.expectedProjectVersion(), rework.selectedNodeKeys(),
                        rework.expectedExecutions().stream().map(item -> new ProjectReworkService.ExpectedExecution(
                                item.nodeKey(), item.executionId(), item.version())).toList(), rework.reason())),
                SecurityFrameworkUtils.getLoginUserId(), key));
    }
}
