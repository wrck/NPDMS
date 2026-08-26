package cn.iocoder.yudao.module.pms.customer.service.guard;

import cn.iocoder.yudao.module.pms.asset.api.customer.AssetCustomerReferenceGuardApi;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceGuardApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Service
public class CustomerDeletionGuardService {

    @Resource
    private ProjectCustomerReferenceGuardApi projectGuardApi;
    @Resource
    private AssetCustomerReferenceGuardApi assetGuardApi;

    public CustomerDeletionGuardResult check(Long tenantId, Long customerId) {
        if (tenantId == null || customerId == null) {
            throw new IllegalArgumentException("客户删除守卫查询不完整");
        }
        CustomerReferenceGuardQuery query = new CustomerReferenceGuardQuery(tenantId, customerId);
        List<CustomerReferenceGuardResult> results = List.of(
                checkProvider("PROJ", query, projectGuardApi::check),
                checkProvider("AST", query, assetGuardApi::check));
        long referenceCount = results.stream().mapToLong(CustomerReferenceGuardResult::referenceCount).sum();
        CustomerReferenceGuardStatus status = aggregate(results);
        return new CustomerDeletionGuardResult(status == CustomerReferenceGuardStatus.CLEAR,
                status, referenceCount, results);
    }

    private CustomerReferenceGuardResult checkProvider(
            String provider, CustomerReferenceGuardQuery query,
            Function<CustomerReferenceGuardQuery, CustomerReferenceGuardResult> check) {
        try {
            CustomerReferenceGuardResult result = check.apply(query);
            if (result == null || result.status() == null) {
                return unknown(provider);
            }
            CustomerReferenceGuardStatus.valueOf(result.status());
            return result;
        } catch (RuntimeException ex) {
            return unknown(provider);
        }
    }

    private CustomerReferenceGuardStatus aggregate(List<CustomerReferenceGuardResult> results) {
        if (results.stream().anyMatch(result -> CustomerReferenceGuardStatus.REFERENCED.name().equals(result.status()))) {
            return CustomerReferenceGuardStatus.REFERENCED;
        }
        if (results.stream().anyMatch(result -> !CustomerReferenceGuardStatus.CLEAR.name().equals(result.status()))) {
            return CustomerReferenceGuardStatus.UNKNOWN;
        }
        return CustomerReferenceGuardStatus.CLEAR;
    }

    private CustomerReferenceGuardResult unknown(String provider) {
        return new CustomerReferenceGuardResult(CustomerReferenceGuardStatus.UNKNOWN.name(),
                provider, 0, LocalDateTime.now());
    }
}
