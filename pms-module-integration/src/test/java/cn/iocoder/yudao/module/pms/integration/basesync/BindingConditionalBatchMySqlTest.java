package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.BindingWrite;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in rollback-only validation on existing new-suite bindings; no schema or business writes. */
class BindingConditionalBatchMySqlTest {
 private Connection connection;private URLClassLoader loader;private Configuration configuration;
 private List<BindingWrite> originals;private List<List<String>> before;
 @BeforeEach void connect()throws Exception{
  assumeTrue("true".equals(System.getenv("BINDING_ROLLBACK_TEST")));
  String url=System.getenv("TEST_MYSQL_URL");
  assertNotNull(url);assertTrue(url.startsWith("jdbc:mysql://172.17.90.178:13306/npdms?"));
  var properties=new Properties();properties.setProperty("user",System.getenv("TEST_MYSQL_USERNAME"));properties.setProperty("password",System.getenv("TEST_MYSQL_PASSWORD"));
  loader=new URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
  var driver=(Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();connection=driver.connect(url,properties);connection.setAutoCommit(false);
  configuration=new Configuration();try(var input=new ClassPathResource("mapper/basesync/BaseSyncMetadataMapper.xml").getInputStream()){new XMLMapperBuilder(input,configuration,"mapper/basesync/BaseSyncMetadataMapper.xml",configuration.getSqlFragments()).parse();}
  originals=new ArrayList<>();
  try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT b.id,b.tenant_id,b.task_id,b.last_run_id,b.object_key,b.source_key,b.target_id,b.fingerprint,b.updater FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id WHERE b.tenant_id=1 AND t.task_code='BASE_P22' AND t.deleted=FALSE AND b.deleted=FALSE ORDER BY b.id LIMIT 2001 FOR UPDATE")){
   while(rs.next())originals.add(new BindingWrite(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getString(8),rs.getString(8),rs.getString(9)));
  }
  assertEquals(2001,originals.size());before=snapshot();
 }
 @AfterEach void rollbackAndVerify()throws Exception{
  try{if(connection!=null){connection.rollback();if(before!=null)assertEquals(before,snapshot(),"Rollback must retain every original binding field");connection.rollback();connection.close();}}finally{if(loader!=null)loader.close();}
 }
 private List<List<String>> snapshot()throws SQLException{
  var result=new ArrayList<List<String>>();
  try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT b.* FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id WHERE b.tenant_id=1 AND t.task_code='BASE_P22' ORDER BY b.id LIMIT 2001")){
   while(rs.next()){var row=new ArrayList<String>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.add(rs.getString(i));result.add(row);}
  }return result;
 }
 private BindingWrite changed(BindingWrite r){return new BindingWrite(r.id(),r.tenantId(),r.taskId(),r.runId(),r.object(),r.sourceKey(),r.targetId(),r.fingerprint().equals("b".repeat(64))?"c".repeat(64):"b".repeat(64),r.fingerprint(),"batch-rollback-proof");}
 private int update(List<BindingWrite> rows)throws SQLException{
  var statement=configuration.getMappedStatement(BaseSyncMetadataMapper.class.getName()+".updateBindings");var parameters=Map.of("command",new BaseSyncMetadataMapper.BindingBatch(rows));var bound=statement.getBoundSql(parameters);
  try(var sql=connection.prepareStatement(bound.getSql())){new DefaultParameterHandler(statement,parameters,bound).setParameters(sql);return sql.executeUpdate();}
 }
 @Test void batchUpdateUsesTheStableSourceUniqueKeyInsteadOfScanningAllBindings()throws Exception{
  var statement=configuration.getMappedStatement(BaseSyncMetadataMapper.class.getName()+".updateBindings");
  var parameters=Map.of("command",new BaseSyncMetadataMapper.BindingBatch(originals.subList(0,2000).stream().map(this::changed).toList()));var bound=statement.getBoundSql(parameters);
  boolean targetFound=false;
  try(var sql=connection.prepareStatement("EXPLAIN "+bound.getSql())){
   new DefaultParameterHandler(statement,parameters,bound).setParameters(sql);
   try(var rs=sql.executeQuery()){while(rs.next())if("binding_row".equals(rs.getString("table"))){targetFound=true;assertEquals("uk_int_base_sync_binding_source",rs.getString("key"));assertEquals(1,rs.getLong("rows"));}}
  }
  assertTrue(targetFound);
 }
 @Test void fiveHundredExistingRowsUseOneNativeConditionalStatement()throws Exception{
  assertEquals(500,update(originals.subList(0,500).stream().map(this::changed).toList()));
  assertEquals(0,update(originals.subList(0,500).stream().map(this::changed).toList()),"Old fingerprint cannot be reused");
 }
 @Test void largestSupportedRuntimeBatchRetainsAffectedRowChecks()throws Exception{
  assertEquals(2000,update(originals.subList(0,2000).stream().map(this::changed).toList()));
  assertEquals(0,update(originals.subList(0,2000).stream().map(this::changed).toList()));
 }
 @Test void staleFingerprintReportsPartialMatchAndAllChangesRollBack()throws Exception{
  var first=changed(originals.getFirst());var second=changed(originals.get(1));
  var stale=new BindingWrite(second.id(),second.tenantId(),second.taskId(),second.runId(),second.object(),second.sourceKey(),second.targetId(),second.fingerprint(),"0".repeat(64),second.actor());
  assertEquals(1,update(List.of(first,stale))); // RunSyncIdentityStore rejects 1 != 2 and the Run transaction rolls back.
 }
 @Test void tenantTaskObjectSourceTargetAndFingerprintRemainRequired()throws Exception{
  var r=changed(originals.getFirst());
  for(var invalid:List.of(
    new BindingWrite(r.id(),2,r.taskId(),r.runId(),r.object(),r.sourceKey(),r.targetId(),r.fingerprint(),r.previousFingerprint(),r.actor()),
    new BindingWrite(r.id(),r.tenantId(),-1,r.runId(),r.object(),r.sourceKey(),r.targetId(),r.fingerprint(),r.previousFingerprint(),r.actor()),
    new BindingWrite(r.id(),r.tenantId(),r.taskId(),r.runId(),"OTHER",r.sourceKey(),r.targetId(),r.fingerprint(),r.previousFingerprint(),r.actor()),
    new BindingWrite(r.id(),r.tenantId(),r.taskId(),r.runId(),r.object(),"absent-source",r.targetId(),r.fingerprint(),r.previousFingerprint(),r.actor()),
    new BindingWrite(r.id(),r.tenantId(),r.taskId(),r.runId(),r.object(),r.sourceKey(),-1,r.fingerprint(),r.previousFingerprint(),r.actor()),
    new BindingWrite(r.id(),r.tenantId(),r.taskId(),r.runId(),r.object(),r.sourceKey(),r.targetId(),r.fingerprint(),"0".repeat(64),r.actor())))assertEquals(0,update(List.of(invalid)));
 }
 @Test void deletedBindingCannotBeUpdated()throws Exception{
  var r=changed(originals.getFirst());
  try(var sql=connection.prepareStatement("UPDATE int_base_sync_binding SET deleted=TRUE WHERE id=? AND tenant_id=1")){sql.setLong(1,r.id());assertEquals(1,sql.executeUpdate());}
  assertEquals(0,update(List.of(r)));
 }
}
