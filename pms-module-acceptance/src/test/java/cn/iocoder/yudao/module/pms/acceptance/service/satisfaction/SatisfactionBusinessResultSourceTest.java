package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SatisfactionBusinessResultSourceTest {
    final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    final SatisfactionResultFileMapper files = mock(SatisfactionResultFileMapper.class);
    final FileEvidenceApi evidence = mock(FileEvidenceApi.class);
    final SatisfactionBusinessResultSource source = new SatisfactionBusinessResultSource(tasks, results, files, evidence);
    final SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
    final SatisfactionResultDO result = new SatisfactionResultDO();
    final Query query = new Query(7L, 80L, SatisfactionBusinessResultSource.TYPE, "10", "12");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        task.setId(10L); task.setTenantId(7L); task.setProjectId(80L); task.setResultId(12L); task.setVersion(2L);
        result.setId(12L); result.setTenantId(7L); result.setCollectionTaskId(10L); result.setResponseId(12L);
        result.setResultVersion(1); result.setVersion(0); result.setResultStatus("EFFECTIVE"); result.setPassed(true);
        result.setEffectiveFrom(LocalDateTime.of(2026, 9, 20, 22, 0));
        when(tasks.selectById(10L)).thenReturn(task); when(tasks.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(results.selectById(12L)).thenReturn(result); when(results.selectByIdForUpdate(7L, 12L)).thenReturn(result);
        when(files.selectListByResult(any())).thenReturn(List.of(file("RESULT_DOCUMENT"), file("SIGNATURE")));
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, "VALID", 1, 2, 3));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void exactPassingResultLocksAndValidatesDocumentAndSignature() {
        var found = source.lockAndInspect(query).result();
        assertEquals(Validity.CURRENT, found.validity()); assertEquals("12", found.resultId()); assertEquals("10", found.objectId());
        var queries = org.mockito.ArgumentCaptor.forClass(FileEvidenceApi.Query.class);
        verify(evidence, times(2)).lockAndRevalidate(queries.capture());
        assertEquals(List.of("SATISFACTION_RESULT", "SATISFACTION_RESPONSE"), queries.getAllValues().stream().map(FileEvidenceApi.Query::objectType).toList());
        assertEquals(List.of("SATISFACTION_RESULT_DOCUMENT", "SATISFACTION_SIGNATURE"), queries.getAllValues().stream().map(FileEvidenceApi.Query::purposeCode).toList());
    }
    @Test void failedInvalidatedAndReplacedResultsNeverBorrowANewResult() {
        result.setPassed(false); result.setResultStatus("FAILED");
        assertEquals(Validity.NOT_CURRENT, source.lockAndInspect(query).result().validity());
        result.setPassed(true); result.setResultStatus("INVALIDATED");
        assertEquals(Validity.REVOKED, source.lockAndInspect(query).result().validity());
        result.setResultStatus("EFFECTIVE"); task.setResultId(13L);
        assertEquals(Validity.NOT_CURRENT, source.lockAndInspect(query).result().validity());
        verifyNoInteractions(evidence);
    }
    @Test void unavailableChangedOrMissingSignatureCannotSatisfyDelivery() {
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false, "UNAVAILABLE", 1, 2, 3));
        assertEquals(Status.UNAVAILABLE, source.lockAndInspect(query).status());
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, "VALID", 1, 2, 4));
        assertEquals(Status.UNAVAILABLE, source.lockAndInspect(query).status());
        when(files.selectListByResult(any())).thenReturn(List.of(file("RESULT_DOCUMENT")));
        assertEquals(Status.UNAVAILABLE, source.lockAndInspect(query).status());
    }
    @ParameterizedTest @ValueSource(strings = {"tenant", "project", "task", "result"})
    void mismatchedIdentityCannotLeakOrValidateEvidence(String damage) {
        switch (damage) {
            case "tenant" -> result.setTenantId(8L);
            case "project" -> task.setProjectId(81L);
            case "task" -> task.setId(11L);
            case "result" -> result.setId(13L);
        }
        assertThrows(IllegalArgumentException.class, () -> source.lockAndInspect(query));
        verifyNoInteractions(evidence);
    }
    @Test void absentPinnedVersionCannotFallBackToCurrentAndLocksRequireExactIdentity() {
        assertEquals(Status.NOT_FOUND, source.inspect(new Query(7L, 80L, query.type(), "10", "13")).status());
        assertThrows(IllegalArgumentException.class, () -> source.lockAndInspect(new Query(7L, 80L, query.type(), "10", null)));
        verifyNoInteractions(tasks, files, evidence);
    }
    private SatisfactionResultFileDO file(String role) {
        var row = new SatisfactionResultFileDO(); row.setTenantId(7L); row.setResultId(12L); row.setFileRole(role);
        row.setArtifactId(role.equals("SIGNATURE") ? 21L : 20L); row.setVersionNo(1); row.setReferenceKey(role);
        row.setFileHash("a".repeat(64)); row.setArtifactVersion(1); row.setAvailabilityVersion(2); row.setReferenceVersion(3);
        return row;
    }
}
