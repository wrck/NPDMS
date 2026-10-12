package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsProductCatalogSyncTemplateTest {
    @Test void generatedSourceQueryPassesTheRealReadonlySqlCompiler() {
        var source=DppmsProductCatalogSyncTemplate.create(12L,9L,List.of("CODE","code")).sources().getFirst();
        var compiled=assertDoesNotThrow(()->MysqlSyncReader.compile(source));
        assertEquals(List.of(9L,"CODE","code"),compiled.values());
        assertEquals("SELECT i.id,i.item,i.itemname,i.describe_,? AS migrationDefinitionRevisionId FROM fb_items i WHERE CAST(i.item AS BINARY) IN (?,?)",compiled.sql());
    }
    @Test void codesAreBoundAsValuesAndTechnicalNullIdsDoNotBecomeBusinessKeys() {
        String code=" CODE');DROP TABLE fb_items;-- ";
        var definition=DppmsProductCatalogSyncTemplate.create(12L,9L,List.of(code));var source=definition.sources().getFirst();
        assertFalse(source.sql().contains(code));assertEquals(code,source.parameters().get("productCode0"));
        var fields=new LinkedHashMap<String,Object>();fields.put("id",null);fields.put("item",code);fields.put("itemname",null);fields.put("describe_"," name ");
        fields.put(DppmsProductCatalogSyncTemplate.DEFINITION,9L);
        var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),List.of(new MysqlSyncReader.SourceRows("fb_items","fb_items",List.of(fields))),1);
        var row=new SyncFieldMapper().transform(definition,snapshot,List.of()).getFirst();
        assertEquals(code,row.sourceKey());assertNull(row.fields().get("id"));assertTrue(row.fields().containsKey("id"));
        assertEquals(fields,row.fields());assertEquals("STREAMING_CURSOR",definition.effectiveReadStrategy());
    }
    @Test void emptySelectionsAndInvalidRevisionsCannotReadTheWholeDirectory() {
        assertThrows(IllegalArgumentException.class,()->DppmsProductCatalogSyncTemplate.create(12L,9L,List.of()));
        assertThrows(IllegalArgumentException.class,()->DppmsProductCatalogSyncTemplate.create(12L,null,List.of("CODE")));
        assertThrows(IllegalArgumentException.class,()->DppmsProductCatalogSyncTemplate.create(12L,9L,List.of("CODE","CODE")));
    }
}
