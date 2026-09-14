package cn.iocoder.yudao.module.pms.project.api.taskbusiness;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner-authenticated business references for the approved project-template upgrade (PM-03 / PM-11).
 * Implementations are Spring beans keyed by ownerContext + objectType. No business body is copied.
 * Every result MUST belong to context.tenantId/projectId and be visible to context.actorId;
 * inspect must reject missing, foreign-project and unauthorized objects rather than return dummy facts.
 */
public interface TaskBusinessObjectProvider {
    String ownerContext();
    String objectType();

    /** Deployment contract metadata only; MUST NOT query, create or mutate business objects. */
    default Set<String> completionFactCodes() { return Set.of(); }

    /** Deployment metadata: true only when lockStageCompletionFact implements the declared facts for stage receivers.
     * Does not inspect business data, grant access or assert that any condition is satisfied. */
    default boolean supportsStageCompletionFacts() { return false; }

    /** Optional human-readable labels for completion facts; keys SHOULD be a subset of completionFactCodes. */
    default Map<String, String> completionFactLabels() { return Map.of(); }

    /** Owner-authorized project-page actions (e.g. QUERY/CREATE); absence never grants permission. */
    default Set<String> inspectContext(Context context) { return Set.of(); }

    /** Optional search support. Empty means no selectable candidates, not an implicit object creation. */
    default List<BusinessObjectFact> candidates(Context context) { return List.of(); }

    BusinessObjectFact inspect(Context context, String objectId);

    /**
     * One read of Owner page actions and the requested linked objects. Providers may
     * reuse facts within this call only; every object retains inspect's authorization
     * and identity checks. This read does not replace lockAndRevalidate for commands.
     */
    default BusinessObjectInspection inspectContextAndObjects(Context context, List<String> objectIds) {
        var actions = inspectContext(context);
        return new BusinessObjectInspection(actions, objectIds.stream().map(id -> inspect(context, id)).toList());
    }

    record BusinessObjectInspection(Set<String> allowedActions, List<BusinessObjectFact> objects) {
        public BusinessObjectInspection {
            allowedActions = Set.copyOf(allowedActions);
            objects = List.copyOf(objects);
        }
    }

    /**
     * MUST use Spring Propagation.MANDATORY, join the caller's transaction, lock the Owner object,
     * recheck tenant/project/actor/action scope and expectedVersion, and retain locks until commit.
     * The caller locks PROJ project -> task -> execution contract -> links before entering Owner.
     * This method must not create or mutate the Owner's business body.
     */
    BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion);

    /**
     * Unattended rule evaluation: no browser identity, body values or action grants.
     * Join the existing transaction, revalidate the exact PROJ execution, lock the Owner result,
     * and require evidence from this execution round. Null means unavailable, never false.
     */
    default CompletionFact lockCompletionFact(CompletionContext context, String objectId) { return null; }

    /** Same Owner result for a stage receiver; unsupported providers return unavailable, never satisfied. */
    default CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) { return null; }

    /**
     * Current project business objects matching this binding. No task round is an association criterion.
     * Page by ascending stable object ID; null means the Owner cannot resolve associations, not an empty set.
     */
    default List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) { return null; }

    record AssociationContext(Long tenantId, Long projectId, String targetObjectKey, String bindingParameters) { }
    record AssociationCandidate(String objectId, String factVersion) { }

    record CompletionContext(Long tenantId,
            cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext execution) { }

    record StageCompletionContext(Long tenantId,
            cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext execution) { }

    record CompletionFact(String objectId, String factVersion, boolean handlingCompleted, Map<String, Boolean> completionFacts) {
        public CompletionFact { completionFacts = Map.copyOf(completionFacts); }
    }

    record Context(Long tenantId, Long actorId, Long projectId, Long taskId, String correlationId) {}

    record BusinessObjectFact(String objectId, String displayName, String factVersion,
                              Set<String> allowedActions, Map<String, Boolean> completionFacts,
                              List<BusinessArtifact> artifacts) {
        public BusinessObjectFact {
            allowedActions = Set.copyOf(allowedActions);
            completionFacts = Map.copyOf(completionFacts);
            artifacts = List.copyOf(artifacts);
        }
    }

    /** Public immutable reference only: referenceKey is an opaque Owner key, never URL/blob/secret. */
    record BusinessArtifact(String artifactId, Integer versionNo, String referenceKey,
                            String displayName, String sourceVersion) {}
}
