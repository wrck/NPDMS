package cn.iocoder.yudao.module.pms.engineering.config;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.OwnerFactVersionMismatchException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImplementationReadinessConfigurationTest {
    @Test void quantityProjectionSumsOnlyMatchingOwnerDimensionsAndRetainsSerialDetails() {
        var api = mock(DeliveryScopeApi.class);
        when(api.getAssignedScope(1L, null)).thenReturn(new AssignedDeliveryScopeResult(1L, 2L, List.of(
                line(1L, "MODEL-A", "SN-1"), line(2L, "MODEL-A", "SN-2"),
                line(3L, "MODEL-A"), line(4L, "MODEL-A"), line(5L, "MODEL-B"))));
        var fact = new ImplementationReadinessConfiguration().implementationDeliveryScope(api).inspectAssignedScope(1L);
        assertEquals(4, fact.lines().size());
        assertEquals(2, fact.lines().stream().filter(row -> !row.serialNumbers().isEmpty()).count());
        var quantity = fact.lines().stream().filter(row -> row.serialNumbers().isEmpty()).toList();
        assertEquals(new BigDecimal("2"), quantity.stream().filter(row -> "MODEL-A".equals(row.productModel())).findFirst().orElseThrow().assignedQuantity());
        assertEquals(BigDecimal.ONE, quantity.stream().filter(row -> "MODEL-B".equals(row.productModel())).findFirst().orElseThrow().assignedQuantity());
    }

    @Test void ownerStalenessAndUnavailableRemainDifferentFailures() {
        var api = mock(DeliveryScopeApi.class);
        var port = new ImplementationReadinessConfiguration().implementationDeliveryScope(api);
        when(api.getAssignedScope(1L, 2L)).thenThrow(new DeliveryScopeFactException(DeliveryScopeFactException.Code.SCOPE_STALE, "stale"));
        assertThrows(OwnerFactVersionMismatchException.class, () -> port.lockAndRevalidate(1L, 2L));
        when(api.getAssignedScope(1L, null)).thenThrow(new DeliveryScopeFactException(DeliveryScopeFactException.Code.PROVIDER_UNAVAILABLE, "offline"));
        assertThrows(DeliveryScopeFactException.class, () -> port.inspectAssignedScope(1L));
    }

    private AssignedDeliveryScopeLine line(long detailId, String model, String... serials) {
        return new AssignedDeliveryScopeLine(20L, detailId, 30L, BigDecimal.ONE, "台", "PRODUCT", model, List.of(serials));
    }
}
