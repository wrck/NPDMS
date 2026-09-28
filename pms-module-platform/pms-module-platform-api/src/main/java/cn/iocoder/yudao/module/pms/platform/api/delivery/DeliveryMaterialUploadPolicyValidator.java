package cn.iocoder.yudao.module.pms.platform.api.delivery;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;

/**
 * 统一交付材料的 Owner 上传策略校验（SPI）：交付材料文件锚固定为
 * PLT/DELIVERY_MATERIAL/{fileObjectId}，purposeCode 通常为类型目录编码；
 * 当 purposeCode 不是类型目录编码（如模板冻结交付件的交付件编码）时，
 * 平台策略提供方按材料归属 Owner 模块委派本校验方裁决约束与授权，
 * 平台不代答 Owner 的业务授权（项目范围、允许来源、生命周期等）。
 * 实现方注册为 Spring Bean，按 {@link #ownerModule()} 路由（最多一个）。
 */
public interface DeliveryMaterialUploadPolicyValidator {

    /** 承接的材料归属模块（材料 owner_module）。 */
    String ownerModule();

    /**
     * 校验并返回上传/读取约束。objectType/entityId 取自材料归属三元组
     * （objectType="DELIVERY_MATERIAL" 语义下的 owner 三元组编码见实现方约定）。
     *
     * @param tenantId      租户
     * @param actorUserId   操作人
     * @param entityType    材料归属 entityType
     * @param entityId      材料归属 entityId
     * @param purposeCode   文件用途编码（非类型目录编码才进入本校验）
     * @param action        文件动作（UPLOAD/READ/DOWNLOAD/PREVIEW/REFERENCE）
     * @param lock          是否处于锁定重验事务
     * @param expectedScopeVersion 期望范围版本（可空）
     * @throws cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException 校验不通过
     */
    FileBusinessObjectPolicyFact validateUpload(Long tenantId, Long actorUserId, String entityType,
                                                String entityId, String purposeCode, String action,
                                                boolean lock, Long expectedScopeVersion);
}
