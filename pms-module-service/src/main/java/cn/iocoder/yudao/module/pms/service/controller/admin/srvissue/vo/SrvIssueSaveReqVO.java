package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检问题创建/修改 Request VO")
@Data
public class SrvIssueSaveReqVO {

    @Schema(description = "问题编号", example = "1024")
    private Long id;

    @Schema(description = "所属巡检任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "所属巡检任务编号不能为空")
    private Long taskId;

    @Schema(description = "问题编码，任务内唯一", requiredMode = Schema.RequiredMode.REQUIRED, example = "ISS-001")
    @NotBlank(message = "问题编码不能为空")
    @Size(max = 64, message = "问题编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "问题名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "端口异常")
    @NotBlank(message = "问题名称不能为空")
    @Size(max = 255, message = "问题名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "问题描述")
    private String description;

    @Schema(description = "严重程度 H 高 / M 中 / L 低", example = "M")
    private String severity;

    @Schema(description = "责任人", example = "300")
    private Long ownerUserId;

    @Schema(description = "整改截止时间")
    private LocalDateTime deadline;

    @Schema(description = "整改方案")
    private String solution;

    @Schema(description = "验证结果")
    private String verifyResult;

    @Schema(description = "验证人", example = "300")
    private Long verifiedBy;

    @Schema(description = "验证时间")
    private LocalDateTime verifiedTime;

    @Schema(description = "状态 0待分派 1已分派 2待验证 3已关闭 4已取消", example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
