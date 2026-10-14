package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.collection.CutoverCollectionLogDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.CutoverCollectionLogMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverRuntimeChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverRuntimeClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.collection.CutoverCollectionResultFacts;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionBusinessResultReceiver.Result;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverCollectionResultReceiverTest {
    private final CutoverCollectionSources sources = mock(CutoverCollectionSources.class);
    private final CutoverCollectionLogMapper logs = mock(CutoverCollectionLogMapper.class);
    private final CutoverRuntimeChecklistApplicationService checklist = mock(CutoverRuntimeChecklistApplicationService.class);
    private final CutoverRuntimeClosureApplicationService closure = mock(CutoverRuntimeClosureApplicationService.class);
    private final CutoverCollectionResultReceiver receiver = new CutoverCollectionResultReceiver(sources, logs, checklist, closure);
    private final Result result = new Result(1L, "cutover-post-collection", 30L, "CUT", "CUTOVER_CLOSURE", 20L,
            50L, 60L, 70L, "PLT-80", 2L, 90L, "SSH", "SUCCEEDED", null, "display version", "Version");

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var task = new CutoverTaskDO(); task.setId(10L); task.setTenantId(1L); task.setProjectId(20L);
        when(sources.owner(1L, result.entry(), 30L, true)).thenReturn(new CutoverCollectionSources.Owner(task, "CUTOVER_CLOSURE", 3L, false));
        var device = new CutoverTaskDeviceScopeDO(); device.setDeviceId(50L);
        when(sources.taskDevices(1L, 10L)).thenReturn(List.of(device));
        when(logs.insert(any(CutoverCollectionLogDO.class))).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void persistsStructuredReceiptAndRoutesItWithoutWritingLegacyJson() {
        receiver.receive(result);
        var captured = ArgumentCaptor.forClass(CutoverCollectionLogDO.class);
        verify(logs).insert(captured.capture());
        assertEquals(10L, captured.getValue().getTaskId());
        assertEquals(result, CutoverCollectionResultFacts.read(captured.getValue()));
        assertNull(captured.getValue().getResultSnapshot());
        verify(closure).receivePlatformResult(result); verifyNoInteractions(checklist);
    }

    @Test void identicalReplayDoesNotDeliverTwiceAndAlteredFileCannotReplaceEvidence() {
        var stored = new CutoverCollectionLogDO(); stored.setTenantId(1L); stored.setTaskId(10L);
        CutoverCollectionResultFacts.apply(new CutoverCollectionResultFacts.Write(stored, result));
        stored.setResultSnapshot("{\"obsolete\":true}");
        when(logs.findResult(any())).thenReturn(stored);
        receiver.receive(result);
        stored.setFileVersionId(91L);
        assertThrows(CollectionOperationException.class, () -> receiver.receive(result));
        verify(logs, never()).insert(any(CutoverCollectionLogDO.class));
        verifyNoInteractions(closure, checklist);
    }

    @Test void foreignTenantIsRejectedBeforeOwnerReads() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(CollectionOperationException.class, () -> receiver.receive(result));
        verifyNoInteractions(sources, logs, closure, checklist);
    }
}
