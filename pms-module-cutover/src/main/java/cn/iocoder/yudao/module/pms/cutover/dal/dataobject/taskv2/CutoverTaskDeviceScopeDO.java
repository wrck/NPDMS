package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

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
    private Long cutoverTaskId;
    private Long projectId;
    private Long deviceId;
    private String serialNumberSnapshot;
    private Long projectAssignmentVersion;
    private Integer activeMarker;
    @Version
    private Integer version;
}
