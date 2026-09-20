package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公开端 - 现场培训记录查看 Response VO（ACC-01，无鉴权令牌访问）。
 */
@Schema(description = "公开端 - 现场培训记录查看 Response VO")
@Data
public class TrainingPublicRespVO {

    @Schema(description = "培训记录编码", example = "TR-2026-001")
    private String code;

    @Schema(description = "培训名称", example = "设备运维培训")
    private String name;

    @Schema(description = "培训类型名称，逗号分隔（已翻译）", example = "技术原理类,产品运维类")
    private String trainingTypeLabels;

    @Schema(description = "培训时间")
    private LocalDate trainingTime;

    @Schema(description = "培训工程师姓名")
    private String trainerName;

    @Schema(description = "培训内容")
    private String content;

    @Schema(description = "令牌有效期")
    private LocalDateTime tokenExpiresAt;

    @Schema(description = "状态：1已外发（待确认） 2客户已确认")
    private Integer status;

    @Schema(description = "客户签字人（已确认时返回）")
    private String signConfirmerName;

    @Schema(description = "客户确认时间（已确认时返回）")
    private LocalDateTime signTime;

    @Schema(description = "客户评价：培训工程师技术水平及表达能力（已确认时返回）")
    private String skillRating;

    @Schema(description = "客户评价：培训内容及讲解效果（已确认时返回）")
    private String effectRating;

    @Schema(description = "客户评价：培训满意度（已确认时返回）")
    private String satisfactionRating;

    @Schema(description = "客户综合意见（已确认时返回）")
    private String signOpinion;
}
