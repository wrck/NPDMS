package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** Immutable source factory version facts, separate from the MES current baseline. */
@TableName("ast_device_factory_version_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceFactoryVersionRecordDO extends TenantBaseDO {
    @TableId private Long id;
    private Long projectId;
    private String deviceSn;
    private String conpVersion;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
}
