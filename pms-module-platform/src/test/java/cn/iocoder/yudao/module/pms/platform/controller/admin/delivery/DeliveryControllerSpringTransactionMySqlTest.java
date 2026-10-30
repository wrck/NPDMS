package cn.iocoder.yudao.module.pms.platform.controller.admin.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.FileEvidenceService;
import com.baomidou.mybatisplus.core.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real Controller and evidence-service Spring proxies plus production MyBatis XML, isolated MySQL only.
 * Owner/permission ports are controlled fixtures here; native scope authorization is exercised by HTTP.
 */
@EnabledIfSystemProperty(named="delivery.controller.mysql", matches="true")
class DeliveryControllerSpringTransactionMySqlTest {
 static final AtomicBoolean permission=new AtomicBoolean(true), ownerAllowed=new AtomicBoolean(true), failEvidence=new AtomicBoolean(false);
 static final AtomicInteger materialReads=new AtomicInteger(), evidenceReads=new AtomicInteger();
 static long training;
 static final AtomicBoolean operatePermission=new AtomicBoolean(true);
 Long withdrawnMaterial;
 AnnotationConfigApplicationContext context;
 DeliveryController controller; FileEvidenceApi evidence; JdbcTemplate jdbc;
 @Configuration @EnableTransactionManagement(proxyTargetClass=true) @EnableMethodSecurity
 static class Config {
  @Bean DataSource dataSource() {
   String url=System.getenv("DELIVERY_CONTROLLER_JDBC_URL");
   assertEquals("jdbc:mysql://127.0.0.1:25406/delivery_b12_live_20261005?useSSL=false&allowPublicKeyRetrieval=true",url);
   return new DriverManagerDataSource(url,System.getenv("DELIVERY_CONTROLLER_DB_USER"),System.getenv("DELIVERY_CONTROLLER_DB_PASSWORD"));
  }
  @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
  @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
   var cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
   cfg.setEnvironment(new Environment("controller-regression",new SpringManagedTransactionFactory(),ds));
   for(var type:List.of(DeliveryMaterialMapper.class,FileArtifactMapper.class,FileVersionMapper.class,FileReferenceMapper.class)) {
    cfg.addMapper(type);
    String resource="mapper/"+(type==DeliveryMaterialMapper.class?"delivery/":"file/")+type.getSimpleName()+".xml";
    try(var input=Config.class.getClassLoader().getResourceAsStream(resource)) {
     assertNotNull(input);new XMLMapperBuilder(input,cfg,resource,cfg.getSqlFragments()).parse();
    }
   }
   return new MybatisSqlSessionFactoryBuilder().build(cfg);
  }
  @Bean SqlSessionTemplate sqlSession(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
  @Bean FileArtifactMapper artifacts(SqlSessionTemplate sql){return sql.getMapper(FileArtifactMapper.class);}
  @Bean FileReferenceMapper references(SqlSessionTemplate sql){return sql.getMapper(FileReferenceMapper.class);}
  @Bean FileVersionMapper versions(SqlSessionTemplate sql) {
   var real=sql.getMapper(FileVersionMapper.class);
   var result=mock(FileVersionMapper.class,org.mockito.AdditionalAnswers.delegatesTo(real));
   doAnswer(call->{
    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    evidenceReads.incrementAndGet();
    var row=real.selectOne(call.getArgument(0));
    if(failEvidence.get())throw new IllegalStateException("synthetic evidence failure after real SQL");
    return row;
   }).when(result).selectOne(any(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery.class));
   return result;
  }
  @Bean DeliveryMaterialMapper materialsMapper(SqlSessionTemplate sql,DataSource ds) {
   var real=sql.getMapper(DeliveryMaterialMapper.class);
   var result=mock(DeliveryMaterialMapper.class,org.mockito.AdditionalAnswers.delegatesTo(real));
   doAnswer(call->{
    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());materialReads.incrementAndGet();
    if(failEvidence.get())new JdbcTemplate(ds).update("INSERT INTO b13_controller_tx_probe(id) VALUES(1)");
    return real.selectListForOwner(call.getArgument(0));
   }).when(result).selectListForOwner(any(cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialOwnerQuery.class));
   return result;
  }
  @Bean FileEvidenceApi evidence(FileArtifactMapper a,FileVersionMapper v,FileReferenceMapper r){return new FileEvidenceService(a,v,r);}
  @Bean DeliveryCatalogService catalog(){return mock(DeliveryCatalogService.class);}
  @Bean DeliveryRequirementService requirements(){return mock(DeliveryRequirementService.class);}
  @Bean DeliverySubmissionMapper submissions(){return mock(DeliverySubmissionMapper.class);}
  @Bean DeliveryFulfillmentService fulfillment(){return mock(DeliveryFulfillmentService.class);}
  @Bean DeliveryDocumentOriginResolver origins(){return mock(DeliveryDocumentOriginResolver.class);}
  @Bean DeliveryOwnerAccess owners() {
   var owner=mock(DeliveryOwnerAccess.class);
   when(owner.require(anyString(),anyString(),anyLong(),nullable(String.class),anyBoolean(),anyBoolean())).thenAnswer(call->{
    if(Boolean.TRUE.equals(call.getArgument(5))) {
     assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
     assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }
    if(!ownerAllowed.get())throw new BusinessContractException("DELIVERY_ACCESS_DENIED","synthetic scope denied");
    return 1L;
   });when(owner.allowsGenericDeliveryActions(anyString(),anyString())).thenReturn(true);return owner;
  }
  @Bean(name="ss") SecurityFrameworkService security(){
   var security=mock(SecurityFrameworkService.class);when(security.hasPermission(anyString())).thenAnswer(call->permission.get() && (!"pms:delivery:operate".equals(call.getArgument(0)) || operatePermission.get()));return security;
  }
  @Bean DeliveryEventPublisher publisher(DataSource ds) {
   var outbox=mock(cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi.class);
   doAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());new JdbcTemplate(ds).update("INSERT INTO b13_controller_outbox_probe(event_key) VALUES(?)",call.getArgument(1,String.class));return null;})
    .when(outbox).append(anyString(),anyString(),any(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent.class));
   return new DeliveryEventPublisher(outbox);
  }
  @Bean DeliveryMaterialService materials(DeliveryMaterialMapper m,DeliveryCatalogService c,FileEvidenceApi e,DeliveryEventPublisher publisher){return new DeliveryMaterialService(m,c,e,publisher,List.of());}
  @Bean DeliveryController controller(DeliveryCatalogService c,DeliveryMaterialService m,DeliveryRequirementService r,DeliveryOwnerAccess o,DeliverySubmissionMapper s,FileEvidenceApi e){return new DeliveryController(c,m,r,o,s,e);}
 }
 @BeforeEach void start() {
  permission.set(true);operatePermission.set(true);ownerAllowed.set(true);failEvidence.set(false);materialReads.set(0);evidenceReads.set(0);
  training=Long.parseLong(System.getenv("DELIVERY_CONTROLLER_TRAINING_ID"));TenantContextHolder.setTenantId(1L);
  context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(DeliveryController.class);evidence=context.getBean(FileEvidenceApi.class);jdbc=new JdbcTemplate(context.getBean(DataSource.class));
  jdbc.execute("CREATE TABLE IF NOT EXISTS b13_controller_tx_probe(id BIGINT PRIMARY KEY)");jdbc.update("DELETE FROM b13_controller_tx_probe");
  jdbc.execute("CREATE TABLE IF NOT EXISTS b13_controller_outbox_probe(id BIGINT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(256))");jdbc.update("DELETE FROM b13_controller_outbox_probe");
 }
 @AfterEach void stop(){if(context!=null){if(withdrawnMaterial!=null)jdbc.update("UPDATE plt_delivery_material SET status='ACTIVE' WHERE id=? AND tenant_id=1",withdrawnMaterial);context.close();}TenantContextHolder.clear();}
 @Test void mandatoryEvidenceStillRejectsCallsWithoutOuterTransaction() {
  assertThrows(IllegalTransactionStateException.class,()->evidence.inspectDocument(1L,1L));
 }
 @Test void proxiedControllerReadsRealNativeFileIdentityInOneTransaction() {
  assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(controller));assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(evidence));
  var rows=controller.listMaterials("IMP","training",training,null).getData();assertFalse(rows.isEmpty());
  var file=rows.stream().filter(r->r.getFileReferenceId()!=null).findFirst().orElseThrow();assertNotNull(file.getFileBusinessKey());
  var stored=jdbc.queryForMap("SELECT owner_context,object_type,object_id,purpose_code,reference_key FROM plt_file_reference WHERE tenant_id=1 AND id=?",file.getFileReferenceId());
  assertEquals(stored.get("object_id"),file.getFileBusinessKey().objectId());assertEquals(stored.get("reference_key"),file.getFileBusinessKey().referenceKey());
  assertTrue(evidenceReads.get()>0);assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
 @Test void deniedOwnerNeverReadsMaterialsOrEvidence() {
  ownerAllowed.set(false);assertThrows(BusinessContractException.class,()->controller.listMaterials("IMP","training",training,null));
  assertEquals(0,materialReads.get());assertEquals(0,evidenceReads.get());
 }
 @Test void deniedMethodPermissionNeverReadsMaterialsOrEvidence() {
  permission.set(false);assertThrows(AccessDeniedException.class,()->controller.listMaterials("IMP","training",training,null));
  assertEquals(0,materialReads.get());assertEquals(0,evidenceReads.get());
 }
 @Test void evidenceFailureRollsBackWorkOnTheSameMyBatisSpringTransaction() {
  failEvidence.set(true);assertThrows(IllegalStateException.class,()->controller.listMaterials("IMP","training",training,null));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM b13_controller_tx_probe",Integer.class));
  assertTrue(evidenceReads.get()>0);assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
 long activeFile() {
  withdrawnMaterial=jdbc.queryForObject("SELECT id FROM plt_delivery_material WHERE tenant_id=1 AND owner_module='IMP' AND entity_type='training' AND entity_id=? AND material_kind='FILE' AND requirement_id IS NULL AND status='ACTIVE' ORDER BY id DESC LIMIT 1",Long.class,training);
  return withdrawnMaterial;
 }
 @Test void fileWithdrawalAndResponseEvidenceCommitTogether() {
  long id=activeFile();var response=controller.withdrawMaterial(id).getData();
  assertEquals("WITHDRAWN",response.getStatus());assertNotNull(response.getFileBusinessKey());
  assertEquals("WITHDRAWN",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=?",String.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM b13_controller_outbox_probe",Integer.class));
 }
 @Test void withdrawalResponseFailureRollsBackMaterialAndOutbox() {
  long id=activeFile();failEvidence.set(true);
  assertThrows(IllegalStateException.class,()->controller.withdrawMaterial(id));
  assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=?",String.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM b13_controller_outbox_probe",Integer.class));
  assertTrue(evidenceReads.get()>0);
 }
 @Test void deniedWithdrawalDoesNotChangeMaterialOrAppendOutbox() {
  long id=activeFile();ownerAllowed.set(false);
  assertThrows(BusinessContractException.class,()->controller.withdrawMaterial(id));
  assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=?",String.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM b13_controller_outbox_probe",Integer.class));
  assertEquals(0,evidenceReads.get());
 }
 @Test void templateOwnerAdvertisesOnlySupportedGenericMaterialActions() {
  assertEquals(List.of("REGISTER_MATERIAL","WITHDRAW_MATERIAL"),controller.allowedActions("ACC","project_deliverable",9100001L).getData());
 }
 @Test void ordinaryOwnerRetainsGenericSubmissionAndConfirmationActions() {
  assertEquals(List.of("REGISTER_MATERIAL","WITHDRAW_MATERIAL","SUBMIT","CONFIRM","WITHDRAW_SUBMISSION"),controller.allowedActions("IMP","training",training).getData());
 }
 @Test void queryOnlyPermissionNeverAdvertisesWrites() {
  operatePermission.set(false);assertTrue(controller.allowedActions("ACC","project_deliverable",9100001L).getData().isEmpty());
 }

}
