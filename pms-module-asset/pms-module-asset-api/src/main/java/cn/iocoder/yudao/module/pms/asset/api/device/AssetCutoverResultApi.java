package cn.iocoder.yudao.module.pms.asset.api.device;

import java.time.LocalDateTime;
import java.util.List;

/** 资产 Owner 已消费的割接服务事实；不把记录创建当成设备版本或生命周期变化。 */
public interface AssetCutoverResultApi {
    List<Receipt> byTask(Query query);
    record Query(Long tenantId, Long projectId, Long taskId) { }
    record Receipt(Long deviceId, String serialNumber, Long taskId, Long closureId, Long closureVersion,
                   String refreshStatus, LocalDateTime cutoverAt, LocalDateTime refreshedAt) { }
}
