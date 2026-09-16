package cn.iocoder.yudao.module.pms.lowcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * DTO representing a Flowable process instance.
 */
@Data
@Schema(description = "流程实例")
public class ProcessInstanceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流程实例ID")
    private String id;

    @Schema(description = "流程定义Key")
    private String processDefinitionKey;

    @Schema(description = "流程定义名称")
    private String processDefinitionName;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "发起人ID")
    private String startUserId;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "当前任务名称")
    private String currentTaskName;
}
