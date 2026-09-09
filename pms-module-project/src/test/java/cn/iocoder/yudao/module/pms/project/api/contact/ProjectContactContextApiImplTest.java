package cn.iocoder.yudao.module.pms.project.api.contact;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectContactContextApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectAncestorQueryApi ancestors = mock(ProjectAncestorQueryApi.class);
    private final AuthorizationGrantApi grants = mock(AuthorizationGrantApi.class);
    private final ProjectContactContextApiImpl api = new ProjectContactContextApiImpl(projects,members,scopes,ancestors,grants,mock(CustomerQueryApi.class));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var row = new ProjectMasterDO(); row.setId(7L); row.setTenantId(1L); row.setCustomerId(8L); row.setVersion(2); row.setLifecycleStatus("ACTIVE");
        when(projects.selectById(7L)).thenReturn(row); when(projects.selectByIdForUpdate(7L)).thenReturn(row);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(ancestors.getAncestors(any())).thenReturn(new ProjectAncestorResult(7L,7L,1L,List.of()));
        when(grants.listEffective(any())).thenReturn(List.of());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void visibleServiceRoleAloneDoesNotGrantContactMaintenance() {
        when(members.selectParticipantFacts(any())).thenReturn(List.of());
        assertFalse(api.inspect(new ProjectContactContextApi.Query(1L,3L,7L)).canManage());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2)));
    }
    @Test void managerCanMaintainOnlyWithCurrentProjectVersion() {
        when(members.selectParticipantFacts(any())).thenReturn(List.of(new ProjectMemberAssignmentDO()));
        var context = api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,2));
        assertTrue(context.canManage()); assertEquals(8L, context.customerId());
        assertThrows(RuntimeException.class, () -> api.lockForWrite(new ProjectContactContextApi.WriteQuery(1L,3L,7L,1)));
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectContactContextApi.Query(2L,3L,7L)));
    }
}
