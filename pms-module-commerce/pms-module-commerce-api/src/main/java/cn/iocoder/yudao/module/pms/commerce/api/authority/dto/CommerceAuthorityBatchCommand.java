package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceAuthorityBatchCommand(Long tenantId, String eventId, String batchId,
                                            String sourceSystem, String sourceWatermark,
                                            List<CommerceContractFact> contracts,
                                            List<CommerceSalesOrderFact> salesOrders,
                                            List<CommerceOrderLineFact> orderLines,
                                            List<CommerceOrderContractRelationFact> orderContractRelations,
                                            LocalDateTime occurredAt, String correlationId) {

    public CommerceAuthorityBatchCommand {
        if (tenantId == null || tenantId < 0) {
            throw invalid("tenantId must be non-negative");
        }
        eventId = text(eventId, 128, "eventId");
        batchId = text(batchId, 128, "batchId");
        sourceSystem = text(sourceSystem, 32, "sourceSystem");
        sourceWatermark = text(sourceWatermark, 128, "sourceWatermark");
        contracts = facts(contracts, CommerceContractFact::sourceKey, "contracts");
        salesOrders = facts(salesOrders, CommerceSalesOrderFact::sourceKey, "salesOrders");
        orderLines = facts(orderLines, CommerceOrderLineFact::sourceKey, "orderLines");
        orderContractRelations = relations(orderContractRelations);
        if (contracts.isEmpty() && salesOrders.isEmpty() && orderLines.isEmpty()
                && orderContractRelations.isEmpty()) {
            throw invalid("batch must contain at least one fact");
        }
        occurredAt = time(occurredAt, "occurredAt");
        correlationId = text(correlationId, 128, "correlationId");
    }

    private static <T> List<T> facts(List<T> values, Function<T, String> keyFunction, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw invalid(field + " must be a complete list");
        }
        List<T> stable = values.stream().sorted(Comparator.comparing(keyFunction)).toList();
        Set<String> keys = new HashSet<>();
        for (T value : stable) {
            if (!keys.add(keyFunction.apply(value))) {
                throw invalid("duplicate sourceKey in " + field);
            }
        }
        return stable;
    }

    private static List<CommerceOrderContractRelationFact> relations(
            List<CommerceOrderContractRelationFact> values) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw invalid("orderContractRelations must be a complete list");
        }
        Comparator<CommerceOrderContractRelationFact> comparator = Comparator
                .comparing(CommerceOrderContractRelationFact::salesOrderSourceKey)
                .thenComparing(CommerceOrderContractRelationFact::contractSourceKey);
        List<CommerceOrderContractRelationFact> stable = values.stream().sorted(comparator).toList();
        Set<String> keys = new HashSet<>();
        for (CommerceOrderContractRelationFact value : stable) {
            String key = value.salesOrderSourceKey() + "\u0000" + value.contractSourceKey();
            if (!keys.add(key)) {
                throw invalid("duplicate orderContractRelation source identity");
            }
        }
        return stable;
    }
}
