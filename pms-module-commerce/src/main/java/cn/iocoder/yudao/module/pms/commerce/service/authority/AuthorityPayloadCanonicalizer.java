package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** ERP权威事实的稳定载荷表示；expectedPreviousSourceVersion不属于Owner载荷。 */
@Component
public class AuthorityPayloadCanonicalizer {

    public String batchDigest(CommerceAuthorityBatchCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenantId", command.tenantId());
        value.put("eventId", command.eventId());
        value.put("batchId", command.batchId());
        value.put("sourceSystem", command.sourceSystem());
        value.put("sourceWatermark", command.sourceWatermark());
        value.put("contracts", command.contracts().stream().map(this::versionedContract).toList());
        value.put("salesOrders", command.salesOrders().stream().map(this::versionedOrder).toList());
        value.put("orderLines", command.orderLines().stream().map(this::versionedLine).toList());
        value.put("relations", command.orderContractRelations().stream().map(this::versionedRelation).toList());
        value.put("occurredAt", time(command.occurredAt()));
        return sha256(JsonUtils.toJsonString(value));
    }

    public String contractPayload(CommerceContractFact fact) {
        return JsonUtils.toJsonString(contract(fact));
    }

    public String contractPayload(ContractDO row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("companyCode", row.getCompanyCode());
        value.put("contractNo", row.getContractNo());
        value.put("contractName", row.getContractName());
        value.put("customerCode", row.getCustomerCode());
        value.put("customerName", row.getCustomerName());
        value.put("amount", decimal(row.getContractAmount()));
        value.put("currencyCode", row.getCurrencyCode());
        value.put("lifecycleStatus", row.getSourceLifecycleStatus());
        value.put("sourceUpdatedAt", time(row.getSourceUpdatedAt()));
        return JsonUtils.toJsonString(value);
    }

    public String orderPayload(CommerceSalesOrderFact fact) {
        return JsonUtils.toJsonString(order(fact));
    }

    public String orderPayload(SalesOrderDO row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("companyCode", row.getCompanyCode());
        value.put("orderNo", row.getOrderNo());
        value.put("orderType", row.getOrderType());
        value.put("customerCode", row.getCustomerCode());
        value.put("customerName", row.getCustomerName());
        value.put("amount", decimal(row.getOrderAmount()));
        value.put("currencyCode", row.getCurrencyCode());
        value.put("lifecycleStatus", row.getSourceLifecycleStatus());
        value.put("sourceUpdatedAt", time(row.getSourceUpdatedAt()));
        return JsonUtils.toJsonString(value);
    }

    public String linePayload(CommerceOrderLineFact fact) {
        return JsonUtils.toJsonString(line(fact));
    }

    public String linePayload(SalesOrderLineDO row, String salesOrderSourceKey) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("salesOrderSourceKey", salesOrderSourceKey);
        value.put("lineCode", row.getLineCode());
        value.put("itemCode", row.getItemCode());
        value.put("itemDescription", row.getItemDesc());
        value.put("productCode", row.getProductCode());
        value.put("modelCode", row.getModelCode());
        value.put("orderQuantity", decimal(row.getOrderQty()));
        value.put("openQuantity", decimal(row.getOpenQty()));
        value.put("deliveredQuantity", decimal(row.getDeliveredQty()));
        value.put("unitCode", row.getUnitCode());
        value.put("unitScale", row.getUnitScale());
        value.put("quantityStatus", row.getQuantityStatus());
        value.put("lifecycleStatus", row.getSourceLifecycleStatus());
        value.put("sourceUpdatedAt", second(row.getSourceUpdatedAt()));
        return JsonUtils.toJsonString(value);
    }

    public String relationPayload(CommerceOrderContractRelationFact fact) {
        return JsonUtils.toJsonString(relation(fact));
    }

    public String relationPayload(SalesOrderContractRelationDO row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("effectiveFrom", time(row.getEffectiveFrom()));
        value.put("effectiveTo", time(row.getEffectiveTo()));
        return JsonUtils.toJsonString(value);
    }

    private Map<String, Object> versionedContract(CommerceContractFact fact) {
        return versioned(fact.sourceKey(), fact.expectedPreviousSourceVersion(), fact.sourceVersion(), contract(fact));
    }

    private Map<String, Object> versionedOrder(CommerceSalesOrderFact fact) {
        return versioned(fact.sourceKey(), fact.expectedPreviousSourceVersion(), fact.sourceVersion(), order(fact));
    }

    private Map<String, Object> versionedLine(CommerceOrderLineFact fact) {
        return versioned(fact.sourceKey(), fact.expectedPreviousSourceVersion(), fact.sourceVersion(), line(fact));
    }

    private Map<String, Object> versionedRelation(CommerceOrderContractRelationFact fact) {
        Map<String, Object> value = versioned(fact.salesOrderSourceKey() + "\u0000" + fact.contractSourceKey(),
                fact.expectedPreviousSourceVersion(), fact.sourceVersion(), relation(fact));
        return value;
    }

    private Map<String, Object> versioned(String sourceKey, String expected, String version,
                                          Map<String, Object> payload) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceKey", sourceKey);
        value.put("expectedPreviousSourceVersion", expected);
        value.put("sourceVersion", version);
        value.put("payload", payload);
        return value;
    }

    private Map<String, Object> contract(CommerceContractFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("companyCode", fact.companyCode());
        value.put("contractNo", fact.contractNo());
        value.put("contractName", fact.contractName());
        value.put("customerCode", fact.customerCode());
        value.put("customerName", fact.customerName());
        value.put("amount", decimal(fact.amount()));
        value.put("currencyCode", fact.currencyCode());
        value.put("lifecycleStatus", fact.lifecycleStatus().name());
        value.put("sourceUpdatedAt", time(fact.sourceUpdatedAt()));
        return value;
    }

    private Map<String, Object> order(CommerceSalesOrderFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("companyCode", fact.companyCode());
        value.put("orderNo", fact.orderNo());
        value.put("orderType", fact.orderType());
        value.put("customerCode", fact.customerCode());
        value.put("customerName", fact.customerName());
        value.put("amount", decimal(fact.amount()));
        value.put("currencyCode", fact.currencyCode());
        value.put("lifecycleStatus", fact.lifecycleStatus().name());
        value.put("sourceUpdatedAt", time(fact.sourceUpdatedAt()));
        return value;
    }

    private Map<String, Object> line(CommerceOrderLineFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("salesOrderSourceKey", fact.salesOrderSourceKey());
        value.put("lineCode", fact.lineCode());
        value.put("itemCode", fact.itemCode());
        value.put("itemDescription", fact.itemDescription());
        value.put("productCode", fact.productCode());
        value.put("modelCode", fact.modelCode());
        value.put("orderQuantity", decimal(fact.orderQuantity()));
        value.put("openQuantity", decimal(fact.openQuantity()));
        value.put("deliveredQuantity", decimal(fact.deliveredQuantity()));
        value.put("unitCode", fact.unitCode());
        value.put("unitScale", fact.unitScale());
        value.put("quantityStatus", fact.quantityStatus());
        value.put("lifecycleStatus", fact.lifecycleStatus().name());
        value.put("sourceUpdatedAt", second(fact.sourceUpdatedAt()));
        return value;
    }

    private Map<String, Object> relation(CommerceOrderContractRelationFact fact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("effectiveFrom", time(fact.effectiveFrom()));
        value.put("effectiveTo", time(fact.effectiveTo()));
        return value;
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String time(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.withNano(value.getNano() / 1_000_000 * 1_000_000).toString();
    }

    private String second(LocalDateTime value) {
        return value == null ? null : value.withNano(0).toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
}
