package cn.iocoder.yudao.module.system.dal.dataobject.organization;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** Independent ownership metadata, not an extension of CompanyDO or DeptDO. */
@TableName("system_organization_ownership")
@Data @EqualsAndHashCode(callSuper=true)
public class OrganizationOwnershipDO extends TenantBaseDO {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private String objectType;
    private Long targetId;
    private String managedBy;
}

