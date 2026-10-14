package cn.iocoder.yudao.module.pms.cutover.service.taskv2.port;

import lombok.Builder;

/** CUT 对完整项目展示与客户归属事实的预留端口。 */
public interface CutoverProjectContextPort {

    ProjectContextFact inspect(Inspection query);

    ProjectContextFact lockAndRevalidate(ProjectContextFact expected);

    @Builder
    record Inspection(Long tenantId, Long projectId, long expectedProjectScopeVersion) { }

    @Builder
    record ProjectContextFact(Long tenantId, Long projectId, int projectVersion,
                              String projectCode, String projectName,
                              Long customerId, String customerCode, String customerName,
                              Long departmentId, String departmentCode, String departmentName,
                              long projectScopeVersion) {
    }
}
