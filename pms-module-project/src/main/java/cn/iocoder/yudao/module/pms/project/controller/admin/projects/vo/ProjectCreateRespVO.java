package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手工创建项目 Response VO（创建结果与实例化摘要，幂等重放原样返回）
 */
@Schema(description = "管理后台 - 项目手工创建 Response VO")
@Data
public class ProjectCreateRespVO {

    @Schema(description = "项目ID", example = "1")
    private Long id;

    @Schema(description = "项目编码（租户内唯一，创建后不可变）", example = "PJT2026000001")
    private String projectCode;

    @Schema(description = "项目状态（初始 S0）", example = "S0")
    private String status;

    @Schema(description = "冻结的生命周期模板ID", example = "910001")
    private Long lifecycleTemplateId;

    @Schema(description = "冻结的模板版本号", example = "2")
    private Integer lifecycleTemplateRevisionNo;

    @Schema(description = "模板加载方式：AUTO_DEFAULT/MANUAL_SELECTED", example = "AUTO_DEFAULT")
    private String templateLoadMethod;
}
