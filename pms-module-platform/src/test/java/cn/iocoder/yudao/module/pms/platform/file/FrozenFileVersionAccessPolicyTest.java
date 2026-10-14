package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FrozenFileVersionAccessQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrozenFileVersionAccessPolicyTest {
    @Test void existingProvidersDenyHistoricalContentByDefault() {
        var provider = mock(FileBusinessObjectPolicyProvider.class, CALLS_REAL_METHODS);
        when(provider.ownerContext()).thenReturn("CUT");
        when(provider.objectType()).thenReturn("CUTOVER_PLAN");
        var registry = new FileBusinessObjectPolicyRegistry(List.of(provider));
        assertEquals(FILE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectFrozenFileVersion(query())).getCode());
        assertEquals(FILE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> registry.lockAndRevalidateFrozenFileVersion(query())).getCode());
    }

    @Test void historicalScopeCannotChangeBetweenNormalAuthorizationAndProof() {
        var provider = mock(FileBusinessObjectPolicyProvider.class);
        when(provider.ownerContext()).thenReturn("CUT");
        when(provider.objectType()).thenReturn("CUTOVER_PLAN");
        var changed = new FileBusinessObjectPolicyFact(true, 10L, "IMMUTABLE", "MULTIPLE",
                Set.of("CUTOVER_PLAN"), Set.of("application/pdf"), 100L, "INTERNAL");
        when(provider.inspectFrozenFileVersion(query())).thenReturn(changed);
        when(provider.lockAndRevalidateFrozenFileVersion(query())).thenReturn(changed);
        var registry = new FileBusinessObjectPolicyRegistry(List.of(provider));
        assertEquals(FILE_SCOPE_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> registry.inspectFrozenFileVersion(query())).getCode());
        assertEquals(FILE_SCOPE_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> registry.lockAndRevalidateFrozenFileVersion(query())).getCode());
    }

    @Test void businessReadCannotRequestAHistoricalContentGrant() {
        assertThrows(IllegalArgumentException.class, () -> new FrozenFileVersionAccessQuery(
                new FileBusinessObjectPolicyQuery(1L, 8L, "CUT", "CUTOVER_PLAN", "10", "FULL_PLAN", "slot", "READ"),
                60L, 1, 9L));
    }

    private static FrozenFileVersionAccessQuery query() {
        return new FrozenFileVersionAccessQuery(new FileBusinessObjectPolicyQuery(
                1L, 8L, "CUT", "CUTOVER_PLAN", "10", "FULL_PLAN", "slot", "DOWNLOAD"), 60L, 1, 9L);
    }
}
