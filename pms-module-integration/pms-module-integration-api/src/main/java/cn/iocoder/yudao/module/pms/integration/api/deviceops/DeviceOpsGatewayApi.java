package cn.iocoder.yudao.module.pms.integration.api.deviceops;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;

public interface DeviceOpsGatewayApi {

    DeviceOpsDispatchResult dispatch(DeviceOpsDispatchCommand command);

    DeviceOpsTaskSnapshot query(String platformTaskId);

    void cancel(String platformTaskId, String reason);
}
