package cn.iocoder.yudao.module.pms.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * DTO representing a Flowable task.
 */
@Data
@Schema(description = "任务")
public class TaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private String id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "办理人")
    private String assignee;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "流程定义名称")
    private String processDefinitionName;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "业务Key")
    private String businessKey;
}
