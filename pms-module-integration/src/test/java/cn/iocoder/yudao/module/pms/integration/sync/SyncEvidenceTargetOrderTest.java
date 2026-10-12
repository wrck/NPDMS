package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import static org.junit.jupiter.api.Assertions.*;

class SyncEvidenceTargetOrderTest {
    private static boolean compare(Class<?> type,Map<String,Object> left,Map<String,Object> right) throws Exception {
        var source=new SyncDefinition.Source();source.targets(List.of());
        Method m=type.getDeclaredMethod("sameDerivedFacts",SyncDefinition.Source.class,Map.class,Map.class,boolean.class);m.setAccessible(true);
        return (boolean)m.invoke(null,source,left,right,false);
    }
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;
    }
    private static Map<String,Object> reference(boolean historical,String step,long id,String table) {
        return historical?map("step",step,"table",table,"context","AST","id",id):map("context","AST","id",id,"table",table,"step",step);
    }
    private static List<Map<String,Object>> refs(boolean historical) {
        return List.of(reference(historical,"device",2108970914711343105L,"ast_device"),
            reference(historical,"event",2108970914715537409L,"ast_device_shipment"),
            reference(historical,"deviceShipment",2108970914711343105L,"ast_device"));
    }
    @Test void observedHistoricalReferenceShapeDoesNotParseWholeTrees() throws Exception {
        var left=map("sourceId",1001,"_targets",refs(true));var right=map("_targets",refs(false),"sourceId",1001L);
        assertTrue(compare(LegacyTargetOrderEvidenceSelection.class,left,right));
        try(var mock=Mockito.mockStatic(JsonUtils.class,Mockito.CALLS_REAL_METHODS)) {
            assertTrue(compare(SyncEvidenceSelection.class,left,right));
            mock.verify(()->JsonUtils.parseTree(ArgumentMatchers.anyString()),Mockito.never());
        }
    }
    @Test void referenceOrderIdentityAndExtraFieldsRemainObservable() throws Exception {
        var left=map("sourceId",1001,"_targets",refs(true));
        var swapped=new ArrayList<>(refs(false));Collections.swap(swapped,0,1);
        var changed=new ArrayList<>(refs(false));var ref=new LinkedHashMap<>(changed.get(0));ref.put("id",2108970914711343106L);changed.set(0,ref);
        var extra=new ArrayList<>(refs(false));var ex=new LinkedHashMap<>(extra.get(0));ex.put("extra","new");extra.set(0,ex);
        for(Object rs:List.of(swapped,changed,extra,refs(false).subList(0,2))) {
            var right=map("sourceId",1001L,"_targets",rs);
            assertFalse(compare(LegacyTargetOrderEvidenceSelection.class,left,right));assertFalse(compare(SyncEvidenceSelection.class,left,right));
        }
    }
    @Test void malformedAndNullReferenceValuesFollowOriginalRuleAndNeverMutateInputs() throws Exception {
        List<Object> variants=Arrays.asList(null,"raw",List.of(),refs(true),refs(false),Arrays.asList((Object)null),
            List.of(map("id",null)),List.of(map("id",1)),List.of(map("id",1L)),List.of(map("id","1")),
            List.of(map("id",1L,"extra",null)),List.of(Map.of(7,"nonStringKey")),List.of(List.of("nested")));
        for(Object a:variants)for(Object b:variants) {
            var left=map("sourceId",1,"_targets",a);var right=map("_targets",b,"sourceId",1L);
            String beforeL=JsonUtils.toJsonString(left),beforeR=JsonUtils.toJsonString(right);
            assertEquals(compare(LegacyTargetOrderEvidenceSelection.class,left,right),compare(SyncEvidenceSelection.class,left,right));
            assertEquals(beforeL,JsonUtils.toJsonString(left));assertEquals(beforeR,JsonUtils.toJsonString(right));
        }
    }
}
