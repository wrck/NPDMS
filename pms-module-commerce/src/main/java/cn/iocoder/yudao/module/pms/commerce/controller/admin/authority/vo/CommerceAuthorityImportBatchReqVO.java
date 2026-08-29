package cn.iocoder.yudao.module.pms.commerce.controller.admin.authority.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CommerceAuthorityImportBatchReqVO(
        @NotBlank @Size(max = 128) String sourceBatchId,
        List<@NotNull @Valid ContractRecord> contracts,
        List<@NotNull @Valid SalesOrderRecord> salesOrders,
        List<@NotNull @Valid SalesOrderLineRecord> salesOrderLines) {

    @AssertTrue(message = "受控导入批次至少包含一条来源记录")
    public boolean isNonEmptyBatch() {
        return contracts != null && !contracts.isEmpty()
                || salesOrders != null && !salesOrders.isEmpty()
                || salesOrderLines != null && !salesOrderLines.isEmpty();
    }

    public record ContractRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @NotBlank @Size(max = 128) String sourceVersion,
            @NotBlank @Size(max = 64) String companyCode,
            @NotBlank @Size(max = 128) String contractNo,
            @Size(max = 255) String contractName,
            @NotBlank @Size(max = 32) String status,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @NotBlank @Size(max = 128) String sourceVersion,
            @NotBlank @Size(max = 64) String companyCode,
            @NotBlank @Size(max = 32) String orderType,
            @NotBlank @Size(max = 128) String orderNo,
            @NotBlank @Size(max = 32) String status,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderLineRecord(
            @NotBlank @Size(max = 128) String sourceRecordKey,
            @NotBlank @Size(max = 128) String sourceVersion,
            @NotBlank @Size(max = 128) String orderSourceRecordKey,
            @NotBlank @Size(max = 64) String lineNo,
            @NotBlank @Size(max = 128) String itemCode,
            @Size(max = 500) String itemDescription,
            @Size(max = 64) String productCode,
            @PositiveOrZero BigDecimal orderQuantity,
            @PositiveOrZero BigDecimal openQuantity,
            @PositiveOrZero BigDecimal deliveredQuantity,
            @NotBlank @Size(max = 16) String unitCode,
            @NotNull @Min(0) @Max(6) Integer unitScale,
            @NotBlank @Size(max = 32) String quantityStatus,
            @NotBlank @Size(max = 32) String status,
            @NotNull LocalDateTime sourceUpdatedAt) {
    }
}
