package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目授权分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectAuthorizationPageReqVO extends PageParam {

    @Schema(description = "被授权用户ID")
    @Positive(message = "被授权用户无效")
    private Long subjectUserId;

    @Schema(description = "动作：PROJECT_VIEW/PROJECT_MANAGE")
    @Pattern(regexp = "PROJECT_VIEW|PROJECT_MANAGE", message = "授权动作无效")
    private String actionCode;

    @Schema(description = "范围：CURRENT_PROJECT/PROJECT_AND_DESCENDANTS")
    @Pattern(regexp = "CURRENT_PROJECT|PROJECT_AND_DESCENDANTS", message = "授权范围无效")
    private String scopeCode;

    @Schema(description = "状态：ACTIVE/REVOKED/EXPIRED")
    @Pattern(regexp = "ACTIVE|REVOKED|EXPIRED", message = "授权状态无效")
    private String statusCode;

    @Schema(description = "有效时点")
    private LocalDateTime effectiveAt;

    @AssertTrue(message = "每页条数不能超过100")
    @Schema(hidden = true)
    public boolean isPageSizeWithinLimit() {
        return getPageSize() != null && getPageSize() <= 100;
    }
}
