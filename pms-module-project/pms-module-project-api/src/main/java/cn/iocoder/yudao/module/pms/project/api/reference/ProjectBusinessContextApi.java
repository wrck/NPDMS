package cn.iocoder.yudao.module.pms.project.api.reference;

import lombok.Builder;

/** 项目业务消费者的授权展示快照；不以项目生命周期作为独立业务的准入条件。 */
public interface ProjectBusinessContextApi {
    Context inspect(Query query);
    /** 调用方写事务内按项目树、项目的顺序锁定并核对期望版本。 */
    Context lockAndRevalidate(Revalidation query);
    ServiceManagers serviceManagers(Query query);
    ServiceManagers lockServiceManagers(Revalidation query);

    @Builder
    record Query(Long tenantId, Long actorId, Long projectId, Long expectedScopeVersion) {}
    @Builder
    record Revalidation(Query query, Long expectedProjectVersion) {}
    @Builder
    record Context(Long tenantId, Long projectId, Long projectVersion, Long scopeVersion,
                   String projectCode, String projectName, Long customerId, String customerCode,
                   String customerName, Long departmentId, String departmentCode, String departmentName) {}
    record ServiceManagers(Long projectVersion, java.util.List<Long> userIds) {}
}
