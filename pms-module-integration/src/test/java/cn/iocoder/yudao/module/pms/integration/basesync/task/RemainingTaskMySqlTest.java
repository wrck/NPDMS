package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class RemainingTaskMySqlTest extends RemainingTaskMySqlSupport {
 @Test void ordersAndLinesPreviewWithoutAnyWriteThenBatchInsertWithActualParentAndDeferredOptionalIds(){
  order("A");order("B");line("A",3);
  var task=new P14SalesOrderSyncTask(session.getMapper(P14SalesOrderSyncTask.TargetMapper.class),org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));
  assertEquals(new SyncCounts(3,3,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));verify(identities,never()).saveAll(any());
  assertEquals(new SyncCounts(3,3,0,0,0),run(task,false));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));
  assertEquals(jdbc.queryForObject("SELECT id FROM com_sales_order WHERE order_no='A'",Long.class),jdbc.queryForObject("SELECT order_id FROM com_sales_order_line",Long.class));
  assertNull(jdbc.queryForObject("SELECT product_id FROM com_sales_order_line",Long.class));assertNull(jdbc.queryForObject("SELECT customer_id FROM com_sales_order_line",Long.class));
  assertEquals("PENDING_AUTHORITY",jdbc.queryForObject("SELECT quantity_status FROM com_sales_order_line",String.class));
  assertEquals(new SyncCounts(3,0,0,3,0),run(task,true));
 }
 @Test void laterInvalidLineRollsBackAllEarlierHeadersAndDoesNotAdoptManualOrders(){
  order("A");line("A",-1);var task=new P14SalesOrderSyncTask(session.getMapper(P14SalesOrderSyncTask.TargetMapper.class),org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));
  assertThrows(IllegalArgumentException.class,()->run(task,false));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_sales_order",Integer.class));
  saved.clear();jdbc.update("UPDATE pm_order_line_from_erp SET orderQuantity=3");
  jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,company_code,order_type,order_no,status,creator,updater) VALUES(99,1,'SAP','001','0','A','ENABLED','manual','manual')");
  assertThrows(IllegalArgumentException.class,()->run(task,true));assertEquals("manual",jdbc.queryForObject("SELECT creator FROM com_sales_order",String.class));
 }
 @Test void duplicateOrderPayloadAndSameVersionChangesAreRejected(){
  order("A");jdbc.update("INSERT INTO pm_order_data_from_erp(id,orderNumber,compCode,orderType,source,syncTime,contractNo) VALUES(3,'A','01','0','SAP','2020-01-01','OTHER')");
  var task=new P14SalesOrderSyncTask(session.getMapper(P14SalesOrderSyncTask.TargetMapper.class),org.mockito.Mockito.mock(cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.class));
  assertThrows(IllegalArgumentException.class,()->run(task,true));
  jdbc.update("DELETE FROM pm_order_data_from_erp WHERE id=3");run(task,false);jdbc.update("UPDATE pm_order_data_from_erp SET contractNo='CHANGED'");
  assertThrows(IllegalArgumentException.class,()->run(task,true));
 }
 @Test void realProductsRequireOneExactUpstreamAndPreserveExistingProjectList(){
  project();jdbc.update("INSERT INTO pm_project_product_line_real(id,projectId,contractNo,projectCode,orderExecNumber,productFirstName,productName,productSubCode,productSubModel,productSubName,num,memo) VALUES(1,1,'C','P','E','CAT','NAME','ITEM','MODEL','DESC',2,'memo')");
  jdbc.update("INSERT INTO pm_project_real_product_line_from_sms(projectCode,orderExecNumber,productFirstName,productName,productSubCode,productSubModel,productSubName,num,memo,dataSource) VALUES('P','E','CAT','NAME','ITEM','MODEL','DESC',2,'memo','SMS')");
  var task=new P20RealProductSyncTask(session.getMapper(P20RealProductSyncTask.TargetMapper.class),projects);
  assertEquals(new SyncCounts(1,1,0,0,0),run(task,true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_real_product",Integer.class));
  run(task,false);assertEquals(new SyncCounts(1,0,0,1,0),run(task,true));
  jdbc.update("INSERT INTO pm_project_product_line_real(id,projectId,contractNo,projectCode,orderExecNumber,productFirstName,productName,productSubCode,productSubModel,productSubName,num,memo) VALUES(2,1,'C','P','E','CAT','NAME','ITEM','MODEL','DESC',2,'memo')");
  assertThrows(IllegalArgumentException.class,()->run(task,true));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_real_product",Integer.class));
 }
 @Test void originalExecutionIdsShareTheLastWholeRevisionWithoutOverwritingConsumerFields(){
  project();jdbc.update("INSERT INTO pm_project_property_from_sms(id,orderExecNumber,dataSource,projectCode,projectName,corporationCode) VALUES(1,'E','SMS','P','old','01'),(2,'E','SMS','P','last','01')");
  var delegate=session.getMapper(P13ExecutionOrderSyncTask.TargetMapper.class);
  var mapper=mock(P13ExecutionOrderSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(delegate));doReturn(0).when(mapper).oneSourcePerTargetIndex();
  var task=new P13ExecutionOrderSyncTask(mapper);
  assertEquals(new SyncCounts(2,0,0,0,0),run(task,true)); // Multiple originals share a target; preview validates source without inventing committed headers.
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM com_crm_execution_order",Integer.class));
  assertEquals(new SyncCounts(2,1,0,1,0),run(task,false));assertEquals("last",jdbc.queryForObject("SELECT project_name FROM com_crm_execution_order",String.class));
  assertEquals(3,saved.size());
  jdbc.update("UPDATE com_crm_execution_order SET primary_project_id=99");
  assertThrows(IllegalArgumentException.class,()->run(task,true));assertEquals(99L,jdbc.queryForObject("SELECT primary_project_id FROM com_crm_execution_order",Long.class));
 }
 @Test void currentOneSourcePerTargetMetadataIndexIsAnExplicitVersionBlock(){
  var task=new P13ExecutionOrderSyncTask(session.getMapper(P13ExecutionOrderSyncTask.TargetMapper.class));
  var error=assertThrows(IllegalArgumentException.class,()->run(task,false));assertTrue(error.getMessage().contains("METADATA_MULTI_SOURCE_INDEX_REQUIRED"));
  var preview=context(true);assertEquals(0,transaction.execute(status->task.execute(preview)).read());assertTrue(preview.runState().warnings().contains("P13_EXECUTION_SCHEMA_REQUIRED:uk_int_base_sync_binding_target"));
 }
 @Test void targetXmlQueriesAreAcceptedByProductionParser()throws Exception{
  new cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration();var config=session.getConfiguration();
  var examples=Map.of(P14SalesOrderSyncTask.TargetMapper.class.getName()+".selectOrders",Map.of("query",new P14SalesOrderSyncTask.OrdersQuery(1,List.of(new P14SalesOrderSyncTask.OrderKey("SAP","001","0","A")),true)),
   P13ExecutionOrderSyncTask.TargetMapper.class.getName()+".selectExisting",Map.of("query",new P13ExecutionOrderSyncTask.ExistingQuery(1,List.of(new P13ExecutionOrderSyncTask.Key("SMS","E")),true)),
   P20RealProductSyncTask.TargetMapper.class.getName()+".selectProjects",Map.of("query",new P20RealProductSyncTask.ProjectsQuery(1,List.of(1000L),true)));
  for(var example:examples.entrySet())assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(config.getMappedStatement(example.getKey()).getBoundSql(example.getValue()).getSql()));
 }
}
