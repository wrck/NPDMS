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

@TableName("com_sales_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String sourceSystem;
    private String sourceRecordKey;
    private String sourceVersion;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private String orderType;
    private String orderNo;
    private String salesType;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String sourceProjectName;
    private String orderComment;
    private BigDecimal orderAmount;
    private String currencyCode;
    private String authorityStatus;
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

    public LocalDateTime getSyncedAt() {
        return sourceSyncTime;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.sourceSyncTime = syncedAt;
    }
}
