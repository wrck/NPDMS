package cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 项目任务完成判定追加事实 DO。 */
@TableName("proj_project_task_completion_evaluation")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTaskCompletionEvaluationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectTaskId;
    private Long executionContractId;
    private Integer taskVersion;
    private Integer contractVersion;
    private String evaluationResultCode;
    private String unmetItemsJson;
    private String commandId;
    private String idempotencyKey;
    private String factContextCode;
    private String factObjectType;
    private String factObjectKey;
    private Long factVersion;
    private String gateSnapshotRef;
    private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
    private Integer version;
}
