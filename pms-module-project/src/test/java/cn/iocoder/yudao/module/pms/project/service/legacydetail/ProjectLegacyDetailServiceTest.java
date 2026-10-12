package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacySnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacySourceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectLegacyDetailServiceTest {
    @Mock ProjectManualCreationService projects;
    @Mock ProjectContractQueryApi contracts;
    @Mock ProjectLegacyDetailMapper mapper;
    LegacyDetailCatalog catalog = new LegacyDetailCatalog();
    LegacyDetailNormalizer normalizer = new LegacyDetailNormalizer(catalog);
    ProjectLegacyDetailService service;
    ProjectAccessActor actor = new ProjectAccessActor(1L, 1L);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new ProjectLegacyDetailService(projects, contracts, mapper, normalizer, catalog);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    LegacyDetailInput input(String remark, String expected) {
        var counts = new LinkedHashMap<String, Integer>();
        catalog.domains().forEach(d -> counts.put(d.code(), 0)); counts.put("project", 1);
        var values=new LinkedHashMap<String,String>();
        catalog.require("project").fields().forEach(field->values.put(field.key(),null));
        values.put("projectId","101");values.put("projectName",remark);
        return new LegacyDetailInput("synthetic-batch", "101", "SYNTHETIC-CONTRACT", LocalDateTime.of(2026, 10, 8, 10, 0), expected,
                counts, List.of(new LegacyDetailInput.SourceRecord("project", "101", null, null, null, values)));
    }
    ProjectLegacySourceDO source() {
        var source = new ProjectLegacySourceDO(); source.setId(11L); source.setProjectId(99L);
        source.setTenantId(1L); source.setSourceProjectKey("101"); source.setSourceContractNo("SYNTHETIC-CONTRACT");
        source.setCurrentSnapshotId(22L); source.setVersion(1L); return source;
    }
    ProjectLegacySnapshotDO snapshot(String checksum) {
        var snapshot = new ProjectLegacySnapshotDO(); snapshot.setId(22L); snapshot.setTenantId(1L); snapshot.setSourceId(11L);
        snapshot.setChecksum(checksum); snapshot.setSourceReadAt(LocalDateTime.of(2026, 10, 8, 9, 0)); snapshot.setRecordCount(1); return snapshot;
    }
    void contract() { when(contracts.getCurrentContractNumbers(99L)).thenReturn(java.util.Set.of("SYNTHETIC-CONTRACT")); }

    @Test void rejectsUnassociatedContractAndDeniedManagementBeforeWriting() {
        when(contracts.getCurrentContractNumbers(99L)).thenReturn(java.util.Set.of("OTHER"));
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("", null), actor));
        verifyNoInteractions(mapper);
        doThrow(invalidParamException("DENIED")).when(projects).getProjectForManage(99L, actor);
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("", null), actor));
        verifyNoInteractions(mapper);
    }
    @Test void firstImportAppendsEvidenceAndUsesCompareAndSet() {
        contract(); when(mapper.updateHead(any())).thenReturn(1);
        var result = service.ingest(99L, input("history", null), actor);
        assertEquals("IMPORTED", result.decision()); assertEquals(1, result.records());
        verify(mapper).insertSource(any()); verify(mapper).appendSnapshot(any());
        verify(mapper).appendRecord(argThat(row->{
            var values=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(row.getPayloadJson(),
                    new tools.jackson.core.type.TypeReference<Map<String,String>>(){});
            return values.keySet().equals(input("history",null).records().getFirst().values().keySet())
                    &&values.containsKey("customConfig")&&values.get("customConfig")==null&&"history".equals(values.get("projectName"));
        }));
        verify(mapper).updateHead(argThat(q -> q.expectedVersion() == 0L && q.tenantId() == 1L));
        verify(projects).getProjectForManage(99L, actor); verifyNoMoreInteractions(projects);
    }
    @Test void freshFullCaptureAppendsAfterALegacyPartialSnapshotAndThenRemainsUnchanged() {
        contract();var source=source();when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        var fresh=input("history",null);var full=normalizer.normalize(fresh);
        // Historical encoding omitted NULL content and had no full-field coverage marker.
        var legacyDigest=new java.util.TreeMap<String,Object>();
        legacyDigest.put("sourceProjectKey",fresh.sourceProjectKey());legacyDigest.put("sourceContractNo",fresh.sourceContractNo());
        legacyDigest.put("counts",full.counts());legacyDigest.put("records",full.records().stream().map(LegacyDetailNormalizer.Record::checksum).toList());
        String legacyChecksum=LegacyDetailNormalizer.sha(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(legacyDigest));
        assertNotEquals(legacyChecksum,full.checksum());
        String legacyPayload=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(fresh.records().getFirst().values());
        assertFalse(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(legacyPayload,
                new tools.jackson.core.type.TypeReference<Map<String,String>>(){}).containsKey("customConfig"));
        var historical=snapshot(legacyChecksum);historical.setBatchKey("legacy-partial-batch");
        historical.setDomainCountsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(full.counts()));
        var head=new java.util.concurrent.atomic.AtomicReference<ProjectLegacySnapshotDO>(historical);
        when(mapper.selectSnapshot(any())).thenAnswer(call->head.get());
        doAnswer(call->{head.set(call.getArgument(0));return 1;}).when(mapper).appendSnapshot(any());
        when(mapper.updateHead(any())).thenReturn(1);
        var imported=service.ingest(99L,input("history",legacyChecksum),actor);
        assertEquals("IMPORTED",imported.decision());assertEquals(full.checksum(),imported.checksum());
        assertNotEquals(historical.getId(),imported.snapshotId());assertEquals(legacyChecksum,historical.getChecksum());
        var saved=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO.class);
        verify(mapper).appendRecord(saved.capture());
        var values=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(saved.getValue().getPayloadJson(),
                new tools.jackson.core.type.TypeReference<Map<String,String>>(){});
        assertEquals(fresh.records().getFirst().values(),values);assertTrue(values.containsKey("customConfig"));assertNull(values.get("customConfig"));
        source.setCurrentSnapshotId(imported.snapshotId());source.setVersion(source.getVersion()+1);
        var second=new LegacyDetailInput("second-full-batch",fresh.sourceProjectKey(),fresh.sourceContractNo(),
                fresh.sourceReadAt(),imported.checksum(),fresh.domainCounts(),fresh.records());
        var unchanged=service.ingest(99L,second,actor);
        assertEquals("UNCHANGED",unchanged.decision());assertEquals(imported.snapshotId(),unchanged.snapshotId());
        verify(mapper,times(1)).appendSnapshot(any());verify(mapper,times(1)).appendRecord(any());verify(mapper,times(1)).updateHead(any());
    }
    @Test void refusesBindingTheSameLegacyProjectToAnotherRuntimeProject() {
        contract(); var source = source(); source.setProjectId(100L);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("", null), actor));
        verify(mapper, never()).appendSnapshot(any()); verify(mapper, never()).updateHead(any());
    }
    @Test void exactBatchReplayDoesNotAdvanceHeadAndConflictingPayloadIsRejected() {
        contract(); when(mapper.selectSourceForUpdate(any())).thenReturn(source());
        var prior = snapshot(normalizer.normalize(input("history", null)).checksum());
        when(mapper.selectBatch(any())).thenReturn(prior);
        assertEquals("REPLAYED", service.ingest(99L, input("history", null), actor).decision());
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("changed", null), actor));
        verify(mapper, never()).appendRecord(any()); verify(mapper, never()).updateHead(any());
    }
    @Test void unchangedSnapshotDoesNotDuplicateHistory() {
        contract(); when(mapper.selectSourceForUpdate(any())).thenReturn(source());
        when(mapper.selectSnapshot(any())).thenReturn(snapshot(normalizer.normalize(input("history", null)).checksum()));
        assertEquals("UNCHANGED", service.ingest(99L, input("history", null), actor).decision());
        verify(mapper, never()).appendSnapshot(any()); verify(mapper, never()).updateHead(any());
    }
    @Test void changedHistoryRequiresExpectedHeadAndCannotMoveBackwardInTime() {
        contract(); when(mapper.selectSourceForUpdate(any())).thenReturn(source());
        var head = snapshot(normalizer.normalize(input("history", null)).checksum());
        when(mapper.selectSnapshot(any())).thenReturn(head);
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("changed", null), actor));
        head.setSourceReadAt(LocalDateTime.of(2026, 10, 8, 11, 0));
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("changed", head.getChecksum()), actor));
        verify(mapper, never()).appendSnapshot(any());
    }
    @Test void changedHistoryAppendsNewSnapshotAndCasFailureIsReported() {
        contract(); when(mapper.selectSourceForUpdate(any())).thenReturn(source());
        var head = snapshot(normalizer.normalize(input("history", null)).checksum());
        when(mapper.selectSnapshot(any())).thenReturn(head); when(mapper.updateHead(any())).thenReturn(1, 0);
        assertEquals("IMPORTED", service.ingest(99L, input("changed", head.getChecksum()), actor).decision());
        assertThrows(ServiceException.class, () -> service.ingest(99L, input("changed", head.getChecksum()), actor));
        // Transactional rollback is separately exercised against real MySQL/API; this unit verifies rejection.
    }
    @Test void otherTenantAndDeniedReadCannotReachPersistence() {
        assertThrows(ServiceException.class, () -> service.sources(99L, new ProjectAccessActor(2L, 1L)));
        doThrow(invalidParamException("DENIED")).when(projects).getProject(99L, actor);
        assertThrows(ServiceException.class, () -> service.sources(99L, actor)); verifyNoInteractions(mapper);
    }
    @Test void rejectsForeignSourcesAndSnapshotsAndUnboundedPage() {
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source()));
        assertThrows(ServiceException.class, () -> service.records(99L, 12L, null, "project", null, null, null, 1, 20, actor));
        var foreign = snapshot("synthetic"); foreign.setSourceId(12L);
        when(mapper.selectSnapshot(any())).thenReturn(foreign);
        assertThrows(ServiceException.class, () -> service.records(99L, 11L, 22L, "project", null, null, null, 1, 20, actor));
        assertThrows(ServiceException.class, () -> service.records(99L, 11L, null, "project", null, null, null, Integer.MAX_VALUE, 100, actor));
        verify(mapper, never()).selectRecords(any());
    }
}
