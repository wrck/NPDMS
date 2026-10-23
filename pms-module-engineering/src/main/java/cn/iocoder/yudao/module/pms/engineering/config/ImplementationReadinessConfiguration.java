package cn.iocoder.yudao.module.pms.engineering.config;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.ArrivalAcceptanceFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.ImplementationArrivalFactApiImpl;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.*;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅装配已存在的 COM/AST 事实契约，不改变原到货命令。 */
@Configuration(proxyBeanMethods = false)
public class ImplementationReadinessConfiguration {
    @Bean @ConditionalOnMissingBean(DeliveryScopePort.class)
    DeliveryScopePort implementationDeliveryScope(DeliveryScopeApi api) {
        return new DeliveryScopePort() {
            public AssignedScope inspectAssignedScope(Long projectId) { return read(projectId, null); }
            public AssignedScope lockAndRevalidate(Long projectId, Long expectedScopeVersion) {
                return read(projectId, expectedScopeVersion);
            }
            private AssignedScope read(Long projectId, Long version) {
                try {
                    var fact = api.getAssignedScope(projectId, version);
                    return new AssignedScope(fact.projectId(), fact.scopeVersion(), assignedLines(fact.assignedLines()));
                } catch (DeliveryScopeFactException failure) {
                    if (failure.getCode() == DeliveryScopeFactException.Code.SCOPE_STALE)
                        throw new OwnerFactVersionMismatchException("COM delivery scope changed");
                    throw failure;
                }
            }
        };
    }

    @Bean @ConditionalOnMissingBean(ArrivalAcceptanceFactApi.class)
    ArrivalAcceptanceFactApi implementationArrivalFact(ArrivalAcceptanceMapper arrivals, ArrivalLineMapper lines,
            ArrivalDifferenceMapper differences, DeliveryScopePort delivery, DeviceScopeFactPort devices) {
        return new ImplementationArrivalFactApiImpl(arrivals, lines, differences, delivery, devices);
    }

    private static java.util.List<DeliveryScopePort.AssignedLine> assignedLines(
            java.util.List<cn.iocoder.yudao.module.pms.commerce.api.scope.dto.AssignedDeliveryScopeLine> source) {
        record QuantityKey(Long orderLineId, String product, String model, String unit) { }
        var result = new java.util.ArrayList<DeliveryScopePort.AssignedLine>();
        var quantities = new java.util.LinkedHashMap<QuantityKey, java.math.BigDecimal>();
        for (var line : source) {
            if (line.serialNumbers().isEmpty()) {
                quantities.merge(new QuantityKey(line.orderLineId(), line.productCode(), line.productModel(), line.unitCode()),
                        line.quantity(), java.math.BigDecimal::add);
            } else {
                result.add(new DeliveryScopePort.AssignedLine(line.orderLineId(), line.quantity(), line.unitCode(),
                        line.productCode(), line.productModel(), new java.util.TreeSet<>(line.serialNumbers())));
            }
        }
        quantities.forEach((key, quantity) -> result.add(new DeliveryScopePort.AssignedLine(key.orderLineId(),
                quantity, key.unit(), key.product(), key.model(), java.util.Set.of())));
        return java.util.List.copyOf(result);
    }
}
