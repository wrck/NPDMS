package cn.iocoder.yudao.module.pms.acceptance.controller.admin.completioncertificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 电子完工证明设备明细 Response VO")
@Data
public class CompletionCertificateDeviceRespVO {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "设备类型", example = "智能电网安全防护设备")
    private String deviceType;

    @Schema(description = "设备型号", example = "DPtech-LPH-9000")
    private String deviceModel;

    @Schema(description = "数量", example = "1")
    private Integer quantity;

}
