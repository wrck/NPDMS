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
    private final CutoverTaskCreationQueryService service = new CutoverTaskCreationQueryService(projects, scopes, shipments, devices, customer, tasks);
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
        when(projects.inspect(any())).thenReturn(ProjectBusinessContextApi.Context.builder().projectId(101L).build());
        assertEquals(presentation, service.project(new CutoverTaskCreationQueryService.ProjectQuery(actor, 101L)).project());
        verify(projects).presentation(argThat(value -> value.tenantId() == 1L && value.actorId() == 9L && value.expectedScopeVersion() == 7L));
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
}
