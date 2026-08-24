package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_delivery_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryScopeDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long orderLineId;
    private Long projectId;
    private BigDecimal allocatedQty;
    private String scopeStatus;
    private Long allocationVersion;
    private String sourceEvidence;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @Version
    private Integer version;
}
