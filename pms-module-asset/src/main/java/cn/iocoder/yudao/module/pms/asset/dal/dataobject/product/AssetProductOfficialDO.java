package cn.iocoder.yudao.module.pms.asset.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** CRM 同步目录；获批准的 DPPMS 导入仅补缺失产品，既有目录字段保持来源权威。 */
@TableName("ast_product_official_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductOfficialDO extends BaseBusinessEntity {

    private String productCode;
    private String productName;
    private String productModel;
    private String productDesc;
    private String technicalSpec;
    private String status;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    private String syncStatus;

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
