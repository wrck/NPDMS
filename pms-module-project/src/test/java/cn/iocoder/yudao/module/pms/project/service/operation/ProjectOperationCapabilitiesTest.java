package cn.iocoder.yudao.module.pms.project.service.operation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOperationCapabilitiesTest {
    private ProjectOperationRuleEvaluator.Evaluation matched() {
        return new ProjectOperationRuleEvaluator.Evaluation("MATCHED", null);
    }
    @Test void projectCannotGrantMissingOwnerPermission() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", false, true, true,
                matched(), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("OWNER_OPERATION_FORBIDDEN", result.reason());
    }
    @Test void ownerCannotGrantMissingExecutionEligibility() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, false, true,
                matched(), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("NODE_EXECUTION_FORBIDDEN", result.reason());
    }
    @Test void unknownIsNotAnEmptyRule() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, true, true,
                ProjectOperationRuleEvaluator.Evaluation.unknown("FACT_UNAVAILABLE"), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("FACT_UNAVAILABLE", result.reason());
    }
    @Test void unknownWithoutDiagnosticMustStillBeDenied() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, true, true,
                new ProjectOperationRuleEvaluator.Evaluation("UNKNOWN", null), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("OPERATION_RULE_UNKNOWN", result.reason());
    }
    @Test void missingPreconditionObservationIsNotPermission() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, true, true,
                null, ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("OPERATION_PRECONDITION_NOT_EVALUATED", result.reason());
    }
    @Test void missingRuntimeNeverGrantsExecution() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, true, false,
                matched(), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertFalse(result.allowed()); assertEquals("OPERATION_RUNTIME_NOT_INSTALLED", result.reason());
    }
    @Test void postconditionIsNotClaimedSatisfiedByAnOpenPage() {
        var result = ProjectOperationCapabilities.combine("op", 1, "操作", true, true, true,
                matched(), ProjectOperationRuleEvaluator.Evaluation.pending(), null);
        assertTrue(result.allowed()); assertEquals("NOT_EVALUATED", result.post().outcome());
    }
    @Test void explicitNoneStillNeedsBothAuthorities() {
        var none = new ProjectOperationRuleEvaluator.Evaluation("NO_ADDITIONAL_RULE", null);
        assertTrue(ProjectOperationCapabilities.combine("op", 1, "操作", true, true, true, none, none, null).allowed());
        assertFalse(ProjectOperationCapabilities.combine("op", 1, "操作", true, false, true, none, none, null).allowed());
    }
}
