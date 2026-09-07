package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessRevalidationQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.INVALID_REQUEST;
import static cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException.Code.OWNER_DATA_CORRUPTED;
import static cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import static org.junit.jupiter.api.Assertions.*;

/** EXE-06 / F-IMP-001: malformed Owner identities must never produce READY. */
class ImplementationReadinessValidationTest {

    @Test
    void sourceIdsMustBePositiveAndNonNull() {
        for (Long invalid : Arrays.asList(null, 0L, -1L)) {
            corrupted(() -> source(Arrays.asList(invalid), watermark()));
            corrupted(() -> source(Arrays.asList(1L, invalid), watermark()));
        }
    }

    @Test
    void sourceIdsRemainUniqueAndImmutable() {
        corrupted(() -> source(List.of(1L, 1L), watermark()));
        ArrayList<Long> ids = new ArrayList<>(List.of(2L, 1L));
        SourceFact fact = source(ids, watermark());
        ids.clear();
        assertEquals(List.of(1L, 2L), fact.sourceObjectIds());
        assertThrows(UnsupportedOperationException.class, () -> fact.sourceObjectIds().add(3L));
    }

    @Test
    void nullWatermarkMembersHaveStableOwnerFailure() {
        corrupted(() -> source(List.of(1L), Arrays.asList((WatermarkEntry) null)));
        corrupted(() -> source(List.of(1L), Arrays.asList(watermark().get(0), null)));
    }

    @Test
    void nullContextMembersHaveStableOwnerFailure() {
        ImplementationReadinessContextFact valid = context(false);
        corrupted(() -> new ImplementationReadinessContextFact(1L,
                Arrays.asList((DeviceFact) null), valid.approvedPlan(), valid.sourceFacts()));
        List<SourceFact> sources = new ArrayList<>(valid.sourceFacts());
        sources.set(1, null);
        corrupted(() -> new ImplementationReadinessContextFact(1L,
                valid.devices(), valid.approvedPlan(), sources));
    }

    @Test
    void nullCallerDevicesHaveStableInputFailureInBothQueries() {
        List<ImplementationReadinessQuery.ExpectedDevice> devices = Arrays.asList(
                new ImplementationReadinessQuery.ExpectedDevice(1L, "SN-1", 1L), null);
        assertEquals(INVALID_REQUEST, assertThrows(ImplementationReadinessException.class,
                () -> new ImplementationReadinessQuery(1L, 1L, devices)).getCode());
        assertEquals(INVALID_REQUEST, assertThrows(ImplementationReadinessException.class,
                () -> new ImplementationReadinessRevalidationQuery(1L, 1L, 1L, 0L, devices)).getCode());
    }

    @Test
    void validAndReopenedContextsKeepDifferentDecisions() {
        assertTrue(context(false).isReady());
        assertFalse(context(true).isReady());
    }

    @Test
    void missingSourceMayStayEmptyButMayNotClaimCompletion() {
        SourceFact missing = new SourceFact(SourceCode.EXE_02, CompletionStatus.NOT_COMPLETED,
                0L, List.of(), List.of(), false);
        assertTrue(missing.sourceObjectIds().isEmpty());
        corrupted(() -> source(List.of(), List.of()));
    }

    private static void corrupted(Runnable operation) {
        assertEquals(OWNER_DATA_CORRUPTED,
                assertThrows(ImplementationReadinessException.class, operation::run).getCode());
    }

    private static SourceFact source(List<Long> ids, List<WatermarkEntry> marks) {
        return new SourceFact(SourceCode.EXE_01, CompletionStatus.ACCEPTED, 1L, ids, marks, false);
    }

    private static List<WatermarkEntry> watermark() {
        return List.of(new WatermarkEntry("FACT_VERSION", 1L, 1L));
    }

    private static ImplementationReadinessContextFact context(boolean reopened) {
        List<SourceFact> sources = Arrays.stream(SourceCode.values()).map(code -> new SourceFact(
                code, code == SourceCode.EXE_01 ? CompletionStatus.ACCEPTED : CompletionStatus.COMPLETED,
                1L, List.of(1L), watermark(), reopened)).toList();
        return new ImplementationReadinessContextFact(1L, List.of(new DeviceFact(1L, "SN-1", 1L)),
                new ApprovedPlanFact(1L, 1L), sources);
    }
}
