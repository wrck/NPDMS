package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.solutionreview.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionService;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class SolutionTieredReviewServiceTest {
    @Mock SolutionMapper solutions;
    @Mock SolutionReviewMapper reviews;
    @Mock SolutionReviewBpmApi bpm;
    @Mock ProjectScopeApi scope;
    @Mock ProjectParticipantFactApi participants;
    @Mock PermissionApi permissions;
    @Mock EngineeringRuleReevaluationEvents events;
    @Mock DeliverableMapper deliverables;
    @Mock EngineeringRecordCodeGenerator codes;
    @Mock SolutionService original;
    @Mock SolutionReviewPolicyService policies;
    @InjectMocks SolutionTieredReviewService service;
    SolutionDO solution;
    SolutionReviewDO stored;
    private final SolutionTieredReviewService.Selection selection = new SolutionTieredReviewService.Selection(9L, 42L);
    private SolutionTieredReviewService.Start command(int version) {
        return new SolutionTieredReviewService.Start(9L,42L,version,"definition-1", Map.of("manager",8L,"engineering",10L));
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        var member = new ProjectParticipantFact(9L,7L,Set.of("PROJECT_MANAGER"),"PRIMARY","ACTIVE","S3",1L,1L);
        when(participants.inspect(any())).thenReturn(member); when(participants.lockAndRevalidate(any())).thenReturn(member);
        solution = new SolutionDO(); solution.setId(42L); solution.setTenantId(1L); solution.setProjectId(9L);
        solution.setVersion(0L); solution.setStatus(0); solution.setReviewLevel(1); solution.setSolutionType("IMPLEMENTATION");
        solution.setName("重大方案"); solution.setCode("SOL-42"); solution.setBackground("保留源正文");
        when(reviews.source(any())).thenReturn(solution);
        when(reviews.bySolution(any())).thenAnswer(call -> stored);
        when(reviews.insert(any(SolutionReviewDO.class))).thenAnswer(call -> { stored = call.getArgument(0); stored.setId(100L); return 1; });
        when(reviews.updateById(any(SolutionReviewDO.class))).thenReturn(1);
        when(reviews.selectById(100L)).thenAnswer(call -> stored);
        when(solutions.updateById(any(SolutionDO.class))).thenAnswer(call -> { solution.setVersion(solution.getVersion()+1); return 1; });
        when(bpm.start(any())).thenReturn(new SolutionReviewBpmApi.Started("instance-1","definition-1"));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    private void result(String status) {
        when(bpm.result(1L,"instance-1")).thenReturn(new SolutionReviewBpmApi.Result("instance-1","definition-1",
                "SOL_REVIEW:1:42",status,List.of(new SolutionReviewBpmApi.Review("manager",8L,"APPROVE","初审",LocalDateTime.now().minusMinutes(1)),
                new SolutionReviewBpmApi.Review("engineering",10L,status,"复审",LocalDateTime.now()))));
    }
    @Test void freezesVersionAndIdenticalSubmissionReplaysWithoutSecondProcess() {
        var request = command(0); service.start(request);
        assertEquals(2, solution.getStatus()); assertEquals(2, stored.getSourceVersion()); assertEquals(0, stored.getRequestVersion());
        assertSame(stored, service.start(request)); verify(bpm,times(1)).start(any());
        assertThrows(IllegalArgumentException.class, () -> service.start(command(9)));
    }
    @Test void firstApprovalDoesNotCompleteMajorSolution() {
        service.start(command(0)); result("RUNNING"); service.refresh(selection);
        assertEquals(2, solution.getStatus()); assertNull(solution.getBaselineVersion());
        verifyNoInteractions(deliverables);
    }
    @Test void finalApprovalArchivesOnceAndProtectsApprovedVersion() {
        service.start(command(0)); result("APPROVE"); service.refresh(selection);
        assertEquals(3, solution.getStatus()); assertEquals(solution.getVersion().longValue(), solution.getBaselineVersion().longValue());
        assertEquals(10L, solution.getApprovedBy()); assertEquals("APPROVE", stored.getStatus());
        assertTrue(service.approved(solution, true));
        service.refresh(selection); verify(deliverables,times(1)).insert(any(cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO.class));
        solution.setVersion(solution.getVersion()+1); assertFalse(service.approved(solution,true));
    }
    @Test void rejectionKeepsOriginalAndOnlyCopiesToNewDraft() {
        service.start(command(0)); result("REJECT"); service.refresh(selection);
        assertEquals(4,solution.getStatus()); assertNull(solution.getBaselineVersion()); assertFalse(service.approved(solution,true));
        when(original.createSolution(any())).thenReturn(43L);
        assertEquals(43L,service.revise(selection));
        var captured = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO.class);
        verify(original).createSolution(captured.capture()); assertNull(captured.getValue().getId());
        assertEquals("保留源正文",captured.getValue().getBackground()); assertEquals(4,solution.getStatus());
    }
    @Test void currentSourceChangeRejectsTerminalResultWithoutOverwrite() {
        service.start(command(0)); result("APPROVE"); solution.setVersion(99L);
        assertThrows(IllegalStateException.class, () -> service.refresh(selection)); assertNull(solution.getBaselineVersion());
        verifyNoInteractions(deliverables);
    }
    @Test void staleVersionMissingManagerAndEmptyScopeFailBeforeProcessStart() {
        assertThrows(IllegalArgumentException.class, () -> service.start(command(3)));
        when(participants.inspect(any())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.start(command(0)));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> service.read(selection)); verifyNoInteractions(bpm);
    }
    @Test void mismatchedProcessIdentityNeverApprovesSource() {
        service.start(command(0));
        when(bpm.result(1L,"instance-1")).thenReturn(new SolutionReviewBpmApi.Result("instance-1","foreign","SOL_REVIEW:1:42","APPROVE",List.of()));
        assertThrows(IllegalStateException.class, () -> service.refresh(selection)); assertNull(solution.getBaselineVersion());
    }
    @Test void reviewFormRequiresSameTenantExistingReviewAndCurrentProjectScope() {
        when(solutions.selectById(42L)).thenReturn(solution);
        assertThrows(RuntimeException.class, () -> service.sourceForReview("SOL_REVIEW:2:42"));
        verify(solutions, never()).selectById(anyLong());
        assertThrows(RuntimeException.class, () -> service.sourceForReview("SOL_REVIEW:1:42"));
        service.start(command(0));
        assertSame(solution, service.sourceForReview("SOL_REVIEW:1:42"));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> service.sourceForReview("SOL_REVIEW:1:42"));
    }
}
