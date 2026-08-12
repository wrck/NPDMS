package cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 团队批量变更批次 Response VO（FR-PROJ-014）。
 */
@Schema(description = "管理后台 - 团队批量变更批次 Response VO")
@Data
public class TeamBatchChangeRespVO {

    @Schema(description = "批次编号（主键）", example = "1024")
    private Long id;

    @Schema(description = "批次编号，全局唯一", example = "BC20260730001")
    private String batchNo;

    @Schema(description = "源用户编号", example = "1")
    private Long sourceUserId;

    @Schema(description = "目标用户编号", example = "2")
    private Long targetUserId;

    @Schema(description = "范围类型：ALL / SELECTED", example = "SELECTED")
    private String scopeType;

    @Schema(description = "变更原因", example = "人员离职角色移交")
    private String reason;

    @Schema(description = "状态：0处理中 1成功 2部分成功 3失败", example = "0")
    private Integer status;

    @Schema(description = "总条数", example = "10")
    private Integer totalCount;

    @Schema(description = "成功条数", example = "8")
    private Integer successCount;

    @Schema(description = "失败条数", example = "2")
    private Integer failureCount;

    @Schema(description = "备注", example = "本次移交仅限在建项目")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "变更明细列表")
    private List<TeamBatchChangeItemRespVO> items;
}
