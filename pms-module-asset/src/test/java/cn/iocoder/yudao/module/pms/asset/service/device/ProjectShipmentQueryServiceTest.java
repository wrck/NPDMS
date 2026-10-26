package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShipmentPageContextQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectShipmentQueryServiceTest {
    final DeviceMapper mapper=mock(DeviceMapper.class);
    final ProjectScopeApi access=mock(ProjectScopeApi.class);
    final ProjectShipmentQueryService service=new ProjectShipmentQueryService(mapper,access);
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void noProjectEmptyPageForeignTenantAndDeniedProjectCannotReadSourceContext() {
        TenantContextHolder.setTenantId(1L);
        for(var query:List.of(new ProjectShipmentPageContextQuery(1L,null,List.of(1L)),
                new ProjectShipmentPageContextQuery(1L,10L,List.of()),
                new ProjectShipmentPageContextQuery(2L,10L,List.of(1L)),
                new ProjectShipmentPageContextQuery(1L,10L,List.of(1L)))) {
            assertTrue(service.forPage(query).isEmpty());
        }
        verifyNoInteractions(mapper);
    }
    @Test void authorizedContextIsLimitedToRequestedProjectAndPageDevices() {
        TenantContextHolder.setTenantId(1L);
        when(access.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        var query=new ProjectShipmentPageContextQuery(1L,10L,List.of(1L,2L));
        when(mapper.selectProjectShipmentContext(query)).thenReturn(List.of());
        assertTrue(service.forPage(query).isEmpty());
        verify(mapper).selectProjectShipmentContext(query);
    }

    @Test void ownerFailureCannotExposeContext() {
        TenantContextHolder.setTenantId(1L);
        when(access.resolveCurrent(any())).thenThrow(new IllegalStateException("owner unavailable"));
        assertTrue(service.forPage(new ProjectShipmentPageContextQuery(1L,10L,List.of(1L))).isEmpty());
        verifyNoInteractions(mapper);
    }
}
