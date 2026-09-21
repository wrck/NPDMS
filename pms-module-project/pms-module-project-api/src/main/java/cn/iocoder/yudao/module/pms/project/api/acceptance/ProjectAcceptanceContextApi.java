package cn.iocoder.yudao.module.pms.project.api.acceptance;

/** Project identity and authorization for independent ACC operations; no stage or task identity. */
public interface ProjectAcceptanceContextApi {
    Context inspect(Query query);
    Context lock(Query query, Integer expectedProjectVersion, Long expectedTreeVersion);
    record Query(Long tenantId, Long projectId, Long actorId) {
        public Query {
            if (tenantId == null || projectId == null || projectId <= 0 || actorId == null || actorId <= 0)
                throw new IllegalArgumentException("ACCEPTANCE_PROJECT_CONTEXT_REQUIRED");
        }
    }
    record Context(Long projectId, Long rootProjectId, Integer projectVersion, Long treeVersion, String lifecycleStatus) { }
}
