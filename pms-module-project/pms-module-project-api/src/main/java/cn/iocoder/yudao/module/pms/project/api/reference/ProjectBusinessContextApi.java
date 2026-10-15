package cn.iocoder.yudao.module.pms.project.api.reference;

import lombok.Builder;

/** 项目业务消费者的授权展示快照；不以项目生命周期作为独立业务的准入条件。 */
public interface ProjectBusinessContextApi {
    /** 当前查询范围；服务经理范围仅来自有效成员事实，不由管理员权限推导。 */
    AccessScope currentActorScope(ActorQuery query);
    /** 当前可见项目的名称/编码包含查询，按名称、ID排序，最多100项。 */
    java.util.List<Option> searchOptions(OptionsQuery query);
    Context inspect(Query query);
    /** 当前项目展示资料；不参与已冻结业务事实的相等性或版本重验。 */
    Presentation presentation(Query query);
    /** 调用方写事务内按项目树、项目的顺序锁定并核对期望版本。 */
    Context lockAndRevalidate(Revalidation query);
    ServiceManagers serviceManagers(Query query);
    ServiceManagers lockServiceManagers(Revalidation query);
    /** 已授权项目当前冻结计划的实施前置适用性，不读取其他模块业务表。 */
    ImplementationRequirements implementationRequirements(Query query);
    ImplementationRequirements lockImplementationRequirements(Revalidation query);

    record ImplementationRequirements(Long planVersionId, Long planVersion, boolean configured,
                                      java.util.Set<String> requiredSourceCodes) {
        public ImplementationRequirements {
            var supported = java.util.Set.of("EXE_01", "EXE_02", "EXE_03", "EXE_04");
            if (requiredSourceCodes == null || !supported.containsAll(requiredSourceCodes)
                    || configured && (planVersionId == null || planVersionId <= 0 || planVersion == null || planVersion < 0)
                    || !configured && !supported.equals(requiredSourceCodes))
                throw new IllegalArgumentException("PROJECT_IMPLEMENTATION_REQUIREMENTS_INVALID");
            requiredSourceCodes = java.util.Set.copyOf(requiredSourceCodes);
        }
    }

    @Builder
    record Query(Long tenantId, Long actorId, Long projectId, Long expectedScopeVersion) {}
    @Builder
    record Revalidation(Query query, Long expectedProjectVersion) {}
    @Builder
    record Context(Long tenantId, Long projectId, Long projectVersion, Long scopeVersion,
                   String projectCode, String projectName, Long customerId, String customerCode,
                   String customerName, Long departmentId, String departmentCode, String departmentName,
                   String majorProjectLevel) {}
    record ServiceManagers(Long projectVersion, java.util.List<Long> userIds) {}
    @Builder
    record ActorQuery(Long tenantId, Long actorId) {}
    record AccessScope(java.util.Set<Long> visibleProjectIds, java.util.Set<Long> serviceManagedProjectIds) {
        public AccessScope {
            visibleProjectIds = java.util.Set.copyOf(visibleProjectIds);
            serviceManagedProjectIds = java.util.Set.copyOf(serviceManagedProjectIds);
        }
    }
    @Builder
    record OptionsQuery(Long tenantId, Long actorId, String keyword) {}
    record Option(Long id, String code, String name, String customerName) {}
    @Builder
    record Presentation(Long projectId, String projectCode, String projectName, String customerName,
                        String projectManagerName, String serviceManagerNames, String salesNames,
                        String departmentName, String industryName, String majorProjectLevel) {}
}
