package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 工程交底书分页 Request VO（FR-ENG-006）。
 */
@Schema(description = "管理后台 - PMS 工程交底书分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BriefingPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "交底书编号，模糊匹配", example = "BR-2026")
    private String code;

    @Schema(description = "交底书名称，模糊匹配", example = "网络安全")
    private String name;

    @Schema(description = "交底类型", example = "STANDARD")
    private String briefingType;

    @Schema(description = "状态：0 草稿 1 已生成 2 已审核 3 已发布 4 已作废", example = "0")
    private Integer status;

    @Schema(description = "编制人", example = "1024")
    private Long creatorUserId;

    @Schema(description = "审核人", example = "1024")
    private Long approverUserId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
