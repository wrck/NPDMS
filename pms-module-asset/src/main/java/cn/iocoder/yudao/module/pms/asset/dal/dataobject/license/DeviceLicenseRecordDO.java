package cn.iocoder.yudao.module.pms.asset.dal.dataobject.license;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("ast_device_license_record")
@Data
@EqualsAndHashCode(callSuper=true)
public class DeviceLicenseRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long deviceId;
    private String deviceSn;
    private Long contractId;
    private String contractNo;
    private String contractType;
    private String productCode;
    private String productModel;
    private String licenseStatus;
    @JsonIgnore @ToString.Exclude private String licenseCodeCiphertext;
}
