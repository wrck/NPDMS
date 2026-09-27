package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/** 实体可声明启用的公共能力。能力决定公共面板与公共实现是否接入，不在继承链上生成组合父类。 */
public enum BusinessCapabilityType {
    /** 交付件：材料类型、要求匹配、提交/确认/归集 */
    DELIVERY,
    /** 审批：按用途声明审批事实与公共办理面板 */
    APPROVAL,
    /** 内容历史：继承式修订实体 */
    CONTENT_HISTORY,
    /** 动态表单/扩展字段组合 */
    DYNAMIC_FORM
}
