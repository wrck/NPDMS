package cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接执行新增/修改 Request VO（FR-CUT-011）。
 */
@Schema(description = "管理后台 - 割接执行新增/修改 Request VO")
@Data
public class CutExecutionSaveReqVO {

    @Schema(description = "执行编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "割接任务编号不能为空")
    private Long taskId;

    @Schema(description = "执行编码，任务内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "EXE20260101001")
    @NotBlank(message = "执行编码不能为空")
    @Size(max = 64, message = "执行编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "步骤名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "主用链路下线")
    @NotBlank(message = "步骤名称不能为空")
    @Size(max = 255, message = "步骤名称长度不能超过 255 个字符")
    private String stepName;

    @Schema(description = "操作人编号", example = "1024")
    private Long operatorUserId;

    @Schema(description = "操作时间", example = "2026-01-01T10:00:00")
    private java.time.LocalDateTime operationTime;

    @Schema(description = "执行结果", example = "主用已下线，流量已切换")
    private String result;

    @Schema(description = "异常记录", example = "无")
    private String exceptionRecord;

    @Schema(description = "证据附件", example = "/file/evidence.log")
    private String evidenceUrl;

    @Schema(description = "备注", example = "执行人现场确认")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
