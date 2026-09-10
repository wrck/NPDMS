package cn.iocoder.yudao.module.pms.project.controller.admin.normalclosure;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureErrors.failure;

/** Approval decisions are exclusively platform BPM operations, never controller CRUD. */
@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects/{projectId}/normal-closure")
public class NormalClosureController {
    private final NormalClosureQueryService query;
    private final NormalClosureApplicationService application;
    private final Environment environment;
    public record CheckRequest(@NotNull @Min(0) Integer expectedProjectVersion, @NotNull @Positive Long expectedTreeVersion) {}
    public record SubmitRequest(@NotNull @Positive Long snapshotId, @NotNull @Min(0) Integer expectedProjectVersion,
                                @NotNull @Positive Long expectedTreeVersion) {}

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:query')")
    public CommonResult<NormalClosureViews.Overview> get(@PathVariable Long projectId) {
        return trusted(() -> success(query.get(projectId, actor())));
    }
    @PostMapping("/actions/check")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:submit')")
    public CommonResult<NormalClosureSnapshotDO> check(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @RequestBody @Valid CheckRequest body) {
        return trusted(() -> success(application.check(new NormalClosureApplicationService.CheckCommand(projectId,
                body.expectedProjectVersion(), body.expectedTreeVersion(), key), actor())));
    }
    @PostMapping("/actions/submit")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:submit')")
    public CommonResult<NormalClosureApplicationDO> submit(@PathVariable Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @RequestBody @Valid SubmitRequest body) {
        return trusted(() -> success(application.submit(new NormalClosureApplicationService.SubmitCommand(projectId,
                body.snapshotId(), body.expectedProjectVersion(), body.expectedTreeVersion(), key), actor())));
    }
    @GetMapping("/applications/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:query')")
    public CommonResult<NormalClosureViews.ApplicationDetail> detail(@PathVariable Long projectId, @PathVariable Long id) {
        return trusted(() -> success(query.application(projectId, id, actor())));
    }
    private NormalClosureAccess.Actor actor() {
        return new NormalClosureAccess.Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }
    private <T> T trusted(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw failure("CLOSURE_TENANT_REQUIRED");
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
