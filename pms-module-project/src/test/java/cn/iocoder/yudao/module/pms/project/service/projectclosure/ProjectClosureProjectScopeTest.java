package cn.iocoder.yudao.module.pms.project.service.projectclosure;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.ProjectClosureMapper;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectClosureProjectScopeTest {
    final ProjectClosureMapper mapper=mock(ProjectClosureMapper.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final ProjectClosureServiceImpl service=new ProjectClosureServiceImpl();
    final ProjectClosureDO row=new ProjectClosureDO();
    final ProjectScopeResult allowed=new ProjectScopeResult(7L,2L,Set.of(7L),Set.of());
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(10L).setTenantId(1L).setUserType(2),null));
        ReflectionTestUtils.setField(service,"projectClosureMapper",mapper);
        ReflectionTestUtils.setField(service,"projectScopeApi",scopes);
        row.setId(1L);row.setTenantId(1L);row.setProjectId(7L);row.setStatus(0);
        when(mapper.selectById(1L)).thenReturn(row);
        when(scopes.resolveCurrent(any())).thenReturn(allowed);
        when(scopes.lockAndRevalidate(any())).thenReturn(allowed);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void authorizedDetailCanBeRead() {assertSame(row,service.getProjectClosure(1L));}
    @Test void outOfScopeDetailIsDenied() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,2L,Set.of(),Set.of(7L)));
        assertThrows(ServiceException.class,()->service.getProjectClosure(1L));
    }
    @Test void wrongTenantIsNotReadable() {row.setTenantId(2L);assertThrows(ServiceException.class,()->service.getProjectClosure(1L));verifyNoInteractions(scopes);}
    @Test void emptyScopeDoesNotQueryUnfilteredPages() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertEquals(0L,service.getProjectClosurePage(new ProjectClosurePageReqVO()).getTotal());
        verify(mapper,never()).selectPage(any(cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosurePageQuery.class));
    }
    @Test void explicitUnauthorizedProjectReturnsEmpty() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(7L));
        var request=new ProjectClosurePageReqVO();request.setProjectId(8L);
        assertEquals(0L,service.getProjectClosurePage(request).getTotal());
        verify(mapper,never()).selectPage(any(cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosurePageQuery.class));
    }
    @Test void viewPermissionDoesNotBecomeManagePermission() {
        when(scopes.resolveCurrent(any())).thenAnswer(call->ProjectScopeApi.ACTION_VIEW.equals(((ProjectCurrentScopeQuery)call.getArgument(0)).actionCode())?allowed:new ProjectScopeResult(7L,2L,Set.of(),Set.of()));
        assertSame(row,service.getProjectClosure(1L));
        assertThrows(ServiceException.class,()->service.startApprove(1L));
        assertThrows(ServiceException.class,()->service.passProjectClosure(1L));
        assertThrows(ServiceException.class,()->service.rejectProjectClosure(1L));
        assertThrows(ServiceException.class,()->service.archiveProjectClosure(1L));

        verify(mapper,never()).updateById(any(ProjectClosureDO.class));
    }
    @Test void revokedScopeAtLockDoesNotWrite() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,3L,Set.of(),Set.of()));
        assertThrows(ServiceException.class,()->service.startApprove(1L));
        verify(mapper,never()).updateById(any(ProjectClosureDO.class));
    }

    @Test void reparentingCannotUseAnAuthorizedTargetToBypassTheOriginalOwner() {
        var request = new ProjectClosureSaveReqVO(); request.setId(1L); request.setProjectId(8L);
        assertThrows(ServiceException.class, () -> service.updateProjectClosure(request));
        verify(mapper,never()).updateById(any(ProjectClosureDO.class));
    }
    @Test void authorizedPageCarriesOnlyResolvedProjectsToTheMapper() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(7L));
        when(mapper.selectPage(any(cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosurePageQuery.class))).thenReturn(cn.iocoder.yudao.framework.common.pojo.PageResult.empty());
        var request=new ProjectClosurePageReqVO();request.setProjectId(7L);request.setPageNo(2);
        service.getProjectClosurePage(request);
        var query=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosurePageQuery.class);
        verify(mapper).selectPage(query.capture());
        assertEquals(Set.of(7L),query.getValue().getVisibleProjectIds());
        assertEquals(2,query.getValue().getPageNo());
    }
    @Test void authorizedApproverCanStartApprovalThroughTheExistingStateMachine() {
        row.setStatus(1);service.startApprove(1L);
        var update=org.mockito.ArgumentCaptor.forClass(ProjectClosureDO.class);
        verify(mapper).updateById(update.capture());assertEquals(2,update.getValue().getStatus());
        verify(scopes).lockAndRevalidate(any());
    }
}
