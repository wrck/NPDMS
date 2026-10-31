package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessContextFact.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ImplementationReadinessRulesTest {
    private final List<DeviceFact> devices = List.of(new DeviceFact(1L, "SN-1", 3L), new DeviceFact(2L, "SN-2", 4L));
    @Test void installationExemptionRequiresRemainingDevicesAndVersionsPolicy() {
        var rows = List.of(new ImplementationReadinessRules.Record(11L, 1L, 1L, true));
        var allowed = ImplementationReadinessRules.installation(rows, devices,
                new ImplementationReadinessRules.InstallationScope(9L, Set.of(2L), "policy-a"));
        assertEquals(CompletionStatus.COMPLETED, allowed.completionStatus());
        assertTrue(allowed.watermarkEntries().contains(new WatermarkEntry("DEVICE_ASSIGNMENT", 2L, 4L)));
        var changed = ImplementationReadinessRules.installation(rows, devices,
                new ImplementationReadinessRules.InstallationScope(9L, Set.of(2L), "policy-b"));
        assertNotEquals(allowed, changed);
        var removed = ImplementationReadinessRules.installation(rows, devices,
                new ImplementationReadinessRules.InstallationScope(9L, Set.of(), "policy-c"));
        assertEquals(CompletionStatus.NOT_COMPLETED, removed.completionStatus());
        var incomplete = ImplementationReadinessRules.installation(
                List.of(new ImplementationReadinessRules.Record(11L, 2L, 1L, false)), devices,
                new ImplementationReadinessRules.InstallationScope(9L, Set.of(2L), "policy-a"));
        assertEquals(CompletionStatus.NOT_COMPLETED, incomplete.completionStatus());
    }
    @Test void oneCompletedDeviceDoesNotCompleteProjectMilestone() {
        var fact = ImplementationReadinessRules.milestone(SourceCode.EXE_02,
                List.of(new ImplementationReadinessRules.Record(11L, 1L, 1L, true)), devices);
        assertEquals(CompletionStatus.NOT_COMPLETED, fact.completionStatus());
    }
    @Test void reopenedOrFailedCurrentRecordBlocksPreviouslyCompletedCoverage() {
        var fact = ImplementationReadinessRules.milestone(SourceCode.EXE_03, List.of(
                new ImplementationReadinessRules.Record(11L, 1L, 1L, true),
                new ImplementationReadinessRules.Record(12L, 1L, 2L, true),
                new ImplementationReadinessRules.Record(13L, 2L, 2L, false)), devices);
        assertEquals(CompletionStatus.NOT_COMPLETED, fact.completionStatus());
    }
    @Test void completeCoverageCarriesEveryRecordAndAssignmentVersion() {
        var fact = ImplementationReadinessRules.milestone(SourceCode.EXE_04, List.of(
                new ImplementationReadinessRules.Record(11L, 1L, 1L, true),
                new ImplementationReadinessRules.Record(12L, 2L, 2L, true)), devices);
        assertEquals(CompletionStatus.COMPLETED, fact.completionStatus());
        assertTrue(fact.watermarkEntries().contains(new WatermarkEntry("DEVICE_ASSIGNMENT", 2L, 4L)));
        var changed = ImplementationReadinessRules.milestone(SourceCode.EXE_04, List.of(
                new ImplementationReadinessRules.Record(11L, 1L, 1L, true),
                new ImplementationReadinessRules.Record(12L, 2L, 2L, true)),
                List.of(devices.getFirst(), new DeviceFact(2L, "SN-2", 5L)));
        assertNotEquals(fact, changed);
    }
    @Test void unboundProjectRecordCannotStandInForDeviceCoverage() {
        var fact = ImplementationReadinessRules.milestone(SourceCode.EXE_04,
                List.of(new ImplementationReadinessRules.Record(11L, 1L, null, true)), devices);
        assertEquals(CompletionStatus.NOT_COMPLETED, fact.completionStatus());
    }
}
