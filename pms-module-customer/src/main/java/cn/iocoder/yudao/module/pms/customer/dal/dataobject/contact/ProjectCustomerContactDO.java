package cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Independent project contact facts, retaining the source contact identity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cus_project_customer_contact_relation")
public class ProjectCustomerContactDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private Long customerId;
    private Long customerContactId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String department;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String mobile;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String roleCode;
    private Boolean primaryFlag;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime primarySetTime;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String remark;
    @Version private Integer version;
    private Long deletedBy;
    private LocalDateTime deletedAt;
}
