package cn.iocoder.yudao.module.pms.project.controller.admin.planchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 计划变更创建/更新 Request VO")
@Data
public class PlanChangeSaveReqVO {

    @Schema(description = "编号，更新时必填")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "变更单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更单号不能为空")
    private String changeNo;

    @Schema(description = "变更标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更标题不能为空")
    private String title;

    @Schema(description = "变更类型 PLAN_ADJUST/SCOPE_CHANGE/DATE_SHIFT/OTHER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    @Schema(description = "变更原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更原因不能为空")
    private String reason;

    @Schema(description = "客户证明材料文件URL列表（JSON数组）")
    private String customerProofFiles;

    @Schema(description = "申请人编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请人不能为空")
    private Long applicantUserId;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请时间不能为空")
    private LocalDateTime applyTime;

    @Schema(description = "当前基线版本号")
    private Integer baselineVersion;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "阶段快照列表，至少一条")
    @NotEmpty(message = "至少包含一条阶段快照")
    private List<PlanChangePhaseSnapshotItem> phaseSnapshots;

}
