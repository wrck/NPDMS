package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectReworkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/pms/projects/{projectId}/rework")
@PreAuthorize("@ss.hasPermission('pms:project-plan:rework')")
public class ProjectReworkController {
    private final ProjectReworkService service;
    public record Selection(@NotEmpty List<@NotBlank String> selectedNodeKeys) { }
    public record Expected(@NotBlank String nodeKey, @NotNull @Positive Long executionId,
                           @NotNull @PositiveOrZero Integer version) { }
    public record Apply(@NotNull @Positive Long planVersionId, @NotNull @PositiveOrZero Integer expectedProjectVersion,
                        @NotEmpty List<@NotBlank String> selectedNodeKeys, @NotEmpty List<@NotNull @Valid Expected> expectedExecutions,
                        @NotBlank @Size(max = 500) String reason) { }

    @GetMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectReworkService.State> get(@PathVariable("projectId") Long projectId) {
        return success(service.get(projectId, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectReworkService.Preview> preview(@PathVariable("projectId") Long projectId,
                                                             @Valid @RequestBody Selection body) {
        return success(service.preview(projectId, body.selectedNodeKeys(), SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ProjectReworkService.Result> apply(@PathVariable("projectId") Long projectId,
            @Valid @RequestBody Apply body, @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key) {
        return success(service.apply(new ProjectReworkService.Apply(projectId, body.planVersionId(), body.expectedProjectVersion(),
                body.selectedNodeKeys(), body.expectedExecutions().stream().map(item ->
                        new ProjectReworkService.ExpectedExecution(item.nodeKey(), item.executionId(), item.version())).toList(), body.reason()),
                SecurityFrameworkUtils.getLoginUserId(), key));
    }
}
