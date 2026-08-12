package cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检报告 Response VO")
@Data
public class SrvReportRespVO {

    @Schema(description = "报告编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long taskId;

    @Schema(description = "报告编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "RPT-001")
    private String code;

    @Schema(description = "报告类型 STANDARD 标准 / PDF / DOC / XML", example = "STANDARD")
    private String reportType;

    @Schema(description = "报告内容")
    private String content;

    @Schema(description = "巡检快照")
    private String snapshot;

    @Schema(description = "生成人", example = "300")
    private Long generatedBy;

    @Schema(description = "生成时间")
    private LocalDateTime generatedTime;

    @Schema(description = "状态 0草稿 1已生成 2已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
