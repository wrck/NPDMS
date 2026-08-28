package cn.iocoder.yudao.module.pms.customer.dal.dataobject.security;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cus_customer_scope_slice")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerScopeSliceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String subjectType;
    private Long subjectId;
    private String departmentMode;
    private String departmentCodes;
    private String marketMode;
    private String marketCodes;
    private String systemMode;
    private String systemCodes;
    private String expendMode;
    private String expendCodes;
    private String industryMode;
    private String industryCodes;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private Integer version;
}
