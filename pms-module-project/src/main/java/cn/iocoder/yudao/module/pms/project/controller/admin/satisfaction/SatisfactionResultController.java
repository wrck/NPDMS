package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.export.ExportTaskFact;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResultExportApplicationService;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionResultManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-results")
@Validated
@RequiredArgsConstructor
public class SatisfactionResultController {
    private final SatisfactionResultManagementService resultService;
    private final SatisfactionResultExportApplicationService exportService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<List<SatisfactionResultManagementService.ResultView>> list(
            @RequestParam(required = false) Long projectId) {
        return success(resultService.list(tenantId(), actorId(), projectId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:query')")
    public CommonResult<SatisfactionResultManagementService.ResultView> get(@PathVariable("id") Long resultId) {
        return success(resultService.get(tenantId(), actorId(), resultId));
    }

    @GetMapping("/{id}/files/{sequence}/download")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:download')")
    public CommonResult<SatisfactionResultManagementService.DownloadFact> download(
            @PathVariable("id") Long resultId, @PathVariable @Positive Integer sequence) {
        return success(resultService.download(tenantId(), actorId(), resultId, sequence));
    }

    @PostMapping("/{id}/actions/invalidate")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionResultManagementService.InvalidationResult> invalidate(
            @PathVariable("id") Long resultId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody InvalidateReqVO request) {
        return success(resultService.invalidate(tenantId(), actorId(), resultId, request.expectedResultVersion,
                request.reasonCode, request.reasonSummary, operationId));
    }

    @PostMapping("/exports")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:export')")
    public CommonResult<ExportTaskFact> export(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @Valid @RequestBody ExportReqVO request) {
        return success(exportService.request(tenantId(), actorId(), operationId, request.projectId,
                request.fields, request.includeFiles));
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
    private Long actorId() { return SecurityFrameworkUtils.getLoginUserId(); }

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
