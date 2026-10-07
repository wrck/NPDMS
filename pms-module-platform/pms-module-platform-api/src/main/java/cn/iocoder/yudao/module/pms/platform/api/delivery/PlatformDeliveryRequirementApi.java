package cn.iocoder.yudao.module.pms.platform.api.delivery;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;

import java.util.List;
import java.util.Optional;

/**
 * 统一交付要求跨模块 API（TEMPLATE_FROZEN 链，P06R I2）：平台承接模板冻结应交根的存储、
 * 提交台账与证据收敛；编排语义（计划改版 staging、来源业务校验、门禁判定、权限与数据范围）
 * 全部留在 Owner 模块（ACC）。模块间不得直接写 plt_delivery_* 表。
 *
 * <p>身份约定：TEMPLATE_FROZEN 要求 owner 三元组固定为
 * ({@link #TEMPLATE_OWNER_MODULE}, {@link #TEMPLATE_ENTITY_TYPE}, projectId)，
 * type_code=模板交付件编码，(tenant, owner, entity, type_code) 唯一即项目内交付件编码唯一。
 */
public interface PlatformDeliveryRequirementApi {

    String TEMPLATE_OWNER_MODULE = "ACC";
    String TEMPLATE_ENTITY_TYPE = "project_deliverable";

    /** 计数单位：模板冻结要求固定按材料计数（与 acc 来源版本附件集语义一致）。 */
    String TEMPLATE_COUNTING_UNIT = "MATERIAL";

    // ---- 值域常量（沿用历史列值域；平台 DO 常量引用本接口，保证单一取值）----

    /** 提交来源类型。 */
    String SOURCE_UPLOAD = "UPLOAD";
    String SOURCE_BUSINESS_RESULT = "BUSINESS_RESULT";
    String SOURCE_BUSINESS_DOCUMENT = "BUSINESS_DOCUMENT";
    String SOURCE_AUTO_PROJECTION = "AUTO_PROJECTION";

    /** 材料种类与来源方式。 */
    String MATERIAL_KIND_FILE = "FILE";
    String MATERIAL_KIND_BUSINESS_RESULT = "BUSINESS_RESULT";
    String MATERIAL_SOURCE_UPLOAD = "UPLOAD";
    String MATERIAL_SOURCE_GENERATED = "GENERATED";
    String MATERIAL_SOURCE_ASSOCIATED = "ASSOCIATED";

    /** 材料状态与文件统一锚（新登记材料经 PLT/DELIVERY_MATERIAL 上传锚归属校验）。 */
    String MATERIAL_STATUS_ACTIVE = "ACTIVE";
    String MATERIAL_FILE_OWNER_CONTEXT = "PLT";
    String MATERIAL_FILE_OBJECT_TYPE = "DELIVERY_MATERIAL";

    /** 材料归档状态。 */
    String ARCHIVE_NOT_REQUIRED = "NOT_REQUIRED";
    String ARCHIVE_PENDING_COMPENSATION = "PENDING_COMPENSATION";
    String ARCHIVE_ARCHIVED = "ARCHIVED";
    String ARCHIVE_INVALID = "INVALID";

    /** 要求状态。 */
    String STATUS_OPEN = "OPEN";
    String STATUS_SATISFIED = "SATISFIED";
    String STATUS_CONFIRMED = "CONFIRMED";

    /** 旧 acc_project_deliverable 附件锚（V374 迁移保留原锚，不重挂不可变历史；重选入新提交时接受）。 */
    String LEGACY_OWNER_CONTEXT = "ACC";
    String LEGACY_OBJECT_TYPE = "PROJECT_DELIVERABLE";
    String LEGACY_PURPOSE_CODE = "PROJECT_DELIVERABLE_DOCUMENT";

    /** 模板冻结交付件定义（Owner 模块从计划快照解出；平台只负责落库与幂等）。 */
    record TemplateFrozenDefinition(String deliverableCode, String name, String stageCode, String taskCode,
                                    boolean required, int minimumQuantity, Long sourceDefinitionId,
                                    String frozenConfigJson) {
    }

    /** 模板冻结要求视图（静态字段；version 供乐观并发控制）。 */
    record TemplateFrozenView(Long id, Long projectId, String deliverableCode, String name, String stageCode,
                              String taskCode, Long planVersionId, Long sourceDefinitionId, boolean required,
                              int minimumQuantity, String countingUnit, String status, String frozenConfigJson,
                              Integer version) {
    }

    /** 模板冻结材料视图（静态字段；归档事实随行，供补偿与镜像展示）。 */
    record TemplateFrozenMaterialView(Long id, Long requirementId, String materialKind, Long fileReferenceId,
                                      Long fileArtifactId, Integer fileVersionNo, String fileSha256, String fileName,
                                      String businessObjectType, String businessObjectId, Long businessRevisionNo,
                                      String status, String archiveStatus, String archiveFailureCode,
                                      Integer archiveRetryCount) {
    }

    /** 提交台账视图（静态字段）。 */
    record TemplateFrozenSubmissionView(Long id, Long requirementId, String requestKey, String sourceType,
                                        String status, String requestPayloadJson, String decisionEvidenceJson,
                                        List<Long> materialIds, java.time.LocalDateTime createTime) {
    }

    /** 扩展提交命令：requestKey 幂等（要求内唯一），payload 供重放比对。 */
    record TemplateFrozenSubmitCommand(Long requirementId, String requestKey, List<Long> materialIds,
                                       String sourceType, String requestPayloadJson, String decisionEvidenceJson) {
    }

    /** 提交结果：replay=true 表示幂等重放（未产生新提交）。 */
    record TemplateFrozenSubmitOutcome(Long submissionId, boolean replay, String requirementStatus,
                                       String decisionEvidenceJson) {
    }

    /** 收敛结果：材料锁定重验后的要求状态与留存有效材料（失效材料已撤回）。 */
    record TemplateFrozenConvergence(TemplateFrozenView requirement,
                                     List<TemplateFrozenMaterialView> activeMaterials) {
    }

    // ———— 生命周期（ACC 初始化 / 计划改版编排；幂等按 (project, deliverableCode) 身份） ————

    /**
     * 实例化模板冻结要求：已存在的 (project, code) 返回既有ID（幂等），缺失的按定义落库
     * （minimum=max(required?1:0, definition.minimumQuantity)，counting=MATERIAL，status=OPEN）。
     */
    List<Long> instantiateTemplateFrozen(Long projectId, Long planVersionId, List<TemplateFrozenDefinition> definitions);

    /**
     * 计划改版定义更新：按 expectedVersion 乐观锁更新名称/阶段/任务/冻结配置；
     * 版本冲突抛 DELIVERY_REQUIREMENT_VERSION_CONFLICT。
     */
    void updateTemplateFrozen(Long requirementId, Integer expectedVersion, TemplateFrozenDefinition definition);

    /** 阶段编码改名暂存落地：项目内 stage_code=fromStageCode 的模板要求改写为 toStageCode。 */
    int renameStage(Long projectId, String fromStageCode, String toStageCode);

    /**
     * 退役未承接的要求（计划中已删除的交付件）：仅当无任何材料与提交历史时逻辑删除；
     * 有历史的ID跳过（返回值=实际退役数），历史去留由 Owner 模块披露。
     */
    int retireUnhandled(List<Long> requirementIds);

    /**
     * 计划改版编码改名暂存：type_code 置为事务内临时编码（~plan:{id}），先腾空目标编码再落地最终编码，
     * 支持同一批次内的编码互换（对齐旧 acc 根改名暂存语义）；不改版本号（最终更新才递增）。
     */
    void stageTemplateFrozenCodes(List<Long> requirementIds);

    // ———— 锁定读 / 查询 ————

    Optional<TemplateFrozenView> lockById(Long requirementId);

    Optional<TemplateFrozenView> lockByIdentity(Long projectId, String deliverableCode);

    List<TemplateFrozenView> lockByTask(Long projectId, String taskCode);

    List<TemplateFrozenView> lockByProject(Long projectId);

    Optional<TemplateFrozenView> findById(Long requirementId);

    /** 非锁定身份读（上传会话建立等轻校验路径用；并发控制路径用 lockByIdentity）。 */
    Optional<TemplateFrozenView> findByIdentity(Long projectId, String deliverableCode);

    List<TemplateFrozenView> listByProject(Long projectId);

    // ———— 提交台账 ————

    /**
     * 模板冻结扩展提交：锁要求行 → requestKey 重放（payload 不一致拒绝）→ 校验材料绑定与证据 →
     * 整体置换（旧 ACTIVE 材料中不属于本次提交的撤回）→ 取代旧 CURRENT 提交 → 落台账 → 状态收敛。
     */
    TemplateFrozenSubmitOutcome submitTemplateFrozen(TemplateFrozenSubmitCommand command);

    /** 提交判定证据回填（仅 CURRENT 提交；返回是否回填）。 */
    boolean updateSubmissionDecision(Long submissionId, String decisionEvidenceJson);

    Optional<TemplateFrozenSubmissionView> findCurrentSubmission(Long requirementId);

    Optional<TemplateFrozenSubmissionView> findSubmissionByRequestKey(Long requirementId, String requestKey);

    /** 按提交ID定位（归档补偿等按材料反查来源链路用）。 */
    Optional<TemplateFrozenSubmissionView> findSubmissionById(Long submissionId);

    List<TemplateFrozenSubmissionView> listSubmissions(Long requirementId);

    /**
     * 投影失效：按 requestKey 定位提交，CURRENT → WITHDRAWN 并撤回其材料，随后状态收敛；
     * 已失效为幂等空操作。返回是否发生失效。materialArchiveStatus 非空时，被撤回材料的
     * 归档状态置为该值（如验收报告撤销置 INVALID 不再归档；满意度撤销保持待归档传 null）。
     */
    boolean revokeProjectionSubmission(Long requirementId, String requestKey, String materialArchiveStatus);

    // ———— 材料登记与收敛 ————

    /** 手工上传文件登记（TEMPLATE_FROZEN：typeCode=交付件编码，不做目录校验）。 */
    Long registerTemplateFrozenFile(Long requirementId, Long fileReferenceId, String title, String sourceKind);

    /** 自动投影文件登记（投影事实为权威，不做 owner 锚校验；archiveStatus 随迁）。 */
    Long registerProjectionFile(Long requirementId, FileArtifactVersionFact fact, String title, String archiveStatus);

    /**
     * 归集文档材料登记：文件锚点属其他业务对象（历史锚不重挂），按文件自身身份冻结证据；
     * sourceCode 记入 businessObjectType，幂等范围 =（要求 + 来源编码 + 引用）。
     */
    Long registerTemplateFrozenDocument(Long requirementId, String sourceCode, Long fileReferenceId, String title);

    /** 业务成果材料登记（幂等范围=要求+业务对象+修订；旧修订退场由提交整体置换处理）。 */
    Long registerTemplateFrozenBusinessResult(Long requirementId, String businessObjectType, String businessObjectId,
                                              Long businessRevisionNo, String title);

    List<TemplateFrozenMaterialView> listMaterials(Long requirementId);

    /** 撤回单条材料（Owner 模块判定证据失效后的显式退场动作）。 */
    void withdrawMaterial(Long materialId);

    /** 待归档材料队列（归档补偿按文件锚点分组推进；PENDING_COMPENSATION 升序）。 */
    List<TemplateFrozenMaterialView> listPendingArchiveMaterials();

    /** 归档补偿锁定读：按ID清单固定材料行（补偿事务内防并发双写）。 */
    List<TemplateFrozenMaterialView> lockMaterials(List<Long> materialIds);

    /** 归档状态推进：仅当行仍为 PENDING_COMPENSATION 时生效（返回是否更新）。 */
    boolean markMaterialArchiveState(Long materialId, String archiveStatus, String failureCode,
                                     java.time.LocalDateTime archiveTime, String updater);

    /** 归档失败水位：仅当行仍为 PENDING_COMPENSATION 时累加重试计数（返回新计数，0=未生效）。 */
    int bumpMaterialArchiveRetry(Long materialId, String failureCode);

    /** 定位包含指定材料的提交（投影来源反查：材料 → 所属提交台账）。 */
    Optional<Long> findSubmissionIdByMaterial(Long materialId);

    /** Independent archive targets remain pending even when another submission archived a shared file. */
    List<TemplateFrozenSubmissionView> listPendingArchiveSubmissions();
    Optional<TemplateFrozenSubmissionView> lockPendingArchiveSubmission(Long submissionId);
    void requireSubmissionArchive(Long submissionId);
    boolean markSubmissionArchiveState(Long submissionId, String archiveStatus, String failureCode);


    /**
     * 门禁收敛：锁要求行 → ACTIVE 材料逐条锁定重验（文件按自身锚点，业务成果按 Owner 提供方）→
     * 证据永久失效的材料撤回 → refreshStatus 状态收敛。判定不满足不抛错（由状态表达）。
     */
    TemplateFrozenConvergence revalidateConvergence(Long requirementId);

    /** 仅状态收敛（数量/规则重算并落库），供 Owner 模块在自有动作后复用。 */
    TemplateFrozenView refreshStatus(Long requirementId);
}
