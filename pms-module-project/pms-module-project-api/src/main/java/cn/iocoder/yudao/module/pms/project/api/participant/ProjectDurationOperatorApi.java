package cn.iocoder.yudao.module.pms.project.api.participant;

/** 当前工期办理资格；组织授权不构造成员事实。调用方仍负责功能权限与 PROJECT_MANAGE。 */
public interface ProjectDurationOperatorApi {
    Fact inspect(Query query);
    /** 必须加入调用方事务，锁定当前项目版本及实际授权来源，不能使用预检代替。 */
    Fact lockAndRevalidate(RevalidationQuery query);
    /** 首次工期还要求项目已有当前有效PM；后续变更不追加此首次前置。 */
    Fact lockInitialAndRevalidate(RevalidationQuery query);
    record Query(Long tenantId, Long projectId, Long userId) { }
    record RevalidationQuery(Query subject, Long expectedProjectVersion) { }
    record Fact(Long projectId, Long userId, String lifecycleStatus, Long projectVersion, String basis) { }
}
