package cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 割接风险 Response VO（FR-CUT-004 / FR-CUT-006）。
 */
@Schema(description = "管理后台 - 割接风险 Response VO")
@Data
public class CutRiskRespVO {

    @Schema(description = "风险编号", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", example = "1024")
    private Long taskId;

    @Schema(description = "风险编码", example = "RSK20260101001")
    private String code;

    @Schema(description = "风险名称", example = "业务中断风险")
    private String name;

    @Schema(description = "类型 RISK/SURVEY", example = "RISK")
    private String riskType;

    @Schema(description = "风险描述", example = "割接期间业务流量可能中断")
    private String description;

    @Schema(description = "影响分析", example = "影响核心交易系统 5 分钟")
    private String impact;

    @Schema(description = "缓解措施", example = "提前切换备用链路")
    private String mitigation;

    @Schema(description = "责任人编号", example = "1024")
    private Long ownerUserId;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "需二线确认")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
