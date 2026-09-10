package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.BusinessArtifact;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TaskBusinessLinkFact(Long id, String objectId, String displayName, String factVersion,
        Map<String, Boolean> completionFacts, List<BusinessArtifact> artifacts, Set<String> allowedActions) {
    public TaskBusinessLinkFact {
        completionFacts = Map.copyOf(completionFacts);
        artifacts = List.copyOf(artifacts);
        allowedActions = Set.copyOf(allowedActions);
    }
}
