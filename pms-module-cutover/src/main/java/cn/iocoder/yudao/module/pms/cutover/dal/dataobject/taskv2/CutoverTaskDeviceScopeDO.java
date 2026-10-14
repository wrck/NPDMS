package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_task_device_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverTaskDeviceScopeDO extends TenantBaseDO {

    @TableId
    private Long id;
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Long projectId;
    private Long deviceId;
    @TableField(value = "sn")
    private String serialNumberSnapshot;
    private Long projectAssignmentVersion;
    @TableField(value = "device_type_code")
    private String deviceTypeCodeSnapshot;
    @TableField(value = "device_type_source_version")
    private String deviceTypeSourceVersionSnapshot;
    private Integer activeMarker;
    @Version
    private Integer version;
}
