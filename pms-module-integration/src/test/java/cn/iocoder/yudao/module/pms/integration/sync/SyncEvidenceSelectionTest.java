package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncEvidenceSelectionTest {
    private final Map<String,Object> raw=Map.of("id",1,"name","原始名称","unmapped","保留字段");
    private SyncDefinition definition(String policy) {
        return SyncDefinition.builder().adapter("TABLE_MAPPING").readStrategy("STREAMING_CURSOR").evidencePolicy(policy)
                .sources(List.of(SyncDefinition.Source.builder().object("ROW").sourceObject("source_table").sourceKey("id").build())).build();
    }
    private Map<String,Object> fields(Map<String,Object> source) {
        var fields=new LinkedHashMap<>(source);fields.put("_syncSourcePayload",source);
        fields.put("_targets",List.of(Map.of("table","target_table","id",22L)));
        fields.put("target.name",source.get("name"));return fields;
    }
    private SyncBindingDO binding(Map<String,Object> prior,boolean compact) {
        return new SyncBindingDO().setObjectKey("ROW").setSourceObject("source_table").setSourceKey("1")
                .setTargetId(22L).setTargetShared(true).setLastRunId(100L).setFieldsJson(SyncBindingSnapshots.encode(prior,compact));
    }
    private SyncEvidenceSelection.Selected select(String policy,Map<String,Object> source,String action,
                                                   Map<String,Object> after,SyncBindingDO binding,boolean preview) {
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.of(2026,10,9,1,0),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(source))),100);
        return SyncEvidenceSelection.select(definition(policy),snapshot,
                List.of(new DataSyncAdapter.Change("ROW","1",22L,action,Map.of(),after,null)),
                binding==null?List.of():List.of(binding),preview);
    }
    @Test void reusesOnlyVerifiedUnchangedCompleteSourceAndTarget() {
        for(boolean compact:List.of(false,true)) {
            var result=select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),binding(fields(raw),compact),false);
            assertTrue(result.snapshot().objects().isEmpty());assertEquals(1,result.reused().size());
            assertEquals(LocalDateTime.of(2026,10,9,1,0),result.snapshot().upper());
        }
    }
    @Test void compressedSnapshotsStillDetectSourceAndDerivedChanges() {
        var source=new LinkedHashMap<>(raw);source.put("largeFact","完整来源事实".repeat(500));
        var after=fields(source);var prior=binding(after,true);
        assertEquals("NPDMS_SYNC_BINDING_Z1",JsonUtils.parseMap(prior.getFieldsJson()).get("$format"));
        assertEquals(1,select("REUSE_UNCHANGED",source,"UNCHANGED",after,prior,false).reused().size());
        var changedSource=new LinkedHashMap<>(source);changedSource.put("unmapped","changed");
        assertTrue(select("REUSE_UNCHANGED",changedSource,"UNCHANGED",fields(changedSource),prior,false).reused().isEmpty());
        var changedDerived=new LinkedHashMap<>(after);changedDerived.put("target.name","changed derived fact");
        assertTrue(select("REUSE_UNCHANGED",source,"UNCHANGED",changedDerived,prior,false).reused().isEmpty());
    }
    @Test void legacyExclusiveBindingNeedsFreshEvidenceAndOwnershipMetadataUpgrade() {
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),
                binding(fields(raw),false).setTargetShared(false),false).reused().isEmpty());
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),
                binding(fields(raw),false).setTargetShared(null),false).reused().isEmpty());
    }
    @Test void fullPolicyAndLegacyDefinitionsKeepEverySourceRecord() {
        for(String policy:Arrays.asList(null,"","FULL")) {
            var result=select(policy,raw,"UNCHANGED",fields(raw),binding(fields(raw),false),false);
            assertEquals(1,result.snapshot().objects().getFirst().rows().size());assertTrue(result.reused().isEmpty());
        }
    }
    @Test void sourceChangeIncludingUnmappedFieldsStillCreatesEvidence() {
        var changed=new LinkedHashMap<>(raw);changed.put("unmapped","新来源原文");
        assertEquals(1,select("REUSE_UNCHANGED",changed,"UNCHANGED",fields(changed),binding(fields(raw),false),false)
                .snapshot().objects().getFirst().rows().size());
    }
    @Test void targetDriftAndExceptionalRowsCannotUseSourceEqualityToSkipValidation() {
        for(String action:List.of("CREATED","UPDATED","ISSUE","CONFLICT","SKIPPED"))
            assertTrue(select("REUSE_UNCHANGED",raw,action,fields(raw),binding(fields(raw),false),false).reused().isEmpty());
        var changed=fields(raw);changed.put("target.name","新关联结果");
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",changed,binding(fields(raw),false),false).reused().isEmpty());
    }
    @Test void unprovenLegacyBindingsAndChangedIdentitiesRequireNewEvidence() {
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),null,false).reused().isEmpty());
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),binding(raw,false),false).reused().isEmpty());
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),binding(fields(raw),false).setLastRunId(null),false).reused().isEmpty());
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),binding(fields(raw),false).setTargetId(99L),false).reused().isEmpty());
        assertTrue(select("REUSE_UNCHANGED",raw,"UNCHANGED",fields(raw),binding(fields(raw),false).setSourceObject("other"),false).reused().isEmpty());
    }
    @Test void previewMarkerDoesNotManufactureSourceChanges() {
        var preview=fields(raw);preview.put("_previewVirtualIds",true);
        assertEquals(1,select("REUSE_UNCHANGED",raw,"UNCHANGED",preview,binding(fields(raw),false),true).reused().size());
        assertTrue(preview.containsKey("_previewVirtualIds"));
    }
    @Test void compactCodecIsLosslessWithNullsAndDerivedOverridesAndStoresRawOnce() {
        var source=new LinkedHashMap<String,Object>();source.put("id",1);source.put("name","源");
        source.put("optional",null);source.put("payload","x".repeat(4000));
        var fields=fields(source);fields.put("name","派生覆盖");fields.put("derivedNull",null);
        var compact=SyncBindingSnapshots.encode(fields,true);
        assertTrue(compact.length()<JsonUtils.toJsonString(fields).length()-3000);
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(compact)));
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(JsonUtils.toJsonString(fields))));
    }
    @Test void nullDerivedOverrideCannotResurrectANonNullSourceValue() {
        var fields=fields(raw);fields.put("name",null);
        String encoded=SyncBindingSnapshots.encode(fields,true);
        assertEquals(JsonUtils.toJsonString(fields),encoded);
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(encoded)));
        assertNull(SyncBindingSnapshots.decode(encoded).get("name"));
    }
    @Test void incompleteSourceProjectionFallsBackToLosslessLegacyShape() {
        var fields=fields(raw);fields.remove("unmapped");
        assertEquals(JsonUtils.toJsonString(fields),SyncBindingSnapshots.encode(fields,true));
    }
    private SyncEvidenceSelection.Selected selectJson(Map<String,Object> source,String priorJson,String currentJson,String priorText,String currentText) {
        var d=definition("REUSE_UNCHANGED");
        d.sources().getFirst().targets(List.of(new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep(
                "target","target_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(SyncDefinition.Mapping.builder().target("payload").source("$ROW").conversion("JSON").build()),List.of())));
        var prior=fields(raw);prior.put("target.payload",priorJson);prior.put("target.text",priorText);
        var after=fields(source);after.put("target.payload",currentJson);after.put("target.text",currentText);
        return SyncEvidenceSelection.select(d,new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(source))),100),
                List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),
                List.of(binding(prior,true)),false);
    }
    @Test void mysqlJsonFormattingAndObjectKeyOrderDoNotManufactureNewEvidence() {
        assertEquals(1,selectJson(raw,"{\"b\":2,\"a\":{\"y\":4,\"x\":3}}",
                "{ \"a\": {\"x\": 3, \"y\": 4}, \"b\": 2 }","text","text").reused().size());
    }
    @Test void jsonValueOrArrayOrderChangesAndInvalidLegacyJsonStillNeedEvidence() {
        assertTrue(selectJson(raw,"{\"a\":[1,2]}","{\"a\":[2,1]}","text","text").reused().isEmpty());
        assertTrue(selectJson(raw,"{\"a\":1}","{\"a\":2}","text","text").reused().isEmpty());
        assertTrue(selectJson(raw,"not-json","{}","text","text").reused().isEmpty());
    }
    @Test void jsonNormalizationNeverHidesOriginalSourceOrOrdinaryStringChanges() {
        var changed=new LinkedHashMap<>(raw);changed.put("unmapped","changed");
        assertTrue(selectJson(changed,"{\"a\":1}","{\"a\": 1}","text","text").reused().isEmpty());
        assertTrue(selectJson(raw,"{\"a\":1}","{\"a\": 1}","{\"a\":1}","{\"a\": 1}").reused().isEmpty());
    }

    private SyncEvidenceSelection.Selected selectTemporal(String conversion,Object priorValue,Object currentValue) {
        var d=definition("REUSE_UNCHANGED");
        d.sources().getFirst().targets(List.of(new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep(
                "target","target_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(SyncDefinition.Mapping.builder().target("date").source("date").conversion(conversion).build()),List.of())));
        var prior=fields(raw);prior.put("target.date",priorValue);
        var after=fields(raw);after.put("target.date",currentValue);
        System.out.println("TEMPORAL_FACT_REPRESENTATIONS="+JsonUtils.toJsonString(Map.of("conversion",conversion,"prior",priorValue,"current",currentValue)));
        return SyncEvidenceSelection.select(d,new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(raw))),100),
                List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),
                List.of(binding(prior,true)),false);
    }
    @Test void jdbcDateAndMappedLocalDateMustNotManufactureEvidence() {
        var date=java.time.LocalDate.of(2026,10,1);
        assertEquals(1,selectTemporal("DATE",date,java.sql.Date.valueOf(date)).reused().size());
    }
    @Test void jdbcTimestampAndMappedLocalDateTimeMustNotManufactureEvidence() {
        var date=java.time.LocalDateTime.of(2026,10,1,0,0);
        assertEquals(1,selectTemporal("DATETIME",date,java.sql.Timestamp.valueOf(date)).reused().size());
    }

    @Test void temporalNormalizationDoesNotHideFactChangesOrUndeclaredFields() {
        var day=java.time.LocalDate.of(2026,10,1);var time=day.atStartOfDay();
        assertTrue(selectTemporal("DATE",day,java.sql.Date.valueOf(day.plusDays(1))).reused().isEmpty());
        assertTrue(selectTemporal("DATETIME",time,java.sql.Timestamp.valueOf(time.plusSeconds(1))).reused().isEmpty());
        assertTrue(selectTemporal("DIRECT",day,java.sql.Date.valueOf(day)).reused().isEmpty());
        assertTrue(selectTemporal("DATETIME",time,java.sql.Timestamp.valueOf(time.plusNanos(1))).reused().isEmpty());
    }
    @Test void alreadyMatchingJdbcLegacyDatesRemainReusable() {
        var day=java.sql.Date.valueOf("2026-10-01");
        assertEquals(1,selectTemporal("DATE",day,day).reused().size());
        var time=java.sql.Timestamp.valueOf("2026-10-01 00:00:00");
        assertEquals(1,selectTemporal("DATETIME",time,time).reused().size());
    }

    @Test void normalizeDeclaredFactsBeforeSerializingTheWholeSnapshot() {
        var d=definition("REUSE_UNCHANGED");
        d.sources().getFirst().targets(List.of(new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep(
                "target","target_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(SyncDefinition.Mapping.builder().target("payload").source("$ROW").conversion("JSON").build()),List.of())));
        var prior=fields(raw);prior.put("target.payload","{\"b\":2,\"a\":1}");
        var after=fields(raw);after.put("target.payload","{ \"a\": 1, \"b\": 2 }");
        var saved=binding(prior,true);
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(raw))),100);
        var changes=List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null));
        try(var json=org.mockito.Mockito.mockStatic(JsonUtils.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            assertEquals(1,SyncEvidenceSelection.select(d,snapshot,changes,List.of(saved),false).reused().size());
            json.verify(()->JsonUtils.toJsonString(org.mockito.ArgumentMatchers.argThat(value ->
                    value instanceof Map<?,?> map && map.get("target.payload") instanceof tools.jackson.databind.JsonNode
                            && !map.containsKey("_syncSourcePayload"))),org.mockito.Mockito.times(2));
        }
    }
    @Test void invalidJsonCannotGainEvidenceReuseFromUnrelatedTemporalNormalization() {
        var d=definition("REUSE_UNCHANGED");
        d.sources().getFirst().targets(List.of(new cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep(
                "target","target_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(SyncDefinition.Mapping.builder().target("payload").source("$ROW").conversion("JSON").build(),
                        SyncDefinition.Mapping.builder().target("date").source("date").conversion("DATETIME").build()),List.of())));
        var date=LocalDateTime.of(2026,10,1,0,0);
        var prior=fields(raw);prior.put("target.payload","invalid-json");prior.put("target.date",date);
        var after=fields(raw);after.put("target.payload","invalid-json");after.put("target.date",java.sql.Timestamp.valueOf(date));
        var saved=binding(prior,true);
        assertTrue(SyncEvidenceSelection.select(d,new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(raw))),100),
                List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),
                List.of(saved),false).reused().isEmpty());
    }
    @Test void equalInvalidLegacyJsonStillUsesTheEstablishedExactFactRule() {
        assertEquals(1,selectJson(raw,"invalid-json","invalid-json","text","text").reused().size());
        assertTrue(selectJson(raw,"invalid-json","different-invalid-json","text","text").reused().isEmpty());
    }


    private SyncEvidenceSelection.Selected selectWithTargets(Map<String,Object> after) {
        var d=definition("REUSE_UNCHANGED");d.sources().getFirst().targets(List.of());
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.now(),
            List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(raw))),100);
        return SyncEvidenceSelection.select(d,snapshot,
            List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),
            List.of(binding(fields(raw),true)),false);
    }
    @Test void verifiedSourcePayloadIsNotSerializedTwice() {
        var saved=binding(fields(raw),true);
        var d=definition("REUSE_UNCHANGED");d.sources().getFirst().targets(List.of());
        var after=fields(raw);after.put("_syncSourcePayload",new LinkedHashMap<>(raw));
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.now(),
            List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(raw))),100);
        try(var json=org.mockito.Mockito.mockStatic(JsonUtils.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            assertEquals(1,SyncEvidenceSelection.select(d,snapshot,
                List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),List.of(saved),false).reused().size());
            json.verify(()->JsonUtils.toJsonString(org.mockito.ArgumentMatchers.argThat(value ->
                value instanceof Map<?,?> map && map.containsKey("_syncSourcePayload"))),org.mockito.Mockito.never());
        }
    }
    @Test void changedOrMissingAfterPayloadCannotUseTheSourceShortcut() {
        var changed=fields(raw);var payload=new LinkedHashMap<>(raw);payload.put("unmapped","tampered");
        changed.put("_syncSourcePayload",payload);assertTrue(selectWithTargets(changed).reused().isEmpty());
        var missing=fields(raw);missing.remove("_syncSourcePayload");assertTrue(selectWithTargets(missing).reused().isEmpty());
    }
    @Test void verifiedPayloadStillChecksFlattenedSourceAndTargetFacts() {
        var changedSourceField=fields(raw);changedSourceField.put("name","tampered flattened fact");
        assertTrue(selectWithTargets(changedSourceField).reused().isEmpty());
        var changedTarget=fields(raw);changedTarget.put("target.name","different target fact");
        assertTrue(selectWithTargets(changedTarget).reused().isEmpty());
    }
    @Test void merelyJsonEquivalentAfterPayloadRetainsTheFullComparisonFallback() {
        var after=fields(raw);var payload=new LinkedHashMap<>(raw);payload.put("id",1L);
        assertNotEquals(raw,payload);after.put("_syncSourcePayload",payload);
        assertEquals(1,selectWithTargets(after).reused().size());
    }
}
