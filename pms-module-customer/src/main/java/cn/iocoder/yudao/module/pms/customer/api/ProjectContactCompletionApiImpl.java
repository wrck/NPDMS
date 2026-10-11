package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactCompletionApi;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ProjectCustomerContactMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.ProjectContactCompletionQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.contact.ContactRules;
import cn.iocoder.yudao.module.pms.customer.service.contact.ContactValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectContactCompletionApiImpl implements ProjectContactCompletionApi {
    private final CustomerMasterMapper customers;
    private final ProjectCustomerContactMapper contacts;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Fact lockAndInspect(Query query) {
        if (query == null || query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0)
            throw new IllegalArgumentException("PROJECT_CONTACT_FACT_SCOPE_INVALID");
        // Existing project customer ID takes precedence; code is only a stable fallback, never a name match.
        var customer = query.customerId() != null ? customers.selectById(query.customerId())
                : query.customerCode() == null || query.customerCode().isBlank() ? null
                : customers.selectByTenantIdAndCode(query.tenantId(), query.customerCode());
        if (customer == null) return new Fact(false, java.util.List.of());
        if (!Objects.equals(customer.getTenantId(), query.tenantId()) || Boolean.TRUE.equals(customer.getDeleted()))
            throw new IllegalArgumentException("PROJECT_CONTACT_CUSTOMER_UNAVAILABLE");
        var evidence = new ArrayList<Evidence>();
        for (var row : contacts.selectCompletionForUpdate(new ProjectContactCompletionQuery(query.tenantId(), query.projectId(), customer.getId()))) {
            if (!Objects.equals(row.getTenantId(), query.tenantId()) || !Objects.equals(row.getProjectId(), query.projectId())
                    || !Objects.equals(row.getCustomerId(), customer.getId()))
                throw new IllegalArgumentException("PROJECT_CONTACT_FACT_IDENTITY_MISMATCH");
            if (!Integer.valueOf(0).equals(row.getStatus()) || Boolean.TRUE.equals(row.getDeleted())) continue;
            try {
                ContactRules.normalize(new ContactValues(row.getName(), row.getDepartment(), row.getTitle(), row.getMobile(),
                        row.getPhone(), row.getEmail(), row.getRemark()), 0);
            } catch (IllegalArgumentException invalid) { continue; }
            if (row.getId() == null || row.getVersion() == null) throw new IllegalArgumentException("PROJECT_CONTACT_FACT_VERSION_MISSING");
            evidence.add(new Evidence(row.getId(), row.getVersion()));
        }
        return new Fact(!evidence.isEmpty(), evidence);
    }
}
