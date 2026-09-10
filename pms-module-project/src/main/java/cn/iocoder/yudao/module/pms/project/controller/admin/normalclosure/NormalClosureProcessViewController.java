package cn.iocoder.yudao.module.pms.project.controller.admin.normalclosure;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureAccess;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureQueryService;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureViews;
import jakarta.validation.constraints.Positive;
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

/** Read-only BPM business view. A workflow assignment never grants project access. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/normal-closure-applications")
public class NormalClosureProcessViewController {
    private final NormalClosureQueryService query;
    private final Environment environment;

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acc-project-closure:query')")
    public CommonResult<NormalClosureViews.ApplicationDetail> detail(@PathVariable @Positive Long id) {
        return trusted(() -> success(query.processViewByApplicationId(id, new NormalClosureAccess.Actor(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString()))));
    }

    private <T> T trusted(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw failure("CLOSURE_TENANT_REQUIRED");
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
