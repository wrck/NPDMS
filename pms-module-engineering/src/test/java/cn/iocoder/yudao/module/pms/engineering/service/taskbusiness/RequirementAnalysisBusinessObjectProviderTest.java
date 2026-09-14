package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactVector;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormQueryService;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class RequirementAnalysisBusinessObjectProviderTest {
    private final RequirementAnalysisDynamicFormQueryService query = mock(RequirementAnalysisDynamicFormQueryService.class);
    private final RequirementAnalysisRootMapper roots = mock(RequirementAnalysisRootMapper.class);
    private final RequirementAnalysisFactApi facts = mock(RequirementAnalysisFactApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    private final RequirementAnalysisBusinessObjectProvider provider = new RequirementAnalysisBusinessObjectProvider(query, roots, facts, executions);
    private final Context context = new Context(1L, 7L, 9L, 91L, "test");
    private RequirementAnalysisVersionRespVO draft;
    private RequirementAnalysisWorkspaceRespVO workspace;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var login = new LoginUser(); login.setId(7L); login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        draft = new RequirementAnalysisVersionRespVO(); draft.setPreparationId(42L); draft.setProjectId(9L);
        draft.setVersion(2); draft.setContentVersion(1); draft.setDynamicFormInstanceVersion(3);
        draft.setStatus("DRAFT"); draft.setBusinessVersion(1); draft.setControlledFiles(Map.of());
        draft.setAllowedActions(List.of("PATCH_FORM"));
        workspace = new RequirementAnalysisWorkspaceRespVO(); workspace.setProjectId(9L); workspace.setDraft(draft);
        workspace.setAllowedActions(List.of());
        when(query.getWorkspace(any(), any())).thenReturn(workspace); when(query.getDetail(any(), any())).thenReturn(draft);
        when(query.getWorkspace(any(), any(), any())).thenReturn(workspace);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void combinedInspectionReusesCurrentVersionAndDoesNotCacheAcrossCalls() {
        var result = provider.inspectContextAndObjects(context, List.of("42"));
        assertEquals(Set.of("QUERY", "PATCH_FORM"), result.allowedActions());
        assertEquals("42", result.objects().getFirst().objectId());
        verify(query).getWorkspace(any(), any());
        verify(query, never()).getDetail(any(), any());
        draft.setAllowedActions(List.of());
        draft.setContentVersion(2);
        var refreshed = provider.inspectContextAndObjects(context, List.of("42"));
        assertEquals(Set.of("QUERY"), refreshed.allowedActions());
        assertNotEquals(result.objects().getFirst().factVersion(), refreshed.objects().getFirst().factVersion());
        verify(query, times(2)).getWorkspace(any(), any());
    }

    @Test void combinedInspectionKeepsHistoricalVisibilityAndCompletedProofChecks() {
        when(query.getDetail(eq(43L), any())).thenThrow(new IllegalStateException("not visible"));
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("43")));
        verify(query).getDetail(eq(43L), any());
        draft.setStatus("COMPLETED");
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("42")));
        verify(facts).inspect(any());
        draft.setStatus("DRAFT"); draft.setProjectId(10L);
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("42")));
    }

    @Test void automaticAssociationUsesCurrentOwnerRecordAndBindingWithoutAnyBusinessRoundField() {
        SecurityContextHolder.clearContext();
        var row = new PreparationDO(); row.setId(42L); row.setProjectId(9L); row.setVersion(2);
        row.setTemplateSnapshot("{\"binding\":{\"dynamicFormTemplateRevisionId\":701}}");
        when(roots.selectDraft(any())).thenReturn(row);
        var association = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext(
                1L,9L,"PRE_04_REQUIREMENT_ANALYSIS","{\"dynamicFormTemplateRevisionId\":701}");
        assertEquals("42",provider.associationCandidates(association,null,100).getFirst().objectId());
        verifyNoInteractions(executions,query,facts);
        assertTrue(provider.associationCandidates(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext(
                1L,9L,"PRE_04_REQUIREMENT_ANALYSIS","{\"dynamicFormTemplateRevisionId\":702}"),null,100).isEmpty());
    }

    @Test void unattendedCompletionReadsOnlySameRoundOwnerResultWithoutBrowserIdentityOrPrivateBody() {
        SecurityContextHolder.clearContext();
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                9L, 1, 91L, 1, 92L, 1, 93L, 94L, 1, 1, 95L, 1, true, java.time.LocalDateTime.of(2026,9,14,9,0));
        var row = new PreparationDO(); row.setId(42L); row.setProjectId(9L); row.setTenantId(1L);
        row.setDynamicFormInstanceId(500L); row.setVersion(3); row.setContentVersion(2);
        row.setStatusCode("COMPLETED"); row.setCompletedBy(7L); row.setCompletedAt(java.time.LocalDateTime.now());
        when(roots.selectForUpdate(any())).thenReturn(row);
        var result = provider.lockCompletionFact(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42");
        assertTrue(result.handlingCompleted()); assertTrue(result.completionFacts().get("REQUIREMENT_ANALYSIS_COMPLETED"));
        verify(executions).lockAndRevalidate(execution);
        verifyNoInteractions(query, facts);
        row.setStatusCode("DRAFT");
        assertFalse(provider.lockCompletionFact(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42").handlingCompleted());
        doThrow(new IllegalStateException("stale execution")).when(executions).lockAndRevalidate(execution);
        assertThrows(RuntimeException.class, () -> provider.lockCompletionFact(
                new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42"));
    }
    @Test void candidatesUseNewDynamicFormOwnerAndNeverPretendDraftIsCompleted() {
        var result = provider.candidates(context);
        assertEquals(1, result.size()); assertEquals("42", result.getFirst().objectId());
        assertEquals(false, result.getFirst().completionFacts().get(RequirementAnalysisBusinessObjectProvider.COMPLETED_FACT));
        assertTrue(result.getFirst().allowedActions().contains("LINK")); verifyNoInteractions(facts, roots);
    }

    @Test void stageCompletionUsesTheSameOwnerResultAndLocksTheStageWithoutReadingPrivateBody() {
        SecurityContextHolder.clearContext();
        var execution = stageExecution(9L,90L,true);
        var row = new PreparationDO(); row.setId(42L); row.setTenantId(1L); row.setProjectId(9L);
        row.setDynamicFormInstanceId(500L); row.setVersion(3); row.setContentVersion(2);
        row.setStatusCode("COMPLETED"); row.setCompletedBy(7L); row.setCompletedAt(java.time.LocalDateTime.now());
        when(roots.selectForUpdate(any())).thenReturn(row);
        var request = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.StageCompletionContext(1L,execution);
        assertTrue(provider.lockStageCompletionFact(request,"42").handlingCompleted());
        verify(executions).lockAndRevalidateStage(execution); verify(executions,never()).lockAndRevalidate(any());
        verifyNoInteractions(query,facts);
        row.setStatusCode("DRAFT");
        assertFalse(provider.lockStageCompletionFact(request,"42").handlingCompleted());
        row.setProjectId(10L);
        assertThrows(RuntimeException.class,() -> provider.lockStageCompletionFact(request,"42"));
        doThrow(new IllegalStateException("stale stage")).when(executions).lockAndRevalidateStage(execution);
        assertThrows(RuntimeException.class,() -> provider.lockStageCompletionFact(request,"42"));
    }
    @Test void stageContextUsesStageIdentityAndPreservesOwnerActionsWithoutCreatingAnything() {
        var result = provider.inspectStage(new StageBusinessViewProvider.Context(1L, 7L, 9L, 90L,
                "PRE_04_REQUIREMENT_ANALYSIS", "CREATE_ON_FIRST_ACTION", stageExecution(9L, 90L, true)));
        assertEquals(Set.of("QUERY", "PATCH_FORM"), result.allowedActions()); verifyNoInteractions(roots, facts);
    }
    @Test void missingInactiveOrDifferentStageExecutionCannotGrantOwnerWrites() {
        for (var execution : Arrays.asList(null, stageExecution(9L,90L,false), stageExecution(10L,90L,true), stageExecution(9L,91L,true))) {
            var result = provider.inspectStage(new StageBusinessViewProvider.Context(1L,7L,9L,90L,
                    "PRE_04_REQUIREMENT_ANALYSIS","CREATE_ON_FIRST_ACTION",execution));
            assertEquals(Set.of("QUERY"), result.allowedActions());
        }
        verifyNoInteractions(roots, facts);
    }
    private cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext stageExecution(Long projectId, Long stageId, boolean writable) {
        return new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext(
                projectId,1,stageId,1,92L,1,93L,94L,1,2,writable);
    }
    @Test void foreignProjectObjectIsRejected() {
        draft.setProjectId(10L); assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
    }
    @Test void queryOnlyOwnerCannotLinkOrGainWriteActions() {
        draft.setAllowedActions(List.of());
        assertEquals(Set.of("QUERY"), provider.inspect(context, "42").allowedActions());
    }
    @Test void completedStateWithoutOwnerProofCannotBecomeACompletionFact() {
        draft.setStatus("COMPLETED");
        assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
        verify(facts).inspect(any());
    }
    @Test void staleVersionFailsAfterLockingOwnerRoot() {
        var row = new PreparationDO(); row.setProjectId(9L); when(roots.selectForUpdate(any())).thenReturn(row);
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidate(context, "42", "old"));
        verify(roots).selectForUpdate(any()); verifyNoInteractions(facts);
    }
    @Test void forgedActorAndWrongStageTargetAreRejectedBeforeOwnerQueries() {
        assertThrows(RuntimeException.class, () -> provider.inspect(new Context(1L, 8L, 9L, 91L, "test"), "42"));
        assertThrows(RuntimeException.class, () -> provider.inspectStage(new StageBusinessViewProvider.Context(
                1L, 7L, 9L, 90L, "OLD_REQUIREMENT", "REFERENCE_EXISTING", null)));
        verifyNoInteractions(query);
    }
    @Test void multiAttachmentFactFitsExistingStorageAndDetectsFileVersionChanges() {
        List<FileArtifactVersionFact> files = new ArrayList<>();
        for (int i = 1; i <= 20; i++) files.add(new FileArtifactVersionFact((long) i, 1, "reference-" + i,
                "EVIDENCE", "附件" + i, 10L, "text/plain", null, "AVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 1), 1L));
        draft.setControlledFiles(Map.of("PROJECT_BACKGROUND", files));
        var original = provider.inspect(context, "42");
        assertTrue(original.factVersion().length() <= 256); assertEquals(20, original.artifacts().size());
        files.set(0, new FileArtifactVersionFact(1L, 1, "reference-1", "EVIDENCE", "附件1", 10L,
                "text/plain", null, "UNAVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 2), 1L));
        assertNotEquals(original.factVersion(), provider.inspect(context, "42").factVersion());
    }
    @Test void completedFactReusesOwnersLockedProofAndRejectsForeignProof() {
        draft.setStatus("COMPLETED"); draft.setAllowedActions(List.of("CREATE_DRAFT"));
        var fact = mock(RequirementAnalysisFact.class);
        var vector = mock(RequirementAnalysisFactVector.class);
        when(fact.projectId()).thenReturn(9L); when(fact.preparationId()).thenReturn(42L);
        when(fact.status()).thenReturn("COMPLETED"); when(fact.factVector()).thenReturn(vector);
        when(fact.businessVersion()).thenReturn(1); when(fact.contentVersion()).thenReturn(1);
        when(fact.projectVersion()).thenReturn(2); when(fact.templateRevision()).thenReturn(8L);
        when(facts.inspect(any())).thenReturn(fact); when(facts.lockAndRevalidate(any())).thenReturn(fact);
        String version = provider.inspect(context, "42").factVersion();
        var locked = provider.lockAndRevalidate(context, "42", version);
        assertEquals(true, locked.completionFacts().get(RequirementAnalysisBusinessObjectProvider.COMPLETED_FACT));
        verify(facts).lockAndRevalidate(any()); verifyNoInteractions(roots);
        when(fact.projectId()).thenReturn(10L);
        assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
    }
}
