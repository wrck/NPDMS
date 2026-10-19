package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectDictionaryTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DppmsDirectDictionaryTask task;
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final LocalDateTime time=LocalDateTime.of(2026,10,16,0,0);
    SyncRunDO run;
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p08-direct-"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        String audit="creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE";
        jdbc.execute("CREATE TABLE system_dict_type(id BIGINT PRIMARY KEY,name VARCHAR(100),type VARCHAR(100),status INT,remark VARCHAR(500),"+audit+",deleted_time TIMESTAMP,UNIQUE(name),UNIQUE(type))");
        jdbc.execute("CREATE TABLE system_dict_data(id BIGINT PRIMARY KEY,sort INT,label VARCHAR(100),value VARCHAR(100) CHECK(value<>'bad'),dict_type VARCHAR(100),status INT,color_type VARCHAR(100),css_class VARCHAR(100),remark VARCHAR(500),"+audit+",UNIQUE(dict_type,value))");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("DirectDictionaryTypeMapper","DirectDictionaryDataMapper")) {
            String path="mapper/sync/"+name+".xml";
            try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        task=new DppmsDirectDictionaryTask(session.getMapper(DirectDictionaryTypeMapper.class),session.getMapper(DirectDictionaryDataMapper.class),permissions,source,users,tasks,runs);
        var definition=DppmsDirectDictionaryTemplate.create(9L);
        var taskRow=new SyncTaskDO().setId(12L).setAdapter(definition.adapter()).setSourceSystem("DPPMS")
                .setVersion(0).setActiveRunId(13L).setDefinition(JsonUtils.toJsonString(definition));taskRow.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(taskRow);
        run=new SyncRunDO().setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");
        run.setTenantId(1L);run.setCreateTime(time);run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Batch batch(boolean fail,List<Binding> bindings) {
        var type=new LinkedHashMap<String,Object>();DppmsDictionaryTypeSyncTemplate.ORIGINAL_COLUMNS.forEach(k->type.put(k,null));
        type.put("id",1);type.put("dataTypeCode","01");type.put("dataTypeName","来源字典");type.put("createTime",time);type.put("definitionMissing",0);
        List<Row> rows=new ArrayList<>();rows.add(new Row("fnd_basic_data_type","1",type,target(bindings,"fnd_basic_data_type","1")));
        for(int i=1;i<=2;i++) {
            String key=Integer.toString(i+30);var item=new LinkedHashMap<String,Object>();DppmsDictionaryDataSyncTemplate.ORIGINAL_COLUMNS.forEach(k->item.put(k,null));
            item.put("id",key);item.put("dataTypeCode","01");item.put("basicDataId",fail && i==2?"bad":Integer.toString(i));item.put("basicDataName","项"+i);item.put("createTime",time);
            rows.add(new Row("fnd_basic_data",key,item,target(bindings,"fnd_basic_data",key)));
        }
        return new Batch("integration:1:12",rows,bindings,true,"RETAIN",false,"UPSERT",false);
    }
    Long target(List<Binding> bindings,String object,String key){return bindings.stream().filter(b->object.equals(b.object()) && key.equals(b.sourceKey())).map(Binding::targetId).findFirst().orElse(null);}
    Batch missingGroup(String nativeKey,List<Binding> bindings) {
        var base=batch(false,bindings);var type=new LinkedHashMap<String,Object>();DppmsDictionaryTypeSyncTemplate.ORIGINAL_COLUMNS.forEach(k->type.put(k,null));
        type.put("sourceId",nativeKey);type.put("dataTypeCode","99");type.put("dataTypeName","99");type.put("definitionMissing",1);
        List<Row> rows=new ArrayList<>();rows.add(new Row(DppmsDirectDictionaryTemplate.MISSING_TYPE,nativeKey,type,target(bindings,DppmsDirectDictionaryTemplate.MISSING_TYPE,nativeKey)));
        for(var row:base.rows())if("fnd_basic_data".equals(row.object())) {
            var fields=new LinkedHashMap<>(row.fields());fields.put("dataTypeCode","99");rows.add(new Row(row.object(),row.sourceKey(),fields,row.targetId()));
        }
        return new Batch(base.owner(),List.copyOf(rows),bindings,true,base.missingPolicy(),false,base.loadingMode(),false);
    }
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    void applying(){run.setPreview(false).setStatus("APPLYING");}
    @Test void previewResolvesThisRunPlannedTypeWithoutWritesAndApplyRequiresTransaction() {
        assertEquals(3,task.preview(batch(false,List.of())).size());assertEquals(0,count("system_dict_type"));assertEquals(0,count("system_dict_data"));
        applying();assertThrows(IllegalStateException.class,()->task.apply(batch(false,List.of())));assertEquals(0,count("system_dict_type"));
        verifyNoInteractions(users);
    }
    @Test void lateItemConstraintRollsBackEarlierTypeAndItemWrites() {
        applying();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->task.apply(batch(true,List.of()))));
        assertEquals(0,count("system_dict_type"));assertEquals(0,count("system_dict_data"));
    }
    @Test void replayKeepsAllHistoricalFieldsAndNoOtherDefinitionIsAdopted() {
        applying();var changes=tx.execute(status->task.apply(batch(false,List.of())));
        assertEquals(1,count("system_dict_type"));assertEquals(2,count("system_dict_data"));
        var bindings=changes.stream().map(c->new Binding(c.object(),c.sourceKey(),c.targetId(),c.after())).toList();
        assertTrue(tx.execute(status->task.apply(batch(false,bindings))).stream().allMatch(c->"UNCHANGED".equals(c.action())));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(false,List.of()))));
        jdbc.update("UPDATE system_dict_data SET remark='人工维护'");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->task.apply(batch(false,bindings))));
        assertEquals(2,count("system_dict_data"));
    }
    @Test void originalTaskDefinitionStaysIndependentAndPermissionsAreMandatory() {
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(task)).validate(DppmsDirectDictionaryTemplate.create(9L)));
        assertEquals("DPPMS_DICTIONARIES",DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P08",9L)).adapter());
        when(permissions.hasAnyPermissions(7L,"system:dict:create")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->task.preview(batch(false,List.of())));assertEquals(0,count("system_dict_type"));
    }
    @Test void missingGroupsUseNativeItemEvidenceAndKeepOneTypeWhenRepresentativeChanges() {
        assertEquals(3,task.preview(missingGroup("31",List.of())).size());assertEquals(0,count("system_dict_type"));
        applying();var first=tx.execute(status->task.apply(missingGroup("31",List.of())));
        var type=first.stream().filter(c->DppmsDirectDictionaryTemplate.MISSING_TYPE.equals(c.object())).findFirst().orElseThrow();
        assertEquals("31",type.sourceKey());assertNotNull(type.targetId());
        var bindings=first.stream().map(c->new Binding(c.object(),c.sourceKey(),c.targetId(),c.after())).toList();
        var replay=tx.execute(status->task.apply(missingGroup("32",bindings)));
        var alias=replay.stream().filter(c->DppmsDirectDictionaryTemplate.MISSING_TYPE.equals(c.object())).findFirst().orElseThrow();
        assertEquals("32",alias.sourceKey());assertEquals(type.targetId(),alias.targetId());assertEquals("UNCHANGED",alias.action());
        assertEquals(1,count("system_dict_type"));assertEquals(2,count("system_dict_data"));
    }
    @Test void originalTypeNativeIdCannotBeReplacedByItsBusinessCode() {
        var original=batch(false,List.of());var rows=new ArrayList<>(original.rows());var type=rows.getFirst();
        rows.set(0,new Row(type.object(),"01",type.fields(),null));
        var wrong=new Batch(original.owner(),rows,List.of(),true,"RETAIN",false,"UPSERT",false);
        assertThrows(IllegalArgumentException.class,()->task.preview(wrong));assertEquals(0,count("system_dict_type"));
        var definition=DppmsDirectDictionaryTemplate.create(9L);
        assertEquals(List.of("id","sourceId","id"),definition.sources().stream().map(SyncDefinition.Source::sourceKey).toList());
        assertTrue(definition.sources().stream().allMatch(source->source.sql()!=null && source.queryRef()==null && source.groupKey()==null && source.rowProjection()==null));
    }
}
