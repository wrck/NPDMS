package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisBusinessResultSourceTest {
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisBusinessResultSource source = new RequirementAnalysisBusinessResultSource(mapper);
    private final RequirementAnalysisRevisionDO row = new RequirementAnalysisRevisionDO();
    private final Query exact = new Query(1L, 3L, RequirementAnalysisBusinessResultSource.TYPE, "100", "40");

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        row.setId(40L); row.setEntityId(100L); row.setTenantId(1L); row.setProjectId(3L);
        row.setRevisionNo(2); row.setVersion(5); row.setRevisionState("FROZEN"); row.setStatusCode("COMPLETED");
        row.setEffectiveMarker(1); row.setFrozenAt(LocalDateTime.of(2026, 9, 18, 1, 0));
        when(mapper.selectRevision(any())).thenReturn(row);
        when(mapper.selectEffective(any())).thenReturn(row);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void nativeObjectAndResultIdentitiesDoNotFollowOptimisticVersions() {
        var first = source.inspect(exact).result();
        assertEquals("100", first.objectId()); assertEquals("40", first.resultId()); assertEquals("2", first.businessRevision());
        assertEquals(Validity.CURRENT, first.validity());
        row.setVersion(6); row.setEffectiveMarker(null);
        var historical = source.inspect(exact).result();
        assertEquals(first.resultId(), historical.resultId()); assertEquals(first.businessRevision(), historical.businessRevision());
        assertNotEquals(first.observationVersion(), historical.observationVersion());
        assertEquals(Validity.NOT_CURRENT, historical.validity()); assertEquals(first.formedAt(), historical.formedAt());
        assertTrue(source.descriptor().historicalLookup());
        verify(mapper, never()).selectEffective(any());
    }
    @Test void aNewRevisionOfTheSameObjectIsANewResultAndCurrentLookupIsExplicit() {
        var first = source.inspect(exact).result();
        row.setId(41L); row.setRevisionNo(3);
        var next = source.inspect(new Query(1L, 3L, exact.type(), "100", null)).result();
        assertEquals(first.objectId(), next.objectId()); assertNotEquals(first.resultId(), next.resultId());
        verify(mapper).selectEffective(new RequirementProjectQuery(1L, 3L));
    }
    @Test void aMissingPinnedResultNeverFallsBackToTheCurrentRevision() {
        when(mapper.selectRevision(any())).thenReturn(null);
        assertEquals(Status.NOT_FOUND, source.inspect(exact).status());
        verify(mapper, never()).selectEffective(any());
    }
    @ParameterizedTest @ValueSource(strings = {"tenant", "project", "object", "result"})
    void rejectsCrossScopeOwnerRows(String damage) {
        switch (damage) {
            case "tenant" -> row.setTenantId(2L);
            case "project" -> row.setProjectId(4L);
            case "object" -> row.setEntityId(101L);
            case "result" -> row.setId(41L);
            default -> throw new AssertionError(damage);
        }
        assertThrows(IllegalArgumentException.class, () -> source.inspect(exact));
    }
    @ParameterizedTest @ValueSource(strings = {"state", "status", "time", "version", "revision", "marker"})
    void inconsistentRecordsAreNotSuccessfulFacts(String damage) {
        switch (damage) {
            case "state" -> row.setRevisionState("UNKNOWN");
            case "status" -> row.setStatusCode("DRAFT");
            case "time" -> row.setFrozenAt(null);
            case "version" -> row.setVersion(null);
            case "revision" -> row.setRevisionNo(0);
            case "marker" -> row.setEffectiveMarker(2);
            default -> throw new AssertionError(damage);
        }
        assertEquals(Status.UNAVAILABLE, source.inspect(exact).status());
    }
    @Test void draftAndDeletedRecordsDoNotBecomeCompletionResults() {
        row.setRevisionState("DRAFT");
        assertEquals(Status.NOT_FORMED, source.inspect(exact).status());
        row.setDeleted(true);
        assertEquals(Status.NOT_FOUND, source.inspect(exact).status());
    }
    @Test void invalidInputIsRejectedBeforeAnyOwnerQuery() {
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(2L,3L,exact.type(),"100","40")));
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L,3L,new Type("OTHER","REQUIREMENT_ANALYSIS","REQUIREMENT_ANALYSIS_COMPLETED"),"100","40")));
        for (String id : new String[]{"040", "40.0", "+40", "-1", "9223372036854775808"})
            assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L,3L,exact.type(),"100",id)));
        verifyNoInteractions(mapper);
    }

    @Test void originalOwnerEventMapsToNativeLookupWithoutCreatingAResult() {
        var type=RequirementAnalysisBusinessResultSource.TYPE;
        var event=new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,1L,3L,type.ownerContext(),type.entityType(),
                "40","40",2,"native-fact",type.resultType(),"OWNER.CHANGED","key",9L,LocalDateTime.now(),"trace");
        var query=source.changeQuery(event);
        assertEquals(type,query.type());assertEquals(3L,query.projectId());
        assertNull(query.objectId());assertEquals("40",query.resultId());
        assertTrue(source.declaresFormation(event));
        verifyNoInteractions(mapper);
    }
}
