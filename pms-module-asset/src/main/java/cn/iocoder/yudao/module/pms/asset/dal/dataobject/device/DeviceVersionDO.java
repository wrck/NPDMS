package cn.iocoder.yudao.module.pms.asset.dal.dataobject.device;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AST 设备档案版本历史 DO（ast_device_version，追加只读）。
 */
@TableName("ast_device_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceVersionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 设备编号（ast_device.id） */
    private Long deviceId;
    /** 设备内递增版本号 */
    private Integer versionNo;
    /** 变更类型 CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP/LOCATION_EFFECTIVE */
    private String changeType;
    /** 变更说明 */
    private String changeDescription;
    /** 变更前快照 */
    private String beforeSnapshot;
    /** 变更后快照 */
    private String afterSnapshot;
}
