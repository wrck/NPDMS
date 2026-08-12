package cn.iocoder.yudao.module.pms.engineering.controller.admin.formtemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - PMS 准备数据表单模板 Response VO（FR-ENG-007）。
 */
@Schema(description = "管理后台 - PMS 准备数据表单模板 Response VO")
@Data
public class FormTemplateRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "模板编号", example = "FT-2026-001")
    private String code;

    @Schema(description = "模板名称", example = "网络安全设备标准采集表单")
    private String name;

    @Schema(description = "产品类型", example = "FIREWALL")
    private String productType;

    @Schema(description = "表单配置JSON")
    private String conf;

    @Schema(description = "表单字段JSON")
    private String fields;

    @Schema(description = "模板说明")
    private String description;

    @Schema(description = "状态：0 草稿 1 已发布 2 已停用", example = "0")
    private Integer status;

    @Schema(description = "模板版本号", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
