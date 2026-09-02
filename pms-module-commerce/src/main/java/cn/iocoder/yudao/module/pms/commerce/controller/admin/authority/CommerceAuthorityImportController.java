package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo.CommerceAuthorityImportBatchReqVO;
import cn.iocoder.yudao.module.pms.commerce.service.authority.CommerceAuthorityImportApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/pms/commerce-authority/import-batches")
@Validated
@RequiredArgsConstructor
public class CommerceAuthorityImportController {

    private final CommerceAuthorityImportApplicationService applicationService;
    private final Environment environment;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:commerce:authority:write')")
    public CommonResult<CommerceAuthorityBatchResult> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String operationId,
            @RequestHeader("X-Source-System") @NotBlank @Size(max = 32)
            @jakarta.validation.constraints.Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String sourceSystem,
            @RequestHeader("X-Correlation-Id") @NotBlank @Size(max = 128) String correlationId,
            @Valid @RequestBody CommerceAuthorityImportBatchReqVO request) {
        return withTenant(() -> {
            Long tenantId = currentTenantId();
            Long actorUserId = currentUserId();
            CommerceAuthorityBatchCommand command = toCommand(
                    tenantId, sourceSystem.trim(), operationId.trim(), correlationId.trim(), request);
            return success(applicationService.execute(command,
                    new CommerceAuthorityImportApplicationService.Actor(tenantId, actorUserId)));
        });
    }

    CommerceAuthorityBatchCommand toCommand(Long tenantId, String sourceSystem, String operationId,
                                             String correlationId, CommerceAuthorityImportBatchReqVO request) {
        List<CommerceContractFact> contracts = safe(request.contracts()).stream()
                .map(value -> new CommerceContractFact(value.sourceRecordKey(),
                        value.expectedPreviousSourceVersion(), value.sourceVersion(), value.companyCode(),
                        value.contractNo(), value.contractName(), value.customerCode(), value.customerName(),
                        value.amount(), value.currencyCode(), value.lifecycleStatus(), value.sourceUpdatedAt()))
                .toList();
        List<CommerceSalesOrderFact> orders = safe(request.salesOrders()).stream()
                .map(value -> new CommerceSalesOrderFact(value.sourceRecordKey(),
                        value.expectedPreviousSourceVersion(), value.sourceVersion(), value.companyCode(),
                        value.orderNo(), value.orderType(), value.customerCode(), value.customerName(),
                        value.amount(), value.currencyCode(), value.lifecycleStatus(), value.sourceUpdatedAt()))
                .toList();
        List<CommerceOrderLineFact> lines = safe(request.salesOrderLines()).stream()
                .map(value -> new CommerceOrderLineFact(value.sourceRecordKey(),
                        value.expectedPreviousSourceVersion(), value.sourceVersion(), value.orderSourceRecordKey(),
                        value.lineNo(), value.itemCode(), value.itemDescription(), value.productCode(),
                        value.modelCode(), value.orderQuantity(), value.openQuantity(), value.deliveredQuantity(),
                        value.unitCode(), value.unitScale(), value.quantityStatus(), value.lifecycleStatus(),
                        value.sourceUpdatedAt()))
                .toList();
        List<CommerceOrderContractRelationFact> relations = safe(request.orderContractRelations()).stream()
                .map(value -> new CommerceOrderContractRelationFact(value.salesOrderSourceKey(),
                        value.contractSourceKey(), value.expectedPreviousSourceVersion(), value.sourceVersion(),
                        value.effectiveFrom(), value.effectiveTo()))
                .toList();
        return new CommerceAuthorityBatchCommand(tenantId, operationId, request.batchId(), sourceSystem,
                request.sourceWatermark(), contracts, orders, lines, relations,
                request.occurredAt(), correlationId);
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

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
