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
    /** 模板冻结的本任务交付件（ACC应交根行），只读展示；满足状态由满足/验收链维护。 */
    private java.util.List<TaskDeliverableItem> deliverables;

    @Data
    public static class TaskDeliverableItem {
        private Long id;
        private String deliverableCode;
        private String name;
        private Boolean required;
        private String status;
    }
}
