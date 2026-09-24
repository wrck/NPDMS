package cn.iocoder.yudao.module.pms.asset.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 产品信息 Response VO")
@Data
public class ProductOfficialRespVO {

    @Schema(description = "产品信息ID", example = "1024")
    private Long id;

    @Schema(description = "产品编码", example = "01100003")
    private String productCode;

    @Schema(description = "产品名称", example = "DPtech IPS2000-MA-N")
    private String productName;

    @Schema(description = "产品型号", example = "IPS2000-MA-N+1Y")
    private String productModel;

    @Schema(description = "发布状态：ACTIVE 已发布 / FAST001_TEST_PUBLISHED 测试发布", example = "ACTIVE")
    private String status;
}
