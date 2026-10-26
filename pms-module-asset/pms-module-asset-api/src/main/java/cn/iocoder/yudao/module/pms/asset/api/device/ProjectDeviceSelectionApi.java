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

    /** 任务授权复核：发起人来自已验证的任务，不能来自终端任意输入。 */
    List<SelectedProjectDevice> validateSelection(AuthorizedSelection query);
    /** 安装地址维护专用：当前登录人授权项目内，允许已有已完成安装历史的设备新增安装记录。 */
    List<SelectedProjectDevice> validateInstallationSelection(AuthorizedSelection query);

    record AuthorizedSelection(Long tenantId, Long actorId, Long projectId, List<Long> deviceIds) { }

}
