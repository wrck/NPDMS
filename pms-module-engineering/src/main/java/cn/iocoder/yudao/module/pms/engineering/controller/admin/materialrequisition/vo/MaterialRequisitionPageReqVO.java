package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS OA领料申请分页 Request VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS OA领料申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialRequisitionPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "领料单号，模糊匹配", example = "MR2026")
    private String code;

    @Schema(description = "领料名称，模糊匹配", example = "光模块")
    private String name;

    @Schema(description = "领料类型", example = "SPARE")
    private String requisitionType;

    @Schema(description = "关联设备编号", example = "1024")
    private Long equipmentId;

    @Schema(description = "库存状态：IN_STOCK 有库存 / OUT_OF_STOCK 无库存 / RESERVED 已预留", example = "IN_STOCK")
    private String stockStatus;

    @Schema(description = "状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "申请人编号", example = "1024")
    private Long applicantUserId;

    @Schema(description = "触发来源", example = "MANUAL")
    private String triggerSource;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
