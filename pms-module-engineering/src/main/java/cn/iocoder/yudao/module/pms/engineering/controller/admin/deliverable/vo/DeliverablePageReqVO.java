package cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 阶段交付件归集分页 Request VO（FR-ENG-027）。
 */
@Schema(description = "管理后台 - 阶段交付件归集分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverablePageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "阶段编号", example = "3010")
    private Long phaseId;

    @Schema(description = "交付件编码，模糊匹配", example = "D2026")
    private String code;

    @Schema(description = "交付件名称，模糊匹配", example = "日报")
    private String name;

    @Schema(description = "类型 DAILY / RECEIPT / SERVICE / COMPLETION / TEST / CONFIG", example = "DAILY")
    private String deliverableType;

    @Schema(description = "来源业务类型", example = "INSTALLATION")
    private String sourceType;

    @Schema(description = "来源业务编号", example = "1024")
    private Long sourceId;

    @Schema(description = "状态：0待归集 1已归集 2已作废", example = "1")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
