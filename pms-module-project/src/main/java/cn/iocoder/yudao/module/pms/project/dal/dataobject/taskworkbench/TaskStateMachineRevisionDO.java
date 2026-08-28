package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户级项目任务状态机版本 DO。
 */
@TableName("proj_task_state_machine_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskStateMachineRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Integer revisionNo;
    private String status;
    private LocalDateTime effectiveFrom;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer version;
}
