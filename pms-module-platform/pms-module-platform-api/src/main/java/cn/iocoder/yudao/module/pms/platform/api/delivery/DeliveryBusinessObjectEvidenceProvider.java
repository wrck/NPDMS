package cn.iocoder.yudao.module.pms.platform.api.delivery;

/**
 * 业务成果材料证据提供方（SPI）：BUSINESS_RESULT 材料登记与重验时，
 * 由业务成果 Owner 模块校验对象当前状态与修订锚一致，平台不代答业务事实。
 * 实现方注册为 Spring Bean，按 {@link #supports(String)} 路由（成果类型可动态，
 * 如项目业务成果的 resultType）。
 */
public interface DeliveryBusinessObjectEvidenceProvider {

    /**
     * 是否承接该业务成果对象类型。动态类型（成果类型清单在 Owner 模块运行时才可知）
     * 的提供方在此按当前清单判断。
     */
    boolean supports(String businessObjectType);

    /**
     * 校验业务成果当前证据有效：对象存在、处于产出该交付件事实的状态、修订锚一致。
     * 无修订锚的对象（businessRevisionNo 为空）仅校验存在与状态。
     *
     * @param tenantId          租户
     * @param projectId         项目上下文（非项目要求可空）
     * @param businessObjectId  业务对象标识（Owner 模块自定义编码，可含复合身份）
     * @param businessRevisionNo 业务修订锚（可空）
     * @throws cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException 证据无效
     */
    void validateCurrent(Long tenantId, Long projectId, String businessObjectId, Long businessRevisionNo);
}
