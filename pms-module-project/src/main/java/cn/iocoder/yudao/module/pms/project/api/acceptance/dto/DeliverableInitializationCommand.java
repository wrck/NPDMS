package cn.iocoder.yudao.module.pms.project.api.acceptance.dto;

import java.util.List;

public record DeliverableInitializationCommand(
        long tenantId,
        long projectId,
        long templateRevisionId,
        List<DeliverableRequirementSnapshot> requirements) {

    public DeliverableInitializationCommand {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }
}
