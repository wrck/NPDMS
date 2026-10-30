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
    private String productDesc;
    private String technicalSpec;

    @Schema(description = "发布状态：ACTIVE 已发布 / FAST001_TEST_PUBLISHED 测试发布", example = "ACTIVE")
    private String status;

    /** 产品分类（CRM productCode） */
    private String productCategory;
    /** 产品类型（CRM productSubCode） */
    private String productType;
    /** CRM listPrice */
    private java.math.BigDecimal listPrice;
    /** CRM status */
    private Integer crmStatus;
    /** CRM BU */
    private String businessUnit;
    /** CRM productLine */
    private String productLine;
    /** CRM orgId */
    private Integer orgId;
    /** CRM createTime */
    private java.time.LocalDateTime sourceCreatedAt;
    /** CRM updateTime */
    private java.time.LocalDateTime sourceUpdatedAt;
    /** CRM statecode */
    private Integer crmStateCode;
    /** CRM statuscode */
    private Integer crmStatusCode;
    /** CRM productStage */
    private Integer productStage;
    /** CRM endOfSaleDate */
    private java.time.LocalDateTime endOfSaleDate;
    /** CRM endOfSupportDate */
    private java.time.LocalDateTime endOfSupportDate;
    /** CRM endOfLifeDate */
    private java.time.LocalDateTime endOfLifeDate;
    /** CRM lastRenewalDate */
    private java.time.LocalDateTime lastRenewalDate;
}
