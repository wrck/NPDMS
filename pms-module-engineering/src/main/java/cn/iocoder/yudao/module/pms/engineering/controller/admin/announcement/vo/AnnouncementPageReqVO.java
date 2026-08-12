package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 技术公告分页 Request VO（FR-ENG-009）。
 */
@Schema(description = "管理后台 - PMS 技术公告分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementPageReqVO extends PageParam {

    @Schema(description = "公告编号，模糊匹配", example = "TA-2026")
    private String code;

    @Schema(description = "公告标题，模糊匹配", example = "停产")
    private String title;

    @Schema(description = "公告类型：TECH_NOTICE/EOS/EOM", example = "EOS")
    private String announcementType;

    @Schema(description = "适用设备型号，模糊匹配", example = "FW-2000")
    private String productModel;

    @Schema(description = "严重等级", example = "HIGH")
    private String severity;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "1")
    private Integer status;

    @Schema(description = "发布日期区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDate[] publishDateRange;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
