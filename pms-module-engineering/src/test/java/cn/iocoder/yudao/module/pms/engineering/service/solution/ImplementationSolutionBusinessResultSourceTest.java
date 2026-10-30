package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImplementationSolutionBusinessResultSourceTest {
    private final SolutionMapper mapper = mock(SolutionMapper.class);
    private final ImplementationSolutionBusinessResultSource source =
            new ImplementationSolutionBusinessResultSource(mapper);
    private final SolutionDO row = new SolutionDO();
    private final Query query = new Query(1L, 3L, ImplementationSolutionBusinessResultSource.TYPE, "40", null);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        row.setId(40L); row.setTenantId(1L); row.setProjectId(3L); row.setVersion(4L); row.setStatus(3);
        row.setApprovedTime(LocalDateTime.of(2026, 9, 30, 10, 0)); row.setBaselineVersion(5);
        when(mapper.selectById(40L)).thenReturn(row);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void approvalFormsAFactAnchoredToTheFrozenBaseline() {
        var result = source.inspect(query).result();
        assertEquals("40", result.resultId());
        assertEquals("5", result.businessRevision());
        assertEquals("4", result.observationVersion());
        assertEquals(Validity.CURRENT, result.validity());
        assertEquals(row.getApprovedTime(), result.formedAt());
    }

    @Test void reapprovalReplacesTheFactWithANewBaselineAndTime() {
        var approved = source.inspect(query).result();
        row.setVersion(6L); row.setBaselineVersion(7); row.setApprovedTime(LocalDateTime.of(2026, 9, 30, 15, 0));
        var reapproved = source.inspect(query).result();
        assertEquals(approved.resultId(), reapproved.resultId());
        assertNotEquals(approved.businessRevision(), reapproved.businessRevision());
        assertNotEquals(approved.formedAt(), reapproved.formedAt());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 4, 5, 6})
    void onlyTheApprovedStateIsAnApprovedFact(int status) {
        row.setStatus(status);
        assertEquals(Status.NOT_FORMED, source.inspect(query).status());
    }

    @ParameterizedTest @ValueSource(strings = {"time", "baseline", "version"})
    void missingApprovalFactMetadataIsUnavailable(String damage) {
        if (damage.equals("time")) row.setApprovedTime(null);
        else if (damage.equals("baseline")) row.setBaselineVersion(null);
        else row.setVersion(null);
        assertEquals(Status.UNAVAILABLE, source.inspect(query).status());
    }

    @Test void lockedLookupReadsThroughTheOwnerLockQuery() {
        when(mapper.selectResultForUpdate(any())).thenReturn(row);
        var observation = source.lockAndInspect(query);
        assertEquals(Status.AVAILABLE, observation.status());
        verify(mapper).selectResultForUpdate(argThat(q -> q.tenantId() == 1L && q.projectId() == 3L && q.solutionId() == 40L));
        verify(mapper, never()).selectById(any());
    }

    @Test void exactHistoricalLookupIsNotClaimedForAMutableRow() {
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L, 3L, query.type(), "40", "40")));
        verifyNoInteractions(mapper);
    }

    @Test void scopeAndNativeIdChecksPrecedeReading() {
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(2L, 3L, query.type(), "40", null)));
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L, 3L, query.type(), "40.5", null)));
        verifyNoInteractions(mapper);
        row.setProjectId(4L);
        assertThrows(IllegalArgumentException.class, () -> source.inspect(query));
    }

    @Test void deletedAndAbsentRowsAreNotSuccessfulFacts() {
        row.setDeleted(true); assertEquals(Status.NOT_FOUND, source.inspect(query).status());
        when(mapper.selectById(40L)).thenReturn(null); assertEquals(Status.NOT_FOUND, source.inspect(query).status());
    }

    @Test void originalOwnerEventMapsToNativeLookupWithoutCreatingAResult() {
        var type = ImplementationSolutionBusinessResultSource.TYPE;
        var event = new BusinessOperationResultEvent(UUID.randomUUID().toString(), 1, 1L, 3L, type.ownerContext(), type.entityType(),
                "40", null, 2L, "native-fact", type.resultType(), "OWNER.ImplementationSolution.CHANGED", "key", 9L, LocalDateTime.now(), "trace");
        var changeQuery = source.changeQuery(event);
        assertEquals(type, changeQuery.type()); assertEquals(3L, changeQuery.projectId());
        assertEquals("40", changeQuery.objectId()); assertNull(changeQuery.resultId());
        assertTrue(source.declaresFormation(event));
        assertTrue(source.transactionalChangeCoverage());
        verifyNoInteractions(mapper);
    }

    @Test void nonApprovalResultCodesAreNotFormationsAndRevisionEventsAreRejected() {
        var type = ImplementationSolutionBusinessResultSource.TYPE;
        var withdrawn = new BusinessOperationResultEvent(UUID.randomUUID().toString(), 1, 1L, 3L, type.ownerContext(), type.entityType(),
                "40", null, 2L, "native-fact", "IMPLEMENTATION_PLAN_WITHDRAWN", "OWNER.ImplementationSolution.CHANGED", "key", 9L, LocalDateTime.now(), "trace");
        assertFalse(source.declaresFormation(withdrawn));
        var revised = new BusinessOperationResultEvent(UUID.randomUUID().toString(), 1, 1L, 3L, type.ownerContext(), type.entityType(),
                "40", "9", 2L, "native-fact", type.resultType(), "OWNER.ImplementationSolution.CHANGED", "key", 9L, LocalDateTime.now(), "trace");
        assertThrows(IllegalArgumentException.class, () -> source.changeQuery(revised));
    }
}
