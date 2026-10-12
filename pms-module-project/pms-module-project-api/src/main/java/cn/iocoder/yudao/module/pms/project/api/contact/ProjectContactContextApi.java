package cn.iocoder.yudao.module.pms.project.api.contact;

/** PROJ supplies customer affiliation and current contact-maintenance authority to CUS. */
public interface ProjectContactContextApi {
    Context inspect(Query query);
    /** Locks project scope/identity until the caller's transaction ends. */
    Context lockForWrite(WriteQuery query);
    /** Assign a stable customer identity to a previously unassociated project; never reassigns an existing customer. */
    Context associateCustomer(AssociateQuery query);
    /** 精确来源最终客户，不以名称推断购货方身份；查询仍受项目范围保护。 */
    FinalCustomer inspectFinalCustomer(Query query);
    /** 专用历史联系人迁移Owner使用，允许已闭环项目；不改变正常维护准入。 */
    FinalCustomer lockHistoricalFinalCustomer(WriteQuery query);

    record Query(Long tenantId, Long actorUserId, Long projectId) {}
    record WriteQuery(Long tenantId, Long actorUserId, Long projectId, Long expectedProjectVersion) {}
    record AssociateQuery(Long tenantId, Long actorUserId, Long projectId, Long expectedProjectVersion, Long customerId) {}
    record FinalCustomer(Long id, Long projectId, String name, String sourceProjectKey) {}
    record Context(Long projectId, Long customerId, Long projectVersion, String lifecycleStatus, boolean canManage, boolean canViewHistory) {
        public Context(Long projectId, Long customerId, Long projectVersion, String lifecycleStatus, boolean canManage) {
            this(projectId, customerId, projectVersion, lifecycleStatus, canManage, canManage);
        }
    }
}
