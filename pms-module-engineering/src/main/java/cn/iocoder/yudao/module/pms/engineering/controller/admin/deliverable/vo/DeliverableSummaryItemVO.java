package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按项目交付件汇总条目（6.4 / ACC-04）。
 * <p>
 * category 取值：RECEIPT 到货签收单 / SCHEME 实施方案 / PRELIMINARY 初验报告 / FINAL 终验报告 /
 * TRAINING 现场培训记录 / SATISFACTION 满意度调查报告 / OTHER 其他工程归集。
 */
@Schema(description = "管理后台 - 按项目交付件汇总条目")
@Data
public class DeliverableSummaryItemVO {

    @Schema(description = "汇总类别", requiredMode = Schema.RequiredMode.REQUIRED, example = "SCHEME")
    private String category;

    @Schema(description = "交付件编码", example = "SOLUTION-SOL-001")
    private String code;

    @Schema(description = "名称", example = "XX项目实施方案（基线v3）")
    private String name;

    @Schema(description = "来源说明", example = "4.1 审批通过自动归档")
    private String sourceLabel;

    @Schema(description = "状态：0 待归集 1 已归集 2 已作废；ACC 归档件为 null", example = "1")
    private Integer status;

    @Schema(description = "归档/生效时间")
    private LocalDateTime archivedTime;

    @Schema(description = "文件地址（无文件时为空，走详情页查看）")
    private String fileUrl;

    @Schema(description = "来源类型", example = "SOLUTION")
    private String sourceType;

    @Schema(description = "来源编号", example = "1024")
    private Long sourceId;

    @Schema(description = "备注（含未接入说明）", example = "到货签收单自动归档在途：EXE-01 切片承接中")
    private String remark;
}
