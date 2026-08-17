package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 项目模板 Response VO（F-PM03）
 */
@Schema(description = "管理后台 - 项目模板 Response VO")
@Data
public class ProjectTemplateRespVO {

    @Schema(description = "模板编号", example = "1")
    private Long id;

    @Schema(description = "模板编码（租户内唯一）", example = "TPL-STD-DELIVERY")
    private String code;

    @Schema(description = "模板名称", example = "标准交付模板")
    private String name;

    @Schema(description = "状态：DRAFT草稿/ACTIVE生效/RETIRED停用", example = "DRAFT")
    private String status;

    @Schema(description = "匹配优先级（数值小者先命中）", example = "100")
    private Integer matchPriority;

    @Schema(description = "业务场景描述", example = "适用于标准签约交付项目")
    private String description;

    @Schema(description = "系统保留编码标志：不得删除/复用/改义", example = "false")
    private Boolean systemReserved;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
