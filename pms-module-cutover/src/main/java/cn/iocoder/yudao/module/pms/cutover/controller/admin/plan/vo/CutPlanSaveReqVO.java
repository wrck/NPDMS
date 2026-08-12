package cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 割接方案新增/修改 Request VO（FR-CUT-008）。
 */
@Schema(description = "管理后台 - 割接方案新增/修改 Request VO")
@Data
public class CutPlanSaveReqVO {

    @Schema(description = "方案编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "割接任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "割接任务编号不能为空")
    private Long taskId;

    @Schema(description = "方案编码，任务内唯一且创建后不可变", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLN20260101001")
    @NotBlank(message = "方案编码不能为空")
    @Size(max = 64, message = "方案编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "方案名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "核心交换机替换方案")
    @NotBlank(message = "方案名称不能为空")
    @Size(max = 128, message = "方案名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "割接前检查项", example = "确认备用链路状态正常")
    private String preCheck;

    @Schema(description = "割接步骤", example = "1. 下线主用 2. 切换备用 3. 验证")
    private String procedure;

    @Schema(description = "业务测试与验证", example = "全量业务联调通过")
    private String verification;

    @Schema(description = "回退方案", example = "回退到割接前快照")
    private String rollback;

    @Schema(description = "方案等级 A/B/C/D", example = "C")
    private String level;

    @Schema(description = "备注", example = "需提前演练")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

    @Schema(description = "乐观锁版本号，修改时必填", example = "0")
    private Integer version;
}
