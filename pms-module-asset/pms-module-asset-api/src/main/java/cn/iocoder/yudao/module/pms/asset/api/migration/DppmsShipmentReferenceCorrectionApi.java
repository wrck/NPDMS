package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;

/** Limited repair of verified references; preserves every other business and historical audit column. */
public interface DppmsShipmentReferenceCorrectionApi {
    List<Baseline> preview(Command command);
    void correct(Command command,List<Baseline> baselines);
    void verify(Command command,List<Baseline> baselines);
    record Command(Long tenantId,Long actorId,Long projectId,List<Reference> references) {}
    record Reference(String sourceKey,Long shipmentId,Long deviceId,Long orderLineId,String deviceSn,
                     String orderNo,String originalLineNo) {}
    record Baseline(Reference reference,String shipmentFieldsJson,String deviceFieldsJson) {}
}
