package cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("ast_device_warranty_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceWarrantyRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
    private String combinedDeviceSn;
    private String productCode;
    private String productName;
    private String productDescription;
    private String gradeName;
    private LocalDateTime shipmentTime;
    private String warrantyFlag;
    private LocalDateTime sourceWarrantyStartTime;
    private LocalDateTime sourceWarrantyEndTime;
    private LocalDateTime originalWarrantyStartTime;
    private LocalDateTime originalWarrantyEndTime;
    private Integer remainingDaysAtSource;
    private Integer originalRemainingDaysAtSource;
    private String contractType;
    private String contractTypeName;
    private String projectName;
    private String customerName;
    private String finalCustomerName;
    private String agentName;
    private String officeCode;
    private String officeName;
    private String marketCode;
    private String marketName;
    private String systemCode;
    private String systemName;
    private String secondarySn;
    private String secondaryItem;
    private String secondaryProductName;
    private String secondaryProductDescription;
    private LocalDateTime sourceSyncTime;
    private String sourceShipmentKey;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private Integer warrantyMonths;
    private String warrantyGrade;
    private String warrantyContractNo;
    private Boolean extended;
    private String remark;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Integer version;
}
