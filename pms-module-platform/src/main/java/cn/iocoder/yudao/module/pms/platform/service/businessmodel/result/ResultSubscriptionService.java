package cn.iocoder.yudao.module.pms.platform.service.businessmodel.result;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSelectionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.subscription.ResultSubscriptionPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence.EvidenceResultRefDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence.PlatformExecutionEvidenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.result.PlatformResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.evidence.EvidenceImpactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.evidence.EvidenceResultRefMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.evidence.PlatformExecutionEvidenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.result.PlatformBusinessResultMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.result.PlatformResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.result.query.ResultSubscriptionPageQuery;
import cn.iocoder.yudao.module.pms.platform.support.result.ResultSelectionEvaluator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 独立结果订阅默认实现：补扫采纳与正式判断共用 ResultSelectionEvaluator 同一选择语义；
 * 已 SATISFIED 的订阅不自动重开、不重复写采纳证据；结果失效不回滚历史判断，只记录影响。
 */
@Service
public class ResultSubscriptionService implements ResultSubscriptionPort {

    private final PlatformResultSubscriptionMapper subscriptionMapper;
    private final PlatformBusinessResultMapper resultMapper;
    private final PlatformExecutionEvidenceMapper evidenceMapper;
    private final EvidenceResultRefMapper evidenceResultRefMapper;
    private final EvidenceImpactMapper evidenceImpactMapper;

    public ResultSubscriptionService(PlatformResultSubscriptionMapper subscriptionMapper,
                                     PlatformBusinessResultMapper resultMapper,
                                     PlatformExecutionEvidenceMapper evidenceMapper,
                                     EvidenceResultRefMapper evidenceResultRefMapper,
                                     EvidenceImpactMapper evidenceImpactMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.resultMapper = resultMapper;
        this.evidenceMapper = evidenceMapper;
        this.evidenceResultRefMapper = evidenceResultRefMapper;
        this.evidenceImpactMapper = evidenceImpactMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ResultSubscriptionRecord create(CreateCommand command, EntityActor actor) {
        validateCommand(command);
        subscriptionMapper.selectByCode(command.subscriptionCode()).ifPresent(existing -> {
            throw new BusinessContractException("CONTRACT_DUPLICATE_CODE",
                    "订阅编码已存在: " + command.subscriptionCode());
        });
        PlatformResultSubscriptionDO row = new PlatformResultSubscriptionDO();
        row.setSubscriptionCode(command.subscriptionCode());
        row.setSubscriberKind(command.subscriberKind());
        row.setSubscriberNodeKey(command.subscriberNodeKey());
        row.setResultType(command.resultType());
        row.setOwnerModule(command.ownerModule());
        row.setEntityType(command.entityType());
        row.setExpectedObjectIds(command.policy().expectedObjectIds().isEmpty() ? null
                : JsonUtils.toJsonString(command.policy().expectedObjectIds()));
        row.setAcquisition(command.policy().acquisition().name());
        row.setSelection(command.policy().selection().name());
        row.setValidityPolicy(command.policy().validity().name());
        row.setPinnedResultId(command.policy().pinnedResultId());
        row.setRoundNo(command.roundNo());
        // 轮次形成基线取建立时该结果类型当前最大形成序号（不含）。
        row.setFormationBaseline(currentBaseline(command.resultType(), command.ownerModule(),
                command.entityType()));
        row.setProjectStableRef(command.projectStableRef());
        row.setPlanVersion(command.planVersion());
        row.setRuleVersion(command.ruleVersion());
        row.setStatus(DecisionStatus.COLLECTING.name());
        row.setVersion(0);
        row.setTenantId(actor.tenantId());
        subscriptionMapper.insert(row);
        return toRecord(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ResultSubscriptionDecision backfill(long subscriptionId, long throughSequence, EntityActor actor) {
        PlatformResultSubscriptionDO row = requireSubscription(subscriptionId, actor);
        if (throughSequence < row.getFormationBaseline()) {
            throw new BusinessContractException("CONTRACT_REJECTED",
                    "补扫上界不能早于轮次形成基线: " + throughSequence);
        }
        if (DecisionStatus.SATISFIED.name().equals(row.getStatus())) {
            // 已满足的订阅不自动重开、不重复写采纳证据。
            return satisfiedDecision(row);
        }
        var policy = toPolicy(row);
        List<BusinessResultRecord> candidates = resultMapper
                .selectInventory(row.getResultType(), row.getOwnerModule(), row.getEntityType(),
                        null, null, policy.validity() == ResultSelectionPolicy.Validity.CURRENT_VALID)
                .stream().map(PlatformBusinessResultService::toRecord)
                // 声明了期望对象集合的订阅只在该范围内补扫；晚于补扫上界形成的结果留给后续轮次。
                .filter(item -> row.getExpectedObjectIds() == null
                        || expectedObjects(row).contains(item.objectRef().entityId()))
                .filter(item -> item.sequence() <= throughSequence)
                .toList();
        var accumulated = ResultSelectionEvaluator.Accumulator.empty();
        var adopted = new ArrayList<String>();
        for (BusinessResultRecord item : candidates) {
            var candidate = new ResultSelectionEvaluator.Candidate(item.objectRef().entityId(),
                    item.resultId(), item.sequence(), item.valid());
            var qualification = ResultSelectionEvaluator.qualify(policy, row.getFormationBaseline(),
                    throughSequence, candidate);
            accumulated = ResultSelectionEvaluator.accumulate(policy, accumulated,
                    List.of(qualification));
            if (qualification.eligibility() == ResultSelectionEvaluator.Eligibility.ELIGIBLE) {
                adopted.add(item.resultId());
            }
        }
        var decision = ResultSelectionEvaluator.decide(policy, accumulated, true);
        String basis = switch (decision.status()) {
            case SATISFIED -> "RESULT_QUALIFIED";
            case WAITING -> decision.missingObjects().isEmpty() ? "RESULT_NONE_QUALIFIED" : "RESULT_MISSING_EXPECTED";
            case AMBIGUOUS -> "RESULT_AMBIGUOUS";
            case UNAVAILABLE -> "RESULT_SOURCE_UNAVAILABLE";
            case COLLECTING -> "RESULT_SCAN_COMPLETE";
        };
        return persist(row, decision, basis, adopted);
    }

    @Override
    public ResultSubscriptionRecord get(long subscriptionId, EntityActor actor) {
        return toRecord(requireSubscription(subscriptionId, actor));
    }

    @Override
    public List<ResultSubscriptionRecord> listCollecting(String resultType, String ownerModule,
                                                         String entityType, EntityActor actor) {
        return subscriptionMapper.selectCollecting(resultType, ownerModule, entityType)
                .stream().map(ResultSubscriptionService::toRecord).toList();
    }

    /** 订阅分页：管理端查询入口，条件为空时只按租户边界返回。 */
    public PageResult<PlatformResultSubscriptionDO> page(ResultSubscriptionPageQuery pageQuery) {
        return subscriptionMapper.selectPage(pageQuery, pageQuery.toWrapper());
    }

    @Override
    public ResultSubscriptionDecision lastDecision(long subscriptionId, EntityActor actor) {
        PlatformResultSubscriptionDO row = requireSubscription(subscriptionId, actor);
        return evidenceMapper.selectBySubscriptionRound(row.getId(), row.getRoundNo())
                .<ResultSubscriptionDecision>map(evidence -> new ResultSubscriptionDecision(
                        DecisionStatus.valueOf(evidence.getDecision()), valueOrZero(row.getLastExamined()),
                        valueOrZero(row.getLastEligible()),
                        row.getMissingObjects() == null ? Set.of() : new HashSet<>(JsonUtils.parseArray(
                                row.getMissingObjects(), Long.class)),
                        "RESULT_EVIDENCE_RECORDED", adoptedRefs(evidence.getId())))
                .orElseGet(() -> new ResultSubscriptionDecision(DecisionStatus.valueOf(row.getStatus()),
                        valueOrZero(row.getLastExamined()), valueOrZero(row.getLastEligible()),
                        row.getMissingObjects() == null ? Set.of() : Set.copyOf(JsonUtils.parseArray(
                                row.getMissingObjects(), Long.class)),
                        "RESULT_NO_EVIDENCE_YET", List.of()));
    }

    /** 结果失效：不回滚历史判断、不重开订阅，仅按采纳引用记录证据历史影响。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordInvalidationImpact(String resultId, String detail) {
        for (EvidenceResultRefDO ref : evidenceResultRefMapper.selectByResultId(resultId)) {
            var impact = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.evidence.EvidenceImpactDO();
            impact.setEvidenceId(ref.getEvidenceId());
            impact.setResultId(resultId);
            impact.setImpact("RESULT_INVALIDATED");
            impact.setDetail(detail);
            impact.setRecordedAt(java.time.LocalDateTime.now());
            evidenceImpactMapper.insert(impact);
        }
    }

    private ResultSubscriptionDecision persist(PlatformResultSubscriptionDO row,
                                               ResultSelectionEvaluator.Decision decision,
                                               String basis, List<String> adopted) {
        // 同一轮次的证据承载该轮最新判定：再次补扫更新判定与采纳引用；
        // 历史轮次与失效影响不在此覆盖（失效影响只追加）。
        var existing = evidenceMapper.selectBySubscriptionRound(row.getId(), row.getRoundNo());
        Long evidenceId;
        if (existing.isPresent()) {
            var evidence = existing.get();
            evidence.setDecision(decision.status().name());
            evidence.setAdoptedFactRefs(adopted.isEmpty() ? evidence.getAdoptedFactRefs()
                    : JsonUtils.toJsonString(adopted));
            evidenceMapper.updateById(evidence);
            evidenceId = evidence.getId();
        } else {
            var evidence = new PlatformExecutionEvidenceDO();
            evidence.setProjectStableRef(row.getProjectStableRef());
            evidence.setPlanVersion(row.getPlanVersion());
            evidence.setNodeKey(row.getSubscriberNodeKey());
            evidence.setRoundNo(row.getRoundNo());
            evidence.setRuleVersion(row.getRuleVersion());
            evidence.setDecision(decision.status().name());
            evidence.setSubscriptionId(row.getId());
            evidence.setAdoptedFactRefs(adopted.isEmpty() ? null : JsonUtils.toJsonString(adopted));
            evidence.setTenantId(row.getTenantId());
            evidenceMapper.insert(evidence);
            evidenceId = evidence.getId();
        }
        for (String resultId : adopted) {
            var ref = new EvidenceResultRefDO();
            ref.setEvidenceId(evidenceId);
            ref.setResultId(resultId);
            ref.setTenantId(row.getTenantId());
            try {
                evidenceResultRefMapper.insert(ref);
            } catch (DuplicateKeyException ex) {
                // 同一证据对同一结果的采纳引用唯一；重复补扫幂等跳过。
            }
        }
        row.setStatus(decision.status().name());
        row.setLastExamined(decision.examined());
        row.setLastEligible(decision.eligible());
        row.setMissingObjects(decision.missingObjects().isEmpty() ? null
                : JsonUtils.toJsonString(List.copyOf(decision.missingObjects())));
        subscriptionMapper.updateById(row);
        return new ResultSubscriptionDecision(decision.status(), decision.examined(), decision.eligible(),
                decision.missingObjects(), basis, adopted);
    }

    private ResultSubscriptionDecision satisfiedDecision(PlatformResultSubscriptionDO row) {
        return lastDecision(row.getId(), null);
    }

    private long currentBaseline(String resultType, String ownerModule, String entityType) {
        return resultMapper.selectInventory(resultType, ownerModule, entityType, null, null, false)
                .stream().mapToLong(item -> PlatformBusinessResultService.toRecord(item).sequence())
                .max().orElse(0L);
    }

    private Set<Long> expectedObjects(PlatformResultSubscriptionDO row) {
        return new HashSet<>(JsonUtils.parseArray(row.getExpectedObjectIds(), Long.class));
    }

    private ResultSelectionPolicy toPolicy(PlatformResultSubscriptionDO row) {
        return new ResultSelectionPolicy(
                ResultSelectionPolicy.Acquisition.valueOf(row.getAcquisition()),
                ResultSelectionPolicy.Selection.valueOf(row.getSelection()),
                ResultSelectionPolicy.Validity.valueOf(row.getValidityPolicy()),
                row.getPinnedResultId(),
                row.getExpectedObjectIds() == null ? List.of() : List.copyOf(expectedObjects(row)));
    }

    private PlatformResultSubscriptionDO requireSubscription(long subscriptionId, EntityActor actor) {
        PlatformResultSubscriptionDO row = subscriptionMapper.selectById(subscriptionId);
        if (row == null) {
            throw new BusinessContractException("CONTRACT_REJECTED", "订阅不存在: " + subscriptionId);
        }
        if (actor != null && !actor.tenantId().equals(row.getTenantId())) {
            throw new BusinessContractException("CONTRACT_TENANT_MISMATCH", "订阅租户不匹配: " + subscriptionId);
        }
        return row;
    }

    private static ResultSubscriptionRecord toRecord(PlatformResultSubscriptionDO row) {
        return new ResultSubscriptionRecord(row.getId(), row.getSubscriptionCode(), row.getSubscriberKind(),
                row.getSubscriberNodeKey(), row.getResultType(), row.getOwnerModule(), row.getEntityType(),
                new ResultSelectionPolicy(
                        ResultSelectionPolicy.Acquisition.valueOf(row.getAcquisition()),
                        ResultSelectionPolicy.Selection.valueOf(row.getSelection()),
                        ResultSelectionPolicy.Validity.valueOf(row.getValidityPolicy()),
                        row.getPinnedResultId(),
                        row.getExpectedObjectIds() == null ? List.of()
                                : List.copyOf(JsonUtils.parseArray(row.getExpectedObjectIds(), Long.class))),
                row.getRoundNo(), row.getFormationBaseline(), row.getStatus());
    }

    private List<String> adoptedRefs(Long evidenceId) {
        return evidenceResultRefMapper.selectByEvidenceId(evidenceId).stream()
                .map(EvidenceResultRefDO::getResultId).toList();
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static void validateCommand(CreateCommand command) {
        if (command.subscriptionCode() == null || command.subscriptionCode().isBlank()
                || command.subscriberKind() == null || command.subscriberNodeKey() == null
                || command.resultType() == null || command.ownerModule() == null
                || command.entityType() == null || command.policy() == null) {
            throw new BusinessContractException("CONTRACT_REJECTED", "订阅身份与选择策略不能为空");
        }
        if (command.policy().selection() == ResultSelectionPolicy.Selection.ALL_EXPECTED
                && command.policy().expectedObjectIds().isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED",
                    "ALL_EXPECTED 选择必须声明期望对象集合");
        }
        if (command.policy().acquisition() == ResultSelectionPolicy.Acquisition.PINNED_RESULT
                && (command.policy().pinnedResultId() == null || command.policy().pinnedResultId().isBlank())) {
            throw new BusinessContractException("CONTRACT_REJECTED", "固定结果订阅必须声明结果身份");
        }
    }
}
