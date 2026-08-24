package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 创建项目授权 Request VO")
@Data
public class ProjectAuthorizationCreateReqVO {

    @Schema(description = "被授权用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "被授权用户不能为空")
    @Positive(message = "被授权用户无效")
    private Long subjectUserId;

    @Schema(description = "动作：PROJECT_VIEW/PROJECT_MANAGE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "授权动作不能为空")
    @Pattern(regexp = "PROJECT_VIEW|PROJECT_MANAGE", message = "授权动作无效")
    private String actionCode;

    @Schema(description = "范围：CURRENT_PROJECT/PROJECT_AND_DESCENDANTS",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "授权范围不能为空")
    @Pattern(regexp = "CURRENT_PROJECT|PROJECT_AND_DESCENDANTS", message = "授权范围无效")
    private String scopeCode;

    @Schema(description = "生效时间；为空时服务端取当前时间")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间；为空表示长期有效")
    private LocalDateTime effectiveTo;

    @Schema(description = "授权原因")
    @Size(max = 500, message = "授权原因不能超过500字")
    private String reason;

    @AssertTrue(message = "授权失效时间必须晚于生效时间")
    @Schema(hidden = true)
    public boolean isEffectiveIntervalValid() {
        return effectiveFrom == null || effectiveTo == null || effectiveTo.isAfter(effectiveFrom);
    }
}
