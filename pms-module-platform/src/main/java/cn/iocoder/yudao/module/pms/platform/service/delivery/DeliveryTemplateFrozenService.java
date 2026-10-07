package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveRetryQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveStateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementScopeLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementTaskLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySubmissionCurrentLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialSubmissionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 模板冻结交付链平台承接（P06R I2）：要求实例化/更新/退役、扩展提交（整体置换 + 幂等）、
 * 材料登记与门禁收敛。编排语义留在 ACC；本服务只保证统一模型自身的约束与不变量
 * （单 CURRENT 提交、材料-要求绑定、失效材料退场、状态由 refreshStatus 推导）。
 */
@Service
@RequiredArgsConstructor
public class DeliveryTemplateFrozenService implements PlatformDeliveryRequirementApi {

    private final DeliveryRequirementMapper requirementMapper;
    private final DeliverySubmissionMapper submissionMapper;
    private final DeliveryMaterialMapper materialMapper;
    private final DeliveryRequirementService requirementService;
    private final DeliveryMaterialService materialService;

    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryFulfillmentService fulfillmentService;

    // ———— 生命周期 ————

    @Override
    @Transactional
    public List<Long> instantiateTemplateFrozen(Long projectId, Long planVersionId,
                                                List<TemplateFrozenDefinition> definitions) {
        if (projectId == null || definitions == null) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_INVALID", "实例化参数不完整");
        }
        List<Long> ids = new ArrayList<>();
        for (TemplateFrozenDefinition definition : definitions) {
            validateDefinition(definition);
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            DeliveryRequirementDO existing = requirementMapper.selectIdentityForUpdate(
                    new DeliveryRequirementIdentityLockQuery(tenantId, TEMPLATE_OWNER_MODULE, TEMPLATE_ENTITY_TYPE,
                            projectId, definition.deliverableCode()));
            if (existing != null) {
                ids.add(existing.getId());
                continue;
            }
            DeliveryRequirementDO row = new DeliveryRequirementDO();
            row.setOwnerModule(TEMPLATE_OWNER_MODULE);
            row.setEntityType(TEMPLATE_ENTITY_TYPE);
            row.setEntityId(projectId);
            row.setTypeCode(definition.deliverableCode());
            row.setName(definition.name());
            row.setRequirementKind(DeliveryRequirementDO.KIND_TEMPLATE_FROZEN);
            row.setStageCode(definition.stageCode());
            row.setTaskCode(definition.taskCode());
            row.setPlanVersionId(planVersionId);
            row.setSourceDefinitionId(definition.sourceDefinitionId());
            row.setProjectId(projectId);
            row.setFrozenConfigJson(definition.frozenConfigJson());
            row.setRequired(definition.required());
            row.setMinimumQuantity(Math.max(definition.required() ? 1 : 0, definition.minimumQuantity()));
            row.setCountingUnit(TEMPLATE_COUNTING_UNIT);
            row.setStatus(DeliveryRequirementDO.STATUS_OPEN);
            row.setConfigId(0L);
            row.setConfigVersion(0);
            requirementMapper.insert(row);
            ids.add(row.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public void updateTemplateFrozen(Long requirementId, Integer expectedVersion,
                                     TemplateFrozenDefinition definition) {
        validateDefinition(definition);
        DeliveryRequirementDO row = lockTemplateRequirement(requirementId);
        if (expectedVersion != null && !expectedVersion.equals(row.getVersion())) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_VERSION_CONFLICT",
                    "交付件定义已被并发修改，请刷新后重试: " + requirementId);
        }
        row.setName(definition.name());
        row.setStageCode(definition.stageCode());
        row.setTaskCode(definition.taskCode());
        row.setSourceDefinitionId(definition.sourceDefinitionId());
        row.setFrozenConfigJson(definition.frozenConfigJson());
        row.setRequired(definition.required());
        row.setMinimumQuantity(Math.max(definition.required() ? 1 : 0, definition.minimumQuantity()));
        requirementMapper.updateById(row);
        requirementService.refreshStatus(row);
    }

    @Override
    @Transactional
    public int renameStage(Long projectId, String fromStageCode, String toStageCode) {
        if (projectId == null || fromStageCode == null || toStageCode == null) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_INVALID", "阶段改名参数不完整");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<DeliveryRequirementDO> rows = requirementMapper.selectScopeForUpdate(
                DeliveryRequirementScopeLockQuery.byStage(tenantId, projectId, fromStageCode));
        for (DeliveryRequirementDO row : rows) {
            row.setStageCode(toStageCode);
            requirementMapper.updateById(row);
        }
        return rows.size();
    }

    @Override
    @Transactional
    public int retireUnhandled(List<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return 0;
        }
        int retired = 0;
        for (Long id : requirementIds) {
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            DeliveryRequirementDO row = requirementMapper.selectIdForUpdate(new DeliveryRequirementIdLockQuery(tenantId, id));
            if (row == null || !DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(row.getRequirementKind())) {
                continue;
            }
            // 平台侧退役护栏：有任何材料或提交历史（含已撤回）即不退役，历史披露留给 Owner 模块。
            if (!materialMapper.selectByRequirement(id).isEmpty()
                    || !submissionMapper.selectByRequirement(id).isEmpty()) {
                continue;
            }
            requirementMapper.deleteById(id);
            retired++;
        }
        return retired;
    }

    @Override
    @Transactional
    public void stageTemplateFrozenCodes(List<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return;
        }
        for (Long id : requirementIds) {
            DeliveryRequirementDO row = lockTemplateRequirement(id);
            row.setTypeCode("~plan:" + id);
            requirementMapper.updateById(row);
        }
    }

    // ———— 锁定读 / 查询 ————

    @Override
    public Optional<TemplateFrozenView> lockById(Long requirementId) {
        return Optional.ofNullable(requirementMapper.selectIdForUpdate(new DeliveryRequirementIdLockQuery(
                        TenantContextHolder.getRequiredTenantId(), requirementId)))
                .map(DeliveryTemplateFrozenService::toView);
    }

    @Override
    public Optional<TemplateFrozenView> lockByIdentity(Long projectId, String deliverableCode) {
        return Optional.ofNullable(requirementMapper.selectIdentityForUpdate(
                        new DeliveryRequirementIdentityLockQuery(TenantContextHolder.getRequiredTenantId(),
                                TEMPLATE_OWNER_MODULE, TEMPLATE_ENTITY_TYPE, projectId, deliverableCode)))
                .map(DeliveryTemplateFrozenService::toView);
    }

    @Override
    public List<TemplateFrozenView> lockByTask(Long projectId, String taskCode) {
        return requirementMapper.selectTaskForUpdate(new DeliveryRequirementTaskLockQuery(
                        TenantContextHolder.getRequiredTenantId(), projectId, taskCode))
                .stream().map(DeliveryTemplateFrozenService::toView).toList();
    }

    @Override
    public List<TemplateFrozenView> lockByProject(Long projectId) {
        return requirementMapper.selectScopeForUpdate(DeliveryRequirementScopeLockQuery.all(
                        TenantContextHolder.getRequiredTenantId(), projectId))
                .stream().map(DeliveryTemplateFrozenService::toView).toList();
    }

    @Override
    public Optional<TemplateFrozenView> findById(Long requirementId) {
        return Optional.ofNullable(requirementService.requireRequirement(requirementId))
                .map(DeliveryTemplateFrozenService::toView);
    }

    @Override
    public Optional<TemplateFrozenView> findByIdentity(Long projectId, String deliverableCode) {
        return requirementMapper.selectByOwnerAndType(TEMPLATE_OWNER_MODULE, TEMPLATE_ENTITY_TYPE,
                        projectId, deliverableCode)
                .map(DeliveryTemplateFrozenService::toView);
    }

    @Override
    public List<TemplateFrozenView> listByProject(Long projectId) {
        return requirementMapper.selectByProject(projectId).stream()
                .map(DeliveryTemplateFrozenService::toView).toList();
    }

    // ———— 提交台账 ————

    @Override
    @Transactional
    public TemplateFrozenSubmitOutcome submitTemplateFrozen(TemplateFrozenSubmitCommand command) {
        if (command == null || command.requirementId() == null || command.requestKey() == null
                || command.requestKey().isBlank() || command.requestKey().length() > 128) {
            throw new BusinessContractException("DELIVERY_SUBMISSION_INVALID", "提交参数不完整");
        }
        if (command.materialIds() == null || command.materialIds().isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "提交必须携带至少一个材料");
        }
        // 先锁要求行串行化同要求提交，再做幂等重放判断（避免重放与置换交错）。
        DeliveryRequirementDO requirement = lockTemplateRequirement(command.requirementId());
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Optional<DeliverySubmissionDO> replay = submissionMapper.selectByRequestKey(
                requirement.getId(), command.requestKey());
        if (replay.isPresent()) {
            // 携带请求快照的提交在重放时比对载荷，同键不同载荷显式拒绝（对齐 submit 契约注释）。
            if (command.requestPayloadJson() != null
                    && !JsonUtils.parseTree(replay.get().getRequestPayloadJson() == null ? ""
                            : replay.get().getRequestPayloadJson())
                            .equals(JsonUtils.parseTree(command.requestPayloadJson()))) {
                throw new BusinessContractException("DELIVERY_SUBMISSION_PAYLOAD_CONFLICT",
                        "同一提交标识不能用于不同材料");
            }
            return replayOutcome(replay.get(), requirement);
        }
        // 整体置换只退出本要求的使用关系，共享材料仍供其他要求使用。
        fulfillmentService.retainOnly(requirement.getId(), new HashSet<>(command.materialIds()));
        DeliveryRequirementService.SubmissionOutcome outcome = requirementService.submit(
                requirement.getId(), command.requestKey(), command.materialIds(), command.sourceType(),
                command.requestPayloadJson(), command.decisionEvidenceJson());
        if (outcome.replay()) {
            return replayOutcome(outcome.submission(), requirement);
        }
        return new TemplateFrozenSubmitOutcome(outcome.submission().getId(), false,
                outcome.requirement().getStatus(), command.decisionEvidenceJson());
    }

    private TemplateFrozenSubmitOutcome replayOutcome(DeliverySubmissionDO replayRow, DeliveryRequirementDO requirement) {
        return new TemplateFrozenSubmitOutcome(replayRow.getId(), true, requirement.getStatus(),
                replayRow.getDecisionEvidenceJson());
    }

    @Override
    @Transactional
    public boolean updateSubmissionDecision(Long submissionId, String decisionEvidenceJson) {
        DeliverySubmissionDO row = Optional.ofNullable(submissionMapper.selectById(submissionId))
                .orElseThrow(() -> new BusinessContractException("DELIVERY_SUBMISSION_NOT_FOUND",
                        "提交记录不存在: " + submissionId));
        if (!DeliverySubmissionDO.STATUS_CURRENT.equals(row.getStatus())) {
            return false;
        }
        row.setDecisionEvidenceJson(decisionEvidenceJson);
        submissionMapper.updateById(row);
        return true;
    }

    @Override
    public Optional<TemplateFrozenSubmissionView> findCurrentSubmission(Long requirementId) {
        return Optional.ofNullable(submissionMapper.selectCurrentForUpdate(new DeliverySubmissionCurrentLockQuery(
                        TenantContextHolder.getRequiredTenantId(), requirementId)))
                .map(DeliveryTemplateFrozenService::toSubmissionView);
    }

    @Override
    public Optional<TemplateFrozenSubmissionView> findSubmissionByRequestKey(Long requirementId, String requestKey) {
        return submissionMapper.selectByRequestKey(requirementId, requestKey)
                .map(DeliveryTemplateFrozenService::toSubmissionView);
    }

    @Override
    public Optional<TemplateFrozenSubmissionView> findSubmissionById(Long submissionId) {
        return Optional.ofNullable(submissionMapper.selectById(submissionId))
                .map(DeliveryTemplateFrozenService::toSubmissionView);
    }

    @Override
    public List<TemplateFrozenSubmissionView> listSubmissions(Long requirementId) {
        return submissionMapper.selectByRequirement(requirementId).stream()
                .map(DeliveryTemplateFrozenService::toSubmissionView).toList();
    }

    @Override
    @Transactional
    public boolean revokeProjectionSubmission(Long requirementId, String requestKey, String materialArchiveStatus) {
        lockTemplateRequirement(requirementId);
        DeliverySubmissionDO row = submissionMapper.selectByRequestKey(requirementId, requestKey)
                .orElse(null);
        if (row == null || !DeliverySubmissionDO.STATUS_CURRENT.equals(row.getStatus())) {
            return false;
        }
        row.setStatus(DeliverySubmissionDO.STATUS_WITHDRAWN);
        submissionMapper.updateById(row);
        List<Long> materialIds = parseMaterialIds(row);
        for (Long materialId : materialIds) {
            fulfillmentService.withdraw(requirementId, materialId);
        }
        if (materialArchiveStatus != null && !materialArchiveStatus.isBlank()) {
            markSubmissionArchiveState(row.getId(), materialArchiveStatus, null);
        }
        requirementService.refreshStatus(requirementService.requireRequirement(requirementId));
        return true;
    }

    // ———— 材料登记与收敛 ————

    @Override
    @Transactional
    public Long registerTemplateFrozenFile(Long requirementId, Long fileReferenceId, String title, String sourceKind) {
        return materialService.registerTemplateFrozenFile(lockTemplateRequirement(requirementId),
                fileReferenceId, title, sourceKind).getId();
    }

    @Override
    @Transactional
    public Long registerProjectionFile(Long requirementId, FileArtifactVersionFact fact, String title,
                                       String archiveStatus) {
        return materialService.registerProjectionFile(lockTemplateRequirement(requirementId), fact, title,
                archiveStatus).getId();
    }

    @Override
    @Transactional
    public Long registerTemplateFrozenBusinessResult(Long requirementId, String businessObjectType,
                                                     String businessObjectId, Long businessRevisionNo, String title) {
        return materialService.registerTemplateFrozenBusinessResult(lockTemplateRequirement(requirementId),
                businessObjectType, businessObjectId, businessRevisionNo, title).getId();
    }

    @Override
    @Transactional
    public Long registerTemplateFrozenDocument(Long requirementId, String sourceCode, Long fileReferenceId,
                                               String title) {
        return materialService.registerTemplateFrozenDocument(lockTemplateRequirement(requirementId),
                sourceCode, fileReferenceId, title).getId();
    }

    @Override
    public List<TemplateFrozenMaterialView> listMaterials(Long requirementId) {
        return materialMapper.selectByRequirement(requirementId).stream()
                .map(row -> toMaterialView(row, requirementId)).toList();
    }

    @Override
    @Transactional
    public void withdrawMaterial(Long materialId) {
        materialService.withdrawTrusted(materialId);
    }

    @Override
    public List<TemplateFrozenMaterialView> listPendingArchiveMaterials() {
        return materialMapper.selectByArchiveStatus(DeliveryMaterialDO.ARCHIVE_PENDING_COMPENSATION).stream()
                .map(DeliveryTemplateFrozenService::toMaterialView).toList();
    }

    @Override
    @Transactional
    public List<TemplateFrozenMaterialView> lockMaterials(List<Long> materialIds) {
        return materialMapper.selectMaterialsForUpdate(new DeliveryMaterialIdLockQuery(
                        TenantContextHolder.getRequiredTenantId(), materialIds)).stream()
                .map(DeliveryTemplateFrozenService::toMaterialView).toList();
    }

    @Override
    @Transactional
    public boolean markMaterialArchiveState(Long materialId, String archiveStatus, String failureCode,
                                            java.time.LocalDateTime archiveTime, String updater) {
        return materialMapper.updateArchiveStateIfPending(new DeliveryMaterialArchiveStateQuery(
                TenantContextHolder.getRequiredTenantId(), materialId, archiveStatus, failureCode,
                archiveTime, updater)) == 1;
    }

    @Override
    @Transactional
    public int bumpMaterialArchiveRetry(Long materialId, String failureCode) {
        materialMapper.bumpArchiveRetryIfPending(new DeliveryMaterialArchiveRetryQuery(
                TenantContextHolder.getRequiredTenantId(), materialId, failureCode));
        return materialMapper.selectById(materialId) == null ? 0
                : Optional.ofNullable(materialMapper.selectById(materialId).getArchiveRetryCount()).orElse(0);
    }

    @Override
    public Optional<Long> findSubmissionIdByMaterial(Long materialId) {
        if (materialMapper.selectById(materialId) == null) return Optional.empty();
        return submissionMapper.selectListUsingMaterial(new DeliveryMaterialSubmissionQuery(
                        TenantContextHolder.getRequiredTenantId(), materialId)).stream()
                .findFirst().map(DeliverySubmissionDO::getId);
    }

    @Override
    public List<TemplateFrozenSubmissionView> listPendingArchiveSubmissions() {
        return submissionMapper.selectPendingArchiveSubmissions(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveQueueQuery(
                TenantContextHolder.getRequiredTenantId())).stream().map(DeliveryTemplateFrozenService::toSubmissionView).toList();
    }
    @Override
    @Transactional
    public Optional<TemplateFrozenSubmissionView> lockPendingArchiveSubmission(Long submissionId) {
        return Optional.ofNullable(submissionMapper.selectArchiveSubmissionForUpdate(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionQuery(
                TenantContextHolder.getRequiredTenantId(),submissionId))).map(DeliveryTemplateFrozenService::toSubmissionView);
    }
    @Override
    @Transactional
    public void requireSubmissionArchive(Long submissionId) {
        submissionMapper.requireArchiveSubmission(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionQuery(
                TenantContextHolder.getRequiredTenantId(),submissionId));
    }
    @Override
    @Transactional
    public boolean markSubmissionArchiveState(Long submissionId,String archiveStatus,String failureCode) {
        if (!Set.of(ARCHIVE_ARCHIVED,ARCHIVE_INVALID,ARCHIVE_PENDING_COMPENSATION).contains(archiveStatus))
            throw new BusinessContractException("DELIVERY_ARCHIVE_STATE_INVALID","Unsupported archive transition");
        boolean updated=submissionMapper.updateArchiveSubmissionState(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryArchiveSubmissionStateQuery(
                TenantContextHolder.getRequiredTenantId(),submissionId,archiveStatus,failureCode))==1;
        if(updated && ARCHIVE_ARCHIVED.equals(archiveStatus)) {
            var source=submissionMapper.selectById(submissionId);
            if(source!=null)for(Long materialId:parseMaterialIds(source))
                markMaterialArchiveState(materialId,ARCHIVE_ARCHIVED,null,java.time.LocalDateTime.now(),source.getUpdater());
        }
        return updated;
    }

    @Override
    @Transactional
    public TemplateFrozenConvergence revalidateConvergence(Long requirementId) {
        DeliveryRequirementDO requirement = lockTemplateRequirement(requirementId);
        List<TemplateFrozenMaterialView> activeMaterials = new ArrayList<>();
        for (DeliveryMaterialDO material : materialMapper.selectByRequirement(requirement.getId())) {
            if (!DeliveryMaterialDO.STATUS_ACTIVE.equals(material.getStatus())) {
                continue;
            }
            try {
                materialService.lockAndRevalidateActive(material);
                activeMaterials.add(toMaterialView(material, requirementId));
            } catch (BusinessContractException invalid) {
                // 证据永久失效（文件不可用/业务成果不成立）→ 材料退场，状态由 refreshStatus 收敛表达。
                material.setStatus(DeliveryMaterialDO.STATUS_WITHDRAWN);
                materialMapper.updateById(material);
            }
        }
        DeliveryRequirementDO refreshed = requirementService.refreshStatus(requirement);
        return new TemplateFrozenConvergence(toView(refreshed), List.copyOf(activeMaterials));
    }

    @Override
    @Transactional
    public TemplateFrozenView refreshStatus(Long requirementId) {
        return toView(requirementService.refreshStatus(requirementService.requireRequirement(requirementId)));
    }

    // ———— 内部 ————

    private DeliveryRequirementDO lockTemplateRequirement(Long requirementId) {
        DeliveryRequirementDO row = requirementMapper.selectIdForUpdate(new DeliveryRequirementIdLockQuery(
                TenantContextHolder.getRequiredTenantId(), requirementId));
        if (row == null) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_NOT_FOUND", "交付要求不存在: " + requirementId);
        }
        if (!DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(row.getRequirementKind())) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_KIND_MISMATCH",
                    "要求不是模板冻结链: " + requirementId);
        }
        return row;
    }

    private static void validateDefinition(TemplateFrozenDefinition definition) {
        if (definition == null || definition.deliverableCode() == null || definition.deliverableCode().isBlank()) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_INVALID", "交付件编码不能为空");
        }
    }

    private static List<Long> parseMaterialIds(DeliverySubmissionDO row) {
        try {
            return JsonUtils.parseArray(row.getMaterialIdsJson(), Long.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static TemplateFrozenView toView(DeliveryRequirementDO row) {
        return new TemplateFrozenView(row.getId(), row.getProjectId(), row.getTypeCode(), row.getName(),
                row.getStageCode(), row.getTaskCode(), row.getPlanVersionId(), row.getSourceDefinitionId(),
                Boolean.TRUE.equals(row.getRequired()), row.getMinimumQuantity(), row.getCountingUnit(),
                row.getStatus(), row.getFrozenConfigJson(),
                row.getVersion() == null ? null : row.getVersion().intValue());
    }

    private static TemplateFrozenMaterialView toMaterialView(DeliveryMaterialDO row) {
        return toMaterialView(row, row.getRequirementId());
    }

    private static TemplateFrozenMaterialView toMaterialView(DeliveryMaterialDO row, Long requirementId) {
        return new TemplateFrozenMaterialView(row.getId(), requirementId, row.getMaterialKind(),
                row.getFileReferenceId(), row.getFileArtifactId(), row.getFileVersionNo(), row.getFileSha256(),
                row.getFileName(), row.getBusinessObjectType(), row.getBusinessObjectId(), row.getBusinessRevisionNo(),
                row.getStatus(), row.getArchiveStatus(), row.getArchiveFailureCode(), row.getArchiveRetryCount());
    }

    private static TemplateFrozenSubmissionView toSubmissionView(DeliverySubmissionDO row) {
        return new TemplateFrozenSubmissionView(row.getId(), row.getRequirementId(), row.getRequestKey(),
                row.getSourceType(), row.getStatus(), row.getRequestPayloadJson(), row.getDecisionEvidenceJson(),
                parseMaterialIds(row), row.getCreateTime());
    }
}
