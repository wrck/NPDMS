package cn.iocoder.yudao.module.pms.commerce.service.associated;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.associated.ProjectShipmentLogisticsMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectShipmentLogisticsServiceTest {
    final ProjectShipmentLogisticsMapper mapper = mock(ProjectShipmentLogisticsMapper.class);
    final ProjectScopeApi projects = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectShipmentLogisticsService service = new ProjectShipmentLogisticsService(mapper, projects, permissions);
    final Reference reference = new Reference("DPPMS", "P-1", "C-1");
    final Fact fact = new Fact("DPPMS", "P-1", "C-1", "快递公司", "EXP-1", "收件人");
    Query query() { return new Query(1L, 7L, 86648L, List.of(reference)); }
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void close() { TenantContextHolder.clear(); }
    void visible() {
        when(permissions.hasAnyPermissions(7L, "pms:device:query")).thenReturn(true);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(86648L, 1L, Set.of(86648L), Set.of()));
    }
    @Test void tenantMismatchCannotReachData() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.read(query()));
        verifyNoInteractions(permissions, projects, mapper);
    }
    @Test void devicePermissionIsRequired() {
        assertThrows(AccessDeniedException.class, () -> service.read(query()));
        verifyNoInteractions(projects, mapper);
    }
    @Test void placeholderScopeCannotReadLogistics() {
        visible();
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(86648L, 1L, Set.of(), Set.of(86648L)));
        assertThrows(AccessDeniedException.class, () -> service.read(query()));
        verifyNoInteractions(mapper);
    }
    @Test void emptyReferencesNeverBroadenQuery() {
        visible();
        assertEquals(List.of(), service.read(new Query(1L, 7L, 86648L, List.of())));
        verifyNoInteractions(mapper);
    }
    @Test void exactFactReturnedAndMissingFactRemainsMissing() {
        visible(); when(mapper.selectFacts(query())).thenReturn(List.of(fact), List.of());
        assertEquals(List.of(fact), service.read(query()));
        assertEquals(List.of(), service.read(query()));
    }
    @Test void ambiguousOrUnrequestedFactCannotBeSilentlyChosen() {
        visible(); when(mapper.selectFacts(query())).thenReturn(List.of(fact, fact),
                List.of(new Fact("DPPMS", "P-OTHER", "C-1", "", "", "")));
        assertThrows(IllegalStateException.class, () -> service.read(query()));
        assertThrows(IllegalStateException.class, () -> service.read(query()));
    }
}
