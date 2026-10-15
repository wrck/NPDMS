package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GenericCacheRefreshTest {
    JdbcTemplate jdbc;TransactionTemplate tx;GenericSyncEngine engine;SyncDisplayCacheMapper mapper;
    SyncDefinition projects,plans;
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:cache"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/sync/SyncDisplayCacheMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(SyncDisplayCacheMapper.class);
        var catalog=new GenericTargetCatalog();
        engine=new GenericSyncEngine(catalog,new GenericSyncJdbcStore(ds),event->{});
        org.springframework.test.util.ReflectionTestUtils.setField(engine,"displayCaches",mapper);
        for(String name:GenericCacheRefresh.SOURCES.keySet()) {
            var fields=new LinkedHashMap<String,String>();
            fields.put("id","BIGINT PRIMARY KEY");fields.put("tenant_id","BIGINT NOT NULL");fields.put("deleted","BOOLEAN NOT NULL");
            fields.put("creator","VARCHAR(64)");fields.put("updater","VARCHAR(64)");fields.put("create_time","TIMESTAMP");fields.put("update_time","TIMESTAMP");
            for(String column:catalog.required(name).columns())fields.putIfAbsent(column,
                    Set.of("quantity","lease_duration").contains(column)?"DECIMAL(20,4) CHECK ("+column+">=0)":
                    column.endsWith("_at")||column.startsWith("effective_")?"TIMESTAMP":Set.of("level","item_group","after_days").contains(column)?"INT":"VARCHAR(64)");
            jdbc.execute("CREATE TABLE "+name+" ("+String.join(",",fields.entrySet().stream().map(e->"`"+e.getKey()+"` "+e.getValue()).toList())+")");
            jdbc.update("INSERT INTO "+name+"(id,tenant_id,deleted) VALUES (1,1,false),(2,2,false)");
        }
        jdbc.execute("CREATE TABLE int_sync_binding(id BIGINT,tenant_id BIGINT,task_id BIGINT,deleted INT,fields_json VARCHAR(100))");
        projects=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P21",1L));
        plans=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P23",1L));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void publicValidationAndFieldMappingAcceptBothCompleteCacheTemplates() {
        var validator=new SyncDefinitionValidator(List.of());
        org.springframework.test.util.ReflectionTestUtils.setField(validator,"genericEngine",engine);
        for(var definition:List.of(projects,plans)) {
            assertDoesNotThrow(()->validator.validate(definition));
            var source=definition.sources().getFirst();
            var fields=new LinkedHashMap<String,Object>();
            source.columns().forEach(column->fields.put(column,null));
            fields.put(GenericCacheRefresh.ROW_KEY,1L);
            var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),
                    List.of(new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),List.of(fields))),0);
            var rows=new SyncFieldMapper().transform(definition,snapshot,List.of());
            assertEquals(1,rows.size());
            assertNull(rows.getFirst().targetId());
            assertDoesNotThrow(()->engine.bind(definition).preview(new DataSyncAdapter.Batch(
                    "integration:1:10",rows,List.of(),true,"RETAIN",false,GenericCacheRefresh.MODE,true)));
        }
    }
    DataSyncAdapter.Row row(SyncDefinition d,int index,String key,Map<String,Object> overrides) {
        var source=d.sources().get(index);var values=new LinkedHashMap<String,Object>();
        source.columns().forEach(c->values.put(c,null));values.putAll(overrides);
        return new DataSyncAdapter.Row(source.object(),key,values,null);
    }
    DataSyncAdapter.Batch batch(DataSyncAdapter.Row... rows){return new DataSyncAdapter.Batch("integration:1:10",List.of(rows),List.of(),true,"RETAIN",false,GenericCacheRefresh.MODE,true);}
    List<DataSyncAdapter.Change> apply(SyncDefinition d,DataSyncAdapter.Row... rows){return tx.execute(s->engine.bind(d).apply(batch(rows)));}
    int count(String table,long tenant){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Integer.class,tenant);}
    @Test void duplicatesAndNullsSurviveAndOtherTenantIsUntouched() {
        var source=row(projects,0,"1",Map.of("projectCode","P001","productSubCode","A"));
        var duplicate=row(projects,0,"2",source.fields());
        var changes=apply(projects,source,duplicate);
        assertEquals(2,count("proj_project_lease_product",1));assertEquals(1,count("proj_project_lease_product",2));
        assertEquals(0,count("proj_project_product_configuration",1));assertEquals(1,count("proj_project_product_configuration",2));
        assertEquals(1,count("com_contract_collection_plan",1));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_lease_product WHERE tenant_id=1 AND quantity IS NULL",Integer.class));
        assertNotEquals(changes.get(0).targetId(),changes.get(1).targetId());assertTrue(changes.getFirst().after().containsKey("_targets"));
    }
    @Test void previewAndInvalidConversionNeverClearAnything() {
        assertEquals(1,engine.bind(projects).preview(batch(row(projects,0,"1",Map.of()))).size());
        assertEquals(1,count("proj_project_lease_product",1));
        assertThrows(IllegalArgumentException.class,()->apply(projects,row(projects,0,"1",Map.of("num","not-a-number"))));
        assertEquals(1,count("proj_project_lease_product",1));assertEquals(1,count("proj_project_product_configuration",1));
    }
    @Test void lateFailureRollsBackBothCacheTables() {
        assertThrows(RuntimeException.class,()->apply(projects,row(projects,0,"1",Map.of("num","1")),
                row(projects,1,"1",Map.of("quantity","-1"))));
        for(String table:List.of("proj_project_lease_product","proj_project_product_configuration"))
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=1 AND tenant_id=1",Integer.class));
    }
    @Test void completeEmptySnapshotPublishesEmptyCache() {
        apply(plans);assertEquals(0,count("com_contract_collection_plan",1));assertEquals(1,count("com_contract_collection_plan",2));
        assertEquals(1,count("proj_project_lease_product",1));
    }
    @Test void originalNullablePlanAuditIsPreserved() {
        apply(plans,row(plans,0,"1",Map.of("contractNo","C001","createBy","legacy-name")));
        var saved=jdbc.queryForMap("SELECT * FROM com_contract_collection_plan WHERE tenant_id=1");
        assertEquals("legacy-name",saved.get("creator"));assertNull(saved.get("create_time"));assertNull(saved.get("update_time"));
    }
    @Test void partialSourcesAndOtherTablesAreRejected() {
        var source=plans.sources().getFirst();
        assertThrows(IllegalArgumentException.class,()->engine.bind(plans.toBuilder().sources(List.of(source.toBuilder()
                .filters(List.of(new SyncDefinition.Filter("id","EQ",1))).build())).build()));
        assertThrows(IllegalArgumentException.class,()->engine.bind(plans.toBuilder().readStrategy("STREAMING_CURSOR").build()));
        assertThrows(IllegalArgumentException.class,()->engine.bind(plans.toBuilder().clearBeforeLoad(false).build()));
        assertThrows(IllegalArgumentException.class,()->engine.bind(projects.toBuilder().sources(List.of(projects.sources().getFirst())).build()));
        assertThrows(IllegalArgumentException.class,()->new SyncDisplayCacheMapper.Scope(1L,null));
    }
    @Test void bindingRetirementKeepsHistoricalFieldsAndOtherTasks() {
        jdbc.update("INSERT INTO int_sync_binding VALUES (1,1,10,0,'old'),(2,1,20,0,'other'),(3,2,10,0,'tenant')");
        tx.executeWithoutResult(s->mapper.retireBindings(new SyncQueries.Task(1L,10L)));
        assertEquals("old",jdbc.queryForObject("SELECT fields_json FROM int_sync_binding WHERE id=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM int_sync_binding WHERE id=1",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE deleted=0",Integer.class));
    }
}
