package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi.Fact;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService.View;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService.Workspace;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService;
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

class RequirementAnalysisEntityBusinessObjectContractTest {
    @Test void stageCapabilityIsMetadataOnly() {
        assertTrue(provider.supportsStageCompletionFacts());
        verifyNoInteractions(query, roots, facts, executions);
    }
    private final RequirementAnalysisEntityQueryService query = mock(RequirementAnalysisEntityQueryService.class);
    private final RequirementAnalysisMapper roots = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisEntityFactApi facts = mock(RequirementAnalysisEntityFactApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    private final RequirementAnalysisEntityBusinessObjectProvider provider = new RequirementAnalysisEntityBusinessObjectProvider(query, roots, facts, executions);
    private final Context context = new Context(1L, 7L, 9L, 91L, "test");
    private View draft;
    private RequirementAnalysisRevisionDO revision;
    private Workspace workspace;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var login = new LoginUser(); login.setId(7L); login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        revision = new RequirementAnalysisRevisionDO(); revision.setId(42L); revision.setEntityId(40L);
        revision.setTenantId(1L); revision.setProjectId(9L); revision.setVersion(2); revision.setRevisionNo(1); revision.setRevisionState("DRAFT");
        draft = mock(View.class); when(draft.projectId()).thenAnswer(invocation -> revision.getProjectId());
        when(draft.revision()).thenAnswer(invocation -> revision.revisionMetadata());
        when(draft.attachments()).thenReturn(List.of()); when(draft.allowedActions()).thenReturn(List.of("PATCH_FORM"));
        workspace = mock(Workspace.class); when(workspace.projectId()).thenReturn(9L); when(workspace.draft()).thenReturn(draft);
        when(workspace.allowedActions()).thenReturn(List.of());
        when(query.revision(any(), any())).thenReturn(draft);
        when(query.workspace(any(), any(), any(), any())).thenReturn(workspace);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void combinedInspectionReusesCurrentVersionAndDoesNotCacheAcrossCalls() {
        var result = provider.inspectContextAndObjects(context, List.of("42"));
        assertEquals(Set.of("QUERY", "PATCH_FORM"), result.allowedActions());
        assertEquals("42", result.objects().getFirst().objectId());
        verify(query).workspace(eq(context.projectId()), any(), isNull(), eq(context.taskId()));
        verify(query, never()).revision(any(), any());
        when(draft.allowedActions()).thenReturn(List.of());
        when(draft.extensionValueVersion()).thenReturn(2);
        var refreshed = provider.inspectContextAndObjects(context, List.of("42"));
        assertEquals(Set.of("QUERY"), refreshed.allowedActions());
        assertNotEquals(result.objects().getFirst().factVersion(), refreshed.objects().getFirst().factVersion());
        verify(query, times(2)).workspace(eq(context.projectId()), any(), isNull(), eq(context.taskId()));
    }

    @Test void combinedInspectionKeepsHistoricalVisibilityAndCompletedProofChecks() {
        when(query.revision(eq(43L), any())).thenThrow(new IllegalStateException("not visible"));
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("43")));
        verify(query).revision(eq(43L), any());
        revision.setRevisionState("FROZEN");
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("42")));
        verify(facts).inspect(any());
        revision.setRevisionState("DRAFT"); revision.setProjectId(10L);
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context, List.of("42")));
    }

    @Test void automaticAssociationUsesCurrentOwnerRecordAndBindingWithoutAnyBusinessRoundField() {
        SecurityContextHolder.clearContext();
        var row = new RequirementAnalysisRevisionDO(); row.setId(42L); row.setProjectId(9L); row.setVersion(2);
        row.setExecutionSnapshot("{\"binding\":{\"dynamicFormTemplateRevisionId\":701}}");
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
        var row = new RequirementAnalysisRevisionDO(); row.setId(42L); row.setProjectId(9L); row.setTenantId(1L);
        row.setVersion(3); row.setRevisionNo(2);
        row.setRevisionState("FROZEN"); row.setFrozenBy(7L); row.setFrozenAt(java.time.LocalDateTime.now());
        when(roots.lockRevision(any())).thenReturn(row);
        var result = provider.lockCompletionFact(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42");
        assertTrue(result.handlingCompleted()); assertTrue(result.completionFacts().get("REQUIREMENT_ANALYSIS_COMPLETED"));
        verify(executions).lockAndRevalidate(execution);
        verifyNoInteractions(query, facts);
        row.setRevisionState("DRAFT");
        assertFalse(provider.lockCompletionFact(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42").handlingCompleted());
        doThrow(new IllegalStateException("stale execution")).when(executions).lockAndRevalidate(execution);
        assertThrows(RuntimeException.class, () -> provider.lockCompletionFact(
                new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(1L, execution), "42"));
    }
    @Test void candidatesUseNewDynamicFormOwnerAndNeverPretendDraftIsCompleted() {
        var result = provider.candidates(context);
        assertEquals(1, result.size()); assertEquals("42", result.getFirst().objectId());
        assertEquals(false, result.getFirst().completionFacts().get(RequirementAnalysisEntityBusinessObjectProvider.COMPLETED_FACT));
        assertTrue(result.getFirst().allowedActions().contains("LINK")); verifyNoInteractions(facts, roots);
    }

    @Test void stageCompletionUsesTheSameOwnerResultAndLocksTheStageWithoutReadingPrivateBody() {
        SecurityContextHolder.clearContext();
        var execution = stageExecution(9L,90L,true);
        var row = new RequirementAnalysisRevisionDO(); row.setId(42L); row.setTenantId(1L); row.setProjectId(9L);
        row.setVersion(3); row.setRevisionNo(2);
        row.setRevisionState("FROZEN"); row.setFrozenBy(7L); row.setFrozenAt(java.time.LocalDateTime.now());
        when(roots.lockRevision(any())).thenReturn(row);
        var request = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.StageCompletionContext(1L,execution);
        assertTrue(provider.lockStageCompletionFact(request,"42").handlingCompleted());
        verify(executions).lockAndRevalidateStage(execution); verify(executions,never()).lockAndRevalidate(any());
        verifyNoInteractions(query,facts);
        row.setRevisionState("DRAFT");
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
        revision.setProjectId(10L); assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
    }
    @Test void queryOnlyOwnerCannotLinkOrGainWriteActions() {
        when(draft.allowedActions()).thenReturn(List.of());
        assertEquals(Set.of("QUERY"), provider.inspect(context, "42").allowedActions());
    }
    @Test void completedStateWithoutOwnerProofCannotBecomeACompletionFact() {
        revision.setRevisionState("FROZEN");
        assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
        verify(facts).inspect(any());
    }
    @Test void staleVersionFailsAfterLockingOwnerRoot() {
        var row = new RequirementAnalysisRevisionDO(); row.setProjectId(9L); when(roots.lockRevision(any())).thenReturn(row);
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidate(context, "42", "old"));
        verify(roots).lockRevision(any()); verifyNoInteractions(facts);
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
        var fileSet = mock(FileReferenceSetFact.class); when(fileSet.activeFacts()).thenReturn(files);
        when(draft.attachments()).thenReturn(List.of(fileSet));
        var original = provider.inspect(context, "42");
        assertTrue(original.factVersion().length() <= 256); assertEquals(20, original.artifacts().size());
        files.set(0, new FileArtifactVersionFact(1L, 1, "reference-1", "EVIDENCE", "附件1", 10L,
                "text/plain", null, "UNAVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 2), 1L));
        assertNotEquals(original.factVersion(), provider.inspect(context, "42").factVersion());
    }
    @Test void completedFactReusesOwnersLockedProofAndRejectsForeignProof() {
        revision.setRevisionState("FROZEN"); when(draft.allowedActions()).thenReturn(List.of("CREATE_DRAFT"));
        var fact = mock(Fact.class);
        when(fact.projectId()).thenReturn(9L); when(fact.revisionId()).thenReturn(42L);
        when(fact.frozenBy()).thenReturn(7L); when(fact.frozenAt()).thenReturn(java.time.LocalDateTime.now());
        when(facts.inspect(any())).thenReturn(fact); when(facts.lockAndRevalidate(any())).thenReturn(fact);
        String version = provider.inspect(context, "42").factVersion();
        var locked = provider.lockAndRevalidate(context, "42", version);
        assertEquals(true, locked.completionFacts().get(RequirementAnalysisEntityBusinessObjectProvider.COMPLETED_FACT));
        verify(facts).lockAndRevalidate(any()); verifyNoInteractions(roots);
        when(fact.projectId()).thenReturn(10L);
        assertThrows(RuntimeException.class, () -> provider.inspect(context, "42"));
    }
}
