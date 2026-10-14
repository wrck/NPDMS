package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@TableName("cut_task_stage_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverTaskStageHistoryDO {

    @TableId
    private Long id;
    private Long tenantId;
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Integer sequenceNo;
    @TableField(value = "from_stage_code")
    private String fromStage;
    @TableField(value = "to_stage_code")
    private String toStage;
    @TableField(value = "from_status_code")
    private String fromStatus;
    @TableField(value = "to_status_code")
    private String toStatus;
    private String triggerType;
    private Long triggerReferenceId;
    @TableField(value = "actor_user_id")
    private Long actorId;
    private String correlationId;
    @TableField(value = "occur_time")
    private LocalDateTime occurredAt;
    private String creator;
    private LocalDateTime createTime;
    @TableField(exist = false)
    private Boolean deleted;
}
