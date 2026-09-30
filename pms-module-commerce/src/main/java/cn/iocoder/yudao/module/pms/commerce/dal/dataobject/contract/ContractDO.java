package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("com_contract")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractDO extends BaseBusinessEntity {
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
    private String currencyName;
    private LocalDateTime contractCreateTime;
    private String projectName;
    private String projectCode;
    private String marketCode;
    private String marketName;
    private String departmentCode;
    private String departmentName;
    private String systemCode;
    private String systemName;
    private String expendCode;
    private String expendName;
    private String industryCode;
    private String industryName;
    private String marketingRepresentativeCode;
    private String marketingRepresentativeName;
    private String secondaryRepresentativeCode;
    private String authorityStatus;
    private String sourceLifecycleStatus;
    private LocalDateTime sourceSyncTime;
    private LocalDateTime sourceUpdatedAt;
    private String status;

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
