package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CommerceAuthorityImportBatchReqVO(
        @NotBlank @Size(max = 128) String batchId,
        @NotBlank @Size(max = 128) String sourceWatermark,
        @NotNull LocalDateTime occurredAt,
        List<@NotNull @Valid ContractRecord> contracts,
        List<@NotNull @Valid SalesOrderRecord> salesOrders,
        List<@NotNull @Valid SalesOrderLineRecord> salesOrderLines,
        List<@NotNull @Valid OrderContractRelationRecord> orderContractRelations) {

    @AssertTrue(message = "受控导入批次至少包含一条来源记录")
    public boolean isNonEmptyBatch() {
        return nonEmpty(contracts) || nonEmpty(salesOrders) || nonEmpty(salesOrderLines)
                || nonEmpty(orderContractRelations);
    }

    private static boolean nonEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    public record ContractRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @Size(max = 64) String expectedPreviousSourceVersion,
            @NotBlank @Size(max = 64) String sourceVersion,
            @NotBlank @Size(max = 64) String companyCode,
            @NotBlank @Size(max = 64) String contractNo,
            @Size(max = 512) String contractName,
            @Size(max = 64) String customerCode,
            @Size(max = 512) String customerName,
            @PositiveOrZero BigDecimal amount,
            @Size(max = 32) String currencyCode,
            @NotNull CommerceSourceLifecycleStatus lifecycleStatus,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @Size(max = 64) String expectedPreviousSourceVersion,
            @NotBlank @Size(max = 64) String sourceVersion,
            @NotBlank @Size(max = 64) String companyCode,
            @NotBlank @Size(max = 64) String orderNo,
            @NotBlank @Size(max = 32) String orderType,
            @Size(max = 64) String customerCode,
            @Size(max = 512) String customerName,
            @PositiveOrZero BigDecimal amount,
            @Size(max = 32) String currencyCode,
            @NotNull CommerceSourceLifecycleStatus lifecycleStatus,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderLineRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @Size(max = 64) String expectedPreviousSourceVersion,
            @NotBlank @Size(max = 64) String sourceVersion,
            @NotBlank @Size(max = 128) String orderSourceRecordKey,
            @NotBlank @Size(max = 32) String lineNo,
            @Size(max = 64) String itemCode,
            @Size(max = 512) String itemDescription,
            @Size(max = 64) String productCode,
            @Size(max = 64) String modelCode,
            @PositiveOrZero BigDecimal orderQuantity,
            @PositiveOrZero BigDecimal openQuantity,
            @PositiveOrZero BigDecimal deliveredQuantity,
            @NotBlank @Size(max = 32) String unitCode,
            @NotNull @Min(0) @Max(6) Integer unitScale,
            @NotBlank @Size(max = 32) String quantityStatus,
            @NotNull CommerceSourceLifecycleStatus lifecycleStatus,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }

    public record OrderContractRelationRecord(
            @NotBlank @Size(max = 128) String salesOrderSourceKey,
            @NotBlank @Size(max = 128) String contractSourceKey,
            @Size(max = 64) String expectedPreviousSourceVersion,
            @NotBlank @Size(max = 64) String sourceVersion,
            @NotNull LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo) {
    }
}
