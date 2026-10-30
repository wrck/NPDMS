package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsDesktopApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsDispatchDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsDispatchMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDesktopSourceApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DacDesktopDispatchServiceTest {
    private final DeviceOpsDispatchMapper rows = mock(DeviceOpsDispatchMapper.class);
    private final CollectionDesktopSourceApi sources = mock(CollectionDesktopSourceApi.class);
    private final AtomicReference<DeviceOpsDispatchDO> persisted = new AtomicReference<>();
    private DacDesktopDispatchService service;

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var properties = new DacDeviceOpsGatewayProperties();
        properties.setRequestSigningKey("test-only-desktop-request-key-0123456789");
        service = new DacDesktopDispatchService(properties, rows, sources, "test-only-desktop-callback-key-0123456789");
        when(rows.selectByTenantAndPlatformTaskId(1L, "task-1")).thenAnswer(invocation -> persisted.get());
        when(rows.lockByTenantAndPlatformTaskId(1L, "task-1")).thenAnswer(invocation -> persisted.get());
        when(rows.insert(any(DeviceOpsDispatchDO.class))).thenAnswer(invocation -> { persisted.set(invocation.getArgument(0)); return 1; });
        var task = mock(CollectionTaskDTO.class);
        when(task.projectId()).thenReturn("7"); when(task.platformTaskId()).thenReturn("task-1");
        when(task.deviceId()).thenReturn("8"); when(task.deviceName()).thenReturn("test-device");
        when(task.host()).thenReturn("192.0.2.8"); when(task.port()).thenReturn(22); when(task.protocol()).thenReturn("SSH");
        when(task.templateId()).thenReturn("9"); when(task.templateVersion()).thenReturn("1");
        when(task.templateHash()).thenReturn(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("show version".getBytes(StandardCharsets.UTF_8))));
        when(task.sourceContext()).thenReturn("PLT"); when(task.sourceObjectType()).thenReturn("CollectionCenter");
        when(sources.snapshot(new CollectionDesktopSourceApi.TaskKey(1L, "task-1")))
                .thenReturn(new CollectionDesktopSourceApi.Snapshot(task, 42L, "operator", "show version", null, null, null));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void ticketContainsNoCredentialAndRejectsTamperingAndCrossTenantUse() {
        var ticket = register();
        var grant = service.resolve(ticket.value());
        assertEquals(42L, grant.actorId());
        assertEquals("7", grant.projectId());
        assertEquals("show version", ((Map<?,?>) grant.submission().get("script")).get("content"));
        assertNull(((Map<?,?>) grant.submission().get("connection")).get("password"));
        assertFalse(ticket.toString().contains(ticket.value()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.resolve(ticket.value() + "x"));
        TenantContextHolder.setTenantId(2L);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.resolve(ticket.value()));
    }

    @Test void cancellationBeforeClaimFencesExecutionAndProducesDurableProof() {
        var grant = service.resolve(register().value());
        service.cancel("task-1");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.claim(claim(grant, UUID.randomUUID().toString())));
        var result = service.query(persisted.get());
        assertEquals("CANCELLED", result.externalStatus());
        assertEquals("CANCELLED_BEFORE_DISPATCH", result.failureCategory());
        assertNull(result.externalTaskId());
        verify(sources, never()).accepted(any());
    }

    @Test void oneTaskCannotBeExecutedByTwoLocalClientsAndTerminalObservationCannotRewind() {
        var grant = service.resolve(register().value());
        String execution = UUID.randomUUID().toString();
        var claim = claim(grant, execution);
        service.claim(claim);
        assertDoesNotThrow(() -> service.claim(claim));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.claim(claim(grant, UUID.randomUUID().toString())));
        service.observe(new DacDesktopDispatchService.Observation(claim.authorization(), execution, "collection-1", "SUCCEEDED", false));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.observe(
                new DacDesktopDispatchService.Observation(claim.authorization(), execution, "collection-1", "EXECUTING", false)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.authenticateCallback(
                new DacDesktopDispatchService.CallbackAuthorization(claim.authorization(), "collection-other")));
        verify(sources, times(1)).accepted(any());
    }

    @Test void readTicketNeverCarriesExecutionOrCallbackCapability() {
        register();
        var ticket = service.access(new DeviceOpsDesktopApi.Access(1L, 50L, "7", "TASK_READ", "task-1", null));
        var grant = service.resolve(ticket.value());
        assertEquals("TASK_READ", grant.operation());
        assertNull(grant.submission());
        assertNull(grant.taskToken());
        assertFalse(persisted.get().getDesktopClaimed());
    }

    @Test void connectionTicketFreezesDeviceAndProtocolAndRejectsMissingScope() {
        var scope=new DeviceOpsDesktopApi.ConnectionScope("9007199254740993","SSH2");
        var ticket=service.access(new DeviceOpsDesktopApi.Access(1L,50L,"7","CONNECTIONS_READ",null,scope));
        var grant=service.resolve(ticket.value());
        assertEquals(scope,grant.connectionScope());
        assertNull(grant.submission()); assertNull(grant.taskToken());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.access(
                new DeviceOpsDesktopApi.Access(1L,50L,"7","CONNECTION_SAVE",null,null)));
    }

    @Test void cancellationAfterClaimAlsoRejectsSameClientExecutionRetry() {
        var grant=service.resolve(register().value());
        var claim=claim(grant, UUID.randomUUID().toString());
        service.claim(claim);
        service.cancel("task-1");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.claim(claim));
        assertTrue(service.control(claim).cancelRequested());
    }

    @Test void desktopTicketsRequireTheSameSigningKeyStrengthAsServerDispatch() {
        var properties=new DacDeviceOpsGatewayProperties();
        properties.setRequestSigningKey("weak");
        assertThrows(IllegalArgumentException.class, () -> new DacDesktopDispatchService(properties,rows,sources,"test-only-desktop-callback-key-0123456789"));
    }

    private DeviceOpsDesktopApi.Ticket register() { return service.register(new DeviceOpsDesktopApi.Registration(1L, 42L, "7", "task-1")); }
    private DacDesktopDispatchService.Claim claim(DacDesktopDispatchService.Grant grant, String execution) {
        return new DacDesktopDispatchService.Claim(new DacDesktopDispatchService.Authorization("task-1", grant.taskToken()), execution);
    }
}
