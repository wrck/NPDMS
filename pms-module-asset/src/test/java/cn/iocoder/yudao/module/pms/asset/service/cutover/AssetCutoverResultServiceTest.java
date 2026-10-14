package cn.iocoder.yudao.module.pms.asset.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.cutover.DeviceCutoverResultDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.DeviceCutoverResultMapper;
import cn.iocoder.yudao.module.pms.cutover.api.result.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.storage.DeviceCutoverResultFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.storage.DeviceCutoverResultFileMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetCutoverResultServiceTest {
    private final DeviceCutoverResultMapper records = mock(DeviceCutoverResultMapper.class);
    private final CutoverResultApi results = mock(CutoverResultApi.class);
    private final DeviceCutoverResultFileMapper files = mock(DeviceCutoverResultFileMapper.class);
    private final AssetCutoverResultService service = new AssetCutoverResultService(records, results, files);
    private final CutoverCompleted event = new CutoverCompleted("CUTOVER_COMPLETED:11:1", 1L, 9L, 10L, 11L, 1L,
            "SUCCESS", "CUTOVER_CLOSURE:11:1", 123L, "test");
    private final CutoverResultApi.Result result = new CutoverResultApi.Result(1L, 9L, 10L, "CUT-10", "割接", 11L, 1L,
            event.resultRef(), 5L, LocalDateTime.of(2026, 10, 12, 10, 0),
            List.of(new CutoverResultApi.Device(2L, "SN-2", 3L)), List.of(), List.of());
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(results.lockAndInspect(any())).thenReturn(result);
        when(records.byTask(any())).thenReturn(List.of());
        when(records.insert(any(DeviceCutoverResultDO.class))).thenAnswer(invocation -> {
            DeviceCutoverResultDO row = invocation.getArgument(0); row.setId(100L); return 1;
        });
        when(files.selectListByParents(any())).thenReturn(List.of());
        when(files.insert(any(DeviceCutoverResultFileDO.class))).thenReturn(1);
        var device = new DeviceDO(); device.setId(2L); device.setTenantId(1L); device.setProjectId(9L);
        device.setSn("SN-2"); device.setProjectAssignmentVersion(3L);
        when(records.selectCurrentDeviceForUpdate(any())).thenReturn(device);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void replayDoesNotDuplicateAssetHistory() {
        var row = storedResult(); when(records.byTask(any())).thenReturn(List.of(row));
        service.accept(event); verify(records, never()).insert(any(DeviceCutoverResultDO.class));
    }
    @Test void movedDeviceKeepsHistoricalFactWithoutClaimingCurrentRefresh() {
        var moved = new DeviceDO(); moved.setId(2L); moved.setProjectId(99L); moved.setProjectAssignmentVersion(4L);
        when(records.selectCurrentDeviceForUpdate(any())).thenReturn(moved);
        service.accept(event);
        var captured = org.mockito.ArgumentCaptor.forClass(DeviceCutoverResultDO.class);
        verify(records).insert(captured.capture());
        assertEquals("HISTORICAL_SCOPE_CHANGED", captured.getValue().getRefreshStatus());
        assertEquals(9L, captured.getValue().getProjectId());
    }
    @Test void conflictingReplayCannotReplaceAssetEvidence() {
        var row = storedResult(); row.setClosureVersion(2L);
        when(records.byTask(any())).thenReturn(List.of(row));
        assertThrows(IllegalArgumentException.class, () -> service.accept(event));
        verify(records, never()).insert(any(DeviceCutoverResultDO.class));
    }
    @Test void foreignTenantEventCannotReadOrWriteOwnerFacts() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.accept(event));
        verifyNoInteractions(results, records, files);
    }

    @Test void incompletePreviousDeviceSetCannotBeReplayed() {
        var twoDevices = new CutoverResultApi.Result(result.tenantId(), result.projectId(), result.taskId(),
                result.taskNo(), result.taskName(), result.closureId(), result.closureVersion(), result.resultRef(),
                result.submittedBy(), result.archivedAt(),
                List.of(result.devices().getFirst(), new CutoverResultApi.Device(3L, "SN-3", 4L)), List.of(), List.of());
        when(results.lockAndInspect(any())).thenReturn(twoDevices);
        when(records.byTask(any())).thenReturn(List.of(storedResult()));
        assertThrows(IllegalArgumentException.class, () -> service.accept(event));
        verify(records, never()).insert(any(DeviceCutoverResultDO.class));
        verify(files, never()).insert(any(DeviceCutoverResultFileDO.class));
    }

    @Test void changedCollectionFileVersionCannotReplaceReplayEvidence() {
        var source = new CutoverResultApi.CollectionFile(2L, "PRE_CHECK", 8L, "PLT-1", 4L, 101L);
        when(results.lockAndInspect(any())).thenReturn(withFiles(List.of(source), List.of()));
        when(records.byTask(any())).thenReturn(List.of(storedResult()));
        var old = DeviceCutoverResultFileDO.builder().tenantId(1L).deviceCutoverResultId(100L)
                .fileSourceCode("COLLECTION").collectionStageCode("PRE_CHECK").actorUserId(8L)
                .platformTaskId("PLT-1").resultVersion(4L).fileVersionId(100L).build();
        when(files.selectListByParents(any())).thenReturn(List.of(old));
        assertThrows(IllegalArgumentException.class, () -> service.accept(event));
        verify(records, never()).insert(any(DeviceCutoverResultDO.class));
        verify(files, never()).insert(any(DeviceCutoverResultFileDO.class));
    }

    @Test void persistsCollectionAndManualFilesUnderGeneratedDeviceRoot() {
        var collection = new CutoverResultApi.CollectionFile(2L, "PRE_CHECK", 8L, "PLT-1", 4L, 101L);
        var manual = new CutoverResultApi.ManualFile(2L, "POST_CHECK", 60L, 2, "cutover-closure:11:manual", "a".repeat(64));
        when(results.lockAndInspect(any())).thenReturn(withFiles(List.of(collection), List.of(manual)));
        service.accept(event);
        var roots = ArgumentCaptor.forClass(DeviceCutoverResultDO.class);
        verify(records).insert(roots.capture());
        assertEquals(100L, roots.getValue().getId());
        assertEquals(2L, roots.getValue().getDeviceId());
        assertNull(roots.getValue().getResultSnapshot());
        var children = ArgumentCaptor.forClass(DeviceCutoverResultFileDO.class);
        verify(files, times(2)).insert(children.capture());
        var collected = children.getAllValues().get(0);
        var uploaded = children.getAllValues().get(1);
        for (var file : children.getAllValues()) {
            assertEquals(1L, file.getTenantId()); assertEquals(100L, file.getDeviceCutoverResultId());
            assertEquals(5L, file.getCreateUserId()); assertNotNull(file.getCreateTime());
        }
        assertEquals("COLLECTION", collected.getFileSourceCode()); assertEquals("PRE_CHECK", collected.getCollectionStageCode());
        assertEquals(8L, collected.getActorUserId()); assertEquals("PLT-1", collected.getPlatformTaskId());
        assertEquals(4L, collected.getResultVersion()); assertEquals(101L, collected.getFileVersionId());
        assertEquals(0, collected.getSortOrder()); assertNull(collected.getFileArtifactId());
        assertNull(collected.getFileVersionNo()); assertNull(collected.getFileReferenceKey()); assertNull(collected.getFileSha256());
        assertEquals("MANUAL", uploaded.getFileSourceCode()); assertEquals("POST_CHECK", uploaded.getCollectionStageCode());
        assertEquals(60L, uploaded.getFileArtifactId()); assertEquals(2, uploaded.getFileVersionNo());
        assertEquals("cutover-closure:11:manual", uploaded.getFileReferenceKey()); assertEquals("a".repeat(64), uploaded.getFileSha256());
        assertEquals(1, uploaded.getSortOrder()); assertNull(uploaded.getActorUserId());
        assertNull(uploaded.getPlatformTaskId()); assertNull(uploaded.getResultVersion()); assertNull(uploaded.getFileVersionId());
    }

    private DeviceCutoverResultDO storedResult() {
        var row = new DeviceCutoverResultDO(); row.setId(100L); row.setTenantId(1L); row.setProjectId(9L);
        row.setTaskId(10L); row.setTaskNo("CUT-10"); row.setTaskName("割接");
        row.setClosureId(11L); row.setClosureVersion(1L); row.setResultRef(event.resultRef());
        row.setSubmitUserId(5L); row.setCutoverAt(result.archivedAt());
        row.setDeviceId(2L); row.setSerialNumber("SN-2"); row.setAssignmentVersion(3L);
        return row;
    }

    private CutoverResultApi.Result withFiles(List<CutoverResultApi.CollectionFile> collection,
                                              List<CutoverResultApi.ManualFile> manual) {
        return new CutoverResultApi.Result(result.tenantId(), result.projectId(), result.taskId(), result.taskNo(),
                result.taskName(), result.closureId(), result.closureVersion(), result.resultRef(), result.submittedBy(),
                result.archivedAt(), result.devices(), collection, manual);
    }
}
