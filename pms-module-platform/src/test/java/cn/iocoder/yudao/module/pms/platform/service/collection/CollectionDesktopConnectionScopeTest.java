package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsDesktopApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionDesktopConnectionScopeTest {
    CollectionApplicationService application=mock(CollectionApplicationService.class);
    CollectionAuthorization authorization=mock(CollectionAuthorization.class);
    DeviceOpsDesktopApi desktop=mock(DeviceOpsDesktopApi.class);
    CollectionDesktopApplicationService service=new CollectionDesktopApplicationService(application,authorization,desktop);
    CollectionApplicationApi.SourceQuery source=new CollectionApplicationApi.SourceQuery("center",7L,42L);

    @BeforeEach void tenant(){ TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear(){ TenantContextHolder.clear(); }

    @Test void deviceScopeComesFromAuthorizedSourceAndUsesCurrentProtocol() {
        when(application.desktopConnectionContext(any())).thenReturn(context(9L));
        service.access(new CollectionDesktopApplicationService.Access(source,"CONNECTIONS_READ",null,9L,"SSH"));
        verify(application).desktopConnectionContext(new CollectionApplicationService.DesktopConnectionContext(source,9L));
        verify(desktop).access(new DeviceOpsDesktopApi.Access(1L,42L,"7","CONNECTIONS_READ",null,
                new DeviceOpsDesktopApi.ConnectionScope("9","SSH2")));
        verify(authorization).permission(42L,"pms:device-credential:use");
    }

    @Test void deviceMismatchAndInvalidProtocolCannotIssueTickets() {
        when(application.desktopConnectionContext(any())).thenReturn(context(9L));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"CONNECTION_SAVE",null,10L,"SSH")));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"CONNECTIONS_READ",null,9L,"SERIAL")));
        verifyNoInteractions(desktop);
    }

    @Test void projectFallbackHasNoInventedDeviceAndRetainsSavePermissions() {
        when(application.desktopConnectionContext(any())).thenReturn(context(null));
        service.access(new CollectionDesktopApplicationService.Access(source,"CONNECTION_SAVE",null,null,"TELNET"));
        verify(desktop).access(new DeviceOpsDesktopApi.Access(1L,42L,"7","CONNECTION_SAVE",null,
                new DeviceOpsDesktopApi.ConnectionScope(null,"TELNET")));
        verify(authorization).permission(42L,"pms:device-credential:create");
        verify(authorization).project(42L,7L,true,false);
    }

    @Test void missingOperationProtocolOrTaskIdentifierIsAnActionableRejection() {
        assertThrows(CollectionOperationException.class,()->service.access(null));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,null,null,null,null)));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"CONNECTIONS_READ",null,null,null)));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"TASK_READ",null,null,null)));
        assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"CONNECTION_SAVE",null,0L,"SSH")));
        verifyNoInteractions(application,desktop);
    }

    @Test void missingProjectCannotBecomeANullPointerWhenIssuingTicket() {
        when(application.desktopConnectionContext(any())).thenReturn(CollectionSourceAdapter.Source.builder().deviceId(9L).build());
        var failure=assertThrows(CollectionOperationException.class,()->service.access(
                new CollectionDesktopApplicationService.Access(source,"CONNECTIONS_READ",null,9L,"SSH")));
        assertTrue(failure.getMessage().contains("缺少项目"));
        verifyNoInteractions(desktop);
    }

    @Test void httpMetadataRequiresCompleteTargetAndVersionButAcceptsVersionZero() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            var empty=new cn.iocoder.yudao.module.pms.platform.controller.admin.collection.CollectionDesktopController.Metadata(
                    null,null,null,null,null,null,null,null,null,null,null,null);
            var fields=validator.validate(empty).stream().map(v->v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(fields.containsAll(java.util.Set.of("requestKey","expectedVersion","host","port","protocol","username")));
            var complete=new cn.iocoder.yudao.module.pms.platform.controller.admin.collection.CollectionDesktopController.Metadata(
                    "scope-test-request-123456",0L,9L,"192.0.2.10",22,"SSH","fixture","show version",null,null,null,null);
            assertTrue(validator.validate(complete).isEmpty());
        }
    }

    private CollectionSourceAdapter.Source context(Long device) {
        return CollectionSourceAdapter.Source.builder().projectId(7L).deviceId(device).canExecute(true).build();
    }
}
