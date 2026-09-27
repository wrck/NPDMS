package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewPolicyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewPolicyMapper;
import cn.iocoder.yudao.module.pms.project.api.rule.ProjectFieldRuleApi;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionReviewPolicyService.*;

class SolutionReviewPolicyServiceTest {
    final ProjectFieldRuleApi rules = mock(ProjectFieldRuleApi.class);
    final SolutionReviewPolicyMapper records = mock(SolutionReviewPolicyMapper.class);
    final SolutionReviewPolicyService service = new SolutionReviewPolicyService(rules, records);
    final SolutionDO solution = new SolutionDO();
    SolutionReviewPolicyDO stored;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); solution.setId(42L); solution.setProjectId(9L); solution.setVersion(6L); solution.setReviewLevel(0);
        when(records.bySolution(any())).thenAnswer(call -> stored);
        when(records.insert(any(SolutionReviewPolicyDO.class))).thenAnswer(call -> { stored = call.getArgument(0); return 1; });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void result(String major, String ordinary) {
        when(rules.lockAndEvaluate(any())).thenReturn(new ProjectFieldRuleApi.Evaluation(true, Map.of(MAJOR,major,ORDINARY,ordinary), "{\"projectVersion\":12}"));
    }
    @Test void ordinaryInputCannotBypassConfiguredMajorReview() {
        result("MATCHED", "NOT_MATCHED");
        assertThrows(ServiceException.class, () -> service.freeze(solution, 0));
        assertNull(stored); assertEquals(0, solution.getReviewLevel());
        service.freeze(solution, 1);
        assertEquals(1, solution.getReviewLevel()); assertEquals(6, stored.getSourceVersion());
        assertEquals("{\"projectVersion\":12}", stored.getEvidenceJson());
        assertThrows(ServiceException.class, () -> service.requireOrdinaryApproval(solution));
    }
    @Test void missingAndContradictoryDecisionsProduceNoRecord() {
        for (var outcomes : new String[][]{{"NOT_MATCHED","NOT_MATCHED"},{"MATCHED","MATCHED"},{"UNKNOWN","MATCHED"}}) {
            result(outcomes[0], outcomes[1]);
            assertThrows(ServiceException.class, () -> service.freeze(solution, 0));
            assertThrows(ServiceException.class, () -> service.freeze(solution, 1));
        }
        assertNull(stored);
    }
    @Test void frozenOrdinaryDecisionSurvivesLaterSourceChangesAndNeverGetsOverwritten() {
        result("NOT_MATCHED", "MATCHED"); service.freeze(solution, 0);
        var evidence = stored.getEvidenceJson(); result("MATCHED", "NOT_MATCHED");
        service.requireOrdinaryApproval(solution); service.freeze(solution, 0);
        assertEquals(evidence, stored.getEvidenceJson()); verify(records,times(1)).insert(any(SolutionReviewPolicyDO.class));
        verify(rules,times(1)).lockAndEvaluate(any());
    }
    @Test void managedApprovalWithoutSubmissionEvidenceIsRejectedButUnconfiguredTemplatesRemainCompatible() {
        result("NOT_MATCHED", "MATCHED");
        assertThrows(ServiceException.class, () -> service.requireOrdinaryApproval(solution));
        when(rules.lockAndEvaluate(any())).thenReturn(new ProjectFieldRuleApi.Evaluation(false,Map.of(),null));
        service.requireOrdinaryApproval(solution); service.freeze(solution,0); assertNull(stored);
    }
}
