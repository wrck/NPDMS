package cn.iocoder.yudao.module.pms.asset.service.producttype;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetProductTypeSourceOrderTest {

    private final AssetProductTypeSourceOrder sourceOrder = new AssetProductTypeSourceOrder();
    private final LocalDateTime currentTime = LocalDateTime.of(2026, 8, 30, 10, 0);

    @Test
    void shouldRejectEarlierSourceUpdatedAtRegardlessOfSourceVersion() {
        assertEquals(AssetProductTypeSourceOrder.Decision.STALE_SOURCE,
                sourceOrder.decide(currentTime.minusSeconds(1), "999", "b".repeat(64), "TYPE-B",
                        currentTime, "1", "a".repeat(64), "TYPE-A"));
    }

    @Test
    void shouldTreatSameWatermarkAndSameFactsAsIdempotentReplay() {
        assertEquals(AssetProductTypeSourceOrder.Decision.IDEMPOTENT_REPLAY,
                sourceOrder.decide(currentTime, "v1", "a".repeat(64), "TYPE-A",
                        currentTime, "v1", "a".repeat(64), "TYPE-A"));
    }

    @Test
    void shouldTreatAnyDifferentFactAtSameWatermarkAsConflict() {
        assertEquals(AssetProductTypeSourceOrder.Decision.SOURCE_CONFLICT,
                sourceOrder.decide(currentTime, "v2", "a".repeat(64), "TYPE-A",
                        currentTime, "v1", "a".repeat(64), "TYPE-A"));
        assertEquals(AssetProductTypeSourceOrder.Decision.SOURCE_CONFLICT,
                sourceOrder.decide(currentTime, "v1", "b".repeat(64), "TYPE-A",
                        currentTime, "v1", "a".repeat(64), "TYPE-A"));
        assertEquals(AssetProductTypeSourceOrder.Decision.SOURCE_CONFLICT,
                sourceOrder.decide(currentTime, "v1", "a".repeat(64), "TYPE-B",
                        currentTime, "v1", "a".repeat(64), "TYPE-A"));
    }

    @Test
    void shouldAcceptLaterWatermarkEvenWhenSourceVersionLooksSmaller() {
        assertEquals(AssetProductTypeSourceOrder.Decision.NEWER,
                sourceOrder.decide(currentTime.plusSeconds(1), "1", "b".repeat(64), "TYPE-A",
                        currentTime, "999", "a".repeat(64), "TYPE-A"));
    }
}
