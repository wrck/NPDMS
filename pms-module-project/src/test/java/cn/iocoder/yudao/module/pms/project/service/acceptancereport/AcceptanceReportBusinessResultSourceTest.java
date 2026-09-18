package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptanceReportBusinessResultSourceTest {
    private final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    private final AcceptanceReportVersionMapper reports = mock(AcceptanceReportVersionMapper.class);
    private final AcceptanceReportBusinessResultSource source = new AcceptanceReportBusinessResultSource(activities,reports);
    private final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    private final AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
    private final Query exact = new Query(1L,3L,AcceptanceReportBusinessResultSource.TYPE,"100","40");

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        activity.setId(100L); activity.setTenantId(1L); activity.setProjectId(3L); activity.setVersion(2);
        activity.setActivityStatus("PENDING"); activity.setCurrentReportVersionId(40L);
        report.setId(40L); report.setTenantId(1L); report.setAcceptanceId(100L); report.setReportVersionNo(2);
        report.setReportStatus("EFFECTIVE"); report.setEffectiveFrom(LocalDateTime.of(2026,9,18,1,0));
        when(activities.selectById(100L)).thenReturn(activity); when(reports.selectById(40L)).thenReturn(report);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void reportResultDoesNotChangeAcceptanceCompletion() {
        var found = source.inspect(exact).result();
        assertEquals("100",found.objectId()); assertEquals("40",found.resultId()); assertEquals("2",found.businessRevision());
        assertEquals("REPORT_VERSION_PUBLISHED",found.type().resultType()); assertEquals("PENDING",activity.getActivityStatus());
        assertEquals(found,source.inspect(new Query(1L,3L,exact.type(),"100",null)).result());
        verify(activities,never()).updateById(any(AcceptanceActivityDO.class));
        verify(reports,never()).updateById(any(AcceptanceReportVersionDO.class));
    }
    @ParameterizedTest @ValueSource(strings = {"SUPERSEDED","REVOKED"})
    void replacementAndRevocationRetainTheHistoricalResultIdentity(String status) {
        var first = source.inspect(exact).result();
        report.setReportStatus(status); report.setEffectiveTo(first.formedAt().plusDays(1));
        activity.setCurrentReportVersionId(null); activity.setVersion(3);
        var history = source.inspect(exact).result();
        assertEquals(first.resultId(),history.resultId()); assertEquals(first.businessRevision(),history.businessRevision());
        assertEquals(first.formedAt(),history.formedAt());
        assertEquals(status.equals("REVOKED")?Validity.REVOKED:Validity.NOT_CURRENT,history.validity());
        assertTrue(source.descriptor().historicalLookup());
        assertEquals(Status.NOT_FORMED,source.inspect(new Query(1L,3L,exact.type(),"100",null)).status());
    }
    @Test void unknownPinnedResultNeverSelectsTheCurrentReport() {
        assertEquals(Status.NOT_FOUND,source.inspect(new Query(1L,3L,exact.type(),"100","41")).status());
        verifyNoInteractions(activities); verify(reports,never()).selectById(40L);
    }
    @Test void draftIsNotAPublication() {
        report.setReportStatus("DRAFT"); report.setEffectiveFrom(null);
        assertEquals(Status.NOT_FORMED,source.inspect(exact).status());
        assertEquals(Status.UNAVAILABLE,source.inspect(new Query(1L,3L,exact.type(),"100",null)).status());
    }
    @ParameterizedTest @ValueSource(strings = {"report-tenant","activity-tenant","project","object","result"})
    void rejectsMismatchedOwnerRows(String damage) {
        switch (damage) {
            case "report-tenant" -> report.setTenantId(2L);
            case "activity-tenant" -> activity.setTenantId(2L);
            case "project" -> activity.setProjectId(4L);
            case "object" -> report.setAcceptanceId(101L);
            case "result" -> report.setId(41L);
            default -> throw new AssertionError(damage);
        }
        assertThrows(IllegalArgumentException.class,()->source.inspect(exact));
    }
    @ParameterizedTest @ValueSource(strings = {"status","from","to","pointer","version","revision"})
    void inconsistentCurrentAndHistoricalObservationsFailClosed(String damage) {
        switch (damage) {
            case "status" -> report.setReportStatus("UNKNOWN");
            case "from" -> report.setEffectiveFrom(null);
            case "to" -> report.setEffectiveTo(report.getEffectiveFrom());
            case "pointer" -> activity.setCurrentReportVersionId(41L);
            case "version" -> activity.setVersion(null);
            case "revision" -> report.setReportVersionNo(0);
            default -> throw new AssertionError(damage);
        }
        assertEquals(Status.UNAVAILABLE,source.inspect(exact).status());
    }
    @Test void invalidTenantDoesNotReadAnyOwnerData() {
        assertThrows(IllegalArgumentException.class,()->source.inspect(new Query(2L,3L,exact.type(),"100","40")));
        verifyNoInteractions(activities,reports);
    }
}
