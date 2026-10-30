package cn.iocoder.yudao.module.pms.platform.service.approval;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.ApprovalAssociationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalExecutionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalSubmissionRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalEffectDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalOpinionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalAttemptMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalEffectMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalOpinionMapper;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 公共审批关联服务：发起/撤回/重提走公共操作并按配置路由到执行实现；
 * 引擎可信结果经校验后幂等更新（重复回调不重复生效，冲突结论显式拒绝）。
 * "流程批准"与"领域生效"分别记录；重提生成新尝试并保留旧意见与结论。
 */
@Service
@RequiredArgsConstructor
public class ApprovalAssociationService implements ApprovalAssociationApi {

    public static final String EVENT_TYPE = "pms.approval.decided";

    private final ApprovalAttemptMapper attemptMapper;
    private final ApprovalOpinionMapper opinionMapper;
    private final ApprovalEffectMapper effectMapper;
    private final ApprovalBackendRegistry backendRegistry;
    private final PlatformBusinessEventApi outbox;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityAccessPort accessPort;
    private final BusinessOperationDispatcher dispatcher;

    @Transactional
    public ApprovalAttemptDO submit(EntityRef subject, String purpose, String attemptId,
                                    String submissionBasis, String neutralProcessRef, Long actorUserId) {
        ApprovalAttemptDO existing = attemptMapper.selectBySubjectAndAttempt(
                subject.ownerModule(), subject.entityType(), subject.entityId(), purpose, attemptId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        ApprovalExecutionPort backend = backendRegistry.requireActive();
        // 提交时服务端记录当前内容并发基准，生效时据此判断批准是否仍适用于当前内容。
        Integer contentBasis = currentContentBasis(subject, actorUserId);
        // 引擎发起按 attemptId 幂等：关联落库失败重发同键不产生第二个流程实例。
        String instanceRef = backend.submit(new ApprovalSubmissionRequest(subject, purpose, attemptId,
                submissionBasis, neutralProcessRef));
        ApprovalAttemptDO row = new ApprovalAttemptDO();
        row.setOwnerModule(subject.ownerModule());
        row.setEntityType(subject.entityType());
        row.setEntityId(subject.entityId());
        row.setPurpose(purpose);
        row.setAttemptId(attemptId);
        row.setSubmissionBasis(submissionBasis);
        row.setSubmissionConcurrencyBasis(contentBasis);
        row.setNeutralProcessRef(neutralProcessRef);
        row.setBackendId(backend.backendId());
        row.setInstanceRef(instanceRef);
        row.setStatus(ApprovalAttemptDO.STATUS_PENDING);
        try {
            attemptMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            return attemptMapper.selectBySubjectAndAttempt(subject.ownerModule(), subject.entityType(),
                    subject.entityId(), purpose, attemptId).orElseThrow(() -> conflict);
        }
        opinionMapper.insert(opinion(row.getId(), ApprovalOpinionDO.ACTION_SUBMIT, submissionBasis,
                actorUserId));
        return row;
    }

    /** 批次发起：一次引擎实例覆盖多个主体，各主体行共用 attemptId 与 instance_ref。 */
    @Transactional
    public List<ApprovalAttemptDO> submitBatch(List<EntityRef> subjects, String purpose, String attemptId,
                                               String submissionBasis, String neutralProcessRef,
                                               Long actorUserId) {
        if (subjects == null || subjects.isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "批次审批至少需要一个业务主体");
        }
        List<ApprovalAttemptDO> rows = new ArrayList<>();
        for (EntityRef subject : subjects) {
            ApprovalAttemptDO row = submit(subject, purpose, attemptId, submissionBasis,
                    neutralProcessRef, actorUserId);
            if (row.getBatchGroupRef() == null) {
                row.setBatchGroupRef(attemptId);
                attemptMapper.updateById(row);
            }
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public ApprovalAttemptDO withdraw(Long attemptRowId, String reason, Long actorUserId) {
        ApprovalAttemptDO row = requireAttempt(attemptRowId);
        if (!ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessContractException("APPROVAL_ATTEMPT_NOT_PENDING",
                    "仅进行中的尝试可撤回: " + attemptRowId);
        }
        backendRegistry.requireActive().withdraw(row.getBackendId(), row.getInstanceRef(),
                row.getAttemptId(), reason);
        return applyWithdrawal(row, reason, actorUserId);
    }

    @Transactional
    public ApprovalAttemptDO resubmit(Long attemptRowId, String newAttemptId, String submissionBasis,
                                      Long actorUserId) {
        ApprovalAttemptDO previous = requireAttempt(attemptRowId);
        if (ApprovalAttemptDO.STATUS_PENDING.equals(previous.getStatus())) {
            throw new BusinessContractException("APPROVAL_ATTEMPT_STILL_PENDING",
                    "上一轮尝试仍在进行中，不能重提: " + attemptRowId);
        }
        attemptMapper.selectBySubjectAndAttempt(previous.getOwnerModule(), previous.getEntityType(),
                previous.getEntityId(), previous.getPurpose(), newAttemptId).ifPresent(row -> {
            throw new BusinessContractException("APPROVAL_ATTEMPT_EXISTS",
                    "尝试幂等键已存在: " + newAttemptId);
        });
        EntityRef subject = new EntityRef(previous.getTenantId(), previous.getOwnerModule(),
                previous.getEntityType(), previous.getEntityId());
        ApprovalExecutionPort backend = backendRegistry.requireActive();
        // 重提同样记录当前内容基准：批准只证明重提时点的内容。
        Integer contentBasis = currentContentBasis(subject, actorUserId);
        String instanceRef = backend.submit(new ApprovalSubmissionRequest(subject, previous.getPurpose(),
                newAttemptId, submissionBasis, previous.getNeutralProcessRef()));
        ApprovalAttemptDO row = new ApprovalAttemptDO();
        row.setOwnerModule(previous.getOwnerModule());
        row.setEntityType(previous.getEntityType());
        row.setEntityId(previous.getEntityId());
        row.setPurpose(previous.getPurpose());
        row.setAttemptId(newAttemptId);
        row.setSubmissionBasis(submissionBasis);
        row.setSubmissionConcurrencyBasis(contentBasis);
        row.setNeutralProcessRef(previous.getNeutralProcessRef());
        row.setBackendId(backend.backendId());
        row.setInstanceRef(instanceRef);
        row.setStatus(ApprovalAttemptDO.STATUS_PENDING);
        row.setPreviousAttemptId(previous.getId());
        try {
            attemptMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            return attemptMapper.selectBySubjectAndAttempt(previous.getOwnerModule(),
                    previous.getEntityType(), previous.getEntityId(), previous.getPurpose(), newAttemptId)
                    .orElseThrow(() -> conflict);
        }
        opinionMapper.insert(opinion(row.getId(), ApprovalOpinionDO.ACTION_SUBMIT, submissionBasis,
                actorUserId));
        return row;
    }

    @Transactional
    public ApprovalAttemptDO decide(Long attemptRowId, boolean approved, String comment, Long actorUserId) {
        ApprovalAttemptDO row = requireAttempt(attemptRowId);
        if (!ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessContractException("APPROVAL_ATTEMPT_NOT_PENDING",
                    "仅进行中的尝试可决定: " + attemptRowId);
        }
        backendRegistry.requireActive().decide(row.getInstanceRef(), row.getAttemptId(), approved, comment);
        // 引擎会同步回调 recordTrustedResult；此处重读保证回执反映落库后状态。
        return attemptMapper.selectById(attemptRowId);
    }

    @Override
    @Transactional
    public ApprovalRecordingResult recordTrustedResult(ApprovalTrustedResult result) {
        List<ApprovalAttemptDO> rows = attemptMapper.selectByInstanceRef(result.instanceRef());
        if (rows.isEmpty()) {
            throw new BusinessContractException("APPROVAL_INSTANCE_UNKNOWN",
                    "可信结果的实例关联不存在: " + result.instanceRef());
        }
        int updated = 0;
        List<String> skipped = new ArrayList<>();
        for (ApprovalAttemptDO row : rows) {
            try {
                backendRegistry.requireBackendMatches(row, result.backendId());
                if (!row.getAttemptId().equals(result.attemptId())) {
                    // 旧尝试的回调不得污染新提交。
                    skipped.add("主体 " + row.getId() + ": 尝试不一致（行 " + row.getAttemptId()
                            + " != 结果 " + result.attemptId() + "）");
                    continue;
                }
                if (ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus())) {
                    row.setStatus(result.approved() ? ApprovalAttemptDO.STATUS_APPROVED
                            : ApprovalAttemptDO.STATUS_REJECTED);
                    row.setConclusionBasis(result.conclusionBasis());
                    row.setDecidedTime(LocalDateTime.now());
                    attemptMapper.updateById(row);
                    opinionMapper.insert(opinion(row.getId(), result.approved()
                                    ? ApprovalOpinionDO.ACTION_APPROVE : ApprovalOpinionDO.ACTION_REJECT,
                            result.conclusionBasis(), null));
                    publishDecided(row, result.approved());
                    updated++;
                } else if ((result.approved()
                        && ApprovalAttemptDO.STATUS_APPROVED.equals(row.getStatus()))
                        || (!result.approved()
                        && ApprovalAttemptDO.STATUS_REJECTED.equals(row.getStatus()))) {
                    // 幂等重放：同结论重复回调不重复生效。
                    skipped.add("主体 " + row.getId() + ": 同结论重复回调（幂等跳过）");
                } else if (ApprovalAttemptDO.STATUS_WITHDRAWN.equals(row.getStatus())) {
                    // 批次成员已撤回：不并入批次结论，也不虚构其成功。
                    skipped.add("主体 " + row.getId() + ": 已撤回，不并入批次结论");
                } else {
                    throw new BusinessContractException("APPROVAL_RESULT_CONFLICT",
                            "可信结果与既有结论冲突: 尝试 " + row.getId());
                }
            } catch (BusinessContractException perRowFailure) {
                skipped.add("主体 " + row.getId() + ": " + perRowFailure.getMessage());
            }
        }
        return new ApprovalRecordingResult(updated, skipped);
    }

    @Override
    @Transactional
    public void recordWithdrawal(String backendId, String instanceRef, String attemptId, String reason) {
        for (ApprovalAttemptDO row : attemptMapper.selectByInstanceRef(instanceRef)) {
            if (!row.getAttemptId().equals(attemptId)
                    || !ApprovalAttemptDO.STATUS_PENDING.equals(row.getStatus())) {
                continue;
            }
            backendRegistry.requireBackendMatches(row, backendId);
            applyWithdrawal(row, reason, null);
        }
    }

    public ApprovalAttemptDO requireAttempt(Long attemptRowId) {
        ApprovalAttemptDO row = attemptMapper.selectById(attemptRowId);
        if (row == null) {
            throw new BusinessContractException("APPROVAL_ATTEMPT_NOT_FOUND",
                    "审批尝试不存在: " + attemptRowId);
        }
        return row;
    }

    public List<ApprovalAttemptDO> listAttempts(String ownerModule, String entityType, Long entityId,
                                                String purpose) {
        return attemptMapper.selectBySubject(ownerModule, entityType, entityId, purpose);
    }

    /**
     * 领域生效（与流程批准分列）：仅 APPROVED 尝试可执行，命令必须是目录声明的操作；
     * 成功、待恢复或失败分别落 plt_approval_effect，同幂等键重放返回既有记录。
     */
    @Transactional
    public ApprovalEffectDO executeEffect(Long attemptRowId, String operationCode, String idempotencyKey,
                                          Map<String, Object> input, Long concurrencyBasis,
                                          Long actorUserId) {
        ApprovalAttemptDO attempt = requireAttempt(attemptRowId);
        if (!ApprovalAttemptDO.STATUS_APPROVED.equals(attempt.getStatus())) {
            throw new BusinessContractException("APPROVAL_NOT_APPROVED",
                    "流程尚未批准，不能执行生效命令: " + attemptRowId);
        }
        Optional<ApprovalEffectDO> existing = effectMapper.selectByKey(attemptRowId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        BusinessModelDescriptor descriptor = catalog.require(attempt.getOwnerModule(),
                attempt.getEntityType());
        BusinessOperationDescriptor operation = descriptor.operations().stream()
                .filter(op -> op.code().equals(operationCode)).findFirst()
                .orElseThrow(() -> new BusinessContractException("OPERATION_NOT_DECLARED",
                        "操作未在目录声明: " + operationCode));
        // 生效前重读当前内容：批准所证明的是提交时点的内容；内容已变化则批准失效，
        // 不自动重审，也不自动改用最新内容（调用方须重新发起审批）。
        EntityActor actor = new EntityActor(TenantContextHolder.getRequiredTenantId(), actorUserId, null);
        BusinessEntityData data = accessPort.read(EntityDataRef.current(new EntityRef(
                TenantContextHolder.getRequiredTenantId(), attempt.getOwnerModule(),
                attempt.getEntityType(), attempt.getEntityId())), actor, "approval-effect");
        ApprovalEffectDO effect = new ApprovalEffectDO();
        effect.setAttemptRowId(attemptRowId);
        effect.setOperationCode(operationCode);
        effect.setIdempotencyKey(idempotencyKey);
        if (data == null || !data.available()) {
            effect.setConcurrencyBasis(attempt.getSubmissionConcurrencyBasis());
            effect.setStatus(ApprovalEffectDO.STATUS_FAILED);
            effect.setDetail("APPROVAL_CONTENT_UNAVAILABLE: 审批主体当前内容不可读，批准不能生效");
            effectMapper.insert(effect);
            throw new BusinessContractException("APPROVAL_CONTENT_UNAVAILABLE",
                    "审批主体当前内容不可读，批准不能生效: " + attemptRowId);
        }
        if (attempt.getSubmissionConcurrencyBasis() != null && data.concurrencyBasis() != null
                && !attempt.getSubmissionConcurrencyBasis().equals(data.concurrencyBasis().intValue())) {
            effect.setConcurrencyBasis(data.concurrencyBasis().intValue());
            effect.setStatus(ApprovalEffectDO.STATUS_FAILED);
            effect.setDetail("APPROVAL_CONTENT_CHANGED: 提交基准 " + attempt.getSubmissionConcurrencyBasis()
                    + " 与当前内容版本 " + data.concurrencyBasis() + " 不一致，批准不再适用");
            effectMapper.insert(effect);
            throw new BusinessContractException("APPROVAL_CONTENT_CHANGED",
                    "提交后内容已变化，批准不再适用于当前内容: " + attemptRowId);
        }
        Long basis = concurrencyBasis != null ? concurrencyBasis : data.concurrencyBasis();
        effect.setConcurrencyBasis(basis == null ? null : basis.intValue());
        try {
            BusinessOperationReceipt receipt = dispatcher.dispatch(new BusinessOperationRequest(
                    operation.code(), operation.version(),
                    EntityDataRef.current(new EntityRef(TenantContextHolder.getRequiredTenantId(),
                            attempt.getOwnerModule(), attempt.getEntityType(), attempt.getEntityId())),
                    attempt.getOwnerModule(), attempt.getEntityType(),
                    input == null ? Map.of() : input, idempotencyKey, basis,
                    OperationEntryKind.INDEPENDENT, "approval:" + attempt.getAttemptId()));
            if (receipt.outcome() == ReceiptOutcome.FAILED) {
                effect.setStatus(ApprovalEffectDO.STATUS_FAILED);
                effect.setDetail(receipt.failureReason());
            } else if (receipt.outcome() == ReceiptOutcome.APPROVAL_PENDING) {
                effect.setStatus(ApprovalEffectDO.STATUS_PENDING_RECOVERY);
                effect.setDetail("命令回执为 APPROVAL_PENDING，待恢复");
            } else {
                effect.setStatus(ApprovalEffectDO.STATUS_SUCCESS);
                effect.setReceiptOutcome(receipt.outcome().name());
            }
        } catch (BusinessContractException failure) {
            effect.setStatus(ApprovalEffectDO.STATUS_FAILED);
            effect.setDetail(failure.getErrorCode() + ": " + failure.getMessage());
            effectMapper.insert(effect);
            throw failure;
        }
        effectMapper.insert(effect);
        return effect;
    }

    /** 提交时读取主体的当前内容并发基准；主体不可读时显式拒绝，不空缺基准。 */
    private Integer currentContentBasis(EntityRef subject, Long actorUserId) {
        EntityActor actor = new EntityActor(subject.tenantId(), actorUserId, null);
        BusinessEntityData data = accessPort.read(EntityDataRef.current(subject), actor, "approval-submit");
        if (data == null || !data.available()) {
            throw new BusinessContractException("APPROVAL_CONTENT_UNAVAILABLE",
                    "审批主体当前内容不可读，不能提交审批: " + subject);
        }
        return data.concurrencyBasis() == null ? null : data.concurrencyBasis().intValue();
    }

    private ApprovalAttemptDO applyWithdrawal(ApprovalAttemptDO row, String reason, Long actorUserId) {        row.setStatus(ApprovalAttemptDO.STATUS_WITHDRAWN);
        row.setConclusionBasis(reason);
        row.setDecidedTime(LocalDateTime.now());
        attemptMapper.updateById(row);
        opinionMapper.insert(opinion(row.getId(), ApprovalOpinionDO.ACTION_WITHDRAW, reason, actorUserId));
        return row;
    }

    private ApprovalOpinionDO opinion(Long attemptRowId, String action, String comment, Long actorUserId) {
        ApprovalOpinionDO opinion = new ApprovalOpinionDO();
        opinion.setAttemptRowId(attemptRowId);
        opinion.setAction(action);
        opinion.setComment(comment);
        opinion.setActorUserId(actorUserId == null ? 0L : actorUserId);
        return opinion;
    }

    private void publishDecided(ApprovalAttemptDO row, boolean approved) {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"eventId\":\"" + eventId + "\",\"attemptRowId\":" + row.getId()
                + ",\"approved\":" + approved + ",\"instanceRef\":\"" + row.getInstanceRef() + "\"}";
        outbox.append("ApprovalAttempt", String.valueOf(row.getId()),
                new PlatformCommandExecutionApi.BusinessEvent(eventId, EVENT_TYPE, payload));
    }
}
