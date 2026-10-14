package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericTargetStep;
import java.lang.reflect.*;
import java.util.*;
import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import static org.junit.jupiter.api.Assertions.*;

class SyncEvidenceKeyOrderTest {
    private static SyncDefinition.Source source(boolean declared) {
        var source=new SyncDefinition.Source();
        source.targets(declared?List.of(new GenericTargetStep("target","unused_table",List.of("id"),"UPSERT","IGNORE",List.of(),null,null,null,
            List.of(new SyncDefinition.Mapping().target("payload").conversion("JSON"),
                    new SyncDefinition.Mapping().target("at").conversion("DATETIME"),
                    new SyncDefinition.Mapping().target("day").conversion("DATE")),List.of())):List.of());
        return source;
    }
    private static boolean compare(Class<?> type,SyncDefinition.Source source,Map<String,Object> left,Map<String,Object> right,boolean verified) throws Exception {
        Method m=type.getDeclaredMethod("sameDerivedFacts",SyncDefinition.Source.class,Map.class,Map.class,boolean.class);m.setAccessible(true);
        try {return (boolean)m.invoke(null,source,left,right,verified);}
        catch(InvocationTargetException e) {if(e.getCause() instanceof Exception cause)throw cause;throw e;}
    }
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    @Test void keyOrderOnlyDoesNotParseTwoWholeSnapshotTrees() throws Exception {
        var left=map("id",1,"name","设备");var right=map("name","设备","id",1L);
        assertTrue(compare(LegacyKeyOrderEvidenceSelection.class,source(false),left,right,false));
        try(var mock=Mockito.mockStatic(JsonUtils.class,Mockito.CALLS_REAL_METHODS)) {
            assertTrue(compare(SyncEvidenceSelection.class,source(false),left,right,false));
            mock.verify(()->JsonUtils.parseTree(ArgumentMatchers.anyString()),Mockito.never());
        }
    }
    @Test void identicalDeclaredJsonIsParsedOnlyOnce() throws Exception {
        String json="{\"b\":2,\"a\":1}";
        var left=map("id",1,"target.payload",json);var right=map("target.payload",json,"id",1L);
        try(var mock=Mockito.mockStatic(JsonUtils.class,Mockito.CALLS_REAL_METHODS)) {
            assertTrue(compare(SyncEvidenceSelection.class,source(true),left,right,false));
            mock.verify(()->JsonUtils.parseTree(json),Mockito.times(1));
        }
    }
    @Test void equivalentNestedObjectOrderDoesNotParseWholeSnapshotTrees() throws Exception {
        var left=map("id",1,"target.payload","{\"b\":{\"z\":1,\"a\":2},\"a\":1}");
        var right=map("target.payload","{\"a\":1,\"b\":{\"a\":2,\"z\":1}}","id",1L);
        try(var mock=Mockito.mockStatic(JsonUtils.class,Mockito.CALLS_REAL_METHODS)) {
            assertTrue(compare(SyncEvidenceSelection.class,source(true),left,right,false));
            mock.verify(()->JsonUtils.parseTree(ArgumentMatchers.<String>argThat(text->text!=null&&text.contains("\"target.payload\""))),Mockito.never());
        }
    }
    @Test void extraMissingNullAndChangedFactsRemainVisibleWithoutMutatingInputs() throws Exception {
        var left=map("id",1,"target.payload","{\"a\":1}","empty",null);
        for(var right:List.of(map("id",1L,"target.payload","{\"a\":2}"),map("id",1L),
                map("id",1L,"target.payload","{\"a\":1}","extra","new"),
                map("target.payload","{\"a\":1}","id",1L,"anotherNull",null))) {
            var leftCopy=new LinkedHashMap<>(left);var rightCopy=new LinkedHashMap<>(right);
            String l=JsonUtils.toJsonString(left),r=JsonUtils.toJsonString(right);
            assertEquals(compare(LegacyKeyOrderEvidenceSelection.class,source(true),left,right,false),compare(SyncEvidenceSelection.class,source(true),left,right,false));
            assertEquals(leftCopy,left);assertEquals(rightCopy,right);assertEquals(l,JsonUtils.toJsonString(left));assertEquals(r,JsonUtils.toJsonString(right));
        }
    }
    @Test void differentialOraclePreservesNumericTemporalJsonAndPayloadRules() throws Exception {
        List<Object> values=Arrays.asList(null,1,1L,1.0,1.0f,new BigDecimal("1.00"),new BigInteger("9007199254740993"),
            "1",true,"设备😀","{\"a\":1,\"b\":2}","{\"b\":2,\"a\":1}","[1,2]","[2,1]","null","not-json",
            JsonUtils.parseTree("{\"a\":1,\"b\":2}"),LocalDateTime.of(2026,10,11,1,2,3),
            java.sql.Timestamp.valueOf("2026-10-11 01:02:03.123456789"),java.sql.Timestamp.valueOf("2026-10-11 01:02:03.123"),
            LocalDate.of(2026,10,11),java.sql.Date.valueOf("2026-10-11"));
        for(Object a:values)for(Object b:values)for(boolean verified:new boolean[]{false,true}) {
            var left=map("id",1,"target.payload",a,"target.at",a,"target.day",a,"_syncSourcePayload",map("raw",a));
            var right=map("_syncSourcePayload",map("raw",b),"target.day",b,"target.at",b,"target.payload",b,"id",1L);
            assertEquals(compare(LegacyKeyOrderEvidenceSelection.class,source(true),left,right,verified),
                compare(SyncEvidenceSelection.class,source(true),left,right,verified),"a="+a+", b="+b+", verified="+verified);
        }
    }
}
