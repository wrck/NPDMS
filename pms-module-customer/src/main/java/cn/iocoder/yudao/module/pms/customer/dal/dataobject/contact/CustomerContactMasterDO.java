package cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Customer-owned master. Project-local edits must not update this record. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cus_customer_contact")
public class CustomerContactMasterDO extends TenantBaseDO {
    @TableId private Long id;
    private Long customerId;
    @TableField(exist=false) private String customerName;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String department;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String mobile;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String email;
    private Boolean primaryFlag;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String remark;
    @Version private Integer version;
}
