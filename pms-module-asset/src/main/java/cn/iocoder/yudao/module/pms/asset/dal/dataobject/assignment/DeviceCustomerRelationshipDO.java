package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceCustomerRelationshipDO extends TenantBaseDO {

    private Long id;
    private String deviceSn;
    private Long customerId;
    private String relationshipType;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long assignmentVersion;
    private String reason;
    private String operationId;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Integer version;
}
