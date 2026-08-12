package cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 客户精简 Response VO，用于下拉选择。
 */
@Schema(description = "管理后台 - 客户精简 Response VO")
@Data
public class CustomerSimpleRespVO {

    @Schema(description = "客户编号", example = "1024")
    private Long id;

    @Schema(description = "客户编码", example = "C20260101001")
    private String code;

    @Schema(description = "客户名称", example = "上海某某有限公司")
    private String name;
}
