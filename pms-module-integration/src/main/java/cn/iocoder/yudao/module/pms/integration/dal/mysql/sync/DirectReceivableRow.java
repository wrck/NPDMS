package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** Immutable original business history; no current authority or permission is derived from this entity. */
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("com_contract_receivable")
public class DirectReceivableRow extends TenantBaseDO {
    @TableId
    private Long id;
    private Long contractId;
    private String contractNo;
    private Long companyId;
    private String companyCode;
    private String companyName;
    private String companyResolutionSource;
    private String mappingStatus;
    private String projectName;
    private String sourceOrderNo;
    private String customerCode;
    private String customerName;
    private java.math.BigDecimal contractAmount;
    private java.math.BigDecimal deliveredAmount;
    private java.math.BigDecimal collectedAmount;
    private java.math.BigDecimal collectedRatio;
    private java.math.BigDecimal receivableAmount;
    private java.math.BigDecimal overdueAmount;
    private String currencyName;
    private String marketCode;
    private String marketName;
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private String industryName;
    private String marketingRepresentativeName;
    private String sourceBatchCode;
    private String importBatchNo;
    private String projectCode;
    private String systemSourceKey;
    private String systemCode;
    private String systemName;
    private String industryCode;
    private String expendSourceKey;
    private String expendCode;
    private String expendName;
    private String marketingRepresentativeCode;
    private String secondaryRepresentativeCode;
    private String originalDivisionValues;
    private java.time.LocalDateTime sourceEffectiveFrom;
    private java.time.LocalDateTime sourceEffectiveTo;
    private java.time.LocalDateTime contractCreateTime;
    private java.time.LocalDateTime latestShipTime;
    private String sourceSystem;
    private String sourceRecordKey;
    private java.time.LocalDateTime sourceSyncTime;
    private Long sourceRowId;
    private String sourceRevisionHash;
    private String sourceDataSource;
    private String sourceCreatedBy;
    private java.time.LocalDateTime sourceCreatedAt;
    private String sourceUpdatedBy;
    private java.time.LocalDateTime sourceUpdatedAt;
    private Double sourceCollectedMoneyRatio;
}
