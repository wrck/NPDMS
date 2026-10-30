package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectContractScopeApi.*;
import cn.iocoder.yudao.module.pms.commerce.service.scope.ProjectContractScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/project-contract-scopes/{projectId}/actions")
@Validated
@RequiredArgsConstructor
public class ProjectContractScopeController {
    private final ProjectContractScopeService service;
    private final Environment environment;

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:commerce:scope:query')")
    public CommonResult<ScopePreview> preview(@PathVariable @Positive Long projectId,
                                              @Valid @RequestBody PreviewRequest request) {
        return withTenant(() -> success(service.preview(context(projectId, request.expectedProjectVersion(),
                request.expectedTreeVersion(), request.expectedScopeVersion(), "preview:" + UUID.randomUUID()))));
    }

    @PostMapping("/synchronize")
    @PreAuthorize("@ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:commerce:scope:query')")
    public CommonResult<ScopeSynchronizationResult> synchronize(@PathVariable @Positive Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody SynchronizeRequest request) {
        return withTenant(() -> success(service.synchronize(context(projectId, request.expectedProjectVersion(),
                request.expectedTreeVersion(), request.expectedScopeVersion(), operationId), request.sourceDigest())));
    }

    private ScopeContext context(Long projectId, Long projectVersion, Long treeVersion, Long scopeVersion, String operationId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) throw exception(FORBIDDEN);
        return new ScopeContext(TenantContextHolder.getTenantId(), projectId, userId,
                projectVersion, treeVersion, scopeVersion, operationId);
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    public record PreviewRequest(@NotNull @Min(0) Long expectedProjectVersion,
                                 @NotNull @Min(0) Long expectedTreeVersion,
                                 @NotNull @Min(0) Long expectedScopeVersion) { }
    public record SynchronizeRequest(@NotNull @Min(0) Long expectedProjectVersion,
                                     @NotNull @Min(0) Long expectedTreeVersion,
                                     @NotNull @Min(0) Long expectedScopeVersion,
                                     @NotBlank @Size(max = 64) String sourceDigest) { }
}
