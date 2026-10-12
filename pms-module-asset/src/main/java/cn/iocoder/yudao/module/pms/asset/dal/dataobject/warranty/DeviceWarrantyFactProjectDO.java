package cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Project association of an immutable source warranty fact; never device ownership. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_device_warranty_fact_project")
public class DeviceWarrantyFactProjectDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long projectId;
    private String sourceProjectKey;
    private String factType;
    private Long factId;
}
