package cn.iocoder.yudao.module.pms.project.controller.admin.projectmember;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectManagerMemberApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectManagerMemberCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectManagerMemberResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;

/** PM-01：经理成员操作；不替代原服务经理端点。 */
@RestController
@RequestMapping("/api/v1/pms/projects")
@Validated
@RequiredArgsConstructor
public class ProjectManagerMemberController {
    private final ProjectManagerMemberApplicationService service;

    @PostMapping("/{id}/actions/update-project-managers")
    @PreAuthorize("@ss.hasPermission('pms:project:assign')")
    public CommonResult<ProjectManagerMemberResult> update(@PathVariable("id") Long projectId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody Request request) {
        String version = ifMatch.trim();
        if (version.startsWith("W/")) version = version.substring(2).trim();
        if (version.startsWith("\"") && version.endsWith("\"") && version.length() >= 2)
            version = version.substring(1, version.length() - 1);
        int expected;
        try { expected = Integer.parseInt(version); }
        catch (NumberFormatException ex) { throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "If-Match版本无效"); }
        return CommonResult.success(service.update(new ProjectManagerMemberCommand(projectId, expected,
                request.addUserIds(), request.removeUserIds(), request.primaryUserId(), request.reason(), key),
                new ProjectManagerMemberApplicationService.Actor(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString())));
    }

    public record Request(Set<@NotNull @Positive Long> addUserIds,
                          Set<@NotNull @Positive Long> removeUserIds,
                          @Positive Long primaryUserId, @NotBlank @Size(max = 500) String reason) { }
}
