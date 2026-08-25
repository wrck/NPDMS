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
}
