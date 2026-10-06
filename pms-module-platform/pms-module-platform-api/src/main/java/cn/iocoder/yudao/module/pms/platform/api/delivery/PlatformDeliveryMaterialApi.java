package cn.iocoder.yudao.module.pms.platform.api.delivery;

import java.util.List;

/**
 * 统一交付材料跨模块 API：业务模块把交付件事实（业务成果归集、文件材料登记）
 * 写入统一模型 plt_delivery_material 的唯一入口；模块间不得直接写平台交付表。
 */
public interface PlatformDeliveryMaterialApi {
    /** Explicit native upload attachment: preserve actual source identity and enabled catalog, without a requirement. */
    default Long registerNativeUploadedFile(cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact fact) {
        throw new UnsupportedOperationException("Native uploaded delivery file registration unavailable");
    }

    /** Trusted native operation after its Owner authorization and file-version lock; no requirement is fabricated. */
    Long registerNativeSourceFile(cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact fact);


    /** 材料状态：有效（登记即冻结，参与计数）。 */
    String STATUS_ACTIVE = "ACTIVE";
    /** 材料状态：已撤回（不再计数，可重新验证后重新提交）。 */
    String STATUS_WITHDRAWN = "WITHDRAWN";

    /**
     * 登记文件材料：文件须经统一两段式上传并以 PLT/DELIVERY_MATERIAL 归属（purposeCode=typeCode），
     * 登记即冻结文件证据。同一文件版本对同一来源实体幂等（返回既有材料ID）。
     *
     * @param projectId 项目上下文（可空：非项目实体）
     */
    Long registerFileMaterial(String ownerModule, String entityType, Long entityId, String typeCode,
                              Long fileReferenceId, String title, String sourceKind, Long projectId);

    /**
     * 登记业务成果材料：经 Owner 模块证据提供方校验后冻结业务对象身份与修订锚。
     * 同一（来源实体 + 业务对象 + 修订锚）幂等（返回既有材料ID）。
     *
     * @param businessRevisionNo 修订锚（如方案基线版本；无修订对象传 null）
     * @param projectId          项目上下文（可空）
     */
    Long registerBusinessResultMaterial(String ownerModule, String entityType, Long entityId, String typeCode,
                                        String businessObjectType, String businessObjectId,
                                        Long businessRevisionNo, String title, Long projectId);

    /** 按来源实体查询材料（全部状态，供汇总与面板装配）。 */
    List<DeliveryMaterialView> listByEntity(String ownerModule, String entityType, Long entityId);

    /** 按 owner 三元组 + 类型查询（typeCode 可空）。 */
    List<DeliveryMaterialView> listByEntityAndType(String ownerModule, String entityType, Long entityId,
                                                   String typeCode);

    /** 按项目上下文查询材料（全部状态，供 6.4 项目交付汇总聚合）。 */
    List<DeliveryMaterialView> listByProject(Long projectId);

    /** 交付材料汇总视图（静态字段，不含行为）。 */
    record DeliveryMaterialView(Long id, String ownerModule, String entityType, Long entityId, String typeCode,
                                String materialKind, Long requirementId, Long projectId, Long fileArtifactId,
                                Integer fileVersionNo, String fileSha256, String fileName,
                                String businessObjectType, String businessObjectId, Long businessRevisionNo,
                                String title, String sourceKind, String status, String archiveStatus,
                                String creator, java.time.LocalDateTime createTime) {
    }
}
