package cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Typed original warranty business fact; does not represent a current renewal approval or projection. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_device_warranty_change_record")
public class DeviceWarrantyChangeRecordDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String sourceSystem;
    private String sourceTable;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceReadAt;
    private String sourceChecksum;
    private Long sourceId;
    private String sourceUsername;
    private Integer updateType;
    private String deviceSn;
    private LocalDateTime warrantyStartTime;
    private LocalDateTime warrantyEndTime;
    private Integer warrantyTimes;
    private LocalDateTime sourceUpdatedAt;
}
