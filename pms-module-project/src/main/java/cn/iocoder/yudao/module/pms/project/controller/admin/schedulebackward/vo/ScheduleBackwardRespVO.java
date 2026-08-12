package cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 工期倒排记录 Response VO（FR-PROJ-018）。
 */
@Schema(description = "管理后台 - 工期倒排记录 Response VO")
@Data
public class ScheduleBackwardRespVO {

    @Schema(description = "倒排记录编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", example = "100")
    private Long projectId;

    @Schema(description = "目标完工日期", example = "2026-12-31")
    private LocalDate targetDate;

    @Schema(description = "项目类型：DIRECT 直签 / INDIRECT 非直签", example = "DIRECT")
    private String projectType;

    @Schema(description = "状态：0草稿 1已计算 2已应用 3已驳回", example = "0")
    private Integer status;

    @Schema(description = "冲突汇总", example = "阶段【需求调研】计划开始日期早于今天")
    private String conflictSummary;

    @Schema(description = "备注", example = "按客户要求年底完工")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "倒排阶段明细列表")
    private List<ScheduleBackwardItemRespVO> items;
}
