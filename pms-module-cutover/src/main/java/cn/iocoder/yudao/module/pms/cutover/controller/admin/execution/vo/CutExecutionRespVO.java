package cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 割接执行 Response VO（FR-CUT-011 / FR-CUT-012）。
 */
@Schema(description = "管理后台 - 割接执行 Response VO")
@Data
public class CutExecutionRespVO {

    @Schema(description = "执行编号", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "执行编码", example = "EXE20260101001")
    private String code;

    @Schema(description = "步骤名称", example = "主用链路下线")
    private String stepName;

    @Schema(description = "操作人编号", example = "1024")
    private Long operatorUserId;

    @Schema(description = "操作时间", example = "2026-01-01T10:00:00")
    private LocalDateTime operationTime;

    @Schema(description = "执行结果", example = "主用已下线，流量已切换")
    private String result;

    @Schema(description = "异常记录", example = "无")
    private String exceptionRecord;

    @Schema(description = "证据附件", example = "/file/evidence.log")
    private String evidenceUrl;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "执行人现场确认")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
