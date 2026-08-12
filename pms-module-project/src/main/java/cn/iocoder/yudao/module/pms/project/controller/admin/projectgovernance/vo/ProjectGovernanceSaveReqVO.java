package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目治理动作创建/更新 Request VO")
@Data
public class ProjectGovernanceSaveReqVO {

    @Schema(description = "编号，更新时必填")
    private Long id;

    @Schema(description = "所属项目编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "治理动作单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "治理动作单号不能为空")
    private String actionNo;

    @Schema(description = "动作类型 ROLLBACK/DIRECT_CLOSE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    @Schema(description = "回退/关闭原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "原因不能为空")
    private String reason;

    @Schema(description = "证明材料文件URL列表（JSON数组）")
    private String proofFiles;

    @Schema(description = "申请人编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请人不能为空")
    private Long applicantUserId;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请时间不能为空")
    private LocalDateTime applyTime;

    @Schema(description = "备注")
    private String remark;

}
