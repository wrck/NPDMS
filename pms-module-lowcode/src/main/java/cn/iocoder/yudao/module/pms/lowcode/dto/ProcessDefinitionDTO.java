package cn.iocoder.yudao.module.pms.lowcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * DTO representing a Flowable process definition.
 */
@Data
@Schema(description = "流程定义")
public class ProcessDefinitionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义ID")
    private String id;

    @Schema(description = "流程定义名称")
    private String name;

    @Schema(description = "流程定义Key")
    private String key;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "部署ID")
    private String deploymentId;

    @Schema(description = "部署时间")
    private Date deployTime;

    @Schema(description = "资源名称")
    private String resourceName;

    @Schema(description = "是否挂起")
    private Boolean suspended;
}
