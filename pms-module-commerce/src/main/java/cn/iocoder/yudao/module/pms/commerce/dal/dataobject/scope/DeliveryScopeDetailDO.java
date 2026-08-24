package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("com_delivery_scope_detail")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopeDetailDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long deliveryScopeId;
    private String officeDepartmentCode;
    private String serialNo;
    private BigDecimal allocatedQty;
    private String detailStatus;
    private String sourceSnapshot;
    @Version
    private Integer version;
}
