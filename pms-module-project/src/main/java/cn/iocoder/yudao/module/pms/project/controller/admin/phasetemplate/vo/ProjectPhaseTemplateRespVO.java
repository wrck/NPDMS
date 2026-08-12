package cn.iocoder.yudao.module.pms.project.controller.admin.phasetemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - PMS 阶段模板 Response VO")
@Data
public class ProjectPhaseTemplateRespVO {

    @Schema(description = "模板编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "模板阶段名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求阶段")
    private String name;

    @Schema(description = "模板阶段编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "REQ")
    private String code;

    @Schema(description = "适用项目类型", example = "实施")
    private String projectType;

    @Schema(description = "描述", example = "需求调研与确认阶段")
    private String description;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "排序号", example = "0")
    private Integer sort;

    @Schema(description = "准入条件", example = "合同已签订")
    private String entryCriteria;

    @Schema(description = "退出条件", example = "需求文档已评审通过")
    private String exitCriteria;

    @Schema(description = "负责角色编码", example = "PROJECT_MANAGER")
    private String responsibleRole;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
