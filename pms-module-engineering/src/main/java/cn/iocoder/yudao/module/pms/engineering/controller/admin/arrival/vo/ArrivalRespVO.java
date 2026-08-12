package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 到货签收 Response VO（FR-ENG-021）。
 */
@Schema(description = "管理后台 - 到货签收 Response VO")
@Data
public class ArrivalRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "签收编码", example = "ARR-2026-001")
    private String code;

    @Schema(description = "到货时间")
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

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
