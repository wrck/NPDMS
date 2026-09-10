package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - TASK_NATIVE动作 Request VO")
@Data
public class ProjectTaskActionReqVO {

    @Size(max = 500, message = "动作原因不能超过500个字符")
    private String reason;
    private Long executionContractId;
    private Integer contractVersion;
    @Size(max = 128, message = "事实对象键不能超过128个字符")
    private String factObjectKey;
    private Long factVersion;
    @Schema(description = "业务关联事实集合版本，业务任务完成必填")
    @Size(max = 64)
    private String expectedBusinessFactVersion;
    private Integer expectedActivityVersion;
    private Integer expectedReportVersion;
}
