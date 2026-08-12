package cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 准备数据表单模板分页 Request VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormTemplatePageReqVO extends PageParam {

    @Schema(description = "模板编号，模糊匹配", example = "FT-2026")
    private String code;

    @Schema(description = "模板名称，模糊匹配", example = "网络安全")
    private String name;

    @Schema(description = "产品类型", example = "FIREWALL")
    private String productType;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
