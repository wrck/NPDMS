package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSurveyPatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSurveyRespVO;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSurveyService;
import jakarta.validation.Valid;
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
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** PRE-02 / F-SOL-002 survey metadata, independent of item content permissions. */
@RestController
@RequestMapping("/api/v1/pms/preparations/{id}/survey")
@Validated
@RequiredArgsConstructor
public class PreparationSurveyController {
    private final PreparationSurveyService service;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermissions('pms:preparation-survey:query','pms:preparation-survey:manage')")
    public CommonResult<PreparationSurveyRespVO> get(@PathVariable("id") @Positive Long id) {
        return withTrustedTenant(() -> success(service.get(id, actor())));
    }

    @PatchMapping
    @PreAuthorize("@ss.hasPermission('pms:preparation-survey:manage')")
    public CommonResult<PreparationSurveyRespVO> patch(@PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody PreparationSurveyPatchReqVO request) {
        return withTrustedTenant(() -> success(service.patch(id, parseVersion(ifMatch), request, actor())));
    }

    private Integer parseVersion(String value) {
        try {
            int version = Integer.parseInt(value);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException failure) { throw exception(PREPARATION_VERSION_NOT_MATCH); }
    }

    private PreparationItemApplicationService.Actor actor() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return new PreparationItemApplicationService.Actor(TenantContextHolder.getRequiredTenantId(),
                actorId, UUID.randomUUID().toString());
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
