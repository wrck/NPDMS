package cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@TableName("ast_device_warranty_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceWarrantyRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
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
