package cn.iocoder.yudao.module.pms.project.api.migration;

/** Authorized formal project identity; relationship consumers do not require a delivery office snapshot. */
public interface ProjectMigrationIdentityContextApi {
    record Query(Long tenantId,Long actorId,Long projectId,Long expectedProjectVersion,
                 String sourceProjectKey,Long sourceConnectionId) {}
    record Context(Long projectId,Long version,String projectCode,String projectType,String companyCode,String companyName) {}
    Context resolve(Query query);
    /** Holds the project lock in the enclosing migration transaction and revalidates the exact source binding. */
    Context lockAndRevalidate(Query query);
}
