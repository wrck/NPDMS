package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 巡检问题整改/验证 Request VO")
@Data
public class SrvIssueActionReqVO {

    @Schema(description = "问题编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "问题编号不能为空")
    private Long id;

    @Schema(description = "整改方案")
    private String solution;

    @Schema(description = "验证结果")
    private String verifyResult;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
