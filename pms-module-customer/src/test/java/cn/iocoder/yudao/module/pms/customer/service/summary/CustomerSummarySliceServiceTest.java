package cn.iocoder.yudao.module.pms.customer.service.summary;

import cn.iocoder.yudao.module.pms.asset.api.customer.AssetCustomerDeviceSummaryApi;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummaryQuery;
import cn.iocoder.yudao.module.pms.asset.api.customer.CustomerDeviceSummarySlice;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummaryQuery;
import cn.iocoder.yudao.module.pms.project.api.customer.CustomerProjectSummarySlice;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerSummaryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSummarySliceServiceTest {

    @Mock ProjectCustomerSummaryApi projectSummaryApi;
    @Mock AssetCustomerDeviceSummaryApi deviceSummaryApi;
    @InjectMocks CustomerProjectSummaryService projectService;
    @InjectMocks CustomerDeviceSummaryService deviceService;

    @Test
    void preservesProjectOwnerSliceMetadata() {
        LocalDateTime dataAsOf = LocalDateTime.now();
        CustomerProjectSummaryQuery query = new CustomerProjectSummaryQuery(1L, 100L, 1, 20);
        CustomerProjectSummarySlice ownerSlice = new CustomerProjectSummarySlice(
                "PROJ", true, dataAsOf, List.of(), 0L);
        when(projectSummaryApi.query(query)).thenReturn(ownerSlice);

        CustomerProjectSummarySlice result = projectService.query(query);

        assertTrue(result.available());
        assertEquals("PROJ", result.provider());
        assertEquals(dataAsOf, result.dataAsOf());
    }

    @Test
    void projectOwnerFailureReturnsUnavailableWithoutFabricatedTotal() {
        CustomerProjectSummaryQuery query = new CustomerProjectSummaryQuery(1L, 100L, 1, 20);
        when(projectSummaryApi.query(query)).thenThrow(new IllegalStateException("PROJ unavailable"));

        CustomerProjectSummarySlice result = projectService.query(query);

        assertFalse(result.available());
        assertEquals(0L, result.total());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void deviceOwnerNullReturnsUnavailable() {
        CustomerDeviceSummaryQuery query = new CustomerDeviceSummaryQuery(1L, 100L, 1, 20);
        when(deviceSummaryApi.query(query)).thenReturn(null);

        CustomerDeviceSummarySlice result = deviceService.query(query);

        assertFalse(result.available());
        assertEquals("AST", result.provider());
        assertTrue(result.items().isEmpty());
    }
}
