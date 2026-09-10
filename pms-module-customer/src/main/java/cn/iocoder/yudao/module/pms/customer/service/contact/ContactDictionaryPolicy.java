package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContactDictionaryPolicy {
    public static final String TITLE = "pms_contact_title";
    /** Customer-side contact classification, never an RBAC role or project-member assignment. */
    public static final String CUSTOMER_ROLE = "pms_customer_contact_role";
    private final DictDataApi dictionaries;

    /** Retain historical values; only a new or changed selection must be currently selectable. */
    public void validateChanges(ContactValues next, ContactValues previous) {
        validate(TITLE, next.title(), previous == null ? null : previous.title());
        validate(CUSTOMER_ROLE, next.roleCode(), previous == null ? null : previous.roleCode());
    }
    private void validate(String type, String next, String previous) {
        if (next != null && !next.isBlank() && !Objects.equals(next, previous)) {
            dictionaries.validateDictDataList(type, List.of(next));
        }
    }
}
