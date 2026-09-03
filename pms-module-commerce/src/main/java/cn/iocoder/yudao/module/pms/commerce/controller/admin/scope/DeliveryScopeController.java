package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.scope.vo.*;
import cn.iocoder.yudao.module.pms.commerce.service.scope.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/delivery-scopes")
@Validated
@RequiredArgsConstructor
public class DeliveryScopeController {

    private final CommerceDeliveryScopeQueryService queryService;
    private final CommerceDeliveryScopeCommandService commandService;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:query')")
    public CommonResult<PageResult<DeliveryScopeRespVO>> page(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long orderLineId,
            @RequestParam(defaultValue = "false") boolean includeHistory,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return withTenant(() -> {
            PageResult<DeliveryScopeView> page = queryService.page(currentTenantId(), currentUserId(), projectId,
                    orderLineId, includeHistory, (pageNo - 1) * pageSize, pageSize);
            return success(new PageResult<>(page.getList().stream().map(this::toResponse).toList(), page.getTotal()));
        });
    }

    @GetMapping("/projects/{projectId}/version")
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:query')")
    public CommonResult<Long> currentVersion(@PathVariable @Positive Long projectId) {
        return withTenant(() -> success(queryService.currentVersion(
                currentTenantId(), currentUserId(), projectId)));
    }

    @PostMapping("/actions/preview")
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:query')")
    public CommonResult<DeliveryScopePreviewResult> preview(@Valid @RequestBody DeliveryScopePreviewReqVO request) {
        return withTenant(() -> success(commandService.preview(new DeliveryScopePreviewCommand(currentTenantId(),
                currentUserId(), request.projectId(), request.expectedProjectVersion(),
                request.expectedProjectScopeVersion(), request.expectedDeliveryScopeVersion(),
                request.orderLineId(), request.expectedOrderLineSourceVersion(),
                request.proposedQuantity(), request.serialNumbers()))));
    }

    @PostMapping("/actions/assign")
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:assign')")
    public CommonResult<DeliveryScopeCommandResult> assign(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody DeliveryScopeAssignReqVO request) {
        return withTenant(() -> success(commandService.assign(new DeliveryScopeAssignCommand(currentTenantId(),
                currentUserId(), request.projectId(), parseVersion(ifMatch), request.expectedProjectScopeVersion(),
                request.expectedDeliveryScopeVersion(),
                request.orderLineId(), request.expectedOrderLineSourceVersion(), request.allocatedQuantity(),
                request.serialNumbers(), request.reason(), operationId))));
    }

    @PostMapping("/{id}/actions/adjust")
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:adjust')")
    public CommonResult<DeliveryScopeCommandResult> adjust(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody DeliveryScopeAdjustReqVO request) {
        return withTenant(() -> success(commandService.adjust(new DeliveryScopeChangeCommand(currentTenantId(),
                currentUserId(), id, request.projectId(), request.expectedProjectVersion(),
                request.expectedProjectScopeVersion(), request.expectedDeliveryScopeVersion(), parseLongVersion(ifMatch),
                request.expectedOrderLineSourceVersion(), request.proposedQuantity(), request.serialNumbers(),
                request.reason(), operationId))));
    }

    @PostMapping("/{id}/actions/release")
    @PreAuthorize("@ss.hasPermission('pms:commerce:scope:release')")
    public CommonResult<DeliveryScopeCommandResult> release(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody DeliveryScopeReleaseReqVO request) {
        return withTenant(() -> success(commandService.release(new DeliveryScopeChangeCommand(currentTenantId(),
                currentUserId(), id, request.projectId(), request.expectedProjectVersion(),
                request.expectedProjectScopeVersion(), request.expectedDeliveryScopeVersion(), parseLongVersion(ifMatch),
                request.expectedOrderLineSourceVersion(), BigDecimal.ZERO, java.util.List.of(),
                request.reason(), operationId))));
    }

    private DeliveryScopeRespVO toResponse(DeliveryScopeView view) {
        var scope = view.scope();
        var details = view.details().stream().map(detail -> new DeliveryScopeRespVO.Detail(detail.getId(),
                detail.getDetailSequence(), detail.getSerialNo(), detail.getProductCode(), detail.getDeviceTypeCode(),
                detail.getAllocatedQty(), detail.getDetailStatus())).toList();
        return new DeliveryScopeRespVO(scope.getId(), scope.getProjectId(), scope.getProjectCode(),
                scope.getOrderLineId(), scope.getOrderNo(), scope.getLineNo(), scope.getItemCode(),
                scope.getAllocatedQty(), scope.getScopeStatus(), scope.getAllocationVersion(),
                scope.getAllocationSource(), scope.getChangeReason(), scope.getOfficeDepartmentId(),
                scope.getOfficeDepartmentCode(), scope.getOfficeDepartmentName(), scope.getOfficeDepartmentVersion(),
                scope.getEffectiveFrom(), scope.getEffectiveTo(), scope.getVersion(), details);
    }

    private Integer parseVersion(String value) {
        long version = parseLongVersion(value);
        if (version > Integer.MAX_VALUE) {
            throw exception(BAD_REQUEST, "If-Match版本超出范围");
        }
        return (int) version;
    }

    private Long parseLongVersion(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long version = Long.parseLong(normalized);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException exception) {
            throw exception(BAD_REQUEST, "If-Match必须是非负版本");
        }
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) throw exception(FORBIDDEN);
        return userId;
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
