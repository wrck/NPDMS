package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleCandidateFactMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ActorRoleMembershipQuery;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoleCandidateActorFactApiTest {
    private final RoleCandidateFactMapper memberships = mock(RoleCandidateFactMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final RoleCandidateFactApiImpl api = new RoleCandidateFactApiImpl(memberships, roles);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); TenantContextHolder.setIgnore(false); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void foreignTenantAndIgnoredTenantCannotReachEitherMapper() {
        assertThrows(IllegalArgumentException.class, () -> api.inspectRole(new RoleCandidateFactApi.Query(2L, "probSupporter")));
        assertThrows(IllegalArgumentException.class, () -> api.inspectActor(new RoleCandidateFactApi.ActorQuery(2L, 8L)));
        assertThrows(IllegalArgumentException.class, () -> api.lockAndInspectActor(new RoleCandidateFactApi.ActorQuery(2L, 8L)));
        TenantContextHolder.setIgnore(true);
        assertThrows(IllegalArgumentException.class, () -> api.inspectRole(new RoleCandidateFactApi.Query(1L, "probSupporter")));
        assertThrows(IllegalArgumentException.class, () -> api.inspectActor(new RoleCandidateFactApi.ActorQuery(1L, 8L)));
        assertThrows(IllegalArgumentException.class, () -> api.lockAndInspectActor(new RoleCandidateFactApi.ActorQuery(1L, 8L)));
        verifyNoInteractions(memberships, roles);
    }
    @Test void nullOrNonPositiveActorCannotReachMapper() {
        assertThrows(IllegalArgumentException.class, () -> api.inspectActor(null));
        for (Long actor : new Long[]{null, 0L, -1L}) {
            var query = new RoleCandidateFactApi.ActorQuery(1L, actor);
            assertThrows(IllegalArgumentException.class, () -> api.inspectActor(query));
            assertThrows(IllegalArgumentException.class, () -> api.lockAndInspectActor(query));
        }
        verifyNoInteractions(memberships, roles);
    }
    @Test void missingDisabledOrForeignRoleHasNoReference() {
        var query = new RoleCandidateFactApi.Query(1L, "probSupporter");
        assertNull(api.inspectRole(query));
        RoleDO disabled = role(1L, 1);
        when(roles.selectByCode("probSupporter")).thenReturn(disabled);
        assertNull(api.inspectRole(query));
        when(roles.selectByCode("probSupporter")).thenReturn(role(2L, 0));
        assertNull(api.inspectRole(query));
        verifyNoInteractions(memberships);
    }
    @Test void enabledRoleReturnsOnlyItsStableReference() {
        when(roles.selectByCode("probSupporter")).thenReturn(role(1L, 0));
        assertEquals(new RoleCandidateFactApi.RoleReference(20L, "probSupporter"),
                api.inspectRole(new RoleCandidateFactApi.Query(1L, "probSupporter")));
        verifyNoInteractions(memberships);
    }
    @Test void emptyActorMembershipsStayEmptyWithoutExpandingToRolePool() {
        var scenario = new ActorRoleMembershipQuery(1L, 8L, false);
        when(memberships.selectActorMemberships(scenario)).thenReturn(List.of());
        assertTrue(api.inspectActor(new RoleCandidateFactApi.ActorQuery(1L, 8L)).isEmpty());
        verify(memberships).selectActorMemberships(scenario);
        verify(memberships, never()).selectCandidates(any());
        verifyNoInteractions(roles);
    }
    @Test void lockingQueryIsRestrictedToCurrentActorAndKeepsReturnedMembershipFact() {
        var fact = new RoleCandidateFactApi.ActorMembership(8L, 20L, "probSupporter", 30L, 40L);
        var scenario = new ActorRoleMembershipQuery(1L, 8L, true);
        when(memberships.selectActorMemberships(scenario)).thenReturn(List.of(fact));
        assertEquals(List.of(fact), api.lockAndInspectActor(new RoleCandidateFactApi.ActorQuery(1L, 8L)));
        verify(memberships).selectActorMemberships(scenario);
        verify(memberships, never()).selectCandidates(any());
        verifyNoInteractions(roles);
    }
    private static RoleDO role(Long tenant, Integer status) {
        RoleDO role = new RoleDO(); role.setId(20L); role.setTenantId(tenant);
        role.setCode("probSupporter"); role.setStatus(status); return role;
    }
}
