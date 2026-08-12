package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 实施方案 Response VO（FR-ENG-011 / FR-ENG-013）。
 */
@Schema(description = "管理后台 - 实施方案 Response VO")
@Data
public class SolutionRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "方案编码", example = "SOL-2026-001")
    private String code;

    @Schema(description = "方案名称", example = "核心交换机替换方案")
    private String name;

    @Schema(description = "方案类型", example = "IMPLEMENTATION")
    private String solutionType;

    @Schema(description = "关联文档模板ID", example = "40005")
    private Long templateId;

    @Schema(description = "关联模板版本ID", example = "40005")
    private Long templateVersionId;

    @Schema(description = "模板快照JSON")
    private String templateSnapshot;

    @Schema(description = "章节填写数据JSON")
    private String sectionData;

    @Schema(description = "背景")
    private String background;

    @Schema(description = "目标")
    private String target;

    @Schema(description = "团队")
    private String team;

    @Schema(description = "清单")
    private String inventory;

    @Schema(description = "计划")
    private String plan;

    @Schema(description = "拓扑")
    private String topology;

    @Schema(description = "接口")
    private String interfacePlan;

    @Schema(description = "IP")
    private String ipPlan;

    @Schema(description = "版本标签", example = "v1.0")
    private String versionLabel;

    @Schema(description = "脚本")
    private String script;

    @Schema(description = "质量")
    private String quality;

    @Schema(description = "风险")
    private String risk;

    @Schema(description = "运维")
    private String oAndM;

    @Schema(description = "审核级别 0 普通 1 重大", example = "0")
    private Integer reviewLevel;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "审核人", example = "1")
    private Long approvedBy;

    @Schema(description = "审核时间")
    private LocalDateTime approvedTime;

    @Schema(description = "审核意见")
    private String approvalOpinion;

    @Schema(description = "基线版本号", example = "1")
    private Integer baselineVersion;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
