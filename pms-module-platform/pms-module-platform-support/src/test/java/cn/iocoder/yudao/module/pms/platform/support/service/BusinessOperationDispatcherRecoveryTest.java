package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessOperationDispatcherRecoveryTest {
    static class NativeOwner extends DefaultBusinessApplicationService {
        NativeOwner() { super((DefaultBusinessApplicationService) null); }
    }

    @Test void nativeOperationRecoveryUsesItsServiceWithoutOpeningGenericCapabilities() {
        var defaults = mock(DefaultBusinessApplicationService.class);
        var owner = mock(NativeOwner.class);
        var dispatcher = new BusinessOperationDispatcher(mock(BusinessEntityPersistenceRegistry.class), defaults);
        dispatcher.register("SOL", "siteSurvey", owner);
        var receipt = mock(BusinessOperationReceipt.class);
        when(owner.recoverReceipt("SOL", "siteSurvey", "create", 1, "lost")).thenReturn(receipt);

        assertEquals("ENTITY_PROVIDER_UNAVAILABLE", assertThrows(BusinessContractException.class,
                () -> dispatcher.capabilityService("SOL", "siteSurvey")).getErrorCode());
        assertSame(receipt, dispatcher.recoverReceipt("SOL", "siteSurvey", "create", 1, "lost"));
        verify(owner).recoverReceipt("SOL", "siteSurvey", "create", 1, "lost");
        verifyNoInteractions(defaults);
    }

    @Test void defaultReceiptRecoveryKeepsTheDefaultService() {
        var defaults = mock(DefaultBusinessApplicationService.class);
        var dispatcher = new BusinessOperationDispatcher(mock(BusinessEntityPersistenceRegistry.class), defaults);
        var receipt = mock(BusinessOperationReceipt.class);
        when(defaults.recoverReceipt("IT", "note", "save", 1, "lost")).thenReturn(receipt);
        assertSame(receipt, dispatcher.recoverReceipt("IT", "note", "save", 1, "lost"));
    }

    @Test void anOwnerWithoutTheRecoveryContractCannotFallThroughToDefaults() {
        var defaults = mock(DefaultBusinessApplicationService.class);
        var dispatcher = new BusinessOperationDispatcher(mock(BusinessEntityPersistenceRegistry.class), defaults);
        dispatcher.register("SOL", "native", mock(AbstractBusinessApplicationService.class));
        assertEquals("RECEIPT_RECOVERY_UNAVAILABLE", assertThrows(BusinessContractException.class,
                () -> dispatcher.recoverReceipt("SOL", "native", "create", 1, "lost")).getErrorCode());
        verifyNoInteractions(defaults);
    }
}
