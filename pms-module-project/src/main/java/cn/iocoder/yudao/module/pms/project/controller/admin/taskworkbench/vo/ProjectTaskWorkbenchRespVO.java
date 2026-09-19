package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import lombok.Data;

import java.util.Set;

@Data
public class ProjectTaskWorkbenchRespVO {
    private ProjectTaskDetailRespVO task;
    private Long executionContractId;
    private Integer contractVersion;
    private String bindingType;
    private String trustedTargetRef;
    private Set<String> allowedActions;
    private String factVersion;
    private String recoverableError;
    private cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.View approval;
    /** 纯订阅任务的可选只读观察；仅RESULT_SUBSCRIPTION绑定返回，不构成第二套任务完成真值。 */
    private java.util.List<cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionObservationQuery.RoundObservation> resultSubscriptions;
}
