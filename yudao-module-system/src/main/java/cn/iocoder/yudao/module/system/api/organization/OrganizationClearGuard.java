package cn.iocoder.yudao.module.system.api.organization;

import java.util.Set;

/** Business owners reject removal of organization IDs referenced by their own data. */
public interface OrganizationClearGuard {
    record Scope(Long tenantId,Set<Long> companyIds,Set<Long> departmentIds) {}
    void check(Scope scope);
}
