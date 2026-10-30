package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectTemplateInitializationService;
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
@RequestMapping("/api/v1/pms/projects/{projectId}/plan/initialize")
@PreAuthorize("@ss.hasPermission('pms:project-plan:manage')")
public class ProjectTemplateInitializationController {
    private final ProjectTemplateInitializationService service;
    public record Initialize(@NotNull @PositiveOrZero Long expectedProjectVersion,
                             @NotNull @Positive Long expectedTemplateRevisionId) { }

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectTemplateInitializationService.Initialized> initialize(
            @PathVariable @Positive Long projectId, @Valid @RequestBody Initialize body,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.initialize(new ProjectTemplateInitializationService.Initialize(projectId,
                body.expectedProjectVersion(), body.expectedTemplateRevisionId()), SecurityFrameworkUtils.getLoginUserId(), key));
    }
}
