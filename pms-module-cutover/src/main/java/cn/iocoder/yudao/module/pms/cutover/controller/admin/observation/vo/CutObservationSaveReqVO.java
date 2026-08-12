package cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 稳定观察新增/修改 Request VO（FR-CUT-013）。
 */
@Schema(description = "管理后台 - 稳定观察新增/修改 Request VO")
@Data
public class CutObservationSaveReqVO {

    @Schema(description = "观察编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "割接任务编号不能为空")
    private Long taskId;

    @Schema(description = "观察编码，任务内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "OBS20260101001")
    @NotBlank(message = "观察编码不能为空")
    @Size(max = 64, message = "观察编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "观察开始时间", example = "2026-01-01T10:00:00")
    private java.time.LocalDateTime observationStart;

    @Schema(description = "观察结束时间", example = "2026-01-02T10:00:00")
    private java.time.LocalDateTime observationEnd;

    @Schema(description = "观察人编号", example = "1024")
    private Long observerUserId;

    @Schema(description = "遗留项清单", example = "现场指示灯异常，待后续处理")
    private String leftoverItems;

    @Schema(description = "遗留项状态 0无遗留 1待处理 2已闭环", example = "0")
    private Integer leftoverStatus;

    @Schema(description = "观察结论", example = "业务运行稳定，无异常")
    private String conclusion;

    @Schema(description = "备注", example = "观察期 24 小时")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
