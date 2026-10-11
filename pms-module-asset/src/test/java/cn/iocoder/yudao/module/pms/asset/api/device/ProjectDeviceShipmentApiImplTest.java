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
    private final ProjectDeviceShipmentApiImpl api = new ProjectDeviceShipmentApiImpl(mapper);

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
}
