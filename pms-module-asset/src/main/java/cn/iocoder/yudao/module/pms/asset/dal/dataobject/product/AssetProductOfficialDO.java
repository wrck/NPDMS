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
}
