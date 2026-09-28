package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryRequirementRuleResolver;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryCapabilityConfigDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryCounting.CountingInput;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 交付要求实例与提交台账：要求从能力配置生成（同实体同类型唯一）；提交按 request_key 幂等，
 * 同一要求新提交取代旧 CURRENT 提交；数量按要求的显式计数单位判定，确认在满足时才有效，
 * 数量回落（撤回/文件失效）时旧确认退回 OPEN，不覆盖历史（事件留痕）。
 */
@Service
@RequiredArgsConstructor
public class DeliveryRequirementService {

    private final DeliveryRequirementMapper requirementMapper;
    private final DeliverySubmissionMapper submissionMapper;
    private final DeliveryMaterialMapper materialMapper;
    private final DeliveryCatalogService catalogService;
    private final DeliveryMaterialService materialService;
    private final PlatformBusinessEventApi outbox;
    /** TEMPLATE_FROZEN 要求的冻结规则判定（Owner 模块注册）；无模板要求时不必存在。 */
    private final ObjectProvider<DeliveryRequirementRuleResolver> ruleResolverProvider;

    /** 按能力配置为实体生成要求实例；已存在的要求保持既有口径，不因配置变更被改写。 */
    @Transactional
    public List<DeliveryRequirementDO> syncFromConfig(String ownerModule, String entityType, Long entityId) {
        List<DeliveryRequirementDO> result = new ArrayList<>();
        for (DeliveryCapabilityConfigDO config : catalogService.enabledConfigs(ownerModule, entityType)) {
            Optional<DeliveryRequirementDO> existing = requirementMapper.selectByOwnerAndType(
                    ownerModule, entityType, entityId, config.getTypeCode());
            if (existing.isPresent()) {
                result.add(existing.get());
                continue;
            }
            DeliveryRequirementDO row = new DeliveryRequirementDO();
            row.setOwnerModule(ownerModule);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setTypeCode(config.getTypeCode());
            row.setRequirementKind(DeliveryRequirementDO.KIND_CATALOG);
            row.setRequired(config.getRequired());
            row.setMinimumQuantity(config.getMinimumQuantity());
            row.setCountingUnit(config.getCountingUnit());
            row.setStatus(DeliveryRequirementDO.STATUS_OPEN);
            row.setConfigId(config.getId());
            row.setConfigVersion(config.getId().intValue());
            requirementMapper.insert(row);
            result.add(row);
        }
        return result;
    }

    public List<DeliveryRequirementDO> listByEntity(String ownerModule, String entityType, Long entityId) {
        return requirementMapper.selectByEntity(ownerModule, entityType, entityId);
    }

    public record RequirementView(DeliveryRequirementDO requirement, int count, int minimumQuantity,
                                  String countingUnit) {
    }

    /** 查询视图：实时计数（不落库状态），落库状态仅由动作刷新。 */
    public List<RequirementView> viewByEntity(String ownerModule, String entityType, Long entityId) {
        return listByEntity(ownerModule, entityType, entityId).stream()
                .map(requirement -> new RequirementView(requirement,
                        countOf(requirement), requirement.getMinimumQuantity(), requirement.getCountingUnit()))
                .toList();
    }

    public int countOf(DeliveryRequirementDO requirement) {
        CountingInput input = countingInput(requirement);
        return DeliveryCounting.count(input, requirement.getCountingUnit());
    }

    private CountingInput countingInput(DeliveryRequirementDO requirement) {
        List<DeliveryMaterialDO> activeMaterials = materialMapper
                .selectByEntity(requirement.getOwnerModule(), requirement.getEntityType(),
                        requirement.getEntityId(), requirement.getTypeCode()).stream()
                .filter(material -> DeliveryMaterialDO.STATUS_ACTIVE.equals(material.getStatus()))
                .toList();
        return new CountingInput(activeMaterials, submissionMapper.selectByRequirement(requirement.getId()));
    }

    public DeliveryRequirementDO requireRequirement(Long id) {
        return Optional.ofNullable(requirementMapper.selectById(id))
                .orElseThrow(() -> new BusinessContractException("DELIVERY_REQUIREMENT_NOT_FOUND",
                        "交付要求不存在: " + id));
    }

    public record SubmissionOutcome(DeliverySubmissionDO submission, boolean replay,
                                    DeliveryRequirementDO requirement, int count) {
    }

    @Transactional
    public SubmissionOutcome submit(Long requirementId, String requestKey, List<Long> materialIds) {
        return submit(requirementId, requestKey, materialIds, null, null, null);
    }

    /**
     * 提交扩展形态：TEMPLATE_FROZEN 链携带来源语义、请求快照与判定证据；
     * CATALOG 路径三参为 null，保持原语义。
     */
    @Transactional
    public SubmissionOutcome submit(Long requirementId, String requestKey, List<Long> materialIds,
                                    String sourceType, String requestPayloadJson, String decisionEvidenceJson) {
        if (materialIds == null || materialIds.isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "提交必须携带至少一个材料");
        }
        DeliveryRequirementDO requirement = requireRequirement(requirementId);
        // 幂等重放按（要求 + request_key）范围：同键落在不同要求上是两个独立提交；
        // 携带请求快照的提交（TEMPLATE_FROZEN）在重放时比对载荷，同键不同载荷显式拒绝。
        Optional<DeliverySubmissionDO> replay = submissionMapper.selectByRequestKey(requirementId, requestKey);
        if (replay.isPresent()) {
            DeliverySubmissionDO replayRow = replay.get();
            if (requestPayloadJson != null
                    && !JsonUtils.parseTree(replayRow.getRequestPayloadJson() == null ? ""
                            : replayRow.getRequestPayloadJson()).equals(JsonUtils.parseTree(requestPayloadJson))) {
                throw new BusinessContractException("DELIVERY_SUBMISSION_PAYLOAD_CONFLICT",
                        "同一提交标识不能用于不同材料");
            }
            return new SubmissionOutcome(replayRow, true, requirement, countOf(requirement));
        }
        if (DeliveryRequirementDO.KIND_CATALOG.equals(requirement.getRequirementKind())) {
            catalogService.requireEnabledType(requirement.getTypeCode());
        }
        List<DeliveryMaterialDO> materials = materialMapper.selectByMaterialIds(materialIds);
        if (materials.size() != materialIds.stream().distinct().count()) {
            throw new BusinessContractException("DELIVERY_MATERIAL_NOT_FOUND", "提交包含不存在的材料");
        }
        List<DeliveryMaterialService.FrozenEvidence> evidence = new ArrayList<>();
        for (DeliveryMaterialDO material : materials) {
            // TEMPLATE_FROZEN 材料按绑定要求归属（项目内共享 owner 三元组，type_code=交付件编码）；
            // CATALOG 材料继续按 owner 三元组 + 类型精确匹配。
            boolean ownerMatch;
            if (DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(requirement.getRequirementKind())) {
                ownerMatch = requirement.getId().equals(material.getRequirementId());
            } else {
                ownerMatch = material.getOwnerModule().equals(requirement.getOwnerModule())
                        && material.getEntityType().equals(requirement.getEntityType())
                        && material.getEntityId().equals(requirement.getEntityId())
                        && material.getTypeCode().equals(requirement.getTypeCode());
            }
            if (!ownerMatch) {
                throw new BusinessContractException("DELIVERY_MATERIAL_OWNER_MISMATCH",
                        "材料与要求归属或类型不一致，拒绝伪造关联: 材料 " + material.getId());
            }
            if (!DeliveryMaterialDO.STATUS_ACTIVE.equals(material.getStatus())) {
                throw new BusinessContractException("DELIVERY_MATERIAL_NOT_ACTIVE",
                        "材料已撤回，不能提交: " + material.getId());
            }
            evidence.add(materialService.revalidateActive(material));
        }
        // 取代旧 CURRENT 提交：同一要求同时只有一个当前提交。
        for (DeliverySubmissionDO current : submissionMapper.selectByRequirement(requirementId)) {
            if (DeliverySubmissionDO.STATUS_CURRENT.equals(current.getStatus())) {
                current.setStatus(DeliverySubmissionDO.STATUS_SUPERSEDED);
                submissionMapper.updateById(current);
            }
        }
        DeliverySubmissionDO submission = new DeliverySubmissionDO();
        submission.setRequirementId(requirementId);
        submission.setRequestKey(requestKey);
        submission.setMaterialIdsJson(JsonUtils.toJsonString(materialIds));
        submission.setSourceType(sourceType);
        submission.setRequestPayloadJson(requestPayloadJson);
        submission.setDecisionEvidenceJson(decisionEvidenceJson);
        submission.setStatus(DeliverySubmissionDO.STATUS_CURRENT);
        submission.setSubmitEvidenceJson(JsonUtils.toJsonString(evidence));
        try {
            submissionMapper.insert(submission);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessContractException("IDEMPOTENCY_DIGEST_CONFLICT", "提交幂等键冲突: " + requestKey);
        }
        DeliveryRequirementDO updated = refreshStatus(requirement);
        publishRequirement(updated, "SUBMITTED");
        return new SubmissionOutcome(submission, false, updated, countOf(updated));
    }

    @Transactional
    public SubmissionOutcome withdrawSubmission(Long submissionId) {
        DeliverySubmissionDO submission = Optional.ofNullable(submissionMapper.selectById(submissionId))
                .orElseThrow(() -> new BusinessContractException("DELIVERY_SUBMISSION_NOT_FOUND",
                        "提交记录不存在: " + submissionId));
        if (!DeliverySubmissionDO.STATUS_CURRENT.equals(submission.getStatus())) {
            throw new BusinessContractException("DELIVERY_SUBMISSION_NOT_CURRENT",
                    "仅当前提交可以撤回: " + submissionId);
        }
        submission.setStatus(DeliverySubmissionDO.STATUS_WITHDRAWN);
        submissionMapper.updateById(submission);
        DeliveryRequirementDO requirement = requireRequirement(submission.getRequirementId());
        DeliveryRequirementDO updated = refreshStatus(requirement);
        publishRequirement(updated, "SUBMISSION_WITHDRAWN");
        return new SubmissionOutcome(submission, false, updated, countOf(updated));
    }

    @Transactional
    public RequirementView confirm(Long requirementId) {
        DeliveryRequirementDO requirement = requireRequirement(requirementId);
        if (DeliveryRequirementDO.KIND_CATALOG.equals(requirement.getRequirementKind())) {
            catalogService.requireEnabledType(requirement.getTypeCode());
        }
        int count = countOf(requirement);
        if (count < requirement.getMinimumQuantity()) {
            throw new BusinessContractException("DELIVERY_REQUIREMENT_NOT_SATISFIED",
                    "数量未满足，不能确认: 需要 " + requirement.getMinimumQuantity() + "，当前 " + count);
        }
        // 确认前重验全部计数材料证据；文件失效或撤回在此显式失败，不伪造确认。
        for (DeliveryMaterialDO material : DeliveryCounting.countedMaterials(countingInput(requirement))) {
            materialService.revalidateActive(material);
        }
        if (DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(requirement.getRequirementKind())) {
            DeliveryRequirementRuleResolver.Resolution resolution = resolveRule(requirement, count);
            if (!resolution.satisfied()) {
                throw new BusinessContractException("DELIVERY_REQUIREMENT_NOT_SATISFIED",
                        "冻结确认规则未通过，不能确认: " + resolution.reason());
            }
        }
        requirement.setStatus(DeliveryRequirementDO.STATUS_CONFIRMED);
        requirement.setConfirmedBy(String.valueOf(
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
        requirement.setConfirmedTime(LocalDateTime.now());
        requirementMapper.updateById(requirement);
        publishRequirement(requirement, "CONFIRMED");
        return new RequirementView(requirement, count, requirement.getMinimumQuantity(),
                requirement.getCountingUnit());
    }

    /** 数量回落时的状态收敛：CONFIRMED/SATISFIED → OPEN（旧确认不再有效，事件留痕）。
     *  TEMPLATE_FROZEN 要求的满足 = 数量达标 且 冻结规则判定通过。 */
    @Transactional
    public DeliveryRequirementDO refreshStatus(DeliveryRequirementDO requirement) {
        int count = countOf(requirement);
        boolean satisfied = count >= requirement.getMinimumQuantity();
        if (satisfied && DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(requirement.getRequirementKind())) {
            satisfied = resolveRule(requirement, count).satisfied();
        }
        String next;
        if (satisfied) {
            next = DeliveryRequirementDO.STATUS_CONFIRMED.equals(requirement.getStatus())
                    ? DeliveryRequirementDO.STATUS_CONFIRMED : DeliveryRequirementDO.STATUS_SATISFIED;
        } else {
            next = DeliveryRequirementDO.STATUS_OPEN;
        }
        if (!next.equals(requirement.getStatus())) {
            requirement.setStatus(next);
            if (DeliveryRequirementDO.STATUS_OPEN.equals(next)) {
                requirement.setConfirmedBy(null);
                requirement.setConfirmedTime(null);
            }
            requirementMapper.updateById(requirement);
            publishRequirement(requirement, "STATUS_REFRESHED");
        }
        return requirement;
    }

    private DeliveryRequirementRuleResolver.Resolution resolveRule(DeliveryRequirementDO requirement, int count) {
        DeliveryRequirementRuleResolver resolver = ruleResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new BusinessContractException("DELIVERY_RULE_RESOLVER_MISSING",
                    "模板冻结要求缺少规则判定方，不能判定满足: " + requirement.getId());
        }
        return resolver.evaluate(requirement.getId(), requirement.getProjectId(), requirement.getTypeCode(), count);
    }

    private void publishRequirement(DeliveryRequirementDO requirement, String action) {
        String eventId = UUID.randomUUID().toString();
        String payload = JsonUtils.toJsonString(new DeliveryEventPublisher.DeliveryChangedMessage(eventId,
                requirement.getOwnerModule(), requirement.getEntityType(), requirement.getEntityId(),
                requirement.getTypeCode(), action, requirement.getStatus(), LocalDateTime.now()));
        outbox.append("DeliveryRequirement", requirement.getId().toString(),
                new PlatformCommandExecutionApi.BusinessEvent(eventId, DeliveryEventPublisher.EVENT_TYPE, payload));
    }
}
