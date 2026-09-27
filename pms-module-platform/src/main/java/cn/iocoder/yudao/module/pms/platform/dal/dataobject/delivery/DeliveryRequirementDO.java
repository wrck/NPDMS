package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 交付要求实例：按（来源实体 + 类型）唯一；材料通过该关系匹配，不复制材料。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_requirement")
public class DeliveryRequirementDO extends TenantBaseDO {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_SATISFIED = "SATISFIED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    @TableId
    private Long id;
    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String typeCode;
    private Boolean required;
    private Integer minimumQuantity;
    private String countingUnit;
    /** OPEN / SATISFIED / CONFIRMED；数量回落时 CONFIRMED 一并退回 OPEN，旧确认不再有效。 */
    private String status;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    /** 生成本要求的能力配置主键；配置启停与变更按兼容规则影响后续要求，不改写既有要求。 */
    private Long configId;
    private Integer configVersion;
    @Version
    private Integer version;
}
