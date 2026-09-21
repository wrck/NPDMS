package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import java.util.List;

/** 校验并锁定属于当前项目或其关联合同的设备，返回服务器序列号快照。 */
public interface ProjectDeviceSelectionApi {
    List<SelectedProjectDevice> validateSelection(Long projectId, List<Long> deviceIds);
}
