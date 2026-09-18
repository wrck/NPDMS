package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionCheckpoint;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionCheckpoint.Phase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ResultSubscriptionCheckpointTest {
    @Test void inventoryAndChangesKeepTheOriginalFormationBoundary() {
        var initial = ResultSubscriptionCheckpoint.initial(12);
        var inventory = initial.inventoryPage("owner-cursor-A").inventoryPage("owner-cursor-B");
        var first = inventory.inventoryComplete(29).changesPage(20);
        assertEquals(12, first.baselineSequence());
        assertEquals(29, first.throughSequence());
        assertEquals("owner-cursor-B", first.inventoryCursor());
        var live = first.changesPage(29);
        assertEquals(Phase.LIVE, live.phase());
        assertNull(live.throughSequence());
        var next = live.beginChanges(41).changesPage(41);
        assertEquals(12, next.baselineSequence());
        assertEquals(41, next.processedSequence());
        assertEquals(ResultSubscriptionCheckpoint.initial(12), initial);
    }

    @Test void emptyInventoryAndEmptyChangesAreNotErrorsOrInventedNewResults() {
        var live = ResultSubscriptionCheckpoint.initial(0).inventoryComplete(0).changesPage(0);
        assertEquals(Phase.LIVE, live.phase());
        assertEquals(live, live.beginChanges(0).changesPage(0));
        assertNull(live.inventoryCursor());
    }

    @Test void cursorIsOpaqueAndCannotBecomeTheChangeBoundary() {
        var checkpoint = ResultSubscriptionCheckpoint.initial(5).inventoryPage("9007199254740999");
        assertEquals(5, checkpoint.processedSequence());
        checkpoint = checkpoint.inventoryPage("cursor-0001");
        assertEquals("cursor-0001", checkpoint.inventoryCursor());
        assertEquals(5, checkpoint.baselineSequence());
    }

    @Test void catchingUpCannotRecaptureANewerUpperBound() {
        var checkpoint = ResultSubscriptionCheckpoint.initial(5).inventoryComplete(9);
        assertThrows(IllegalStateException.class, () -> checkpoint.beginChanges(10));
        assertThrows(IllegalStateException.class, () -> checkpoint.inventoryComplete(10));
        assertThrows(IllegalStateException.class, () -> checkpoint.inventoryPage("a"));
        assertEquals(9, checkpoint.throughSequence());
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 4})
    void capturesCannotMoveBehindTheFormationOrProcessedPosition(long upper) {
        assertThrows(IllegalArgumentException.class, () -> ResultSubscriptionCheckpoint.initial(5).inventoryComplete(upper));
        var live = ResultSubscriptionCheckpoint.initial(5).inventoryComplete(10).changesPage(10);
        assertThrows(IllegalArgumentException.class, () -> live.beginChanges(upper));
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 4, 5, 11, Long.MAX_VALUE})
    void cannotSkipTheFixedUpperBoundOrAcknowledgeANonAdvancingPage(long next) {
        var checkpoint = ResultSubscriptionCheckpoint.initial(5).inventoryComplete(10);
        assertThrows(IllegalArgumentException.class, () -> checkpoint.changesPage(next));
        assertEquals(5, checkpoint.processedSequence());
    }

    @Test void partialPageProgressIsImmutableAndReplayCanUseTheOldCheckpoint() {
        var checkpoint = ResultSubscriptionCheckpoint.initial(7).inventoryComplete(40);
        var next = checkpoint.changesPage(17);
        assertEquals(40, next.throughSequence());
        assertEquals(7, checkpoint.processedSequence());
        assertEquals(next, checkpoint.changesPage(17));
    }

    @Test void inventoryCannotBeAcknowledgedAsChanges() {
        var checkpoint = ResultSubscriptionCheckpoint.initial(5);
        assertThrows(IllegalStateException.class, () -> checkpoint.changesPage(6));
        assertThrows(IllegalStateException.class, () -> checkpoint.beginChanges(6));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\t", "same"})
    void unfinishedInventoryRequiresARealNextCursor(String next) {
        var checkpoint = ResultSubscriptionCheckpoint.initial(5).inventoryPage("same");
        assertThrows(IllegalArgumentException.class, () -> checkpoint.inventoryPage(next));
    }

    @Test void nullAndOversizedCursorsAreRejected() {
        var checkpoint = ResultSubscriptionCheckpoint.initial(0);
        assertThrows(IllegalArgumentException.class, () -> checkpoint.inventoryPage(null));
        assertThrows(IllegalArgumentException.class, () -> checkpoint.inventoryPage("a".repeat(129)));
    }

    @Test void rejectsCorruptPersistedCombinations() {
        assertThrows(NullPointerException.class, () -> new ResultSubscriptionCheckpoint(null,0,null,0,null));
        assertThrows(IllegalArgumentException.class, () -> ResultSubscriptionCheckpoint.initial(-1));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.INVENTORY,1,null,2,null));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.INVENTORY,1,null,1,2L));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.CHANGES,1,null,2,null));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.CHANGES,1,null,2,1L));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.LIVE,1,null,0,null));
        assertThrows(IllegalArgumentException.class, () -> new ResultSubscriptionCheckpoint(Phase.LIVE,1,null,2,3L));
    }

    @Test void largestSupportedSequenceNeverRequiresIncrementingPastLongRange() {
        var live = ResultSubscriptionCheckpoint.initial(Long.MAX_VALUE).inventoryComplete(Long.MAX_VALUE).changesPage(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, live.processedSequence());
        assertEquals(live, live.beginChanges(Long.MAX_VALUE).changesPage(Long.MAX_VALUE));
    }
}
