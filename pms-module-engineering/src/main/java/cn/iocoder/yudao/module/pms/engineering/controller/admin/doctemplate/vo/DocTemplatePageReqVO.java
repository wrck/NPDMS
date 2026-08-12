package cn.iocoder.yudao.module.pms.engineering.controller.admin.doctemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 工程文档模板分页 Request VO（V36 结构化文档模板）。
 */
@Schema(description = "管理后台 - PMS 工程文档模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DocTemplatePageReqVO extends PageParam {

    @Schema(description = "模板编号，模糊匹配", example = "DT-REQ")
    private String code;

    @Schema(description = "模板名称，模糊匹配", example = "需求分析")
    private String name;

    @Schema(description = "文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案", example = "REQUIREMENT")
    private String docCategory;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
