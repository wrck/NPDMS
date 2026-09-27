package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ast_site")
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteDO extends BaseBusinessEntity {

    private String code;
    private String name;
    private Long customerId;
    private Long addressId;
    private String siteType;
    private Integer status;

}
