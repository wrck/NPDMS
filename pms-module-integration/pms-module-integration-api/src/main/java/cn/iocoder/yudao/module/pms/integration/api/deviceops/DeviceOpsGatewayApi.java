package cn.iocoder.yudao.module.pms.integration.api.deviceops;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;

import java.util.List;
import java.util.Map;

public interface DeviceOpsGatewayApi {

    DeviceOpsDispatchResult dispatch(DeviceOpsDispatchCommand command);

    DeviceOpsTaskSnapshot query(String platformTaskId);

    void cancel(String platformTaskId, String reason);

    default boolean retryResultDelivery(String platformTaskId) {
        return false;
    }

    /** DAC 侧按发起对象返回的结构化解析记录；载荷跟随 DAC 版本，按原始 JSON 对象透传。 */
    default List<Map<String, Object>> semanticResults(String platformTaskId) {
        throw new UnsupportedOperationException("Semantic results unavailable");
    }
}
