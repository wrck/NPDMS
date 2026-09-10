package cn.iocoder.yudao.module.pms.project.api.contact;

/** PROJ supplies customer affiliation and current contact-maintenance authority to CUS. */
public interface ProjectContactContextApi {
    Context inspect(Query query);
    /** Locks project scope/identity until the caller's transaction ends. */
    Context lockForWrite(WriteQuery query);
    /** Assign a stable customer identity to a previously unassociated project; never reassigns an existing customer. */
    Context associateCustomer(AssociateQuery query);

    record Query(Long tenantId, Long actorUserId, Long projectId) {}
    record WriteQuery(Long tenantId, Long actorUserId, Long projectId, Integer expectedProjectVersion) {}
    record AssociateQuery(Long tenantId, Long actorUserId, Long projectId, Integer expectedProjectVersion, Long customerId) {}
    record Context(Long projectId, Long customerId, Integer projectVersion, String lifecycleStatus, boolean canManage, boolean canViewHistory) {
        public Context(Long projectId, Long customerId, Integer projectVersion, String lifecycleStatus, boolean canManage) {
            this(projectId, customerId, projectVersion, lifecycleStatus, canManage, canManage);
        }
    }
}
