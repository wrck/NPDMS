package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目任务状态迁移 DO。 */
@TableName("proj_task_state_transition")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskStateTransitionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long revisionId;
    private String fromStatusCode;
    private String actionCode;
    private String toStatusCode;
    private String standardStatusMapping;
    private String allowedRoleCode;
    private String entryCondition;
    private String exitCondition;
    private Integer version;
}
