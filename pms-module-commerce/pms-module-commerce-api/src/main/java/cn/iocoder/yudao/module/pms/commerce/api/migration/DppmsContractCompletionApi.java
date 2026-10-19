package cn.iocoder.yudao.module.pms.commerce.api.migration;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Completes current-run delivery objects from qualified, complete local migration inputs. */
public interface DppmsContractCompletionApi {
    record DeliveryScope(Set<Long> contractIds,Set<Long> shipmentContractIds,Set<Long> packageIds) {}
    record Command(Long tenantId,Long actorId,boolean preview,DeliveryScope current,
                   Set<Long> shipmentContractIds,Set<Long> receivableIds) {}
    record Result(long changedContracts,long changedShipmentContracts,long changedPackages) {
        public Result(long changedContracts){this(changedContracts,0,0);}
    }
    enum Target { CONTRACT, SHIPMENT_CONTRACT, PACKAGE }
    record ProjectionQuery(Long tenantId,Long actorId,Target target,Set<Long> ids) {}
    /** Latest mutable Owner projection only; this never replaces original source evidence. */
    @Data class Projection {
        private Long id;
        private Long companyId;
        private String companyCode;
        private String companyName;
        private Long contractId;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private Long shipmentContractReferenceId;
        private Long version;
        private String currencyName;
        private String currencyCode;
        private BigDecimal contractAmount;
        private LocalDateTime contractCreateTime;
        private String projectName;
        private String projectCode;
        private String marketCode;
        private String marketName;
        private String industryCode;
        private String industryName;
        private String marketingRepresentativeCode;
        private String marketingRepresentativeName;
        private String secondaryRepresentativeCode;
        private String systemName;
    }
    Result complete(Command command);
    List<Projection> readProjection(ProjectionQuery query);
}
