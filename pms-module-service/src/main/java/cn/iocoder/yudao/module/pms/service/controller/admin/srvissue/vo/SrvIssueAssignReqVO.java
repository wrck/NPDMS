package cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 巡检问题分派 Request VO")
@Data
public class SrvIssueAssignReqVO {

    @Schema(description = "问题编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "问题编号不能为空")
    private Long id;

    @Schema(description = "责任人", requiredMode = Schema.RequiredMode.REQUIRED, example = "300")
    @NotNull(message = "责任人不能为空")
    private Long ownerUserId;

    @Schema(description = "整改截止时间")
    private LocalDateTime deadline;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

}
