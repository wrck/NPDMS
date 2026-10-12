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
@TableName("ast_device_warranty_service_record")
public class DeviceWarrantyServiceRecordDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String sourceSystem;
    private String sourceTable;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceReadAt;
    private String sourceChecksum;
    private String sourceId;
    private String renewalContractNo;
    private String deviceSn;
    private String grade;
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
    private String warrantyFlag;
    private String remark;
    private Integer extensionFlag;
    private Integer sourceState;
}
