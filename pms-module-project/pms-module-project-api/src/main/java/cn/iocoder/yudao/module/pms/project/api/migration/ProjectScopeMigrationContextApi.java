package cn.iocoder.yudao.module.pms.project.api.migration;

/** Formal project/office context for approved historical scopes, including closed projects. */
public interface ProjectScopeMigrationContextApi {
    Context resolve(Query query);
    Context lockAndRevalidate(Query query);
    record Query(Long tenantId,Long actorId,Long projectId,Long expectedProjectVersion,String sourceProjectKey,Long sourceConnectionId) {
        public Query(Long tenantId,Long actorId,Long projectId,Long expectedProjectVersion,String sourceProjectKey) {
            this(tenantId,actorId,projectId,expectedProjectVersion,sourceProjectKey,null);
        }
    }
    record Context(Long projectId,Long projectVersion,String projectCode,String projectName,
                   String companyCode,String companyName,String customerCode,String customerName,
                   String managerEmployeeNo,String managerName,Long departmentId,String departmentCode,
                   String departmentName,Integer departmentVersion) {}
}
