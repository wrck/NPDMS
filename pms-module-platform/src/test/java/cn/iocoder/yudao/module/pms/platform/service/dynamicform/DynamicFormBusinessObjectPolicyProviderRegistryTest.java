package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class DynamicFormBusinessObjectPolicyProviderRegistryTest {

    @Test
    void exactProviderAndActionAreRequired() {
        DynamicFormBusinessObjectPolicyProvider provider = mock(DynamicFormBusinessObjectPolicyProvider.class);
        DynamicFormProviderKey key = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        DynamicFormOwnerKey owner = new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "11");
        DynamicFormInstancePolicyQuery query = new DynamicFormInstancePolicyQuery(1L, 2L, key, owner, 21L,
                DynamicFormBusinessAction.PATCH);
        when(provider.providerKey()).thenReturn(key);
        when(provider.inspectInstanceOwnerPolicy(query)).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.PATCH, true, null, 4L, "DRAFT:4"));
        DynamicFormBusinessObjectPolicyProviderRegistry registry =
                new DynamicFormBusinessObjectPolicyProviderRegistry(List.of(provider));

        assertThat(registry.inspectInstance(query).scopeVersion()).isEqualTo(4L);

        when(provider.inspectInstanceOwnerPolicy(query)).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.READ, true, null, 4L, "DRAFT:4"));
        assertThatThrownBy(() -> registry.inspectInstance(query)).isInstanceOf(ServiceException.class);
    }

    @Test
    void duplicateProviderFailsClosed() {
        DynamicFormBusinessObjectPolicyProvider first = mock(DynamicFormBusinessObjectPolicyProvider.class);
        DynamicFormBusinessObjectPolicyProvider second = mock(DynamicFormBusinessObjectPolicyProvider.class);
        DynamicFormProviderKey key = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        when(first.providerKey()).thenReturn(key);
        when(second.providerKey()).thenReturn(key);
        DynamicFormBusinessObjectPolicyProviderRegistry registry =
                new DynamicFormBusinessObjectPolicyProviderRegistry(List.of(first, second));
        DynamicFormInstancePolicyQuery query = new DynamicFormInstancePolicyQuery(1L, 2L, key,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "11"), 21L,
                DynamicFormBusinessAction.READ);

        assertThatThrownBy(() -> registry.inspectInstance(query)).isInstanceOf(ServiceException.class);
    }

    @Test
    void revisionRevalidationUsesThePolicyFrozenBeforeAnyPltLock() {
        DynamicFormBusinessObjectPolicyProvider provider = mock(DynamicFormBusinessObjectPolicyProvider.class);
        DynamicFormProviderKey key = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        DynamicFormRevisionPolicyQuery query = new DynamicFormRevisionPolicyQuery(0L, 9L, key,
                10L, 11L, 1, 2, "PRE_04_REQUIREMENT_ANALYSIS",
                DynamicFormBusinessAction.REVISION_FROZEN_USE, List.of());
        DynamicFormPolicyFact fact = new DynamicFormPolicyFact(DynamicFormBusinessAction.REVISION_FROZEN_USE,
                true, null, 2L, "PRE04_SCHEMA_COMPATIBLE");
        when(provider.providerKey()).thenReturn(key);
        when(provider.inspectRevisionCompatibility(query)).thenReturn(fact);
        DynamicFormBusinessObjectPolicyProviderRegistry registry =
                new DynamicFormBusinessObjectPolicyProviderRegistry(List.of(provider));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(registry.inspectRevision(query)).isEqualTo(fact);
            assertThat(registry.revalidateRevision(query, fact)).isEqualTo(fact);
            verify(provider, times(1)).inspectRevisionCompatibility(query);
        } finally {
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(0));
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
