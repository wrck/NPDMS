package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @deprecated V160起由统一权威订单行载体
 * {@link cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO}替代。
 */
@TableName("com_order_line")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated(since = "2026.09", forRemoval = true)
public class OrderLineDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Long orderId;
    private String lineCode;
    private String itemCode;
    private String modelCode;
    private BigDecimal quantity;
    private String unitCode;
    private String quantityStatus;
    private String sourceLifecycleStatus;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    @Version
    private Integer version;
}
