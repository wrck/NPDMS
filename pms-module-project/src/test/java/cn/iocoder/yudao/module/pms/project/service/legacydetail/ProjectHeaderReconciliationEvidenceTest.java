package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectHeaderReconciliationEvidenceTest {
    PlatformMigrationEvidenceApi api=mock(PlatformMigrationEvidenceApi.class);
    ProjectLegacyNativeEvidenceService service=new ProjectLegacyNativeEvidenceService(api);
    ProjectLegacyRecordDO row() {
        var row=new ProjectLegacyRecordDO();row.setId(5L);row.setSourceTable("pm_project_header");row.setSourceKey("101");
        row.setDomainCode("project");row.setChecksum("a".repeat(64));return row;
    }
    MigrationPreviewResult preview(MigrationPreviewClassification classification,Long batchId,List<MigrationPreviewTarget> targets) {
        return new MigrationPreviewResult("DPPMS","pm_project_header",List.of(
                new MigrationPreviewItem("101:42:101",classification,batchId==null?null:20L,batchId,"a".repeat(64),targets)),
                batchId==null?1:0,0,0,0,batchId==null?0:1,0);
    }
    void typedSourcePayload() {
        var fact=mock(MigrationSourceRecordFact.class);
        when(fact.sourceRecordId()).thenReturn(20L);
        when(fact.sourcePayloadJson()).thenReturn("{\"legacyRecordId\":5,\"snapshotId\":42,\"values\":{}}");
        when(api.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(fact),null));
    }
    @Test void completedHeaderCorrectionTakesPrecedenceWithoutReadingAnOldReceipt() {
        when(api.previewBatch(any())).thenReturn(preview(MigrationPreviewClassification.MAPPED,10L,List.of(
                new MigrationPreviewTarget("MAPPED","PROJ","ProjectParty","proj_project_party",200L,"PARTY",0,"RESULT"))));
        typedSourcePayload();
        var status=service.statuses(1L,"101",42L,List.of(row())).get("101");
        assertEquals("MAPPED",status.classification());assertEquals("proj_project_party",status.targets().getFirst().targetTable());
        verify(api,times(1)).previewBatch(argThat(q->q.purposeCode().equals("NATIVE_PROJECT_HEADER_V1_42_project")));
    }
    @Test void originalV4ReceiptRemainsReadableUntilHeaderCorrectionExists() {
        when(api.previewBatch(any())).thenAnswer(call->{
            MigrationBatchPreviewCommand request=call.getArgument(0);
            if(request.purposeCode().startsWith("NATIVE_PROJECT_HEADER_V1"))
                return preview(MigrationPreviewClassification.NO_EXISTING_SOURCE,null,List.of());
            assertEquals("NATIVE_V4_42_project",request.purposeCode());
            return preview(MigrationPreviewClassification.MAPPED,10L,List.of());
        });
        typedSourcePayload();
        assertEquals("MAPPED",service.statuses(1L,"101",42L,List.of(row())).get("101").classification());
        verify(api,times(2)).previewBatch(any());
    }
    @Test void correctionProtocolCannotBeReusedForAnyOtherDomain() {
        assertThrows(IllegalArgumentException.class,()->service.reconcile(null,null,"plans",List.of(),Map.of(),"PROJECT_HEADER_V1"));
        assertThrows(IllegalArgumentException.class,()->service.reconcile(null,null,"project",List.of(),Map.of(),"UNKNOWN"));
        verifyNoInteractions(api);
    }
}
