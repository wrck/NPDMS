package cn.iocoder.yudao.module.pms.customer.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_customer_location_reference")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerLocationReferenceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long customerId;
    private String locationType;
    private Long locationId;
    private Integer sourceVersion;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
