package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.RequirementAnalysisEntityFactApi;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityQueryService;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisEntityBusinessObjectProviderTest {
    private final RequirementAnalysisEntityQueryService queries = mock(RequirementAnalysisEntityQueryService.class);
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisEntityFactApi facts = mock(RequirementAnalysisEntityFactApi.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final RequirementAnalysisEntityBusinessObjectProvider provider = new RequirementAnalysisEntityBusinessObjectProvider(queries, mapper, facts, executions);
    private final TaskBusinessObjectProvider.Context context = new TaskBusinessObjectProvider.Context(1L,7L,9L,91L,"test");
    private RequirementAnalysisRevisionDO row;
    private RequirementAnalysisEntityQueryService.View view;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var login = new LoginUser(); login.setId(7L); login.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        row = new RequirementAnalysisRevisionDO(); row.setId(42L); row.setEntityId(40L); row.setProjectId(9L); row.setTenantId(1L);
        row.setRevisionNo(1); row.setVersion(2); row.setRevisionState("DRAFT");
        view = mock(RequirementAnalysisEntityQueryService.View.class);
        when(view.projectId()).thenReturn(9L); when(view.revision()).thenAnswer(invocation -> row.revisionMetadata());
        when(view.allowedActions()).thenReturn(List.of("PATCH_FORM")); when(view.attachments()).thenReturn(List.of());
        when(queries.workspace(any(),any(),any(),any())).thenReturn(new RequirementAnalysisEntityQueryService.Workspace(9L,null,view,List.of()));
        when(queries.revision(any(),any())).thenReturn(view);
        when(mapper.lockRevision(any())).thenReturn(row);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void combinesCurrentReadsAndDetectsSubsequentRevisionChange() {
        var initial = provider.inspectContextAndObjects(context,List.of("42"));
        assertEquals("42", initial.objects().getFirst().objectId());
        verify(queries,never()).revision(any(),any());
        row.setVersion(3);
        var changed = provider.inspectContextAndObjects(context,List.of("42"));
        assertNotEquals(initial.objects().getFirst().factVersion(),changed.objects().getFirst().factVersion());
        verify(queries,times(2)).workspace(eq(9L),any(),isNull(),eq(91L));
    }
    @Test void historicalVisibilityAndCompletedProofCannotBeSkipped() {
        when(queries.revision(eq(43L),any())).thenThrow(new IllegalStateException("not visible"));
        assertThrows(RuntimeException.class, () -> provider.inspectContextAndObjects(context,List.of("43")));
        row.setRevisionState("FROZEN");
        assertThrows(RuntimeException.class, () -> provider.inspect(context,"42"));
        verify(facts).inspect(argThat(q -> q.entityId().equals(40L) && q.revisionId().equals(42L)));
    }
    @Test void unattendedCompletionUsesNewOwnerRowsAndRequiresFreezeEvidence() {
        SecurityContextHolder.clearContext();
        var execution = new ProjectTaskExecutionContext(9L,1,91L,1,92L,1,93L,94L,1,1,95L,1,true,LocalDateTime.now());
        var request = new TaskBusinessObjectProvider.CompletionContext(1L,execution);
        assertFalse(provider.lockCompletionFact(request,"42").handlingCompleted());
        row.setRevisionState("FROZEN");
        assertThrows(RuntimeException.class, () -> provider.lockCompletionFact(request,"42"));
        row.setFrozenBy(7L); row.setFrozenAt(LocalDateTime.now());
        assertTrue(provider.lockCompletionFact(request,"42").handlingCompleted());
        row.setProjectId(10L);
        assertThrows(RuntimeException.class, () -> provider.lockCompletionFact(request,"42"));
        verifyNoInteractions(queries,facts);
    }
    @Test void automaticAssociationRetainsOriginalFormBindingIdentity() {
        SecurityContextHolder.clearContext();
        row.setExecutionSnapshot("{\"binding\":{\"dynamicFormTemplateRevisionId\":701}}");
        when(mapper.selectDraft(any())).thenReturn(row);
        var match = new TaskBusinessObjectProvider.AssociationContext(1L,9L,"PRE_04_REQUIREMENT_ANALYSIS","{\"dynamicFormTemplateRevisionId\":701}");
        assertEquals("42",provider.associationCandidates(match,null,100).getFirst().objectId());
        var mismatch = new TaskBusinessObjectProvider.AssociationContext(1L,9L,"PRE_04_REQUIREMENT_ANALYSIS","{\"dynamicFormTemplateRevisionId\":702}");
        assertTrue(provider.associationCandidates(mismatch,null,100).isEmpty());
        verifyNoInteractions(queries,facts,executions);
    }
    @Test void draftRevalidationRejectsStaleVersionAndForeignProject() {
        String version = provider.inspect(context,"42").factVersion();
        row.setVersion(3);
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidate(context,"42",version));
        row.setProjectId(10L);
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidate(context,"42",version));
    }
}
