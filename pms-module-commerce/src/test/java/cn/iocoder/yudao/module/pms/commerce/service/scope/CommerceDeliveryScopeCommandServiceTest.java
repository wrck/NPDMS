package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.CommerceDeliveryScopeCommandQuery.*;
import cn.iocoder.yudao.module.pms.commerce.domain.scope.DeliveryScopeStateMachine;
import cn.iocoder.yudao.module.pms.commerce.domain.scope.DeliveryScopeValidationRules;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.OVER_ALLOCATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommerceDeliveryScopeCommandServiceTest {

    private static final Long TENANT = 1L;
    private static final Long PROJECT = 10L;
    private static final Long ACTOR = 20L;
    private static final Long ORDER_LINE = 30L;

    private final DirectPlatform platform = new DirectPlatform();
    private final ProjectScopeQualificationAdapter projectQualification = mock(ProjectScopeQualificationAdapter.class);
    private final DeviceAndLocationFactAdapter deviceAndLocation = mock(DeviceAndLocationFactAdapter.class);
    private final CommerceDeliveryScopeCommandMapper commandMapper = mock(CommerceDeliveryScopeCommandMapper.class);
    private final DeliveryScopeMapper scopeMapper = mock(DeliveryScopeMapper.class);
    private final DeliveryScopeDetailMapper detailMapper = mock(DeliveryScopeDetailMapper.class);
    private final CommerceOutboxEventMapper outboxMapper = mock(CommerceOutboxEventMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    private CommerceDeliveryScopeCommandService service;
    private ProjectScopeQualificationAdapter.Snapshot project;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        service = new CommerceDeliveryScopeCommandService(platform, projectQualification, deviceAndLocation,
                commandMapper, scopeMapper, detailMapper, outboxMapper, new DeliveryScopeValidationRules(),
                new DeliveryScopeStateMachine(), clock);
        project = project("ACTIVE", "S4");
        when(projectQualification.inspect(TENANT, PROJECT, ACTOR)).thenReturn(project);
        when(projectQualification.lockAndRevalidate(project)).thenReturn(project);
        when(deviceAndLocation.inspect(any(), any(), any())).thenReturn(new DeviceAndLocationFactAdapter.Snapshot(List.of()));
        when(commandMapper.insertProjectVersionIfAbsent(any())).thenReturn(1);
        when(commandMapper.selectProjectVersionForUpdate(any())).thenReturn(projectVersion());
        when(commandMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(orderLine()));
        when(commandMapper.selectCurrentScopesForUpdate(any())).thenReturn(List.of());
        when(commandMapper.selectScopeDetailsForUpdate(any())).thenReturn(List.of());
        when(commandMapper.selectMaxAllocationVersions(any())).thenReturn(List.of());
        when(commandMapper.endScope(any())).thenReturn(1);
        when(commandMapper.endDetails(any())).thenReturn(1);
        when(commandMapper.advanceProjectVersion(any())).thenReturn(1);
        when(scopeMapper.insert(any(DeliveryScopeDO.class))).thenReturn(1);
        when(detailMapper.insert(any(DeliveryScopeDetailDO.class))).thenReturn(1);
        when(outboxMapper.insert(any(CommerceOutboxEventDO.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void applyCreatesQualifiedScopeDetailWatermarkAndAssignedOutbox() {
        CommandResult result = service.apply(new ApplyCommand(TENANT, PROJECT, ACTOR, 0L,
                List.of(line("3")), "INITIAL_ASSIGNMENT", "apply-key", "corr-apply"));

        assertEquals(1L, result.scopeVersion());
        assertEquals("APPLY", result.action());
        verify(scopeMapper).insert(argThat((DeliveryScopeDO row) -> "ACTIVE".equals(row.getScopeStatus())
                && new BigDecimal("3").compareTo(row.getAllocatedQty()) == 0));
        verify(detailMapper).insert(argThat((DeliveryScopeDetailDO row) -> "UNRESOLVED".equals(row.getLocationResolutionStatus())
                && "EA".equals(row.getUnitCode()) && "P-1".equals(row.getProductCode())));
        verify(outboxMapper).insert(argThat((CommerceOutboxEventDO event) -> "DeliveryScopeAssigned".equals(event.getEventType())
                && event.getScopeVersion() == 1L
                && event.getPayload().matches(".*\\\"dimensionDigest\\\":\\\"[0-9a-f]{64}\\\".*")));
        verify(commandMapper).advanceProjectVersion(argThat(query -> query.expectedScopeVersion() == 0L
                && query.newScopeVersion() == 1L && "ASSIGNED".equals(query.changeType())));
        assertEquals("corr-apply", platform.facts.getFirst().correlationId());
    }

    @Test
    void applyRejectsConcurrentProjectOverallocationBeforeAnyWrite() {
        DeliveryScopeDO otherProject = scope(99L, "ACTIVE", "8");
        when(commandMapper.selectCurrentScopesForUpdate(any())).thenReturn(List.of(otherProject));

        CommerceDeliveryScopeCommandException failure = assertThrows(CommerceDeliveryScopeCommandException.class,
                () -> service.apply(new ApplyCommand(TENANT, PROJECT, ACTOR, 0L,
                        List.of(line("3")), "INITIAL_ASSIGNMENT", "over-key", "corr-over")));

        assertEquals(OVER_ALLOCATION, failure.getCode());
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(commandMapper, never()).advanceProjectVersion(any());
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void releaseInS5AppendsConflictWithoutReleasedOutbox() {
        DeliveryScopeDO active = scope(PROJECT, "ACTIVE", "3");
        DeliveryScopeDetailDO detail = storedDetail(active.getId(), "ACTIVE", "3");
        project = project("ACTIVE", "S5");
        when(projectQualification.inspect(TENANT, PROJECT, ACTOR)).thenReturn(project);
        when(projectQualification.lockAndRevalidate(project)).thenReturn(project);
        when(commandMapper.selectCurrentScopesForUpdate(any())).thenReturn(List.of(active));
        when(commandMapper.selectScopeDetailsForUpdate(any())).thenReturn(List.of(detail));
        when(commandMapper.selectMaxAllocationVersions(any()))
                .thenReturn(List.of(new AllocationVersionFact(ORDER_LINE, 1L)));

        CommandResult result = service.release(new ReleaseCommand(TENANT, PROJECT, ACTOR, 0L,
                List.of(ORDER_LINE), "PROJECT_PROTECTED", "manager-request", "release-key", "corr-release"));

        assertTrue(result.protectedAsConflict());
        verify(scopeMapper).insert(argThat((DeliveryScopeDO row) -> "CONFLICT".equals(row.getScopeStatus())
                && row.getEffectiveTo() == null && row.getAllocationVersion() == 2L));
        verify(detailMapper).insert(argThat((DeliveryScopeDetailDO row) -> "ACTIVE".equals(row.getDetailStatus())));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void applyDecreaseInS5AppendsConflictWithoutAssignedOrReleasedOutbox() {
        DeliveryScopeDO active = scope(PROJECT, "ACTIVE", "5");
        DeliveryScopeDetailDO detail = storedDetail(active.getId(), "ACTIVE", "5");
        project = project("ACTIVE", "S5");
        when(projectQualification.inspect(TENANT, PROJECT, ACTOR)).thenReturn(project);
        when(projectQualification.lockAndRevalidate(project)).thenReturn(project);
        when(commandMapper.selectCurrentScopesForUpdate(any())).thenReturn(List.of(active));
        when(commandMapper.selectScopeDetailsForUpdate(any())).thenReturn(List.of(detail));
        when(commandMapper.selectMaxAllocationVersions(any()))
                .thenReturn(List.of(new AllocationVersionFact(ORDER_LINE, 1L)));

        CommandResult result = service.apply(new ApplyCommand(TENANT, PROJECT, ACTOR, 0L,
                List.of(line("3")), "PROTECTED_ADJUST", "adjust-key", "corr-adjust"));

        assertTrue(result.protectedAsConflict());
        verify(scopeMapper).insert(argThat((DeliveryScopeDO row) -> "CONFLICT".equals(row.getScopeStatus())
                && new BigDecimal("5").compareTo(row.getAllocatedQty()) == 0));
        verify(detailMapper).insert(argThat((DeliveryScopeDetailDO row) -> "ACTIVE".equals(row.getDetailStatus())
                && new BigDecimal("5").compareTo(row.getAllocatedQty()) == 0));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void resolveConflictToActiveAppendsHistoryAndAssignedOutbox() {
        DeliveryScopeDO conflict = scope(PROJECT, "CONFLICT", "3");
        when(commandMapper.selectCurrentScopesForUpdate(any())).thenReturn(List.of(conflict));
        when(commandMapper.selectScopeDetailsForUpdate(any())).thenReturn(List.of(storedDetail(conflict.getId(), "ACTIVE", "3")));
        when(commandMapper.selectMaxAllocationVersions(any()))
                .thenReturn(List.of(new AllocationVersionFact(ORDER_LINE, 2L)));

        CommandResult result = service.resolveConflict(new ResolveConflictCommand(TENANT, PROJECT, ACTOR, 0L,
                Resolution.ACTIVE, List.of(line("4")), null, "ERP-V2-CONFIRMED",
                "resolve-key", "corr-resolve"));

        assertFalse(result.protectedAsConflict());
        verify(scopeMapper).insert(argThat((DeliveryScopeDO row) -> "ACTIVE".equals(row.getScopeStatus())
                && row.getAllocationVersion() == 3L));
        verify(outboxMapper).insert(argThat((CommerceOutboxEventDO event) -> "DeliveryScopeAssigned".equals(event.getEventType())));
    }

    private ScopeLine line(String quantity) {
        BigDecimal value = new BigDecimal(quantity);
        Location location = new Location(LocationResolution.UNRESOLVED, null, null, null, null, "机房A");
        ScopeDetail detail = new ScopeDetail("OFFICE-A", value, "EA", "P-1", "M-1", null, location);
        return new ScopeLine(ORDER_LINE, "V1", value, "EA", List.of(detail));
    }

    private OrderLineDO orderLine() {
        OrderLineDO line = new OrderLineDO();
        line.setId(ORDER_LINE);
        line.setTenantId(TENANT);
        line.setSourceVersion("V1");
        line.setItemCode("P-1");
        line.setModelCode("M-1");
        line.setQuantity(new BigDecimal("10"));
        line.setUnitCode("EA");
        line.setQuantityStatus("CONFIRMED");
        line.setSourceLifecycleStatus("ACTIVE");
        line.setSourceUpdatedAt(LocalDateTime.now(clock));
        return line;
    }

    private DeliveryScopeProjectVersionDO projectVersion() {
        DeliveryScopeProjectVersionDO row = new DeliveryScopeProjectVersionDO();
        row.setId(100L);
        row.setTenantId(TENANT);
        row.setProjectId(PROJECT);
        row.setScopeVersion(0L);
        row.setPayloadVersion(0);
        row.setVersion(0);
        return row;
    }

    private DeliveryScopeDO scope(Long projectId, String status, String quantity) {
        DeliveryScopeDO row = new DeliveryScopeDO();
        row.setId(projectId.equals(PROJECT) ? 200L : 201L);
        row.setTenantId(TENANT);
        row.setProjectId(projectId);
        row.setOrderLineId(ORDER_LINE);
        row.setAllocatedQty(new BigDecimal(quantity));
        row.setScopeStatus(status);
        row.setAllocationVersion(1L);
        row.setVersion(0);
        return row;
    }

    private DeliveryScopeDetailDO storedDetail(Long scopeId, String status, String quantity) {
        DeliveryScopeDetailDO row = new DeliveryScopeDetailDO();
        row.setId(300L);
        row.setTenantId(TENANT);
        row.setDeliveryScopeId(scopeId);
        row.setAllocatedQty(new BigDecimal(quantity));
        row.setUnitCode("EA");
        row.setProductCode("P-1");
        row.setModelCode("M-1");
        row.setLocationText("机房A");
        row.setLocationResolutionStatus("UNRESOLVED");
        row.setDetailStatus(status);
        row.setSourceSnapshot("{}");
        return row;
    }

    private ProjectScopeQualificationAdapter.Snapshot project(String lifecycle, String stage) {
        return new ProjectScopeQualificationAdapter.Snapshot(TENANT, PROJECT, ACTOR, lifecycle, stage, 1, 2L, 3L);
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final List<SuccessFacts> facts = new ArrayList<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            T response = operation.get();
            facts.add(successFactsFactory.apply(response));
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
