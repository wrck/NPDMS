package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.service.operation.SatisfactionOperationResultBridge;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event.IndependentSatisfactionResultChanged;
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

class SatisfactionOperationResultBridgeTest {
    final ProjectBusinessResultRecordingApi journal = mock(ProjectBusinessResultRecordingApi.class);
    final PlatformBusinessEventApi outbox = mock(PlatformBusinessEventApi.class);
    final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    final SatisfactionOperationResultBridge bridge = new SatisfactionOperationResultBridge(of(journal), of(outbox), tasks, results);
    final SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
    final SatisfactionResultDO result = new SatisfactionResultDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        task.setId(10L); task.setTenantId(7L); task.setProjectId(80L); task.setOriginKind("DIRECT");
        result.setId(12L); result.setTenantId(7L); result.setCollectionTaskId(10L); result.setVersion(0);
        when(tasks.selectById(10L)).thenReturn(task); when(results.selectById(12L)).thenReturn(result);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void decisionAndInvalidationPublishDurableExactResultsWithoutTaskIdentity() {
        bridge.onAppended(message("RECORDED")); result.setVersion(1); bridge.onAppended(message("INVALIDATED"));
        var captured = org.mockito.ArgumentCaptor.forClass(BusinessOperationResultEvent.class);
        verify(journal, times(2)).record(captured.capture());
        assertTrue(captured.getAllValues().stream().allMatch(e -> e.projectId().equals(80L) && e.objectId().equals("10") && e.revisionId().equals("12")));
        assertEquals(java.util.List.of("SATISFACTION_RESULT_RECORDED", "SATISFACTION_RESULT_INVALIDATED"), captured.getAllValues().stream().map(BusinessOperationResultEvent::resultCode).toList());
        verify(outbox, times(2)).append(eq("SATISFACTION"), eq("10"), any());
    }
    @Test void projectMismatchAndFailedJournalNeverPublishACompletionNotification() {
        task.setProjectId(81L);
        assertThrows(IllegalArgumentException.class, () -> bridge.onAppended(message("RECORDED")));
        task.setProjectId(80L); doThrow(new IllegalStateException("JOURNAL_UNAVAILABLE")).when(journal).record(any());
        assertThrows(IllegalStateException.class, () -> bridge.onAppended(message("RECORDED")));
        verifyNoInteractions(outbox);
    }
    private PlatformOutboxAppended message(String change) {
        var event = new IndependentSatisfactionResultChanged(UUID.randomUUID().toString(), 7L, 80L, 10L, 12L, 19L, change);
        return new PlatformOutboxAppended(new PlatformOutboxMessageDTO(event.eventId(), IndependentSatisfactionResultChanged.TYPE,
                JsonUtils.toJsonString(event), 0, 7L, LocalDateTime.now()), null);
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
