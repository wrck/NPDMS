package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArrivalAcceptanceFactContractTest {

    @Test
    void normalizesQueryAndFactCollectionsIntoStableOrder() {
        ArrivalQuantityScopeFact later = quantity(20L, "P-2", "M-2", "2");
        ArrivalQuantityScopeFact earlier = quantity(10L, "P-1", "M-1", "1");
        ArrivalAcceptanceFactQuery query = new ArrivalAcceptanceFactQuery(
                1L, 100L, Set.of(30L, 10L), List.of(later, earlier));

        assertEquals(List.of(10L, 30L), query.deviceIds().stream().toList());
        assertEquals(List.of(earlier, later), query.quantityScopes());

        ArrivalScopeWatermark watermark = new ArrivalScopeWatermark(9L, Map.of(30L, 3L, 10L, 1L));
        ArrivalAcceptanceFact fact = new ArrivalAcceptanceFact(
                1L, 100L, List.of(9L, 3L), ArrivalAcceptanceFact.DECISION_NOT_ACCEPTED,
                4L, watermark, false,
                Set.of(30L), Set.of(), Set.of(10L),
                List.of(later), List.of(), List.of(earlier));

        assertEquals(List.of(3L, 9L), fact.sourceAcceptanceIds());
        assertEquals(List.of(10L, 30L), fact.scopeWatermark().deviceAssignmentVersions().keySet().stream().toList());
        assertEquals(List.of(30L), fact.acceptedDeviceIds().stream().toList());
    }

    @Test
    void rejectsEmptyScopeInvalidWatermarkAndOverlappingResults() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalAcceptanceFactQuery(1L, 100L, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalScopeWatermark(0L, Map.of()));

        ArrivalScopeWatermark watermark = new ArrivalScopeWatermark(1L, Map.of(10L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalAcceptanceFact(
                1L, 100L, List.of(1L), ArrivalAcceptanceFact.DECISION_ACCEPTED,
                1L, watermark, false,
                Set.of(10L), Set.of(10L), Set.of(),
                List.of(), List.of(), List.of()));
    }

    @Test
    void revalidationRequiresExpectedFactAndScopeVersions() {
        ArrivalQuantityScopeFact scope = quantity(10L, "P-1", "M-1", "1");
        ArrivalScopeWatermark watermark = new ArrivalScopeWatermark(1L, Map.of());

        assertThrows(IllegalArgumentException.class, () -> new ArrivalAcceptanceFactRevalidationQuery(
                1L, 100L, Set.of(), List.of(scope), null, watermark));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalAcceptanceFactRevalidationQuery(
                1L, 100L, Set.of(), List.of(scope), 1L, null));
    }

    @Test
    void publicFactDoesNotExposeOwnerOrFileContentFields() {
        Set<String> componentNames = Arrays.stream(ArrivalAcceptanceFact.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(componentNames.contains("ownerDo"));
        assertFalse(componentNames.contains("fileBody"));
        assertFalse(componentNames.contains("downloadUrl"));
        assertFalse(componentNames.contains("signerName"));
        assertFalse(componentNames.contains("signerPhone"));
    }

    private static ArrivalQuantityScopeFact quantity(Long orderLineId, String productCode,
                                                     String modelCode, String quantity) {
        return new ArrivalQuantityScopeFact(orderLineId, productCode, modelCode,
                new BigDecimal(quantity), "UNIT");
    }
}
