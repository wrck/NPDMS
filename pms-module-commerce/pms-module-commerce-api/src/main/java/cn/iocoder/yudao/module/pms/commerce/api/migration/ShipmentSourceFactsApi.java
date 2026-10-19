package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** Exact local package/contract facts for incoming shipment events. */
public interface ShipmentSourceFactsApi {
    List<Fact> read(Query query);
    record Query(Long tenantId,Long actorId,List<String> packageSourceKeys) {}
    record Fact(String packageSourceKey,LocalDateTime shipmentTime,String contractNo,String contractType) {
        public Fact(String packageSourceKey,LocalDateTime shipmentTime,String contractNo) {this(packageSourceKey,shipmentTime,contractNo,null);}
    }
}
