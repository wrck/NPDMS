package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanTemplateBindingService;
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
@RequestMapping("/api/v1/pms/projects/{projectId}/plan/draft/{draftId}/template-binding")
@PreAuthorize("@ss.hasPermission('pms:project-plan:manage')")
public class ProjectPlanTemplateBindingController {
    private final ProjectPlanTemplateBindingService service;
    public record Import(@NotNull @PositiveOrZero Long expectedVersion,
                         @NotNull @Positive Long expectedBasePlanVersionId,
                         @NotNull @Positive Long targetTemplateRevisionId) { }

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectPlanTemplateBindingService.Imported> importPublished(
            @PathVariable("projectId") @Positive Long projectId, @PathVariable("draftId") @Positive Long draftId,
            @Valid @RequestBody Import body, @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.importPublished(new ProjectPlanTemplateBindingService.Import(projectId, draftId,
                body.expectedVersion(), body.expectedBasePlanVersionId(), body.targetTemplateRevisionId()),
                SecurityFrameworkUtils.getLoginUserId(), key));
    }
}
