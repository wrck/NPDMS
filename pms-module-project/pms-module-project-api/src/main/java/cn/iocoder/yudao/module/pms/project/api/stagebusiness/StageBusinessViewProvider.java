package cn.iocoder.yudao.module.pms.project.api.stagebusiness;

import java.util.Set;

/** PM-03/PM-11: Owner-authorized stage page context, never a task identity or a business command. */
public interface StageBusinessViewProvider {
    String ownerContext();
    String objectType();
    Result inspectStage(Context context);

    record Context(Long tenantId, Long actorId, Long projectId, Long stageId,
                   String targetObjectKey, String instanceResolutionStrategy) { }
    record Result(Set<String> allowedActions) {
        public Result { allowedActions = Set.copyOf(allowedActions); }
    }
}
