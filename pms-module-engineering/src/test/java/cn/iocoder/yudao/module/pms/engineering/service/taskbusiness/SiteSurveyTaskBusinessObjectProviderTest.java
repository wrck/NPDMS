package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskCandidateQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskObjectQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.StageCompletionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyTaskBusinessObjectProviderTest {
    @Test void stageCapabilityIsMetadataOnly() {
        assertTrue(provider.supportsStageCompletionFacts());
        verifyNoInteractions(mapper, scope, permissions, executions);
    }
    private final SiteSurveyMapper mapper = mock(SiteSurveyMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    private final SiteSurveyTaskBusinessObjectProvider provider =
            new SiteSurveyTaskBusinessObjectProvider(mapper, scope, permissions, executions);
    private final Context context = new Context(3L, 9L, 100L, 200L, "survey-test");
    private final SiteSurveyTaskObjectQuery objectQuery = new SiteSurveyTaskObjectQuery(3L, 100L, 42L);
    private final ProjectStageExecutionContext stageExecution = new ProjectStageExecutionContext(
            100L, 1, 300L, 2, 301L, 1, 302L, 303L, 1, 2, true);
    private final StageCompletionContext stageContext = new StageCompletionContext(3L, stageExecution);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(3L);
        LoginUser login = new LoginUser();
        login.setId(9L);
        login.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:query")).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void completionFactCatalogIsDeploymentMetadataWithoutBusinessAccess() {
        clearContext();
        assertEquals(Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"), provider.completionFactCodes());
        verifyNoInteractions(mapper, scope, permissions);
    }

    @Test
    void confirmedAndArchivedAreDistinctOwnerFactsNeverPreparationReadyOrFakeArtifacts() {
        for (int status = 0; status <= 3; status++) {
            when(mapper.selectTaskObject(objectQuery)).thenReturn(row(status));
            var fact = provider.inspect(context, "42");
            assertEquals(status == 1 || status == 3, fact.completionFacts().get("SURVEY_CONFIRMED"));
            assertEquals(status == 3, fact.completionFacts().get("SURVEY_ARCHIVED"));
            assertEquals(Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"), fact.completionFacts().keySet());
            assertTrue(fact.artifacts().isEmpty());
            assertEquals(Set.of("QUERY"), fact.allowedActions());
            assertEquals("SOL_SITE_SURVEY:v1:7:" + status, fact.factVersion());
        }
        verify(mapper, times(4)).selectTaskObject(objectQuery);
        verifyNoMoreInteractions(mapper); // Neither reads nor facts invoke insert/update/old CRUD.
    }

    @Test
    void writesRequireManageAndFunctionalPermissionAndEntityState() {
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:update")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:create")).thenReturn(true);
        when(mapper.selectTaskObject(objectQuery)).thenReturn(row(0));
        assertEquals(Set.of("QUERY", "CREATE", "UPDATE", "CONFIRM", "REJECT", "LINK", "UNLINK"),
                provider.inspect(context, "42").allowedActions());
        when(mapper.selectTaskObject(objectQuery)).thenReturn(row(1));
        assertEquals(Set.of("QUERY", "CREATE", "ARCHIVE", "LINK", "UNLINK"),
                provider.inspect(context, "42").allowedActions());
        when(mapper.selectTaskObject(objectQuery)).thenReturn(row(3));
        assertEquals(Set.of("QUERY", "CREATE", "LINK", "UNLINK"), provider.inspect(context, "42").allowedActions());
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:update")).thenReturn(false);
        assertEquals(Set.of("QUERY", "CREATE"), provider.inspect(context, "42").allowedActions());
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(), Set.of()));
        assertEquals(Set.of("QUERY"), provider.inspect(context, "42").allowedActions());
    }

    @Test
    void forgedTenantOrActorIsRejectedBeforeAnyQuery() {
        Context foreignTenant = new Context(4L, 9L, 100L, 200L, "spoof");
        assertThrows(ServiceException.class, () -> provider.candidates(foreignTenant));
        assertThrows(ServiceException.class, () -> provider.inspect(foreignTenant, "42"));
        assertThrows(ServiceException.class, () -> provider.inspectContext(foreignTenant));
        assertThrows(ServiceException.class,
                () -> provider.lockAndRevalidate(foreignTenant, "42", "SOL_SITE_SURVEY:v1:7:1"));
        assertThrows(ServiceException.class,
                () -> provider.inspect(new Context(3L, 8L, 100L, 200L, "spoof"), "42"));
        verifyNoInteractions(mapper, scope, permissions);
    }

    @Test
    void noFunctionalQueryPermissionNeverReadsAnObject() {
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:query")).thenReturn(false);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        assertThrows(ServiceException.class, () -> provider.candidates(context));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "unknownVersion"));
        verifyNoInteractions(mapper);
    }

    @Test
    void emptyViewScopeDoesNotFallBackToFullProjectOrPlaceholderAccess() {
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(), Set.of(100L)));
        assertThrows(ServiceException.class, () -> provider.candidates(context));
        verifyNoInteractions(mapper, permissions);
    }

    @Test
    void foreignTenantProjectAndDeletedRowsFailClosedEvenIfMapperReturnsThem() {
        SiteSurveyDO foreignTenant = row(1);
        foreignTenant.setTenantId(4L);
        SiteSurveyDO foreignProject = row(1);
        foreignProject.setProjectId(101L);
        SiteSurveyDO deleted = row(1);
        deleted.setDeleted(true);
        for (SiteSurveyDO invalid : List.of(foreignTenant, foreignProject, deleted)) {
            when(mapper.selectTaskObject(objectQuery)).thenReturn(invalid);
            when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(invalid);
            when(mapper.selectTaskCandidates(any())).thenReturn(List.of(invalid));
            assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
            assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:7:1"));
            assertThrows(ServiceException.class, () -> provider.candidates(context));
        }
    }

    @Test
    void contextActionsAllowAuthorizedCreationWithoutReadingOrCreatingAnInstance() {
        assertEquals(Set.of("QUERY"), provider.inspectContext(context));
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        assertEquals(Set.of("QUERY"), provider.inspectContext(context));
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:create")).thenReturn(true);
        assertEquals(Set.of("QUERY", "CREATE"), provider.inspectContext(context));
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:update")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:delete")).thenReturn(true);
        assertEquals(Set.of("QUERY", "CREATE", "UPDATE", "CONFIRM", "REJECT", "ARCHIVE", "DELETE"),
                provider.inspectContext(context));
        when(permissions.hasAnyPermissions(9L, "pms:eng-site-survey:query")).thenReturn(false);
        assertThrows(ServiceException.class, () -> provider.inspectContext(context));
        verifyNoInteractions(mapper);
    }

    @Test
    void wrongObjectIdentityCannotBeReturnedEvenWithinSameTenantAndProject() {
        SiteSurveyDO other = row(1);
        other.setId(43L);
        when(mapper.selectTaskObject(objectQuery)).thenReturn(other);
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(other);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:7:1"));
    }

    @Test
    void candidatesUseBoundedTrustedQueryAndEmptyNeverCreatesOrCompletesAnything() {
        SiteSurveyTaskCandidateQuery query = new SiteSurveyTaskCandidateQuery(3L, 100L, 100);
        when(mapper.selectTaskCandidates(query)).thenReturn(List.of());
        assertTrue(provider.candidates(context).isEmpty());
        verify(mapper).selectTaskCandidates(query);
        verifyNoMoreInteractions(mapper);
        assertThrows(IllegalArgumentException.class, () -> new SiteSurveyTaskCandidateQuery(3L, 100L, 101));
        assertThrows(IllegalArgumentException.class, () -> new SiteSurveyTaskCandidateQuery(3L, 100L, 0));
    }

    @Test
    void missingObjectOrUnknownVersionAndStateDoNotBecomeSatisfiedFacts() {
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        assertThrows(ServiceException.class, () -> provider.inspect(context, "https://not-an-id"));
        SiteSurveyDO unknownVersion = row(1);
        unknownVersion.setVersion(null);
        when(mapper.selectTaskObject(objectQuery)).thenReturn(unknownVersion);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        when(mapper.selectTaskObject(objectQuery)).thenReturn(row(9));
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
    }

    @Test
    void lockRevalidatesBothVersionAndStatusWithoutModifyingArchivedHistory() {
        SiteSurveyDO archived = row(3);
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(archived);
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "unknownVersion"));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", null));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:6:3"));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:7:1"));
        assertTrue(provider.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:7:3")
                .completionFacts().get("SURVEY_ARCHIVED"));
        assertEquals(7, archived.getVersion());
        assertEquals(3, archived.getStatus());
        verify(mapper, times(5)).selectTaskObjectForUpdate(objectQuery);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void springProxyRejectsLockWithoutExistingTransactionBeforeMapperAccess() {
        AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
                fail("MANDATORY must never start a new transaction");
            }
            @Override protected void doCommit(DefaultTransactionStatus status) { fail("No transaction to commit"); }
            @Override protected void doRollback(DefaultTransactionStatus status) { fail("No transaction to roll back"); }
        };
        ProxyFactory factory = new ProxyFactory(provider);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        SiteSurveyTaskBusinessObjectProvider proxy = (SiteSurveyTaskBusinessObjectProvider) factory.getProxy();
        assertThrows(IllegalTransactionStateException.class,
                () -> proxy.lockAndRevalidate(context, "42", "SOL_SITE_SURVEY:v1:7:1"));
        assertThrows(IllegalTransactionStateException.class,
                () -> proxy.lockStageCompletionFact(stageContext, "42"));
        assertThrows(IllegalTransactionStateException.class,
                () -> proxy.lockCompletionFact(null, "42"));
        verifyNoInteractions(mapper, scope, permissions, executions);
    }

    private SiteSurveyDO row(int status) {
        SiteSurveyDO row = new SiteSurveyDO();
        row.setId(42L);
        row.setTenantId(3L);
        row.setProjectId(100L);
        row.setName("真实工勘");
        row.setVersion(7);
        row.setStatus(status);
        row.setDeleted(false);
        row.setConclusion("https://not-a-plt-artifact");
        return row;
    }

    @Test
    void unattendedFactsReadCurrentOwnerResultWithoutOriginTimeOrRoundComparison() {
        SecurityContextHolder.clearContext();
        var started = java.time.LocalDateTime.of(2026, 9, 14, 12, 0);
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L, 1, 200L, 1, 201L, 1, 202L, 203L, 1, 2, 204L, 1, true, started);
        var evaluation = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(3L, execution);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        var survey = row(1); survey.setConfirmedAt(started.minusDays(1)); survey.setUpdateTime(started.plusMinutes(1));
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(survey);
        var old = provider.lockCompletionFact(evaluation, "42");
        assertTrue(old.handlingCompleted()); assertTrue(old.completionFacts().get("SURVEY_CONFIRMED"));
        survey.setConfirmedAt(started.plusSeconds(1));
        var current = provider.lockCompletionFact(evaluation, "42");
        assertTrue(current.handlingCompleted()); assertTrue(current.completionFacts().get("SURVEY_CONFIRMED"));
        assertFalse(current.completionFacts().get("SURVEY_ARCHIVED"));
        survey.setStatus(3); survey.setArchivedAt(started.plusMinutes(1));
        assertTrue(provider.lockCompletionFact(evaluation, "42").completionFacts().get("SURVEY_ARCHIVED"));
        verifyNoInteractions(scope, permissions);
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
    }

    @Test
    void missingTimestampsDoNotBlockCurrentBusinessResultButStaleTaskContextStillFails() {
        var started = java.time.LocalDateTime.of(2026, 9, 14, 12, 0);
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L, 1, 200L, 1, 201L, 1, 202L, 203L, 1, 2, 204L, 1, true, started);
        var evaluation = new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(3L, execution);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(row(1));
        assertTrue(provider.lockCompletionFact(evaluation, "42").handlingCompleted());
        when(executions.lockAndRevalidate(execution)).thenThrow(new IllegalStateException("stale execution"));
        assertThrows(IllegalStateException.class, () -> provider.lockCompletionFact(evaluation, "42"));
        verify(mapper, times(1)).selectTaskObjectForUpdate(objectQuery);
    }

    @Test
    void firstExecutionCanExplicitlyShareAnExistingConfirmedResult() {
        SecurityContextHolder.clearContext();
        var started = java.time.LocalDateTime.of(2026,9,14,12,0);
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L,1,200L,1,201L,1,202L,203L,1,1,204L,1,true,started);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        var survey = row(1); survey.setConfirmedAt(started.minusHours(1));
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(survey);
        var result = provider.lockCompletionFact(new cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext(3L,execution),"42");
        assertTrue(result.handlingCompleted()); assertTrue(result.completionFacts().get("SURVEY_CONFIRMED"));
        assertEquals(7, survey.getVersion());
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void stageReadsOwnerResultInCurrentExecutionWithoutTaskOrTimestampEvidence(int status) {
        SecurityContextHolder.clearContext();
        when(executions.lockAndRevalidateStage(stageExecution)).thenReturn(stageExecution);
        var survey = row(status);
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(survey);

        var result = provider.lockStageCompletionFact(stageContext, "42");

        assertEquals(status == 1 || status == 3, result.handlingCompleted());
        assertEquals(status == 1 || status == 3, result.completionFacts().get("SURVEY_CONFIRMED"));
        assertEquals(status == 3, result.completionFacts().get("SURVEY_ARCHIVED"));
        assertEquals("SOL_SITE_SURVEY_RESULT:7:" + status + ":execution:303", result.factVersion());
        assertEquals("42", result.objectId());
        var order = inOrder(executions, mapper);
        order.verify(executions).lockAndRevalidateStage(stageExecution);
        order.verify(mapper).selectTaskObjectForUpdate(objectQuery);
        verifyNoMoreInteractions(executions, mapper);
        verifyNoInteractions(scope, permissions);
        assertEquals(status, survey.getStatus());
        assertEquals(7, survey.getVersion());
        assertNull(survey.getConfirmedAt());
        assertNull(survey.getArchivedAt());
    }

    @Test
    void staleStageFailsBeforeOwnerRead() {
        when(executions.lockAndRevalidateStage(stageExecution)).thenThrow(new IllegalStateException("stale stage"));
        assertThrows(IllegalStateException.class, () -> provider.lockStageCompletionFact(stageContext, "42"));
        verifyNoInteractions(mapper, scope, permissions);
    }

    @Test
    void stageRejectsMissingOrForeignTenantBeforeExecutionLookup() {
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(null, "42"));
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(new StageCompletionContext(3L, null), "42"));
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(new StageCompletionContext(4L, stageExecution), "42"));
        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(new StageCompletionContext(null, stageExecution), "42"));
        verifyNoInteractions(executions, mapper, scope, permissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "99999999999999999999999"})
    void stageRejectsInvalidObjectIdentityBeforeOwnerRead(String objectId) {
        when(executions.lockAndRevalidateStage(stageExecution)).thenReturn(stageExecution);
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(stageContext, objectId));
        verifyNoInteractions(mapper, scope, permissions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "object", "project", "tenant", "deleted", "version", "negativeVersion", "status", "unknownStatus"})
    void unavailableStageFactThrowsInsteadOfBecomingFalse(String invalid) {
        when(executions.lockAndRevalidateStage(stageExecution)).thenReturn(stageExecution);
        var survey = row(1);
        switch (invalid) {
            case "object" -> survey.setId(43L);
            case "project" -> survey.setProjectId(101L);
            case "tenant" -> survey.setTenantId(4L);
            case "deleted" -> survey.setDeleted(true);
            case "version" -> survey.setVersion(null);
            case "negativeVersion" -> survey.setVersion(-1);
            case "status" -> survey.setStatus(null);
            case "unknownStatus" -> survey.setStatus(4);
            default -> { }
        }
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn("missing".equals(invalid) ? null : survey);
        assertThrows(ServiceException.class, () -> provider.lockStageCompletionFact(stageContext, "42"));
        verify(mapper).selectTaskObjectForUpdate(objectQuery);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void sameSurveyCanSupplyIndependentStageContextsWithoutOverwritingEarlierEvidence() {
        when(executions.lockAndRevalidateStage(stageExecution)).thenReturn(stageExecution);
        var other = new ProjectStageExecutionContext(100L, 1, 400L, 2, 401L, 1, 302L, 403L, 1, 1, true);
        when(executions.lockAndRevalidateStage(other)).thenReturn(other);
        when(mapper.selectTaskObjectForUpdate(objectQuery)).thenReturn(row(1));
        var first = provider.lockStageCompletionFact(stageContext, "42");
        var second = provider.lockStageCompletionFact(new StageCompletionContext(3L, other), "42");
        assertEquals(first.completionFacts(), second.completionFacts());
        assertEquals("SOL_SITE_SURVEY_RESULT:7:1:execution:303", first.factVersion());
        assertEquals("SOL_SITE_SURVEY_RESULT:7:1:execution:403", second.factVersion());
        verify(mapper, times(2)).selectTaskObjectForUpdate(objectQuery);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void stageViewReusesOwnerPermissionsWithoutCreatingOrManuallyLinkingRecords() {
        var stage = new cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider.Context(
                3L,9L,100L,300L,"SITE_SURVEY","CREATE_ON_FIRST_ACTION",stageExecution);
        assertEquals(Set.of("QUERY"),provider.inspectStage(stage).allowedActions());
        when(permissions.hasAnyPermissions(9L,"pms:project-task:execute")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L,"pms:eng-site-survey:create")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L,"pms:eng-site-survey:update")).thenReturn(true);
        when(permissions.hasAnyPermissions(9L,"pms:eng-site-survey:delete")).thenReturn(true);
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L,9L,100L,ProjectScopeApi.ACTION_EDIT)))
                .thenReturn(new ProjectScopeResult(100L,1L,Set.of(100L),Set.of()));
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L,9L,100L,ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L,1L,Set.of(100L),Set.of()));
        assertEquals(Set.of("QUERY","CREATE","UPDATE","CONFIRM","REJECT","ARCHIVE","DELETE"),provider.inspectStage(stage).allowedActions());
        var readonly = new cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider.Context(
                3L,9L,100L,300L,"SITE_SURVEY","READ_ONLY_AGGREGATE",stageExecution);
        assertEquals(Set.of("QUERY"),provider.inspectStage(readonly).allowedActions());
        verifyNoInteractions(mapper,executions);
    }
}
