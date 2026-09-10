package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NormalClosureApplicationServiceTest {
    private final NormalClosureAccess access = mock(NormalClosureAccess.class);
    private final NormalClosureCheckService checks = mock(NormalClosureCheckService.class);
    private final NormalClosureMapper mapper = mock(NormalClosureMapper.class);
    private final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final BpmNormalClosureApi bpm = mock(BpmNormalClosureApi.class);
    private final ExplicitPermissionApi permissions = mock(ExplicitPermissionApi.class);
    private final NormalClosureApplicationService service = new NormalClosureApplicationService(access, checks, mapper, stages, commands, bpm, permissions);
    private final NormalClosureAccess.Actor actor = new NormalClosureAccess.Actor(7L, 11L, "corr");
    private final NormalClosureApplicationService.SubmitCommand submit = new NormalClosureApplicationService.SubmitCommand(9L, 5L, 4, 3L, "submit-1");
    private ProjectMasterDO project;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setVersion(4); project.setCurrentStage("S1");
        when(access.lock(9L, 4, 3L, actor)).thenReturn(new NormalClosureAccess.Context(project, 3L));
        when(mapper.selectLatestSnapshot(any())).thenReturn(snapshot());
        when(checks.evaluateLocked(project, 3L, 11L, "corr")).thenReturn(evaluation("digest-original"));
        var sm = new ProjectMemberAssignmentDO(); sm.setUserId(12L);
        when(mapper.selectPrimaryServiceManagersForUpdate(any())).thenReturn(List.of(sm));
        when(permissions.lockAndCheck(7L, 23L, NormalClosureAccess.AUDIT)).thenReturn(true);
        doAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                ((Supplier<?>) invocation.getArgument(3)).get())).when(commands).execute(any(), anyString(), any(), any(), any());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    static NormalClosureSnapshotDO snapshot() {
        var s = new NormalClosureSnapshotDO(); s.setId(5L); s.setProjectId(9L); s.setPassed(true); s.setProjectVersion(4);
        s.setTreeVersion(3L); s.setFromStage("S1"); s.setSourceDigest("digest-original"); return s;
    }
    static NormalClosureCheckService.Evaluation evaluation(String digest) {
        return new NormalClosureCheckService.Evaluation(NormalClosurePolicy.parseFrozen(NormalClosurePolicyTest.policy()),
                List.of(new NormalClosureViews.Check("TASK_BUSINESS_FACTS", true, null, 21L)), "[]", "{}", digest, null);
    }
    @Test void changedOwnerVersionStillPassingCannotReuseApprovalEvidence() {
        var ex = assertThrows(RuntimeException.class, () -> NormalClosureApplicationService.requireUnchangedEvidence(snapshot(), evaluation("digest-new")));
        assertEquals("CLOSURE_APPROVED_FACTS_STALE_CANCEL_AND_REAPPLY", ex.getMessage());
        assertDoesNotThrow(() -> NormalClosureApplicationService.requireUnchangedEvidence(snapshot(), evaluation("digest-original")));
    }
    @Test void onlyLatestPassingMatchingStageAndVersionsCanSubmit() {
        var stale = snapshot(); stale.setId(6L);
        assertThrows(RuntimeException.class, () -> NormalClosureApplicationService.requireSnapshot(stale, submit, "S1"));
        stale.setId(5L); stale.setPassed(false);
        assertThrows(RuntimeException.class, () -> NormalClosureApplicationService.requireSnapshot(stale, submit, "S1"));
        stale.setPassed(true);
        assertThrows(RuntimeException.class, () -> NormalClosureApplicationService.requireSnapshot(stale, submit, "S6"));
    }
    @Test void submitRejectsChangedFactsBeforeBpmStart() {
        when(checks.evaluateLocked(project, 3L, 11L, "corr")).thenReturn(evaluation("digest-changed"));
        assertThrows(RuntimeException.class, () -> service.submit(submit, actor));
        verifyNoInteractions(bpm); verify(mapper, never()).insertApplication(any()); verifyNoInteractions(stages);
    }
    @Test void superAdminWithoutExplicitMaterialGrantCannotStart() {
        when(permissions.lockAndCheck(7L, 23L, NormalClosureAccess.AUDIT)).thenReturn(false);
        assertEquals("CLOSURE_EXPLICIT_MATERIAL_REVIEWER_REQUIRED", assertThrows(RuntimeException.class,
                () -> service.submit(submit, actor)).getMessage());
        verifyNoInteractions(bpm); verify(mapper, never()).insertApplication(any());
    }
    @Test void bpmUnavailableNeverCreatesApplicationOrExit() {
        when(bpm.inspectDefinition(7L, NormalClosurePolicy.PROCESS_KEY)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.submit(submit, actor));
        verify(mapper, never()).insertApplication(any()); verify(mapper, never()).closeProjectIfMatch(any()); verifyNoInteractions(stages);
    }
    @Test void twoRealBpmReviewsCloseCurrentS1AtomicallyWithoutInventingS6() {
        var app = NormalClosureBpmEvidenceTest.application(); app.setId(8L); app.setTenantId(7L); app.setProjectId(9L);
        app.setSnapshotId(5L); app.setStatus("IN_REVIEW"); app.setVersion(0); app.setProjectVersion(4); app.setTreeVersion(3L);
        app.setFromStage("S1"); app.setApplicantUserId(11L);
        when(bpm.inspectResult(7L, "instance-1")).thenReturn(NormalClosureBpmEvidenceTest.result("APPROVE", NormalClosureBpmEvidenceTest.approved()));
        when(mapper.selectApplicationByProcess(any())).thenReturn(app);
        when(mapper.selectApplicationForUpdate(any())).thenReturn(app);
        when(access.lockProject(9L, 7L)).thenReturn(project);
        when(access.lock(eq(9L), eq(4), eq(3L), any())).thenReturn(new NormalClosureAccess.Context(project, 3L));
        var stage = new ProjectStageInstanceDO(); stage.setId(31L); stage.setVersion(2); stage.setStageCode("S1");
        var graph = mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver.Resolution.class);
        when(graph.current()).thenReturn(stage);
        var proof = evaluation("digest-original");
        when(checks.evaluateLocked(eq(project), eq(3L), eq(11L), anyString())).thenReturn(
                new NormalClosureCheckService.Evaluation(proof.policy(), proof.checks(), proof.evidence(), proof.sourceVector(), proof.sourceDigest(), graph));
        when(mapper.selectSnapshot(any())).thenReturn(snapshot()); when(mapper.selectFrozenTemplateRevisionId(any())).thenReturn(41L);
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(mapper.closeProjectIfMatch(any())).thenReturn(1);
        when(mapper.insertExitRecord(any())).thenReturn(1); when(mapper.insertReview(any())).thenReturn(1);
        when(mapper.decideApplicationIfMatch(any())).thenReturn(1);
        service.onBpmResult(7L, "instance-1");
        var exit = org.mockito.ArgumentCaptor.forClass(NormalClosureExitRecordDO.class);
        verify(mapper).insertExitRecord(exit.capture()); assertEquals("S1", exit.getValue().getClosedFromStage());
        assertEquals("NORMAL_CLOSED", exit.getValue().getAfterLifecycleStatus()); assertEquals(5, exit.getValue().getProjectVersion());
        assertEquals(31L, exit.getValue().getStageInstanceId()); assertEquals(41L, exit.getValue().getTemplateRevisionId());
        verify(mapper, times(2)).insertReview(any()); verify(stages, times(1)).updateStatusIfMatch(any());
        verify(mapper).closeProjectIfMatch(argThat(update -> "S1".equals(update.fromStage())));
    }
    @Test void checkOnlyAppendsImmutableSnapshotAndCannotCloseProject() {
        when(mapper.insertSnapshot(any())).thenReturn(1);
        var row = service.check(new NormalClosureApplicationService.CheckCommand(9L, 4, 3L, "check-1"), actor);
        assertTrue(row.getPassed()); assertEquals("S1", row.getFromStage());
        verify(mapper).insertSnapshot(any()); verify(mapper, never()).closeProjectIfMatch(any());
        verifyNoInteractions(bpm, stages);
    }
}
