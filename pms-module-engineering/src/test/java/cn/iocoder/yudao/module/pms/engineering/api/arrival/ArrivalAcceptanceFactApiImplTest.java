package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.projection.ArrivalProjectFactAllocation;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalAcceptanceFactApiImplTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PROJECT_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 10, 0);

    @Mock
    private ArrivalAcceptanceMapper acceptanceMapper;
    @Mock
    private ArrivalLineMapper lineMapper;
    @Mock
    private ArrivalDifferenceMapper differenceMapper;
    @Mock
    private DeliveryScopePort deliveryScopePort;
    @Mock
    private DeviceScopeFactPort deviceScopeFactPort;

    private ArrivalAcceptanceFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        api = new ArrivalAcceptanceFactApiImpl(acceptanceMapper, lineMapper, differenceMapper,
                deliveryScopePort, deviceScopeFactPort,
                Clock.fixed(Instant.parse("2026-08-30T02:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void implementationIsProxyableButNotRegisteredBeforeOwnerProvidersExist() throws Exception {
        assertFalse(Modifier.isFinal(ArrivalAcceptanceFactApiImpl.class.getModifiers()));
        assertFalse(ArrivalAcceptanceFactApiImpl.class.isAnnotationPresent(Service.class));
        Transactional inspect = ArrivalAcceptanceFactApiImpl.class
                .getMethod("inspect", ArrivalAcceptanceFactQuery.class)
                .getAnnotation(Transactional.class);
        Transactional locked = ArrivalAcceptanceFactApiImpl.class
                .getMethod("lockAndRevalidate", ArrivalAcceptanceFactRevalidationQuery.class)
                .getAnnotation(Transactional.class);
        assertTrue(inspect.readOnly());
        assertTrue(locked.rollbackFor().length > 0);
    }

    @Test
    void inspectReturnsStableMultiBatchAcceptedFact() {
        prepareCurrentScope();
        prepareReadFacts();

        ArrivalAcceptanceFact fact = api.inspect(query());

        assertEquals(ArrivalAcceptanceFact.DECISION_ACCEPTED, fact.decision());
        assertEquals(List.of(20L, 30L, 40L), fact.sourceAcceptanceIds());
        assertEquals(3L, fact.factVersion());
        assertFalse(fact.reopened());
        assertEquals(Set.of(11L, 12L), fact.acceptedDeviceIds());
        assertEquals(List.of(quantity("6")), fact.acceptedQuantityScopes());
        assertEquals(List.of(quantity("4")), fact.exemptedQuantityScopes());
        assertEquals(new ArrivalScopeWatermark(5L, Map.of(11L, 7L, 12L, 8L)),
                fact.scopeWatermark());
    }

    @Test
    void latestQualifiedDifferenceSourceMarksCurrentFactReopened() {
        prepareCurrentScope();
        prepareReadFacts();
        when(acceptanceMapper.selectLatestProjectFactAllocations(any())).thenReturn(List.of(
                allocation(4L, "DIFFERENCE", 99L, 40L, null),
                allocation(3L, "ACCEPTANCE", 40L, 40L, null)));

        ArrivalAcceptanceFact fact = api.inspect(query());

        assertEquals(4L, fact.factVersion());
        assertTrue(fact.reopened());
    }

    @Test
    void duplicatedMaximumOrUnprovableSuccessorSourceFailsClosed() {
        prepareCurrentScope();
        prepareReadFacts();
        when(acceptanceMapper.selectLatestProjectFactAllocations(any())).thenReturn(List.of(
                allocation(4L, "DIFFERENCE", 99L, 40L, null),
                allocation(4L, "ACCEPTANCE", 40L, 40L, null)));
        assertThrows(IllegalStateException.class, () -> api.inspect(query()));

        when(acceptanceMapper.selectLatestProjectFactAllocations(any())).thenReturn(List.of(
                allocation(4L, "ACCEPTANCE", 40L, 40L, 30L)));
        assertThrows(IllegalStateException.class, () -> api.inspect(query()));
    }

    @Test
    void staleScopeReturnsCurrentFactWithoutLockingProviders() {
        prepareCurrentScope();
        prepareReadFacts();
        ArrivalAcceptanceFactRevalidationQuery stale = new ArrivalAcceptanceFactRevalidationQuery(
                TENANT_ID, PROJECT_ID, Set.of(11L, 12L), List.of(quantity("10")),
                3L, new ArrivalScopeWatermark(4L, Map.of(11L, 7L, 12L, 8L)));

        ArrivalAcceptanceFact fact = api.lockAndRevalidate(stale);

        assertEquals(ArrivalAcceptanceFact.DECISION_STALE, fact.decision());
        assertEquals(5L, fact.scopeWatermark().deliveryScopeVersion());
        verify(deliveryScopePort, never()).lockAndRevalidate(any(), any());
        verify(lineMapper, never()).selectConfirmedAcceptedByProjectForUpdate(any());
    }

    @Test
    void staleFactVersionIsDetectedAfterOwnerAndLocalLocks() {
        prepareCurrentScope();
        prepareLockedScope();
        prepareLockedFacts();
        ArrivalAcceptanceFactRevalidationQuery stale = new ArrivalAcceptanceFactRevalidationQuery(
                TENANT_ID, PROJECT_ID, Set.of(11L, 12L), List.of(quantity("10")),
                2L, new ArrivalScopeWatermark(5L, Map.of(11L, 7L, 12L, 8L)));

        ArrivalAcceptanceFact fact = api.lockAndRevalidate(stale);

        assertEquals(ArrivalAcceptanceFact.DECISION_STALE, fact.decision());
        assertEquals(3L, fact.factVersion());
        verify(lineMapper).selectConfirmedAcceptedByProjectForUpdate(any());
        verify(differenceMapper).selectEffectiveExemptionsByProjectForUpdate(any());
        verify(acceptanceMapper).selectConfirmedByProjectForUpdate(any());
        verify(acceptanceMapper).selectLatestAllocatedRootsForUpdate(any());
        verify(differenceMapper).selectLatestAllocatedDifferencesForUpdate(any());
    }

    @Test
    void currentReopenedFactIsNotPermanentlyStale() {
        prepareCurrentScope();
        prepareLockedScope();
        prepareLockedFacts();
        when(acceptanceMapper.selectLatestAllocatedRootsForUpdate(any())).thenReturn(List.of(
                allocation(3L, "ACCEPTANCE", 40L, 40L, null)));
        when(differenceMapper.selectLatestAllocatedDifferencesForUpdate(any())).thenReturn(List.of(
                allocation(4L, "DIFFERENCE", 99L, 40L, null)));
        ArrivalAcceptanceFactRevalidationQuery current = new ArrivalAcceptanceFactRevalidationQuery(
                TENANT_ID, PROJECT_ID, Set.of(11L, 12L), List.of(quantity("10")),
                4L, new ArrivalScopeWatermark(5L, Map.of(11L, 7L, 12L, 8L)));

        ArrivalAcceptanceFact fact = api.lockAndRevalidate(current);

        assertEquals(ArrivalAcceptanceFact.DECISION_ACCEPTED, fact.decision());
        assertTrue(fact.reopened());
    }

    @Test
    void runtimeTenantMismatchFailsBeforeProviderOrDatabaseAccess() {
        TenantContextHolder.setTenantId(2L);

        assertThrows(IllegalArgumentException.class, () -> api.inspect(query()));

        verify(deliveryScopePort, never()).inspectAssignedScope(any());
        verify(acceptanceMapper, never()).selectConfirmedByProject(any());
    }

    @Test
    void partiallyOverlappingAssignedSerialsFailBeforeDeviceFactLookup() {
        when(deliveryScopePort.inspectAssignedScope(PROJECT_ID)).thenReturn(
                new DeliveryScopePort.AssignedScope(PROJECT_ID, 5L, List.of(
                        new DeliveryScopePort.AssignedLine(1L, BigDecimal.valueOf(2), "SET",
                                "P-1", "M-1", Set.of("SN-1", "SN-2")),
                        new DeliveryScopePort.AssignedLine(2L, BigDecimal.valueOf(2), "SET",
                                "P-2", "M-2", Set.of("SN-2", "SN-3")))));

        assertThrows(IllegalStateException.class, () -> api.inspect(query()));

        verify(deviceScopeFactPort, never()).resolveBySerials(any(), any(), any());
        verify(acceptanceMapper, never()).selectConfirmedByProject(any());
    }

    private void prepareCurrentScope() {
        when(deliveryScopePort.inspectAssignedScope(PROJECT_ID)).thenReturn(deliveryScope());
        when(deviceScopeFactPort.resolveBySerials(TENANT_ID, PROJECT_ID, Set.of("SN-1", "SN-2")))
                .thenReturn(deviceScope());
    }

    private void prepareLockedScope() {
        when(deliveryScopePort.lockAndRevalidate(PROJECT_ID, 5L)).thenReturn(deliveryScope());
        when(deviceScopeFactPort.lockAndRevalidate(any(), any(), any())).thenReturn(deviceScope());
    }

    private void prepareReadFacts() {
        when(lineMapper.selectConfirmedAcceptedByProject(any())).thenReturn(lines());
        when(differenceMapper.selectEffectiveExemptionsByProject(any())).thenReturn(differences());
        when(acceptanceMapper.selectConfirmedByProject(any())).thenReturn(roots());
        prepareAllocation();
    }

    private void prepareLockedFacts() {
        when(lineMapper.selectConfirmedAcceptedByProjectForUpdate(any())).thenReturn(lines());
        when(acceptanceMapper.selectConfirmedByProjectForUpdate(any())).thenReturn(roots());
        when(differenceMapper.selectEffectiveExemptionsByProjectForUpdate(any())).thenReturn(differences());
        when(acceptanceMapper.selectLatestAllocatedRootsForUpdate(any())).thenReturn(List.of(
                allocation(3L, "ACCEPTANCE", 40L, 40L, null),
                allocation(2L, "ACCEPTANCE", 30L, 30L, null)));
        when(differenceMapper.selectLatestAllocatedDifferencesForUpdate(any())).thenReturn(List.of());
    }

    private void prepareAllocation() {
        when(acceptanceMapper.selectLatestProjectFactAllocations(any())).thenReturn(List.of(
                allocation(3L, "ACCEPTANCE", 40L, 40L, null),
                allocation(2L, "ACCEPTANCE", 30L, 30L, null)));
    }

    private static ArrivalAcceptanceFactQuery query() {
        return new ArrivalAcceptanceFactQuery(TENANT_ID, PROJECT_ID,
                Set.of(11L, 12L), List.of(quantity("10")));
    }

    private static DeliveryScopePort.AssignedScope deliveryScope() {
        return new DeliveryScopePort.AssignedScope(PROJECT_ID, 5L, List.of(
                new DeliveryScopePort.AssignedLine(1L, BigDecimal.valueOf(2), "SET",
                        "P-1", "M-1", Set.of("SN-1", "SN-2")),
                new DeliveryScopePort.AssignedLine(2L, BigDecimal.TEN, "EA",
                        "P-2", "M-2", Set.of())));
    }

    private static DeviceScopeFactPort.DeviceScopeFact deviceScope() {
        return new DeviceScopeFactPort.DeviceScopeFact(PROJECT_ID, List.of(
                new DeviceScopeFactPort.DeviceFact(12L, "SN-2", PROJECT_ID, 8L),
                new DeviceScopeFactPort.DeviceFact(11L, "SN-1", PROJECT_ID, 7L)));
    }

    private static List<ArrivalLineDO> lines() {
        return List.of(deviceLine(30L, 11L), deviceLine(20L, 12L), quantityLine(20L, "6"));
    }

    private static ArrivalLineDO deviceLine(Long acceptanceId, Long deviceId) {
        ArrivalLineDO line = new ArrivalLineDO();
        line.setArrivalAcceptanceId(acceptanceId);
        line.setScopeType("DEVICE");
        line.setDeviceId(deviceId);
        line.setAcceptedQuantity(BigDecimal.ONE);
        line.setUnit("SET");
        return line;
    }

    private static ArrivalLineDO quantityLine(Long acceptanceId, String quantity) {
        ArrivalLineDO line = new ArrivalLineDO();
        line.setArrivalAcceptanceId(acceptanceId);
        line.setScopeType("ORDER_MODEL_QUANTITY");
        line.setOrderLineId(2L);
        line.setProductCode("P-2");
        line.setModelCode("M-2");
        line.setAcceptedQuantity(new BigDecimal(quantity));
        line.setUnit("EA");
        return line;
    }

    private static List<ArrivalDifferenceDO> differences() {
        ArrivalDifferenceDO difference = new ArrivalDifferenceDO();
        difference.setArrivalAcceptanceId(40L);
        difference.setResolutionStatus("EXEMPTED");
        difference.setReason("approved reason");
        difference.setRiskDescription("accepted risk");
        difference.setScopeSnapshot("{\"scopeType\":\"ORDER_MODEL_QUANTITY\",\"orderLineId\":2,"
                + "\"productCode\":\"P-2\",\"modelCode\":\"M-2\",\"quantity\":4,"
                + "\"unitCode\":\"EA\"}");
        difference.setApprovedBy(7L);
        difference.setApprovedAt(NOW.minusHours(1));
        difference.setEvidenceId(90L);
        difference.setEvidenceRevision(1);
        difference.setExemptionExpiresAt(NOW.plusDays(1));
        return List.of(difference);
    }

    private static List<ArrivalAcceptanceDO> roots() {
        return List.of(root(20L, 1L), root(30L, 2L), root(40L, 3L));
    }

    private static ArrivalAcceptanceDO root(Long id, Long factVersion) {
        ArrivalAcceptanceDO root = new ArrivalAcceptanceDO();
        root.setId(id);
        root.setProjectId(PROJECT_ID);
        root.setStatus("CONFIRMED");
        root.setProjectFactVersion(factVersion);
        return root;
    }

    private static ArrivalProjectFactAllocation allocation(
            Long version, String type, Long sourceId, Long acceptanceId, Long predecessorId) {
        return new ArrivalProjectFactAllocation(version, type, sourceId, acceptanceId, predecessorId);
    }

    private static ArrivalQuantityScopeFact quantity(String value) {
        return new ArrivalQuantityScopeFact(2L, "P-2", "M-2", new BigDecimal(value), "EA");
    }
}
