package cn.iocoder.yudao.module.pms.commerce.api.scope;

import java.util.List;

/** Project-scoped read-only logistics for the currently displayed shipment records. */
public interface ProjectShipmentLogisticsApi {
    List<Fact> read(Query query);
    record Reference(String sourceSystem, String packageNo, String contractNo) {}
    record Query(Long tenantId, Long userId, Long projectId, List<Reference> references) {}
    record Fact(String sourceSystem, String packageNo, String contractNo,
                String carrierName, String expressNo, String receiverName) {
        public Reference reference() { return new Reference(sourceSystem, packageNo, contractNo); }
    }
}
