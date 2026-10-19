package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncFingerprint;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only connection-local temporary metadata/project tables. No old tasks or publication tables are queried. */
class IndependentScopeMySqlTest extends RemainingTaskMySqlSupport {
 BaseSyncPublishedProjects scopes;
 @BeforeEach void completedScope(){
  jdbc.execute("CREATE TEMPORARY TABLE int_base_sync_run(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,connection_id BIGINT,task_code VARCHAR(16),status VARCHAR(32),preview BOOLEAN,deleted BOOLEAN,read_count BIGINT)");
  jdbc.execute("CREATE TEMPORARY TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(64),project_type VARCHAR(16),deleted BOOLEAN)");
  jdbc.execute("ALTER TABLE int_base_sync_binding DROP INDEX uk_int_base_sync_binding_target");
  jdbc.update("INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows) VALUES(1,1,'project','BASE_P16','DPPMS',3,1,100)");
  jdbc.update("INSERT INTO int_base_sync_run VALUES(10,1,1,3,'BASE_P16','COMPLETED',FALSE,FALSE,5),(11,1,1,3,'BASE_P16','PREVIEW_READY',TRUE,FALSE,5),(12,1,1,3,'BASE_P16','FAILED',FALSE,FALSE,0)");
  jdbc.update("INSERT INTO proj_project VALUES(100,1,'P','10',FALSE),(200,1,'Q','10',FALSE)");
  bind(1,"PROJECT_MASTER","2",100,"a".repeat(64));bind(2,"PROJECT_MASTER","3",200,"b".repeat(64));
  bind(3,"PROJECT_ALIAS","1",100,proof(1,2,100,"P"));bind(4,"PROJECT_ALIAS","2",100,proof(2,2,100,"P"));bind(5,"PROJECT_ALIAS","3",200,proof(3,3,200,"Q"));
  transaction.setIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
  scopes=new BaseSyncPublishedProjects(session.getMapper(BaseSyncPublishedProjects.ReferenceMapper.class));
 }
 void bind(long id,String object,String key,long target,String fingerprint){jdbc.update("INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id) VALUES(?,1,1,?,?,?,?,10)",id,object,key,target,fingerprint);}
 static String proof(long key,long canonical,long target,String code){return SyncFingerprint.of(Arrays.asList(key,canonical,"10",code,target));}
 BaseSyncPublishedProjects.Snapshot load(){return transaction.execute(status->scopes.load(new BaseSyncPublishedProjects.Query(1,3,100)));}
 @Test void completedFullRunPinsEveryOriginalAliasAndIgnoresFailedAndPreviewRuns(){
  var result=load();assertEquals(10,result.publicationId());assertEquals(3,result.members().size());assertEquals(2,result.canonical().size());assertEquals("2",result.canonical().get(100L).sourceKey());assertThrows(UnsupportedOperationException.class,()->result.members().clear());
 }
 @Test void missingUnrefreshedOrChangedRowsCannotBecomeACompleteOrWiderScope(){
  jdbc.update("UPDATE int_base_sync_binding SET last_run_id=9 WHERE source_key='1' AND object_key='PROJECT_ALIAS'");assertTrue(assertThrows(IllegalStateException.class,this::load).getMessage().contains("INCOMPLETE"));
  jdbc.update("UPDATE int_base_sync_binding SET last_run_id=10");jdbc.update("UPDATE proj_project SET project_code='RENAMED' WHERE id=100");assertTrue(assertThrows(IllegalStateException.class,this::load).getMessage().contains("SOURCE_OR_PROJECT_CHANGED"));
 }
 @Test void emptySuccessfulScopeStaysEmptyAndMissingFormalCompletionFails(){
  jdbc.update("INSERT INTO int_base_sync_run VALUES(13,1,1,3,'BASE_P16','COMPLETED',FALSE,FALSE,0)");assertTrue(load().members().isEmpty());
  jdbc.update("UPDATE int_base_sync_run SET status='SUCCESS' WHERE preview=FALSE");assertTrue(assertThrows(IllegalStateException.class,this::load).getMessage().contains("BASE_P16_SCOPE_REQUIRED"));
 }
 @Test void unchangedMasterAndAliasRefreshOnlyTheirNewSuiteRunMembership(){
  var task=new P16ProjectSyncTask(null,null,null,null);var row=new P16ProjectSyncTask.Row().setId(100L).setProjectCode("P").setProjectType("10");
  var source=new P16ProjectSyncTask.Source().setProjectId(2L).setCanonicalProjectId(2L).setProjectCode("P").setProjectType("10").setTarget(row).setAction(cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction.UNCHANGED).setMasterPrevious("a".repeat(64)).setOriginalPrevious("b".repeat(64)).setAliasPrevious("c".repeat(64));
  var c=context(false).forParameters(new P16ProjectSyncTask.Parameters());
  task.syncDataInsertAfter(new cn.iocoder.yudao.module.pms.integration.api.basesync.SyncBatch<>(c.forData("ProjectMaster",false,true),List.of(source)));
  task.syncDataInsertAfter(new cn.iocoder.yudao.module.pms.integration.api.basesync.SyncBatch<>(c.forData("ProjectAlias",false,true),List.of(source)));
  org.mockito.Mockito.verify(identities).saveAll(org.mockito.ArgumentMatchers.argThat(rows->rows.size()==2&&rows.stream().allMatch(r->r.previousFingerprint()!=null)));
  org.mockito.Mockito.verify(identities).saveAll(org.mockito.ArgumentMatchers.argThat(rows->rows.size()==1&&rows.getFirst().object().equals("PROJECT_ALIAS")&&rows.getFirst().previousFingerprint().equals("c".repeat(64))));
 }
 @Test void nativeScopeXmlRetainsTenantConnectionAndCapacityBoundaries()throws Exception{
  assertThrows(IllegalStateException.class,()->transaction.execute(status->scopes.load(new BaseSyncPublishedProjects.Query(1,4,100))));
  assertThrows(IllegalStateException.class,()->transaction.execute(status->scopes.load(new BaseSyncPublishedProjects.Query(2,3,100))));
  assertThrows(IllegalStateException.class,()->transaction.execute(status->scopes.load(new BaseSyncPublishedProjects.Query(1,3,2))));
  var config=session.getConfiguration();new cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration();
  for(var statement:List.of("selectCompleted","countMasters","selectPage")){
   Object query=statement.equals("selectCompleted")?new BaseSyncPublishedProjects.Query(1,3,100):new BaseSyncPublishedProjects.Page(1,3,10,"");
   assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(config.getMappedStatement(BaseSyncPublishedProjects.ReferenceMapper.class.getName()+"."+statement).getBoundSql(Map.of("query",query)).getSql()));
  }
 }
}
