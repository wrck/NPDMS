package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目治理动作 Response VO")
@Data
public class ProjectGovernanceRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属项目编号")
    private Long projectId;

    @Schema(description = "治理动作单号")
    private String actionNo;

    @Schema(description = "动作类型 ROLLBACK/DIRECT_CLOSE")
    private String actionType;

    @Schema(description = "回退/关闭原因")
    private String reason;

    @Schema(description = "证明材料文件URL列表（JSON数组）")
    private String proofFiles;

    @Schema(description = "申请人编号")
    private Long applicantUserId;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "审批人编号")
    private Long approverUserId;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "审批意见")
    private String approveOpinion;

    @Schema(description = "执行前项目状态")
    private Integer beforeProjectStatus;

    @Schema(description = "执行后项目状态")
    private Integer afterProjectStatus;

    @Schema(description = "执行前项目经理")
    private Long beforeManagerUserId;

    @Schema(description = "执行后项目经理")
    private Long afterManagerUserId;

    @Schema(description = "执行时间")
    private LocalDateTime executeTime;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
