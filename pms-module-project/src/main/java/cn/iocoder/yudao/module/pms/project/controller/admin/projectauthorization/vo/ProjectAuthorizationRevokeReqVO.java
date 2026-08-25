package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 撤销项目授权 Request VO")
@Data
public class ProjectAuthorizationRevokeReqVO {

    @Schema(description = "撤权原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "撤权原因不能为空")
    @Size(max = 500, message = "撤权原因不能超过500字")
    private String reason;
}
