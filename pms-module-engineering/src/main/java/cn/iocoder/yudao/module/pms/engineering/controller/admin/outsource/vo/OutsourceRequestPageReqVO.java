package cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - PMS 外包申请分页 Request VO（FR-ENG-002）。
 */
@Schema(description = "管理后台 - PMS 外包申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class OutsourceRequestPageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "外包单号，模糊匹配", example = "OS2026")
    private String code;

    @Schema(description = "外包名称，模糊匹配", example = "驻场运维")
    private String name;

    @Schema(description = "外包类型", example = "LABOR")
    private String outsourceType;

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
