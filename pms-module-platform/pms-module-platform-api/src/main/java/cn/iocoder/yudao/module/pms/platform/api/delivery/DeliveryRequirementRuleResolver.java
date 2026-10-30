package cn.iocoder.yudao.module.pms.platform.api.delivery;

/**
 * 模板冻结要求的状态解析（SPI）：TEMPLATE_FROZEN 要求的"满足"= 材料数量达标 且
 * 冻结确认规则判定通过。规则引擎与项目事实在 Owner 模块侧（如 ProjectDeliverableRuleApi），
 * 平台经本 SPI 取判定结果，不反向依赖业务模块。实现方注册为 Spring Bean（最多一个）。
 */
public interface DeliveryRequirementRuleResolver {

    /**
     * 判定要求当前是否满足。判定语义（来源白名单/文档范围/确认规则）由 Owner 模块按其规则
     * 上下文承接；frozen_config_json 仅作冻结快照留档，不参与判定。
     *
     * @param requirementId    要求实例ID
     * @param projectId        项目上下文（可空：非项目要求不在此承接）
     * @param requirementCode  模板交付件编码（要求实例的 type_code）
     * @param materialCount    当前有效材料计数（按要求的计数单位）
     */
    Resolution evaluate(Long requirementId, Long projectId, String requirementCode, int materialCount);

    record Resolution(boolean satisfied, String reason, String evidenceJson) {
    }
}
