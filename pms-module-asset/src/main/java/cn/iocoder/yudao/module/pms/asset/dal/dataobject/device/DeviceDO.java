package cn.iocoder.yudao.module.pms.asset.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("ast_device")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String sn;
    private String name;
    private String productCode;
    private String productModel;
    private String productName;
    private String productDesc;
    private LocalDateTime shipmentTime;
    private String packageNo;
    private String contractNo;
    private Long shipmentRecordId;
    private Long projectId;
    private Long projectAssignmentVersion;
    private Long customerId;
    private Long customerAssignmentVersion;
    private Long siteId;
    private Long siteLocationId;
    private String locationResolutionStatus;
    private String locationSnapshot;
    private LocalDateTime locationEffectiveFrom;
    private Long locationRecordId;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private String warrantyStatus;
    private String conpVersion;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String status;
    private String remark;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    private String syncStatus;
    @Version
    private Integer version;
}
