package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityWriteApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 旧受控导入契约的兼容适配；所有Owner写入统一委托批次CAS入口。 */
@Deprecated(since = "2026.09")
@Service
@RequiredArgsConstructor
public class CommerceAuthorityWriteService implements CommerceAuthorityWriteApi {
    private final CommerceAuthorityIngestApi ingestApi;
    private final ContractMapper contractMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderLineMapper lineMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthorityWriteResult apply(CommerceAuthorityWriteCommand command) {
        if (command == null || command.tenantId() == null || blank(command.sourceBatchId())
                || blank(command.operationId())) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_INVALID_ARGUMENT");
        }
        String sourceSystem = sourceSystem(command);
        List<CommerceContractFact> contracts = new ArrayList<>();
        for (CommerceAuthorityWriteCommand.ContractSourceRecord source : safe(command.contracts())) {
            ContractDO current = contractMapper.selectBySourceForUpdate(new AuthoritySourceLockQuery(
                    command.tenantId(), sourceSystem, source.sourceRecordKey()));
            contracts.add(new CommerceContractFact(source.sourceRecordKey(),
                    current == null ? null : current.getMasterSourceVersion(), source.sourceVersion(),
                    source.companyCode(), source.contractNo(), source.contractName(), null, null, null, null,
                    lifecycle(source.status()), source.sourceUpdatedAt()));
        }
        List<CommerceSalesOrderFact> orders = new ArrayList<>();
        for (CommerceAuthorityWriteCommand.SalesOrderSourceRecord source : safe(command.salesOrders())) {
            SalesOrderDO current = orderMapper.selectBySourceForUpdate(new AuthoritySourceLockQuery(
                    command.tenantId(), sourceSystem, source.sourceRecordKey()));
            orders.add(new CommerceSalesOrderFact(source.sourceRecordKey(),
                    current == null ? null : current.getSourceVersion(), source.sourceVersion(),
                    source.companyCode(), source.orderNo(), source.orderType(), null, null, null, null,
                    lifecycle(source.status()), source.sourceUpdatedAt()));
        }
        List<CommerceOrderLineFact> lines = new ArrayList<>();
        for (CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord source : safe(command.salesOrderLines())) {
            SalesOrderLineDO current = lineMapper.selectBySourceForUpdate(new AuthoritySourceLockQuery(
                    command.tenantId(), sourceSystem, source.sourceRecordKey()));
            lines.add(new CommerceOrderLineFact(source.sourceRecordKey(),
                    current == null ? null : current.getSourceVersion(), source.sourceVersion(),
                    source.orderSourceRecordKey(), source.lineNo(), source.itemCode(), source.itemDescription(),
                    source.productCode(), null, source.orderQuantity(), source.openQuantity(),
                    source.deliveredQuantity(), source.unitCode(), source.unitScale(), source.quantityStatus(),
                    lifecycle(source.status()), source.sourceUpdatedAt()));
        }
        LocalDateTime occurredAt = occurredAt(command);
        CommerceAuthorityBatchResult result = ingestApi.ingestBatch(new CommerceAuthorityBatchCommand(
                command.tenantId(), command.operationId().trim(), command.sourceBatchId().trim(),
                sourceSystem, command.sourceBatchId().trim(), contracts, orders, lines, List.of(),
                occurredAt, command.operationId().trim()));
        boolean replayed = result.decision() != CommerceAuthorityBatchResult.Decision.ACCEPTED;
        return new AuthorityWriteResult(result.batchId(), replayed,
                contracts.size(), orders.size(), lines.size());
    }

    private String sourceSystem(CommerceAuthorityWriteCommand command) {
        Set<String> systems = new TreeSet<>();
        safe(command.contracts()).forEach(value -> systems.add(required(value.sourceSystem())));
        safe(command.salesOrders()).forEach(value -> systems.add(required(value.sourceSystem())));
        safe(command.salesOrderLines()).forEach(value -> systems.add(required(value.sourceSystem())));
        if (systems.size() != 1) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_REQUIRES_ONE_SOURCE_SYSTEM");
        }
        return systems.iterator().next();
    }

    private LocalDateTime occurredAt(CommerceAuthorityWriteCommand command) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(safe(command.contracts()).stream()
                                        .map(CommerceAuthorityWriteCommand.ContractSourceRecord::sourceUpdatedAt),
                                safe(command.salesOrders()).stream()
                                        .map(CommerceAuthorityWriteCommand.SalesOrderSourceRecord::sourceUpdatedAt)),
                        safe(command.salesOrderLines()).stream()
                                .map(CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord::sourceUpdatedAt))
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_REQUIRES_SOURCE_TIME"));
    }

    private CommerceSourceLifecycleStatus lifecycle(String value) {
        String normalized = required(value).toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "ENABLED" -> CommerceSourceLifecycleStatus.ACTIVE;
            case "CANCELLED", "CANCELED", "DISABLED" -> CommerceSourceLifecycleStatus.CANCELLED;
            case "RETURNED" -> CommerceSourceLifecycleStatus.RETURNED;
            default -> throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_LIFECYCLE_INVALID");
        };
    }

    private String required(String value) {
        if (blank(value)) {
            throw new IllegalArgumentException("COMMERCE_AUTHORITY_IMPORT_REQUIRED_FIELD");
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
