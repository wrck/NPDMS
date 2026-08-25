package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("ast_site_location")
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteLocationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long siteId;
    private Long parentId;
    private String code;
    private String name;
    private String locationType;
    private String treePath;
    private Integer treeDepth;
    private Integer treeSort;
    private Integer status;
    private Integer version;

}
