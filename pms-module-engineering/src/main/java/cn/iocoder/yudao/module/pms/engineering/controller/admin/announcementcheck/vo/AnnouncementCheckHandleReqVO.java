package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - PMS 公告预检查处置 Request VO（FR-ENG-009）。
 * <p>
 * 用于检查记录状态推进：已检查 → 已处置 / 已忽略。
 */
@Schema(description = "管理后台 - PMS 公告预检查处置 Request VO")
@Data
public class AnnouncementCheckHandleReqVO {

    @Schema(description = "检查记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "检查记录ID不能为空")
    private Long id;

    @Schema(description = "处理意见")
    @Size(max = 500, message = "处理意见长度不能超过 500 个字符")
    private String handleOpinion;

    @Schema(description = "处置动作：HANDLE 处置 / IGNORE 忽略", example = "HANDLE")
    private String handleAction;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
