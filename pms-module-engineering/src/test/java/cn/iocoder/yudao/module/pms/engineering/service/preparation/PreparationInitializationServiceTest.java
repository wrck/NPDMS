package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationResult;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalog;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparationInitializationServiceTest {

    @Mock private PreparationMapper preparationMapper;
    @Mock private PreparationItemMapper itemMapper;
    @Mock private DynamicFormInstanceMapper formMapper;
    @Mock private FixedSurveyFormCatalogProvider catalogProvider;
    @Mock private ProjectWorkBindingFactApi workBindingFactApi;
    @Mock private ProjectParticipantFactApi participantFactApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private PermissionApi permissionApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private OperationAuditApi operationAuditApi;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PreparationInitializationService service;

    private final AtomicReference<PreparationDO> persisted = new AtomicReference<>();
    private final Map<String, String> completedDigests = new HashMap<>();
    private final Map<String, PreparationInitializationResult> completedResponses = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(fact());
        when(preparationMapper.selectBusinessVersionForUpdate(any()))
                .thenAnswer(invocation -> persisted.get());
        when(catalogProvider.load()).thenReturn(catalog());
        when(preparationMapper.insert(any())).thenAnswer(invocation -> {
            PreparationDO row = invocation.getArgument(0);
            row.setId(1000L);
            persisted.set(row);
            return 1;
        });
        when(itemMapper.insert(any())).thenAnswer(invocation -> {
            PreparationItemDO row = invocation.getArgument(0);
            row.setId(2000L);
            return 1;
        });
        when(formMapper.insert(any())).thenAnswer(invocation -> {
            DynamicFormInstanceDO row = invocation.getArgument(0);
            row.setId(3000L);
            return 1;
        });
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            PlatformCommandExecutionApi.IdempotencyScope scope = invocation.getArgument(0);
            String digest = invocation.getArgument(1);
            String scopeKey = scope.actorId() + ":" + scope.key();
            if (completedDigests.containsKey(scopeKey)) {
                if (!completedDigests.get(scopeKey).equals(digest)) {
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.CONFLICT, null);
                }
                return new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                        completedResponses.get(scopeKey));
            }
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> factsFactory = invocation.getArgument(4);
            PreparationInitializationResult response = (PreparationInitializationResult) operation.get();
            factsFactory.apply(response);
            completedDigests.put(scopeKey, digest);
            completedResponses.put(scopeKey, response);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void initializesFrozenFactsThenCrossActorRecoveryReturnsSameBusinessFact() {
        var created = service.initialize(command(7L, PreparationInitializationApi.TRIGGER_PROJECT_CREATION));

        assertEquals(1000L, created.preparationId());
        assertEquals("DRAFT", persisted.get().getStatusCode());
        assertEquals("NOT_READY", persisted.get().getReadinessStatusCode());
        assertFalse(persisted.get().getSnapshotCurrent());
        ArgumentCaptor<PreparationItemDO> itemCaptor = ArgumentCaptor.forClass(PreparationItemDO.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertEquals("POWER", itemCaptor.getValue().getItemCode());
        ArgumentCaptor<DynamicFormInstanceDO> formCaptor =
                ArgumentCaptor.forClass(DynamicFormInstanceDO.class);
        verify(formMapper).insert(formCaptor.capture());
        assertEquals("DRAFT", formCaptor.getValue().getStatusCode());
        assertEquals("{}", formCaptor.getValue().getValueSnapshot());

        when(permissionApi.hasAnyPermissions(8L,
                PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 3L, Set.of(100L), Set.of()));

        var recovered = service.initialize(command(8L,
                PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY));

        assertEquals(created, recovered);
        verify(participantFactApi).lockAndRevalidate(any());
        verify(commandExecutionApi).execute(any(), any(), any(), any(), any());
        verify(operationAuditApi).record(eq(1L), eq(8L), eq("PRE02-OP-8"),
                eq("PREPARATION_INITIALIZATION_RECOVERY"), eq("Preparation"), eq("1000"),
                eq("NO_CHANGE"), any());
    }

    @Test
    void sameActorProjectCreationRetryReplaysPlatformResult() {
        PreparationInitializationCommand command = command(
                7L, PreparationInitializationApi.TRIGGER_PROJECT_CREATION);

        var first = service.initialize(command);
        var replay = service.initialize(command);

        assertEquals(first, replay);
        verify(preparationMapper).insert(any());
        verify(commandExecutionApi, times(2)).execute(any(), any(), any(), any(), any());
        verifyNoInteractions(operationAuditApi);
    }

    @Test
    void sameActorAuthorizedRecoveryRetryReplaysPlatformResult() {
        when(permissionApi.hasAnyPermissions(8L,
                PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 3L, Set.of(100L), Set.of()));
        PreparationInitializationCommand command = command(
                8L, PreparationInitializationApi.TRIGGER_AUTHORIZED_RECOVERY);

        var first = service.initialize(command);
        var replay = service.initialize(command);

        assertEquals(first, replay);
        verify(preparationMapper).insert(any());
        verify(commandExecutionApi, times(2)).execute(any(), any(), any(), any(), any());
        verify(participantFactApi, times(2)).lockAndRevalidate(any());
        verifyNoInteractions(operationAuditApi);
    }

    private PreparationInitializationCommand command(Long actorId, String trigger) {
        return new PreparationInitializationCommand(100L, 200L, 300L,
                2, 4, 6, trigger, "PRE02-KEY-" + actorId,
                "PRE02-OP-" + actorId, actorId);
    }

    private ProjectWorkBindingFact fact() {
        return new ProjectWorkBindingFact(100L, 2, 200L, 4, 300L, 6,
                400L, 5, "BUSINESS_OBJECT", "SOL", "SITE_SURVEY_PREPARATION",
                "PRE_02_SITE_SURVEY", "PRE_02_SITE_SURVEY", 1, 1,
                "[{\"itemCode\":\"POWER\",\"itemName\":\"供电\",\"enabled\":true,"
                        + "\"formCode\":\"POWER\",\"formVersion\":1,\"evidenceRequired\":false,"
                        + "\"sourceRequirementCode\":\"NONE\",\"waiverAllowed\":true,"
                        + "\"approvalRoleCode\":\"SERVICE_MANAGER_L1\",\"sortOrder\":1}]");
    }

    private FixedSurveyFormCatalog catalog() {
        return new FixedSurveyFormCatalog(1, "PRE_02_SITE_SURVEY", 1,
                List.of(new FixedSurveyFormCatalog.FieldDefinition(
                        "siteCondition", "TEXT", true, 200, List.of(), 1)),
                List.of(new FixedSurveyFormCatalog.FormDefinition("POWER", 1)));
    }
}
