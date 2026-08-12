package cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理后台 - 工期倒排新增/修改 Request VO（FR-PROJ-018）。
 */
@Schema(description = "管理后台 - 工期倒排新增/修改 Request VO")
@Data
public class ScheduleBackwardSaveReqVO {

    @Schema(description = "倒排记录编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "目标完工日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-12-31")
    @NotNull(message = "目标完工日期不能为空")
    private LocalDate targetDate;

    @Schema(description = "项目类型：DIRECT 直签 / INDIRECT 非直签",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "DIRECT")
    @NotBlank(message = "项目类型不能为空")
    private String projectType;

    @Schema(description = "备注", example = "按客户要求年底完工")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;

}
