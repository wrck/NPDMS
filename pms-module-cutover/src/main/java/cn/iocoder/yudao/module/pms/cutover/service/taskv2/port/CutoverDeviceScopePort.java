package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import java.util.List;

/** CUT 对 AST 稳定设备身份与项目归属事实的消费端口。 */
public interface CutoverDeviceScopePort {

    List<DeviceFact> resolveBySerials(List<String> serialNumbers);

    List<DeviceFact> lockAndRevalidate(Long projectId, List<DeviceFact> expectedDevices);

    record DeviceFact(Long deviceId, String serialNumber, Long projectId,
                      long projectAssignmentVersion) {
    }
}
