package cn.iocoder.yudao.module.pms.platform.businessview.application;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview.BusinessViewRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.BusinessViewRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query.*;
import cn.iocoder.yudao.module.pms.platform.service.businessview.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewErrors.*;

/** PM-03: application failure, idempotency, authorization and row-lifecycle evidence (not MySQL). */
class BusinessViewApplicationTest {
    private final Map<Long, BusinessViewRevisionDO> rows = new LinkedHashMap<>();
    private final Map<PlatformCommandExecutionApi.IdempotencyScope, Cached> cache = new HashMap<>();
    private final List<PlatformCommandExecutionApi.SuccessFacts> audits = new ArrayList<>();
    private BusinessViewRevisionMapper mapper;
    private PermissionApi permission;
    private BusinessViewComponentProvider provider;
    private PlatformCommandExecutionApi commands;
    private BusinessViewAccess access;
    private TransactionTemplate transactions;
    private BusinessViewApplicationService service;
    private record Cached(String digest, BusinessViewRevision response) { }
    private static final Context CONTEXT = new Context(1L, 7L);

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        LoginUser login = new LoginUser(); login.setId(7L); login.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        mapper = mock(BusinessViewRevisionMapper.class);
        permission = mock(PermissionApi.class);
        when(permission.hasAnyPermissions(eq(7L), any(String.class))).thenReturn(true);
        access = new BusinessViewAccess(permission, new MockEnvironment());
        provider = mock(BusinessViewComponentProvider.class);
        when(provider.component()).thenReturn(component());
        when(provider.canConfigure(eq(CONTEXT), any())).thenReturn(true);
        when(provider.validateConfiguration(eq(CONTEXT), isNull(), any())).thenReturn(new Dependencies(false));
        commands = mock(PlatformCommandExecutionApi.class);
        transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call ->
                call.<TransactionCallback<Object>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
        when(commands.execute(any(), anyString(), eq(BusinessViewRevision.class), any(), any())).thenAnswer(call -> {
            var scope = call.<PlatformCommandExecutionApi.IdempotencyScope>getArgument(0);
            String digest = call.getArgument(1);
            Cached existing = cache.get(scope);
            if (existing != null) return new PlatformCommandExecutionApi.ExecutionResult<>(
                    existing.digest().equals(digest) ? PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                            : PlatformCommandExecutionApi.Decision.CONFLICT, existing.response());
            BusinessViewRevision result = call.<Supplier<BusinessViewRevision>>getArgument(3).get();
            audits.add(call.<Function<BusinessViewRevision, PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result));
            cache.put(scope, new Cached(digest, result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
        when(mapper.insert(any(BusinessViewRevisionDO.class))).thenAnswer(call -> {
            var row = call.<BusinessViewRevisionDO>getArgument(0); rows.put(row.getId(), row); return 1;
        });
        when(mapper.selectByRow(any())).thenAnswer(call -> {
            var query = call.<BusinessViewRowQuery>getArgument(0); var row = rows.get(query.revisionId());
            return row != null && row.getTenantId().equals(query.tenantId()) ? row : null;
        });
        when(mapper.selectIdentityForUpdate(any())).thenAnswer(call -> {
            var query = call.<BusinessViewIdentityQuery>getArgument(0);
            return rows.values().stream().filter(row -> row.getTenantId().equals(query.tenantId())
                    && row.getEntityType().equals(query.entityType()) && row.getViewKey().equals(query.viewKey()))
                    .sorted(Comparator.comparing(BusinessViewRevisionDO::getRevisionNo)).toList();
        });
        when(mapper.updateDraftIfMatch(any())).thenReturn(1);
        when(mapper.publishIfMatch(any())).thenReturn(1);
        when(mapper.disableIfMatch(any())).thenReturn(1);
        service = service(List.of(provider));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    private BusinessViewApplicationService service(List<BusinessViewComponentProvider> providers) {
        return new BusinessViewApplicationService(mapper, new BusinessViewComponentRegistry(providers), access, commands, transactions);
    }
    private Component component() {
        return new Component("REQUIREMENT_ANALYSIS", "SOL", ViewSource.PAGE, "PROJ_REQUIREMENT_ANALYSIS", "1",
                JsonUtils.parseTree("{\"type\":\"object\"}"), JsonUtils.parseTree("[\"VIEW\",\"EDIT\"]"),
                "SOL_QUERY", "SOL_COMMAND", "SOL_PERMISSION");
    }
    private BusinessViewApplicationService.Selection selection(String viewKey) {
        return new BusinessViewApplicationService.Selection("REQUIREMENT_ANALYSIS", viewKey, "PROJ_REQUIREMENT_ANALYSIS", "1", null);
    }
    private BusinessViewRevision create() { return service.create("create", selection("analysis")); }
    private void error(int code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(ServiceException.class, executable).getCode());
    }

    @Test void pageRegistrationLifecyclePreservesPublishedBodyAndDisabledHistory() {
        var draft = create();
        assertEquals("DRAFT", draft.status()); assertEquals(0, draft.version()); assertEquals(1L, draft.revisionNo());
        assertTrue(service.validate(draft.id()).valid());
        var published = service.publish(draft.id(), 0, "publish");
        assertEquals("PUBLISHED", published.status()); assertEquals(1, published.version());
        error(STATE_INVALID.getCode(), () -> service.update(draft.id(), 1, "edit", selection("analysis")));
        var copied = service.copy(draft.id(), 1, "copy");
        assertEquals(2L, copied.revisionNo()); assertEquals(0, copied.version()); assertNull(copied.publishedAt());
        var disabled = service.disable(draft.id(), 1, "disable");
        assertEquals("DISABLED", disabled.status()); assertEquals(published.contextSchema(), disabled.contextSchema());
        assertEquals(published.publishedAt(), disabled.publishedAt());
        var historical = service.getRevision(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        assertTrue(historical.allowedActions().isEmpty());
        error(UNAVAILABLE.getCode(), () -> service.getRevision(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE)));
        error(UNAVAILABLE.getCode(), () -> service.getRevision(new BusinessViewQueryApi.Query(copied.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE)));
        assertTrue(audits.stream().allMatch(fact -> fact.eventType() == null && fact.businessEvents().isEmpty()));
    }

    @Test void sameKeySameIntentReplaysWithoutAnotherWriteButDifferentIntentConflicts() {
        var first = create();
        assertEquals(first, create());
        verify(mapper, times(1)).insert(any(BusinessViewRevisionDO.class));
        error(KEY_CONFLICT.getCode(), () -> service.create("create", selection("different")));
        assertEquals(1, audits.size());
        var scope = cache.keySet().iterator().next();
        assertEquals(1L, scope.tenantId()); assertEquals(7L, scope.actorId());
        assertEquals("PLT:BUSINESS_VIEW:CREATE", scope.scopeCode());
    }

    @Test void replayStillRechecksCurrentFeatureAndOwnerPermissions() {
        create();
        when(permission.hasAnyPermissions(7L, "pms:business-view:manage")).thenReturn(false);
        error(403, this::create);
        when(permission.hasAnyPermissions(7L, "pms:business-view:manage")).thenReturn(true);
        when(provider.canConfigure(CONTEXT, ConfigurationAction.MANAGE)).thenReturn(false);
        error(403, this::create);
        verify(mapper, times(1)).insert(any(BusinessViewRevisionDO.class));
    }

    @Test void failedValidateDoesNotAuthorizePublishAfterDirectoryDrift() {
        var draft = create(); assertTrue(service.validate(draft.id()).valid());
        when(provider.validateConfiguration(CONTEXT, null, ValidationMode.LOCK_FOR_PUBLISH))
                .thenThrow(new IllegalArgumentException("component: no longer deployed"));
        error(UNAVAILABLE.getCode(), () -> service.publish(draft.id(), 0, "publish"));
        assertNull(rows.get(draft.id()).getPublishedAt());
        verify(mapper, never()).publishIfMatch(any());
        assertEquals(1, audits.size());
    }

    @Test void crossTenantAndAnonymousCallsCannotReadEvenKnownIdentifiers() {
        var draft = create(); TenantContextHolder.setTenantId(2L);
        error(NOT_FOUND.getCode(), () -> service.get(draft.id()));
        SecurityContextHolder.clearContext();
        error(403, () -> service.get(draft.id()));
    }

    @Test void bodyCannotChangeIdentityOwnerOrPageFormCombination() {
        var draft = create();
        error(INVALID.getCode(), () -> service.update(draft.id(), 0, "update", selection("other")));
        error(INVALID.getCode(), () -> service.create("bad", new BusinessViewApplicationService.Selection(
                "REQUIREMENT_ANALYSIS", "other", "PROJ_REQUIREMENT_ANALYSIS", "1", 30L)));
        verify(mapper, never()).updateDraftIfMatch(any());
    }

    @Test void stateAndVersionRejectionsNeverWriteAnotherRevision() {
        var draft = create();
        error(VERSION_CONFLICT.getCode(), () -> service.publish(draft.id(), 4, "stale"));
        error(STATE_INVALID.getCode(), () -> service.disable(draft.id(), 0, "disable"));
        error(DRAFT_EXISTS.getCode(), () -> service.copy(draft.id(), 0, "copy"));
        assertEquals(1, rows.size()); assertEquals(1, audits.size());
    }

    @Test void updateUsesPerRowCasAndChangedExpectedVersionChangesDigest() {
        var draft = create(); var updated = service.update(draft.id(), 0, "update", selection("analysis"));
        assertEquals(1, updated.version());
        assertEquals(updated, service.update(draft.id(), 0, "update", selection("analysis")));
        error(KEY_CONFLICT.getCode(), () -> service.update(draft.id(), 1, "update", selection("analysis")));
        verify(mapper, times(1)).updateDraftIfMatch(any());
    }

    @Test void casFailureAndConcurrentIdentityConflictDoNotRecordSuccess() {
        var draft = create(); when(mapper.publishIfMatch(any())).thenReturn(0);
        error(VERSION_CONFLICT.getCode(), () -> service.publish(draft.id(), 0, "publish"));
        assertEquals(1, audits.size());
        when(mapper.insert(any(BusinessViewRevisionDO.class))).thenThrow(new DuplicateKeyException("uk_bvr_identity"));
        error(IDENTITY_CONFLICT.getCode(), () -> service.create("other", selection("other")));
        assertEquals(1, audits.size());
    }

    @Test void emptyOrDuplicateDeployedCatalogNeverPermitsUnknownComponents() {
        assertTrue(service(List.of()).components().isEmpty());
        error(UNAVAILABLE.getCode(), () -> service(List.of()).create("new", selection("analysis")));
        assertTrue(service(List.of(provider, provider)).components().isEmpty());
        error(UNAVAILABLE.getCode(), () -> service(List.of(provider, provider)).create("new", selection("analysis")));
        verify(mapper, never()).insert(any(BusinessViewRevisionDO.class));
    }

    @Test void noOwnerScopeProducesNoRows() {
        when(provider.canConfigure(CONTEXT, ConfigurationAction.QUERY)).thenReturn(false);
        when(mapper.selectPage(any(BusinessViewPageQuery.class))).thenAnswer(call -> {
            assertTrue(call.<BusinessViewPageQuery>getArgument(0).getOwnerContexts().isEmpty());
            return new PageResult<BusinessViewRevisionDO>(List.of(), 0L);
        });
        assertTrue(service.page(1, 10, null, null).getList().isEmpty());
    }

    @Test void internalQueryDoesNotRequireGlobalConfigurationOrGrantObjectActions() {
        var draft = create(); service.publish(draft.id(), 0, "publish");
        when(permission.hasAnyPermissions(eq(7L), any(String.class))).thenReturn(false);
        var fact = service.getRevision(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE));
        assertTrue(fact.allowedActions().isEmpty());
        error(403, () -> service.get(draft.id()));
    }

    @Test void batchLocksEveryIdentityBeforeCallingAnyOwnerDependency() {
        var z = service.create("zcreate", selection("z"));
        var a = service.create("acreate", selection("a"));
        service.publish(z.id(), 0, "zpublish"); service.publish(a.id(), 0, "apublish");
        clearInvocations(mapper, provider);
        var result = service.lockAndRevalidateAll(List.of(
                new BusinessViewQueryApi.Query(z.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 1),
                new BusinessViewQueryApi.Query(a.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 1)));
        assertEquals(List.of(z.id(), a.id()), result.stream().map(BusinessViewRevision::id).toList());
        var order = inOrder(mapper, provider);
        order.verify(mapper).selectByRow(new BusinessViewRowQuery(1L, z.id()));
        order.verify(mapper).selectByRow(new BusinessViewRowQuery(1L, a.id()));
        order.verify(mapper).selectIdentityForUpdate(new BusinessViewIdentityQuery(1L, "REQUIREMENT_ANALYSIS", "a"));
        order.verify(mapper).selectIdentityForUpdate(new BusinessViewIdentityQuery(1L, "REQUIREMENT_ANALYSIS", "z"));
        order.verify(provider, times(2)).validateConfiguration(CONTEXT, null, ValidationMode.LOCK_FOR_PUBLISH);
    }

    @Test void batchRejectsAnyStaleVersionBeforeLockingDependencies() {
        var draft = create(); service.publish(draft.id(), 0, "publish"); clearInvocations(provider);
        error(VERSION_CONFLICT.getCode(), () -> service.lockAndRevalidateAll(List.of(
                new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 0))));
        verify(provider, never()).validateConfiguration(any(), any(), any());
    }

    @Test void templateLockRevalidationUsesExpectedRowVersionAndLiveDependencies() {
        var draft = create(); service.publish(draft.id(), 0, "publish");
        clearInvocations(mapper, provider);
        var fact = service.lockAndRevalidate(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 1));
        assertTrue(fact.allowedActions().isEmpty());
        var order = inOrder(mapper, provider);
        order.verify(mapper).selectByRow(any());
        order.verify(mapper).selectIdentityForUpdate(any());
        order.verify(provider).validateConfiguration(CONTEXT, null, ValidationMode.LOCK_FOR_PUBLISH);
        error(VERSION_CONFLICT.getCode(), () -> service.lockAndRevalidate(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.NEW_REFERENCE, 0)));
        error(INVALID.getCode(), () -> service.lockAndRevalidate(new BusinessViewQueryApi.Query(draft.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE, 1)));
    }
}
