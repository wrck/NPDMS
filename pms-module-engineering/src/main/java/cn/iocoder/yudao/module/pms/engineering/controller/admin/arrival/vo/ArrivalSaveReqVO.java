package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 到货签收新增/修改 Request VO（FR-ENG-021）。
 */
@Schema(description = "管理后台 - 到货签收新增/修改 Request VO")
@Data
public class ArrivalSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "签收编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ARR-2026-001")
    @NotBlank(message = "签收编码不能为空")
    private String code;

    @Schema(description = "到货时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "到货时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime arrivalTime;

    @Schema(description = "签收人", example = "1")
    private Long receiverUserId;

    @Schema(description = "关联设备编号", example = "1")
    private Long equipmentId;

    @Schema(description = "到货数量", example = "1")
    private Integer quantity;

    @Schema(description = "外观与清单检查结果")
    private String inspectionResult;

    @Schema(description = "异常记录")
    private String exceptionRecord;

    @Schema(description = "签收单附件")
    private String attachmentUrl;

    @Schema(description = "状态：0 待签收 1 已签收 2 异常", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
