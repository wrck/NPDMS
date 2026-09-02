package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 受控本地来源批次；ERP认证、HTTP、调度和游标不属于该契约。 */
public record CommerceAuthorityWriteCommand(
        Long tenantId,
        String sourceBatchId,
        String operationId,
        List<ContractSourceRecord> contracts,
        List<SalesOrderSourceRecord> salesOrders,
        List<SalesOrderLineSourceRecord> salesOrderLines) {

    public record ContractSourceRecord(
            String sourceSystem, String sourceRecordKey, String sourceVersion,
            String companyCode, String contractNo, String contractName,
            String status, LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderSourceRecord(
            String sourceSystem, String sourceRecordKey, String sourceVersion,
            String companyCode, String orderType, String orderNo,
            String status, LocalDateTime sourceUpdatedAt) {
    }

    public record SalesOrderLineSourceRecord(
            String sourceSystem, String sourceRecordKey, String sourceVersion,
            String orderSourceRecordKey, String lineNo, String itemCode, String itemDescription,
            BigDecimal orderQuantity, BigDecimal openQuantity, BigDecimal deliveredQuantity,
            String unitCode, Integer unitScale, String quantityStatus,
            String status, LocalDateTime sourceUpdatedAt) {
    }
}
