package cn.iocoder.yudao.module.pms.bindings.service;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.bindings.backend.*;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.InlineInstanceDO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScenarioExecutionServiceTest {
    private final ProcessDefinitionPort definitions = mock(ProcessDefinitionPort.class);
    private final BusinessResultPort results = mock(BusinessResultPort.class);
    private final BusinessEntityAccessPort access = mock(BusinessEntityAccessPort.class);
    private final InlineSyncBackend backend = mock(InlineSyncBackend.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<InlineSyncBackend> inline = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EventDrivenBackend> evented = mock(ObjectProvider.class);
    private final ScenarioExecutionService service = new ScenarioExecutionService(definitions, results, access, inline, evented);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(definitions.findPublished("ready")).thenReturn(Optional.of(new ProcessDefinitionSnapshot(
                "ready", 1, "ready", "PRJ", "project", "PRJ/project", 1,
                "evaluate", 1, "FIELD_CONDITION", "1", List.of(), "ready-result")));
        when(inline.getIfAvailable()).thenReturn(backend);
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void runRequiresObjectAccessBeforeReadingSystemFacts() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            when(access.read(any(), any(), anyString())).thenThrow(new BusinessContractException("ACCESS_DENIED", "out of scope"));
            assertThrows(BusinessContractException.class, () -> service.run(new ScenarioExecutionService.RunCommand("ready", 9L, "key", null)));
            verifyNoInteractions(backend);
        }
    }

    @Test void runUsesTheAuthenticatedActorAndExactLongId() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            Long id = 9007199254740993L;
            var ref = new EntityRef(7L, "PRJ", "project", id);
            when(access.read(any(), any(), anyString())).thenReturn(new BusinessEntityData(ref, null, Map.of(), 1L, true, null));
            service.run(new ScenarioExecutionService.RunCommand("ready", id, "key", null));
            verify(access).read(eq(EntityDataRef.current(ref)), eq(new EntityActor(7L, 12L, "bindings:scenario")), eq("bindings:run"));
            verify(backend).run(argThat(command -> command.ref().equals(ref) && command.idempotencyKey().equals("key")));
        }
    }

    @Test void resultInventoryFiltersUnauthorizedAndCrossTenantObjects() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            var allowed = result(new EntityRef(7L, "PRJ", "project", 9L));
            var forbidden = result(new EntityRef(7L, "PRJ", "project", 10L));
            var otherTenant = result(new EntityRef(8L, "PRJ", "project", 9L));
            when(results.inventory(any())).thenReturn(List.of(allowed, forbidden, otherTenant));
            when(access.read(eq(EntityDataRef.current(allowed.objectRef())), any(), anyString()))
                    .thenReturn(new BusinessEntityData(allowed.objectRef(), null, Map.of(), 1L, true, null));
            when(access.read(eq(EntityDataRef.current(forbidden.objectRef())), any(), anyString()))
                    .thenThrow(new BusinessContractException("ACCESS_DENIED", "out of scope"));
            assertEquals(List.of(allowed), service.listResults("ready"));
            verify(access, never()).read(eq(EntityDataRef.current(otherTenant.objectRef())), any(), anyString());
        }
    }

    @Test void instanceInventoryFiltersUnauthorizedObjects() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            doAnswer(call -> { Consumer<InlineSyncBackend> consumer = call.getArgument(0); consumer.accept(backend); return null; })
                    .when(inline).ifAvailable(any());
            var row = new InlineInstanceDO(); row.setTenantId(7L); row.setOwnerModule("PRJ");
            row.setEntityType("project"); row.setEntityId(9L);
            when(backend.listInstances("ready")).thenReturn(List.of(row));
            when(access.read(any(), any(), anyString())).thenThrow(new BusinessContractException("ACCESS_DENIED", "out of scope"));
            assertTrue(service.listInstances("ready").isEmpty());
        }
    }

    @Test void dependencyFailureIsNotMistakenForAnEmptyAuthorizedInventory() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            when(results.inventory(any())).thenReturn(List.of(result(new EntityRef(7L, "PRJ", "project", 9L))));
            when(access.read(any(), any(), anyString())).thenThrow(new IllegalStateException("database unavailable"));
            assertThrows(IllegalStateException.class, () -> service.listResults("ready"));
        }
    }

    @Test void ownerForbiddenResponseFiltersOnlyTheUnauthorizedObject() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            var allowed = result(new EntityRef(7L, "SOL", "siteSurvey", 9L));
            var forbidden = result(new EntityRef(7L, "SOL", "siteSurvey", 10L));
            when(results.inventory(any())).thenReturn(List.of(allowed, forbidden));
            when(access.read(eq(EntityDataRef.current(allowed.objectRef())), any(), anyString()))
                    .thenReturn(new BusinessEntityData(allowed.objectRef(), null, Map.of(), 1L, true, null));
            when(access.read(eq(EntityDataRef.current(forbidden.objectRef())), any(), anyString()))
                    .thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(
                            cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN));
            assertEquals(List.of(allowed), service.listResults("ready"));
        }
    }

    @Test void otherOwnerBusinessFailuresAreNotHiddenAsPermissionFiltering() {
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(12L);
            when(results.inventory(any())).thenReturn(List.of(result(new EntityRef(7L, "SOL", "siteSurvey", 9L))));
            var failure = new cn.iocoder.yudao.framework.common.exception.ServiceException(1014001001, "invalid business state");
            when(access.read(any(), any(), anyString())).thenThrow(failure);
            assertSame(failure, assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.listResults("ready")));
        }
    }

    private static BusinessResultRecord result(EntityRef ref) {
        return new BusinessResultRecord("ready-result", ref, "result-" + ref, ResultSemantics.NEW_RESULT,
                "key", null, true, null, 1L);
    }
}
