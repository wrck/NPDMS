package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private String contractNo;
    private String masterSourceSystem;
    private String masterSourceRecordKey;
    private String masterSourceVersion;
    private String contractType;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String contractName;
    private BigDecimal contractAmount;
    private String currencyCode;
    private String authorityStatus;
    private String sourceLifecycleStatus;
    private LocalDateTime sourceSyncTime;
    private LocalDateTime sourceUpdatedAt;
    private String status;
    @Version
    private Integer version;

    public String getSourceVersion() {
        return masterSourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.masterSourceVersion = sourceVersion;
    }

    public String getSourceSystem() {
        return masterSourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.masterSourceSystem = sourceSystem;
    }

    public String getSourceKey() {
        return masterSourceRecordKey;
    }

    public void setSourceKey(String sourceKey) {
        this.masterSourceRecordKey = sourceKey;
    }

    public LocalDateTime getSyncedAt() {
        return sourceSyncTime;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.sourceSyncTime = syncedAt;
    }
}
