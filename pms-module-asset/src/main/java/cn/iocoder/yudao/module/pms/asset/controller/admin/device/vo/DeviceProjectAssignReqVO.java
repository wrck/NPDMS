package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 设备项目归属 Request VO")
@Data
public class DeviceProjectAssignReqVO {

    @Schema(description = "目标项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标项目不能为空")
    private Long projectId;

    @Schema(description = "变更原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更原因不能为空")
    @Size(max = 500, message = "变更原因不能超过500个字符")
    private String reason;
}
