package cn.iocoder.yudao.module.pms.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Request payload for completing a task.
 */
@Data
@Schema(description = "完成任务请求")
public class CompleteTaskRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @Schema(description = "流程变量")
    private Map<String, Object> variables;

    @Schema(description = "审批意见")
    private String comment;
}
