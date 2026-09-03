package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_task_stage_history")
@Data
public class CutoverTaskStageHistoryDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long cutoverTaskId;
    private Integer sequenceNo;
    private String fromStage;
    private String toStage;
    private String fromStatus;
    private String toStatus;
    private String triggerType;
    private Long triggerReferenceId;
    private Long actorId;
    private String correlationId;
    private LocalDateTime occurredAt;
    private String creator;
    private LocalDateTime createTime;
    @TableField(exist = false)
    private Boolean deleted;
}
