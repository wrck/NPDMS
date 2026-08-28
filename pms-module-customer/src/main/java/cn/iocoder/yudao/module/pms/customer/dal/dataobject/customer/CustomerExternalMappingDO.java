package cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_customer_external_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerExternalMappingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long customerId;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer currentMarker;
}
