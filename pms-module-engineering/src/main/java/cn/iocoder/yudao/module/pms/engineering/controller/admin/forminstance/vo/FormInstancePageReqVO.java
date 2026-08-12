package cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 准备数据表单实例分页 Request VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单实例分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormInstancePageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "关联模板ID", example = "1024")
    private Long templateId;

    @Schema(description = "实例编号，模糊匹配", example = "FI-2026")
    private String code;

    @Schema(description = "实例名称，模糊匹配", example = "防火墙")
    private String name;

    @Schema(description = "状态：0 待填 1 已填 2 已提交 3 已审核 4 已驳回", example = "0")
    private Integer status;

    @Schema(description = "填报人", example = "1024")
    private Long fillerUserId;

    @Schema(description = "审核人", example = "1024")
    private Long approverUserId;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
