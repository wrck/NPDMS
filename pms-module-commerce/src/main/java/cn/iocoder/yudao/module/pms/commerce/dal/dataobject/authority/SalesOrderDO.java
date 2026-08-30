package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_sales_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String companyCode;
    private String orderNo;
    private String orderType;
    private String customerCode;
    private String customerName;
    private BigDecimal orderAmount;
    private String currencyCode;
    private String authorityStatus;
    private String sourceLifecycleStatus;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    @Version
    private Integer version;
}
