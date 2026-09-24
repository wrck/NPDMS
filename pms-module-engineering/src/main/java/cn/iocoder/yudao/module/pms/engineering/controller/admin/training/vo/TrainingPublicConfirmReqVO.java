package cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公开端 - 现场培训记录客户确认 Request VO（ACC-01）。
 * <p>
 * 三项评价的取值与 Demo 6.1 客户填写区域一致：
 * 技术水平/讲解效果：很好、良好、一般、差；培训满意度：非常满意、较满意、一般、差。
 */
@Schema(description = "公开端 - 现场培训记录客户确认 Request VO")
@Data
public class TrainingPublicConfirmReqVO {

    @Schema(description = "培训工程师技术水平及表达能力", example = "很好")
    @NotBlank(message = "请评价培训工程师技术水平及表达能力")
    private String skillRating;

    @Schema(description = "培训内容及讲解效果", example = "良好")
    @NotBlank(message = "请评价培训内容及讲解效果")
    private String effectRating;

    @Schema(description = "培训满意度", example = "非常满意")
    @NotBlank(message = "请评价培训满意度")
    private String satisfactionRating;

    @Schema(description = "综合意见")
    @Size(max = 500, message = "综合意见不能超过 500 个字符")
    private String signOpinion;

    @Schema(description = "签字人姓名（历史外发链接按冻结规则采集，新外发不再要求）", example = "张三")
    @Size(max = 64, message = "签字人姓名不能超过 64 个字符")
    private String signConfirmerName;
    @NotBlank(message = "请手写签字")
    @Size(max = 350000, message = "签字图片过大，请清空后重签")
    private String signatureImageDataUrl;

    @Size(max = 32768, message = "补充信息过长")
    private String confirmationValues;
}
