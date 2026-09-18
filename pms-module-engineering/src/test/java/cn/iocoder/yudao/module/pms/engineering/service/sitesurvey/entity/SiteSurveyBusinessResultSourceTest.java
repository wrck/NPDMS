package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyBusinessResultSourceTest {
    private final SiteSurveyEntityMapper mapper = mock(SiteSurveyEntityMapper.class);
    private final SiteSurveyBusinessResultSource source = new SiteSurveyBusinessResultSource(mapper);
    private final SiteSurveyEntityDO row = new SiteSurveyEntityDO();
    private final Query query = new Query(1L,3L,SiteSurveyBusinessResultSource.TYPE,"40",null);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        row.setId(40L); row.setTenantId(1L); row.setProjectId(3L); row.setVersion(2); row.setStatus(1);
        row.setConfirmedAt(LocalDateTime.of(2026,9,18,1,0)); when(mapper.selectById(40L)).thenReturn(row);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void archivingDoesNotFabricateAnotherConfirmationOrBusinessRevision() {
        var confirmed = source.inspect(query).result();
        row.setStatus(3); row.setVersion(3);
        var archived = source.inspect(query).result();
        assertEquals(confirmed.resultId(), archived.resultId()); assertEquals(confirmed.formedAt(), archived.formedAt());
        assertNull(archived.businessRevision()); assertEquals(Validity.CURRENT, archived.validity());
        assertFalse(source.descriptor().historicalLookup()); assertFalse(source.descriptor().exactLookup());
    }
    @ParameterizedTest @ValueSource(ints = {0,2})
    void draftAndRejectionAreNotConfirmation(int status) {
        row.setStatus(status); assertEquals(Status.NOT_FORMED,source.inspect(query).status());
    }
    @ParameterizedTest @ValueSource(strings = {"status", "time", "version"})
    void missingFactMetadataIsUnavailable(String damage) {
        if (damage.equals("status")) row.setStatus(4);
        else if (damage.equals("time")) row.setConfirmedAt(null);
        else row.setVersion(null);
        assertEquals(Status.UNAVAILABLE,source.inspect(query).status());
    }
    @Test void exactHistoricalLookupIsNotClaimedForAMutableRow() {
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L,3L,query.type(),"40","40")));
        verifyNoInteractions(mapper);
    }
    @Test void scopeAndNativeIdChecksPrecedeReading() {
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(2L,3L,query.type(),"40",null)));
        assertThrows(IllegalArgumentException.class, () -> source.inspect(new Query(1L,3L,query.type(),"40.5",null)));
        verifyNoInteractions(mapper);
        row.setProjectId(4L); assertThrows(IllegalArgumentException.class, () -> source.inspect(query));
    }
    @Test void deletedAndAbsentRowsAreNotSuccessfulFacts() {
        row.setDeleted(true); assertEquals(Status.NOT_FOUND,source.inspect(query).status());
        when(mapper.selectById(40L)).thenReturn(null); assertEquals(Status.NOT_FOUND,source.inspect(query).status());
    }
}
