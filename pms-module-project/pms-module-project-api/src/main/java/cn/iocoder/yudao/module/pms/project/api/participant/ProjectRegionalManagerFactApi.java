package cn.iocoder.yudao.module.pms.project.api.participant;

/** 当前区域服务经理资格，不是项目成员事实；调用方另行校验功能权限和项目动作范围。 */
public interface ProjectRegionalManagerFactApi {
    Fact inspect(Query query);
    /** 加入调用方事务，锁定项目版本及实际SYSTEM角色/组织授权来源，撤权后不能沿用预检。 */
    Fact lockAndRevalidate(RevalidationQuery query);
    record Query(Long tenantId, Long projectId, Long userId) { }
    record RevalidationQuery(Query subject, Long expectedProjectVersion) { }
    record Fact(Long projectId, Long userId, Long projectVersion) { }
}
