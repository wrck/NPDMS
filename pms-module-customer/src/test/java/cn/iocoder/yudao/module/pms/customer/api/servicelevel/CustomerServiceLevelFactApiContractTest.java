package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFact;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactQuery;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactResult;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.CustomerServiceLevelFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto.ExpectedCustomerServiceLevelFact;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerServiceLevelFactApiContractTest {

    @Test
    void apiExposesOnlyCurrentInspectionAndLockedRevalidation() {
        List<Method> methods = Arrays.stream(CustomerServiceLevelFactApi.class.getDeclaredMethods())
                .sorted(java.util.Comparator.comparing(Method::getName)).toList();
        assertEquals(List.of("inspectCurrent", "lockAndRevalidate"),
                methods.stream().map(Method::getName).toList());
        assertEquals(CustomerServiceLevelFactQuery.class, methods.get(0).getParameterTypes()[0]);
        assertEquals(CustomerServiceLevelFactResult.class, methods.get(0).getReturnType());
        assertEquals(CustomerServiceLevelFactRevalidationQuery.class, methods.get(1).getParameterTypes()[0]);
        assertEquals(CustomerServiceLevelFactResult.class, methods.get(1).getReturnType());
    }

    @Test
    void availableFactCarriesTheCompleteComparableRevision() {
        CustomerServiceLevelFact fact = availableFact(7L, "LEVEL_1");
        CustomerServiceLevelFactResult result = new CustomerServiceLevelFactResult(
                CustomerServiceLevelFactResult.Decision.AVAILABLE, fact);
        CustomerServiceLevelFactRevalidationQuery revalidation =
                new CustomerServiceLevelFactRevalidationQuery(1L, 10L, expected(fact));

        assertEquals(CustomerServiceLevelFactResult.Decision.AVAILABLE, result.decision());
        assertEquals(91L, revalidation.expectedFact().serviceLevelRevisionId());
        assertEquals("LEVEL_1", revalidation.expectedFact().serviceLevelCode());
        assertEquals(7L, revalidation.expectedFact().factVersion());
    }

    @Test
    void notConfiguredIsARealComparableFactWithoutPlaceholders() {
        CustomerServiceLevelFact fact = new CustomerServiceLevelFact(
                CustomerServiceLevelFact.Status.NOT_CONFIGURED,
                1L, 10L, null, null, 0L, null, null);
        CustomerServiceLevelFactResult result = new CustomerServiceLevelFactResult(
                CustomerServiceLevelFactResult.Decision.NOT_CONFIGURED, fact);

        assertEquals(CustomerServiceLevelFactResult.Decision.NOT_CONFIGURED, result.decision());
        assertEquals(0L, result.currentFact().factVersion());
        assertNull(result.currentFact().serviceLevelRevisionId());
        assertNull(result.currentFact().effectiveFrom());
    }

    @Test
    void staleRevalidationCarriesTheCompleteCurrentFactForRefresh() {
        CustomerServiceLevelFact current = availableFact(8L, "LEVEL_2");
        CustomerServiceLevelFactResult result = new CustomerServiceLevelFactResult(
                CustomerServiceLevelFactResult.Decision.STALE, current);

        assertEquals(CustomerServiceLevelFactResult.Decision.STALE, result.decision());
        assertEquals("LEVEL_2", result.currentFact().serviceLevelCode());
        assertEquals(8L, result.currentFact().factVersion());
    }

    @Test
    void publicFailuresRemainClosed() {
        assertEquals(List.of("CUSTOMER_NOT_FOUND", "INVALID_REQUEST", "OWNER_DATA_CORRUPTED",
                        "PROVIDER_UNAVAILABLE", "TENANT_CONTEXT_MISMATCH"),
                Arrays.stream(CustomerServiceLevelFactException.Code.values())
                        .map(Enum::name).sorted().toList());
    }

    private static CustomerServiceLevelFact availableFact(long factVersion, String code) {
        return new CustomerServiceLevelFact(CustomerServiceLevelFact.Status.AVAILABLE,
                1L, 10L, 91L, code, factVersion,
                LocalDateTime.of(2026, 8, 31, 10, 0),
                LocalDateTime.of(2027, 8, 31, 10, 0));
    }

    private static ExpectedCustomerServiceLevelFact expected(CustomerServiceLevelFact fact) {
        return new ExpectedCustomerServiceLevelFact(fact.status(), fact.tenantId(), fact.customerId(),
                fact.serviceLevelRevisionId(), fact.serviceLevelCode(), fact.factVersion(),
                fact.effectiveFrom(), fact.effectiveTo());
    }
}
