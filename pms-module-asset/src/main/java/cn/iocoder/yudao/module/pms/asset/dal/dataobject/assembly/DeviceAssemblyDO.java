package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_assembly")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceAssemblyDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String parentDeviceSn;
    private String childDeviceSn;
    private String positionCode;
    private String assemblyType;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String evidenceRef;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Integer version;
}
