package cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检执行记录 Response VO")
@Data
public class SrvExecutionRespVO {

    @Schema(description = "执行记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long taskId;

    @Schema(description = "执行编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "E-001")
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

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
