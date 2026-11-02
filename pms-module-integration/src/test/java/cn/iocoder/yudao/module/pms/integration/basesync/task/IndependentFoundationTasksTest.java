package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentFoundationTasksTest {
    JdbcTemplate jdbc;SqlSessionTemplate target;SyncSource source;SqlSession nativeSource;
    TransactionTemplate transaction;SyncIdentityStore bindings;SyncReferences references;
    final LocalDateTime time=LocalDateTime.of(2020,1,1,0,0);
    @BeforeEach void setup() throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:independent-foundations-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(data);
        for(var spec:List.of(new Table("cus_customer_master",P10CrmCustomerSyncTask.CustomerRow.class),new Table("cus_customer_external_mapping",P10CrmCustomerSyncTask.CustomerMapping.class))){
            var fields=new ArrayList<String>();
            for(var field:spec.type().getDeclaredFields())fields.add(column(field.getName())+" "+(field.getType()==Long.class?"BIGINT":field.getType()==Boolean.class?"BOOLEAN":field.getType()==LocalDateTime.class?"TIMESTAMP":"VARCHAR(2000)"));
            jdbc.execute("CREATE TABLE "+spec.name()+"("+String.join(",",fields)+",PRIMARY KEY(id))");
        }
        jdbc.execute("CREATE TABLE system_dict_type(id BIGINT PRIMARY KEY,name VARCHAR(100),type VARCHAR(100) UNIQUE,status INT,remark VARCHAR(255),creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,deleted_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE system_dict_data(id BIGINT PRIMARY KEY,sort INT,label VARCHAR(100),`value` VARCHAR(100),dict_type VARCHAR(100),status INT,color_type VARCHAR(100),css_class VARCHAR(100),remark VARCHAR(255),creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,UNIQUE(dict_type,`value`))");
        jdbc.execute("CREATE TABLE independent_binding(domain VARCHAR(40),source_key VARCHAR(128),target_id BIGINT,fingerprint VARCHAR(64),PRIMARY KEY(domain,source_key))");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("target",new SpringManagedTransactionFactory(),data));
        for(var resource:List.of("mapper/basesync/P08TargetMapper.xml","mapper/basesync/P10TargetMapper.xml"))
            try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        target=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
        source=mock(SyncSource.class);var opened=mock(SyncSource.Session.class);nativeSource=mock(SqlSession.class);
        when(source.open(3)).thenReturn(opened);when(opened.sqlSession()).thenReturn(nativeSource);
        references=mock(SyncReferences.class);when(references.findAuditUserCatalogue(any())).thenReturn(List.of(new SyncReferences.AuditUser(99,"author")));when(references.findAuditUsers(any())).thenAnswer(call->{
            SyncReferences.AuditUsersQuery query=call.getArgument(0);
            return query.usernames().contains("author")?List.of(new SyncReferences.AuditUser(99,"author")):List.of();
        });
        bindings=new SyncIdentityStore(){
            public List<Identity> find(Query query){var result=new ArrayList<Identity>();for(String key:query.sourceKeys())result.addAll(jdbc.query("SELECT source_key,target_id,fingerprint FROM independent_binding WHERE domain=? AND source_key=?",(rs,n)->new Identity(rs.getString(1),rs.getLong(2),rs.getString(3)),query.object(),key));return result;}
            public void save(Save row){int affected=row.previousFingerprint()==null?jdbc.update("INSERT INTO independent_binding VALUES(?,?,?,?)",row.object(),row.sourceKey(),row.targetId(),row.fingerprint()):jdbc.update("UPDATE independent_binding SET fingerprint=? WHERE domain=? AND source_key=? AND target_id=? AND fingerprint=?",row.fingerprint(),row.object(),row.sourceKey(),row.targetId(),row.previousFingerprint());if(affected!=1)throw new IllegalStateException("binding changed");}
        };
    }
    record Table(String name,Class<?> type){}
    static String column(String name){return name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    SyncContext<Void> context(boolean preview){return new SyncContext<>(1,2,3,preview,2,100,time,source,bindings,references,"Foundation",target,false,true);}
    SyncCounts run(AbstractSyncTask<?,?> task,boolean preview){return transaction.execute(status->task.execute(context(preview)));}
    void customerSource(List<P10CrmCustomerSyncTask.CustomerSource> rows){
        doAnswer(call->{ResultHandler<P10CrmCustomerSyncTask.CustomerSource> handler=call.getArgument(2);int index=0;for(var row:rows){var result=mock(ResultContext.class);when(result.getResultObject()).thenReturn(row);when(result.getResultCount()).thenReturn(++index);handler.handleResult(result);}return null;}).when(nativeSource).select(anyString(),any(),any(ResultHandler.class));
    }
    P10CrmCustomerSyncTask.CustomerSource customer(){var row=new P10CrmCustomerSyncTask.CustomerSource();row.setSourceKey("account-1");row.setCode("CUS-1");row.setName("原客户");row.setErpCustomerCode("ERP-1");row.setSourceVersion(time.toString());row.setDataAsOf(time);row.setLifecycleStatus("ENABLED");return row;}
    P10CrmCustomerSyncTask customerTask(){
        var mapper=mock(P10CrmCustomerSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(target.getMapper(P10CrmCustomerSyncTask.TargetMapper.class)));
        doReturn(1).when(mapper).erpColumnReady();doReturn(255).when(mapper).nameColumnCapacity();return new P10CrmCustomerSyncTask(mapper);
    }
    @Test void customerPreviewAndRealVersionedReplayUseOnlyNewBindings(){
        var task=customerTask();var row=customer();customerSource(List.of(row));
        assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_master",Integer.class));
        assertEquals(new SyncCounts(1,1,0,0,0),run(task,false));assertEquals(new SyncCounts(1,0,0,1,0),run(task,false));
        row.setSourceVersion(time.plusDays(1).toString());row.setDataAsOf(time.plusDays(1));row.setErpCustomerCode(null);row.setName("新客户");
        assertEquals(new SyncCounts(1,0,1,0,0),run(task,false));assertNull(jdbc.queryForObject("SELECT erp_customer_code FROM cus_customer_master",String.class));
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM cus_customer_master",Long.class));
        jdbc.update("UPDATE cus_customer_master SET remark='formal edit'");
        assertThrows(IllegalStateException.class,()->run(task,false));assertEquals("formal edit",jdbc.queryForObject("SELECT remark FROM cus_customer_master",String.class));
    }
    @Test void customerCannotAdoptExistingFormalMappingAndLaterBatchFailureRollsBackBothTables(){
        var task=customerTask();var row=customer();customerSource(List.of(row));run(task,false);
        jdbc.update("DELETE FROM independent_binding");assertThrows(IllegalStateException.class,()->run(task,true));
        jdbc.update("DELETE FROM cus_customer_external_mapping");jdbc.update("DELETE FROM cus_customer_master");
        var bad=customer();bad.setSourceKey("account-2");bad.setCode("CUS-2");bad.setMigrationIssue("DUPLICATE_CUSTOMER_CODE");
        customerSource(List.of(row,bad));assertThrows(IllegalArgumentException.class,()->run(task,false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_master",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_external_mapping",Integer.class));
    }
    List<P08DictionarySyncTask.DictionarySource> types,items;
    P08DictionarySyncTask dictionaries(){
        var type=new P08DictionarySyncTask.DictionarySource().setRawId(1L).setDataTypeCode("CODE").setDataTypeName("原类型").setDictionaryType(true).setCreateTime(time).setUpdateTime(time).setRawCreator("author");
        var item=new P08DictionarySyncTask.DictionarySource().setRawId(2L).setDataTypeCode("CODE").setBasicDataId("VALUE").setBasicDataName("原标签").setCreateTime(time).setUpdateTime(time).setRawCreator("author");
        types=new ArrayList<>(List.of(type));items=new ArrayList<>(List.of(item));
        when(nativeSource.selectList(endsWith("selectDictionaryType"),any())).thenAnswer(call->new ArrayList<>(types));
        when(nativeSource.selectList(endsWith("selectDictionaryItem"),any())).thenAnswer(call->new ArrayList<>(items));
        return new P08DictionarySyncTask(target.getMapper(P08DictionarySyncTask.TargetMapper.class));
    }
    @Test void dictionaryPreviewUsesPlannedTypesAndReplayPreservesImmutableAudit(){
        var task=dictionaries();assertEquals(new SyncCounts(2,2,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dict_type",Integer.class));
        verify(nativeSource,times(1)).selectList(endsWith("selectDictionaryType"),any());
        verify(nativeSource,times(1)).selectList(endsWith("selectDictionaryItem"),any());
        assertEquals(new SyncCounts(2,2,0,0,0),run(task,false));assertEquals(new SyncCounts(2,0,0,2,0),run(task,false));
        assertEquals("author",jdbc.queryForObject("SELECT creator FROM system_dict_data",String.class));
        items.getFirst().setBasicDataAttri1("source changed");assertThrows(IllegalStateException.class,()->run(task,true));
    }
    @Test void dictionaryUnknownActorIsCopiedWithoutDirectoryLookup(){
        var task=dictionaries();items.getFirst().setRawCreator("not-authorized");
        assertEquals(new SyncCounts(2,2,0,0,0),run(task,false));
        assertEquals("not-authorized",jdbc.queryForObject("SELECT creator FROM system_dict_data",String.class));
        verifyNoInteractions(references);
    }
    P08DictionarySyncTask initializedLevels(){
        var task=dictionaries();types.getFirst().setDataTypeCode("majorProjectLevel");items.clear();
        var labels=List.of("办事处级","市场级","公司级");
        for(int i=0;i<labels.size();i++)items.add(new P08DictionarySyncTask.DictionarySource().setRawId(471L+i)
                .setDataTypeCode("majorProjectLevel").setBasicDataId(labels.get(i)).setBasicDataName(labels.get(i))
                .setCreateTime(time).setUpdateTime(time).setRawCreator("author"));
        run(task,false);
        for(var item:items){
            var row=item.getTarget();row.setValue(item.getBasicDataId()+"重大项目");row.setUpdater("formal-admin");row.setUpdateTime(time.plusDays(1));
            jdbc.update("UPDATE system_dict_data SET `value`=?,updater=?,update_time=? WHERE id=?",row.getValue(),row.getUpdater(),row.getUpdateTime(),row.getId());
            jdbc.update("UPDATE independent_binding SET fingerprint=? WHERE domain='DICTIONARY_ITEM' AND source_key=?",P08DictionarySyncTask.fingerprint(item,row),item.sourceKey());
        }
        return task;
    }
    @Test void approvedInitializedLevelsPreserveValuesAndAuditWhileOtherItemsStillImport(){
        var task=initializedLevels();var before=jdbc.queryForList("SELECT * FROM system_dict_data ORDER BY id");
        assertEquals(new SyncCounts(4,0,0,4,0),run(task,false));
        assertEquals(before,jdbc.queryForList("SELECT * FROM system_dict_data ORDER BY id"));
        items.add(new P08DictionarySyncTask.DictionarySource().setRawId(543L).setDataTypeCode("majorProjectLevel")
                .setBasicDataId("new-level").setBasicDataName("新增项").setCreateTime(time).setUpdateTime(time).setRawCreator("author"));
        assertEquals(new SyncCounts(5,1,0,4,0),run(task,false));
        assertEquals(before,jdbc.queryForList("SELECT * FROM system_dict_data WHERE `value`<>'new-level' ORDER BY id"));
    }
    @Test void initializedLevelReferenceStillRejectsUnboundTargetSourceChangesAndTargetTampering(){
        var task=initializedLevels();var item=items.getFirst();
        item.setBasicDataAttri1("changed-source");assertThrows(IllegalStateException.class,()->run(task,false));item.setBasicDataAttri1(null);
        item.setRawId(471L+(1L<<32));
        jdbc.update("UPDATE independent_binding SET source_key=?,fingerprint=? WHERE domain='DICTIONARY_ITEM' AND source_key='471'",item.sourceKey(),P08DictionarySyncTask.fingerprint(item,item.getTarget()));
        assertThrows(IllegalStateException.class,()->run(task,false));
        String unapprovedKey=item.sourceKey();item.setRawId(471L);
        jdbc.update("UPDATE independent_binding SET source_key='471',fingerprint=? WHERE domain='DICTIONARY_ITEM' AND source_key=?",P08DictionarySyncTask.fingerprint(item,item.getTarget()),unapprovedKey);
        jdbc.update("UPDATE system_dict_data SET remark='changed-target' WHERE id=?",item.getTarget().getId());
        assertThrows(IllegalStateException.class,()->run(task,false));
        jdbc.update("UPDATE system_dict_data SET remark=NULL WHERE id=?",item.getTarget().getId());
        jdbc.update("DELETE FROM independent_binding WHERE domain='DICTIONARY_ITEM' AND source_key='471'");
        assertThrows(IllegalStateException.class,()->run(task,false));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM system_dict_data",Integer.class));
    }
    @Test void newlyDefinedTypeCanReferenceItsBoundGeneratedTypeWithoutRewritingIt(){
        var task=dictionaries();types.clear();run(task,false);
        var original=jdbc.queryForList("SELECT * FROM system_dict_type");
        var generated=new P08DictionarySyncTask.DictionarySource().setRawId(2L).setDataTypeCode("CODE")
                .setDataTypeName("CODE").setDefinitionMissing(true).setDictionaryType(true);
        var row=target.getMapper(P08DictionarySyncTask.TargetMapper.class).selectTypes(
                new P08DictionarySyncTask.TargetMapper.DefinitionsQuery(List.of("CODE"),List.of(),List.of(),true)).getFirst();
        assertEquals(P08DictionarySyncTask.fingerprint(generated,row),jdbc.queryForObject("SELECT fingerprint FROM independent_binding WHERE source_key='missing:2'",String.class));
        var definition=new P08DictionarySyncTask.DictionarySource().setRawId(33L).setDataTypeCode("CODE")
                .setDataTypeName("来源补齐的名称").setDictionaryType(true).setCreateTime(time).setUpdateTime(time).setRawCreator("author");
        types.add(definition);
        assertThrows(IllegalStateException.class,()->run(task,false));
        jdbc.update("INSERT INTO independent_binding VALUES('DICTIONARY_TYPE','33',?,?)",row.getId(),P08DictionarySyncTask.fingerprint(definition,row));
        assertEquals(new SyncCounts(2,0,0,2,0),run(task,false));assertEquals(original,jdbc.queryForList("SELECT * FROM system_dict_type"));
        jdbc.update("UPDATE independent_binding SET fingerprint='changed' WHERE source_key='missing:2'");
        assertThrows(IllegalStateException.class,()->run(task,false));
        assertEquals(original,jdbc.queryForList("SELECT * FROM system_dict_type"));
    }
    @Test void dictionarySecondStepConflictRollsBackTheNewType(){
        var task=dictionaries();items.getFirst().setRawCreator("not-authorized");
        jdbc.update("INSERT INTO system_dict_data(id,label,`value`,dict_type,status,deleted) VALUES(999,'formal label','VALUE','CODE',0,FALSE)");
        assertThrows(IllegalStateException.class,()->run(task,false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dict_type",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM independent_binding",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_dict_data",Integer.class));
        assertEquals("formal label",jdbc.queryForObject("SELECT label FROM system_dict_data",String.class));
        verifyNoInteractions(references);
    }
    @Test void dictionaryApprovedEmpty114AndSystem542RemainNarrowExceptions(){
        var task=dictionaries();var empty=new P08DictionarySyncTask.DictionarySource().setRawId(114L).setDataTypeCode("23");
        types.add(new P08DictionarySyncTask.DictionarySource().setRawId(114L).setDataTypeCode("23").setDataTypeName("23").setDictionaryType(true).setDefinitionMissing(true));
        items.add(empty);items.getFirst().setRawId(542L).setRawCreator("system").setCreateTime(null);
        assertEquals(new SyncCounts(4,3,0,1,0),run(task,true));
        empty.setBasicDataAttri1("not empty anymore");assertThrows(IllegalStateException.class,()->run(task,true));
    }
    @Test void customerValuesCannotExceedApprovedColumnsOrLoseSourceTimePrecision(){
        var row=customer();row.setErpCustomerCode("E".repeat(51));assertThrows(IllegalArgumentException.class,()->P10CrmCustomerSyncTask.validate(row));
        row.setErpCustomerCode(null);row.setAddress("A".repeat(256));assertThrows(IllegalArgumentException.class,()->P10CrmCustomerSyncTask.validate(row));
        row.setAddress(null);row.setDataAsOf(time.plusNanos(1));assertThrows(IllegalArgumentException.class,()->P10CrmCustomerSyncTask.validate(row));
    }
    @Test void customerApprovedNameCapacityPreservesTheWholeNameAndRejectsBeyond255(){
        var task=customerTask();var row=customer();row.setName("客".repeat(255));customerSource(List.of(row));
        assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_master",Integer.class));
        assertEquals(row.getName(),row.getTarget().getName());
        assertEquals(new SyncCounts(1,1,0,0,0),run(task,false));
        assertEquals(row.getName(),jdbc.queryForObject("SELECT name FROM cus_customer_master",String.class));
        assertEquals(new SyncCounts(1,0,0,1,0),run(task,false));
        row.setName("客".repeat(256));
        assertThrows(IllegalArgumentException.class,()->run(task,true));
        assertEquals("客".repeat(255),jdbc.queryForObject("SELECT name FROM cus_customer_master",String.class));
    }
    @Test void customerPreviewReportsUnappliedCapacityWhileActualExecutionStillRefuses(){
        var mapper=mock(P10CrmCustomerSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(target.getMapper(P10CrmCustomerSyncTask.TargetMapper.class)));
        doReturn(1).when(mapper).erpColumnReady();doReturn(128).when(mapper).nameColumnCapacity();
        var task=new P10CrmCustomerSyncTask(mapper);var row=customer();row.setName("客".repeat(143));customerSource(List.of(row));
        var context=context(true);
        assertEquals(new SyncCounts(1,1,0,0,0),transaction.execute(status->task.execute(context)));
        assertTrue(context.runState().warnings().contains("P10_EXECUTION_SCHEMA_REQUIRED:cus_customer_master.name:128->255"));
        assertEquals(row.getName(),row.getTarget().getName());
        assertTrue(assertThrows(IllegalStateException.class,()->run(task,false)).getMessage().startsWith("P10_TARGET_NAME_CAPACITY_REQUIRED:"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_master",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cus_customer_external_mapping",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM independent_binding",Integer.class));
        verify(nativeSource,times(1)).select(anyString(),any(),any(ResultHandler.class));
    }
}
