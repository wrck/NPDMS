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
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private final SiteSurveyMapper mapper = mock(SiteSurveyMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final SiteSurveyTaskBusinessObjectProvider provider =
            new SiteSurveyTaskBusinessObjectProvider(mapper, scope, permissions);
    private final Context context = new Context(3L, 9L, 100L, 200L, "survey-test");
    private final SiteSurveyTaskObjectQuery objectQuery = new SiteSurveyTaskObjectQuery(3L, 100L, 42L);

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
        verifyNoInteractions(mapper, scope, permissions);
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
}
