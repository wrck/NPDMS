package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - PMS 工程交底书生成 Request VO（FR-ENG-006）。
 * <p>
 * 触发系统按模板和前序基线数据快照生成交底书内容与文件。
 */
@Schema(description = "管理后台 - PMS 工程交底书生成 Request VO")
@Data
public class BriefingGenerateReqVO {

    @Schema(description = "交底书编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "交底书编号不能为空")
    private Long id;

    @Schema(description = "模板ID（覆盖已有模板关联）", example = "1024")
    private Long templateId;

    @Schema(description = "前序基线数据快照JSON（需求/方案/工勘聚合）")
    private String sourceSnapshot;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
