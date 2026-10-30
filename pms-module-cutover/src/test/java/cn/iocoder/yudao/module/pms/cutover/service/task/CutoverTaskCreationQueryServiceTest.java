package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.asset.api.device.*;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.*;

class CutoverTaskCreationQueryServiceTest {
    private final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectDeviceShipmentApi shipments = mock(ProjectDeviceShipmentApi.class);
    private final DeviceQueryApi devices = mock(DeviceQueryApi.class);
    private final CutoverCustomerLevelPort customer = mock(CutoverCustomerLevelPort.class);
    private final CutoverTaskQueryService tasks = mock(CutoverTaskQueryService.class);
    private final CutoverProjectContextPort projectContexts = mock(CutoverProjectContextPort.class);
    private final CutoverTaskCreationQueryService service = new CutoverTaskCreationQueryService(projects, scopes, shipments, devices, customer, tasks, projectContexts);
    private final CutoverTaskContext actor = new CutoverTaskContext(1L, 9L, "create-detail-test", true, true, false, false, false);

    @Test void optionsNeverExposeReadOnlyProjectsOrQueryWhenEditScopeIsEmpty() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertTrue(service.projects(new CutoverTaskCreationQueryService.ProjectOptionsQuery(actor, "")).isEmpty());
        verifyNoInteractions(projects);
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(101L));
        when(projects.searchOptions(any())).thenReturn(List.of(new ProjectBusinessContextApi.Option(101L,"P101","可编辑","客户"),
                new ProjectBusinessContextApi.Option(102L,"P102","只读","客户")));
        assertEquals(List.of(101L), service.projects(new CutoverTaskCreationQueryService.ProjectOptionsQuery(actor, "项目"))
                .stream().map(ProjectBusinessContextApi.Option::id).toList());
    }

    @Test void forbiddenProjectCannotReadPresentationCustomerOrDevices() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(101L, 7L, Set.of(), Set.of(101L)));
        assertEquals(DATA_SCOPE_FORBIDDEN, assertThrows(CutoverTaskApplicationException.class,
                () -> service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L))).code());
        assertThrows(CutoverTaskApplicationException.class, () -> service.devices(CutoverTaskCreationQueryService.DevicePageQuery.builder()
                .context(actor).projectId(101L).pageNo(1).pageSize(20).build()));
        verifyNoInteractions(projects, customer, shipments, devices);
    }

    @Test void projectAutofillUsesAuthoritativeOwnerPresentationAndCurrentScopeVersion() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(101L, 7L, Set.of(101L), Set.of()));
        var presentation = ProjectBusinessContextApi.Presentation.builder().projectId(101L).projectName("项目").customerName("客户")
                .projectManagerName("项目经理").serviceManagerNames("服务经理").industryName("金融").build();
        when(projects.presentation(any())).thenReturn(presentation);
        when(projectContexts.inspect(any())).thenReturn(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build());
        assertEquals(presentation, service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L)).project());
        verify(projects).presentation(argThat(value -> value.tenantId() == 1L && value.actorId() == 9L && value.expectedScopeVersion() == 7L));
    }

    @Test void projectSelectionPreservesUnconfiguredStatusBeforeAnyDeviceIsSelected() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(101L, 7L, Set.of(101L), Set.of()));
        when(projectContexts.inspect(any())).thenReturn(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).customerId(201L).build());
        when(customer.inspect(201L)).thenReturn(CutoverCustomerLevelPort.CustomerLevelFact.builder()
                .customerId(201L).status("NOT_CONFIGURED").build());
        var result = service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L));
        assertNull(result.customerServiceLevel());
        assertEquals("NOT_CONFIGURED", result.customerServiceLevelContext().status());
        verifyNoInteractions(shipments, devices, tasks);
    }

    @Test void failedCustomerLookupDoesNotTurnIntoUnconfigured() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(101L, 7L, Set.of(101L), Set.of()));
        when(projectContexts.inspect(any())).thenReturn(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).customerId(201L).build());
        when(customer.inspect(201L)).thenThrow(new CutoverOwnerFactException(
                CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE, "offline"));
        assertEquals("UNAVAILABLE", service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L))
                .customerServiceLevelContext().status());
    }

    @Test void projectWithoutCustomerAssociationAllowsManualSelectionWithoutInventingCustomer() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(101L, 7L, Set.of(101L), Set.of()));
        when(projectContexts.inspect(any())).thenReturn(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build());
        var result = service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L));
        assertEquals("NOT_CONFIGURED", result.customerServiceLevelContext().status());
        assertNull(result.customerServiceLevelContext().customerId());
        assertNull(result.customerServiceLevelContext().serviceLevelCode());
        verifyNoInteractions(customer);
    }

    @Test void unresolvedCustomerCodeIsNotTreatedAsAnAbsentAssociation() {
        var fact = CutoverContextSupport.customerReference(customer,
                CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).customerCode("UNRESOLVED").build());
        assertEquals("UNAVAILABLE", fact.status());
        verifyNoInteractions(customer);
    }

    @Test void serialResolutionRejectsUnshippedOrReassignedDevicesAndReturnsProductFields() {
        var candidate = new CutoverTaskViews.CreateContextCandidate(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build(),
                List.of(new CutoverDeviceScopePort.DeviceFact(401L,"SN-401",101L,5L)), null, null, true);
        when(tasks.resolveCreateContext(actor,List.of("SN-401"))).thenReturn(new CutoverTaskViews.CreateContextData(List.of(candidate),false,List.of(),false));
        DeviceSummaryDTO summary = mock(DeviceSummaryDTO.class);
        when(devices.getDevice(401L)).thenReturn(summary);
        when(summary.projectId()).thenReturn(101L); when(summary.projectAssignmentVersion()).thenReturn(5L);
        var query = new CutoverTaskCreationQueryService.ResolveQuery(actor,List.of("SN-401"));
        assertEquals(INVALID_REQUEST, assertThrows(CutoverTaskApplicationException.class, () -> service.resolve(query)).code());
        when(summary.shipmentTime()).thenReturn(LocalDateTime.now()); when(summary.productCode()).thenReturn("CODE"); when(summary.productModel()).thenReturn("MODEL");
        assertEquals(List.of(new ProjectDeviceShipmentApi.Device(401L,"SN-401","CODE","MODEL")), service.resolve(query).devices());
        when(summary.projectId()).thenReturn(102L);
        assertEquals(DEVICE_SCOPE_STALE, assertThrows(CutoverTaskApplicationException.class, () -> service.resolve(query)).code());
        when(summary.projectId()).thenReturn(101L); when(summary.projectAssignmentVersion()).thenReturn(6L);
        assertEquals(DEVICE_SCOPE_STALE, assertThrows(CutoverTaskApplicationException.class, () -> service.resolve(query)).code());
    }

    @Test void mixedAuthorizedAndUnauthorizedSerialsCannotSilentlyDropTheUnauthorizedInput() {
        var candidate = new CutoverTaskViews.CreateContextCandidate(CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build(),
                List.of(new CutoverDeviceScopePort.DeviceFact(401L,"SN-401",101L,5L)), null, null, true);
        when(tasks.resolveCreateContext(actor,List.of("SN-401", "SN-OUTSIDE")))
                .thenReturn(new CutoverTaskViews.CreateContextData(List.of(candidate), false, List.of(), false));
        assertEquals(DATA_SCOPE_FORBIDDEN, assertThrows(CutoverTaskApplicationException.class,
                () -> service.resolve(new CutoverTaskCreationQueryService.ResolveQuery(actor, List.of("SN-401", "SN-OUTSIDE")))).code());
        verifyNoInteractions(devices);
    }

    @Test void authorizedSerialResolutionDoesNotReadEveryDeviceSeparately() {
        var first = summary(401L, true); var second = summary(402L, true);
        serialCandidates();
        when(devices.getVisibleDeviceSummaries(any())).thenReturn(List.of(second, first));

        var result = service.resolve(new CutoverTaskCreationQueryService.ResolveQuery(actor, List.of("SN-401", "SN-402")));

        assertEquals(List.of(401L, 402L), result.devices().stream().map(ProjectDeviceShipmentApi.Device::deviceId).toList());
        verify(devices, never()).getDevice(any());
    }

    @Test void projectBoundSerialResolutionForwardsTheTrustedContextAndKeepsProductProjection() {
        var first = summary(401L, true);
        var candidate = new CutoverTaskViews.CreateContextCandidate(
                CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build(),
                List.of(new CutoverDeviceScopePort.DeviceFact(401L, "SN-401", 101L, 5L)), null, null, true);
        var ownerQuery = new CutoverTaskQueryService.CreateContextQuery(actor, 101L, List.of("SN-401"));
        when(tasks.resolveCreateContext(ownerQuery)).thenReturn(new CutoverTaskViews.CreateContextData(
                List.of(candidate), false, List.of(), true));
        when(devices.getVisibleDeviceSummaries(any())).thenReturn(List.of(first));

        var result = service.resolve(new CutoverTaskCreationQueryService.ResolveQuery(actor, List.of("SN-401"), 101L));

        assertEquals(List.of(401L), result.devices().stream().map(ProjectDeviceShipmentApi.Device::deviceId).toList());
        verify(tasks).resolveCreateContext(ownerQuery);
        verify(tasks, never()).resolveCreateContext(any(CutoverTaskContext.class), anyList());
        verify(devices).getVisibleDeviceSummaries(new cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery(
                1L, 9L, Set.of(401L), 101L));
    }

    @Test void earlierUnshippedDeviceIsNotHiddenByLaterUnauthorizedDevice() {
        var first = summary(401L, false);
        serialCandidates();
        when(devices.getVisibleDeviceSummaries(any())).thenReturn(List.of(first));
        when(devices.getDevice(402L)).thenThrow(new org.springframework.security.access.AccessDeniedException("later denied"));

        var failure = assertThrows(CutoverTaskApplicationException.class, () -> service.resolve(
                new CutoverTaskCreationQueryService.ResolveQuery(actor, List.of("SN-401", "SN-402"))));

        assertEquals(INVALID_REQUEST, failure.code());
        assertTrue(failure.getMessage().contains("SN-401"));
        verify(devices, never()).getDevice(402L);
    }

    @Test void earlierStaleAssignmentIsNotHiddenByLaterUnauthorizedDevice() {
        var first = summary(401L, true);
        when(first.projectAssignmentVersion()).thenReturn(6L);
        serialCandidates();
        when(devices.getVisibleDeviceSummaries(any())).thenReturn(List.of(first));
        when(devices.getDevice(402L)).thenThrow(new org.springframework.security.access.AccessDeniedException("later denied"));

        var failure = assertThrows(CutoverTaskApplicationException.class, () -> service.resolve(
                new CutoverTaskCreationQueryService.ResolveQuery(actor, List.of("SN-401", "SN-402"))));

        assertEquals(DEVICE_SCOPE_STALE, failure.code());
        verify(devices, never()).getDevice(402L);
    }

    private DeviceSummaryDTO summary(Long id, boolean shipped) {
        var summary = mock(DeviceSummaryDTO.class);
        when(summary.deviceId()).thenReturn(id);
        when(summary.projectId()).thenReturn(101L);
        when(summary.projectAssignmentVersion()).thenReturn(5L);
        when(summary.shipmentTime()).thenReturn(shipped ? LocalDateTime.of(2026, 10, 1, 0, 0) : null);
        return summary;
    }

    private void serialCandidates() {
        var candidate = new CutoverTaskViews.CreateContextCandidate(
                CutoverProjectContextPort.ProjectContextFact.builder().projectId(101L).build(),
                List.of(new CutoverDeviceScopePort.DeviceFact(401L, "SN-401", 101L, 5L),
                        new CutoverDeviceScopePort.DeviceFact(402L, "SN-402", 101L, 5L)), null, null, true);
        when(tasks.resolveCreateContext(actor, List.of("SN-401", "SN-402")))
                .thenReturn(new CutoverTaskViews.CreateContextData(List.of(candidate), false, List.of(), false));
    }
}
