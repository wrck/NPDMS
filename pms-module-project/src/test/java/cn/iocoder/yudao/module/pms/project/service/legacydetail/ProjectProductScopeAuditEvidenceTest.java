package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Baseline;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProductScopeAuditEvidenceTest {
    PlatformMigrationEvidenceApi api=mock(PlatformMigrationEvidenceApi.class);
    ProjectLegacyNativeEvidenceService service=new ProjectLegacyNativeEvidenceService(api);
    Baseline baseline=new Baseline(9001L,null,LocalDateTime.of(2020,1,2,3,4,5,321000000),null,LocalDateTime.of(2020,1,2,3,4,5,321000000));
    ProjectLegacyRecordDO row() {
        var row=new ProjectLegacyRecordDO();row.setId(5L);row.setSourceTable("pm_project_product_line");row.setSourceKey("301");
        row.setDomainCode("products");row.setChecksum("a".repeat(64));return row;
    }
    MigrationPreviewResult preview(Long batchId) {
        return new MigrationPreviewResult("DPPMS","pm_project_product_line",List.of(new MigrationPreviewItem("101:42:301",
                batchId==null?MigrationPreviewClassification.NO_EXISTING_SOURCE:MigrationPreviewClassification.MAPPED,
                batchId==null?null:20L,batchId,"a".repeat(64),batchId==null?List.of():List.of(
                new MigrationPreviewTarget("MAPPED","COM","DeliveryScope","com_delivery_scope",9001L,"BUSINESS_ENTITY",0,"RESULT")))),
                batchId==null?1:0,0,0,0,batchId==null?0:1,0);
    }
    void payload(Long snapshotId,Baseline audit) {
        var fact=mock(MigrationSourceRecordFact.class);when(fact.sourceRecordId()).thenReturn(20L);
        when(fact.sourcePayloadJson()).thenReturn(JsonUtils.toJsonString(new ProjectLegacyNativeEvidenceService.Payload(5L,snapshotId,null,Map.of(),audit)));
        when(api.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(fact),null));
    }
    @Test void approvedAuditRoundTripKeepsNullActorsAndHistoricalMilliseconds() {
        when(api.previewBatch(any())).thenReturn(preview(10L));payload(42L,baseline);
        var status=service.statuses(1L,"101",42L,List.of(row())).get("301");
        assertEquals("MAPPED",status.classification());assertEquals(baseline,status.scopeBaseline());
        verify(api,times(1)).previewBatch(argThat(q->q.purposeCode().equals("NATIVE_PRODUCT_SCOPE_V4_42_products")));
    }
    @Test void originalV1EvidenceRemainsReadableButDoesNotInventAudit() {
        when(api.previewBatch(any())).thenAnswer(call->{
            MigrationBatchPreviewCommand q=call.getArgument(0);
            if(q.purposeCode().startsWith("NATIVE_PRODUCT_SCOPE_V4") || q.purposeCode().startsWith("NATIVE_PRODUCT_SCOPE_V3") || q.purposeCode().startsWith("NATIVE_PRODUCT_SCOPE_V2"))return preview(null);
            assertEquals("NATIVE_PRODUCT_SCOPE_V1_42_products",q.purposeCode());return preview(10L);
        });payload(42L,null);
        var status=service.statuses(1L,"101",42L,List.of(row())).get("301");
        assertEquals("MAPPED",status.classification());assertNull(status.scopeBaseline());verify(api,times(4)).previewBatch(any());
    }
    @Test void originalV3AuditRemainsReadableBeforeCompanionDetailsAreReconciled() {
        when(api.previewBatch(any())).thenAnswer(call->{
            MigrationBatchPreviewCommand q=call.getArgument(0);
            if(q.purposeCode().startsWith("NATIVE_PRODUCT_SCOPE_V4"))return preview(null);
            assertEquals("NATIVE_PRODUCT_SCOPE_V3_42_products",q.purposeCode());return preview(10L);
        });payload(42L,baseline);
        var status=service.statuses(1L,"101",42L,List.of(row())).get("301");
        assertEquals("MAPPED",status.classification());assertEquals(baseline,status.scopeBaseline());
        verify(api,times(2)).previewBatch(any());
    }
    @Test void stagedIsoAuditDatesMustNotBeCoercedToEpochZero() {
        when(api.previewBatch(any())).thenReturn(preview(10L));
        var fact=mock(MigrationSourceRecordFact.class);when(fact.sourceRecordId()).thenReturn(20L);
        when(fact.sourcePayloadJson()).thenReturn("""
                {"legacyRecordId":5,"snapshotId":42,"values":{},"scopeBaseline":{
                  "id":9001,"creator":null,"createTime":"2020-01-02T03:04:05.321",
                  "updater":null,"updateTime":"2020-01-02T03:04:05.321"}}
                """);
        when(api.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(fact),null));
        assertEquals(baseline,service.statuses(1L,"101",42L,List.of(row())).get("301").scopeBaseline());
    }
    @Test void invalidIsoAuditDateMustBlockInsteadOfBecomingEpochZero() {
        when(api.previewBatch(any())).thenReturn(preview(10L));
        var fact=mock(MigrationSourceRecordFact.class);when(fact.sourceRecordId()).thenReturn(20L);
        when(fact.sourcePayloadJson()).thenReturn("""
                {"legacyRecordId":5,"snapshotId":42,"values":{},"scopeBaseline":{
                  "id":9001,"createTime":"invalid","updateTime":"2020-01-02T03:04:05.321"}}
                """);
        when(api.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(fact),null));
        assertThrows(IllegalArgumentException.class,()->service.statuses(1L,"101",42L,List.of(row())));
    }
    @Test void installationEvidenceKeepsEveryOriginalNullAndExactAuditGuard() {
        var original=row();original.setPayloadJson("{\"id\":\"301\",\"updateBy\":null,\"createTime\":\"2020-01-02 03:04:05.321\"}");
        var audit=new cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Baseline(
                700L,"{\"updater\":null,\"create_time\":\"2020-01-02T03:04:05.321\"}",null,null);
        var payload=JsonUtils.parseObject(ProjectLegacyNativeEvidenceService.installationPayload(original,42L,null,audit),Map.class);
        var values=(Map<?,?>)payload.get("values");assertEquals(3,values.size());assertTrue(values.containsKey("updateBy"));assertNull(values.get("updateBy"));
        assertEquals(audit.formalFieldsJson(),((Map<?,?>)payload.get("installationBaseline")).get("formalFieldsJson"));
    }
    @Test void otherSnapshotCannotSupplyAuditOrCurrentScopeBinding() {
        when(api.previewBatch(any())).thenReturn(preview(10L));payload(43L,baseline);
        var status=service.statuses(1L,"101",42L,List.of(row())).get("301");
        assertEquals("NOT_MIGRATED",status.classification());assertNull(status.scopeBaseline());assertTrue(status.targets().isEmpty());
    }
}
