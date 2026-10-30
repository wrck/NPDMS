package cn.iocoder.yudao.module.pms.acceptance.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultFileMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionBusinessResultSource;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.SatisfactionTaskManagementService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SatisfactionTaskBusinessObjectProviderReadTest {
    private final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    private final SatisfactionQuestionnaireMapper questionnaires = mock(SatisfactionQuestionnaireMapper.class);
    private final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    private final SatisfactionResultFileMapper files = mock(SatisfactionResultFileMapper.class);
    private final SatisfactionBusinessResultSource source = mock(SatisfactionBusinessResultSource.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    // Keep the real assignee-only query in the reproduction; the embedded page has project visibility.
    private final SatisfactionTaskManagementService queries = new SatisfactionTaskManagementService(
            tasks, questionnaires, results, null, null, scopes, null, null);
    private final SatisfactionTaskBusinessObjectProvider provider = new SatisfactionTaskBusinessObjectProvider(
            tasks, results, files, queries, source, executions, scopes, permissions);
    private final Context context = new Context(7L, 11L, 9L, 6L, "satisfaction-owner-read");
    private final SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
    private final SatisfactionResultDO result = new SatisfactionResultDO();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        var login = new LoginUser(); login.setId(11L); login.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(11L, "pms:acceptance:satisfaction:query")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenAnswer(invocation -> {
            var query = invocation.getArgument(0, ProjectCurrentScopeQuery.class);
            assertEquals(7L, query.tenantId());
            assertEquals(11L, query.subjectUserId());
            assertEquals(9L, query.anchorProjectId());
            return new ProjectScopeResult(9L, 1L,
                    ProjectScopeApi.ACTION_VIEW.equals(query.actionCode()) ? Set.of(9L) : Set.of(), Set.of());
        });
        task.setId(10L); task.setTenantId(7L); task.setProjectId(9L); task.setVersion(2L);
        task.setAssignedToUserId(21L); task.setTaskStatus("PASSED"); task.setResultId(20L);
        task.setCollectionKey("existing-investigation"); task.setTaskRevisionNo(1);
        result.setId(20L); result.setTenantId(7L); result.setCollectionTaskId(10L);
        result.setPassed(true); result.setResultStatus("EFFECTIVE"); result.setArchiveStatus("ARCHIVED");
        when(tasks.selectById(10L)).thenReturn(task);
        when(results.selectById(20L)).thenReturn(result);
        when(files.selectListByResult(any())).thenReturn(List.of(file("SIGNATURE"), file("RESULT_DOCUMENT")));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectViewerReadsAnotherAssigneesLinkedResult() {
        var inspection = provider.inspectContextAndObjects(context, List.of("10"));
        assertEquals(Set.of("QUERY"), inspection.allowedActions());
        var fact = inspection.objects().getFirst();
        assertEquals("10", fact.objectId());
        assertEquals(Set.of("QUERY"), fact.allowedActions());
        assertTrue(fact.completionFacts().get(SatisfactionTaskBusinessObjectProvider.COMPLETE));
        assertEquals(21L, task.getAssignedToUserId());
        assertEquals("PASSED", task.getTaskStatus());
        assertEquals(2L, task.getVersion());
        verifyNoInteractions(questionnaires, source, executions);
    }

    @Test
    void pendingLinkedInvestigationRemainsVisibleWithoutInventingCompletion() {
        task.setResultId(null); task.setTaskStatus("PENDING_COLLECTION");
        var fact = provider.inspect(context, "10");
        assertFalse(fact.completionFacts().get(SatisfactionTaskBusinessObjectProvider.COMPLETE));
        assertEquals("PENDING_COLLECTION", task.getTaskStatus());
        verifyNoInteractions(results, files, questionnaires, source, executions);
    }

    @Test
    void missingQueryPermissionRejectsBeforeReadingObjects() {
        when(permissions.hasAnyPermissions(11L, "pms:acceptance:satisfaction:query")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        verifyNoInteractions(tasks, results, files, scopes);
    }

    @Test
    void emptyOrForeignProjectScopeRejectsBeforeReadingObjects() {
        for (Set<Long> visible : List.of(Set.<Long>of(), Set.of(99L))) {
            doReturn(new ProjectScopeResult(9L, 1L, visible, Set.of())).when(scopes).resolveCurrent(any());
            assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        }
        verifyNoInteractions(tasks, results, files);
    }

    @Test
    void mismatchedActorOrTenantRejectsBeforeReadingObjects() {
        assertThrows(IllegalStateException.class, () -> provider.inspect(new Context(7L, 22L, 9L, 6L, "other-actor"), "10"));
        assertThrows(IllegalStateException.class, () -> provider.inspect(new Context(8L, 11L, 9L, 6L, "other-tenant"), "10"));
        verifyNoInteractions(tasks, results, files, scopes, permissions);
    }

    @Test
    void missingForeignOrDeletedObjectDoesNotExposeResult() {
        when(tasks.selectById(10L)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        when(tasks.selectById(10L)).thenReturn(task);
        task.setProjectId(99L);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        task.setProjectId(9L); task.setTenantId(8L);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        task.setTenantId(7L); task.setDeleted(true);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        verifyNoInteractions(results, files, source);
    }

    @Test
    void resultFromAnotherTenantOrTaskIsRejected() {
        result.setTenantId(8L);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        result.setTenantId(7L); result.setCollectionTaskId(99L);
        assertThrows(IllegalStateException.class, () -> provider.inspect(context, "10"));
        verifyNoInteractions(files, source);
    }

    @Test
    void assigneeOnlyEndpointAndLockedCommandRetainTheirRestrictions() {
        assertEquals("SATISFACTION_TASK_NOT_FOUND", assertThrows(IllegalStateException.class,
                () -> queries.get(7L, 11L, 10L)).getMessage());
        assertEquals("SATISFACTION_TASK_NOT_FOUND", assertThrows(IllegalStateException.class,
                () -> provider.lockAndRevalidate(context, "10", "unused")).getMessage());
        verify(tasks, never()).selectByIdForUpdate(any(), any());
        verify(scopes, never()).lockAndRevalidate(any());
        verifyNoInteractions(questionnaires, source, executions);
    }

    @Test
    void linkedClosureEvidenceUsesProjectVisibilityWithoutGrantingAssigneeCommands() {
        var observed = provider.inspect(context, "10");
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        when(tasks.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(results.selectByIdForUpdate(7L, 20L)).thenReturn(result);
        when(source.lockAndInspect(any())).thenReturn(BusinessResultSource.Observation.available(
                new BusinessResultSource.Result(7L, 9L, SatisfactionBusinessResultSource.TYPE, "10", "20", "1", "1",
                        BusinessResultSource.Validity.CURRENT, java.time.LocalDateTime.now())));
        var locked = provider.lockLinkedFact(context, "10", observed.factVersion());
        assertEquals(observed.factVersion(), locked.factVersion());
        assertTrue(locked.completionFacts().get(SatisfactionTaskBusinessObjectProvider.COMPLETE));
        assertEquals(Set.of("QUERY"), locked.allowedActions());
        assertEquals(21L, task.getAssignedToUserId());
        assertThrows(IllegalStateException.class, () -> queries.get(7L, 11L, 10L));
        assertThrows(IllegalStateException.class, () -> provider.lockAndRevalidate(context, "10", observed.factVersion()));
    }

    @Test
    void linkedEvidenceRejectsRevokedScopeAndStaleVersions() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 2L, Set.of(), Set.of()));
        assertThrows(IllegalStateException.class, () -> provider.lockLinkedFact(context, "10", "stale"));
        verify(tasks, never()).selectByIdForUpdate(any(), any());
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
        task.setResultId(null);
        when(tasks.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        assertThrows(IllegalStateException.class, () -> provider.lockLinkedFact(context, "10", "stale"));
    }

    private SatisfactionResultFileDO file(String role) {
        var file = new SatisfactionResultFileDO();
        file.setTenantId(7L); file.setResultId(20L); file.setFileRole(role);
        return file;
    }
}
