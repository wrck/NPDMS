package cn.iocoder.yudao.module.pms.project.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApiImpl;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.DeviceProjectOrganizationMapper;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectDeviceNameQueryTest {
    @Test void nameCandidatesStayInCurrentTenantAndBlankDoesNotListAll() {
        var mapper = mock(DeviceProjectOrganizationMapper.class);
        var api = new ProjectDeviceOrganizationApiImpl(mapper, mock(OrganizationScopeApi.class));
        TenantContextHolder.setTenantId(1L);
        try {
            assertTrue(api.findProjectIdsByName(new ProjectDeviceOrganizationApi.NameQuery(2L, "名称")).isEmpty());
            assertTrue(api.findProjectIdsByName(new ProjectDeviceOrganizationApi.NameQuery(1L, "  ")).isEmpty());
            verifyNoInteractions(mapper);
            when(mapper.selectIdsByName(any())).thenReturn(List.of(10L));
            assertEquals(Set.of(10L), api.findProjectIdsByName(new ProjectDeviceOrganizationApi.NameQuery(1L, " 名称 ")));
            verify(mapper).selectIdsByName(argThat(q -> q.tenantId().equals(1L) && q.keyword().equals("名称")));
        } finally { TenantContextHolder.clear(); }
    }
}
