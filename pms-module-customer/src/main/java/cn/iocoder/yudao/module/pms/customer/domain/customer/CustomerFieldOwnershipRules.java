package cn.iocoder.yudao.module.pms.customer.domain.customer;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CustomerFieldOwnershipRules {

    private static final Set<String> CRM_FIELDS = Set.of(
            "code", "name", "shortName", "crmLevel", "crmStatus", "salesOwnerId", "contactMobile", "contactEmail",
            "classification");
    private static final Set<String> PLATFORM_FIELDS = Set.of(
            "remark", "tags", "servicePreference", "deliveryNote");

    private CustomerFieldOwnershipRules() {
    }

    public static void validateBusinessUpdate(Set<String> changedFields, boolean crmMapped) {
        if (!crmMapped) {
            return;
        }
        Set<String> forbidden = intersection(changedFields, CRM_FIELDS);
        if (!forbidden.isEmpty()) {
            throw new IllegalArgumentException("业务身份不能修改 CRM 权威字段: " + forbidden);
        }
    }

    public static void validateCrmUpdate(Set<String> changedFields) {
        Set<String> forbidden = intersection(changedFields, PLATFORM_FIELDS);
        if (!forbidden.isEmpty()) {
            throw new IllegalArgumentException("CRM 身份不能修改平台字段: " + forbidden);
        }
    }

    private static Set<String> intersection(Set<String> fields, Set<String> candidates) {
        Set<String> result = new LinkedHashSet<>(fields);
        result.retainAll(candidates);
        return result;
    }
}
