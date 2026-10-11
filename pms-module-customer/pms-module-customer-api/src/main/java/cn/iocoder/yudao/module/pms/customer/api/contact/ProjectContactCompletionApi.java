package cn.iocoder.yudao.module.pms.customer.api.contact;

import java.util.List;

/** Internal, unattended Owner fact. Caller retains the current PROJ project/round locks.
 * No browser action grant, contact body or lifecycle write is exposed. */
public interface ProjectContactCompletionApi {
    Fact lockAndInspect(Query query);
    record Query(Long tenantId, Long projectId, Long customerId, String customerCode) { }
    record Evidence(Long contactId, Integer version) { }
    record Fact(boolean completed, List<Evidence> records) {
        public Fact { records = List.copyOf(records); }
    }
}
