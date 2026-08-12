package cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检报告创建/修改 Request VO")
@Data
public class SrvReportSaveReqVO {

    @Schema(description = "报告编号", example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属巡检任务编号不能为空")
    private Long taskId;

    @Schema(description = "报告编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "RPT-001")
    @NotBlank(message = "报告编码不能为空")
    @Size(max = 64, message = "报告编码长度不能超过 64 个字符")
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

}
