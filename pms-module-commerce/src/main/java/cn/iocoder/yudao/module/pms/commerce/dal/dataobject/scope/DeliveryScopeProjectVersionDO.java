package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("com_delivery_scope_project_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopeProjectVersionDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long scopeVersion;
    private Integer payloadVersion;
    private String lastChangeType;
    @Version
    private Integer version;
}
