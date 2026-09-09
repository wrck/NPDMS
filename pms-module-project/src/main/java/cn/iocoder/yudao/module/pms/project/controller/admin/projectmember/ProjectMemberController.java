package cn.iocoder.yudao.module.pms.project.controller.admin.projectmember;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmember.*;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserRespDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;

/** PM-01：联合/分次指派及受项目权限约束的项目经理候选。 */
@RestController
@RequestMapping("/api/v1/pms/projects")
@RequiredArgsConstructor
@Validated
public class ProjectMemberController {
    private final ProjectMemberUpdateApplicationService updates;
    private final ProjectManagerCandidateService candidates;

    @GetMapping("/{id}/project-managers")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectManagerMemberResult> current(@PathVariable("id") Long id) {
        return CommonResult.success(candidates.current(id, new ProjectManualCreationService.ProjectAccessActor(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())));
    }

    @GetMapping("/{id}/project-manager-candidates")
    @PreAuthorize("@ss.hasPermission('pms:project:assign')")
    public CommonResult<PageResult<CompanyRoleUserRespDTO>> candidates(@PathVariable("id") Long id,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return CommonResult.success(candidates.page(id, keyword, pageNo, pageSize,
                new ProjectManualCreationService.ProjectAccessActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId())));
    }

    @PostMapping("/{id}/actions/update-members")
    @PreAuthorize("@ss.hasPermission('pms:project:assign')")
    public CommonResult<ProjectMemberUpdateResult> update(@PathVariable("id") Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody Request request) {
        String value = ifMatch.trim();
        if (value.startsWith("W/")) value = value.substring(2).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        int version;
        try { version = Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "If-Match版本无效"); }
        var service = request.serviceManager();
        return CommonResult.success(updates.update(new ProjectMemberUpdateCommand(id, version,
                service == null ? null : new ProjectMemberUpdateCommand.ServiceManager(service.levelCode(), service.managerId(),
                        service.siteId(), service.assignmentType(), service.departmentId(), service.departmentCode()),
                request.addUserIds(), request.removeUserIds(), request.primaryUserId(), request.reason(), key),
                new ProjectManagerMemberApplicationService.Actor(TenantContextHolder.getRequiredTenantId(),
                        SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString())));
    }

    public record Request(@Valid ServiceManager serviceManager, Set<@NotNull @Positive Long> addUserIds,
            Set<@NotNull @Positive Long> removeUserIds, @Positive Long primaryUserId,
            @NotBlank @Size(max = 500) String reason) { }
    public record ServiceManager(@NotBlank @Pattern(regexp = "L1|L2") String levelCode,
            @NotNull @Positive Long managerId, @Positive Long siteId,
            @NotBlank @Pattern(regexp = "PRIMARY|COLLABORATOR") String assignmentType,
            @NotNull @Positive Long departmentId, @NotBlank String departmentCode) { }
}
