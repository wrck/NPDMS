package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import java.util.List;

/** 校验并锁定属于当前项目或其关联合同的设备，返回服务器序列号快照。 */
public interface ProjectDeviceSelectionApi {
    /** 授权项目或其关联合同的可选设备；由 AST 返回设备资料，不授予凭证使用权。 */
    SelectionPage pageSelection(SelectionQuery query);

    record SelectionQuery(Long projectId, String serialKeyword, int pageNo, int pageSize) { }
    record SelectionPage(List<SelectedProjectDevice> list, long total) {
        public SelectionPage { list = List.copyOf(list); }
    }

    List<SelectedProjectDevice> validateSelection(Long projectId, List<Long> deviceIds);
}
