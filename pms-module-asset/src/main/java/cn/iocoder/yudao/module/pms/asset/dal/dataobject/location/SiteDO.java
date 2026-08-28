package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ast_site")
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String code;
    private String name;
    private Long customerId;
    private Long addressId;
    private String siteType;
    private Integer status;
    private Integer version;

}
