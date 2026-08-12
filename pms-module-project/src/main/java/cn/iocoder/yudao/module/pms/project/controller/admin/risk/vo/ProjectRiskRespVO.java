package cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 项目风险 Response VO（FR-PROJ-026）。
 */
@Schema(description = "管理后台 - 项目风险 Response VO")
@Data
public class ProjectRiskRespVO {

    @Schema(description = "风险编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目编号", example = "100")
    private Long projectId;

    @Schema(description = "风险标题", example = "需求频繁变更风险")
    private String title;

    @Schema(description = "风险等级 HIGH/MEDIUM/LOW", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险类型", example = "需求风险")
    private String riskType;

    @Schema(description = "风险原因", example = "客户业务流程未明确")
    private String cause;

    @Schema(description = "风险影响", example = "可能影响交付进度")
    private String impact;

    @Schema(description = "缓解措施", example = "增加需求评审频次")
    private String mitigation;

    @Schema(description = "应急措施", example = "启用备用方案")
    private String contingency;

    @Schema(description = "风险负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "状态：0 已识别 1 处理中 2 已关闭 3 已发生", example = "0")
    private Integer status;

    @Schema(description = "预警阈值", example = "影响 3 个及以上里程碑")
    private String warningThreshold;

    @Schema(description = "复核备注", example = "本月已复核")
    private String reviewNotes;

    @Schema(description = "识别时间")
    private LocalDateTime identifiedAt;

    @Schema(description = "关闭时间")
    private LocalDateTime closedAt;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
