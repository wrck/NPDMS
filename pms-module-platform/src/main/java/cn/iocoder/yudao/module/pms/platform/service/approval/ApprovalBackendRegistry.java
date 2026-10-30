package cn.iocoder.yudao.module.pms.platform.service.approval;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalExecutionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalSubmissionRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 审批执行实现注册表：公共关联服务只依赖中性端口，按 backendId 路由；
 * 未配置或实现未装配时显式拒绝发起（APPROVAL_BACKEND_UNAVAILABLE），不静默降级。
 */
@Component
public class ApprovalBackendRegistry {

    private final ObjectProvider<List<ApprovalExecutionPort>> portsProvider;
    private final String configuredBackendId;

    public ApprovalBackendRegistry(ObjectProvider<List<ApprovalExecutionPort>> portsProvider,
                                   @org.springframework.beans.factory.annotation.Value(
                                           "${pms.plat.approvals.backend-id:approval-local}") String configuredBackendId) {
        this.portsProvider = portsProvider;
        this.configuredBackendId = configuredBackendId;
    }

    public ApprovalExecutionPort requireActive() {
        List<ApprovalExecutionPort> ports = portsProvider.getIfAvailable(() -> List.of());
        Optional<ApprovalExecutionPort> match = ports.stream()
                .filter(port -> configuredBackendId.equals(port.backendId()))
                .findFirst();
        if (match.isEmpty()) {
            throw new BusinessContractException("APPROVAL_BACKEND_UNAVAILABLE",
                    "审批执行实现未装配: " + configuredBackendId);
        }
        return match.get();
    }

    /** 回调校验：结果必须来自关联记录登记的同一执行实现。 */
    public void requireBackendMatches(ApprovalAttemptDO attempt, String resultBackendId) {
        if (!attempt.getBackendId().equals(resultBackendId)) {
            throw new BusinessContractException("APPROVAL_BACKEND_MISMATCH",
                    "可信结果来源与尝试登记的执行实现不一致: 尝试 " + attempt.getId());
        }
    }

    public static ApprovalSubmissionRequest submissionFor(EntityRef subject, String purpose,
                                                          String attemptId, String submissionBasis,
                                                          String neutralProcessRef) {
        return new ApprovalSubmissionRequest(subject, purpose, attemptId, submissionBasis, neutralProcessRef);
    }
}
