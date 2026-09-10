package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import java.util.List;
import java.util.Set;

public record TaskBusinessContext(Long taskId, Long projectId, Long executionContractId,
        Integer contractVersion, String ownerContext, String objectType, String componentKey,
        Long businessViewRevisionId, String instanceResolutionStrategy,
        List<TaskBusinessLinkFact> links, Set<String> allowedActions, String recoverableError, String factVersion,
        Set<String> ownerActions, BusinessViewRevision businessView) {
    public TaskBusinessContext {
        links = List.copyOf(links);
        allowedActions = Set.copyOf(allowedActions);
        ownerActions = Set.copyOf(ownerActions);
    }
}
