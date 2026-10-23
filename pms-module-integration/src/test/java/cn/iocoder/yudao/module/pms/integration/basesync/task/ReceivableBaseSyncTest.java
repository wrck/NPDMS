package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P24ReceivableSyncTask.*;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncReferences;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceivableBaseSyncTest {
    JdbcTemplate jdbc;P24ReceivableSyncTask task;TargetMapper target;SyncSource source;
    TransactionTemplate transaction;SyncIdentityStore identities;SyncReferences references;
    SyncSource.Session external;SourceMapper sourceMapper;SqlSessionTemplate session;
    @BeforeEach void setup() throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:base-p24-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(data);
        new ResourceDatabasePopulator(new ClassPathResource("sql/basesync/p24-fixture.sql")).execute(data);
        var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),data));
        for(String resource:List.of("base-sync-source/P24SourceMapper.xml","mapper/basesync/P24TargetMapper.xml","mapper/basesync/BaseSyncReferencesMapper.xml"))
            try(var input=new ClassPathResource(resource).getInputStream()){
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        target=session.getMapper(TargetMapper.class);
        source=mock(SyncSource.class);external=mock(SyncSource.Session.class);
        when(source.open(3)).thenReturn(external);
        when(external.sqlSession()).thenReturn(session);
        sourceMapper=session.getMapper(SourceMapper.class);
        when(external.mapper(SourceMapper.class)).thenReturn(sourceMapper);
        task=new P24ReceivableSyncTask(target);
        references=spy(new BaseSyncReferences(session.getMapper(BaseSyncReferences.ReferenceMapper.class)));
        transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
        identities=new SyncIdentityStore(){
            public List<Identity> find(Query query){
                var result=new ArrayList<Identity>();
                for(String key:query.sourceKeys()) result.addAll(jdbc.query("SELECT source_key,target_id,fingerprint FROM test_binding WHERE source_key=?",
                        (rs,n)->new Identity(rs.getString(1),rs.getLong(2),rs.getString(3)),key));
                return result;
            }
            public void save(Save row){
                int changed=row.previousFingerprint()==null
                        ?jdbc.update("INSERT INTO test_binding VALUES(?,?,?)",row.sourceKey(),row.targetId(),row.fingerprint())
                        :jdbc.update("UPDATE test_binding SET fingerprint=? WHERE source_key=? AND target_id=? AND fingerprint=?",
                        row.fingerprint(),row.sourceKey(),row.targetId(),row.previousFingerprint());
                if(changed!=1)throw new IllegalStateException("test binding conflict");
            }
        };
    }
    SyncContext<Void> context(boolean preview){return new SyncContext<Void>(1,2,3,preview,1,10,LocalDateTime.of(2026,10,17,12,0),source,identities,references,"BASE_P24",session,false,true);}
    SyncCounts run(boolean preview){return transaction.execute(status->task.execute(context(preview)));}
    ReceivableSyncRow row(){return target.selectExisting(new TargetMapper.ExistingQuery(1,List.of(new TargetMapper.SourceKey("SAP","1")),true)).getFirst();}

    void copySource(long id,String contract){
        jdbc.update("INSERT INTO sms_ofst_contract_head_sap SELECT ?,?,batch_code,project_name,order_num,client_supplier_code,client_supplier_name,contract_money_amount,delivered_money_amount,collected_money_amount,collected_money_ratio,receivables_money_amount,over_due_money_amount,maketing_department_name,office_name,industry_name,marketing_representative_name,currency_name,create_by,create_time,update_by,update_time,effective_from,effective_to,import_batch_num,contract_create_date,projectCode,marketCode,systemId,industryId,officeCode,expendId,usernamec,latest_ship_date,usernamec2,systemid_o,expendid_o,industry_name_o,dataSource FROM sms_ofst_contract_head_sap WHERE id=1",id,contract);
    }

    @Test void nativeMapperWritesFirstBatchBeforeSourceQueryFinishes(){
        copySource(2,"CON-2");
        int[] read={0};boolean[] finished={false};
        var observed=mock(org.apache.ibatis.session.SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(session));
        doAnswer(call->{
            org.apache.ibatis.session.ResultHandler<ReceivableSyncSource> handler=call.getArgument(2);
            session.<ReceivableSyncSource>select(call.getArgument(0),call.getArgument(1),result->{
                read[0]++;handler.handleResult(result);
                assertEquals(read[0],jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
                assertFalse(finished[0]);
            });
            finished[0]=true;return null;
        }).when(observed).select(anyString(),any(),any(org.apache.ibatis.session.ResultHandler.class));
        when(external.sqlSession()).thenReturn(observed);
        assertEquals(new SyncCounts(2,2,0,0,0),run(false));
        assertTrue(finished[0]);verify(external).close();
        verifyNoInteractions(references);
    }

    @Test void streamingRowLimitRollsBackEarlierBusinessAndIdentityWrites(){
        copySource(2,"CON-2");
        var limited=new SyncContext<Void>(1,2,3,false,1,1,context(false).startedAt(),source,identities,references,"BASE_P24",session,false,true);
        assertThrows(IllegalArgumentException.class,()->transaction.execute(status->task.execute(limited)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
        verify(external).close();
    }
    @Test void twoReceivablesUseOneMappedBatchInsertAndSaveBothIdentities(){
        copySource(2,"CON-2");
        var writes=mock(org.apache.ibatis.session.SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(session));
        var batched=new SyncContext<Void>(1,2,3,false,2,10,context(false).startedAt(),source,identities,references,"BASE_P24",writes,false,true);
        assertEquals(new SyncCounts(2,2,0,0,0),transaction.execute(status->task.execute(batched)));
        verify(writes,times(1)).insert(anyString(),any(SyncBatch.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
    }

    @Test void largeBatchChecksEverySourceKeyWithoutAnUnboundedOrExpression(){
        for(int id=2;id<=201;id++)copySource(id,"CON-"+id);
        var observed=mock(TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(target));
        task=new P24ReceivableSyncTask(observed);
        var batch=new SyncContext<Void>(1,2,3,true,500,300,context(true).startedAt(),source,identities,references,"BASE_P24",session,false,true);
        assertEquals(new SyncCounts(201,201,0,0,0),transaction.execute(status->task.execute(batch)));
        var captured=org.mockito.ArgumentCaptor.forClass(TargetMapper.ExistingQuery.class);
        verify(observed,times(3)).selectExisting(captured.capture());
        assertEquals(List.of(100,100,1),captured.getAllValues().stream().map(query->query.keys().size()).toList());
        assertEquals(201,captured.getAllValues().stream().flatMap(query->query.keys().stream()).distinct().count());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
    }

    @Test void originalQueryPrefillsReferencesAndWritesAllBusinessFields(){
        assertEquals(new SyncCounts(1,1,0,0,0),run(false));var row=row();
        assertEquals(52,P24ReceivableSyncTask.values(row).size());
        assertNull(row.getContractId());assertEquals("CON-1",row.getContractNo());
        assertEquals("BATCH",row.getSourceBatchCode());assertEquals("项目名称",row.getProjectName());
        assertEquals("SO-1",row.getSourceOrderNo());assertEquals("CUS-1",row.getCustomerCode());assertEquals("客户名称",row.getCustomerName());
        assertEquals(new BigDecimal("123456789012345678.90"),row.getContractAmount());
        assertEquals(new BigDecimal("21.23"),row.getDeliveredAmount());assertEquals(new BigDecimal("12.34"),row.getCollectedAmount());
        assertEquals(new BigDecimal("0.125000"),row.getCollectedRatio());assertEquals(new BigDecimal("56.78"),row.getReceivableAmount());
        assertEquals(new BigDecimal("9.10"),row.getOverdueAmount());assertEquals("市场",row.getMarketName());
        assertEquals("办事处",row.getDepartmentName());assertEquals("行业",row.getIndustryName());
        assertEquals("市场代表",row.getMarketingRepresentativeName());assertEquals("CNY",row.getCurrencyName());
        assertEquals("author",row.getCreator());assertEquals("unmapped",row.getUpdater());
        assertEquals(LocalDateTime.of(2020,1,2,3,4,5),row.getCreateTime());
        assertEquals(LocalDateTime.of(2021,2,3,4,5,6),row.getUpdateTime());assertEquals(row.getUpdateTime(),row.getSourceSyncTime());
        assertEquals(LocalDateTime.of(2019,1,1,0,0),row.getSourceEffectiveFrom());
        assertEquals(LocalDateTime.of(2023,1,1,0,0),row.getSourceEffectiveTo());assertEquals("IMPORT",row.getImportBatchNo());
        assertEquals(LocalDateTime.of(2018,1,1,0,0),row.getContractCreateTime());assertEquals("PROJECT-1",row.getProjectCode());
        assertEquals("M-1",row.getMarketCode());assertEquals("101",row.getSystemSourceKey());assertEquals("102",row.getIndustryCode());
        assertEquals("D-1",row.getDepartmentCode());assertEquals("103",row.getExpendSourceKey());
        assertEquals("REP-1",row.getMarketingRepresentativeCode());assertEquals("REP-2",row.getSecondaryRepresentativeCode());
        assertEquals(LocalDateTime.of(2022,1,1,0,0),row.getLatestShipTime());
        assertTrue(row.getOriginalDivisionValues().contains("201"));assertTrue(row.getOriginalDivisionValues().contains("202"));
        assertTrue(row.getOriginalDivisionValues().contains("原行业"));assertEquals("SAP",row.getSourceSystem());assertEquals("1",row.getSourceRecordKey());
        assertEquals("PENDING_COMPANY",row.getMappingStatus());assertNull(row.getCompanyId());assertNull(row.getDepartmentId());
        assertNull(row.getSystemCode());assertNull(row.getExpendCode());assertEquals(1L,row.getTenantId());assertFalse(row.getDeleted());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
        assertEquals(new SyncCounts(1,0,0,1,0),run(false));
    }
    @Test void previewDoesNotWriteBusinessOrIdentity(){
        assertEquals(new SyncCounts(1,1,0,0,0),run(true));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
    }
    @Test void originalAuditUsernamesAreRetainedWithoutAccountDirectoryLookup(){
        jdbc.update("INSERT INTO system_users VALUES(200,1,'author',FALSE,0)");
        assertEquals(new SyncCounts(1,1,0,0,0),run(false));
        var planned=new HashMap<String,ReceivableSyncRow>();
        task=spy(task);
        doAnswer(call->{
            SyncBatch<ReceivableSyncSource,Void> batch=call.getArgument(0);call.callRealMethod();
            batch.rows().forEach(source->planned.put(source.getSourceId().toString(),source.getTarget()));
            return null;
        }).when(task).syncDataInsertBefore(any());
        var usernames=List.of("unknown-user"," 未开户User ","");
        for(int index=0;index<usernames.size();index++){
            long id=index+2;copySource(id,"CON-"+id);
            jdbc.update("UPDATE sms_ofst_contract_head_sap SET create_by=?,update_by=? WHERE id=?",usernames.get(index),usernames.get(index),id);
        }
        assertEquals(new SyncCounts(4,3,0,1,0),run(true));
        assertEquals("author",row().getCreator());assertEquals("unmapped",row().getUpdater());
        for(int index=0;index<usernames.size();index++){
            var saved=planned.get(Integer.toString(index+2));
            assertEquals(usernames.get(index),saved.getCreator());assertEquals(usernames.get(index),saved.getUpdater());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
        verifyNoInteractions(references);
    }
    @Test void nullAuditUsernamesRemainRejectedByReceivableRequiredFields(){
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET create_by=NULL");
        assertEquals("回款必填字段缺失：creator",assertThrows(IllegalArgumentException.class,()->run(true)).getMessage());
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET create_by='author',update_by=NULL");
        assertEquals("回款必填字段缺失：updater",assertThrows(IllegalArgumentException.class,()->run(true)).getMessage());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
        verifyNoInteractions(references);
    }
    @Test void commonAuditLookupRetainsDisabledUsersButExcludesOtherTenantsDeletedAndCaseVariants(){
        assertEquals(List.of(new SyncReferences.AuditUser(99,"author")),references.findAuditUsers(
                new SyncReferences.AuditUsersQuery(1,List.of("author","unmapped"))));
        assertTrue(references.findAuditUsers(new SyncReferences.AuditUsersQuery(1,List.of())).isEmpty());
    }
    @Test void changedSourceUpdatesOnlyWithExactBaselineAndRetainsFormalAssociation(){
        run(false);long id=row().getId();
        // A previously confirmed association belongs to the formal row and must survive source refresh.
        jdbc.update("UPDATE com_contract_receivable SET contract_id=123");
        jdbc.update("UPDATE test_binding SET fingerprint=?",P24ReceivableSyncTask.fingerprint(row()));
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET contract_num='NO-MATCH',collected_money_amount=22.22");
        assertEquals(new SyncCounts(1,0,1,0,0),run(false));
        assertEquals(id,row().getId());assertEquals(123L,row().getContractId());
        jdbc.update("UPDATE com_contract_receivable SET company_name='formal edit'");
        assertThrows(IllegalStateException.class,()->run(false));
        assertEquals("formal edit",row().getCompanyName());
    }
    @Test void existingTargetWithoutNewSuiteBindingCannotBeAdopted(){
        run(false);jdbc.update("DELETE FROM test_binding");
        assertThrows(IllegalStateException.class,()->run(false));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
    }
    @Test void nullableContractIsRetainedAsNoWriteAndAmbiguousContractIsNotChosen(){
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET contract_num=NULL");
        assertEquals(new SyncCounts(1,0,0,1,0),run(false));
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET contract_num='CON-1'");
        jdbc.update("INSERT INTO com_contract VALUES(124,1,'CON-1',FALSE)");
        run(false);assertNull(row().getContractId());
    }
    @Test void ratioCannotBeRoundedAndMissingAuditCannotBeInvented(){
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET collected_money_ratio=0.1234567");
        assertThrows(IllegalArgumentException.class,()->run(true));
        jdbc.update("UPDATE sms_ofst_contract_head_sap SET collected_money_ratio=0.125,create_time=NULL");
        assertThrows(IllegalArgumentException.class,()->run(false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
    }
    @Test void failedSecondBatchRollsBackFirstRowAndItsBinding(){
        copySource(2,"FAIL");
        assertThrows(RuntimeException.class,()->run(false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract_receivable",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_binding",Integer.class));
    }
    @Test void targetLookupIsTenantScopedAndOptionalContractIdDoesNotTriggerAssociation(){
        jdbc.update("DELETE FROM com_contract");jdbc.update("INSERT INTO com_contract VALUES(123,2,'CON-1',FALSE)");
        run(false);assertNull(row().getContractId());
        assertTrue(target.selectExisting(new TargetMapper.ExistingQuery(2,List.of(new TargetMapper.SourceKey("SAP","1")),true)).isEmpty());
        assertTrue(target.selectExisting(new TargetMapper.ExistingQuery(1,List.of(),true)).isEmpty());
    }
}
