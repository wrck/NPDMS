package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.BindApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.BindApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.ApprovalAssociationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalExecutionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalSubmissionRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批执行实现：本地单步决定引擎。发起按 attemptId 幂等（重复发起返回同一实例），
 * 决定走真实状态机并同步回传可信结果；重复回调由公共服务幂等吸收。
 * 可用 pms.bindings.approvals.local.enabled=false 整体移除以验证可替换性。
 */
@Service
@ConditionalOnProperty(name = "pms.bindings.approvals.local.enabled",
        havingValue = "true", matchIfMissing = true)
public class LocalApprovalBackend implements ApprovalExecutionPort {

    public static final String BACKEND_ID = "approval-local";

    private final BindApprovalInstanceMapper instanceMapper;
    private final ApprovalAssociationApi associationApi;

    public LocalApprovalBackend(BindApprovalInstanceMapper instanceMapper,
                                ApprovalAssociationApi associationApi) {
        this.instanceMapper = instanceMapper;
        this.associationApi = associationApi;
    }

    @Override
    public String backendId() {
        return BACKEND_ID;
    }

    @Override
    @Transactional
    public String submit(ApprovalSubmissionRequest request) {
        BindApprovalInstanceDO existing = instanceMapper.selectByAttemptHint(request.attemptId())
                .orElse(null);
        if (existing != null) {
            if (!existing.getPurpose().equals(request.purpose())) {
                throw new BusinessContractException("APPROVAL_ATTEMPT_KEY_REUSED",
                        "尝试幂等键已被其他用途使用: " + request.attemptId());
            }
            return existing.getInstanceRef();
        }
        BindApprovalInstanceDO row = new BindApprovalInstanceDO();
        row.setInstanceRef("al-" + UUID.randomUUID());
        row.setAttemptHint(request.attemptId());
        row.setPurpose(request.purpose());
        row.setStatus(BindApprovalInstanceDO.STATUS_PENDING);
        try {
            instanceMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            return instanceMapper.selectByAttemptHint(request.attemptId())
                    .orElseThrow(() -> conflict).getInstanceRef();
        }
        return row.getInstanceRef();
    }

    @Override
    @Transactional
    public void withdraw(String backendId, String instanceRef, String attemptId, String reason) {
        BindApprovalInstanceDO row = requireInstance(instanceRef);
        if (!BindApprovalInstanceDO.STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessContractException("APPROVAL_INSTANCE_NOT_PENDING",
                    "流程实例不在进行中，不能撤回: " + instanceRef);
        }
        row.setStatus(BindApprovalInstanceDO.STATUS_WITHDRAWN);
        row.setDecisionComment(reason);
        row.setDecidedTime(LocalDateTime.now());
        instanceMapper.updateById(row);
        associationApi.recordWithdrawal(backendId, instanceRef, attemptId, reason);
    }

    @Override
    @Transactional
    public void decide(String instanceRef, String attemptId, boolean approved, String comment) {
        BindApprovalInstanceDO row = requireInstance(instanceRef);
        if (!BindApprovalInstanceDO.STATUS_PENDING.equals(row.getStatus())) {
            throw new BusinessContractException("APPROVAL_INSTANCE_NOT_PENDING",
                    "流程实例不在进行中，不能决定: " + instanceRef);
        }
        row.setStatus(approved ? BindApprovalInstanceDO.STATUS_APPROVED
                : BindApprovalInstanceDO.STATUS_REJECTED);
        row.setDecisionComment(comment);
        row.setDecidedTime(LocalDateTime.now());
        instanceMapper.updateById(row);
        // 可信结果同步回传公共服务校验落库；回调重试时公共服务按同结论幂等跳过。
        associationApi.recordTrustedResult(new ApprovalTrustedResult(BACKEND_ID,
                "approval-local:" + row.getId(), instanceRef, attemptId, approved, comment));
    }

    public BindApprovalInstanceDO requireInstance(String instanceRef) {
        return instanceMapper.selectByInstanceRef(instanceRef)
                .orElseThrow(() -> new BusinessContractException("APPROVAL_INSTANCE_UNKNOWN",
                        "流程实例不存在: " + instanceRef));
    }
}
