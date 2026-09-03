package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_sales_order_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderLineDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String sourceSystem;
    private String sourceRecordKey;
    private String sourceVersion;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private String orderType;
    private String orderNo;
    private String lineNo;
    private String lineType;
    private String itemCode;
    private String modelCode;
    private String itemDesc;
    private Long productId;
    private String productCode;
    private BigDecimal orderQty;
    private BigDecimal openQty;
    private BigDecimal deliveredQty;
    private String unitCode;
    private Integer unitScale;
    private String quantityStatus;
    private String sourceLifecycleStatus;
    private LocalDateTime sourceSyncTime;
    private LocalDateTime sourceUpdatedAt;
    private String status;
    @Version
    private Integer version;

    public String getSourceKey() {
        return sourceRecordKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceRecordKey = sourceKey;
    }

    public String getLineCode() {
        return lineNo;
    }

    public void setLineCode(String lineCode) {
        this.lineNo = lineCode;
    }

    public BigDecimal getQuantity() {
        return orderQty;
    }

    public void setQuantity(BigDecimal quantity) {
        this.orderQty = quantity;
    }

    public LocalDateTime getSyncedAt() {
        return sourceSyncTime;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.sourceSyncTime = syncedAt;
    }
}
