package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAncestorProjectionOperationDO extends TenantBaseDO {

    private Long id;
    private String eventId;
    private String operationId;
    private String deviceSn;
    private Long projectId;
    private Long treeVersion;
    private Long assignmentVersion;
}
