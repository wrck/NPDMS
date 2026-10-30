package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.*;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectAllScopeBatchTest {
    final ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    final ProjectMemberAssignmentMapper members=mock(ProjectMemberAssignmentMapper.class);
    final ProjectTreePathMapper paths=mock(ProjectTreePathMapper.class);
    final ProjectTreeVersionMapper versions=mock(ProjectTreeVersionMapper.class);
    final AuthorizationGrantApi grants=mock(AuthorizationGrantApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProjectOrganizationScopeService organizations=mock(ProjectOrganizationScopeService.class);
    final ProjectTreeScopeService service=new ProjectTreeScopeService(projects,members,paths,versions,grants,permissions,organizations);
    final Map<Long,ProjectMasterDO> projectRows=new HashMap<>();
    final Map<Long,ProjectTreeVersionDO> versionRows=new HashMap<>();
    final List<ProjectTreePathDO> pathRows=new ArrayList<>();
    final List<ProjectMemberAssignmentDO> memberRows=new ArrayList<>();
    final List<AuthorizationGrantDTO> grantRows=new ArrayList<>();
    final Set<Long> creators=new HashSet<>(Set.of(4L,13L));

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(0L);
        tree(1,4,true);tree(10,13,true);tree(20,21,false);
        var crossRoot=new ProjectTreePathDO();crossRoot.setRootProjectId(10L);crossRoot.setTreeVersion(7L);
        crossRoot.setAncestorProjectId(2L);crossRoot.setDescendantProjectId(13L);pathRows.add(crossRoot);
        var foreign=new ProjectMasterDO();foreign.setId(31L);foreign.setRootId(30L);foreign.setTenantId(2L);projectRows.put(31L,foreign);
        member(3,"PARTICIPANT");member(11,"PROJECT_MANAGER");member(21,"PROJECT_MANAGER");member(31,"PROJECT_MANAGER");
        grantRows.add(grant(2,"PROJECT_VIEW","CURRENT_PROJECT",false));
        grantRows.add(grant(11,"PROJECT_VIEW","PROJECT_AND_DESCENDANTS",false));
        grantRows.add(grant(2,"PROJECT_MANAGE","PROJECT_AND_DESCENDANTS",false));
        grantRows.add(grant(12,"PROJECT_MANAGE","CURRENT_PROJECT",false));
        grantRows.add(grant(1,"PROJECT_VIEW","PROJECT_AND_DESCENDANTS",true));
        when(projects.selectBatchIds(any())).thenAnswer(i -> ((Collection<Long>)i.getArgument(0)).stream().map(projectRows::get).filter(Objects::nonNull).filter(p->p.getTenantId()==0L).toList());
        when(projects.selectById(any())).thenAnswer(i->projectRows.get(i.getArgument(0)));
        when(projects.selectListCreatedBy(any())).thenAnswer(i->{
            CreatedProjectScopeQuery q=i.getArgument(0);
            return creators.stream().filter(id->q.candidateProjectIds()==null||q.candidateProjectIds().contains(id)).map(projectRows::get).toList();
        });
        when(members.selectAuthorizedByUser(any())).thenAnswer(i->List.copyOf(memberRows));
        when(versions.selectLatestActive(any())).thenAnswer(i->versionRows.get(i.getArgument(0)));
        when(versions.selectLatestActiveBatch(any())).thenAnswer(i->{
            ActiveProjectTreeVersionsQuery q=i.getArgument(0);
            return q.rootProjectIds().stream().map(versionRows::get).filter(Objects::nonNull).toList();
        });
        when(paths.selectByAncestor(any(),any(),any(),any())).thenAnswer(i->pathRows.stream().filter(p->p.getRootProjectId().equals(i.getArgument(0))&&p.getTreeVersion().equals(i.getArgument(1))&&p.getAncestorProjectId().equals(i.getArgument(2))).toList());
        when(paths.selectByAncestors(any(),any(),any())).thenAnswer(i->pathRows.stream().filter(p->p.getRootProjectId().equals(i.getArgument(0))&&p.getTreeVersion().equals(i.getArgument(1))&&((Collection<?>)i.getArgument(2)).contains(p.getAncestorProjectId())).toList());
        when(paths.selectScopeRootPaths(any())).thenAnswer(i->{
            ProjectScopeRootPathsQuery q=i.getArgument(0);
            return pathRows.stream().filter(p->p.getAncestorProjectId().equals(p.getRootProjectId())&&q.trees().stream().anyMatch(t->t.rootProjectId().equals(p.getRootProjectId())&&t.treeVersion().equals(p.getTreeVersion()))).toList();
        });
        when(paths.selectScopeDescendants(any())).thenAnswer(i->{
            ProjectScopeDescendantsQuery q=i.getArgument(0);
            return pathRows.stream().filter(p->q.subtrees().stream().anyMatch(t->t.rootProjectId().equals(p.getRootProjectId())&&t.treeVersion().equals(p.getTreeVersion())&&t.ancestorProjectIds().contains(p.getAncestorProjectId()))).toList();
        });
        when(grants.page(any())).thenAnswer(i->{
            AuthorizationGrantPageQuery q=i.getArgument(0);
            var rows=grantRows.stream().filter(g->g.actionCode().equals(q.actionCode())&&(g.effectiveTo()==null||g.effectiveTo().isAfter(q.effectiveAt()))).toList();
            return new AuthorizationGrantPageResult(rows,rows.size());
        });
        when(grants.listEffective(any())).thenAnswer(i->{
            AuthorizationGrantQuery q=i.getArgument(0);
            return grantRows.stream().filter(g->q.resourceIds().contains(g.resourceId())&&g.actionCode().equals(q.actionCode())&&(g.effectiveTo()==null||g.effectiveTo().isAfter(q.effectiveAt()))).toList();
        });
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}

    @Test void batchMatchesSingleTreeOracleForViewEditAndManageWithConstantReadCounts() {
        for(String action:List.of("PROJECT_VIEW","PROJECT_EDIT","PROJECT_MANAGE")) {
            var expected=new HashSet<Long>();
            for(long root:List.of(1L,10L))expected.addAll(service.resolve(new ProjectScopeQuery(0L,9L,root,action,7L)).fullProjectIds());
            clearInvocations(projects,members,versions,paths,grants);
            assertEquals(expected,service.resolveAllFullProjectIds(0L,9L,action));
            assertFalse(expected.contains(21L));assertFalse(expected.contains(31L));assertFalse(expected.contains(1L));assertFalse(expected.contains(10L));
            verify(members,times(2)).selectAuthorizedByUser(any());
            verify(versions,times(2)).selectLatestActiveBatch(any());
            verify(paths).selectScopeRootPaths(any());verify(paths).selectScopeDescendants(any());
            verify(versions,never()).selectLatestActive(any());verify(projects,never()).selectById(any());
            verify(paths,never()).selectByAncestor(any(),any(),any(),any());
            verify(paths,never()).selectByAncestors(any(),any(),any());
            verify(paths,never()).selectByDescendants(any(),any(),any());
            verify(grants,times(action.equals("PROJECT_VIEW")?2:1)).listEffective(any());
        }
    }

    @Test void revalidatesAllSelectedVersionsAndFailsClosedOnChangeOrMissingRoot() {
        var changed=new ProjectTreeVersionDO();changed.setRootProjectId(1L);changed.setTreeVersion(8L);
        doReturn(List.of(versionRows.get(1L),versionRows.get(10L)),List.of(changed,versionRows.get(10L))).when(versions).selectLatestActiveBatch(any());
        assertThrows(ServiceException.class,()->service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
        verify(paths,never()).selectScopeRootPaths(any());
        projectRows.remove(1L);
        assertThrows(ServiceException.class,()->service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
    }

    @Test void rechecksRoleAfterLoadingCurrentTrees() {
        when(permissions.hasAnyRoles(9L,"super_admin")).thenReturn(false,true);
        assertEquals(Set.of(1L,2L,3L,4L,10L,11L,12L,13L),service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
        verify(grants,never()).listEffective(any());
    }

    @Test void discoveryDoesNotFreezeRevokedMembershipOrCreatorRights() {
        grantRows.clear();creators.clear();
        when(members.selectAuthorizedByUser(any())).thenReturn(List.copyOf(memberRows),List.of());
        assertEquals(Set.of(),service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
        memberRows.clear();
        when(members.selectAuthorizedByUser(any())).thenReturn(List.of());
        when(projects.selectListCreatedBy(any())).thenReturn(List.of(projectRows.get(13L)),List.of());
        assertEquals(Set.of(),service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
    }

    @Test void organizationDiscoveryIsRevalidatedAndOverviewIsNotReturnedAsFullAccess() {
        memberRows.clear();grantRows.clear();creators.clear();
        when(organizations.projectIds(any())).thenReturn(Set.of(4L),Set.of());
        assertEquals(Set.of(),service.resolveAllFullProjectIds(0L,9L,"PROJECT_MANAGE"));
        doAnswer(invocation -> {
            ProjectOrganizationScopeService.Query q=invocation.getArgument(0);
            return q.summaryOnly() ? Set.of(4L) : Set.of();
        }).when(organizations).projectIds(any());
        assertEquals(Set.of(),service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
    }

    @Test void emptyAnchorsDoNotLoadTreesAndOwnerFailureIsNotHidden() {
        memberRows.clear();grantRows.clear();creators.clear();
        assertEquals(Set.of(),service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
        verifyNoInteractions(versions,paths);
        when(grants.page(any())).thenThrow(new IllegalStateException("owner"));
        assertThrows(IllegalStateException.class,()->service.resolveAllFullProjectIds(0L,9L,"PROJECT_VIEW"));
    }

    void tree(long root,long end,boolean active) {
        for(long id=root;id<=end;id++) {
            var p=new ProjectMasterDO();p.setId(id);p.setRootId(root);p.setTenantId(0L);projectRows.put(id,p);
            for(long ancestor=root;ancestor<=id;ancestor++) {
                var path=new ProjectTreePathDO();path.setRootProjectId(root);path.setTreeVersion(7L);path.setAncestorProjectId(ancestor);path.setDescendantProjectId(id);pathRows.add(path);
            }
        }
        if(active){var v=new ProjectTreeVersionDO();v.setRootProjectId(root);v.setTreeVersion(7L);versionRows.put(root,v);}
    }
    void member(long id,String role){var m=new ProjectMemberAssignmentDO();m.setProjectId(id);m.setMemberRole(role);memberRows.add(m);}
    AuthorizationGrantDTO grant(long id,String action,String scope,boolean expired) {
        return new AuthorizationGrantDTO(100L+id,0L,"USER",9L,"PROJ","PROJECT",id,action,scope,
                LocalDateTime.now().minusDays(2),expired?LocalDateTime.now().minusDays(1):null,"ACTIVE","PROJ",null,null,7L,null,null,null,null,0L);
    }
}
