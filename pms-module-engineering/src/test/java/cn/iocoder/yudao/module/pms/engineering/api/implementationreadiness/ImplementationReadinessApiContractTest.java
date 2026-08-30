package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessResult;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessSnapshotFact;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImplementationReadinessApiContractTest {

    @Test
    void apiExposesOnlyInspectAndLockRevalidation() {
        List<Method> methods = Arrays.stream(ImplementationReadinessApi.class.getDeclaredMethods())
                .sorted(java.util.Comparator.comparing(Method::getName)).toList();
        assertEquals(List.of("inspect", "lockAndRevalidate"), methods.stream().map(Method::getName).toList());
        assertEquals(ImplementationReadinessQuery.class, methods.get(0).getParameterTypes()[0]);
        assertEquals(ImplementationReadinessResult.class, methods.get(0).getReturnType());
        assertEquals(ImplementationReadinessRevalidationQuery.class, methods.get(1).getParameterTypes()[0]);
        assertEquals(ImplementationReadinessResult.class, methods.get(1).getReturnType());
    }

    @Test
    void inputSortsCompleteDevicesAndRejectsNormalizedDuplicates() {
        ImplementationReadinessQuery query = new ImplementationReadinessQuery(1L, 10L, List.of(
                new ImplementationReadinessQuery.ExpectedDevice(22L, "SN-22", 8L),
                new ImplementationReadinessQuery.ExpectedDevice(11L, "sn-11", 7L)));
        assertEquals(List.of(11L, 22L), query.expectedDevices().stream()
                .map(ImplementationReadinessQuery.ExpectedDevice::deviceId).toList());

        ImplementationReadinessException duplicate = assertThrows(ImplementationReadinessException.class,
                () -> new ImplementationReadinessQuery(1L, 10L, List.of(
                        new ImplementationReadinessQuery.ExpectedDevice(11L, "sn-1", 7L),
                        new ImplementationReadinessQuery.ExpectedDevice(12L, "SN-1", 8L))));
        assertEquals(ImplementationReadinessException.Code.DUPLICATE_DEVICE, duplicate.getCode());
    }

    @Test
    void revalidationCarriesOnlySnapshotAndCompleteDeviceExpectations() {
        ImplementationReadinessRevalidationQuery query = new ImplementationReadinessRevalidationQuery(
                1L, 10L, 91L, 3L, List.of(
                new ImplementationReadinessQuery.ExpectedDevice(22L, "SN-22", 8L),
                new ImplementationReadinessQuery.ExpectedDevice(11L, "SN-11", 7L)));
        assertEquals(List.of(11L, 22L), query.expectedDevices().stream()
                .map(ImplementationReadinessQuery.ExpectedDevice::deviceId).toList());
        assertEquals(91L, query.expectedSnapshotId());
        assertEquals(3L, query.expectedSnapshotVersion());
    }

    @Test
    void contextRequiresExactlyFourOrderedSourceFactsAndStructuredWatermarks() {
        ImplementationReadinessContextFact context = context();
        assertEquals(List.of("EXE_01", "EXE_02", "EXE_03", "EXE_04"), context.sourceFacts().stream()
                .map(fact -> fact.sourceCode().name()).toList());

        ImplementationReadinessException incomplete = assertThrows(ImplementationReadinessException.class,
                () -> new ImplementationReadinessContextFact(5L, context.devices(), context.approvedPlan(),
                        context.sourceFacts().subList(0, 3)));
        assertEquals(ImplementationReadinessException.Code.OWNER_DATA_CORRUPTED, incomplete.getCode());

        ImplementationReadinessException wrongStatus = assertThrows(ImplementationReadinessException.class,
                () -> source(ImplementationReadinessContextFact.SourceCode.EXE_01,
                        ImplementationReadinessContextFact.CompletionStatus.COMPLETED, 11L));
        assertEquals(ImplementationReadinessException.Code.OWNER_DATA_CORRUPTED, wrongStatus.getCode());
    }

    @Test
    void resultKeepsReadyNotReadyAndStaleSemanticsDistinct() {
        ImplementationReadinessContextFact context = context();
        ImplementationReadinessSnapshotFact readySnapshot = snapshot(
                ImplementationReadinessSnapshotFact.Decision.READY, context, List.of());
        ImplementationReadinessResult ready = new ImplementationReadinessResult(
                ImplementationReadinessResult.Decision.READY, readySnapshot, context, List.of(), List.of());
        assertEquals(ImplementationReadinessResult.Decision.READY, ready.decision());

        ImplementationReadinessResult stale = new ImplementationReadinessResult(
                ImplementationReadinessResult.Decision.STALE, readySnapshot, null,
                List.of("DEVICE_SCOPE_CHANGED"), List.of("DEVICE_SCOPE_CHANGED"));
        assertEquals(ImplementationReadinessResult.Decision.STALE, stale.decision());

        ImplementationReadinessException inconsistent = assertThrows(ImplementationReadinessException.class,
                () -> new ImplementationReadinessResult(ImplementationReadinessResult.Decision.NOT_READY,
                        readySnapshot, context, List.of("EXE_02_NOT_COMPLETED"), List.of()));
        assertEquals(ImplementationReadinessException.Code.OWNER_DATA_CORRUPTED, inconsistent.getCode());
    }

    @Test
    void missingOrReopenedSourceProducesARealNotReadyFactWithoutPlaceholders() {
        ImplementationReadinessContextFact missingContext = contextWithExe02(
                new ImplementationReadinessContextFact.SourceFact(
                        ImplementationReadinessContextFact.SourceCode.EXE_02,
                        ImplementationReadinessContextFact.CompletionStatus.NOT_COMPLETED,
                        0L, List.of(), List.of(), false));
        ImplementationReadinessSnapshotFact missingSnapshot = snapshot(
                ImplementationReadinessSnapshotFact.Decision.NOT_READY, missingContext,
                List.of("EXE_02_NOT_COMPLETED"));
        ImplementationReadinessResult notReady = new ImplementationReadinessResult(
                ImplementationReadinessResult.Decision.NOT_READY, missingSnapshot, missingContext,
                List.of("EXE_02_NOT_COMPLETED"), List.of());
        assertEquals(ImplementationReadinessResult.Decision.NOT_READY, notReady.decision());
        assertEquals(List.of(), missingContext.sourceFacts().get(1).sourceObjectIds());

        ImplementationReadinessContextFact reopenedContext = contextWithExe02(
                new ImplementationReadinessContextFact.SourceFact(
                        ImplementationReadinessContextFact.SourceCode.EXE_02,
                        ImplementationReadinessContextFact.CompletionStatus.COMPLETED,
                        22L, List.of(22L), List.of(new ImplementationReadinessContextFact.WatermarkEntry(
                        "FACT_VERSION", 22L, 22L)), true));
        assertThrows(ImplementationReadinessException.class, () -> snapshot(
                ImplementationReadinessSnapshotFact.Decision.READY, reopenedContext, List.of()));
    }

    @Test
    void publicFailuresRemainClosed() {
        assertEquals(List.of("DUPLICATE_DEVICE", "INVALID_REQUEST", "OWNER_DATA_CORRUPTED",
                        "PROVIDER_UNAVAILABLE", "SNAPSHOT_NOT_FOUND", "TENANT_CONTEXT_MISMATCH"),
                Arrays.stream(ImplementationReadinessException.Code.values()).map(Enum::name).sorted().toList());
    }

    private static ImplementationReadinessSnapshotFact snapshot(
            ImplementationReadinessSnapshotFact.Decision decision,
            ImplementationReadinessContextFact context,
            List<String> unmetCodes) {
        return new ImplementationReadinessSnapshotFact(1L, 10L, 91L, 1, 3L, "CUTOVER",
                decision, context, unmetCodes, LocalDateTime.of(2026, 8, 31, 10, 0));
    }

    private static ImplementationReadinessContextFact context() {
        return new ImplementationReadinessContextFact(5L, List.of(
                new ImplementationReadinessContextFact.DeviceFact(22L, "SN-22", 8L),
                new ImplementationReadinessContextFact.DeviceFact(11L, "SN-11", 7L)),
                new ImplementationReadinessContextFact.ApprovedPlanFact(31L, 2L),
                List.of(source(ImplementationReadinessContextFact.SourceCode.EXE_04,
                                ImplementationReadinessContextFact.CompletionStatus.COMPLETED, 44L),
                        source(ImplementationReadinessContextFact.SourceCode.EXE_02,
                                ImplementationReadinessContextFact.CompletionStatus.COMPLETED, 22L),
                        source(ImplementationReadinessContextFact.SourceCode.EXE_01,
                                ImplementationReadinessContextFact.CompletionStatus.ACCEPTED, 11L),
                        source(ImplementationReadinessContextFact.SourceCode.EXE_03,
                                ImplementationReadinessContextFact.CompletionStatus.COMPLETED, 33L)));
    }

    private static ImplementationReadinessContextFact contextWithExe02(
            ImplementationReadinessContextFact.SourceFact exe02) {
        ImplementationReadinessContextFact base = context();
        return new ImplementationReadinessContextFact(base.projectScopeVersion(), base.devices(), base.approvedPlan(),
                List.of(base.sourceFacts().get(0), exe02, base.sourceFacts().get(2), base.sourceFacts().get(3)));
    }

    private static ImplementationReadinessContextFact.SourceFact source(
            ImplementationReadinessContextFact.SourceCode sourceCode,
            ImplementationReadinessContextFact.CompletionStatus completionStatus,
            long version) {
        return new ImplementationReadinessContextFact.SourceFact(sourceCode, completionStatus, version,
                List.of(version), List.of(new ImplementationReadinessContextFact.WatermarkEntry(
                "FACT_VERSION", version, version)), false);
    }
}
