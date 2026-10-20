package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberPageQuery;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberSelectionApiImplTest {
    final ProjectMemberAssignmentMapper members=mock(ProjectMemberAssignmentMapper.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final ProjectMemberSelectionApiImpl api=new ProjectMemberSelectionApiImpl(members,scopes,users);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L); when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(7L),Set.of()));}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void currentPageWithoutRoleKeepsAllCurrentMembers(){
        var member=new ProjectMemberAssignmentDO(); member.setId(8L);member.setVersion(3);member.setMemberName("成员");member.setMemberRole("END_USER");member.setExternalMobile("13800000000");
        when(members.selectMemberPage(any())).thenReturn(new PageResult<>(List.of(member),1L));
        var result=api.page(query(null));
        assertEquals(8L,result.members().getFirst().assignmentId());
        var query=ArgumentCaptor.forClass(ProjectMemberPageQuery.class);verify(members).selectMemberPage(query.capture());
        assertEquals("CURRENT",query.getValue().getState());assertNull(query.getValue().getRole());assertEquals(7L,query.getValue().getProjectId());
        verifyNoInteractions(users);
    }
    @Test void emptyOrForeignScopeDoesNotReadMembers(){
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(),Set.of()));
        assertThrows(SecurityException.class,()->api.page(query(null)));
        assertThrows(SecurityException.class,()->api.page(ProjectMemberSelectionApi.Query.builder().tenantId(2L).actorId(2L).projectId(7L).pageNo(1).pageSize(100).build()));
        verifyNoInteractions(members,users);
    }
    @Test void revalidationRejectsMissingAndStaleMembersAndRevokedScope(){
        var query=revalidation(null);
        assertThrows(IllegalArgumentException.class,()->api.lockAndInspect(query));
        var member=new ProjectMemberAssignmentDO();member.setVersion(4);when(members.selectMemberSelectionForUpdate(any())).thenReturn(member);
        assertThrows(IllegalArgumentException.class,()->api.lockAndInspect(query));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,2L,Set.of(),Set.of()));
        assertThrows(SecurityException.class,()->api.lockAndInspect(query));
    }
    @Test void rejectsNonEndUserRecordsEvenWhenAssignmentVersionMatches(){
        var member=new ProjectMemberAssignmentDO();member.setId(8L);member.setVersion(3);member.setMemberName("工程师");member.setMemberRole("PROJECT_MANAGER");
        when(members.selectMemberSelectionForUpdate(any())).thenReturn(member);
        var query=revalidation("END_USER");
        assertThrows(IllegalArgumentException.class,()->api.lockAndInspect(query));
        assertEquals(8L,api.lockAndInspect(revalidation(null)).assignmentId());
        member.setMemberRole("END_USER");member.setId(8L);member.setExternalMobile("13800000000");
        assertEquals(8L,api.lockAndInspect(query).assignmentId());
        verifyNoInteractions(users);
    }
    @Test void roleFilterUsesExistingCurrentPageQueryAndRejectsUnknownRoles(){
        when(members.selectMemberPage(any())).thenReturn(new PageResult<>(List.of(),0L));
        api.page(query("END_USER"));
        var selected=ArgumentCaptor.forClass(ProjectMemberPageQuery.class);verify(members).selectMemberPage(selected.capture());
        assertEquals("END_USER",selected.getValue().getRole());
        assertEquals("CURRENT",selected.getValue().getState());
        assertNotNull(selected.getValue().getEffectiveAt());
        assertThrows(IllegalArgumentException.class,()->api.page(query("UNKNOWN")));
    }
    private ProjectMemberSelectionApi.Query query(String role){
        return ProjectMemberSelectionApi.Query.builder().tenantId(1L).actorId(2L).projectId(7L).pageNo(1).pageSize(100).role(role).build();
    }
    private ProjectMemberSelectionApi.Revalidation revalidation(String role){
        return ProjectMemberSelectionApi.Revalidation.builder().tenantId(1L).actorId(2L).projectId(7L).assignmentId(8L).expectedVersion(3L).role(role).build();
    }
}
