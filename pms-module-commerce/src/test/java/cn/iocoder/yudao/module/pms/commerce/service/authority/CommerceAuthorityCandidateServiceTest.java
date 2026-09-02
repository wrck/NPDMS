package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.AuthorityCandidateDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateOwnerFact;
import cn.iocoder.yudao.module.pms.commerce.service.authorization.CompanyScopeGuard;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceAuthorityCandidateServiceTest {

    @Mock private AuthorityCandidateMapper candidateMapper;
    @Mock private OrganizationScopeApi organizationScopeApi;

    private RecordingCommandApi commandApi;
    private CommerceAuthorityCandidateService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        commandApi = new RecordingCommandApi();
        service = new CommerceAuthorityCandidateService(commandApi, candidateMapper,
                new CompanyScopeGuard(organizationScopeApi),
                Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsPendingCandidateInsideActiveCompanyScope() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        when(candidateMapper.insert((AuthorityCandidateDO) any())).thenAnswer(invocation -> {
            invocation.<AuthorityCandidateDO>getArgument(0).setId(101L);
            return 1;
        });

        var result = service.create(create("K-1", "V1", "ACME", "IDEM-1", "CORR-1"));

        assertEquals(101L, result.candidateId());
        assertEquals("PENDING_RECONCILIATION", result.candidateStatus());
        assertEquals("CORR-1", commandApi.facts.correlationId());
        assertEquals(64, commandApi.digest.length());
        ArgumentCaptor<AuthorityCandidateDO> rowCaptor = ArgumentCaptor.forClass(AuthorityCandidateDO.class);
        verify(candidateMapper).insert(rowCaptor.capture());
        AuthorityCandidateDO row = rowCaptor.getValue();
        assertEquals(1L, row.getTenantId());
        assertEquals("PLATFORM_MANUAL", row.getCandidateSourceSystem());
        assertEquals(11L, row.getSubmittedBy());
        assertNull(row.getDecidedAt());
    }

    @Test
    void replaysSameCandidateAndRejectsDifferentPayload() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        AuthorityCandidateDO existing = candidate(101L, "K-1", "V1", "ACME", "PENDING_RECONCILIATION", 0);
        when(candidateMapper.selectByIdentityForUpdate(any())).thenReturn(existing);

        var replay = service.create(create("K-1", "V1", "ACME", "IDEM-1", "CORR-1"));
        assertEquals(101L, replay.candidateId());
        verify(candidateMapper, never()).insert(any(AuthorityCandidateDO.class));

        var changedPayload = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                1L, 11L, "CONTRACT", "K-1", "V1",
                "{\"companyCode\":\"ACME\",\"contractNo\":\"C-2\"}",
                "{\"referenceKey\":\"REF-1\"}", "IDEM-2", "CORR-2");
        var error = assertThrows(CommerceAuthorityCandidateService.CandidateException.class,
                () -> service.create(changedPayload));
        assertEquals(CommerceAuthorityCandidateService.Code.SOURCE_VERSION_PAYLOAD_CONFLICT, error.getCode());
    }

    @Test
    void replaysSameCandidateWhenJsonObjectKeysAreReordered() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        when(candidateMapper.insert((AuthorityCandidateDO) any())).thenAnswer(invocation -> {
            invocation.<AuthorityCandidateDO>getArgument(0).setId(101L);
            return 1;
        });
        var original = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                1L, 11L, "CONTRACT", "K-1", "V1",
                "{\"companyCode\":\"ACME\",\"contractNo\":\"C-1\"}",
                "{\"referenceKey\":\"REF-1\",\"nested\":{\"a\":1,\"b\":2}}",
                "IDEM-1", "CORR-1");
        var reordered = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                1L, 11L, "CONTRACT", "K-1", "V1",
                "{\"contractNo\":\"C-1\",\"companyCode\":\"ACME\"}",
                "{\"nested\":{\"b\":2,\"a\":1},\"referenceKey\":\"REF-1\"}",
                "IDEM-1", "CORR-1");

        var first = service.create(original);
        String firstDigest = commandApi.digest;
        ArgumentCaptor<AuthorityCandidateDO> rowCaptor = ArgumentCaptor.forClass(AuthorityCandidateDO.class);
        verify(candidateMapper).insert(rowCaptor.capture());
        when(candidateMapper.selectByIdentityForUpdate(any())).thenReturn(rowCaptor.getValue());
        var replay = service.create(reordered);

        assertEquals(101L, first.candidateId());
        assertEquals(101L, replay.candidateId());
        assertEquals(firstDigest, commandApi.digest);
        verify(candidateMapper, times(1)).insert(any(AuthorityCandidateDO.class));
    }

    @Test
    void rejectsNonNormalizedCompanyCodeBeforePlatformClaim() {
        var command = new CommerceAuthorityCandidateService.CreateCandidateCommand(
                1L, 11L, "CONTRACT", "K-1", "V1",
                "{\"companyCode\":\" ACME \",\"contractNo\":\"C-1\"}",
                "{\"referenceKey\":\"REF-1\"}", "IDEM-1", "CORR-1");

        var error = assertThrows(CommerceAuthorityCandidateService.CandidateException.class,
                () -> service.create(command));

        assertEquals(CommerceAuthorityCandidateService.Code.INVALID_REQUEST, error.getCode());
        assertEquals(0, commandApi.calls);
        verifyNoInteractions(candidateMapper, organizationScopeApi);
    }

    @Test
    void rejectsOutOfScopeBeforePlatformClaimOrDatabaseAccess() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));

        assertThrows(CompanyScopeGuard.CompanyScopeDeniedException.class,
                () -> service.create(create("K-1", "V1", "OTHER", "IDEM-1", "CORR-1")));

        assertEquals(0, commandApi.calls);
        verifyNoInteractions(candidateMapper);
    }

    @Test
    void listsOnlyWithinCurrentCompanyScopeAndEmptyScopeReadsNothing() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME"), scope("BETA")));
        when(candidateMapper.selectVisiblePage(any())).thenReturn(List.of(
                candidate(101L, "K-1", "V1", "ACME", "PENDING_RECONCILIATION", 0)));

        assertEquals(1, service.listVisible(new CommerceAuthorityCandidateService.ListCandidatesQuery(
                1L, 11L, null, null, 1, 20)).size());
        verify(candidateMapper).selectVisiblePage(argThat(query -> query.companyCodes().size() == 2
                && query.companyCodes().contains("ACME") && query.offset() == 0 && query.limit() == 20));

        reset(candidateMapper, organizationScopeApi);
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of());
        assertTrue(service.listVisible(new CommerceAuthorityCandidateService.ListCandidatesQuery(
                1L, 11L, null, null, 1, 20)).isEmpty());
        verifyNoInteractions(candidateMapper);
    }

    @Test
    void reconcilesOnlyConfirmedOwnerFromSameCompany() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        AuthorityCandidateDO current = candidate(101L, "K-1", "V1", "ACME", "PENDING_RECONCILIATION", 2);
        when(candidateMapper.selectCandidateById(any())).thenReturn(current);
        when(candidateMapper.selectByIdForUpdate(any())).thenReturn(current);
        when(candidateMapper.selectConfirmedOwnerForUpdate(any())).thenReturn(
                new AuthorityCandidateOwnerFact(201L, "CONTRACT", "ACME", "ERP-V3", "CONFIRMED"));
        when(candidateMapper.decideByVersion(any())).thenReturn(1);

        var result = service.reconcile(decide(101L, 2, 201L, "IDEM-M", "CORR-M"));

        assertEquals("MATCHED", result.candidateStatus());
        assertEquals("ERP-V3", result.matchedOwnerSourceVersion());
        verify(candidateMapper).decideByVersion(argThat(update -> "MATCHED".equals(update.candidateStatus())
                && "CONTRACT".equals(update.matchedOwnerType()) && update.matchedOwnerId() == 201L));
    }

    @Test
    void rejectsWrongOwnerCompanyWithoutDecisionWrite() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        AuthorityCandidateDO current = candidate(101L, "K-1", "V1", "ACME", "PENDING_RECONCILIATION", 2);
        when(candidateMapper.selectCandidateById(any())).thenReturn(current);
        when(candidateMapper.selectByIdForUpdate(any())).thenReturn(current);
        when(candidateMapper.selectConfirmedOwnerForUpdate(any())).thenReturn(
                new AuthorityCandidateOwnerFact(201L, "CONTRACT", "OTHER", "ERP-V3", "CONFIRMED"));

        var error = assertThrows(CommerceAuthorityCandidateService.CandidateException.class,
                () -> service.reconcile(decide(101L, 2, 201L, "IDEM-M", "CORR-M")));

        assertEquals(CommerceAuthorityCandidateService.Code.COMPANY_SCOPE_MISMATCH, error.getCode());
        verify(candidateMapper, never()).decideByVersion(any());
        assertNull(commandApi.facts);
    }

    @Test
    void rejectsCandidateWithoutOwnerReferenceAndKeepsHistory() {
        when(organizationScopeApi.getActiveScopes(11L)).thenReturn(List.of(scope("ACME")));
        AuthorityCandidateDO current = candidate(101L, "K-1", "V1", "ACME", "PENDING_RECONCILIATION", 2);
        when(candidateMapper.selectCandidateById(any())).thenReturn(current);
        when(candidateMapper.selectByIdForUpdate(any())).thenReturn(current);
        when(candidateMapper.decideByVersion(any())).thenReturn(1);

        var result = service.reject(decide(101L, 2, null, "IDEM-R", "CORR-R"));

        assertEquals("REJECTED", result.candidateStatus());
        verify(candidateMapper).decideByVersion(argThat(update -> update.matchedOwnerId() == null
                && update.matchedOwnerType() == null && update.matchedOwnerSourceVersion() == null));
    }

    private CommerceAuthorityCandidateService.CreateCandidateCommand create(
            String key, String version, String company, String idempotencyKey, String correlationId) {
        return new CommerceAuthorityCandidateService.CreateCandidateCommand(1L, 11L, "CONTRACT", key, version,
                "{\"companyCode\":\"" + company + "\",\"contractNo\":\"C-1\"}",
                "{\"referenceKey\":\"REF-1\"}", idempotencyKey, correlationId);
    }

    private CommerceAuthorityCandidateService.DecideCandidateCommand decide(
            Long candidateId, int version, Long ownerId, String idempotencyKey, String correlationId) {
        return new CommerceAuthorityCandidateService.DecideCandidateCommand(
                1L, 11L, candidateId, version, ownerId, "人工核对结论", idempotencyKey, correlationId);
    }

    private AuthorityCandidateDO candidate(Long id, String key, String version, String company,
                                           String status, int rowVersion) {
        AuthorityCandidateDO row = new AuthorityCandidateDO();
        row.setId(id); row.setTenantId(1L); row.setObjectType("CONTRACT");
        row.setCandidateSourceSystem("PLATFORM_MANUAL"); row.setCandidateSourceKey(key);
        row.setCandidateVersion(version);
        row.setCandidatePayload("{\"companyCode\":\"" + company + "\",\"contractNo\":\"C-1\"}");
        row.setEvidenceReference("{\"referenceKey\":\"REF-1\"}");
        row.setCandidateStatus(status); row.setVersion(rowVersion);
        return row;
    }

    private UserCompanyDepartmentScopeRespDTO scope(String companyCode) {
        UserCompanyDepartmentScopeRespDTO result = new UserCompanyDepartmentScopeRespDTO();
        result.setCompanyCode(companyCode);
        return result;
    }

    private static final class RecordingCommandApi implements PlatformCommandExecutionApi {
        private int calls;
        private String digest;
        private SuccessFacts facts;

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            calls++;
            digest = requestDigest;
            T response = operation.get();
            facts = successFactsFactory.apply(response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
