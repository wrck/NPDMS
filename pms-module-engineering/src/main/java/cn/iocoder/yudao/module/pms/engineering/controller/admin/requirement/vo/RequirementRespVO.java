package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 需求分析 Response VO（FR-ENG-004）。
 */
@Schema(description = "管理后台 - 需求分析 Response VO")
@Data
public class RequirementRespVO {

    @Schema(description = "主键", example = "1")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "需求编码", example = "REQ-2026-001")
    private String code;

    @Schema(description = "需求名称", example = "核心网扩容需求")
    private String name;

    @Schema(description = "需求类型 BUSINESS / INTERFACE", example = "BUSINESS")
    private String requirementType;

    @Schema(description = "关联文档模板ID", example = "40001")
    private Long templateId;

    @Schema(description = "关联模板版本ID", example = "40001")
    private Long templateVersionId;

    @Schema(description = "模板快照JSON")
    private String templateSnapshot;

    @Schema(description = "章节填写数据JSON")
    private String sectionData;

    @Schema(description = "项目背景目标")
    private String background;

    @Schema(description = "拓扑")
    private String topology;

    @Schema(description = "传输")
    private String transmission;

    @Schema(description = "流量")
    private String traffic;

    @Schema(description = "业务")
    private String business;

    @Schema(description = "IP 规划")
    private String ipPlan;

    @Schema(description = "冗余")
    private String redundancy;

    @Schema(description = "防护")
    private String protection;

    @Schema(description = "运维")
    private String oAndM;

    @Schema(description = "日志留存")
    private String logRetention;

    @Schema(description = "接口关系内容")
    private String interfaceContent;

    @Schema(description = "状态：0 草稿 1 已提交 2 已生效 3 已归档", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
