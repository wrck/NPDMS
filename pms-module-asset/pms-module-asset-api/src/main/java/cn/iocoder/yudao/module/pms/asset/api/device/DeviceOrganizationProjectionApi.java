package cn.iocoder.yudao.module.pms.asset.api.device;

import java.util.Set;

/** AST 所有：从 PROJ/COM 当前权威事实重建设备归属，与来源写入共享事务，失败回滚。 */
public interface DeviceOrganizationProjectionApi {
    record Refresh(Long tenantId, Set<Long> deviceIds, Set<Long> projectIds, Set<String> contractNumbers) {}
    void refresh(Refresh command);
}
