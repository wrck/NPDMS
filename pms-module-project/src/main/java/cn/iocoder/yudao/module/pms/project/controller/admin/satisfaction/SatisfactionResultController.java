package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskFact;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResultExportApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResultManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-results")
@Validated
@RequiredArgsConstructor
public class SatisfactionResultController {
    private final SatisfactionResultManagementService resultService;
    private final SatisfactionResultExportApplicationService exportService;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<List<SatisfactionResultManagementService.ResultView>> list(
            @RequestParam(required = false) Long projectId) {
        return withTenant(() -> success(resultService.list(tenantId(), actorId(), projectId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<SatisfactionResultManagementService.ResultView> get(@PathVariable("id") Long resultId) {
        return withTenant(() -> success(resultService.get(tenantId(), actorId(), resultId)));
    }

    @GetMapping("/{id}/files/{sequence}/download")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:download')")
    public CommonResult<SatisfactionResultManagementService.DownloadFact> download(
            @PathVariable("id") Long resultId, @PathVariable @Positive Integer sequence) {
        return withTenant(() -> success(resultService.download(tenantId(), actorId(), resultId, sequence)));
    }

    @PostMapping("/{id}/actions/invalidate")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionResultManagementService.InvalidationResult> invalidate(
            @PathVariable("id") Long resultId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody InvalidateReqVO request) {
        return withTenant(() -> success(resultService.invalidate(tenantId(), actorId(), resultId,
                request.expectedResultVersion, request.reasonCode, request.reasonSummary, operationId)));
    }

    @PostMapping("/exports")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:export')")
    public CommonResult<ExportTaskFact> export(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody ExportReqVO request) {
        return withTenant(() -> success(exportService.request(tenantId(), actorId(), operationId, request.projectId,
                request.fields, request.includeFiles)));
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
    private Long actorId() { return SecurityFrameworkUtils.getLoginUserId(); }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }

    @Data
    public static class InvalidateReqVO {
        @NotNull @PositiveOrZero private Integer expectedResultVersion;
        @NotBlank @Size(max = 64) private String reasonCode;
        @Size(max = 1000) private String reasonSummary;
    }

    @Data
    public static class ExportReqVO {
        @NotNull @Positive private Long projectId;
        @NotEmpty private List<@NotBlank String> fields;
        private boolean includeFiles;
    }
}
