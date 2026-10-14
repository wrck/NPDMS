package cn.iocoder.yudao.module.pms.asset.dal.dataobject.cutover;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper = true) @TableName("ast_device_cutover_result")
public class DeviceCutoverResultDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long deviceId;
    @TableField(value = "sn")
    private String serialNumber;
    private Long assignmentVersion;
    private Long taskId;
    private Long closureId;
    private Long closureVersion;
    private String eventId;
    private String resultRef;
    @TableField(value = "cutover_time")
    private LocalDateTime cutoverAt;
    @TableField(value = "refresh_status_code")
    private String refreshStatus;
    @TableField(exist = false)
    private String resultSnapshot;

    /** 割接任务号；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String taskNo;
    /** 割接任务名称；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String taskName;
    /** 原闭环提交人；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long submitUserId;
}
