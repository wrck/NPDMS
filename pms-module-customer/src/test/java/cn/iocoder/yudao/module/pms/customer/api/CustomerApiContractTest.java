package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.module.pms.customer.api.guard.CustomerReferenceGuardApi;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.CustomerMasterDataApi;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataResult;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerApiContractTest {

    @Test
    void exposesStableApiMethods() throws Exception {
        assertEquals(CustomerSummaryDTO.class,
                CustomerQueryApi.class.getMethod("getCustomer", Long.class).getReturnType());
        assertEquals(List.class,
                CustomerQueryApi.class.getMethod("getCustomers", Collection.class).getReturnType());
        assertEquals(CustomerMasterDataResult.class,
                CustomerMasterDataApi.class.getMethod("apply", CustomerMasterDataCommand.class).getReturnType());
        assertEquals(CustomerReferenceGuardResult.class,
                CustomerReferenceGuardApi.class.getMethod("check", CustomerReferenceGuardQuery.class).getReturnType());
    }

    @Test
    void masterDataCommandCarriesSourceAndIdempotencyIdentity() {
        assertRecordFields(CustomerMasterDataCommand.class,
                "tenantId", "customerId", "sourceKey", "sourceVersion", "operationId");
    }

    @Test
    void guardResultCarriesAvailabilityAndFreshness() {
        assertRecordFields(CustomerReferenceGuardResult.class,
                "status", "provider", "referenceCount", "dataAsOf");
    }

    private static void assertRecordFields(Class<?> type, String... expectedFields) {
        Set<String> fields = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(fields.containsAll(Set.of(expectedFields)), () -> type.getSimpleName() + " fields: " + fields);
    }
}
