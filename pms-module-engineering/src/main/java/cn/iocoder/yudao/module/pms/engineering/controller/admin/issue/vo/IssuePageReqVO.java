package cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 管理后台 - 实施问题分页 Request VO（FR-ENG-026）。
 */
@Schema(description = "管理后台 - 实施问题分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class IssuePageReqVO extends PageParam {

    @Schema(description = "项目编号", example = "2048")
    private Long projectId;

    @Schema(description = "问题编码，模糊匹配", example = "IS2026")
    private String code;

    @Schema(description = "问题名称，模糊匹配", example = "切换")
    private String name;

    @Schema(description = "来源 INSTALLATION / CONFIGURATION / JOINT_TEST / OTHER", example = "JOINT_TEST")
    private String source;

    @Schema(description = "严重等级 1低 2中 3高", example = "2")
    private Integer severity;

    @Schema(description = "状态：0待处理 1整改中 2待验证 3已关闭 4已挂起", example = "0")
    private Integer status;

    @Schema(description = "责任人编号", example = "1024")
    private Long ownerUserId;

    @Schema(description = "整改时限区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] deadline;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
