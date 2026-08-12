package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 项目组合成员 Response VO")
@Data
public class ProjectPortfolioMemberRespVO {

    @Schema(description = "成员编号", example = "1024")
    private Long id;

    @Schema(description = "组合编号", example = "1")
    private Long portfolioId;

    @Schema(description = "项目编号", example = "10")
    private Long projectId;

    @Schema(description = "项目编码", example = "PRJ001")
    private String projectCode;

    @Schema(description = "项目名称", example = "智慧园区项目")
    private String projectName;

    @Schema(description = "纳入类型 STATIC 静态 / DYNAMIC 动态", example = "STATIC")
    private String inclusionType;

    @Schema(description = "纳入原因", example = "手动选择")
    private String inclusionReason;

    @Schema(description = "排除原因", example = "不符合规则")
    private String exclusionReason;

    @Schema(description = "状态：1纳入 2排除", example = "1")
    private Integer status;

    @Schema(description = "乐观锁版本", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
