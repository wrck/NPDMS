package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAssignmentReconciliationDO extends TenantBaseDO {

    private Long id;
    private String deviceSn;
    private Long projectId;
    private Long projectCustomerId;
    private Long deviceCustomerId;
    private String status;
    private String reason;
    private Integer version;
}
