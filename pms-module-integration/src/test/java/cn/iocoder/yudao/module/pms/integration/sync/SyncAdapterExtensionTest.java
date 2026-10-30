package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Second test-only business sink proves configuration and mapping are not EHR-specific. */
class SyncAdapterExtensionTest {
    @Test void secondBusinessObjectReusesRegistryConfigurationAndMappingWithoutEngineChanges() {
        DataSyncAdapter provider=new DataSyncAdapter() {
            public Descriptor descriptor() {return new Descriptor("TEST_ITEM","测试业务对象",
                    List.of(new ObjectDescriptor("ITEM","条目",List.of(new Field("title","标题","STRING",true)),
                            "TEST","Item","test_item",false)),List.of("RETAIN"),List.of("UPSERT"),false);}
            public List<Change> preview(Batch b){return b.rows().stream().map(r->new Change(r.object(),r.sourceKey(),null,
                    "CREATED",Map.of(),r.fields(),null)).toList();}
            public List<Change> apply(Batch b){return preview(b);}
            public void refreshCaches(){}
        };
        var source=SyncDefinition.Source.builder().object("ITEM").sourceObject("legacy_item").readMode("TABLE")
                .table("legacy_item").sourceKey("legacy_id").columns(List.of("legacy_id","label")).filters(List.of())
                .mappings(List.of(SyncDefinition.Mapping.builder().source("label").target("title").conversion("STRING").build())).build();
        var definition=EhrSyncTemplate.create(1L).toBuilder().adapter("TEST_ITEM").missingPolicy("RETAIN").sources(List.of(source)).build();
        var registry=new SyncDefinitionValidator(List.of(provider));registry.validate(definition);
        var snapshot=new MysqlSyncReader.Snapshot(LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("ITEM","legacy_item",List.of(Map.of("legacy_id",1,"label","测试")))),0);
        var rows=new SyncFieldMapper().transform(definition,snapshot,List.of());
        var changes=registry.adapter("TEST_ITEM").preview(new DataSyncAdapter.Batch("test",rows,List.of(),true,"RETAIN",false,"UPSERT",false));
        assertEquals("测试",changes.getFirst().after().get("title"));
        assertEquals("ITEM",changes.getFirst().object());
        assertThrows(IllegalArgumentException.class,()->registry.validate(definition.toBuilder().loadingMode("INSERT_IGNORE").build()));
        source.syncPrimaryKey(true);
        assertThrows(IllegalArgumentException.class,()->registry.validate(definition));
    }

    @Test void streamingRequiresDescriptorObjectOrder() {
        DataSyncAdapter provider=new DataSyncAdapter() {
            public Descriptor descriptor() {return new Descriptor("STREAM_ORDER","流式依赖顺序",
                    List.of(object("PARENT"),object("CHILD")),List.of("RETAIN"),List.of("UPSERT"),false,true);}
            public List<Change> preview(Batch b){return List.of();}
            public List<Change> apply(Batch b){return List.of();}
            public void refreshCaches(){}
            public boolean requiresAllBindings(){return false;}
            public boolean supportsStreaming(){return true;}
            private ObjectDescriptor object(String name){return new ObjectDescriptor(name,name,
                    List.of(new Field("title","标题","STRING",true)),"TEST",name,"test_"+name.toLowerCase(Locale.ROOT),false);}
        };
        var parent=streamSource("PARENT","legacy_parent");
        var child=streamSource("CHILD","legacy_child");
        var definition=EhrSyncTemplate.create(1L).toBuilder().adapter("STREAM_ORDER").mode("ONCE")
                .missingPolicy("RETAIN").readStrategy("STREAMING_CURSOR").sources(List.of(parent,child)).build();
        var registry=new SyncDefinitionValidator(List.of(provider));
        assertDoesNotThrow(()->registry.validate(definition));
        var reversed=definition.toBuilder().sources(List.of(child,parent)).build();
        var error=assertThrows(IllegalArgumentException.class,()->registry.validate(reversed));
        assertTrue(error.getMessage().contains("适配器声明顺序"));
    }

    private static SyncDefinition.Source streamSource(String object,String sourceObject) {
        return SyncDefinition.Source.builder().object(object).sourceObject(sourceObject).readMode("TABLE")
                .table(sourceObject).sourceKey("legacy_id").columns(List.of("legacy_id","label")).filters(List.of())
                .mappings(List.of(SyncDefinition.Mapping.builder().source("label").target("title").conversion("STRING").build())).build();
    }
}