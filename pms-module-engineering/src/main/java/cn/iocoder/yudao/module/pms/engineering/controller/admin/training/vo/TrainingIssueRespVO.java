package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台 - 现场培训记录外发 Response VO（ACC-01）。
 * <p>
 * 原始令牌仅在本响应返回一次，服务端只保存 SHA-256 摘要；
 * 外部推送通道（短信/钉钉）未接入，调用方需线下将链接送达客户。
 */
@Schema(description = "管理后台 - 现场培训记录外发 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingIssueRespVO {

    @Schema(description = "培训记录编号", example = "1024")
    private Long id;

    @Schema(description = "外发令牌（仅本次返回）")
    private String token;

    @Schema(description = "确认链接路径：/training-records/{token}", example = "/training-records/abc")
    private String signPath;

    @Schema(description = "令牌有效期")
    private LocalDateTime tokenExpiresAt;

    @Schema(description = "培训记录表文件URL")
    private String fileUrl;

    @Schema(description = "外发提示（推送通道未接入说明）")
    private String notice;
}
