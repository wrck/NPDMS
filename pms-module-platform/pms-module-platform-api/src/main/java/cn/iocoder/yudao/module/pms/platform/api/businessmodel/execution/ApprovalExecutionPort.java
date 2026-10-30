package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

/** 审批执行端口：发起/撤回/重提走公共操作；更换审批执行实现无需修改实体或面板。 */
public interface ApprovalExecutionPort {

    /** 本执行实现的身份标识；公共关联服务按它路由发起并校验回调来源。 */
    String backendId();

    String submit(ApprovalSubmissionRequest request);

    void withdraw(String backendId, String instanceRef, String attemptId, String reason);

    /** 审批决定：由引擎执行真实流程，完成时经 ApprovalAssociationApi 回传可信结果。 */
    void decide(String instanceRef, String attemptId, boolean approved, String comment);
}
