package cn.iocoder.yudao.module.pms.cutover.service.task.port;

import java.util.List;

/** CUT 对 AST 稳定设备身份与项目归属事实的消费端口。 */
public interface CutoverDeviceScopePort {

    List<DeviceFact> resolveBySerials(List<String> serialNumbers);

    /** 调用方已核验该项目当前完整范围；只解析请求SN，保留AST状态及归属判定。 */
    List<DeviceFact> resolveBySerials(cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery query);

    List<DeviceFact> lockAndRevalidate(Long projectId, List<DeviceFact> expectedDevices);

    record DeviceFact(Long deviceId, String serialNumber, Long projectId,
                      long projectAssignmentVersion) {
    }
}
