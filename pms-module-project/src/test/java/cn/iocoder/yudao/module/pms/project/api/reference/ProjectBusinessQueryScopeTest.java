package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectBusinessOptionsQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessQueryScopeTest {
    private static final Long TENANT_ID = 7L;
    private static final Long ACTOR_ID = 13L;
    private static final ProjectBusinessContextApi.ActorQuery ACTOR =
            new ProjectBusinessContextApi.ActorQuery(TENANT_ID, ACTOR_ID);
    private static final ProjectAllScopeQuery ALL_SCOPE =
            new ProjectAllScopeQuery(TENANT_ID, ACTOR_ID, ProjectScopeApi.ACTION_VIEW);

    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectBusinessContextApiImpl service = spy(new ProjectBusinessContextApiImpl(projects, scopes, members));

    @BeforeEach
    void setTenant() { TenantContextHolder.setTenantId(TENANT_ID); }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void invalidActorsAndForeignOrMissingTenantContextsCannotReadEitherBatchEndpoint() {
        var invalidActors = new ProjectBusinessContextApi.ActorQuery[] {
                null,
                new ProjectBusinessContextApi.ActorQuery(null, ACTOR_ID),
                new ProjectBusinessContextApi.ActorQuery(8L, ACTOR_ID),
                new ProjectBusinessContextApi.ActorQuery(TENANT_ID, null),
                new ProjectBusinessContextApi.ActorQuery(TENANT_ID, 0L),
                new ProjectBusinessContextApi.ActorQuery(TENANT_ID, -1L)
        };
        for (var invalid : invalidActors) {
            assertForbidden(() -> service.currentActorScope(invalid));
            var options = invalid == null ? null : new ProjectBusinessContextApi.OptionsQuery(
                    invalid.tenantId(), invalid.actorId(), "project");
            assertForbidden(() -> service.searchOptions(options));
        }
        TenantContextHolder.clear();
        assertForbidden(() -> service.currentActorScope(ACTOR));
        assertForbidden(() -> service.searchOptions(new ProjectBusinessContextApi.OptionsQuery(TENANT_ID, ACTOR_ID, "")));
        verifyNoInteractions(scopes, projects, members);
    }

    @Test
    void emptyScopeReturnsNoManagedProjectsWithoutLoadingMembersOrProjects() {
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(Set.of());

        assertEquals(new ProjectBusinessContextApi.AccessScope(Set.of(), Set.of()), service.currentActorScope(ACTOR));

        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verifyNoMoreInteractions(scopes);
        verifyNoInteractions(members, projects);
        verifyNoPerProjectAuthorization();
    }

    @Test
    void sixThousandVisibleProjectsStillUseOneScopeAndOneActiveMembershipQuery() {
        var visible = LongStream.rangeClosed(1, 6687).boxed().collect(Collectors.toSet());
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(visible);
        var startedAt = LocalDateTime.now();
        when(members.selectActiveByUser(any(ActiveProjectMemberQuery.class))).thenAnswer(call -> {
            ActiveProjectMemberQuery query = call.getArgument(0);
            assertEquals(TENANT_ID, query.tenantId());
            assertEquals(ACTOR_ID, query.userId());
            assertNotNull(query.effectiveAt());
            assertFalse(query.effectiveAt().isBefore(startedAt));
            assertFalse(query.effectiveAt().isAfter(LocalDateTime.now()));
            return List.of(member(6687L, ProjectMemberRoles.SERVICE_MANAGER, "PRIMARY"));
        });

        var result = service.currentActorScope(ACTOR);

        assertEquals(6687, result.visibleProjectIds().size());
        assertEquals(visible, result.visibleProjectIds());
        assertEquals(Set.of(6687L), result.serviceManagedProjectIds());
        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verify(members).selectActiveByUser(any(ActiveProjectMemberQuery.class));
        verifyNoMoreInteractions(scopes, members);
        verifyNoInteractions(projects);
        verifyNoPerProjectAuthorization();
    }

    @Test
    void managedScopeIntersectsVisibleProjectsWithPrimaryServiceRolesIncludingLegacyCodes() {
        var visible = Set.of(21L, 22L, 23L, 24L, 25L, 26L, 27L);
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(visible);
        // selectActiveByUser supplies the active interval facts; SQL interval filtering is tested separately.
        when(members.selectActiveByUser(any(ActiveProjectMemberQuery.class))).thenAnswer(call -> {
            ActiveProjectMemberQuery query = call.getArgument(0);
            assertEquals(TENANT_ID, query.tenantId());
            assertEquals(ACTOR_ID, query.userId());
            assertNotNull(query.effectiveAt());
            return List.of(
                    member(21L, ProjectMemberRoles.SERVICE_MANAGER, "PRIMARY"),
                    member(22L, ProjectMemberRoles.LEGACY_SERVICE_MANAGER_L1, null),
                    member(23L, ProjectMemberRoles.LEGACY_SERVICE_MANAGER_L2, "PRIMARY"),
                    member(24L, ProjectMemberRoles.PROJECT_MANAGER, "PRIMARY"),
                    member(25L, ProjectMemberRoles.SERVICE_MANAGER, "SECONDARY"),
                    member(26L, ProjectMemberRoles.SERVICE_MANAGER, "COLLABORATOR"),
                    member(27L, ProjectMemberRoles.TEAM_MEMBER, "PRIMARY"),
                    member(28L, ProjectMemberRoles.SERVICE_MANAGER, "PRIMARY"));
        });

        assertEquals(new ProjectBusinessContextApi.AccessScope(visible, Set.of(21L, 22L, 23L)),
                service.currentActorScope(ACTOR));

        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verify(members).selectActiveByUser(any(ActiveProjectMemberQuery.class));
        verifyNoMoreInteractions(scopes, members);
        verifyNoInteractions(projects);
        verifyNoPerProjectAuthorization();
    }

    @Test
    void projectOptionsUseOneOwnerMapperQueryWithTheCompleteScopeAndTrimmedLiteralKeyword() {
        var visible = Set.of(21L, 22L);
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(visible);
        var expected = new ProjectBusinessOptionsQuery(TENANT_ID, visible, "项目%_");
        when(projects.selectBusinessOptions(any(ProjectBusinessOptionsQuery.class))).thenAnswer(call -> {
            assertEquals(expected, call.getArgument(0));
            return List.of(project(22L, "P22", "项目22", "客户22"), project(21L, "P21", "项目21", "客户21"));
        });

        assertEquals(List.of(new ProjectBusinessContextApi.Option(22L, "P22", "项目22", "客户22"),
                        new ProjectBusinessContextApi.Option(21L, "P21", "项目21", "客户21")),
                service.searchOptions(new ProjectBusinessContextApi.OptionsQuery(TENANT_ID, ACTOR_ID, "  项目%_  ")));

        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verify(projects).selectBusinessOptions(expected);
        verifyNoMoreInteractions(scopes, projects);
        verifyNoInteractions(members);
        verifyNoPerProjectAuthorization();
    }

    @Test
    void missingOptionKeywordIsNormalizedWithoutResolvingMemberships() {
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(Set.of(21L));
        var expected = new ProjectBusinessOptionsQuery(TENANT_ID, Set.of(21L), "");
        when(projects.selectBusinessOptions(expected)).thenReturn(List.of());

        assertTrue(service.searchOptions(new ProjectBusinessContextApi.OptionsQuery(TENANT_ID, ACTOR_ID, null)).isEmpty());

        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verify(projects).selectBusinessOptions(expected);
        verifyNoMoreInteractions(scopes, projects);
        verifyNoInteractions(members);
    }

    @Test
    void optionsWithNoVisibleProjectsDoNotQueryTheOwnerMapper() {
        when(scopes.resolveAllCurrent(ALL_SCOPE)).thenReturn(Set.of());

        assertTrue(service.searchOptions(new ProjectBusinessContextApi.OptionsQuery(TENANT_ID, ACTOR_ID, "项目")).isEmpty());

        verify(scopes).resolveAllCurrent(ALL_SCOPE);
        verifyNoMoreInteractions(scopes);
        verifyNoInteractions(projects, members);
        verifyNoPerProjectAuthorization();
    }

    private void verifyNoPerProjectAuthorization() {
        verify(service, never()).inspect(any());
        verify(service, never()).serviceManagers(any());
        verify(service, never()).lockServiceManagers(any());
    }

    private static void assertForbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class, action).getCode());
    }

    private static ProjectMemberAssignmentDO member(Long projectId, String role, String assignmentType) {
        var row = new ProjectMemberAssignmentDO();
        row.setTenantId(TENANT_ID); row.setUserId(ACTOR_ID); row.setProjectId(projectId);
        row.setMemberRole(role); row.setAssignmentType(assignmentType); row.setStatus("ACTIVE");
        return row;
    }

    private static ProjectMasterDO project(Long id, String code, String name, String customerName) {
        var row = new ProjectMasterDO();
        row.setId(id); row.setProjectCode(code); row.setProjectName(name); row.setCustomerName(customerName);
        return row;
    }
}
