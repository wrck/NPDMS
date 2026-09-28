package cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery;

import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 交付要求实例：按（来源实体 + 类型）唯一；材料通过该关系匹配，不复制材料。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plt_delivery_requirement")
public class DeliveryRequirementDO extends BaseBusinessEntity {

    public static final String STATUS_OPEN = PlatformDeliveryRequirementApi.STATUS_OPEN;
    public static final String STATUS_SATISFIED = PlatformDeliveryRequirementApi.STATUS_SATISFIED;
    public static final String STATUS_CONFIRMED = PlatformDeliveryRequirementApi.STATUS_CONFIRMED;

    /** 要求来源：CATALOG=能力配置生成；TEMPLATE_FROZEN=模板冻结应交根（阶段/任务绑定）。 */
    public static final String KIND_CATALOG = "CATALOG";
    public static final String KIND_TEMPLATE_FROZEN = "TEMPLATE_FROZEN";

    private String ownerModule;
    private String entityType;
    private Long entityId;
    private String typeCode;
    /** 显示名：TEMPLATE_FROZEN=交付件名称；CATALOG 为空（显示名取类型目录）。 */
    private String name;
    private String requirementKind;
    /** TEMPLATE_FROZEN：绑定阶段/任务编码、冻结计划版本、模板定义引用与项目上下文。 */
    private String stageCode;
    private String taskCode;
    private Long planVersionId;
    private Long sourceDefinitionId;
    private Long projectId;
    /** TEMPLATE_FROZEN：冻结要求配置（数量/允许来源/自动来源/确认规则 JSON）。 */
    private String frozenConfigJson;
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
}
