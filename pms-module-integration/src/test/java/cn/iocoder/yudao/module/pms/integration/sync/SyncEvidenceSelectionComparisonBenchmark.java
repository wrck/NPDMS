package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic selector CPU comparison only; no database or end-to-end performance claim. */
@EnabledIfSystemProperty(named="pms.sync.selectionComparisonBenchmark",matches="true")
class SyncEvidenceSelectionComparisonBenchmark {
    record Input(SyncDefinition definition,MysqlSyncReader.Snapshot snapshot,
                 List<DataSyncAdapter.Change> changes,List<SyncBindingDO> bindings) {}
    private static List<Input> fixtures() {
        List<Input> result=new ArrayList<>();
        for(int i=0;i<16;i++) {
            var source=new LinkedHashMap<String,Object>();source.put("id",i+1);source.put("barcode","GENERATED-SN-"+i);
            source.put("at",LocalDateTime.of(2026,10,10,12,0).plusSeconds(i));
            for(int k=0;k<24;k++)source.put("field_"+k,(k%4==0?"模拟来源字段":"generated field")+k+"_"+i);
            var after=new LinkedHashMap<>(source);after.put("_syncSourcePayload",new LinkedHashMap<>(source));
            after.put("_targets",List.of(Map.of("table","target_table","id",9000000L+i)));
            after.put("target.payload",JsonUtils.toJsonString(source));
            for(int k=0;k<12;k++)after.put("target.field_"+k,"derived-"+k+"-"+i);
            var target=new GenericTargetStep("target","target_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(SyncDefinition.Mapping.builder().target("payload").source("$ROW").conversion("JSON").build()),List.of());
            var d=SyncDefinition.builder().adapter("TABLE_MAPPING").readStrategy("STREAMING_CURSOR").evidencePolicy("REUSE_UNCHANGED")
                .sources(List.of(SyncDefinition.Source.builder().object("ROW").sourceObject("source_table").sourceKey("id").targets(List.of(target)).build())).build();
            var binding=new SyncBindingDO().setObjectKey("ROW").setSourceObject("source_table").setSourceKey(Integer.toString(i+1))
                .setTargetId(9000000L+i).setTargetShared(true).setLastRunId(100L).setFieldsJson(SyncBindingSnapshots.encode(after,true));
            var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.of(2026,10,10,12,0),
                List.of(new MysqlSyncReader.SourceRows("ROW","source_table",List.of(source))),1000);
            var input=new Input(d,snapshot,List.of(new DataSyncAdapter.Change("ROW",Integer.toString(i+1),9000000L+i,"UNCHANGED",Map.of(),after,null)),List.of(binding));
            assertEquals(1,LegacySyncEvidenceSelectionForBenchmark.select(d,snapshot,input.changes(),input.bindings(),false).reused().size());
            assertEquals(1,SyncEvidenceSelection.select(d,snapshot,input.changes(),input.bindings(),false).reused().size());
            result.add(input);
        }
        return result;
    }
    private static Map<String,Object> round(boolean candidate,int iterations,List<Input> inputs) {
        var bean=ManagementFactory.getThreadMXBean();boolean cpu=bean.isCurrentThreadCpuTimeSupported()&&bean.isThreadCpuTimeEnabled();
        long cpuStart=cpu?bean.getCurrentThreadCpuTime():0,start=System.nanoTime();int reused=0;
        for(int i=0;i<iterations;i++) {
            var p=inputs.get(i%inputs.size());
            if(candidate)reused+=SyncEvidenceSelection.select(p.definition(),p.snapshot(),p.changes(),p.bindings(),false).reused().size();
            else reused+=LegacySyncEvidenceSelectionForBenchmark.select(p.definition(),p.snapshot(),p.changes(),p.bindings(),false).reused().size();
        }
        long wall=System.nanoTime()-start,cpuNanos=cpu?bean.getCurrentThreadCpuTime()-cpuStart:-1;
        assertEquals(iterations,reused);
        return Map.of("candidate",candidate,"iterations",iterations,"wallSeconds",wall/1e9,"threadCpuSeconds",cpuNanos<0?-1:cpuNanos/1e9,"reused",reused);
    }
    @Test void compareLegacyAndVerifiedPayloadElision() {
        int iterations=Integer.getInteger("pms.sync.selectionComparisonIterations",10000);
        assertTrue(iterations>=1000&&iterations<=50000);var inputs=fixtures();
        for(int i=0;i<4;i++){round(false,2000,inputs);round(true,2000,inputs);}
        List<Map<String,Object>> results=new ArrayList<>();
        for(boolean candidate:new boolean[]{false,true,true,false,false,true,true,false})results.add(round(candidate,iterations,inputs));
        System.out.println("SELECTION_COMPARISON_MICRO="+JsonUtils.toJsonString(Map.of("scope","SYNTHETIC_CPU_ONLY_NO_DATABASE_NO_END_TO_END_CLAIM","fixtures",inputs.size(),"order","ABBAABBA","results",results)));
    }
}
