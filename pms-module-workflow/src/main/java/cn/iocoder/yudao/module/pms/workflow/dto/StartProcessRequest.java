package cn.iocoder.yudao.module.pms.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Request payload for starting a process instance by key.
 */
@Data
@Schema(description = "启动流程请求")
public class StartProcessRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义Key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流程定义Key不能为空")
    @Size(max = 100, message = "流程定义Key长度不能超过 100 个字符")
    private String processDefinitionKey;

    @Schema(description = "业务Key")
    @Size(max = 100, message = "业务Key长度不能超过 100 个字符")
    private String businessKey;

    @Schema(description = "流程变量")
    private Map<String, Object> variables;
}
