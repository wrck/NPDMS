package cn.iocoder.yudao.module.pms.project.controller.admin.taskbusiness;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.BusinessObjectFact;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@RestController
@RequestMapping("/api/v1/pms/project-tasks/{taskId}/business")
@Validated
@RequiredArgsConstructor
public class ProjectTaskBusinessController {
    private final ProjectTaskBusinessService service;
    private final Environment environment;

    @GetMapping("/context")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<TaskBusinessContext> context(@PathVariable Long taskId) {
        return trustedTenant(() -> success(service.getContext(taskId, tenant(), actor(), correlation())));
    }

    @GetMapping("/candidates")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<List<BusinessObjectFact>> candidates(@PathVariable Long taskId) {
        return trustedTenant(() -> success(service.getCandidates(taskId, tenant(), actor(), correlation())));
    }

    @PostMapping("/links")
    @PreAuthorize("@ss.hasPermission('pms:project-task:update')")
    public CommonResult<LinkCommandResult> link(@PathVariable Long taskId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody LinkRequest request) {
        requireVersion(ifMatch, request.expectedContractVersion());
        return trustedTenant(() -> success(service.link(new LinkCommand(taskId, request.objectId(),
                request.expectedTaskVersion(), request.expectedContractVersion(), key), tenant(), actor(), correlation())));
    }

    @PostMapping("/links/{linkId}/actions/unlink")
    @PreAuthorize("@ss.hasPermission('pms:project-task:update')")
    public CommonResult<LinkCommandResult> unlink(@PathVariable Long taskId, @PathVariable Long linkId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody UnlinkRequest request) {
        requireVersion(ifMatch, request.expectedContractVersion());
        return trustedTenant(() -> success(service.unlink(new UnlinkCommand(taskId, linkId,
                request.expectedTaskVersion(), request.expectedContractVersion(), key), tenant(), actor(), correlation())));
    }

    public record LinkRequest(@NotBlank @Size(max = 128) String objectId,
            @NotNull @Min(0) Integer expectedTaskVersion, @NotNull @Min(1) Integer expectedContractVersion) {}
    public record UnlinkRequest(@NotNull @Min(0) Integer expectedTaskVersion,
                                @NotNull @Min(1) Integer expectedContractVersion) {}

    private void requireVersion(String value, Integer expected) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1)
            normalized = normalized.substring(1, normalized.length() - 1);
        try {
            if (!normalized.matches("[0-9]+") || !Objects.equals(Integer.valueOf(normalized), expected))
                throw exception(PROJECT_TASK_VERSION_CONFLICT);
        } catch (NumberFormatException ex) { throw exception(PROJECT_TASK_COMMAND_INVALID); }
    }
    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private Long actor() { return SecurityFrameworkUtils.getLoginUserId(); }
    private String correlation() { return UUID.randomUUID().toString(); }

    private <T> T trustedTenant(Supplier<T> action) {
        // In single-tenant deployment 0 is the configured platform tenant, never a client fallback.
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
