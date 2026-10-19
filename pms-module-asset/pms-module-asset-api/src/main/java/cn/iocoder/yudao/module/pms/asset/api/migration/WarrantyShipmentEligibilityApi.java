package cn.iocoder.yudao.module.pms.asset.api.migration;
import java.util.List;
/** Read-only qualification from the local shipment facts; no device or project assignment. */
public interface WarrantyShipmentEligibilityApi {
    record Query(Long tenantId,Long actorId,List<String> deviceSns) {}
    record Result(String deviceSn,boolean eligible,String issue) {}
    List<Result> resolve(Query query);
}
