package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class SyncConfigurationTest {
    @Test void configurationRoundTripsWithEntitiesAndNullableValues() {
        var expected=EhrSyncTemplate.create(1L);
        var json=JsonUtils.toJsonString(expected);
        var actual=JsonUtils.parseObject(json,SyncDefinition.class);
        assertEquals(expected,actual);
        assertEquals("compID",actual.sources().getFirst().sourceKey());
    }
    @Test void rejectsWriteAndMultiStatementSql() {
        for(String sql:List.of("DELETE FROM company","SELECT 1; DELETE FROM company",
                "SELECT * FROM company FOR UPDATE","SELECT * INTO OUTFILE '/tmp/leak' FROM company",
                "SELECT GET_LOCK('x',1)","SELECT @x := 1")) {
            var source=SyncDefinition.Source.builder().readMode("SQL").sql(sql).sourceKey("id").parameters(Map.of()).build();
            assertThrows(IllegalArgumentException.class,()->MysqlSyncReader.compile(source),sql);
        }
    }
    @Test void bindsNamedParametersInsteadOfInterpolating() {
        var source=SyncDefinition.Source.builder().readMode("SQL").sql("SELECT id FROM company WHERE name=:name")
                .sourceKey("id").parameters(Map.of("name","a' OR 1=1")).build();
        var result=MysqlSyncReader.compile(source);
        assertTrue(result.sql().contains("name=?"));
        assertEquals(List.of("a' OR 1=1"),result.values());
    }
    @Test void supportsJoinSubqueryAndCteSelectSyntax() {
        for(String sql:List.of("SELECT c.id FROM company c JOIN department d ON d.company_id=c.id",
                "SELECT id FROM (SELECT id FROM company) x",
                "WITH c AS (SELECT id FROM company) SELECT id FROM c")) {
            assertDoesNotThrow(()->MysqlSyncReader.compile(SyncDefinition.Source.builder().readMode("SQL")
                    .sql(sql).sourceKey("id").parameters(Map.of()).build()));
        }
    }
    @Test void springJdbcExpandsNamedCollectionParameters() throws Exception {
        var source=SyncDefinition.Source.builder().readMode("SQL").sql("SELECT 2 AS id WHERE 2 IN (:ids)")
                .sourceKey("id").parameters(Map.of("ids",List.of(1,2,3))).build();
        try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:parameter_test","sa","");
            var statement=MysqlSyncReader.prepare(connection,MysqlSyncReader.compile(source));
            var result=statement.executeQuery()) {
            assertTrue(result.next());assertEquals(2,result.getInt(1));
        }
    }
    @Test void emptyInMeansEmptyResult() {
        var source=SyncDefinition.Source.builder().readMode("TABLE").table("company").sourceKey("id")
                .columns(List.of("id")).filters(List.of(new SyncDefinition.Filter("id","IN",List.of()))).build();
        assertTrue(MysqlSyncReader.compile(source).sql().contains("1=0"));
    }
    @Test void rejectsIdentifiersAndUnknownOperators() {
        assertThrows(IllegalArgumentException.class,()->MysqlSyncReader.identifier("id;drop"));
        var source=SyncDefinition.Source.builder().readMode("TABLE").table("company").sourceKey("id")
                .columns(List.of("id")).filters(List.of(new SyncDefinition.Filter("id","OR","x"))).build();
        assertThrows(IllegalArgumentException.class,()->MysqlSyncReader.compile(source));
    }
    @Test void integerConversionDoesNotTruncate() {
        assertEquals(7L,SyncFieldMapper.convert("7","LONG"));
        assertThrows(ArithmeticException.class,()->SyncFieldMapper.convert("7.5","LONG"));
        assertThrows(IllegalArgumentException.class,()->SyncFieldMapper.convert("yes","BOOLEAN"));
    }
    @Test void ehrNullStatusIsExplicitlyMappedNotInvented() {
        var d=EhrSyncTemplate.create(1L);
        var fields=new java.util.LinkedHashMap<String,Object>();
        fields.put("compID",1);fields.put("compCode","001");fields.put("compName","公司");fields.put("isDisabled",null);
        var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),
                List.of(new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(fields))),0);
        var result=new SyncFieldMapper().transform(d,snapshot,List.of());
        assertEquals(0,result.getFirst().fields().get("status"));
    }
    @Test void ehrTemplatePreservesLegacyTextNormalizationForBothObjects() {
        var definition=EhrSyncTemplate.create(1L);
        var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),List.of(
                new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(Map.of(
                        "compID",1,"compCode"," 001 ","compName"," 公司 ","isDisabled",0))),
                new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of(Map.of(
                        "depID",342,"depCode"," 167000015 ","depName","总工办运作支持部 ",
                        "isDisabled",1,"compID",1,"adminID",0)))),0);
        var rows=new SyncFieldMapper().transform(definition,snapshot,List.of());
        assertEquals("001",rows.get(0).fields().get("code"));
        assertEquals("公司",rows.get(0).fields().get("name"));
        assertEquals("167000015",rows.get(1).fields().get("code"));
        assertEquals("总工办运作支持部",rows.get(1).fields().get("name"));
    }
}
