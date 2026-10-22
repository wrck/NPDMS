package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceListNameServiceTest {
    final ProjectDeviceOrganizationApi projects = mock(ProjectDeviceOrganizationApi.class);
    final CustomerQueryApi customers = mock(CustomerQueryApi.class);
    final DeviceListNameService service = new DeviceListNameService(projects, customers);

    @Test void emptyPageDoesNotQueryOtherModules() {
        assertTrue(service.resolve(new DeviceListNameService.Query(1L, List.of())).isEmpty());
        verifyNoInteractions(projects, customers);
    }

    @Test void batchesOnlyCurrentPageAndDoesNotExposeAnotherTenantCustomer() {
        var first = row(1L, 10L, 20L); var second = row(2L, 10L, 20L);
        when(projects.getOrganizations(1L, Set.of(10L))).thenReturn(List.of(
                new ProjectDeviceOrganizationApi.Organization(10L, 1L, "公司", 3L, "OFFICE", "杭州", "项目名称", "PJT001")));
        when(customers.getCustomers(Set.of(20L))).thenReturn(List.of(
                new CustomerSummaryDTO(20L, 2L, "CUS", "其他租户客户", null, "ACTIVE", null, null, null)));
        var names = service.resolve(new DeviceListNameService.Query(1L, List.of(first, second)));
        assertEquals("项目名称", names.get(1L).projectName());
        assertEquals("PJT001", names.get(1L).projectCode());
        assertNull(names.get(1L).customerName());
        assertEquals(names.get(1L), names.get(2L));
        verify(projects).getOrganizations(1L, Set.of(10L)); verify(customers).getCustomers(Set.of(20L));
        verifyNoMoreInteractions(projects, customers);
    }

    private DeviceListProjection row(Long id, Long project, Long customer) {
        var row = mock(DeviceListProjection.class);
        when(row.deviceId()).thenReturn(id); when(row.projectId()).thenReturn(project); when(row.customerId()).thenReturn(customer);
        return row;
    }
}
