package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ShippedProjectQuery;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeviceShipmentApiImplTest {
    private final ProjectDeviceShipmentMapper mapper = mock(ProjectDeviceShipmentMapper.class);
    private final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi scopes = mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    private final ProjectDeviceShipmentApiImpl api = new ProjectDeviceShipmentApiImpl(mapper, scopes);

    @BeforeEach void login() {
        TenantContextHolder.setTenantId(1L);
        LoginUser user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void tenantMismatchFailsBeforeQuery() {
        assertThrows(IllegalArgumentException.class,()->api.resolveShippedProjectIds(new ProjectDeviceShipmentApi.Query(2L,Set.of(10L))));
        verifyNoInteractions(mapper);
    }
    @Test void anonymousFailsBeforeQuery() {
        SecurityContextHolder.clearContext();
        assertThrows(IllegalArgumentException.class,()->api.resolveShippedProjectIds(new ProjectDeviceShipmentApi.Query(1L,Set.of(10L))));
        verifyNoInteractions(mapper);
    }
    @Test void missingScopeFailsRatherThanLookingUnshipped() {
        assertThrows(IllegalArgumentException.class,()->api.resolveShippedProjectIds(new ProjectDeviceShipmentApi.Query(1L,null)));
        verifyNoInteractions(mapper);
    }
    @Test void emptyScopeDoesNotQuery() {
        assertEquals(Set.of(),api.resolveShippedProjectIds(new ProjectDeviceShipmentApi.Query(1L,Set.of())));
        verifyNoInteractions(mapper);
    }
    @Test void passesOnlyTenantAndCandidateScopeAndDeduplicatesProjects() {
        when(mapper.selectShippedProjectIds(new ShippedProjectQuery(1L,Set.of(10L,20L)))).thenReturn(List.of(10L,10L));
        assertEquals(Set.of(10L),api.resolveShippedProjectIds(new ProjectDeviceShipmentApi.Query(1L,Set.of(10L,20L))));
    }

    @Test void pageRequiresCurrentActorAndProjectScopeBeforeReadingAnyDevice() {
        var query = ProjectDeviceShipmentApi.DeviceQuery.builder().tenantId(1L).actorId(7L).projectId(10L).pageNo(1).pageSize(20).build();
        when(scopes.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(10L, 3L, Set.of(), Set.of(10L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> api.pageDevices(query));
        assertThrows(IllegalArgumentException.class, () -> api.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(1L).actorId(8L).projectId(10L).pageNo(1).pageSize(20).build()));
        assertThrows(IllegalArgumentException.class, () -> api.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(2L).actorId(7L).projectId(10L).pageNo(1).pageSize(20).build()));
        verifyNoInteractions(mapper);
    }

    @Test void pageReturnsProductFieldsAndRejectsInvalidPaging() {
        when(scopes.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(10L, 3L, Set.of(10L), Set.of()));
        var row = new cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO();
        row.setId(11L); row.setSn("SN-11"); row.setProductCode("CODE"); row.setProductModel("MODEL");
        when(mapper.selectProjectDevices(any())).thenReturn(List.of(row));
        when(mapper.countProjectDevices(any())).thenReturn(21L);
        var page = api.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(1L).actorId(7L).projectId(10L).keyword(" MODEL ").pageNo(2).pageSize(20).build());
        assertEquals(21L, page.total());
        assertEquals(List.of(new ProjectDeviceShipmentApi.Device(11L, "SN-11", "CODE", "MODEL")), page.list());
        verify(mapper).selectProjectDevices(argThat(query -> query.getTenantId() == 1L && query.getProjectId() == 10L
                && query.getOffset() == 20L && query.getKeyword().equals("MODEL")));
        assertThrows(IllegalArgumentException.class, () -> api.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(1L).actorId(7L).projectId(10L).pageNo(0).pageSize(20).build()));
    }
}
