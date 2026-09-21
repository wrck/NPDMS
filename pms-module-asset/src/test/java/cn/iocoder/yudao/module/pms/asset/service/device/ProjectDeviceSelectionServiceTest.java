package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceArchivePageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceSelectionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectDeviceSelectionQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceSelectionContextApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeviceSelectionServiceTest {
    private final ProjectDeviceSelectionMapper mapper = mock(ProjectDeviceSelectionMapper.class);
    private final ProjectDeviceSelectionContextApi context = mock(ProjectDeviceSelectionContextApi.class);
    private final ProjectDeviceSelectionService service = new ProjectDeviceSelectionService(mapper, context);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(context.getContractNumbers(10L)).thenReturn(Set.of("CONTRACT-10"));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void pageUsesAuthoritativeContractsAndKeepsFiltersAndPagination() {
        var request = new DeviceArchivePageReqVO();
        request.setSelectionProjectId(10L); request.setSn("SN"); request.setName("交换机");
        request.setProductModel("X"); request.setContractNo("CON"); request.setPageNo(3); request.setPageSize(20);
        when(mapper.selectSelectionPage(any())).thenReturn(PageResult.empty());
        service.getPage(request);
        var capture = ArgumentCaptor.forClass(ProjectDeviceSelectionQuery.class);
        verify(mapper).selectSelectionPage(capture.capture());
        var query = capture.getValue();
        assertEquals(1L, query.getTenantId()); assertEquals(10L, query.getProjectId());
        assertEquals(Set.of("CONTRACT-10"), query.getContractNumbers());
        assertEquals("SN", query.getSn()); assertEquals("交换机", query.getName());
        assertEquals("X", query.getProductModel()); assertEquals("CON", query.getContractNo());
        assertEquals(3, query.getPageNo()); assertEquals(20, query.getPageSize());
    }

    @Test void unavailableProjectNeverFallsBackToGlobalQuery() {
        when(context.getContractNumbers(99L)).thenThrow(new IllegalArgumentException("forbidden"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSelection(99L, List.of(1L)));
        verifyNoInteractions(mapper);
    }

    @Test void duplicateDeviceCannotBeSubmittedTwice() {
        assertThrows(ServiceException.class, () -> service.validateSelection(10L, List.of(1L, 1L)));
        verifyNoInteractions(mapper);
    }

    @Test void missingOrOutOfScopeDeviceRejectsWholeSelection() {
        when(mapper.selectSelectionForUpdate(any())).thenReturn(List.of(device(1L)));
        assertThrows(ServiceException.class, () -> service.validateSelection(10L, List.of(1L, 2L)));
    }

    @Test void snapshotsComeFromLockedServerRecords() {
        when(mapper.selectSelectionForUpdate(any())).thenReturn(List.of(device(1L), device(2L)));
        var result = service.validateSelection(10L, List.of(1L, 2L));
        assertEquals(List.of("SN-1", "SN-2"), result.stream().map(row -> row.sn()).toList());
        assertEquals("CONTRACT-10", result.getFirst().contractNo());
    }

    @Test void emptySelectionStillChecksProjectAccessWithoutQueryingAllDevices() {
        assertTrue(service.validateSelection(10L, List.of()).isEmpty());
        verify(context).getContractNumbers(10L); verifyNoInteractions(mapper);
    }

    private DeviceDO device(long id) {
        var row = new DeviceDO(); row.setId(id); row.setSn("SN-" + id); row.setContractNo("CONTRACT-10");
        return row;
    }
}
