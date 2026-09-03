package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import java.time.LocalDateTime;
import java.util.List;

/** CUT对F-AST-002设备产品类型公开事实的最窄消费端口。 */
public interface CutoverDeviceProductTypePort {

    List<ProductTypeFact> resolveAuthorized(Long actorId, List<Long> deviceIds);

    record ProductTypeFact(Long deviceId, String productTypeCode, boolean enabled, String sourceVersion,
                           String resolutionStatus, String syncStatus, LocalDateTime lastSuccessfulSyncTime,
                           boolean fromLastSuccessfulCopy) {
    }
}
