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

    /** Owner-authorized project-page actions (e.g. QUERY/CREATE); absence never grants permission. */
    default Set<String> inspectContext(Context context) { return Set.of(); }

    /** Optional search support. Empty means no selectable candidates, not an implicit object creation. */
    default List<BusinessObjectFact> candidates(Context context) { return List.of(); }

    BusinessObjectFact inspect(Context context, String objectId);

    /**
     * MUST use Spring Propagation.MANDATORY, join the caller's transaction, lock the Owner object,
     * recheck tenant/project/actor/action scope and expectedVersion, and retain locks until commit.
     * The caller locks PROJ project -> task -> execution contract -> links before entering Owner.
     * This method must not create or mutate the Owner's business body.
     */
    BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion);

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
