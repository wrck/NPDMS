package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native source/local field parity; every populated table is connection-local. */
class P15ContractInputMySqlTest extends RemainingTaskMySqlSupport {
 P15DeliverySyncTask.TargetMapper mapper;
 @BeforeEach void contractInputs()throws Exception{
  for(var table:List.of("com_contract","com_shipment_contract_reference","com_contract_receivable")){
   var structure="p15_"+table+"_structure";jdbc.execute("CREATE TEMPORARY TABLE "+structure+" LIKE npdms_reverify_20261014."+table);jdbc.execute("CREATE TEMPORARY TABLE "+table+" LIKE "+structure);
  }
  jdbc.execute("CREATE TEMPORARY TABLE fb_contract(contract_id VARCHAR(64),contract_code VARCHAR(25),project_name VARCHAR(512),marketCode VARCHAR(64),marketName VARCHAR(128),office_code VARCHAR(64),systemName VARCHAR(128),contract_type INT,customer_name VARCHAR(128),warranty VARCHAR(32),systemId BIGINT,remark VARCHAR(1024)) DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci");
  jdbc.execute("CREATE TEMPORARY TABLE sms_ofst_contract_head_sap(id BIGINT PRIMARY KEY,contract_num VARCHAR(45),currency_name VARCHAR(32),contract_money_amount DECIMAL(20,2),contract_create_date DATETIME,project_name VARCHAR(512),projectCode VARCHAR(80),marketCode VARCHAR(64),maketing_department_name VARCHAR(128),officeCode VARCHAR(64),office_name VARCHAR(128),industryId VARCHAR(64),industry_name VARCHAR(128),usernamec VARCHAR(64),marketing_representative_name VARCHAR(128),usernamec2 VARCHAR(64),update_time DATETIME,dataSource VARCHAR(25)) DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci");
  var resource="base-sync-source/P15SourceMapper.xml";try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,session.getConfiguration(),resource,session.getConfiguration().getSqlFragments()).parse();}
  order("A");order("B");jdbc.update("UPDATE pm_order_data_from_erp SET customerName='buyer'");
  jdbc.update("INSERT INTO fb_contract(contract_id,contract_code,project_name,marketCode,marketName,office_code,systemName) VALUES('A','C','shipment project','M1','old market','D1','system'),('B','C','','M2','new market',NULL,NULL)");
  jdbc.update("INSERT INTO sms_ofst_contract_head_sap VALUES(10,'C','CNY',10.00,'2019-01-01','receipt project','RP10','RM10','receipt market','RD10','receipt department','I10','industry','USER10','sales name','USER2','2020-01-03','CRM'),(11,'C',NULL,20.00,NULL,'older project','RP11',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2020-01-02','CRM')");
  jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows) VALUES(10,1,'delivery','BASE_P15','DPPMS',3,1,100),(20,1,'receivable','BASE_P24','DPPMS',3,1,100)");
  jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,company_code,order_type,order_no,contract_no,customer_code,customer_name,status,creator,updater) VALUES(1,1,'SAP','001','0','A','C','C000001','buyer','ENABLED','fixture','fixture')");
  jdbc.update("INSERT INTO com_shipment_contract_reference(id,tenant_id,contract_no,project_name,market_code,market_name,department_code,system_name,source_system,source_record_key,creator,updater) SELECT CASE contract_id WHEN 'A' THEN 1 ELSE 2 END,1,contract_code,project_name,marketCode,marketName,office_code,systemName,'DPPMS',contract_id,'fixture','fixture' FROM fb_contract");
  jdbc.update("INSERT INTO com_contract_receivable(id,tenant_id,contract_no,currency_name,contract_amount,delivered_amount,collected_amount,contract_create_time,project_name,project_code,market_code,market_name,department_code,department_name,industry_code,industry_name,marketing_representative_code,marketing_representative_name,secondary_representative_code,source_sync_time,source_system,source_record_key,creator,updater,create_time,update_time) SELECT id,1,contract_num,currency_name,contract_money_amount,0,0,contract_create_date,project_name,projectCode,marketCode,maketing_department_name,officeCode,office_name,industryId,industry_name,usernamec,marketing_representative_name,usernamec2,update_time,dataSource,CAST(id AS CHAR),'fixture','fixture','2020-01-01',update_time FROM sms_ofst_contract_head_sap");
  jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(1,1,10,'SHIPMENT_CONTRACT','A',1,?,1),(2,1,10,'SHIPMENT_CONTRACT','B',2,?,1),(3,1,20,'RECEIVABLE','10',10,?,1),(4,1,20,'RECEIVABLE','11',11,?,1)","a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(64));
  for(var table:List.of("int_base_sync_task","int_base_sync_binding"))jdbc.execute("CREATE TEMPORARY TABLE "+table+"_reference LIKE "+table);
  var delegate=session.getMapper(P15DeliverySyncTask.TargetMapper.class);mapper=mock(P15DeliverySyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(delegate));doReturn(List.of()).when(mapper).selectCarriers(any());
 }
 List<P15DeliverySyncTask.Source> localContracts(){
  var query=new AbstractSyncTask.SourceQuery<>(100,1,true,new P15DeliverySyncTask.Parameters(Map.of(),3));var config=session.getConfiguration();var bound=config.getMappedStatement(P15DeliverySyncTask.TargetMapper.class.getName()+".selectContract").getBoundSql(query);String sql=bound.getSql();
  // MySQL cannot reopen one TEMPORARY table in two subqueries. The oracle uses identical connection-local copies.
  for(var table:List.of("int_base_sync_task","int_base_sync_binding")){
   jdbc.update("DELETE FROM "+table+"_reference");jdbc.update("INSERT INTO "+table+"_reference SELECT * FROM "+table);
   var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+"+table+"\\b").matcher(sql);int occurrence=0;var output=new StringBuffer();
   while(matcher.find())matcher.appendReplacement(output,matcher.group(1)+" "+table+(++occurrence==1?"":"_reference"));matcher.appendTail(output);sql=output.toString();
  }
  var values=config.newMetaObject(query);var rowMapper=new org.springframework.jdbc.core.BeanPropertyRowMapper<>(P15DeliverySyncTask.Row.class);
  return jdbc.query(sql,statement->{int index=0;for(var parameter:bound.getParameterMappings())statement.setObject(++index,values.getValue(parameter.getProperty()));},(result,index)->new P15DeliverySyncTask.Source().setKey(result.getString("raw_key")).setIssue(result.getString("issue")).setTarget(rowMapper.mapRow(result,index)));
 }
 @Test void localMergeIncludesOwnedCrmReceivablesAndIgnoresUnboundTargetRows(){
  jdbc.update("INSERT INTO com_contract_receivable(id,tenant_id,contract_no,currency_name,contract_amount,delivered_amount,collected_amount,project_code,source_sync_time,source_system,source_record_key,creator,updater,create_time,update_time) VALUES(99,1,'C','BAD',999,0,0,'UNOWNED','2030-01-01','CRM','99','fixture','fixture','2020-01-01','2020-01-01')");
  var row=localContracts().getFirst().getTarget();assertEquals("CNY",row.getCurrencyName());assertEquals("RP10",row.getProjectCode());assertEquals("shipment project",row.getProjectName());assertEquals("M2",row.getMarketCode());assertEquals("receipt department",row.getDepartmentName());assertEquals("system",row.getSystemName());
 }
 @Test void threeRawReadsMatchTheFullyMaterializedLocalMergeWithoutWritingInputs(){
  var task=new ContractProbe(mapper);var raw=task.query(context(true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3)));
  assertEquals(1,raw.size());var expected=localContracts().getFirst();assertEquals(expected.getKey(),raw.getFirst().getKey());assertNull(raw.getFirst().getIssue());
  assertEquals(P15DeliverySyncTask.business(new P15DeliverySyncTask.Validation("Contract",expected.getTarget())),P15DeliverySyncTask.business(new P15DeliverySyncTask.Validation("Contract",raw.getFirst().getTarget())));
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
 }
 @Test void actualContractGenerationDoesNotDependOnFormalOrderMaterialization(){
  jdbc.update("DELETE FROM com_sales_order");var task=new ContractProbe(mapper);
  assertEquals(1,task.queryLocal(context(false).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3))).size());
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,false));
  assertEquals("C00001",jdbc.queryForObject("SELECT customer_code FROM com_contract",String.class));
  assertEquals("shipment project",jdbc.queryForObject("SELECT project_name FROM com_contract",String.class));
  assertEquals("RP10",jdbc.queryForObject("SELECT project_code FROM com_contract",String.class));
  assertEquals(new SyncCounts(1,0,0,1,0),run(task,false));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));
  jdbc.update("UPDATE com_contract SET customer_name='manual change'");
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("EXISTING_FACT_PROTECTED"));
 }
  void materializeOrderInputs(){
   jdbc.update("DELETE FROM com_sales_order");
   jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,company_code,order_type,order_no,contract_no,customer_code,customer_name,status,creator,updater) SELECT id,1,source,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' ELSE compCode END,CAST(orderType AS CHAR),orderNumber,contractNo,customerCode,customerName,'ENABLED','fixture','fixture' FROM pm_order_data_from_erp");
  }
  @Test void maximumPositiveOrderSuppliesTheCustomerInRawAndLocalInputs(){
   jdbc.update("UPDATE pm_order_data_from_erp SET orderNumber='SO200',customerCode='C000009',customerName='selected buyer' WHERE id=1");
   jdbc.update("UPDATE pm_order_data_from_erp SET orderNumber='SO100',customerCode='C000099',customerName='older buyer' WHERE id=2");
   jdbc.update("INSERT INTO pm_order_data_from_erp(id,orderNumber,compCode,orderType,source,syncTime,contractNo,customerCode,customerName) VALUES(3,'ZZRETURN','01',1,'SAP','2030-01-01','C','C000088','return buyer')");
   materializeOrderInputs();var task=new ContractProbe(mapper);var raw=task.query(context(true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3))).getFirst();var local=localContracts().getFirst();
   assertNull(raw.getIssue());assertNull(local.getIssue());assertEquals("C00009",raw.getTarget().getCustomerCode());assertEquals("selected buyer",raw.getTarget().getCustomerName());
   assertEquals(P15DeliverySyncTask.business(new P15DeliverySyncTask.Validation("Contract",local.getTarget())),P15DeliverySyncTask.business(new P15DeliverySyncTask.Validation("Contract",raw.getTarget())));
   assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
  }
  @Test void orderNumberMaximumUsesTheSameExactOrderingInBothPaths(){
   jdbc.update("UPDATE pm_order_data_from_erp SET orderNumber='a',customerCode='C000009',customerName='selected buyer' WHERE id=1");
   jdbc.update("UPDATE pm_order_data_from_erp SET customerCode='C000099',customerName='other buyer' WHERE id=2");
   materializeOrderInputs();var raw=new ContractProbe(mapper).query(context(true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3))).getFirst();var local=localContracts().getFirst();
   assertNull(raw.getIssue());assertNull(local.getIssue());assertEquals("C00009",raw.getTarget().getCustomerCode());assertEquals(raw.getTarget().getCustomerCode(),local.getTarget().getCustomerCode());assertEquals(raw.getTarget().getCustomerName(),local.getTarget().getCustomerName());assertTrue(saved.isEmpty());
  }
  @Test void conflictingCustomersOnTheMaximumOrderStillFailInBothPaths(){
   jdbc.update("UPDATE pm_order_data_from_erp SET orderNumber='A',source='D365',customerCode='C000099',customerName='different buyer' WHERE id=2");
   materializeOrderInputs();var task=new ContractProbe(mapper);assertEquals("CONTRACT_CUSTOMER_CONFLICT",localContracts().getFirst().getIssue());
   assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("CONTRACT_CUSTOMER_CONFLICT"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
  }
  @Test void contractsWithoutPositiveOrdersUseTheMaximumReturnOrderCustomer(){
   jdbc.update("UPDATE pm_order_data_from_erp SET orderType=1");jdbc.update("UPDATE pm_order_data_from_erp SET customerCode='C000099',customerName='different buyer' WHERE id=2");
   materializeOrderInputs();var task=new ContractProbe(mapper);var local=localContracts().getFirst();var raw=task.query(context(true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3))).getFirst();
   assertNull(local.getIssue());assertNull(raw.getIssue());assertEquals("C00099",raw.getTarget().getCustomerCode());assertEquals("different buyer",raw.getTarget().getCustomerName());assertEquals(raw.getTarget().getCustomerCode(),local.getTarget().getCustomerCode());assertEquals(raw.getTarget().getCustomerName(),local.getTarget().getCustomerName());
   assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
  }
  @Test void conflictingCustomersOnTheMaximumReturnOrderStillFailInBothPaths(){
   jdbc.update("UPDATE pm_order_data_from_erp SET orderType=1");jdbc.update("UPDATE pm_order_data_from_erp SET orderNumber='A',source='D365',customerCode='C000099',customerName='different buyer' WHERE id=2");
   materializeOrderInputs();var task=new ContractProbe(mapper);assertEquals("CONTRACT_CUSTOMER_CONFLICT",localContracts().getFirst().getIssue());
   assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("CONTRACT_CUSTOMER_CONFLICT"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
  }
  @Test void invalidCustomerCodeOnTheSelectedOrderStillFailsInBothPaths(){
   jdbc.update("UPDATE pm_order_data_from_erp SET customerCode='INVALID-CUSTOMER-CODE' WHERE id=2");materializeOrderInputs();
   assertEquals("ERP_CUSTOMER_CODE_FORMAT_INVALID",localContracts().getFirst().getIssue());assertTrue(assertThrows(IllegalArgumentException.class,()->run(new ContractProbe(mapper),true)).getMessage().contains("ERP_CUSTOMER_CODE_FORMAT_INVALID"));assertTrue(saved.isEmpty());
  }
 @Test void approvedSourceFilterRequiresBothShipmentContractIdAndNumberToBeEmpty(){
  jdbc.update("INSERT INTO fb_contract(contract_id,contract_code) VALUES('',''),(NULL,NULL)");
  var task=new ContractProbe(mapper);var context=context(true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
  var valid=task.shipments(context);assertEquals(2,valid.size());assertEquals(Set.of("A","B"),valid.stream().map(P15DeliverySyncTask.Source::getKey).collect(java.util.stream.Collectors.toSet()));
  jdbc.update("INSERT INTO fb_contract(contract_id,contract_code) VALUES('','C-NEEDS-IDENTITY'),('D','')");
  var rows=task.shipments(context);assertEquals(4,rows.size());assertTrue(rows.stream().anyMatch(row->"".equals(row.getKey())&&"C-NEEDS-IDENTITY".equals(row.getTarget().getContractNo())));assertTrue(rows.stream().anyMatch(row->"D".equals(row.getKey())));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
 }
 @Test void fullContractPreviewKeepsTargetIdentityChecksWithinTheConfiguredBatchSize(){
  jdbc.update("INSERT INTO pm_order_data_from_erp(id,orderNumber,compCode,orderType,source,syncTime,contractNo,customerCode,customerName) VALUES(3,'D','01','0','SAP','2020-01-01','D','C000001','buyer')");
  assertEquals(new SyncCounts(2,2,0,0,0),run(new ContractProbe(mapper),true));
  var queries=org.mockito.ArgumentCaptor.forClass(P15DeliverySyncTask.ContractsQuery.class);verify(mapper,times(2)).selectContracts(queries.capture());assertTrue(queries.getAllValues().stream().allMatch(query->query.keys().size()==1));
  var identitiesRead=org.mockito.ArgumentCaptor.forClass(SyncIdentityStore.Query.class);verify(identities,times(2)).findByKeys(identitiesRead.capture());assertTrue(identitiesRead.getAllValues().stream().allMatch(query->query.sourceKeys().size()==1));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
 }
 @Test void shipmentStreamReadsEachRawLookupOnceAndPreservesExactLeftJoinFields(){
  jdbc.execute("CREATE TEMPORARY TABLE fb_shipment(packlist_id VARCHAR(64),con_id VARCHAR(64),packdate DATETIME) DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci");
  jdbc.execute("CREATE TEMPORARY TABLE fb_shipment_barcode(id BIGINT,barcode VARCHAR(64),pack_id VARCHAR(64),rma_no VARCHAR(64),warrantyStartDate DATE,warrantyMonth BIGINT,rmaBarcode VARCHAR(64),updateTime DATETIME,orderNumber VARCHAR(64),lineNum BIGINT,item VARCHAR(64),barcode2 VARCHAR(64),item2 VARCHAR(64),com_barcode VARCHAR(64),isRMA INT,profitCenter VARCHAR(64),soleAgentSuffix VARCHAR(64),syncTime DATETIME,uuid VARCHAR(64)) DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci");
  jdbc.update("INSERT INTO fb_shipment VALUES('PK','A','2020-01-01'),('WITHOUT-CONTRACT','missing','2020-02-01')");
  jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,pack_id,uuid) VALUES(1,'SN-1','PK','shipment-uuid-1'),(2,'SN-2','pk','shipment-uuid-2'),(3,'SN-3','WITHOUT-CONTRACT','shipment-uuid-3'),(4,'SN-4',NULL,'shipment-uuid-4'),(5,'SN-5','missing','shipment-uuid-5')");
  var readMapper=spy(session.getMapper(P15DeliverySyncTask.SourceMapper.class));SyncSource readSource=id->new SyncSource.Session(){public org.apache.ibatis.session.SqlSession sqlSession(){return session;}public <M>M mapper(Class<M> type){return type.cast(readMapper);}public void close(){}};
  var context=new SyncContext<Void>(1,2,3,true,1,10000,at,readSource,identities,mock(SyncReferences.class),"BASE_P15",session,false,true).forParameters(new P15DeliverySyncTask.Parameters(Map.of(),3));
  var target=mock(P15DeliverySyncTask.TargetMapper.class);var task=new EventProbe(target);var rows=task.queryEvents(context);assertEquals(5,rows.size());var byKey=new HashMap<String,P15DeliverySyncTask.Row>();for(var row:rows)byKey.put(row.getKey(),row.getTarget());
  assertEquals(java.time.LocalDateTime.of(2020,1,1,0,0),byKey.get("shipment-uuid-1").getShipmentTime());assertEquals("C",byKey.get("shipment-uuid-1").getContractNo());assertNull(byKey.get("shipment-uuid-2").getShipmentTime());assertNull(byKey.get("shipment-uuid-2").getContractNo());assertEquals(java.time.LocalDateTime.of(2020,2,1,0,0),byKey.get("shipment-uuid-3").getShipmentTime());assertNull(byKey.get("shipment-uuid-3").getContractNo());for(var key:List.of("shipment-uuid-4","shipment-uuid-5")){assertNull(byKey.get(key).getShipmentTime());assertNull(byKey.get(key).getContractNo());}
  var lookups=mockingDetails(readMapper).getInvocations().stream().map(invocation->invocation.getMethod().getName()).toList();assertEquals(List.of("selectShipmentPackageReferences","selectShipmentContractReferences"),lookups);
  assertEquals(new SyncCounts(5,5,0,0,0),run(task,true));assertTrue(saved.isEmpty());
  for(var row:rows){assertEquals(row.getKey(),row.getTarget().getSourceKey());assertEquals(row.getKey(),row.getTarget().getUuid());}
  jdbc.update("UPDATE fb_shipment_barcode SET id=id+1000");
  assertEquals(byKey.keySet(),task.queryEvents(context).stream().map(P15DeliverySyncTask.Source::getKey).collect(java.util.stream.Collectors.toSet()));
  var lines=new ArrayList<P15DeliverySyncTask.Source>();
  session.getMapper(P15DeliverySyncTask.SourceMapper.class).selectShipmentOrderLine(new AbstractSyncTask.SourceQuery<>(100,1,true,context.parameters()),result->lines.add(result.getResultObject()));
  assertEquals(byKey.keySet(),lines.stream().map(P15DeliverySyncTask.Source::getKey).collect(java.util.stream.Collectors.toSet()));
  for(String uuid:Arrays.asList(null,""," ")){
   jdbc.update("UPDATE fb_shipment_barcode SET uuid=? WHERE id=1001",uuid);
   assertTrue(assertThrows(IllegalArgumentException.class,()->task.queryEvents(context)).getMessage().contains("SHIPMENT_UUID_MISSING"));
  }
 }
 @Test void legacyShipmentIdentityGuardRequiresExplicitMigrationAndKeepsTenantAndConnectionScope(){
  jdbc.execute("CREATE TEMPORARY TABLE ast_device_shipment(id BIGINT PRIMARY KEY,tenant_id BIGINT,source_key VARCHAR(128),uuid VARCHAR(64))");
  var query=new P15DeliverySyncTask.PrerequisiteQuery(1,3);
  assertFalse(mapper.hasLegacyShipmentIdentities(query));
  jdbc.update("INSERT INTO ast_device_shipment VALUES(100,1,'123','shipment-uuid-123')");
  jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(99,1,10,'SHIPMENT_EVENT','123',100,?,1)","a".repeat(64));
  assertTrue(mapper.hasLegacyShipmentIdentities(query));
  assertFalse(mapper.hasLegacyShipmentIdentities(new P15DeliverySyncTask.PrerequisiteQuery(2,3)));
  assertFalse(mapper.hasLegacyShipmentIdentities(new P15DeliverySyncTask.PrerequisiteQuery(1,4)));
  jdbc.update("UPDATE ast_device_shipment SET source_key=uuid");
  assertTrue(mapper.hasLegacyShipmentIdentities(query));
  jdbc.update("UPDATE int_base_sync_binding SET source_key='shipment-uuid-123' WHERE id=99");
  assertFalse(mapper.hasLegacyShipmentIdentities(query));
  jdbc.update("DELETE FROM ast_device_shipment");
  assertTrue(mapper.hasLegacyShipmentIdentities(query));
 }
 @Test void relationshipVarcharTimePreservesAllSupportedPrecisionAndRejectsLoss(){
  jdbc.execute("CREATE TEMPORARY TABLE fb_shipment_barcode_relation(id BIGINT,sn1 VARCHAR(100),sn2 VARCHAR(100),contract VARCHAR(64),createtime VARCHAR(50),updatetime VARCHAR(50),item1 VARCHAR(15),item2 VARCHAR(15))");
  jdbc.update("INSERT INTO fb_shipment_barcode_relation VALUES(26,'SN-1','SN-2','C','2019-11-14 08:31:39.6900000','2019-11-16 17:38:46.5970000','P1','P2')");
  var task=new RelationshipProbe(mock(P15DeliverySyncTask.TargetMapper.class));assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));
  var row=task.planned.getFirst().getTarget();assertEquals(java.time.LocalDateTime.of(2019,11,14,8,31,39,690000000),row.getEffectiveFrom());assertEquals("2019-11-16 17:38:46.5970000",row.getSourceVersion());assertTrue(saved.isEmpty());
  jdbc.update("UPDATE fb_shipment_barcode_relation SET createtime='2019-11-14 08:31:39.6901234'");
  assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,true)).getMessage().contains("RELATIONSHIP_TIME_PRECISION_EXCEEDS_TARGET"));assertTrue(assertThrows(IllegalArgumentException.class,()->run(task,false)).getMessage().contains("RELATIONSHIP_TIME_PRECISION_EXCEEDS_TARGET"));assertTrue(saved.isEmpty());
 }
 @Test void contractLookupSupportsAFullBatchWithTheRuntimeTenantParserAndStack()throws Exception{
  var plugin=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
  plugin.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler(){public net.sf.jsqlparser.expression.Expression getTenantId(){return new net.sf.jsqlparser.expression.LongValue(1);}public boolean ignoreTable(String table){return !"com_contract".equals(table);}}));
  session.getConfiguration().addInterceptor(plugin);var keys=new ArrayList<P15DeliverySyncTask.ContractIdentity>();for(int i=0;i<500;i++)keys.add(new P15DeliverySyncTask.ContractIdentity("001","C-"+i));
  var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var worker=new Thread(null,()->{try{assertTrue(mapper.selectContracts(new P15DeliverySyncTask.ContractsQuery(1,keys,true)).isEmpty());}catch(Throwable error){failure.set(error);}},"p15-tenant-query",512*1024);worker.setDaemon(true);worker.start();worker.join(10000);assertFalse(worker.isAlive(),"contract lookup did not finish");if(failure.get()!=null)throw new AssertionError("contract lookup failed with tenant parsing and runtime stack",failure.get());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_contract",Integer.class));assertTrue(saved.isEmpty());
 }
 static final class ContractProbe extends P15DeliverySyncTask {
  ContractProbe(TargetMapper mapper){super(mapper,mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));}
  @Override protected String targetMapperNamespace(){return TargetMapper.class.getName();}
  List<Source> query(SyncContext<Parameters> context){return super.syncDataQuery(context.forData("Contract",false,false));}
  List<Source> queryLocal(SyncContext<Parameters> context){return super.syncDataQuery(context.forLocalData("Contract",false,false));}
  List<Source> shipments(SyncContext<Parameters> context){var rows=new ArrayList<Source>();try(var session=context.source().open(context.connectionId())){session.mapper(SourceMapper.class).selectShipmentContract(new SourceQuery<>(context.maxRows(),context.tenantId(),true,context.parameters()),result->rows.add(result.getResultObject()));}return rows;}
  @Override public void work(SyncContext<Parameters> context){syncData(context.forData("Contract",false,false));}
 }
 static final class EventProbe extends P15DeliverySyncTask {
  EventProbe(TargetMapper mapper){super(mapper,mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));}
  @Override protected String sourceMapperNamespace(){return SourceMapper.class.getName();}
  @Override public void work(SyncContext<Parameters> context){syncData(context.forData("ShipmentEvent",false,true));}
  List<Source> queryEvents(SyncContext<Parameters> context){var rows=new ArrayList<Source>();super.syncDataQueryByRowHandler(context.forData("ShipmentEvent",false,true),result->rows.add(result.getResultObject()));return rows;}
 }
 static final class RelationshipProbe extends P15DeliverySyncTask {
  final List<Source> planned=new ArrayList<>();RelationshipProbe(TargetMapper mapper){super(mapper,mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));}
  @Override protected String sourceMapperNamespace(){return SourceMapper.class.getName();}
  @Override public void work(SyncContext<Parameters> context){syncData(context.forData("DeviceRelationship",false,true));}
  @Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> batch){super.syncDataInsertBefore(batch);planned.addAll(batch.rows());}
 }
}
