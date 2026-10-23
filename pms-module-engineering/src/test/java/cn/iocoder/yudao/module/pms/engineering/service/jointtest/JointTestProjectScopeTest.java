package cn.iocoder.yudao.module.pms.engineering.service.jointtest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JointTestProjectScopeTest {
    final JointTestMapper mapper=mock(JointTestMapper.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final JointTestServiceImpl service=new JointTestServiceImpl();
    final JointTestDO row=new JointTestDO();
    final ProjectScopeResult allowed=new ProjectScopeResult(7L,2L,Set.of(7L),Set.of());
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(10L).setTenantId(1L).setUserType(2),null));
        ReflectionTestUtils.setField(service,"jointTestMapper",mapper);
        ReflectionTestUtils.setField(service,"projectScopeApi",scopes);
        row.setId(1L);row.setTenantId(1L);row.setProjectId(7L);row.setStatus(0);
        when(mapper.selectById(1L)).thenReturn(row);
        when(scopes.resolveCurrent(any())).thenReturn(allowed);
        when(scopes.lockAndRevalidate(any())).thenReturn(allowed);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void authorizedDetailCanBeRead() {assertSame(row,service.getJointTest(1L));}
    @Test void outOfScopeDetailIsDenied() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,2L,Set.of(),Set.of(7L)));
        assertThrows(ServiceException.class,()->service.getJointTest(1L));
    }
    @Test void wrongTenantIsNotReadable() {row.setTenantId(2L);assertThrows(ServiceException.class,()->service.getJointTest(1L));verifyNoInteractions(scopes);}
    @Test void emptyScopeDoesNotQueryUnfilteredPages() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertEquals(0L,service.getJointTestPage(new JointTestPageReqVO()).getTotal());
        verify(mapper,never()).selectPage(any(cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query.JointTestPageQuery.class));
    }
    @Test void explicitUnauthorizedProjectReturnsEmpty() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(7L));
        var request=new JointTestPageReqVO();request.setProjectId(8L);
        assertEquals(0L,service.getJointTestPage(request).getTotal());
        verify(mapper,never()).selectPage(any(cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query.JointTestPageQuery.class));
    }
    @Test void viewPermissionDoesNotBecomeManagePermission() {
        when(scopes.resolveCurrent(any())).thenAnswer(call->ProjectScopeApi.ACTION_VIEW.equals(((ProjectCurrentScopeQuery)call.getArgument(0)).actionCode())?allowed:new ProjectScopeResult(7L,2L,Set.of(),Set.of()));
        assertSame(row,service.getJointTest(1L));
        assertThrows(ServiceException.class,()->service.start(1L));
        assertThrows(ServiceException.class,()->service.pass(1L));

        verify(mapper,never()).updateById(any(JointTestDO.class));
    }
    @Test void revokedScopeAtLockDoesNotWrite() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(7L,3L,Set.of(),Set.of()));
        assertThrows(ServiceException.class,()->service.start(1L));
        verify(mapper,never()).updateById(any(JointTestDO.class));
    }

    @Test void reparentingCannotUseAnAuthorizedTargetToBypassTheOriginalOwner() {
        var request = new JointTestSaveReqVO(); request.setId(1L); request.setProjectId(8L);
        assertThrows(ServiceException.class, () -> service.updateJointTest(request));
        verify(mapper,never()).updateById(any(JointTestDO.class));
    }
    @Test void authorizedPageCarriesOnlyResolvedProjectsToTheMapper() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(7L));
        when(mapper.selectPage(any(cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query.JointTestPageQuery.class))).thenReturn(cn.iocoder.yudao.framework.common.pojo.PageResult.empty());
        var request=new JointTestPageReqVO();request.setProjectId(7L);request.setPageNo(2);
        service.getJointTestPage(request);
        var query=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query.JointTestPageQuery.class);
        verify(mapper).selectPage(query.capture());
        assertEquals(Set.of(7L),query.getValue().getVisibleProjectIds());
        assertEquals(2,query.getValue().getPageNo());
    }
}
