package cn.iocoder.yudao.module.pms.asset.dal.dataobject.product;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 产品信息只读副本：由集成同步按来源写入（generic-targets），资产域不承载其领域写入。 */
@TableName("ast_product_official_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class AssetProductOfficialDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String productCode;
    private String productName;
    private String productModel;
    private String productDesc;
    private String technicalSpec;
    private String status;
}
