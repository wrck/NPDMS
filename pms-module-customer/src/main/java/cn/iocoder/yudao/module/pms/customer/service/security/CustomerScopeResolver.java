package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.pms.customer.dal.dataobject.security.CustomerScopeSliceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.security.CustomerScopeSliceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CustomerScopeResolver {

    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "tenant_admin", "crm_admin");

    @Resource
    private CustomerScopeSliceMapper scopeSliceMapper;

    public CustomerVisibleScope resolve(CustomerScopeRequest request) {
        validate(request);
        List<CustomerScopeSliceDO> configured = scopeSliceMapper.selectEffective(new CustomerScopeSliceQuery(
                request.tenantId(), request.userId(), request.roleIds(), LocalDateTime.now()));
        if (configured.isEmpty()) {
            boolean administrator = request.roleCodes().stream().anyMatch(ADMIN_ROLES::contains);
            return new CustomerVisibleScope(administrator, List.of());
        }
        List<CustomerScopeSlice> slices = configured.stream()
                .map(this::toSlice)
                .filter(slice -> slice != null)
                .toList();
        return new CustomerVisibleScope(false, slices);
    }

    private CustomerScopeSlice toSlice(CustomerScopeSliceDO source) {
        Set<String> departments = values(source.getDepartmentMode(), source.getDepartmentCodes());
        Set<String> markets = values(source.getMarketMode(), source.getMarketCodes());
        Set<String> systems = values(source.getSystemMode(), source.getSystemCodes());
        Set<String> expends = values(source.getExpendMode(), source.getExpendCodes());
        Set<String> industries = values(source.getIndustryMode(), source.getIndustryCodes());
        if (departments == null || markets == null || systems == null || expends == null || industries == null) {
            return null;
        }
        return new CustomerScopeSlice(departments, markets, systems, expends, industries);
    }

    private Set<String> values(String mode, String encoded) {
        if ("ALL".equals(mode)) {
            return Set.of();
        }
        if (!"SELECTED".equals(mode) || encoded == null || encoded.isBlank()) {
            return null;
        }
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(values::add);
        return values.isEmpty() ? null : Set.copyOf(values);
    }

    private void validate(CustomerScopeRequest request) {
        if (request == null || request.tenantId() == null || request.userId() == null
                || request.roleIds() == null || request.roleCodes() == null) {
            throw new IllegalArgumentException("客户权限范围请求不完整");
        }
    }
}
