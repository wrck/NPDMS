package cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 实施方案新增/修改 Request VO（FR-ENG-011 / FR-ENG-013）。
 */
@Schema(description = "管理后台 - 实施方案新增/修改 Request VO")
@Data
public class SolutionSaveReqVO {

    @Schema(description = "主键，更新时必填", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "方案编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SOL-2026-001")
    @NotBlank(message = "方案编码不能为空")
    private String code;

    @Schema(description = "方案名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机替换方案")
    @NotBlank(message = "方案名称不能为空")
    private String name;

    @Schema(description = "方案类型", example = "IMPLEMENTATION")
    private String solutionType;

    @Schema(description = "关联文档模板ID（V36 结构化文档模板）", example = "40005")
    private Long templateId;

    @Schema(description = "关联模板版本ID", example = "40005")
    private Long templateVersionId;

    @Schema(description = "模板快照JSON（创建时的模板结构）")
    private String templateSnapshot;

    @Schema(description = "章节填写数据JSON（key=章节编码，value=章节内容）")
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

    @Schema(description = "状态：0 草稿 1 已提交 2 审批中 3 已通过 4 已驳回 5 已撤回 6 已终止", example = "0")
    private Integer status;

    @Schema(description = "审核人", example = "1")
    private Long approvedBy;

    @Schema(description = "审核时间")
    private java.time.LocalDateTime approvedTime;

    @Schema(description = "审核意见")
    private String approvalOpinion;

    @Schema(description = "基线版本号", example = "1")
    private Integer baselineVersion;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
