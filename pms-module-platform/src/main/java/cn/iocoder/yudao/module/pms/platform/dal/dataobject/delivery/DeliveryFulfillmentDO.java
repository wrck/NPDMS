package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 显式材料使用关系：退出一个要求不撤回其他要求正在使用的材料。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_fulfillment")
public class DeliveryFulfillmentDO extends TenantBaseDO {
    public static final String ACTIVE = "ACTIVE";
    public static final String WITHDRAWN = "WITHDRAWN";
    @TableId private Long id;
    private Long requirementId;
    private Long materialId;
    private String status;
}
