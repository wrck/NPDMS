package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 交付能力适用配置：为任意实体声明可用材料类型与默认要求；面板/要求实例由此生成。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_capability_config")
public class DeliveryCapabilityConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String ownerModule;
    private String entityType;
    private String typeCode;
    private Boolean required;
    private Integer minimumQuantity;
    /** MATERIAL / FILE_VERSION / SUBMISSION，取值约束见 DeliveryTypeDO 计数常量。 */
    private String countingUnit;
    private Boolean enabled;
}
