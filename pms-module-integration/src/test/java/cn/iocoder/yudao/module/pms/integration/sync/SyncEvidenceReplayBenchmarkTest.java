package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** CPU/serialization and selected-write counts only, not a database or source SQL throughput claim. */
@EnabledIfSystemProperty(named="pms.sync.benchmark",matches="true")
class SyncEvidenceReplayBenchmarkTest {
    @Test void fourMillionUnchangedFactsSelectNoNewEvidence() {
        int total=Integer.getInteger("pms.sync.benchmark.rows",4_000_000);
        int size=Integer.getInteger("pms.sync.benchmark.chunk",1000);
        assertTrue(total>0&&size>0&&size<=5000);
        var definition=SyncDefinition.builder().adapter("TABLE_MAPPING").evidencePolicy("REUSE_UNCHANGED")
                .sources(List.of(SyncDefinition.Source.builder().object("DEVICE_SN")
                        .sourceObject("fb_shipment_barcode").sourceKey("id").build())).build();
        long start=System.nanoTime(), reused=0, legacyBytes=0, compactBytes=0;
        var upper=LocalDateTime.of(2026,10,9,0,0);
        for(int offset=0;offset<total;offset+=size) {
            List<Map<String,Object>> raw=new ArrayList<>();
            List<SyncBindingDO> bindings=new ArrayList<>();
            List<DataSyncAdapter.Change> changes=new ArrayList<>();
            for(int n=offset;n<Math.min(offset+size,total);n++) {
                Map<String,Object> source=new LinkedHashMap<>();
                source.put("id",n+1);source.put("barcode","BENCH-"+n);source.put("pack_id","PACK-"+(n/20));
                source.put("contract_no","C-"+(n/100));source.put("source_note","source-payload-".repeat(16));
                Map<String,Object> after=new LinkedHashMap<>(source);after.put("_syncSourcePayload",source);
                after.put("_targets",List.of(Map.of("table","ast_device_shipment","id",(long)n+1)));
                after.put("event.device_sn",source.get("barcode"));
                String compact=SyncBindingSnapshots.encode(after,true);
                legacyBytes+=JsonUtils.toJsonString(after).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                compactBytes+=compact.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                raw.add(source);
                bindings.add(new SyncBindingDO().setObjectKey("DEVICE_SN").setSourceObject("fb_shipment_barcode")
                        .setSourceKey(Integer.toString(n+1)).setTargetId((long)n+1).setTargetShared(true).setLastRunId(1L).setFieldsJson(compact));
                changes.add(new DataSyncAdapter.Change("DEVICE_SN",Integer.toString(n+1),(long)n+1,"UNCHANGED",Map.of(),after,null));
            }
            var snapshot=new MysqlSyncReader.Snapshot(upper,List.of(new MysqlSyncReader.SourceRows("DEVICE_SN","fb_shipment_barcode",raw)),0);
            var selected=SyncEvidenceSelection.select(definition,snapshot,changes,bindings,false);
            assertTrue(selected.snapshot().objects().isEmpty());assertTrue(selected.needingEvidence(changes).isEmpty());
            reused+=selected.reused().size();
            if(reused%100_000==0)System.out.println("SYNC_REPLAY_PROGRESS rows="+reused);
        }
        assertEquals(total,reused);assertTrue(compactBytes<legacyBytes);
        double seconds=(System.nanoTime()-start)/1_000_000_000.0;
        System.out.println("SYNC_REPLAY_BENCHMARK="+JsonUtils.toJsonString(Map.of(
                "kind","CPU_SERIALIZATION_AND_WRITE_SELECTION_ONLY","rows",total,"chunkSize",size,
                "seconds",seconds,"rowsPerSecond",total/seconds,"selectedNewEvidenceRows",0,
                "selectedBindingWrites",0,"legacyBindingBytes",legacyBytes,"compactBindingBytes",compactBytes)));
    }
}
