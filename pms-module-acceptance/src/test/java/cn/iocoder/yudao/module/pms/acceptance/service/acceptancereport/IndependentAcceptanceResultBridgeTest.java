package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event.ProjectAcceptanceReportChanged;
import cn.iocoder.yudao.module.pms.acceptance.service.operation.AcceptanceOperationResultBridge;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentAcceptanceResultBridgeTest {
    final ProjectBusinessResultRecordingApi journal = mock(ProjectBusinessResultRecordingApi.class);
    final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    final PlatformBusinessEventApi outbox = mock(PlatformBusinessEventApi.class);
    final AcceptanceOperationResultBridge bridge = new AcceptanceOperationResultBridge(of(journal), of(activities), of(outbox));
    final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        activity.setId(55L); activity.setTenantId(7L); activity.setProjectId(80L); activity.setVersion(3L); activity.setOriginKind("DIRECT");
        when(activities.selectById(55L)).thenReturn(activity);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void independentPublicationAndRevocationEnterTheSameDurableResultChannelWithoutNodeIdentity() {
        for (String change : java.util.List.of("EFFECTIVE", "REPLACED", "REVOKED")) {
            bridge.onAppended(message(change));
        }
        var events = org.mockito.ArgumentCaptor.forClass(BusinessOperationResultEvent.class);
        verify(journal, times(3)).record(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event -> event.projectId().equals(80L)
                && event.objectId().equals("55") && event.revisionId().equals("66") && event.actorId().equals(19L)));
        assertEquals(java.util.List.of("REPORT_VERSION_PUBLISHED", "REPORT_VERSION_PUBLISHED", "REPORT_VERSION_REVOKED"),
                events.getAllValues().stream().map(BusinessOperationResultEvent::resultCode).toList());
        verify(outbox, times(3)).append(eq("ACCEPTANCE"), eq("55"), any());
    }
    @Test void projectMismatchAndJournalFailureCannotEmitACompletionNotification() {
        activity.setProjectId(90L);
        assertThrows(IllegalArgumentException.class, () -> bridge.onAppended(message("EFFECTIVE")));
        activity.setProjectId(80L);
        doThrow(new IllegalStateException("JOURNAL_UNAVAILABLE")).when(journal).record(any());
        assertThrows(IllegalStateException.class, () -> bridge.onAppended(message("EFFECTIVE")));
        verifyNoInteractions(outbox);
    }
    private PlatformOutboxAppended message(String change) {
        var event = new ProjectAcceptanceReportChanged(UUID.randomUUID().toString(), 7L, 80L, 55L, 66L, change, 19L);
        return new PlatformOutboxAppended(new PlatformOutboxMessageDTO(event.eventId(), ProjectAcceptanceReportChanged.EVENT_TYPE,
                JsonUtils.toJsonString(event), 0, 7L, LocalDateTime.now()), null);
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
