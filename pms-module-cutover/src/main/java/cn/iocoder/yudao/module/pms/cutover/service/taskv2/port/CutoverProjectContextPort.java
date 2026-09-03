package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

/** CUT 对完整项目展示与客户归属事实的预留端口。 */
public interface CutoverProjectContextPort {

    ProjectContextFact inspect(Long tenantId, Long projectId, long expectedProjectScopeVersion);

    ProjectContextFact lockAndRevalidate(ProjectContextFact expected);

    record ProjectContextFact(Long tenantId, Long projectId, int projectVersion,
                              String projectCode, String projectName,
                              Long customerId, String customerCode, String customerName,
                              Long departmentId, String departmentCode, String departmentName,
                              long projectScopeVersion) {
    }
}
