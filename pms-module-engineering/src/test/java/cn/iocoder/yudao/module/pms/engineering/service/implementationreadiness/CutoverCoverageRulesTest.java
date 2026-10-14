package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi.Device;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CutoverCoverageRulesTest {
    private final List<Device> scope = List.of(new Device(1L, "SN-1", 1L), new Device(2L, "SN-2", 1L));
    @Test void partialSuccessCannotCompleteWholeProject() {
        var result = CutoverCoverageRules.calculate(scope, List.of(success(11L, 1L, 1L)));
        assertFalse(result.complete()); assertEquals(List.of(2L), result.missingDeviceIds());
    }
    @Test void successfulUnionCoversCurrentRangeWithoutDoubleCounting() {
        var result = CutoverCoverageRules.calculate(scope, List.of(success(11L, 1L, 1L), success(12L, 2L, 1L), success(13L, 1L, 1L)));
        assertTrue(result.complete()); assertEquals(List.of(1L, 2L), result.coveredDeviceIds());
    }
    @Test void newAssignmentInvalidatesPreviousSuccessAndChangesFactVersion() {
        var before = CutoverCoverageRules.calculate(scope, List.of(success(11L, 1L, 1L), success(12L, 2L, 1L)));
        var after = CutoverCoverageRules.calculate(List.of(scope.getFirst(), new Device(2L, "SN-2", 2L)),
                List.of(success(11L, 1L, 1L), success(12L, 2L, 1L)));
        assertFalse(after.complete()); assertNotEquals(before.factVersion(), after.factVersion());
    }
    @Test void emptyRangeOrNoSuccessIsNotAnImplicitWaiver() {
        assertFalse(CutoverCoverageRules.calculate(List.of(), List.of()).complete());
        assertFalse(CutoverCoverageRules.calculate(scope, List.of()).complete());
    }
    private CutoverResultApi.Result success(Long closure, Long device, Long assignment) {
        return new CutoverResultApi.Result(1L, 9L, closure + 100, "CUT-" + closure, "割接", closure, 1L,
                "CUTOVER_CLOSURE:" + closure + ":1", 1L, LocalDateTime.of(2026, 10, 12, 10, 0),
                List.of(new CutoverResultApi.Device(device, "SN-" + device, assignment)), List.of(), List.of());
    }
}
