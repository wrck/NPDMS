package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.servicelevel.CustomerServiceLevelCurrentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException.Code.*;

@Service
@RequiredArgsConstructor
public class CustomerServiceLevelCurrentApiImpl implements CustomerServiceLevelCurrentApi {
    private final CustomerServiceLevelCurrentMapper records;
    private final CustomerMasterMapper customers;
    private final Clock clock;

    @Override
    public Fact inspect(Query query) {
        requireCustomer(query);
        return current(new Selection(query, records.selectCustomerRecords(query), LocalDate.now(clock)));
    }

    private void requireCustomer(Query query) {
        if (query == null || query.tenantId() == null || query.tenantId() <= 0
                || query.customerId() == null || query.customerId() <= 0) throw failure(INVALID_REQUEST, "客户等级查询身份无效");
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw failure(TENANT_CONTEXT_MISMATCH, "客户等级查询租户不一致");
        }
        var customer = customers.selectById(query.customerId());
        if (customer == null || !Objects.equals(query.tenantId(), customer.getTenantId())) {
            throw failure(CUSTOMER_NOT_FOUND, "客户不存在");
        }
    }

    private record Selection(Query query, List<CustomerServiceLevelDO> rows, LocalDate today) { }

    private Fact current(Selection selection) {
        var active = selection.rows().stream().filter(row -> Objects.equals(row.getStatus(), 1)).toList();
        for (var row : active) {
            if (row.getValidFrom() != null && row.getValidTo() != null && row.getValidTo().isBefore(row.getValidFrom())) {
                throw failure(OWNER_DATA_CORRUPTED, "客户服务等级有效期无效，请修正维护记录");
            }
        }
        var current = active.stream().filter(row -> (row.getValidFrom() == null || !selection.today().isBefore(row.getValidFrom()))
                && (row.getValidTo() == null || !selection.today().isAfter(row.getValidTo()))).toList();
        if (current.size() > 1) throw failure(OWNER_DATA_CORRUPTED, "客户存在多个当前有效服务等级，请修正维护记录");
        var fact = Fact.builder().tenantId(selection.query().tenantId()).customerId(selection.query().customerId());
        if (current.isEmpty()) return fact.status(Status.NOT_CONFIGURED).build();
        var row = current.getFirst();
        if (row.getVersion() == null || row.getVersion() < 0 || row.getLevel() == null || row.getLevel().isBlank()) {
            throw failure(OWNER_DATA_CORRUPTED, "客户服务等级记录不完整");
        }
        return fact.status(Status.AVAILABLE).sourceRecordId(row.getId()).sourceRecordVersion(row.getVersion())
                .serviceLevelCode(row.getLevel()).validFrom(row.getValidFrom()).validTo(row.getValidTo()).build();
    }

    private CustomerServiceLevelFactException failure(CustomerServiceLevelFactException.Code code, String message) {
        return new CustomerServiceLevelFactException(code, message);
    }
}
