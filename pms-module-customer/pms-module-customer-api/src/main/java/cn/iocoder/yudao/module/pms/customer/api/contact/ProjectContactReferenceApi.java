package cn.iocoder.yudao.module.pms.customer.api.contact;

import java.util.List;

/** 业务引用当前项目启用联系记录；引用方保存必要快照，主档变更不改写历史。 */
public interface ProjectContactReferenceApi {
    List<Reference> list(Query query);
    Reference lockAndInspect(Revalidation query);
    record Query(Long tenantId, Long actorId, Long projectId) { }
    record Revalidation(Long tenantId, Long actorId, Long projectId, Long contactId, Integer expectedVersion) { }
    record Reference(Long contactId, Integer contactVersion, Long projectId, Long sourceContactId,
                     String name, String mobile, String phone, String department, String title) { }
}
