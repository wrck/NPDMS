package cn.iocoder.yudao.module.pms.customer.service.guard;

import cn.iocoder.yudao.module.pms.asset.api.customer.AssetCustomerReferenceGuardApi;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardResult;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceGuardApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDeletionGuardServiceTest {

    @Mock
    private ProjectCustomerReferenceGuardApi projectGuardApi;
    @Mock
    private AssetCustomerReferenceGuardApi assetGuardApi;

    @InjectMocks
    private CustomerDeletionGuardService service;

    @Test
    void allProvidersClearAllowsDeletion() {
        when(projectGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "PROJ", 0));
        when(assetGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "AST", 0));

        CustomerDeletionGuardResult result = service.check(1L, 100L);

        assertTrue(result.allowed());
        assertEquals(CustomerReferenceGuardStatus.CLEAR, result.status());
        assertEquals(2, result.providerResults().size());
    }

    @Test
    void referencedProviderBlocksDeletion() {
        when(projectGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.REFERENCED, "PROJ", 3));
        when(assetGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "AST", 0));

        CustomerDeletionGuardResult result = service.check(1L, 100L);

        assertFalse(result.allowed());
        assertEquals(CustomerReferenceGuardStatus.REFERENCED, result.status());
        assertEquals(3, result.referenceCount());
    }

    @Test
    void unknownProviderBlocksDeletion() {
        when(projectGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.UNKNOWN, "PROJ", 0));
        when(assetGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "AST", 0));

        CustomerDeletionGuardResult result = service.check(1L, 100L);

        assertFalse(result.allowed());
        assertEquals(CustomerReferenceGuardStatus.UNKNOWN, result.status());
    }

    @Test
    void providerExceptionFailsClosedAsUnknown() {
        when(projectGuardApi.check(any())).thenThrow(new IllegalStateException("PROJ unavailable"));
        when(assetGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "AST", 0));

        CustomerDeletionGuardResult result = service.check(1L, 100L);

        assertFalse(result.allowed());
        assertEquals(CustomerReferenceGuardStatus.UNKNOWN, result.status());
        assertEquals("PROJ", result.providerResults().getFirst().provider());
    }

    @Test
    void nullProviderResultFailsClosedAsUnknown() {
        when(projectGuardApi.check(any())).thenReturn(null);
        when(assetGuardApi.check(any())).thenReturn(result(CustomerReferenceGuardStatus.CLEAR, "AST", 0));

        CustomerDeletionGuardResult result = service.check(1L, 100L);

        assertFalse(result.allowed());
        assertEquals(CustomerReferenceGuardStatus.UNKNOWN, result.status());
    }

    private CustomerReferenceGuardResult result(CustomerReferenceGuardStatus status, String provider, long count) {
        return new CustomerReferenceGuardResult(status.name(), provider, count, LocalDateTime.now());
    }
}
