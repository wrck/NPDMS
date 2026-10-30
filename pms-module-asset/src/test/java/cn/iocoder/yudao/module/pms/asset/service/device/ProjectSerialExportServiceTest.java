package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectSerialExportMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.ProjectSerialExportRow;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectSerialExportQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSerialExportServiceTest {
    final ProjectSerialExportMapper mapper=mock(ProjectSerialExportMapper.class);
    final ProjectScopeApi projects=mock(ProjectScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProjectSerialExportService service=new ProjectSerialExportService(mapper,projects,permissions);
    final ProjectSerialExportService.Request request=new ProjectSerialExportService.Request(10L," 001SN "," 交换机 "," 02010005 ");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user=new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
        when(permissions.hasAnyPermissions(7L,"pms:device:query")).thenReturn(true);
        when(permissions.hasAnyPermissions(7L,"pms:project-device:export")).thenReturn(true);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void queryPermissionDoesNotGrantExport() {
        when(permissions.hasAnyPermissions(7L,"pms:project-device:export")).thenReturn(false);
        assertThrows(ServiceException.class,()->service.read(request));
        verifyNoInteractions(mapper,projects);
    }
    @Test void placeholderAndOutsideProjectsCannotExport() {
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(20L),Set.of(10L)));
        assertThrows(ServiceException.class,()->service.read(request));
        verifyNoInteractions(mapper);
    }
    @Test void changedTenantCannotReuseLoginOrScope() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class,()->service.read(request));
        verifyNoInteractions(mapper,projects);
    }
    @Test void readsBeyondFirstBatchWithExactCursorAndFilters() {
        var first=LongStream.rangeClosed(2,501).mapToObj(id->{var r=new ProjectSerialExportRow();r.setDeviceId(503-id);r.setSn("001"+id);return r;}).toList();
        var last=new ProjectSerialExportRow(); last.setDeviceId(1L);last.setSn("001LAST");last.setLocationSnapshot("{\"fallbackLocation\":\"机房A\",\"secret\":\"not-exported\"}");
        when(mapper.selectBatch(any())).thenAnswer(call->{ProjectSerialExportQuery q=call.getArgument(0);
            assertEquals(1L,q.tenantId());assertEquals(10L,q.projectId());assertEquals("001SN",q.sn());assertEquals("交换机",q.name());assertEquals(" 02010005 ",q.productCode());
            if(q.beforeId()==null)return first;assertEquals(2L,q.beforeId());return List.of(last);});
        var result=service.read(request);
        assertEquals(501,result.size());assertEquals("001LAST",result.getLast().getSn());assertEquals("机房A",result.getLast().getLocation());
        verify(mapper,times(2)).selectBatch(any());
    }
    @Test void laterBatchFailureDoesNotReturnPartialSuccess() {
        var first=LongStream.rangeClosed(2,501).mapToObj(id->{var r=new ProjectSerialExportRow();r.setDeviceId(503-id);return r;}).toList();
        when(mapper.selectBatch(any())).thenReturn(first).thenThrow(new IllegalStateException("source unavailable"));
        assertThrows(IllegalStateException.class,()->service.read(request));
    }
}
