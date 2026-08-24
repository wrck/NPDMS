package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_order_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderLineDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Long orderId;
    private String lineCode;
    private String itemCode;
    private BigDecimal quantity;
    private String unitCode;
    private String quantityStatus;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    @Version
    private Integer version;
}
