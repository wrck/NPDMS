package cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检执行记录创建/修改 Request VO")
@Data
public class SrvExecutionSaveReqVO {

    @Schema(description = "执行记录编号", example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属巡检任务编号不能为空")
    private Long taskId;

    @Schema(description = "执行编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "E-001")
    @NotBlank(message = "执行编码不能为空")
    @Size(max = 64, message = "执行编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "关联规则编号", example = "200")
    private Long ruleId;

    @Schema(description = "执行时间")
    private LocalDateTime executionTime;

    @Schema(description = "执行人", example = "300")
    private Long executorUserId;

    @Schema(description = "执行结果")
    private String result;

    @Schema(description = "异常记录")
    private String exceptionRecord;

    @Schema(description = "证据附件")
    private String evidenceUrl;

    @Schema(description = "状态 0待执行 1执行中 2已完成 3异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
