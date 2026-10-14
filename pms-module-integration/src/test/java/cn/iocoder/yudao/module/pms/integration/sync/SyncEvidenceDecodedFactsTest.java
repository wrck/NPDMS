package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class SyncEvidenceDecodedFactsTest {
    private final Map<String,Object> raw=Map.of("id",1,"name","原始名称","unmapped","保留字段");
    private SyncDefinition definition(String adapter,String policy) {
        return SyncDefinition.builder().adapter(adapter).readStrategy("STREAMING_CURSOR").evidencePolicy(policy)
            .sources(List.of(SyncDefinition.Source.builder().object("ROW").sourceObject("source_table").sourceKey("id").build())).build();
    }
    private Map<String,Object> fields(Map<String,Object> source) {
        var out=new LinkedHashMap<>(source);out.put("_syncSourcePayload",source);
        out.put("_targets",List.of(Map.of("table","target_table","id",22L)));out.put("target.name",source.get("name"));return out;
    }
    private SyncBindingDO binding(boolean compact) {
        return new SyncBindingDO().setObjectKey("ROW").setSourceObject("source_table").setSourceKey("1")
            .setTargetId(22L).setTargetShared(true).setLastRunId(100L).setFieldsJson(SyncBindingSnapshots.encode(fields(raw),compact));
    }
    private SyncEvidenceSelection.Selected select(SyncDefinition d,Map<String,Object> row,Map<String,Object> after,
                SyncBindingDO binding,boolean preview,List<DataSyncAdapter.Binding> decoded) {
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.of(2026,10,9,1,0),
            List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(row))),100);
        return SyncEvidenceSelection.select(d,snapshot,List.of(new DataSyncAdapter.Change("ROW","1",22L,"UNCHANGED",Map.of(),after,null)),
            List.of(binding),preview,decoded);
    }
    @Test void builtInTableSelectionDoesNotDecodeAnAlreadyDecodedChunkAgain() {
        for(boolean compact:List.of(false,true)) {
            var b=binding(compact);var cached=new DataSyncAdapter.Binding("ROW","1",22L,SyncBindingSnapshots.decode(b.getFieldsJson()));
            String before=JsonUtils.toJsonString(cached.lastFields());
            try(var mock=Mockito.mockStatic(SyncBindingSnapshots.class,Mockito.CALLS_REAL_METHODS)) {
                var result=select(definition("TABLE_MAPPING","REUSE_UNCHANGED"),raw,fields(raw),b,false,List.of(cached));
                assertEquals(1,result.reused().size());assertTrue(result.snapshot().objects().isEmpty());
                mock.verify(()->SyncBindingSnapshots.decode(b.getFieldsJson()),Mockito.never());
            }
            assertEquals(before,JsonUtils.toJsonString(cached.lastFields()));
        }
    }
    @Test void otherAdaptersKeepIndependentDecodingEvenWithSuppliedMutableFacts() {
        var b=binding(true);var modified=fields(raw);modified.put("target.name","mutated by adapter");
        var cached=new DataSyncAdapter.Binding("ROW","1",22L,modified);
        try(var mock=Mockito.mockStatic(SyncBindingSnapshots.class,Mockito.CALLS_REAL_METHODS)) {
            assertEquals(1,select(definition("CUSTOM","REUSE_UNCHANGED"),raw,fields(raw),b,false,List.of(cached)).reused().size());
            mock.verify(()->SyncBindingSnapshots.decode(b.getFieldsJson()),Mockito.times(1));
        }
    }
    @Test void missingOrDifferentBindingIdentityFallsBackToOriginalDecode() {
        var b=binding(true);
        List<List<DataSyncAdapter.Binding>> cases=List.of(List.of(),List.of(new DataSyncAdapter.Binding("ROW","2",22L,Map.of())),
            List.of(new DataSyncAdapter.Binding("OTHER","1",22L,Map.of())),List.of(new DataSyncAdapter.Binding("ROW","1",23L,Map.of())),
            List.of(new DataSyncAdapter.Binding("ROW","1",22L,null)));
        for(var supplied:cases)try(var mock=Mockito.mockStatic(SyncBindingSnapshots.class,Mockito.CALLS_REAL_METHODS)) {
            assertEquals(1,select(definition("TABLE_MAPPING","REUSE_UNCHANGED"),raw,fields(raw),b,false,supplied).reused().size());
            mock.verify(()->SyncBindingSnapshots.decode(b.getFieldsJson()),Mockito.times(1));
        }
    }
    @Test void sharedDecodedFactsDoNotHideSourceTargetOrSourceObjectChanges() {
        var b=binding(true);var decoded=SyncBindingSnapshots.decode(b.getFieldsJson());
        var cached=List.of(new DataSyncAdapter.Binding("ROW","1",22L,decoded));String before=JsonUtils.toJsonString(decoded);
        var changedRaw=new LinkedHashMap<>(raw);changedRaw.put("unmapped","changed");
        var changedTarget=fields(raw);changedTarget.put("target.name","target drift");
        var d=definition("TABLE_MAPPING","REUSE_UNCHANGED");
        assertTrue(select(d,changedRaw,fields(changedRaw),b,false,cached).reused().isEmpty());
        assertTrue(select(d,raw,changedTarget,b,false,cached).reused().isEmpty());
        b.setSourceObject("another_table");assertTrue(select(d,raw,fields(raw),b,false,cached).reused().isEmpty());
        assertEquals(before,JsonUtils.toJsonString(decoded));
    }
    @Test void previewAndFullPolicyPreserveOriginalInputAndSelectionRules() {
        var b=binding(true);var cached=List.of(new DataSyncAdapter.Binding("ROW","1",22L,SyncBindingSnapshots.decode(b.getFieldsJson())));
        var after=fields(raw);after.put("_previewVirtualIds",List.of(-1L));String before=JsonUtils.toJsonString(after);
        assertEquals(1,select(definition("TABLE_MAPPING","REUSE_UNCHANGED"),raw,after,b,true,cached).reused().size());
        assertEquals(before,JsonUtils.toJsonString(after));
        var full=select(definition("TABLE_MAPPING","FULL"),raw,fields(raw),b,false,cached);
        assertTrue(full.reused().isEmpty());assertEquals(1,full.snapshot().objects().size());
    }
}
