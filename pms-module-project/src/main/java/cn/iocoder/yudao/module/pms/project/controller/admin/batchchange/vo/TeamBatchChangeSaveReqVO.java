package cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 团队批量变更新增/修改 Request VO（FR-PROJ-014）。
 */
@Schema(description = "管理后台 - 团队批量变更新增/修改 Request VO")
@Data
public class TeamBatchChangeSaveReqVO {

    @Schema(description = "批次编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "源用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "源用户编号不能为空")
    private Long sourceUserId;

    @Schema(description = "目标用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "目标用户编号不能为空")
    private Long targetUserId;

    @Schema(description = "范围类型：ALL 全部项目 / SELECTED 指定项目",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "SELECTED")
    @NotBlank(message = "范围类型不能为空")
    private String scopeType;

    @Schema(description = "指定项目编号列表（scopeType=SELECTED 时生效）", example = "[100,101]")
    private List<Long> projectIds;

    @Schema(description = "变更原因", example = "人员离职角色移交")
    @Size(max = 500, message = "变更原因长度不能超过 500 个字符")
    private String reason;

    @Schema(description = "备注", example = "本次移交仅限在建项目")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;

}
