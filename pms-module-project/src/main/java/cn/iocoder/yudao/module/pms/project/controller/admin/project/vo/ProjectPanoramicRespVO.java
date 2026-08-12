package cn.iocoder.yudao.module.pms.project.controller.admin.project.vo;

import cn.iocoder.yudao.module.pms.project.controller.admin.projectteam.vo.ProjectTeamMemberRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台 - 项目全景 Response VO（FR-PROJ-011 / FR-PROJ-021 / T-V1-PROJ-009）。
 * <p>
 * 汇聚项目基本信息、客户信息、阶段汇总、任务汇总、风险汇总与团队成员列表。
 */
@Schema(description = "管理后台 - 项目全景 Response VO")
@Data
public class ProjectPanoramicRespVO {

    // ============ 项目基本信息 ============

    @Schema(description = "项目编号", example = "1024")
    private Long id;
    @Schema(description = "项目编码", example = "P20260101001")
    private String code;
    @Schema(description = "项目名称", example = "某交付项目一期")
    private String name;
    @Schema(description = "项目分类", example = "交付类")
    private String category;
    @Schema(description = "项目类型", example = "交付类")
    private String projectType;
    @Schema(description = "重大项目标识", example = "false")
    private Boolean majorProjectFlag;
    @Schema(description = "项目经理用户编号", example = "1")
    private Long managerUserId;
    @Schema(description = "项目状态", example = "0")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // ============ 客户信息 ============

    @Schema(description = "客户编号", example = "100")
    private Long customerId;
    @Schema(description = "客户编码", example = "C20260101001")
    private String customerCode;
    @Schema(description = "客户名称", example = "上海某某有限公司")
    private String customerName;

    // ============ 阶段汇总 ============

    @Schema(description = "阶段总数", example = "5")
    private Integer phaseTotalCount;
    @Schema(description = "未开始阶段数", example = "2")
    private Integer phaseNotStartedCount;
    @Schema(description = "进行中阶段数", example = "1")
    private Integer phaseInProgressCount;
    @Schema(description = "已完成阶段数", example = "2")
    private Integer phaseCompletedCount;
    @Schema(description = "已跳过阶段数", example = "0")
    private Integer phaseSkippedCount;

    // ============ 任务汇总 ============

    @Schema(description = "任务总数", example = "20")
    private Integer taskTotalCount;
    @Schema(description = "已完成任务数", example = "8")
    private Integer taskCompletedCount;
    @Schema(description = "进行中任务数", example = "5")
    private Integer taskInProgressCount;
    @Schema(description = "受阻任务数", example = "1")
    private Integer taskBlockedCount;

    // ============ 风险汇总 ============

    @Schema(description = "风险总数", example = "3")
    private Integer riskTotalCount;
    @Schema(description = "高风险数", example = "1")
    private Integer riskHighCount;
    @Schema(description = "中风险数", example = "1")
    private Integer riskMediumCount;
    @Schema(description = "低风险数", example = "1")
    private Integer riskLowCount;
    @Schema(description = "已识别风险数", example = "1")
    private Integer riskIdentifiedCount;
    @Schema(description = "处理中风险数", example = "1")
    private Integer riskInProgressCount;
    @Schema(description = "已关闭风险数", example = "1")
    private Integer riskClosedCount;
    @Schema(description = "已发生风险数", example = "0")
    private Integer riskOccurredCount;

    // ============ 团队成员 ============

    @Schema(description = "团队成员列表")
    private List<ProjectTeamMemberRespVO> teamMembers = Collections.emptyList();
}
