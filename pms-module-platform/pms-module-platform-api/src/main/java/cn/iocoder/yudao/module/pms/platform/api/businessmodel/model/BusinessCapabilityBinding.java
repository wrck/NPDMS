package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/** 实体对公共能力的启用声明；配置来自能力配置，不在业务子类散落开关。 */
public record BusinessCapabilityBinding(BusinessCapabilityType type, String configRef, boolean enabled) {
}
