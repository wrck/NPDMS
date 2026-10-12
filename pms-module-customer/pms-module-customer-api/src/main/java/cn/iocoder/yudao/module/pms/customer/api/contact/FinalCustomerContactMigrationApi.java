package cn.iocoder.yudao.module.pms.customer.api.contact;

import java.time.LocalDateTime;
import java.util.List;

/** CUS owns formal final-user contact facts; callers supply verified migration source values. */
public interface FinalCustomerContactMigrationApi {
    Preview preview(Query query);
    Result carry(Command command);
    void verify(Query query, Result result);

    record Query(Long tenantId, Long actorId, Long projectId, Long expectedProjectVersion) { }
    record FinalCustomer(Long id, Long projectId, String name, String sourceProjectKey) { }
    record Preview(FinalCustomer finalCustomer, String existingContactsChecksum, int existingContactCount) { }
    record HistoricalContact(String sourceKey, String sourceProjectKey, String projectType, String memberRole,
            String contactCode, String name, String phone, String email, String registrationSource,
            LocalDateTime createTime, String creator, LocalDateTime updateTime, String updater,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo) { }
    record Command(Query query, Long projectPartyId, String existingContactsChecksum, List<HistoricalContact> contacts) { }
    record Target(String sourceKey, Long id, String formalFieldsChecksum) { }
    record Result(List<Target> targets) { }
}
