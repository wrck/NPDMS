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
    private final RequirementAnalysisBusinessObjectProvider provider = new RequirementAnalysisBusinessObjectProvider(query, roots, facts);
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
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void candidatesUseNewDynamicFormOwnerAndNeverPretendDraftIsCompleted() {
        var result = provider.candidates(context);
        assertEquals(1, result.size()); assertEquals("42", result.getFirst().objectId());
        assertEquals(false, result.getFirst().completionFacts().get(RequirementAnalysisBusinessObjectProvider.COMPLETED_FACT));
        assertTrue(result.getFirst().allowedActions().contains("LINK")); verifyNoInteractions(facts, roots);
    }
    @Test void stageContextUsesStageIdentityAndPreservesOwnerActionsWithoutCreatingAnything() {
        var result = provider.inspectStage(new StageBusinessViewProvider.Context(1L, 7L, 9L, 90L,
                "PRE_04_REQUIREMENT_ANALYSIS", "CREATE_ON_FIRST_ACTION"));
        assertEquals(Set.of("QUERY", "PATCH_FORM"), result.allowedActions()); verifyNoInteractions(roots, facts);
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
                1L, 7L, 9L, 90L, "OLD_REQUIREMENT", "REFERENCE_EXISTING")));
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
