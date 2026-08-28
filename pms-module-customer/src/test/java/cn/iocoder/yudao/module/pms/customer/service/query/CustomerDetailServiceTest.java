package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummaryQuery;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummarySlice;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerDetailRespVO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerFieldHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.service.history.CustomerHistoryService;
import cn.iocoder.yudao.module.pms.customer.service.location.CustomerLocationReferenceService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService;
import cn.iocoder.yudao.module.pms.customer.service.summary.CustomerDeviceSummaryService;
import cn.iocoder.yudao.module.pms.customer.service.summary.CustomerProjectSummaryService;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummaryQuery;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummarySlice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDetailServiceTest {

    @Mock CustomerResponseService responseService;
    @Mock CustomerLocationReferenceService locationService;
    @Mock CustomerProjectSummaryService projectSummaryService;
    @Mock CustomerDeviceSummaryService deviceSummaryService;
    @Mock CustomerHistoryService historyService;
    @InjectMocks CustomerDetailService service;

    @Test
    void combinesCustomerLocationsOwnerSummariesAndHistory() {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(100L);
        customer.setTenantId(1L);
        var base = new cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO();
        base.setId(100L);
        var location = new CustomerLocationReferenceDO();
        location.setLocationType("ADDRESS");
        var history = new CustomerFieldHistoryDO();
        history.setFieldName("name");
        var projects = new CustomerProjectSummarySlice("PROJ", true, LocalDateTime.now(), List.of(), 0L);
        var devices = new CustomerDeviceSummarySlice("AST", true, LocalDateTime.now(), List.of(), 0L);
        when(responseService.detail(customer, MASKED)).thenReturn(base);
        when(locationService.listCurrent(1L, 100L)).thenReturn(List.of(location));
        when(projectSummaryService.query(new CustomerProjectSummaryQuery(1L, 100L, 1, 20)))
                .thenReturn(projects);
        when(deviceSummaryService.query(new CustomerDeviceSummaryQuery(1L, 100L, 1, 20)))
                .thenReturn(devices);
        when(historyService.list(1L, 100L)).thenReturn(List.of(history));

        CustomerDetailRespVO result = service.get(customer, MASKED);

        assertEquals(100L, result.getId());
        assertEquals("ADDRESS", result.getLocations().get(0).getLocationType());
        assertEquals(projects, result.getProjects());
        assertEquals(devices, result.getDevices());
        assertEquals("name", result.getHistory().get(0).getFieldName());
    }

    @Test
    void keepsOtherCustomerDetailSlicesAvailableWhenDeviceOwnerIsUnavailable() {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(100L);
        customer.setTenantId(1L);
        var base = new cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO();
        base.setId(100L);
        var location = new CustomerLocationReferenceDO();
        location.setLocationType("ADDRESS");
        var history = new CustomerFieldHistoryDO();
        history.setFieldName("name");
        var projects = new CustomerProjectSummarySlice("PROJ", true, LocalDateTime.now(), List.of(), 0L);
        var unavailableDevices = new CustomerDeviceSummarySlice("AST", false, LocalDateTime.now(), List.of(), 0L);
        when(responseService.detail(customer, MASKED)).thenReturn(base);
        when(locationService.listCurrent(1L, 100L)).thenReturn(List.of(location));
        when(projectSummaryService.query(new CustomerProjectSummaryQuery(1L, 100L, 1, 20)))
                .thenReturn(projects);
        when(deviceSummaryService.query(new CustomerDeviceSummaryQuery(1L, 100L, 1, 20)))
                .thenReturn(unavailableDevices);
        when(historyService.list(1L, 100L)).thenReturn(List.of(history));

        CustomerDetailRespVO result = service.get(customer, MASKED);

        assertEquals(100L, result.getId());
        assertTrue(result.getProjects().available());
        assertFalse(result.getDevices().available());
        assertEquals("ADDRESS", result.getLocations().getFirst().getLocationType());
        assertEquals("name", result.getHistory().getFirst().getFieldName());
    }
}
