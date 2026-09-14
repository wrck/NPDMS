package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyRuleEventsTest {
    private final SiteSurveyMapper mapper = mock(SiteSurveyMapper.class);
    private final EngineeringRuleReevaluationEvents events = mock(EngineeringRuleReevaluationEvents.class);
    private final SiteSurveyServiceImpl service = new SiteSurveyServiceImpl();

    private SiteSurveyDO prepare(int status) {
        ReflectionTestUtils.setField(service, "siteSurveyMapper", mapper);
        ReflectionTestUtils.setField(service, "ruleEvents", events);
        ReflectionTestUtils.setField(service, "writeAccess", mock(SiteSurveyWriteAccess.class));
        var row = new SiteSurveyDO(); row.setId(11L); row.setTenantId(7L); row.setProjectId(9L);
        row.setStatus(status); row.setVersion(3);
        when(mapper.selectById(11L)).thenReturn(row);
        return row;
    }

    @ParameterizedTest
    @CsvSource({"0,CONFIRM,1", "0,REJECT,2", "1,ARCHIVE,3"})
    void realOwnerTransitionAppendsWakeupOnlyAfterSuccessfulVersionedWrite(int before, String action, int after) {
        var row = prepare(before);
        when(mapper.updateById(any(SiteSurveyDO.class))).thenAnswer(call -> {
            SiteSurveyDO updated = call.getArgument(0); updated.setVersion(4); return 1;
        });
        switch (action) {
            case "CONFIRM" -> service.confirmSiteSurvey(11L, null);
            case "REJECT" -> service.rejectSiteSurvey(11L, null);
            case "ARCHIVE" -> service.archiveSiteSurvey(11L, null);
            default -> throw new AssertionError(action);
        }
        assertEquals(after, row.getStatus());
        if (after == 1) { assertNotNull(row.getConfirmedAt()); assertNull(row.getArchivedAt()); }
        if (after == 3) assertNotNull(row.getArchivedAt());
        if (after == 2) { assertNull(row.getConfirmedAt()); assertNull(row.getArchivedAt()); }
        var order = inOrder(mapper, events);
        order.verify(mapper).updateById(row);
        order.verify(events).changed(eq(9L), eq("SiteSurvey"), eq(11L), isNull(), eq("site-survey:11:4"));
    }

    @Test void rejectedStateOrCasFailureDoesNotEmitWakeup() {
        var row = prepare(3);
        assertThrows(RuntimeException.class, () -> service.confirmSiteSurvey(11L, null));
        verify(mapper, never()).updateById(any(SiteSurveyDO.class));
        row.setStatus(0);
        when(mapper.updateById(any(SiteSurveyDO.class))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.confirmSiteSurvey(11L, null));
        verifyNoInteractions(events);
    }

    @Test void deletionPublishesAReevaluationButStillProtectsArchivedHistoryAndFailedCas() {
        var row = prepare(3);
        assertThrows(RuntimeException.class, () -> service.deleteSiteSurvey(11L, null));
        verifyNoInteractions(events);
        row.setStatus(0);
        when(mapper.deleteDraft(any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.deleteSiteSurvey(11L, null));
        verifyNoInteractions(events);
        when(mapper.deleteDraft(any())).thenReturn(1);
        service.deleteSiteSurvey(11L, null);
        verify(events).changed(eq(9L), eq("SiteSurvey"), eq(11L), isNull(), eq("site-survey:11:3"));
    }

    @Test void eventWriteFailurePropagatesToTheExistingOwnerTransaction() {
        prepare(0);
        when(mapper.updateById(any(SiteSurveyDO.class))).thenReturn(1);
        doThrow(new IllegalStateException("Outbox unavailable")).when(events).changed(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.confirmSiteSurvey(11L, null));
    }
}
