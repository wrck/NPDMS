package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="pms.sync.targetOrderBenchmark",matches="true")
class SyncEvidenceTargetOrderBenchmark {
    record Fixture(SyncDefinition.Source source,Map<String,Object> left,Map<String,Object> right,String originalLeft,String originalRight) {}
    static Method method(Class<?> type) throws Exception {
        Method m=type.getDeclaredMethod("sameDerivedFacts",SyncDefinition.Source.class,Map.class,Map.class,boolean.class);
        m.setAccessible(true);return m;
    }
    static List<Fixture> fixtures() {
        List<Fixture> out=new ArrayList<>();
        for(int n=0;n<16;n++) {
            var source=new SyncDefinition.Source().targets(List.of(new GenericTargetStep("target","unused",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
                List.of(new SyncDefinition.Mapping().target("payload").conversion("JSON")),List.of())));
            Map<String,Object> raw=new LinkedHashMap<>();raw.put("id",n+1);
            for(int k=0;k<24;k++)raw.put("source_"+k,k%5==0?null:"设备建档与发货字段-"+n+"-"+k);
            Map<String,Object> json=new LinkedHashMap<>();
            for(int k=0;k<32;k++)json.put("property_"+k,List.of("value-"+n+"-"+k,k,List.of(1,2,3)));
            Map<String,Object> reversedJson=new LinkedHashMap<>();var jsonKeys=new ArrayList<>(json.keySet());Collections.reverse(jsonKeys);
            for(var key:jsonKeys)reversedJson.put(key,json.get(key));
            Map<String,Object> left=new LinkedHashMap<>(raw);left.put("_syncSourcePayload",new LinkedHashMap<>(raw));
            left.put("target.id",n+1);left.put("target.payload",JsonUtils.toJsonString(json));
            for(int k=0;k<16;k++)left.put("target.field_"+k,"归档事实-"+n+"-"+k);
            List<Map<String,Object>> leftRefs=new ArrayList<>(),rightRefs=new ArrayList<>();
            for(String step:List.of("device","event","deviceShipment")) {
                long id=2108970914711343105L+n+(step.equals("event")?1:0);
                String table=step.equals("event")?"ast_device_shipment":"ast_device";
                Map<String,Object> lref=new LinkedHashMap<>(),rref=new LinkedHashMap<>();
                lref.put("step",step);lref.put("table",table);lref.put("context","AST");lref.put("id",id);
                rref.put("context","AST");rref.put("id",id);rref.put("table",table);rref.put("step",step);
                leftRefs.add(lref);rightRefs.add(rref);
            }
            left.put("_targets",leftRefs);
            Map<String,Object> right=new LinkedHashMap<>();var keys=new ArrayList<>(left.keySet());Collections.reverse(keys);
            for(var key:keys)right.put(key,left.get(key));
            right.put("_targets",rightRefs);
            right.put("target.id",(long)n+1);right.put("target.payload",JsonUtils.toJsonString(n%2==0?json:reversedJson));
            out.add(new Fixture(source,left,right,JsonUtils.toJsonString(left),JsonUtils.toJsonString(right)));
        }
        return out;
    }
    static long loop(Method m,List<Fixture> cases,int iterations)throws Exception {
        long accepted=0;
        for(int i=0;i<iterations;i++) {var f=cases.get(i%cases.size());if((boolean)m.invoke(null,f.source,f.left,f.right,true))accepted++;}
        assertEquals(iterations,accepted);return accepted;
    }
    @Test void compareTargetOrder()throws Exception {
        int iterations=Integer.getInteger("pms.sync.targetOrderBenchmark.iterations",10000);assertTrue(iterations>0&&iterations<=20000);
        var a=method(LegacyTargetOrderEvidenceSelection.class);var b=method(SyncEvidenceSelection.class);var cases=fixtures();
        for(int i=0;i<4;i++){loop(a,cases,2000);loop(b,cases,2000);}
        var mx=ManagementFactory.getThreadMXBean();assertTrue(mx.isCurrentThreadCpuTimeSupported()&&mx.isThreadCpuTimeEnabled());
        String[] order={"A","B","B","A","A","B","B","A"};List<Map<String,Object>> rounds=new ArrayList<>();
        for(int i=0;i<order.length;i++) {
            long cpu=mx.getCurrentThreadCpuTime(),start=System.nanoTime();long accepted=loop(order[i].equals("A")?a:b,cases,iterations);
            long wall=System.nanoTime()-start,used=mx.getCurrentThreadCpuTime()-cpu;
            rounds.add(Map.of("round",i+1,"implementation",order[i],"wallNanos",wall,"threadCpuNanos",used,"accepted",accepted));
        }
        for(var f:cases){assertEquals(f.originalLeft,JsonUtils.toJsonString(f.left));assertEquals(f.originalRight,JsonUtils.toJsonString(f.right));}
        System.out.println("TARGET_ORDER_BENCHMARK="+JsonUtils.toJsonString(Map.of("fixtures",cases.size(),"iterations",iterations,"warmupPairs",4,"warmupIterations",2000,"rounds",rounds,"scope","synthetic snapshots with observed historical target-reference shape; not database or end-to-end performance")));
    }
}
