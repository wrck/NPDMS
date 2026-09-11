package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageBusinessQueryService;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.StageBusinessContext;
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

    @GetMapping("/context")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<StageBusinessContext> context(@PathVariable @Positive Long projectId,
            @PathVariable @Size(min = 1, max = 32) String stageCode) {
        return success(service.getContext(projectId, stageCode, new ProjectAccessActor(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())));
    }
}
