package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileReferenceSetPolicyBatchTest {
    @Test
    void defaultProviderKeepsPerSetAuthorizationAndRegistrySeparatesOwners() {
        var sol = provider("SOL");
        var acc = provider("ACC");
        var first = query("SOL", "a");
        var second = query("SOL", "b");
        var third = query("ACC", "a");
        doReturn(policy(true, 7L)).when(sol).inspectReferenceSet(first);
        doReturn(policy(true, 8L)).when(sol).inspectReferenceSet(second);
        doReturn(policy(true, 9L)).when(acc).inspectReferenceSet(third);

        var facts = new FileBusinessObjectPolicyRegistry(List.of(sol, acc))
                .inspectReferenceSets(List.of(first, second, third));

        assertEquals(7L, facts.get(first).scopeVersion());
        assertEquals(8L, facts.get(second).scopeVersion());
        assertEquals(9L, facts.get(third).scopeVersion());
        verify(sol).inspectReferenceSets(List.of(first, second));
        verify(acc).inspectReferenceSets(List.of(third));
    }

    @Test
    void missingAndForeignBatchFactsCannotAuthorizeTheCollection() {
        var owner = provider("SOL");
        var expected = query("SOL", "a");
        var registry = new FileBusinessObjectPolicyRegistry(List.of(owner));
        doReturn(Map.of()).when(owner).inspectReferenceSets(List.of(expected));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectReferenceSets(List.of(expected))).getCode());
        doReturn(Map.of(query("SOL", "b"), policy(true, 7L)))
                .when(owner).inspectReferenceSets(List.of(expected));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectReferenceSets(List.of(expected))).getCode());
    }

    @Test
    void oneDeniedFieldRejectsTheCollection() {
        var owner = provider("SOL");
        var first = query("SOL", "a");
        var second = query("SOL", "b");
        doReturn(Map.of(first, policy(true, 7L), second, policy(false, 7L)))
                .when(owner).inspectReferenceSets(List.of(first, second));
        var registry = new FileBusinessObjectPolicyRegistry(List.of(owner));
        assertEquals(FILE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectReferenceSets(List.of(first, second))).getCode());
    }

    @Test
    void unavailableProviderStillFailsClosed() {
        var owner = provider("SOL");
        var query = query("SOL", "a");
        doThrow(new IllegalStateException("unavailable")).when(owner).inspectReferenceSets(List.of(query));
        var registry = new FileBusinessObjectPolicyRegistry(List.of(owner));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectReferenceSets(List.of(query))).getCode());
    }

    private FileBusinessObjectPolicyProvider provider(String owner) {
        var provider = mock(FileBusinessObjectPolicyProvider.class, CALLS_REAL_METHODS);
        when(provider.ownerContext()).thenReturn(owner);
        when(provider.objectType()).thenReturn("RESULT");
        return provider;
    }

    private FileBusinessObjectReferenceSetQuery query(String owner, String purpose) {
        return new FileBusinessObjectReferenceSetQuery(0L, 9L,
                new FileReferenceSetKey(owner, "RESULT", "31", purpose), FileActionCodes.READ);
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long version) {
        return new FileBusinessObjectPolicyFact(allowed, version, "IMMUTABLE", "MULTIPLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }
}
