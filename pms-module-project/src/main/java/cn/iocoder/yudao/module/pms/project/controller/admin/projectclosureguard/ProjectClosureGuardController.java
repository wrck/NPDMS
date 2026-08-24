package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosureguard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardResult;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/pms/closure-gates")
@RequiredArgsConstructor
@Validated
public class ProjectClosureGuardController {
    private final ProjectClosureGuardService guardService;

    @GetMapping("/{projectId}")
    @Operation(summary = "查询项目全部后代闭环守卫")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectClosureGuardResult> evaluate(
            @PathVariable Long projectId,
            @RequestParam("treeVersion") @Positive long treeVersion) {
        return success(guardService.evaluate(projectId, treeVersion,
                new ProjectClosureGuardService.Actor(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString())));
    }
}
